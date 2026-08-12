(ns orcpub.routes
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.test :as test]
            [io.pedestal.http.ring-middlewares :as ring]
            [ring.middleware.resource :as ring-resource]
            [ring.util.response :as ring-resp]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.interceptor.error :as error-int]
            [io.pedestal.interceptor.chain :refer [terminate]]
            #_[com.stuartsierra.component :as component]
            [buddy.auth.protocols :as proto]
            [buddy.auth.backends :as backends]
            [buddy.sign.jwt :as jwt]
            [buddy.hashers :as hashers]
            [buddy.auth.middleware :refer [authentication-request]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [orcpub.time :as time :refer [hours ago from-now instant before?]]
            [clojure.string :as s]
            [clojure.spec.alpha :as spec]
            [clojure.pprint]
            [orcpub.dnd.e5.skills :as skill5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.magic-items :as mi5e]
            [orcpub.dnd.e5.template :as t5e]
            [datomic.api :as d]
            [bidi.bidi :as bidi]
            [orcpub.common :as common]
            [orcpub.route-map :as route-map]
            [orcpub.errors :as errors]
            [orcpub.privacy :as privacy]
            [orcpub.email :as email]
            [orcpub.index :refer [index-page]]
            [orcpub.pdf :as pdf]
            [orcpub.registration :as registration]
            [orcpub.entity.strict :as se]
            [orcpub.entity :as entity]
            [orcpub.security :as security]
            [orcpub.fork.branding :as branding]
            [orcpub.fork.auth :as auth]
            [orcpub.fork.user-data :as user-data]
            [orcpub.routes.party :as party]
            [orcpub.routes.folder :as folder]
            [hiccup.page :as page]
            [environ.core :as environ]
            [clojure.set :as sets]
            [ring.middleware.head :as head]
            [ring.util.codec :as codec]
            [ring.util.request :as req])
  ;; PDFBox 3.x: Use Loader class instead of PDDocument.load() static method
  ;; OLD (2.x): (PDDocument/load input-stream)
  ;; NEW (3.x): (Loader/loadPDF byte-array)  — does NOT accept InputStream
  ;; 
  ;; Import syntax notes for Clojure newcomers:
  ;;   - (org.apache.pdfbox.pdmodel PDDocument PDPage) imports multiple classes from one package
  ;;   - org.apache.pdfbox.Loader imports a single class (no parens needed)
  (:import (org.apache.pdfbox.pdmodel PDPage PDPageContentStream)
           org.apache.pdfbox.Loader
           (java.io ByteArrayOutputStream ByteArrayInputStream))
  (:gen-class))

(deftype FixedBuffer [^long len])

(def ^:private jwt-secret
  "JWT signing secret from SIGNATURE env var.
   nil when unset — check-auth returns 500 with a diagnostic message."
  (environ/env :signature))

(when-not jwt-secret
  (println "WARNING: SIGNATURE env var is not set — all authenticated API calls will fail"))

(def backend (backends/jws {:secret jwt-secret}))

(defn first-user-by [db query value]
  (let [result (d/q query
                    db
                    value)
        user-id (ffirst result)]
    (d/pull db '[*] user-id)))

(def username-query
  '[:find ?e
    :in $ ?username
    :where [?e :orcpub.user/username ?username]])

;; Case-insensitive email lookup to guard against mixed-case legacy data.
;; Callers must pass a lowercased email.
(def email-query
  '[:find ?e
    :in $ ?email
    :where [?e :orcpub.user/email ?stored]
           [(clojure.string/lower-case ?stored) ?email]])

(defn find-user-by-username-or-email [db username-or-email]
  (d/q
   '[:find (pull ?e [*]) .
     :in $ ?user-or-email
     :where (or [?e :orcpub.user/username ?user-or-email]
                [?e :orcpub.user/email ?user-or-email])]
   db
   username-or-email))

(defn find-user-by-username [db username]
  (d/q '[:find (pull ?e [*]) .
         :in $ ?username
         :where [?e :orcpub.user/username ?username]]
       db
       username))

(defn lookup-user-by-username [db username password]
  (let [user (d/q '[:find (pull ?e [*]) .
                    :in $ [?username ?password]
                    :where
                    [?e :orcpub.user/username ?username]
                    [?e :orcpub.user/password ?enc]
                    [(buddy.hashers/check ?password ?enc)]]
                  db
                  [username password])]
    user))

(defn lookup-user-by-email [db email password]
  (let [user (first-user-by db
                         '{:find [?e]
                           :in [$ [?email ?password]]
                           :where [[?e :orcpub.user/email ?email-2]
                                   [(clojure.string/lower-case ?email-2)
                                    ?email]
                                   [?e :orcpub.user/password ?enc]
                                   [(buddy.hashers/check ?password ?enc)]]}
                         [(s/lower-case email) password])]
    user))

(defn lookup-user [db username password]
  (if (re-matches registration/email-format username)
    (lookup-user-by-email db username password)
    (lookup-user-by-username db username password)))

(defn terminate-request [context status message]
  (-> context
      terminate
      (assoc :response {:status status :body {:message message}})))

(def check-auth
  "Interceptor that verifies the JWT bearer token on authenticated routes.
   Returns 401 for missing/invalid tokens, 500 with diagnostic if the
   JWT secret itself is not configured."
  (interceptor/interceptor
   {:name :check-auth
    :enter (fn [context]
             (if-not jwt-secret
               (terminate-request context 500
                                  "Server misconfigured: SIGNATURE env var not set")
               (try
                 (let [request (:request context)
                       updated-request (authentication-request request backend)
                       username (get-in updated-request [:identity :user])]
                   (if (and (:identity updated-request)
                            username)
                     (assoc context :request (assoc updated-request :username username))
                     (terminate-request context 401 "Unauthorized")))
                 (catch Exception e
                   (terminate-request context 401
                                      (str "Authentication failed: "
                                           (.getMessage e)))))))}))

(defn party-owner [db id]
  (d/q '[:find ?owner .
         :in $ ?id
         :where [?id :orcpub.dnd.e5.party/owner ?owner]]
       db
       id))

(def id-path [:request :path-params :id])

