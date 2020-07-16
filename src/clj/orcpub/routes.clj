(ns orcpub.routes
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.test :as test]
            [io.pedestal.http.ring-middlewares :as ring]
            [ring.middleware.cookies :only [wrap-cookies]]
            [ring.middleware.resource :as ring-resource]
            [ring.util.response :as ring-resp]
            [ring.middleware.etag :refer [wrap-etag]]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.interceptor.error :as error-int]
            [io.pedestal.interceptor.chain :refer [terminate]]
            #_[com.stuartsierra.component :as component]
            [buddy.auth.protocols :as proto]
            [buddy.auth.backends :as backends]
            [buddy.sign.jwt :as jwt]
            [buddy.hashers :as hashers]
            [buddy.auth.middleware :refer [authentication-request]]
            [pandect.algo.sha1 :refer [sha1]]
            [clojure.java.io :as io]
            [clj-time.core :as t :refer [hours from-now ago]]
            [clj-time.coerce :as tc :refer [from-date]]
            [clojure.string :as s]
            [clojure.spec.alpha :as spec]
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
            [orcpub.routes.party :as party]
            [orcpub.oauth :as oauth]
            [hiccup.page :as page]
            [environ.core :as environ]
            [clojure.set :as sets])
  (:import (org.apache.pdfbox.pdmodel.interactive.form PDCheckBox PDComboBox PDListBox PDRadioButton PDTextField)

           (org.apache.pdfbox.pdmodel PDDocument PDPage PDPageContentStream)
           (org.apache.pdfbox.pdmodel.graphics.image PDImageXObject)
           (java.io ByteArrayOutputStream ByteArrayInputStream)
           (org.apache.pdfbox.pdmodel.graphics.image JPEGFactory LosslessFactory)
           (org.eclipse.jetty.server.handler.gzip GzipHandler)
           (javax.imageio ImageIO)
           (java.net URL))
  (:gen-class))

(deftype FixedBuffer [^long len])

(def backend (backends/jws {:secret (environ/env :signature)}))

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

(def email-query
  '[:find ?e
    :in $ ?email
    :where [?e :orcpub.user/email ?email]])

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
  {:name :check-auth
   :enter (fn [context]
            (let [request (:request context)
                  updated-request (authentication-request request backend)
                  username (get-in updated-request [:identity :user])]
              (if (and (:identity updated-request)
                       username)
                (assoc context :request (assoc updated-request :username username))
                (terminate-request context 401 "Unauthorized"))))})

(defn party-owner [db id]
  (d/q '[:find ?owner .
         :in $ ?id
         :where [?id :orcpub.dnd.e5.party/owner ?owner]]
       db
       id))

(def id-path [:request :path-params :id])

(def parse-id
  {:name :parse-id
   :enter (fn [context]
            (let [id-str (get-in context id-path)]
              (if (and id-str (re-matches #"\d+" id-str))
                (assoc-in context
                          id-path
                          (Long/parseLong id-str))
                (terminate-request context 400 "Bad ID"))))})


(def check-party-owner
  {:name :check-party-owner
   :enter (fn [context]
            (let [{:keys [identity db] {:keys [id]} :path-params} (:request context)
                  party-owner (party-owner db id)]
              (if (= (:user identity) party-owner)
                context
                (terminate-request context 401 "You don't own this party"))))})

(defn redirect [route-key]
  (ring-resp/redirect (route-map/path-for route-key)))


(defn verification-expired? [verification-sent]
  (t/before? (from-date verification-sent) (-> 24 hours ago)))

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

(defn user-body [db user]
  {:username (:orcpub.user/username user)
   :email (:orcpub.user/email user)
   :following (following-usernames db (map :db/id (:orcpub.user/following user)))})

(defn bad-credentials-response [db username ip]
  (security/add-failed-login-attempt! username ip)
  (if (security/too-many-attempts-for-username? username)
    (login-error errors/too-many-attempts)
    (let [user-for-username (find-user-by-username-or-email db username)]
      (login-error (if (:db/id user-for-username)
                     errors/bad-credentials
                     errors/no-account)))))

(defn create-login-response [db user & [headers]]
  (let [token (create-token (:orcpub.user/username user)
                            (-> 24 hours from-now))]
    {:status 200
     :headers headers
     :body {:user-data (user-body db user)
            :token token}}))

(defn login-response
  [{:keys [json-params db remote-addr] :as request}]
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
                :else (create-login-response db user))))))

