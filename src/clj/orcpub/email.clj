(ns orcpub.email
  "Email sending functionality with error handling.

  Provides functions for sending verification emails, password reset emails,
  and error notification emails. All operations include comprehensive error
  handling to prevent silent failures when the SMTP server is unavailable."
  (:require [hiccup2.core :as hiccup]
            [postal.core :as postal]
            [environ.core :as environ]
            [clojure.pprint :as pprint]
            [clojure.string :as s]
            [orcpub.route-map :as routes]
            [orcpub.fork.branding :as branding]
            [cuerdas.core :as str]))

(defn- social-links-footer
  "Render email footer social links from branding config. Empty links hidden."
  []
  (let [{:keys [patreon twitter facebook]} branding/social-links]
    (remove nil?
      [(when (seq patreon) [:br])
       (when (seq patreon) (str patreon "  <-- Like what we are doing? Support us here."))
       (when (seq twitter) [:br])
       (when (seq twitter) twitter)
       (when (seq facebook) [:br])
       (when (seq facebook) facebook)
       [:br]])))

(defn verification-email-html [first-and-last-name username verification-url]
  (into
   [:div
    (str "Welcome to " branding/app-name
         (when (seq first-and-last-name) (str ", " first-and-last-name)) "!")
    [:br]
    [:br]
    (str "Your " branding/app-name " account is almost ready, we just need you to verify your email address by visiting the following URL to confirm that you are authorized to use this email address:")
    [:br]
    [:br]
    [:a {:href verification-url} verification-url]
    [:br]
    [:br]
    "Sincerely,"
    [:br]
    [:br]
    (str "The " branding/email-sender-name)]
   (social-links-footer)))

(defn verification-email [first-and-last-name username verification-url]
  [{:type "text/html"
    :content (str (hiccup/html (verification-email-html first-and-last-name username verification-url)))}])

(defn email-change-verification-html
  "Email body for existing users changing their email (distinct from registration)."
  [username verification-url]
  [:div
   (str "Dear " branding/app-name " User,")
   [:br]
   [:br]
   "You requested to change the email address on your account (" username "). "
   "Please visit the following URL to confirm this change:"
   [:br]
   [:br]
   [:a {:href verification-url} verification-url]
   [:br]
   [:br]
   "If you did not request this change, you can safely ignore this email."
   [:br]
   [:br]
   "Sincerely,"
   [:br]
   [:br]
   (str "The " branding/email-sender-name)])

(defn email-change-verification-email [username verification-url]
  [{:type "text/html"
    :content (str (hiccup/html (email-change-verification-html username verification-url)))}])

(defn email-cfg []
  (try
    {:user (environ/env :email-access-key)
     :pass (environ/env :email-secret-key)
     :host (environ/env :email-server-url)
     :port (Integer/parseInt (or (environ/env :email-server-port) "587"))
     :ssl (or (str/to-bool (environ/env :email-ssl)) nil)
     :tls (or (str/to-bool (environ/env :email-tls)) nil)}
    (catch NumberFormatException e
      (throw (ex-info "Invalid email server port configuration. Expected a number."
                      {:error :invalid-port
                       :port (environ/env :email-server-port)}
                      e)))))

(defn emailfrom
  "Returns the configured from-address. Delegates to branding/email-from-address
   which already reads EMAIL_FROM_ADDRESS env var with a fallback default."
  []
  branding/email-from-address)

(defn send-verification-email
  "Sends account verification email to a new user.

  Args:
    base-url - Base URL for the application (for verification link)
    user-map - Map containing :email, :username, and :first-and-last-name
    verification-key - Unique key for email verification

  Returns:
    Postal send-message result

  Throws:
    ExceptionInfo with :verification-email-failed error code if email cannot be sent"
  [base-url {:keys [email username first-and-last-name]} verification-key]
  (try
    (let [result (postal/send-message (email-cfg)
                                      {:from (str branding/email-sender-name " <" (emailfrom) ">")
                                       :to email
                                       :subject (str branding/app-name " Email Verification")
                                       :body (verification-email
                                              first-and-last-name
                                              username
                                              (str base-url (routes/path-for routes/verify-route) "?key=" verification-key))})]
      (when (not= :SUCCESS (:error result))
        (throw (ex-info "Failed to send verification email"
                        {:error :email-send-failed
                         :email email
                         :postal-response result})))
      result)
    (catch Exception e
      (println "ERROR: Failed to send verification email to" email ":" (.getMessage e))
      (throw (ex-info "Unable to send verification email. Please check your email configuration or try again later."
                      {:error :verification-email-failed
                       :email email
                       :username username}
                      e)))))