(def parse-id
  (interceptor/interceptor
   {:name :parse-id
    :enter (fn [context]
             (let [id-str (get-in context id-path)]
               (if (and id-str (re-matches #"\d+" id-str))
                 (assoc-in context
                           id-path
                           (Long/parseLong id-str))
                 (terminate-request context 400 "Bad ID"))))}))


(def check-party-owner
  (interceptor/interceptor
   {:name :check-party-owner
    :enter (fn [context]
             (let [{:keys [identity db] {:keys [id]} :path-params} (:request context)
                   party-owner (party-owner db id)]
               (if (= (:user identity) party-owner)
                 context
                 (terminate-request context 401 "You don't own this party"))))}))

(defn folder-owner [db id]
  (d/q '[:find ?owner .
         :in $ ?id
         :where [?id :orcpub.dnd.e5.folder/owner ?owner]]
       db
       id))

(def check-folder-owner
  (interceptor/interceptor
   {:name :check-folder-owner
    :enter (fn [context]
             (let [{:keys [identity db] {:keys [id]} :path-params} (:request context)
                   owner (folder-owner db id)]
               (cond
                 (nil? owner) (terminate-request context 404 "Folder not found")
                 (= (:user identity) owner) context
                 :else (terminate-request context 401 "You don't own this folder"))))}))

(defn redirect [route-key]
  (ring-resp/redirect (route-map/path-for route-key)))


(defn verification-expired? [verification-sent]
  (before? (instant verification-sent) (-> 24 hours ago)))

(defn login-error [error-key & [data]]
  {:status 401 :body (merge
                      data
                      {:error error-key})})

(defn create-token [username exp]
  (jwt/sign {:user username
             :exp exp}
            (environ/env :signature)))

(defn following-usernames [db ids]
  (map :orcpub.user/username
       (d/pull-many db '[:orcpub.user/username] ids)))

(defn user-body
  "Build the user API response. Core fields are inline; fork-specific
   fields (e.g. tier data) are added by user-data/enrich-response."
  [db user]
  (cond-> (user-data/enrich-response
           {:username (:orcpub.user/username user)
            :email (:orcpub.user/email user)
            :send-updates? (boolean (:orcpub.user/send-updates? user))
            :following (following-usernames db (map :db/id (:orcpub.user/following user)))}
           user)
    (:orcpub.user/pending-email user)
    (assoc :pending-email (:orcpub.user/pending-email user))))

(defn bad-credentials-response [db username ip]
  (security/add-failed-login-attempt! username ip)
  (if (security/too-many-attempts-for-username? username)
    (login-error errors/too-many-attempts)
    (let [user-for-username (find-user-by-username-or-email db username)]
      (login-error (if (:db/id user-for-username)
                     errors/bad-credentials
                     errors/no-account)))))

(defn create-login-response [db conn user id & [headers]]
  (let [token (create-token (:orcpub.user/username user)
                            (-> auth/token-lifetime-hours hours from-now))
        now (java.util.Date.)]
    (when auth/track-last-login?
      (d/transact conn [{:db/id id
                         :orcpub.user/last-login now}]))
    {:status 200
     :headers headers
     :body {:user-data (user-body db user)
            :token token}}))

(defn login-response
  [{:keys [json-params db conn remote-addr] :as request}]
  (let [{raw-username :username raw-password :password} json-params]
    (cond
      (s/blank? raw-username) (login-error errors/username-required)
      (s/blank? raw-password) (login-error errors/password-required)
      :else (let [username (s/trim raw-username)
                  password (s/trim raw-password)
                  {:keys [:orcpub.user/verified?
                          :orcpub.user/verification-sent
                          :orcpub.user/email
                          :db/id] :as user} (lookup-user db username password)
                  unverified? (not verified?)
                  expired? (and verification-sent (verification-expired? verification-sent))]
              (cond
                (nil? id) (bad-credentials-response db username remote-addr)
                (and unverified? expired?) (login-error errors/unverified-expired)
                unverified? (login-error errors/unverified {:email email})
                :else
                (create-login-response db conn user id))))))

(defn login [{:keys [json-params db] :as request}]
  (try
    (let [resp (login-response request)]
      resp)
    (catch Throwable e (prn "E" e) (throw e))))


(defn user-for-email [db email]
  (let [user (first-user-by db
                            '{:find [?e]
                              :in [$ ?email]
                              :where [[?e :orcpub.user/email ?email-2]
                                      [(clojure.string/lower-case ?email-2)
                                       ?email]]}
                            (s/lower-case email))]
    user))

(defn base-url [{:keys [scheme headers]}]
  (str (or (headers "x-forwarded-proto") (name scheme)) "://" (headers "host")))

(defn send-verification-email [request params verification-key]
  (email/send-verification-email
   (base-url request)
   params
   verification-key))

(defn send-email-change-verification [request params verification-key]
  (email/send-email-change-verification
   (base-url request)
   params
   verification-key))

(defn do-verification [request params conn & [tx-data]]
  (let [verification-key (str (java.util.UUID/randomUUID))
        now (java.util.Date.)]
    (try
      @(d/transact
        conn
        [(merge
          tx-data
          {:orcpub.user/verified? false
           :orcpub.user/verification-key verification-key
           :orcpub.user/verification-sent now})])
      (send-verification-email request params verification-key)
      {:status 200}
      (catch Exception e
        (println "ERROR: Failed to create verification record:" (.getMessage e))
        (throw (ex-info "Unable to complete registration. Please try again or contact support."
                        {:error :verification-failed}
                        e))))))

(defn register [{:keys [json-params db conn] :as request}]
  (let [{:keys [username email password send-updates?]} json-params
        username (when username (s/trim username))
        email (when email (s/lower-case (s/trim email)))
        password (when password (s/trim password))
        validation (registration/validate-registration
                    json-params
                    (seq (d/q email-query db email))
                    (seq (d/q username-query db username)))
        now (java.util.Date.)]
    (try
      (if (seq validation)
        {:status 400
         :body validation}
        (do-verification
         request
         json-params
         conn
         (merge
          {:orcpub.user/email email
           :orcpub.user/username username
           :orcpub.user/password (hashers/encrypt password)
           :orcpub.user/send-updates? send-updates?
           :orcpub.user/created now}
          (when auth/record-last-login-at-registration?
            {:orcpub.user/last-login now})
          (user-data/registration-defaults))))
      (catch Throwable e (prn e) (throw e)))))

(def user-for-verification-key-query
  '[:find ?e
    :in $ ?key
    :where [?e :orcpub.user/verification-key ?key]])

(def user-for-email-query
  '[:find ?e
    :in $ ?email
    :where [?e :orcpub.user/email ?email]])

(defn user-for-verification-key [db key]
  (first-user-by db user-for-verification-key-query key))

(defn user-id-for-username [db username]
  (d/q
   '[:find ?e .
     :in $ ?username
     :where [?e :orcpub.user/username ?username]]
   db
   username))

(defn verify [{:keys [query-params db conn] :as request}]
  (if-let [key (:key query-params)]
    (let [{:keys [:orcpub.user/verification-sent
                  :orcpub.user/verified?
                  :orcpub.user/username
                  :orcpub.user/pending-email
                  :db/id] :as user} (user-for-verification-key (d/db conn) key)]
      (if username
        (cond
          (and verified? (nil? pending-email))
          (redirect route-map/verify-success-route)

          (or (nil? verification-sent)
              (verification-expired? verification-sent))
          ;; Clean up stale pending state so user can request a fresh change
          (do (let [retractions (cond-> [[:db/retract id :orcpub.user/verification-key key]
                                         [:db/retract id :orcpub.user/verification-sent verification-sent]]
                                  pending-email
                                  (conj [:db/retract id :orcpub.user/pending-email pending-email]))]
                @(d/transact conn retractions))
              (redirect route-map/verify-failed-route))

          pending-email
          ;; Guard: re-check that the target email hasn't been claimed since request.
          ;; All paths retract verification-key and verification-sent to prevent
          ;; link reuse and avoid stale rate-limit data.
          (if (seq (d/q email-query (d/db conn) pending-email))
            (do @(d/transact conn [[:db/retract id :orcpub.user/pending-email pending-email]
                                   [:db/retract id :orcpub.user/verification-key key]
                                   [:db/retract id :orcpub.user/verification-sent verification-sent]])
                (redirect route-map/verify-failed-route))
            (do @(d/transact conn [{:db/id id
                                    :orcpub.user/email pending-email}
                                   [:db/retract id :orcpub.user/pending-email pending-email]
                                   [:db/retract id :orcpub.user/verification-key key]
                                   [:db/retract id :orcpub.user/verification-sent verification-sent]])
                (redirect route-map/verify-success-route)))

          :else
          (do @(d/transact conn [{:db/id id
                                  :orcpub.user/verified? true}])
              (redirect route-map/verify-success-route)))
        {:status 400}))
    {:status 400}))