(defn login [{:keys [json-params db] :as request}]
  (try
    (let [resp (login-response request)]
      resp)
    (catch Throwable e (do (prn "E" e) (throw e)))))


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

(defn do-verification [request params conn & [tx-data]]
  (let [verification-key (str (java.util.UUID/randomUUID))
        now (java.util.Date.)]
    (do @(d/transact
          conn
          [(merge
            tx-data
            {:orcpub.user/verified? false
             :orcpub.user/verification-key verification-key
             :orcpub.user/verification-sent now})])
        (send-verification-email request params verification-key)
        {:status 200})))

(defn register [{:keys [json-params db conn] :as request}]
  (let [{:keys [username email password send-updates?]} json-params
        username (if username (s/trim username))
        email (if email (s/lower-case (s/trim email)))
        password (if password (s/trim password))
        validation (registration/validate-registration
                    json-params
                    (seq (d/q email-query db email))
                    (seq (d/q username-query db username)))]
    (try
      (if (seq validation)
        {:status 400
         :body validation}
        (do-verification
         request
         json-params
         conn
         {:orcpub.user/email email
          :orcpub.user/username username
          :orcpub.user/password (hashers/encrypt password)
          :orcpub.user/send-updates? send-updates?
          :orcpub.user/created (java.util.Date.)}))
      (catch Throwable e (do (prn e) (throw e))))))

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
                  :db/id] :as user} (user-for-verification-key (d/db conn) key)]
      (if username
        (if verified?
          (redirect route-map/verify-success-route)
          (if (or (nil? verification-sent)
                  (verification-expired? verification-sent))
            (redirect route-map/verify-failed-route)
            (do (d/transact conn [{:db/id id
                                   :orcpub.user/verified? true}])
                (redirect route-map/verify-success-route))))
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
                              {:first-and-last-name "OrcPub Patron"})
                       conn
                       {:db/id id}))))

(defn do-send-password-reset [user-id email conn request]
  (let [key (str (java.util.UUID/randomUUID))]
    @(d/transact
      conn
      [{:db/id user-id
        :orcpub.user/password-reset-key key
        :orcpub.user/password-reset-sent (java.util.Date.)}])
    (email/send-reset-email
     (base-url request)
     {:first-and-last-name "OrcPub Patron"
      :email email}
     key)
    {:status 200}))

(defn password-reset-expired? [password-reset-sent]
  (and password-reset-sent (t/before? (tc/from-date password-reset-sent) (-> 24 hours ago))))

(defn password-already-reset? [password-reset password-reset-sent]
  (and password-reset (t/before? (tc/from-date password-reset-sent) (tc/from-date password-reset))))

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
    (catch Throwable e (do (prn e) (throw e)))))

(defn do-password-reset [conn user-id password]
  @(d/transact
    conn
    [{:db/id user-id
      :orcpub.user/password (hashers/encrypt (s/trim password))
      :orcpub.user/password-reset (java.util.Date.)
      :orcpub.user/verified? true}])
  {:status 200})

(defn reset-password [{:keys [json-params db conn cookies identity] :as request}]
  (try
    (let [{:keys [password verify-password]} json-params
          username (:user identity)
          {:keys [:db/id] :as user} (first-user-by db username-query username)]
      (cond
        (not= password verify-password) {:status 400 :message "Passwords do not match"}
        (seq (registration/validate-password password)) {:status 400 :message "New password is invalid"}
        :else (do-password-reset conn id password)))
    (catch Throwable t (do (prn t) (throw t)))))

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

(defn add-spell-cards! [doc spells-known spell-save-dcs spell-attack-mods custom-spells]  (try
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
          parts (vec (partition-all 9 sorted-spells))]
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
                                         2.5
                                         3.5
                                         spells
                                         i))
                  back-page (PDPage.)]
              (with-open [back-page-cs (PDPageContentStream. doc back-page)]
                (.addPage doc back-page)
                (pdf/print-backs back-page-cs doc 2.5 3.5 remaining-desc-lines i)))))))
    (catch Exception e (prn "FAILED ADDING SPELLS CARDS!" e))))