(defn send-email-change-verification
  "Send a verification email for an email-change request (not registration).

  Args:
    base-url - Base URL for the application (for verification link)
    user-map - Map containing :email and :username
    verification-key - Unique key for email change verification

  Returns:
    Postal send-message result

  Throws:
    ExceptionInfo with :email-change-verification-failed error code if email cannot be sent"
  [base-url {:keys [email username]} verification-key]
  (try
    (let [result (postal/send-message (email-cfg)
                                      {:from (str branding/email-sender-name " <" (emailfrom) ">")
                                       :to email
                                       :subject (str branding/app-name " Email Change Verification")
                                       :body (email-change-verification-email
                                              username
                                              (str base-url (routes/path-for routes/verify-route) "?key=" verification-key))})]
      (when (not= :SUCCESS (:error result))
        (throw (ex-info "Failed to send email change verification"
                        {:error :email-send-failed
                         :email email
                         :postal-response result})))
      result)
    (catch Exception e
      (println "ERROR: Failed to send email change verification to" email ":" (.getMessage e))
      (throw (ex-info "Unable to send email change verification. Please check your email configuration or try again later."
                      {:error :email-change-verification-failed
                       :email email
                       :username username}
                      e)))))

(defn reset-password-email-html [first-and-last-name reset-url]
  (into
   [:div
    (str "Dear " (if (seq first-and-last-name) first-and-last-name (str branding/app-name " User")) ",")
    [:br]
    [:br]
    "We received a request to reset your password, to do so please go to the following URL to complete the reset."
    [:br]
    [:br]
    [:a {:href reset-url} reset-url]
    [:br]
    [:br]
    "If you did NOT request a reset, please do NOT click on the link."
    [:br]
    [:br]
    "Sincerely,"
    [:br]
    [:br]
    (str "The " branding/email-sender-name)]
   (social-links-footer)))

(defn reset-password-email [first-and-last-name reset-url]
  [{:type "text/html"
    :content (str (hiccup/html (reset-password-email-html first-and-last-name reset-url)))}])

(defn send-reset-email
  "Sends password reset email to a user.

  Args:
    base-url - Base URL for the application (for reset link)
    user-map - Map containing :email, :username, and :first-and-last-name
    reset-key - Unique key for password reset

  Returns:
    Postal send-message result

  Throws:
    ExceptionInfo with :reset-email-failed error code if email cannot be sent"
  [base-url {:keys [email username first-and-last-name]} reset-key]
  (try
    (let [result (postal/send-message (email-cfg)
                                      {:from (str branding/email-sender-name " <" (emailfrom) ">")
                                       :to email
                                       :subject (str branding/app-name " Password Reset")
                                       :body (reset-password-email
                                              first-and-last-name
                                              (str base-url (routes/path-for routes/reset-password-page-route) "?key=" reset-key))})]
      (when (not= :SUCCESS (:error result))
        (throw (ex-info "Failed to send password reset email"
                        {:error :email-send-failed
                         :email email
                         :postal-response result})))
      result)
    (catch Exception e
      (println "ERROR: Failed to send password reset email to" email ":" (.getMessage e))
      (throw (ex-info "Unable to send password reset email. Please check your email configuration or try again later."
                      {:error :reset-email-failed
                       :email email
                       :username username}
                      e)))))

(defn unsubscribe-url
  "Build a full unsubscribe URL from a base-url and a pre-signed JWT token.
   Token is generated by routes/unsubscribe-token to avoid circular deps."
  [base-url token]
  (str base-url (routes/path-for routes/unsubscribe-route) "?token=" token))

;; ---------------------------------------------------------------------------
;; Error email — helpers
;; ---------------------------------------------------------------------------

(def ^:private error-throttle
  "Maps fingerprint string → last-sent-ms. Suppresses duplicate error emails."
  (atom {}))

(def ^:private throttle-window-ms (* 5 60 1000))

(defn- root-cause [^Throwable ex]
  (loop [e ex]
    (if-let [c (.getCause e)] (recur c) e)))

(defn- orcpub-frame? [^StackTraceElement f]
  (s/starts-with? (.getClassName f) "orcpub."))

(def ^:private infra-prefixes
  ["org.eclipse.jetty." "io.pedestal." "clojure.lang."
   "java.lang.Thread" "sun.reflect." "java.util.concurrent."
   "clojure.core$"])