(defn re-verify [{:keys [query-params db conn] :as request}]
  (let [email (:email query-params)
        {:keys [:orcpub.user/verification-sent
                :orcpub.user/verified?
                :db/id] :as user} (user-for-email db email)]
    (if verified?
      (redirect route-map/verify-success-route)
      (do-verification request
                       (merge query-params
                              {:first-and-last-name auth/verification-display-name})
                       conn
                       {:db/id id}))))

;; ─── Email Preferences ─────────────────────────────────────────────

(defn unsubscribe-token
  "Create a JWT-signed unsubscribe token for embedding in email links.
   Stateless — no DB storage needed. Verified by checking JWT signature."
  [email]
  (jwt/sign {:email (s/lower-case email) :action "unsubscribe"}
            (environ/env :signature)))

(defn unsubscribe
  "GET handler for /unsubscribe?token=<jwt>.
   Verifies JWT signature, sets send-updates? to false, redirects to success page.
   Idempotent — unsubscribing twice is harmless."
  [{:keys [query-params db conn]}]
  (let [token (:token query-params)]
    (if (s/blank? token)
      {:status 400 :body "Missing token"}
      (try
        (let [{:keys [email action]} (jwt/unsign token (environ/env :signature))]
          (if (not= "unsubscribe" action)
            {:status 400 :body "Invalid token"}
            (let [{:keys [:db/id]} (user-for-email (d/db conn) email)]
              (if id
                (do @(d/transact conn [{:db/id id :orcpub.user/send-updates? false}])
                    (redirect route-map/unsubscribe-success-route))
                {:status 400 :body "Unknown email"}))))
        (catch Exception _
          {:status 400 :body "Invalid or tampered token"})))))

(defn update-user-preferences
  "PUT handler for /user — update user preferences (currently send-updates?).
   Requires authentication. Only updates fields present in transit-params.
   Re-reads from DB after transact to return authoritative state."
  [{:keys [transit-params db conn identity]}]
  (let [username (:user identity)
        {:keys [:db/id]} (find-user-by-username db username)]
    (if id
      (do (when (contains? transit-params :send-updates?)
            @(d/transact conn [{:db/id id
                                :orcpub.user/send-updates? (boolean (:send-updates? transit-params))}]))
          ;; Re-read from DB after transact for authoritative response
          (let [updated-user (d/entity (d/db conn) id)]
            {:status 200
             :body {:send-updates? (boolean (:orcpub.user/send-updates? updated-user))}}))
      {:status 400 :body {:error "User not found"}})))

(defn do-send-password-reset [user-id email conn request]
  (let [key (str (java.util.UUID/randomUUID))]
    (try
      @(d/transact
        conn
        [{:db/id user-id
          :orcpub.user/password-reset-key key
          :orcpub.user/password-reset-sent (java.util.Date.)}])
      (email/send-reset-email
       (base-url request)
       {:first-and-last-name auth/verification-display-name
        :email email}
       key)
      {:status 200}
      (catch Exception e
        (println "ERROR: Failed to initiate password reset for user" user-id ":" (.getMessage e))
        (throw (ex-info "Unable to initiate password reset. Please try again or contact support."
                        {:error :password-reset-failed
                         :user-id user-id}
                        e))))))

(defn password-reset-expired? [password-reset-sent]
  (and password-reset-sent (before? (instant password-reset-sent) (-> 24 hours ago))))

(defn password-already-reset? [password-reset password-reset-sent]
  (and password-reset (before? (instant password-reset-sent) (instant password-reset))))

(defn send-password-reset [{:keys [query-params db conn scheme headers] :as request}]
  (try
    (let [email (:email query-params)
          {:keys [:orcpub.user/password-reset-sent
                  :orcpub.user/password-reset
                  :db/id] :as user} (user-for-email db email)
          expired? (password-reset-expired? password-reset-sent)
          already-reset? (password-already-reset? password-reset password-reset-sent)]
      (if id
        (do-send-password-reset id email conn request)
        {:status 400 :body {:error :no-account}}))
    (catch Throwable e (prn e) (throw e))))

(defn do-password-reset [conn user-id password]
  (try
    @(d/transact
      conn
      [{:db/id user-id
        :orcpub.user/password (hashers/encrypt (s/trim password))
        :orcpub.user/password-reset (java.util.Date.)
        :orcpub.user/verified? true}])
    {:status 200}
    (catch Exception e
      (println "ERROR: Failed to reset password for user" user-id ":" (.getMessage e))
      (throw (ex-info "Unable to reset password. Please try again or contact support."
                      {:error :password-update-failed
                       :user-id user-id}
                      e)))))

(defn reset-password [{:keys [json-params db conn cookies identity] :as request}]
  (try
    (let [{:keys [password verify-password]} json-params
          username (:user identity)
          {:keys [:db/id] :as user} (first-user-by db username-query username)]
      (cond
        (not= password verify-password) {:status 400 :message "Passwords do not match"}
        (seq (registration/validate-password password)) {:status 400 :message "New password is invalid"}
        :else (do-password-reset conn id password)))
    (catch Throwable t (prn t) (throw t))))

(def font-sizes
  (merge
   (zipmap (map :key skill5e/skills) (repeat 8))
   (zipmap (map (fn [k] (keyword (str (name k) "-save"))) char5e/ability-keys) (repeat 8))
   {:personality-traits 8
    :ideals 8
    :bonds 8
    :flaws 8
    :features-and-traits 8
    :features-and-traits-2 8
    :attacks-and-spellcasting 8
    :backstory 8
    :other-profs 8
    :equipment 8
    :weapon-name-1 8
    :weapon-name-2 8
    :weapon-name-3 8}))

(defn add-spell-cards! [doc spells-known spell-save-dcs spell-attack-mods custom-spells print-spell-card-dc-mod? logo-img bw? bw-faded?]  (try
    (let [custom-spells-map (common/map-by-key custom-spells)
          spells-map (merge spells/spell-map custom-spells-map)
          flat-spells (-> spells-known vals flatten)
          sorted-spells (sort-by
                         (fn [{:keys [class key]}]
                           [(if (keyword? class)
                              (common/kw-to-name class)
                              class)
                            key])
                         flat-spells)
          parts (vec (partition-all 9 flat-spells))
          ;; Load the embedded fonts + build the memoized image embedder ONCE per
          ;; document, not once per page (print-spells/print-backs used to re-parse
          ;; 4 TTFs per call, and each card icon was re-decoded+embedded per spell).
          fonts (pdf/load-fonts doc)
          img (pdf/make-image-loader doc)]
      (doseq [i (range (count parts))
              :let [part (parts i)]]
        (let [page (PDPage.)]
          (.addPage doc page)
          (with-open [cs (PDPageContentStream. doc page)]
            (let [spells (sequence
                          (comp
                           (filter (fn [spell] (spells-map (:key spell))))
                           (map
                            (fn [{:keys [key class]}]
                              {:spell (spells-map key)
                               :class-nm class
                               :dc (spell-save-dcs class)
                               :attack-bonus (spell-attack-mods class)})))
                          part)
                  remaining-desc-lines (vec
                                        (pdf/print-spells
                                         cs
                                         doc
                                         fonts
                                         img
                                         2.5
                                         3.5
                                         spells
                                         i
                                         print-spell-card-dc-mod?
                                         bw?
                                         bw-faded?))
                  back-page (PDPage.)]
              (with-open [back-page-cs (PDPageContentStream. doc back-page)]
                (.addPage doc back-page)
                (pdf/print-backs back-page-cs fonts img 2.5 3.5 remaining-desc-lines i
                                 logo-img)))))))
    (catch Exception e (prn "FAILED ADDING SPELLS CARDS!" e))))