(defn character-pdf-2 [req]
  (let [fields (-> req :form-params :body clojure.edn/read-string)
        {:keys [image-url image-url-failed faction-image-url faction-image-url-failed spells-known custom-spells spell-save-dcs spell-attack-mods print-spell-cards?]} fields
        input (.openStream (io/resource (cond
                                          (find fields :spellcasting-class-6) "fillable-char-sheet-6-spells.pdf"
                                          (find fields :spellcasting-class-5) "fillable-char-sheet-5-spells.pdf"
                                          (find fields :spellcasting-class-4) "fillable-char-sheet-4-spells.pdf"
                                          (find fields :spellcasting-class-3) "fillable-char-sheet-3-spells.pdf"
                                          (find fields :spellcasting-class-2) "fillable-char-sheet-2-spells.pdf"
                                          (find fields :spellcasting-class-1) "fillable-char-sheet-1-spells.pdf"
                                          :else "fillable-char-sheet-0-spells.pdf")))
        output (ByteArrayOutputStream.)
        user-agent (get-in req [:headers "user-agent"])
        chrome? (re-matches #".*Chrome.*" user-agent)]
    (with-open [doc (PDDocument/load input)]
      (pdf/write-fields! doc fields (not chrome?) font-sizes)
      (if (and print-spell-cards? (seq spells-known))
        (add-spell-cards! doc spells-known spell-save-dcs spell-attack-mods custom-spells))
      (if (and image-url
               (re-matches #"^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]" image-url)
               (not image-url-failed))
        (pdf/draw-image! doc (pdf/get-page doc 1) image-url 0.45 1.75 2.35 3.15))
      (if (and faction-image-url
               (re-matches #"^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]" faction-image-url)
               (not faction-image-url-failed))
        (pdf/draw-image! doc (pdf/get-page doc 1) faction-image-url 5.88 2.4 1.905 1.52))
      (.save doc output))
    (let [a (.toByteArray output)]
      {:status 200 :body (ByteArrayInputStream. a)})))

(defn html-response
  [html & [response]]
  (let [merged (merge
                response
                {:status 200
                 :body html
                 :headers {"Content-Type" "text/html"}})]
    merged))

(defn empty-index [req & [response]]
  (html-response
   (slurp (io/resource "public/blank.html"))
   response))

(def user-by-password-reset-key-query
  '[:find ?e
    :in $ ?key
    :where [?e :orcpub.user/password-reset-key ?key]])

(def default-title
  "The New OrcPub: D&D 5e Character Builder/Generator")

(def default-description
  "Dungeons & Dragons 5th Edition (D&D 5e) character builder/generator and digital character sheet far beyond any other in the multiverse.")

(defn default-image-url [host]
  (str "http://" host "/image/dmv-box-logo.png"))

(defn index-page-response [{:keys [headers uri] :as request}
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
        :image (or image-url (default-image-url host))}
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
  (check-field email-query (:email query-params) db))

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
  (as-> entity $
    (entity/remove-ids $)
    (assoc $
           :db/id "tempid"
           owner-prop username)
    @(d/transact conn [$])
    (get-new-id "tempid" $)
    (d/pull (d/db conn) '[*] $)))

(defn email-for-username [db username]
  (d/q '[:find ?email .
         :in $ ?username
         :where
         [?e :orcpub.user/username ?username]
         [?e :orcpub.user/email ?email]]
       db
       username))

(defn update-entity [conn username entity owner-prop]
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
                      {:error :not-user-entity})))))

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
        (if (not current-valid?)
          (do (prn "INVALID CHARACTER FOUND, REPLACING" #_current-character)
              (prn "INVALID CHARACTER EXPLANATION" #_(spec/explain-data ::se/entity current-character))))
        (if (seq problems)
          {:status 400 :body problems}
          (if (not current-valid?)
            (let [new-character (entity/remove-ids character)
                  tx [[:db/retractEntity (:db/id current-character)]
                      (-> new-character
                          (assoc :db/id "tempid"
                                 :orcpub.entity.strict/owner username)
                          add-dnd-5e-character-tags)]
                  result @(d/transact conn tx)]
              {:status 200
               :body (d/pull (d/db conn) '[*] (-> result :tempids (get "tempid")))})
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
              {:status 200
               :body (d/pull (d/db conn) '[*] id)}))))
      {:status 401 :body "You do not own this character"})))

(defn create-new-character [conn character username]
  (let [result @(d/transact conn
                            [(-> character
                                 (assoc :db/id "tempid"
                                        ::se/owner username)
                                 add-dnd-5e-character-tags)])
        new-id (get-new-id "tempid" result)]
    {:status 200
     :body (d/pull (d/db conn) '[*] new-id)}))

(defn clean-up-character [character]
  (if (-> character ::se/values ::char5e/xps string?)
    (update-in character
               [::se/values ::char5e/xps]
               #(try
                  (if (not (s/blank? %))
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
        (let [clean-character (clean-up-character character)]
          (if (:db/id clean-character)
            (update-character db conn clean-character username)
            (create-new-character conn clean-character username))))
      (catch Exception e (do (prn "ERROR" e) (throw e))))))

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

(defn delete-item [{:keys [db conn username] {:keys [:id]} :path-params}]
  (let [{:keys [::mi5e/owner]} (d/pull db '[::mi5e/owner] id)]
    (if (= username owner)
      (do
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

(defn follow-user [{:keys [db conn identity] {:keys [user]} :path-params}]
  (let [other-user-id (user-id-for-username db user)
        username (:user identity)
        user-id (user-id-for-username db username)]
    @(d/transact conn [{:db/id user-id
                        :orcpub.user/following other-user-id}])
    {:status 200}))

(defn unfollow-user [{:keys [db conn identity] {:keys [user]} :path-params}]
  (let [other-user-id (user-id-for-username db user)
        username (:user identity)
        user-id (user-id-for-username db username)]
    @(d/transact conn [[:db/retract user-id :orcpub.user/following other-user-id]])
    {:status 200}))

(defn delete-character [{:keys [db conn identity] {:keys [id]} :path-params}]
  (let [parsed-id (Long/parseLong id)
        username (:user identity)
        character (d/pull db '[*] parsed-id)
        problems [] #_(dnd-e5-char-type-problems character)]
    (if (owns-entity? db username parsed-id)
      (if (empty? problems)
        (do
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
  {:keys [::se/summary]} (d/pull db '[::se/summary {::se/values [::char5e/description ::char5e/image-url]}] id))

(defn get-character [{:keys [db] {:keys [:id]} :path-params}]
  (let [parsed-id (Long/parseLong id)]
    (get-character-for-id db parsed-id)))

(defn get-user [{:keys [db identity]}]
  (let [username (:user identity)
        user (find-user-by-username-or-email db username)]
    {:status 200 :body (user-body db user)}))

(defn delete-user [{:keys [db conn identity]}]
  (let [username (:user identity)
        user (d/q '[:find ?u .
                    :in $ ?username
                    :where [?u :orcpub.user/username ?username]]
                  db
                  username)]
    @(d/transact conn [[:db/retractEntity user]])
    {:status 200}))

(defn character-summary-description [{:keys [::char5e/race-name ::char5e/subrace-name ::char5e/classes]}]
  (str race-name
       " "
       (if subrace-name (str "(" subrace-name ") "))
       " "
       (if (seq classes)
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
   [route-map/dnd-e5-metamagic-builder-page-route]
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
   (into #{} index-page-routes)))


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

(def get-fa get-file)

(def get-image get-file)

(def get-favicon get-file)

(def routes
  (concat
   (route/expand-routes
    [[["/" {:get `index}
       ^:interceptors [(body-params/body-params) service-error-handler]
       ["/js/*" {:get `get-js}]
       ["/css/*" {:get `get-css}]
       ["/font-awesome-4.7.0/*" {:get `get-fa}]
       ["/image/*" {:get `get-image}]
       ["/favicon/*" {:get `get-favicon}]
       [(route-map/path-for route-map/register-route)
        {:post `register}]
       [(route-map/path-for route-map/user-route) ^:interceptors [check-auth]
        {:get `get-user
         :delete `delete-user}]
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
       [(route-map/path-for route-map/login-route)
        {:post `login}]
       [(route-map/path-for route-map/character-pdf-route)
        {:post `character-pdf-2}]
       [(route-map/path-for route-map/verify-route)
        {:get `verify}]
       [(route-map/path-for route-map/re-verify-route)
        {:get `re-verify}]
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