(defn- infra-frame? [^StackTraceElement f]
  (let [cls (.getClassName f)]
    (some #(s/starts-with? cls %) infra-prefixes)))

(defn- fmt-frame [^StackTraceElement f]
  (str "    " (.getClassName f) "." (.getMethodName f)
       " (" (.getFileName f) ":" (.getLineNumber f) ")"))

(defn- render-stack [^Throwable ex]
  (let [frames     (seq (.getStackTrace ex))
        app-frames (filter orcpub-frame? frames)
        suppressed (count (filter infra-frame? frames))]
    (str
     (if (seq app-frames)
       (s/join "\n" (map fmt-frame app-frames))
       (let [fallback (->> frames (remove infra-frame?) last)]
         (if fallback
           (str (fmt-frame fallback) "  <- deepest non-infrastructure frame")
           "  (no frames available)")))
     (when (pos? suppressed)
       (str "\n    ... " suppressed " infrastructure frames suppressed")))))

(defn- render-cause-chain [^Throwable ex]
  (let [sb (java.lang.StringBuilder.)]
    (loop [e ex, depth 0]
      (when e
        (.append sb (str (when (pos? depth) "\nCaused by: ")
                         (.getName (.getClass e))
                         ": " (or (.getMessage e) "(no message)") "\n"))
        (.append sb (render-stack e))
        (.append sb "\n")
        (recur (.getCause e) (inc depth))))
    (str sb)))

(defn- throttle-fingerprint [^Throwable ex]
  (let [root       (root-cause ex)
        root-class (.getName (.getClass root))
        frames     (seq (.getStackTrace ex))
        app-frame  (first (filter orcpub-frame? frames))]
    (if app-frame
      (str root-class "+" (.getClassName app-frame) "." (.getMethodName app-frame))
      (let [msg (or (.getMessage root) "")]
        (str root-class "+" (subs msg 0 (min 60 (count msg))))))))

(defn- throttled? [fp]
  (when-let [t (get @error-throttle fp)]
    (< (- (System/currentTimeMillis) t) throttle-window-ms)))

(defn- record-sent! [fp]
  (swap! error-throttle assoc fp (System/currentTimeMillis)))

(def ^:private safe-headers
  #{"user-agent" "referer" "content-type" "accept-language" "cf-ipcountry"
    "x-forwarded-for" "x-real-ip" "cf-ray" "sec-fetch-site" "sec-fetch-mode"
    "x-forwarded-host" "x-forwarded-proto"})

(def ^:private drop-req-keys
  #{:json-params :transit-params :form-params :body :db :conn
    :servlet-request :servlet-response :servlet :url-for
    :async-supported? :identity :character-encoding :protocol
    :path-params :content-length})

(defn- scrub-request [req]
  (-> (apply dissoc req drop-req-keys)
      (update :headers #(select-keys (or % {}) safe-headers))))

(defn- pedestal-wrapper?
  "True when ex-data looks like a Pedestal interceptor error map."
  [data]
  (and (map? data) (contains? data :exception) (contains? data :interceptor)))

(defn- email-subject [^Throwable real-ex request]
  (let [cls    (.getSimpleName (.getClass real-ex))
        msg    (let [m (or (.getMessage real-ex) "(no message)")]
                 (subs m 0 (min 80 (count m))))
        method (some-> (:request-method request) name s/upper-case)
        uri    (or (:uri request) "?")]
    (str "[" branding/app-name "] " cls ": " msg " @ " method " " uri)))

(defn- build-body [request real-ex pedestal-meta]
  (str
   "=== Request ===\n"
   (with-out-str (pprint/pprint (scrub-request request)))
   (when-let [u (:username request)] (str "User: " u "\n"))
   "\n=== Exception ===\n"
   (render-cause-chain real-ex)
   (when (instance? clojure.lang.ExceptionInfo real-ex)
     (when-let [d (ex-data real-ex)]
       (str "\n=== Exception Data ===\n"
            (with-out-str (pprint/pprint d)))))
   (when pedestal-meta
     (str "\n=== Interceptor Context ===\n"
          (with-out-str (pprint/pprint pedestal-meta))))))

;; ---------------------------------------------------------------------------

(defn send-error-email
  "Sends a scrubbed, readable error notification email to the configured admin
  address (EMAIL_ERRORS_TO env var).

  - Strips credentials, cookies, body params, and Datomic objects from request
  - Filters stack trace to orcpub.* frames; falls back to deepest non-infra frame
  - Walks the full cause chain
  - Throttles: one email per unique error fingerprint per 5 minutes
  - Extracts Pedestal interceptor metadata as a separate section"
  [context exception]
  (when (not-empty (environ/env :email-errors-to))
    (let [data-map      (ex-data exception)
          pedestal?     (pedestal-wrapper? data-map)
          real-ex       (if pedestal? (:exception data-map) exception)
          pedestal-meta (when pedestal? (dissoc data-map :exception :exception-type))
          request       (or (:request context) {})
          fp            (throttle-fingerprint real-ex)]
      (if (throttled? fp)
        (println "INFO: Suppressed duplicate error email (fingerprint:" fp ")")
        (do
          (record-sent! fp)
          (try
            (let [result (postal/send-message
                          (email-cfg)
                          {:from    (str branding/app-name " Errors <" (emailfrom) ">")
                           :to      (str (environ/env :email-errors-to))
                           :subject (email-subject real-ex request)
                           :body    [{:type    "text/plain"
                                      :content (build-body request real-ex pedestal-meta)}]})]
              (when (not= :SUCCESS (:error result))
                (println "WARNING: Failed to send error notification email:" (:error result)))
              result)
            (catch Exception e
              (println "ERROR: Failed to send error notification email:" (.getMessage e))
              nil)))))))
;; ---------------------------------------------------------------------------
;; User-initiated "this character won't load" report
;; ---------------------------------------------------------------------------

(def ^:private report-throttle (atom {}))
(def ^:private report-throttle-window-ms (* 10 60 1000)) ; one report per user+char / 10 min

(def ^:private max-report-raw-chars 100000)

(defn- header-safe
  "Strip CR/LF and other control chars from a client-supplied value so it can't
   inject extra email headers (Bcc:, Subject:, ...) when placed in the Subject or
   an address header. Collapses runs of control chars to a single space."
  [s]
  (some-> s str (s/replace #"[\p{Cntrl}]+" " ") s/trim))

(defn character-report-message
  "PURE: the exact postal message map for a character-load report — no send, no
   guards. Extracted so the send path AND dev/test tooling compose the identical
   email (see orcpub.dev.report-preview). char-id (client-supplied) is sanitized
   before it reaches the subject, user-email before the cc, and the raw blob is
   capped so a hostile client can't inject headers or send an unbounded body."
  [{:keys [char-id user-email error raw]}]
  (let [char-id*    (header-safe char-id)
        user-email* (header-safe user-email)
        raw-str     (str raw)
        raw*        (if (> (count raw-str) max-report-raw-chars)
                      (str (subs raw-str 0 max-report-raw-chars)
                           "\n…[truncated " (- (count raw-str) max-report-raw-chars) " chars]")
                      raw-str)
        body (str "A user reported a character that could not be loaded.\n\n"
                  "Character ID: " char-id* "\n"
                  "Reported by:  " (or user-email* "unknown") "\n"
                  "Error:        " error "\n\n"
                  "--- raw character data ---\n" raw*)]
    (cond-> {:from    (str branding/email-sender-name " <" (emailfrom) ">")
             :to      branding/report-recipient
             :subject (str branding/app-name " — character load error (id " char-id* ")")
             :body    [{:type "text/plain" :content body}]}
      (not (s/blank? user-email*)) (assoc :cc user-email*))))

(defn character-report-block-reason
  "Why a report would NOT send, or nil if it would proceed. Reads the branding
   config and the throttle atom (does not mutate). Exposed so the dev preview can
   show the gate decision without sending."
  [{:keys [char-id user-email]}]
  (let [now (System/currentTimeMillis)]
    (cond
      (not branding/email-configured?)     :email-not-configured
      (s/blank? branding/report-recipient) :no-recipient
      (when-let [t (get @report-throttle [user-email char-id])]
        (< (- now t) report-throttle-window-ms)) :throttled
      :else nil)))

(defn send-character-report
  "Emails a user-submitted 'character could not be loaded' report (id, reader
   error, raw undecodable data) to the configured support address, cc'ing the
   reporting user. Returns {:sent? true} on success, else
   {:sent? false :reason <kw>} — when email isn't configured, no support address
   is set, the same user+character was reported within the throttle window, or
   the send failed — so the caller can tell the user to use the copyable report."
  [{:keys [char-id user-email] :as report}]
  (if-let [reason (character-report-block-reason report)]
    {:sent? false :reason reason}
    (do
      (swap! report-throttle assoc [user-email char-id] (System/currentTimeMillis))
      (try
        (let [result (postal/send-message (email-cfg) (character-report-message report))]
          (if (= :SUCCESS (:error result))
            {:sent? true}
            (do (println "WARNING: character report email not sent:" (:error result))
                {:sent? false :reason :send-failed})))
        (catch Exception e
          (println "ERROR: character report email failed:" (.getMessage e))
          {:sent? false :reason :exception})))))
