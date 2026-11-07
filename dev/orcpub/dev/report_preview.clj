(ns orcpub.dev.report-preview
  "Dev tool: preview the character-load report email WITHOUT sending it.

   The front-end input guards make the report path hard to exercise through the
   UI, and you don't want to blast a real inbox to test it. This renders the exact
   message orcpub.email/send-character-report would compose, plus the would-it-send
   gate, for a set of canned scenarios — so recipient, subject, body, how much of
   the character data is exposed, header-injection risk, and the config/throttle
   gates are all inspectable at a glance.

   Run:  lein run -m orcpub.dev.report-preview
   REPL: (preview {:char-id \"1\" :user-email \"a@b.com\" :error \"...\" :raw \"{...}\"})"
  (:require [orcpub.email :as email]
            [orcpub.fork.branding :as branding]
            [clojure.string :as s]))

(def scenarios
  [{:label "typical report"
    :report {:char-id "17592342813688"
             :user-email "player@example.com"
             :error "A single colon is not a valid keyword."
             :raw "{:orcpub.entity.strict/selections {:class [...]} :orcpub.entity.strict/values {...}}"}}
   {:label "anonymous (no reporter email) — cc should be omitted"
    :report {:char-id "42" :user-email "" :error "read-string failed" :raw "{:k :v}"}}
   {:label "HEADER-INJECTION probe — newlines/Bcc in char-id + user-email"
    :report {:char-id "9\nBcc: attacker@evil.example"
             :user-email "victim@example.com\r\nSubject: hijacked"
             :error "x" :raw "{:k :v}"}}
   {:label "oversize raw blob (is it truncated?)"
    :report {:char-id "7" :user-email "a@b.com" :error "e"
             :raw (apply str "{" (repeat 4000 "x"))}}])

(defn- one-line [s] (s/replace (str s) #"[\r\n]" "\\\\n"))

(defn preview
  "Render the composed message + gate decision for one report map."
  [report]
  (let [msg    (email/character-report-message report)
        reason (email/character-report-block-reason report)
        body   (get-in msg [:body 0 :content])]
    (println "  would send? " (if reason (str "NO — " (name reason)) "YES"))
    (println "  to:      " (one-line (:to msg)))
    (println "  cc:      " (if (contains? msg :cc) (one-line (:cc msg)) "(omitted)"))
    (println "  from:    " (one-line (:from msg)))
    (println "  subject: " (one-line (:subject msg)))     ; one-lined so injected newlines are visible
    (println "  body bytes:" (count body))
    (println "  body:")
    (println (->> (s/split-lines body) (map #(str "    | " %)) (s/join "\n")))))

(defn -main [& _]
  (println "\n=== character-load report — dev preview (no email sent) ===\n")
  (println "Current branding/config (drives recipient + gate):")
  (println "  app-name:            " branding/app-name)
  (println "  email-sender-name:   " branding/email-sender-name)
  (println "  APP_SUPPORT_EMAIL:   " (pr-str branding/support-email))
  (println "  report-recipient:    " (pr-str branding/report-recipient)
           (cond
             (s/blank? branding/report-recipient) "  <-- EMPTY: reports blocked (:no-recipient) — set APP_SUPPORT_EMAIL or EMAIL_ERRORS_TO"
             (not= branding/report-recipient branding/support-email) "  (falling back to EMAIL_ERRORS_TO)"
             :else "  (from APP_SUPPORT_EMAIL)"))
  (println "  email-configured?:   " branding/email-configured?)
  (doseq [{:keys [label report]} scenarios]
    (println "\n---------------------------------------------------------------")
    (println "scenario:" label)
    (preview report))
  (println "\n(done — nothing was sent)\n"))
