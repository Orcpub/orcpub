(ns orcpub.fork.branding
  "Centralized branding configuration for fork-neutral deployment.
   All values have sensible defaults; forks override via env vars.

   Server-side (.clj) is the source of truth. Client-side branding
   is delivered via the config bridge: index.clj injects client-config
   as window.__BRANDING__ JSON in <head>, and branding.cljs reads it."
  (:require [environ.core :refer [env]])
  (:import [java.time Year]))

;; ─── App Identity ──────────────────────────────────────────────────

(def app-name
  "Full display name. Used in emails, OG tags, page titles."
  (or (env :app-name) "OrcPub"))

(def app-tagline
  "One-line description for OG/meta tags."
  (or (env :app-tagline)
      "D&D 5e character builder/generator and digital character sheet far beyond any other in the multiverse."))

(def app-url
  "Primary application URL for legal pages and external references. Empty = hidden."
  (or (env :app-url) ""))

(def default-page-title
  "Default <title> and og:title when no page-specific title is set."
  (or (env :app-page-title)
      (str app-name ": D&D 5e Character Builder/Generator")))

;; ─── Logos & Images ────────────────────────────────────────────────

(def logo-path
  "Path to the main SVG logo (splash page, header, privacy page)."
  (or (env :app-logo-path) "/image/orcpub-logo.svg"))

(def og-image-filename
  "Filename for the OG meta image (social sharing preview).
   Combined with the request host to form the full URL."
  (or (env :app-og-image) "/image/orcpub-logo.png"))

;; ─── Copyright ─────────────────────────────────────────────────────

(def copyright-holder
  "Entity name shown in legal footer."
  (or (env :app-copyright-holder) "OrcPub"))

(def copyright-year
  "Copyright year string. Defaults to the current year."
  (or (env :app-copyright-year) (str (.getValue (Year/now)))))

;; ─── Email ─────────────────────────────────────────────────────────

(def email-sender-name
  "Display name for outbound emails (verification, password reset)."
  (or (env :app-email-sender-name) (str app-name " Team")))

(def email-from-address
  "From address for outbound emails (verification, password reset, reports).
   Self-hosters MUST set EMAIL_FROM_ADDRESS to an address on a domain they
   control and have verified with their mail provider (SES/SMTP), or outbound
   mail will be rejected. The fallback is an RFC-2606 reserved placeholder — it
   is intentionally non-deliverable so a misconfigured instance fails loudly
   instead of silently sending from a real domain it does not own."
  (or (env :email-from-address) "no-reply@example.com"))

(def email-configured?
  "Whether outbound email can actually be sent — true only when the operator has
   set a real from-address (not the placeholder). Email-dependent features
   should check this and degrade gracefully (e.g. hide a 'send' button, keep the
   copyable fallback) rather than attempt a doomed send."
  (let [from (or (env :email-from-address) "")]
    (and (seq from) (not= from "no-reply@example.com"))))

;; ─── Support & Help ──────────────────────────────────────────────

(def support-email
  "Contact email shown on privacy page, error messages, etc. Empty = hidden."
  (or (env :app-support-email) ""))

(def report-recipient
  "Where user-submitted 'character won't load' reports are emailed. Prefers the
   public support address (APP_SUPPORT_EMAIL); falls back to the admin error inbox
   (EMAIL_ERRORS_TO) that the app's error notifications already use — so an operator
   who has configured error reporting needs no new setting. Empty (neither set) =
   reports can't send and the UI offers the copyable report instead."
  (if (seq support-email) support-email (or (env :email-errors-to) "")))

(def help-url
  "URL for the help/FAQ page. Empty string = hidden."
  (or (env :app-help-url) ""))

;; ─── Social Links ──────────────────────────────────────────────────
;; Each link appears in the header/footer when non-empty.
;; Set the corresponding env var to a URL to enable, or leave unset to hide.
;; e.g. in .env:  APP_SOCIAL_PATREON=https://www.patreon.com/YourProject
;;                APP_SOCIAL_DISCORD=https://discord.gg/your-invite

(def social-links
  "Map of social platform links. Empty string = hidden."
  {:patreon  (or (env :app-social-patreon)  "")
   :facebook (or (env :app-social-facebook) "")
   :bluesky  (or (env :app-social-bluesky)  "")
   :twitter  (or (env :app-social-twitter)  "")
   :reddit   (or (env :app-social-reddit)   "")
   :discord  (or (env :app-social-discord)  "")})

;; ─── Footer ─────────────────────────────────────────────────────

(def copyright-url
  "URL for copyright holder name in footer. Empty string = plain text."
  (or (env :app-copyright-url) ""))

;; ─── UI Behavior ────────────────────────────────────────────────

(def registration-logo-class
  "CSS class for logo on registration/login page."
  "h-55")

(def restrict-print-to-owner?
  "Whether the print button on character list is restricted to the character owner."
  false)

;; ─── Field Limits ────────────────────────────────────────────────
;; Input field max-length constraints for form validation.

(def field-limits
  "Max-length constraints for form input fields."
  {:notes  (or (some-> (env :app-field-limit-notes) Integer/parseInt) 50000)
   :text   (or (some-> (env :app-field-limit-text) Integer/parseInt) 255)
   :number (or (some-> (env :app-field-limit-number) Integer/parseInt) 7)})

;; ─── Client-Side Config Bridge ───────────────────────────────────
;; index.clj injects this as window.__BRANDING__ JSON in <head>.
;; branding.cljs reads it at runtime for CLJS components.

(defn client-config
  "Map of branding values for CLJS injection. Serialized to JSON by index.clj."
  []
  {:app-name                  app-name
   :logo-path                 logo-path
   :copyright-holder          copyright-holder
   :copyright-year            copyright-year
   :copyright-url             copyright-url
   :support-email             support-email
   :help-url                  help-url
   :social-links              social-links
   :field-limits              field-limits
   :registration-logo-class   registration-logo-class
   :restrict-print-to-owner?  restrict-print-to-owner?})