(defn character-pdf-2 [req]
  (let [fields (try
                 (-> req :form-params :body edn/read-string)
                 (catch Exception e
                   (throw (ex-info "Invalid character data format. Unable to parse PDF request."
                                   {:error :invalid-pdf-data}
                                   e))))
        
        {:keys [image-url image-url-failed faction-image-url faction-image-url-failed spells-known custom-spells spell-save-dcs spell-attack-mods print-spell-cards? print-character-sheet-style? print-spell-card-dc-mod? print-card-back-logo? card-back-logo-faded? print-bw? bw-faded? character-name class-level player-name flatten?]} fields

        ;; Printer-friendly mode: monochrome spell-card icons + a forced solid-black
        ;; card-back logo (no color anywhere on the cards). bw-faded? picks the
        ;; icon style: default solid black (white-halo labels) vs faded grayscale.
        bw? (true? print-bw?)
        bw-faded? (true? bw-faded?)

        ;; Resolve the card-back logo to a concrete resource once. nil = off.
        ;; Default is the solid-black mark; the faded brand-orange watermark is an
        ;; opt-in for color printing, and B&W mode overrides it back to solid black.
        card-back-logo-img (when print-card-back-logo?
                             (if (and card-back-logo-faded? (not bw?))
                               "public/image/dmv-mark-faded-orange.png"
                               "public/image/dmv-mark-black.png"))

        sheet6 (str "fillable-char-sheetstyle-" print-character-sheet-style? "-6-spells.pdf")
        sheet5 (str "fillable-char-sheetstyle-" print-character-sheet-style? "-5-spells.pdf")
        sheet4 (str "fillable-char-sheetstyle-" print-character-sheet-style? "-4-spells.pdf")
        sheet3 (str "fillable-char-sheetstyle-" print-character-sheet-style? "-3-spells.pdf")
        sheet2 (str "fillable-char-sheetstyle-" print-character-sheet-style? "-2-spells.pdf")
        sheet1 (str "fillable-char-sheetstyle-" print-character-sheet-style? "-1-spells.pdf")
        sheet0 (str "fillable-char-sheetstyle-" print-character-sheet-style? "-0-spells.pdf")
        input (.openStream (io/resource (cond
                                          (find fields :spellcasting-class-6) sheet6
                                          (find fields :spellcasting-class-5) sheet5
                                          (find fields :spellcasting-class-4) sheet4
                                          (find fields :spellcasting-class-3) sheet3
                                          (find fields :spellcasting-class-2) sheet2
                                          (find fields :spellcasting-class-1) sheet1
                                          :else sheet0)))
        output (ByteArrayOutputStream.)
        filename (cond
                   (and (s/blank? player-name) (s/blank? character-name)) "character.pdf"
                   (s/blank? player-name) (str character-name " - " class-level ".pdf")
                   :else (str player-name " - " character-name " - " class-level ".pdf"))]
        
    ;; PDFBox 3.x: Loader/loadPDF accepts byte[], File, or RandomAccessRead —
    ;; NOT InputStream. Read the resource stream into a byte array first.
    (with-open [doc (Loader/loadPDF (.readAllBytes input))]
      ;; Fillable in every browser by default. The old non-Chrome flattening was a
      ;; workaround for Firefox ignoring NeedAppearances; write-fields! now bakes
      ;; real appearance streams, so values render everywhere AND the form stays
      ;; editable. Clients that want a locked/static PDF pass `:flatten? true`.
      (pdf/write-fields! doc fields (true? flatten?) font-sizes)
      (when (and print-spell-cards? (seq spells-known))
        (add-spell-cards! doc spells-known spell-save-dcs spell-attack-mods custom-spells print-spell-card-dc-mod? card-back-logo-img bw? bw-faded?))

      (when (and image-url
                 (re-matches #"^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]" image-url)
                 (not image-url-failed))
        (case print-character-sheet-style?
          1 (pdf/draw-image! doc (pdf/get-page doc 1) image-url 0.45 1.75 2.35 3.15)
          2 (pdf/draw-image! doc (pdf/get-page doc 1) image-url 0.45 1.75 2.35 3.15)
          3 (pdf/draw-image! doc (pdf/get-page doc 1) image-url 0.45 1.75 2.35 3.15)
          4 (pdf/draw-image! doc (pdf/get-page doc 0) image-url 0.50 0.85 2.35 3.15)))
      (when (and faction-image-url
                 (re-matches #"^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]" faction-image-url)
                 (not faction-image-url-failed))
        (case print-character-sheet-style?
          1 (pdf/draw-image! doc (pdf/get-page doc 1) faction-image-url 5.88 2.4 1.905 1.52)
          2 (pdf/draw-image! doc (pdf/get-page doc 1) faction-image-url 5.88 2.4 1.905 1.52)
          3 (pdf/draw-image! doc (pdf/get-page doc 1) faction-image-url 5.88 2.0 1.905 1.52)
          4 nil))
      (.save doc output))
    (let [a (.toByteArray output)]
      {:status 200
       :headers {"Content-Disposition" (str "inline; filename=\"" filename "\"")}
       :body (ByteArrayInputStream. a)})))

(defn html-response
  [html & [response]]
  (let [merged (merge
                response
                {:status 200
                 :body html
                 :headers {"Content-Type" "text/html"}})]
    merged))

(def user-by-password-reset-key-query
  '[:find ?e
    :in $ ?key
    :where [?e :orcpub.user/password-reset-key ?key]])

(def default-title branding/default-page-title)

(def default-description branding/app-tagline)

(defn default-image-url
  "OG meta image URL. Uses https:// for social sharing compatibility."
  [host]
  (str "https://" host branding/og-image-filename))

(defn index-page-response [{:keys [headers uri csp-nonce] :as request}
                           {:keys [title description image-url]}
                           & [response]]
  (let [host (headers "host")]
    (merge
     response
     {:status 200
      :headers {"Content-Type" "text/html" }
      :body
      (index-page
       {:url (str "http://" host uri)
        :title (or title default-title)
        :description (or description default-description)
        :image (or image-url (default-image-url host))
        :nonce csp-nonce}
       (= "/" uri))})))

(defn default-index-page [request & [response]]
  (index-page-response request {} response))

(defn index [{:keys [headers scheme uri server-name] :as request} & [response]]
  (default-index-page request response))

(defn reset-password-page [{:keys [query-params db conn] :as req}]
  (if-let [key (:key query-params)]
    (let [{:keys [:db/id
                  :orcpub.user/username
                  :orcpub.user/password-reset-key
                  :orcpub.user/password-reset-sent
                  :orcpub.user/password-reset] :as user}
          (first-user-by db user-by-password-reset-key-query key)
          expired? (password-reset-expired? password-reset-sent)
          already-reset? (password-already-reset? password-reset password-reset-sent)]
      (cond
        expired? (redirect route-map/password-reset-expired-route)
        already-reset? (redirect route-map/password-reset-used-route)
        :else (let [token (create-token username (-> 1 hours from-now))]
                (index req {:cookies {"token" token}}))))
    {:status 400
     :body "Key is required"}))

(defn check-field [query value db]
  {:status 200
   :body (-> (d/q query db value)
             seq
             boolean
             str)})

(defn check-username [{:keys [db query-params]}]
  (check-field username-query (:username query-params) db))

(defn check-email [{:keys [db query-params]}]
  (check-field email-query (some-> (:email query-params) s/lower-case) db))

(defn character-for-id [db id]
  (d/pull db '[*] id))

(defn diff-branch [ids]
  (fn [n]
    (or
     (and (map? n)
          (ids (:db/id n)))
     (sequential? n))))

(defn get-new-id [temp-id result]
  (-> result :tempids (get temp-id)))

(defn create-entity [conn username entity owner-prop]
  (try
    (as-> entity $
      (entity/remove-ids $)
      (assoc $
             :db/id "tempid"
             owner-prop username)
      @(d/transact conn [$])
      (get-new-id "tempid" $)
      (d/pull (d/db conn) '[*] $))
    (catch Exception e
      (println "ERROR: Failed to create entity for user" username ":" (.getMessage e))
      (throw (ex-info "Unable to create entity. Please try again or contact support."
                      {:error :entity-creation-failed
                       :username username}
                      e)))))

(defn email-for-username [db username]
  (d/q '[:find ?email .
         :in $ ?username
         :where
         [?e :orcpub.user/username ?username]
         [?e :orcpub.user/email ?email]]
       db
       username))

(defn update-entity [conn username entity owner-prop]
  (try
    (let [id (:db/id entity)
          current (d/pull (d/db conn) '[*] id)
          owner (get current owner-prop)
          email (email-for-username (d/db conn) username)]
      (if ((set [username email]) owner)
        (let [current-ids (entity/db-ids current)
              new-ids (entity/db-ids entity)
              retract-ids (sets/difference current-ids new-ids)
              retractions (map
                           (fn [retract-id]
                             [:db/retractEntity retract-id])
                           retract-ids)
              remove-ids (sets/difference new-ids current-ids)
              with-ids-removed (entity/remove-specific-ids entity remove-ids)
              new-entity (assoc with-ids-removed owner-prop username)
              result @(d/transact conn (concat retractions [new-entity]))]
          (d/pull (d/db conn) '[*] id))
        (throw (ex-info "Not user entity"
                        {:error :not-user-entity}))))
    (catch clojure.lang.ExceptionInfo e
      (throw e))
    (catch Exception e
      (println "ERROR: Failed to update entity for user" username ":" (.getMessage e))
      (throw (ex-info "Unable to update entity. Please try again or contact support."
                      {:error :entity-update-failed
                       :username username
                       :entity-id (:db/id entity)}
                      e)))))

(defn save-entity [conn username e owner-prop]
  (let [without-empty-fields (entity/remove-empty-fields e)]
    (if (:db/id without-empty-fields)
      (update-entity conn username without-empty-fields owner-prop)
      (create-entity conn username without-empty-fields owner-prop))))

(defn owns-entity? [db username entity-id]
  (let [user (find-user-by-username db username)
        username (:orcpub.user/username user)
        email (:orcpub.user/email user)
        entity (d/pull db '[:orcpub.entity.strict/owner] entity-id)
        owner (:orcpub.entity.strict/owner entity)]
    (or (= email owner)
        (= username owner))))

(defn entity-problem [desc actual expected]
  (str desc ", expected: " expected ", actual: " actual))

(defn entity-type-problems [expected-game expected-version expected-type {:keys [::se/type ::se/game ::se/game-version]}]
  (cond-> nil
    (not= expected-game game) (conj (entity-problem "Entity is from the wrong game" game expected-game))
    (not= expected-version game-version) (conj (entity-problem "Entity is from the wrong game version" game-version expected-version))
    (not= expected-type type) (conj (entity-problem "Entity is wrong type" type expected-type))))

(def dnd-e5-char-type-problems (partial entity-type-problems :dnd :e5 :character))

(defn add-dnd-5e-character-tags [character]
  (assoc character
         ::se/game :dnd
         ::se/game-version :e5
         ::se/type :character))

(defn update-character [db conn character username]
  (let [id (:db/id character)]
    (if (owns-entity? db username id)
      (let [current-character (d/pull db '[*] id)
            problems [] #_(dnd-e5-char-type-problems current-character)
            current-valid? (spec/valid? ::se/entity current-character)]
        (when-not current-valid?
          (prn "INVALID CHARACTER FOUND, REPLACING" #_current-character)
          (prn "INVALID CHARACTER EXPLANATION" #_(spec/explain-data ::se/entity current-character)))
        (if (seq problems)
          (throw (ex-info "Character has problems"
                          {:error :character-problems :problems problems}))
          (if-not current-valid?
            (let [new-character (entity/remove-ids character)
                  tx [[:db/retractEntity (:db/id current-character)]
                      (-> new-character
                          (assoc :db/id "tempid"
                                 :orcpub.entity.strict/owner username)
                          add-dnd-5e-character-tags)]
                  result @(d/transact conn tx)]
              (d/pull (d/db conn) '[*] (-> result :tempids (get "tempid"))))
            (let [new-character (entity/remove-orphan-ids character)
                  current-ids (entity/db-ids current-character)
                  new-ids (entity/db-ids new-character)
                  retract-ids (sets/difference current-ids new-ids)
                  retractions (map
                               (fn [retract-id]
                                 [:db/retractEntity retract-id])
                               retract-ids)
                  tx (conj retractions
                           (-> new-character
                               (assoc :orcpub.entity.strict/owner username)
                               add-dnd-5e-character-tags))]
              @(d/transact conn tx)
              (d/pull (d/db conn) '[*] id)))))
      (throw (ex-info "Not user character"
                      {:error :not-user-character})))))

(defn create-new-character
  "Creates a new D&D 5e character.

  Args:
    conn - Database connection
    character - Character data map
    username - Owner username

  Returns:
    Created character entity

  Throws:
    ExceptionInfo on database failure"
  [conn character username]
  (errors/with-db-error-handling :character-creation-failed
    {:username username}
    "Unable to create character. Please try again or contact support."
    (let [result @(d/transact conn
                              [(-> character
                                   (assoc :db/id "tempid"
                                          ::se/owner username)
                                   add-dnd-5e-character-tags)])
          new-id (get-new-id "tempid" result)]
      (d/pull (d/db conn) '[*] new-id))))

(defn clean-up-character [character]
  (if (-> character ::se/values ::char5e/xps string?)
    (update-in character
               [::se/values ::char5e/xps]
               #(try
                  (if-not (s/blank? %)
                    (Long/parseLong %)
                    0)
                  (catch NumberFormatException e 0)))
    character))

(defn do-save-character [db conn transit-params identity]
  (let [character (entity/remove-empty-fields transit-params)
        username (:user identity)
        current-id (:db/id character)]
    (try
      (if-let [data (spec/explain-data ::se/entity character)]
        {:status 400 :body data}
        (let [clean-character (clean-up-character character)
              updated-character (if (:db/id clean-character)
                                  (update-character db conn clean-character username)
                                  (create-new-character conn clean-character username))]
          {:status 200 :body updated-character}))
      (catch clojure.lang.ExceptionInfo e
        (let [data (ex-data e)]
          (case (:error data)
            :character-problems {:status 400 :body (:problems data)}
            :not-user-character {:status 401 :body "You do not own this character"}
            (throw e))))   ; re-throw unrecognised ExceptionInfo (e.g. :db/error from Datomic)
      (catch Exception e (prn "ERROR" e) (throw e)))))

(defn save-character [{:keys [db transit-params body conn identity] :as request}]
  (do-save-character db conn transit-params identity))

(defn owns-item [db username item-id]
  (let [item (d/pull db '[::mi5e/owner] item-id)]
    (= username (::mi5e/owner item))))

(defn save-item [{:keys [db transit-params body conn identity] :as request}]
  (if-let [data (spec/explain-data ::mi5e/magic-item transit-params)]
    {:status 400 :body data}
    (let [username (:user identity)
          result (save-entity conn username transit-params ::mi5e/owner)]
      {:status 200
       :body result})))

(defn get-item [{:keys [db] {:keys [:id]} :path-params}]
  (let [item (d/pull db '[*] id)]
    (if (::mi5e/owner item)
      {:status 200
       :body item}
      {:status 404})))

(defn delete-item
  "Deletes a magic item owned by the user.

  Args:
    request - HTTP request with item ID

  Returns:
    HTTP 200 on success, 401 if not owned

  Throws:
    ExceptionInfo on database failure"
  [{:keys [db conn username] {:keys [:id]} :path-params}]
  (let [{:keys [::mi5e/owner]} (d/pull db '[::mi5e/owner] id)]
    (if (= username owner)
      (errors/with-db-error-handling :item-deletion-failed
        {:item-id id}
        "Unable to delete item. Please try again or contact support."
        @(d/transact conn [[:db/retractEntity id]])
        {:status 200})
      {:status 401})))

(defn item-list [{:keys [db identity]}]
  (let [username (:user identity)
        items (d/q '[:find (pull ?e [*])
                     :in $ ?username
                     :where
                     [?e ::mi5e/owner ?username]]
                   db
                   username)]
    {:status 200 :body (map first items)}))

(defn character-list [{:keys [db identity] :as request}]
  (let [username (:user identity)
        user (find-user-by-username-or-email db username)
        ids (d/q '[:find ?e
                   :in $ [?idents ...]
                   :where
                   [?e ::se/owner ?idents]]
                 db
                 [(:orcpub.user/username user)
                  (:orcpub.user/email user)])
        characters (d/pull-many db '[*] (map first ids))]
    {:status 200 :body characters}))

(defn character-summary-list [{:keys [db body conn identity] :as request}]
  (let [username (:user identity)
        user (find-user-by-username-or-email db username)
        following-ids (map :db/id (:orcpub.user/following user))
        following-usernames (following-usernames db following-ids)
        results (d/q '[:find (pull ?e [:db/id
                                       ::se/summary
                                       ::se/owner])
                       :in $ [?idents ...]
                       :where
                       [?e ::se/owner ?idents]]
                     db
                     (concat
                      [(:orcpub.user/username user)
                       (:orcpub.user/email user)]
                      following-usernames))
        characters (mapv
                    (fn [[{:keys [:db/id ::se/owner ::se/summary]}]]
                      (assoc
                       summary
                       :db/id id
                       ::se/owner (if (= owner (:orcpub.user/email user))
                                    (:orcpub.user/username user)
                                    owner)))
                    results)]
    {:status 200 :body characters}))

(defn follow-user
  "Adds a user to the authenticated user's following list.

  Args:
    request - HTTP request with username to follow

  Returns:
    HTTP 200 on success

  Throws:
    ExceptionInfo on database failure"
  [{:keys [db conn identity] {:keys [user]} :path-params}]
  (let [other-user-id (user-id-for-username db user)
        username (:user identity)
        user-id (user-id-for-username db username)]
    (errors/with-db-error-handling :follow-user-failed
      {:follower username :followed user}
      "Unable to follow user. Please try again or contact support."
      @(d/transact conn [{:db/id user-id
                          :orcpub.user/following other-user-id}])
      {:status 200})))

(defn unfollow-user
  "Removes a user from the authenticated user's following list.

  Args:
    request - HTTP request with username to unfollow

  Returns:
    HTTP 200 on success

  Throws:
    ExceptionInfo on database failure"
  [{:keys [db conn identity] {:keys [user]} :path-params}]
  (let [other-user-id (user-id-for-username db user)
        username (:user identity)
        user-id (user-id-for-username db username)]
    (errors/with-db-error-handling :unfollow-user-failed
      {:follower username :unfollowed user}
      "Unable to unfollow user. Please try again or contact support."
      @(d/transact conn [[:db/retract user-id :orcpub.user/following other-user-id]])
      {:status 200})))

(defn delete-character
  "Deletes a character owned by the authenticated user.

  Args:
    request - HTTP request with character ID in path params

  Returns:
    HTTP 200 on success, 400 for problems, 401 if not owned

  Throws:
    ExceptionInfo on invalid ID or database failure"
  [{:keys [db conn identity] {:keys [id]} :path-params}]
  (let [parsed-id (errors/with-validation :invalid-character-id
                    {:id id}
                    "Invalid character ID format"
                    (Long/parseLong id))
        username (:user identity)
        character (d/pull db '[*] parsed-id)
        problems [] #_(dnd-e5-char-type-problems character)]
    (if (owns-entity? db username parsed-id)
      (if (empty? problems)
        (errors/with-db-error-handling :character-deletion-failed
          {:character-id parsed-id}
          "Unable to delete character. Please try again or contact support."
          @(d/transact conn [[:db/retractEntity parsed-id]])
          {:status 200})
        {:status 400 :body problems})
      {:status 401 :body "You do not own this character"})))

(defn get-character-for-id [db id]
  (let [{:keys [::se/owner] :as character} (d/pull db '[*] id)
        problems [] #_(dnd-e5-char-type-problems character)]
    (if (or (not owner) (seq problems))
      {:status 400 :body problems}
      {:status 200 :body character})))

(defn character-summary-for-id [db id]
  ;; Fixed: bare destructuring outside let silently returned nil
  (let [{:keys [::se/summary]} (d/pull db '[::se/summary {::se/values [::char5e/description ::char5e/image-url]}] id)]
    summary))

(defn get-character
  "Retrieves a character by ID.

  Args:
    request - HTTP request with character ID in path params

  Returns:
    HTTP response with character data

  Throws:
    ExceptionInfo on invalid ID format"
  [{:keys [db] {:keys [:id]} :path-params}]
  (let [parsed-id (errors/with-validation :invalid-character-id
                    {:id id}
                    "Invalid character ID format"
                    (Long/parseLong id))]
    (get-character-for-id db parsed-id)))

(defn report-character-problem
  "User-initiated report that a character failed to load. Auth required. Emails
   the client-supplied diagnostic (char-id, reader error, raw undecodable data)
   to the configured support address, cc'ing the reporting user. Rate-limited
   and gated on email config inside email/send-character-report; returns its
   {:sent? .. :reason ..} so the client can fall back to the copyable report."
  [{:keys [db transit-params identity]}]
  (let [username   (:user identity)
        user       (find-user-by-username-or-email db username)
        user-email (:orcpub.user/email user)
        {:keys [char-id error raw]} transit-params]
    {:status 200
     :body   (email/send-character-report
              {:char-id char-id :user-email user-email :error error :raw raw})}))

(defn get-user [{:keys [db identity]}]
  (let [username (:user identity)
        user (find-user-by-username-or-email db username)]
    {:status 200 :body (user-body db user)}))

(defn delete-user
  "Deletes the authenticated user's account.

  Args:
    request - HTTP request with authenticated user identity

  Returns:
    HTTP 200 on success

  Throws:
    ExceptionInfo on database failure"
  [{:keys [db conn identity]}]
  (let [username (:user identity)
        user (d/q '[:find ?u .
                    :in $ ?username
                    :where [?u :orcpub.user/username ?username]]
                  db
                  username)]
    (errors/with-db-error-handling :user-deletion-failed
      {:username username}
      "Unable to delete user account. Please try again or contact support."
      @(d/transact conn [[:db/retractEntity user]])
      {:status 200})))

(defn rate-limit-remaining-secs
  "Seconds until the user can act again. In the 0–1 min zone (email in transit)
   returns time until the 1-min resend window opens. In the 1–5 min zone (for a
   different email) returns time until the 5-min cooldown expires."
  [verification-sent new-email pending-email]
  (when verification-sent
    (let [elapsed-ms (- (System/currentTimeMillis) (.getTime ^java.util.Date verification-sent))
          ;; If same email, they're waiting for the 1-min resend window to open.
          ;; If different email, they're waiting for the full 5-min cooldown.
          target-ms (if (= new-email pending-email)
                      (* 1 60 1000)
                      (* 5 60 1000))
          remaining-ms (- target-ms elapsed-ms)]
      (when (pos? remaining-ms)
        (int (Math/ceil (/ remaining-ms 1000.0)))))))

(defn email-change-rate-limited? [verification-sent pending-email new-email]
  ;; Only rate-limit if the last key was generated for a pending email change
  ;; (not for initial registration verification).
  ;; Three zones from verification-sent:
  ;;   0–1 min  → too soon, email is in transit (always blocked)
  ;;   1–5 min  → free resend allowed for same email, otherwise blocked
  ;;   5+ min   → open for any request
  (and pending-email
       verification-sent
       (let [elapsed-ms (- (System/currentTimeMillis) (.getTime ^java.util.Date verification-sent))
             same-email? (= new-email pending-email)]
         (cond
           (>= elapsed-ms (* 5 60 1000)) false          ;; past cooldown
           (< elapsed-ms (* 1 60 1000))  true           ;; too soon
           :else                          (not same-email?)))) ;; 1-5 min: resend ok, new email blocked
  )

(defn request-email-change [{:keys [transit-params db conn identity] :as request}]
  (try
    ;; Client sends {:new-email "..."} (confirm-email is validated client-side only)
    (let [new-email (s/lower-case (s/trim (str (:new-email transit-params))))
          username (:user identity)]
      (if (nil? username)
        {:status 400 :body {:error :user-not-found}}
        (let [{:keys [:db/id
                      :orcpub.user/email
                      :orcpub.user/pending-email
                      :orcpub.user/verification-sent] :as user} (find-user-by-username db username)]
          (cond
            (nil? id)
            {:status 400 :body {:error :user-not-found}}

            (registration/bad-email? new-email)
            {:status 400 :body {:error :invalid-email}}

            (= new-email (some-> email s/lower-case))
            {:status 400 :body {:error :same-as-current}}

            (email-change-rate-limited? verification-sent pending-email new-email)
            {:status 429 :body {:error :too-many-requests
                                :retry-after-secs (rate-limit-remaining-secs verification-sent new-email pending-email)}}

            ;; Check no other account already owns this email
            (seq (d/q email-query db new-email))
            {:status 400 :body {:error :email-taken}}

            ;; Free resend: same email, 1–5 min after original send. Re-send with
            ;; existing key and don't update verification-sent (no rolling window).
            (and (= new-email pending-email)
                 verification-sent
                 (let [elapsed (- (System/currentTimeMillis) (.getTime ^java.util.Date verification-sent))]
                   (and (>= elapsed (* 1 60 1000))
                        (< elapsed (* 5 60 1000)))))
            (try
              (send-email-change-verification request
                                              {:email new-email :username username}
                                              (:orcpub.user/verification-key user))
              {:status 200 :body {:pending-email new-email}}
              (catch Throwable e
                (prn "Email resend failed:" (.getMessage e))
                {:status 500 :body {:error :email-send-failed}}))

            :else
            (let [verification-key (str (java.util.UUID/randomUUID))
                  now (java.util.Date.)]
              @(d/transact conn [{:db/id id
                                  :orcpub.user/pending-email new-email
                                  :orcpub.user/verification-key verification-key
                                  :orcpub.user/verification-sent now}])
              ;; Roll back pending-email if verification email fails to send
              (try
                (send-email-change-verification request
                                                {:email new-email :username username}
                                                verification-key)
                {:status 200 :body {:pending-email new-email}}
                (catch Throwable e
                  (errors/log-error "ERROR:" (str "Email send failed, rolling back pending state: " (.getMessage e)))
                  ;; Full rollback: retract all attributes set by the failed attempt
                  @(d/transact conn [[:db/retract id :orcpub.user/pending-email new-email]
                                     [:db/retract id :orcpub.user/verification-key verification-key]
                                     [:db/retract id :orcpub.user/verification-sent now]])
                  {:status 500 :body {:error :email-send-failed}})))))))
    (catch Throwable e (prn e) (throw e))))

(defn character-summary-description [{:keys [::char5e/race-name ::char5e/subrace-name ::char5e/classes]}]
  (str race-name
       " "
       (when subrace-name (str "(" subrace-name ") "))
       " "
       (when (seq classes)
         (s/join
          " / "
          (map
           (fn [{:keys [::char5e/class-name
                        ::char5e/subclass-name
                        ::char5e/level]}]
             (str class-name " (" level ")"))
           classes)))))

(def index-page-paths
  [[route-map/dnd-e5-char-list-page-route]
   [route-map/dnd-e5-char-parties-page-route]
   [route-map/dnd-e5-monster-list-page-route]
   [route-map/dnd-e5-monster-page-route :key ":key"]
   [route-map/dnd-e5-spell-list-page-route]
   [route-map/dnd-e5-spell-page-route :key ":key"]
   [route-map/dnd-e5-spell-builder-page-route]
   [route-map/dnd-e5-monster-builder-page-route]
   [route-map/dnd-e5-selection-builder-page-route]
   [route-map/dnd-e5-background-builder-page-route]
   [route-map/dnd-e5-encounter-builder-page-route]
   [route-map/dnd-e5-combat-tracker-page-route]
   [route-map/dnd-e5-race-builder-page-route]
   [route-map/dnd-e5-subrace-builder-page-route]
   [route-map/dnd-e5-subclass-builder-page-route]
   [route-map/dnd-e5-class-builder-page-route]
   [route-map/dnd-e5-language-builder-page-route]
   [route-map/dnd-e5-invocation-builder-page-route]
   [route-map/dnd-e5-boon-builder-page-route]
   [route-map/dnd-e5-feat-builder-page-route]
   [route-map/dnd-e5-item-list-page-route]
   [route-map/dnd-e5-item-page-route :key ":key"]
   [route-map/dnd-e5-item-builder-page-route]
   [route-map/dnd-e5-char-builder-route]
   [route-map/dnd-e5-newb-char-builder-route]
   [route-map/dnd-e5-my-content-route]
   [route-map/send-password-reset-page-route]
   [route-map/my-account-page-route]
   [route-map/register-page-route]
   [route-map/login-page-route]
   [route-map/verify-sent-route]
   [route-map/password-reset-sent-route]
   [route-map/password-reset-expired-route]
   [route-map/password-reset-used-route]
   [route-map/verify-failed-route]
   [route-map/verify-success-route]
   [route-map/unsubscribe-success-route]
   [route-map/dnd-e5-orcacle-page-route]])

(defn character-page [{:keys [db conn identity headers scheme uri] {:keys [id]} :path-params :as request}]
  (let [host (headers "host")
        {:keys [::se/summary
                ::se/values] :as summary-obj} (character-summary-for-id db id)
        {:keys [::char5e/character-name]} summary
        {:keys [::char5e/description
                ::char5e/image-url]} values]
    (index-page-response request
                         {:title character-name
                          :description (str (character-summary-description summary)
                                            ". "
                                            description)
                          :image-url image-url}
                         {"X-Frame-Options" "ALLOW-FROM https://www.worldanvil.com/"})))

(def header-style
  {:style "color:#2c3445"})

(defn terms-page [body-fn]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (body-fn)})

(defn privacy-policy-page [req]
  (terms-page privacy/privacy-policy))

(defn terms-of-use-page [req]
  (terms-page privacy/terms-of-use))

(defn community-guidelines-page [_]
  (terms-page privacy/community-guidelines))

(defn cookie-policy-page [_]
  (terms-page privacy/cookie-policy))

(defn health-check [_]
  {:status 200 :body "OK"})

(def index-page-routes
  (mapv
   (fn [[route & args]]
     [(apply route-map/path-for route args) :get `default-index-page :route-name route])
   index-page-paths))

(def expanded-index-routes
  (route/expand-routes
   (set index-page-routes)))

(def service-error-handler
  (error-int/error-dispatch [ctx ex]
                            :else (do
                                    (email/send-error-email ctx ex)
                                    (assoc ctx :io.pedestal.interceptor.chain/error ex))))

(def file-hashes (atom {}))

(defn get-file [{:keys [uri] :as request}]
  (ring-resource/resource-request request "public"))

(def get-css get-file)

(def get-js get-file)

(def get-image get-file)

(def get-favicon get-file)

(def webjars-root "META-INF/resources/webjars/")

(defn get-webjar
  "Get a resource containd within a webjar.
   Expects route to be /assets/*"
  [request]
  (let [path (subs (codec/url-decode (req/path-info request)) 1)
        new-path (s/replace-first path #"^assets/" webjars-root)]
    (-> (ring-resp/resource-response new-path)
        (head/head-response request))))

(def routes
  (concat
   (route/expand-routes
    [[["/" {:get `index}
       ^:interceptors [(body-params/body-params) service-error-handler]
       ["/js/*" {:get `get-js}]
       ["/css/*" {:get `get-css}]
       ["/assets/*" {:get `get-webjar}]
       ["/image/*" {:get `get-image}]
       ["/favicon/*" {:get `get-favicon}]
       [(route-map/path-for route-map/register-route)
        {:post `register}]
       [(route-map/path-for route-map/user-route) ^:interceptors [check-auth]
        {:get `get-user
         :put `update-user-preferences
         :delete `delete-user}]
       [(route-map/path-for route-map/user-email-route) ^:interceptors [check-auth]
        {:put `request-email-change}]
       [(route-map/path-for route-map/follow-user-route :user ":user") ^:interceptors [check-auth]
        {:post `follow-user
         :delete `unfollow-user}]

       ;; Items
       [(route-map/path-for route-map/dnd-e5-items-route) ^:interceptors [check-auth]
        {:post `save-item
         :get `item-list}]
       [(route-map/path-for route-map/dnd-e5-item-route :id ":id") ^:interceptors [check-auth parse-id]
        {:delete `delete-item}]
       [(route-map/path-for route-map/dnd-e5-item-route :id ":id") ^:interceptors [parse-id]
        {:get `get-item}]

       ;; Characters
       [(route-map/path-for route-map/dnd-e5-char-list-route) ^:interceptors [check-auth]
        {:post `save-character
         :get `character-list}]
       [(route-map/path-for route-map/dnd-e5-char-summary-list-route) ^:interceptors [check-auth]
        {:get `character-summary-list}]
       [(route-map/path-for route-map/dnd-e5-char-report-route) ^:interceptors [check-auth]
        {:post `report-character-problem}]
       [(route-map/path-for route-map/dnd-e5-char-route :id ":id") ^:interceptors [check-auth]
        {:delete `delete-character}]
       [(route-map/path-for route-map/dnd-e5-char-route :id ":id")
        {:get `get-character}]

       [(route-map/path-for route-map/dnd-e5-char-page-route :id ":id") ^:interceptors [parse-id]
        {:get `character-page}]
       [(route-map/path-for route-map/dnd-e5-char-parties-route) ^:interceptors [check-auth]
        {:post `party/create-party
         :get `party/parties}]
       [(route-map/path-for route-map/dnd-e5-char-party-route :id ":id") ^:interceptors [check-auth parse-id check-party-owner]
        {:delete `party/delete-party}]
       [(route-map/path-for route-map/dnd-e5-char-party-name-route :id ":id") ^:interceptors [check-auth parse-id check-party-owner]
        {:put `party/update-party-name}]
       [(route-map/path-for route-map/dnd-e5-char-party-characters-route :id ":id") ^:interceptors [check-auth parse-id check-party-owner]
        {:post `party/add-character}]
       [(route-map/path-for route-map/dnd-e5-char-party-character-route :id ":id" :character-id ":character-id") ^:interceptors [check-auth parse-id check-party-owner]
        {:delete `party/remove-character}]
       [(route-map/path-for route-map/dnd-e5-char-folders-route) ^:interceptors [check-auth]
        {:post `folder/create-folder
         :get `folder/folders}]
       [(route-map/path-for route-map/dnd-e5-char-folder-route :id ":id") ^:interceptors [check-auth parse-id check-folder-owner]
        {:delete `folder/delete-folder}]
       [(route-map/path-for route-map/dnd-e5-char-folder-name-route :id ":id") ^:interceptors [check-auth parse-id check-folder-owner]
        {:put `folder/update-folder-name}]
       [(route-map/path-for route-map/dnd-e5-char-folder-characters-route :id ":id") ^:interceptors [check-auth parse-id check-folder-owner]
        {:post `folder/add-character}]
       [(route-map/path-for route-map/dnd-e5-char-folder-character-route :id ":id" :character-id ":character-id") ^:interceptors [check-auth parse-id check-folder-owner]
        {:delete `folder/remove-character}]
       [(route-map/path-for route-map/login-route)
        {:post `login}]
       [(route-map/path-for route-map/character-pdf-route)
        {:post `character-pdf-2}]
       [(route-map/path-for route-map/verify-route)
        {:get `verify}]
       [(route-map/path-for route-map/re-verify-route)
        {:get `re-verify}]
       [(route-map/path-for route-map/unsubscribe-route)
        {:get `unsubscribe}]
       [(route-map/path-for route-map/reset-password-route) ^:interceptors [ring/cookies check-auth]
        {:post `reset-password}]
       [(route-map/path-for route-map/reset-password-page-route) ^:interceptors [ring/cookies]
        {:get `reset-password-page}]
       [(route-map/path-for route-map/send-password-reset-route)
        {:get `send-password-reset}]
       [(route-map/path-for route-map/privacy-policy-route)
        {:get `privacy-policy-page}]
       [(route-map/path-for route-map/terms-of-use-route)
        {:get `terms-of-use-page}]
       [(route-map/path-for route-map/community-guidelines-route)
        {:get `community-guidelines-page}]
       [(route-map/path-for route-map/cookies-policy-route)
        {:get `cookie-policy-page}]
       [(route-map/path-for route-map/check-email-route)
        {:get `check-email}]
       [(route-map/path-for route-map/check-username-route)
        {:get `check-username}]
       ["/health"
        {:get `health-check}]]]])
   expanded-index-routes))


