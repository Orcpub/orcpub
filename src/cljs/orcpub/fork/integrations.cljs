(ns orcpub.fork.integrations
  "Client-side integration hooks with minimal defaults.
   Fork overrides: replace with full implementations.

   Lifecycle hooks (track-page-view!, on-app-mount!, etc.) are no-ops.
   UI hooks provide basic defaults (e.g. supporter-link shows a Patreon
   button when configured, share-links provides a single email link).

   Companion to integrations.clj (server-side head tags).
   Server-side loads third-party scripts in <head>;
   this namespace provides the in-app component hooks."
  (:require [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch]]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.magic-items :as mi5e]
            [orcpub.dnd.e5.share-bundle :as sb]
            [orcpub.dnd.e5.share-url :as share-url]
            [orcpub.fork.branding :as branding]
            [orcpub.route-map :as routes]))

;; ─── Page View Tracking ─────────────────────────────────────────
;; Called from the :route event handler (events.cljs), NOT from render
;; function bodies (which fire on every React re-render).

(defn track-page-view!
  "Track a page navigation. No-op by default.
   Fork overrides: call your analytics provider here."
  [_route])

;; ─── App Mount Hook ───────────────────────────────────────────────
;; Called from the app root component-did-mount. Handles mount-time
;; integration setup (e.g. user identification, external service init).

(defn on-app-mount!
  "Mount-time integrations. Called once from app root component-did-mount.
   Context map: {:user-tier :free|... :username str :email str}
   Fork overrides: wire analytics user identification, etc."
  [_context])

;; ─── Analytics Custom Variables ─────────────────────────────────
;; Called from render functions that need to tag analytics events
;; with page-specific data.

(defn track-character-list!
  "Tag the character list view with analytics data. No-op by default."
  [_character-count _user-tier])

;; ─── Content Slot ──────────────────────────────────────────────
;; Hook for rendering supplementary content in the page body.
;; Self-gated: accepts user-tier, returns nil by default.
;; Fork overrides: return hiccup for banners, promotions, etc.

(defn content-slot
  "Supplementary content component. Returns nil (renders nothing) by default.
   Fork overrides: return hiccup to render content in designated slots."
  [_user-tier]
  nil)

;; ─── Supporter Link ──────────────────────────────────────────
;; Header supporter area. Shows a supporter button when a URL is configured.
;; Fork overrides: add tier badges, enhanced button styles, etc.

(defn supporter-link
  "Header supporter link. Shows Patreon/supporter button when URL is configured.
   icon-fn: (fn [icon-name size css] hiccup) — render function, unused in default."
  [_user-tier mobile? _icon-fn]
  (when-let [url (not-empty (:patreon branding/social-links))]
    [:a {:href url :target :_blank}
     [:img.h-32.m-l-10.m-b-5.pointer.opacity-7.hover-opacity-full
      {:src (if mobile?
              "https://c5.patreon.com/external/logo/downloads_logomark_color_on_navy.png"
              "https://c5.patreon.com/external/logo/become_a_patron_button.png")}]]))

;; ─── Support Banner ──────────────────────────────────────────
;; Dismissable banner for site announcements or support messages.
;; Fork overrides: return hiccup for donation CTAs, announcements, etc.

(defn support-banner
  "Site announcement/support banner. Returns nil by default.
   Opts: {:srd-message-closed? bool :hide-header-message? bool
          :frame? bool :user-tier keyword :on-dismiss fn}"
  [_opts]
  nil)

;; ─── PDF Sheet Styles ───────────────────────────────────────
;; Returns the list of available character sheet styles for the dropdown.
;; Fork overrides: return tier-gated styles for premium users.

(defn sheet-styles
  "Available character sheet styles. Returns the default sheet only.
   Fork overrides: return additional styles gated by user tier."
  [_user-tier]
  [{:title "Original 5e Character sheet" :value 1}])

;; ─── PDF Options Slot ────────────────────────────────────────
;; Hook for additional content below PDF sheet options.
;; Fork overrides: return hiccup for premium feature promos, etc.

(defn pdf-options-slot
  "Additional content below PDF options. Returns nil by default."
  [_user-tier]
  nil)

;; ─── Sharing ─────────────────────────────────────────────────
;; People share a character by sending its link (WhatsApp, Discord,
;; a DM), so the primary action is "copy the link" — not "open my
;; mail client". On browsers that expose the native share sheet
;; (navigator.share — mobile and most modern desktop browsers), we
;; also offer "Share", which hands the URL to the OS so the user can
;; pick WhatsApp/Messages/Mail/etc. directly.
;; Fork overrides: add frame support, per-network buttons, etc.

(defn- char-url
  "Absolute, shareable URL for a character page (dynamic protocol/host/port
   so it works on localhost, LAN, and prod without hardcoding a domain)."
  [id]
  (str js/window.location.protocol "//"
       js/window.location.hostname
       (when-let [p js/window.location.port] (when (seq p) (str ":" p)))
       (routes/path-for routes/dnd-e5-char-page-route :id id)))

(defn- exec-copy-fallback!
  "Clipboard copy for non-secure contexts where navigator.clipboard is
   absent (plain http). Returns true on success."
  [text]
  (let [ta (js/document.createElement "textarea")]
    (set! (.-value ta) text)
    (set! (.. ta -style -position) "fixed")
    (set! (.. ta -style -opacity) "0")
    (js/document.body.appendChild ta)
    (.select ta)
    (let [ok (try (js/document.execCommand "copy") (catch :default _ false))]
      (js/document.body.removeChild ta)
      ok)))

(defn- copy-to-clipboard!
  "Copy text, then call (on-done success?). Prefers the async Clipboard API,
   falls back to execCommand where it isn't available."
  [text on-done]
  (if (some-> js/navigator .-clipboard)
    (-> (.writeText js/navigator.clipboard text)
        (.then #(on-done true))
        (.catch #(on-done (exec-copy-fallback! text))))
    (on-done (exec-copy-fallback! text))))

(defn- native-share?
  "True when the browser exposes the OS share sheet."
  []
  (boolean (some-> js/navigator .-share)))

(defn share-controls
  "Reactive share cluster for a character: Copy link (+ native Share where the
   browser supports it), both carrying a link with the character's homebrew
   embedded in the URL fragment (share-bundle -> share-url). The embedded URL is
   recomputed only when the character or plugins actually change (identical?
   guard, so it is not rebuilt on every render), which keeps it ready
   synchronously when a button is clicked — gesture-safe for the native share
   sheet and the async clipboard alike. Falls back to the plain character URL for
   a vanilla character, while a payload is still encoding, or when the homebrew is
   too big to fit in a link (:file tier — the recipient then needs the .orcbrew)."
  [id]
  (let [state (r/atom {:tier :plain :url nil :copied? false})
        prev  (atom {})]
    (fn [id]
      (let [character @(subscribe [:character])
            plugins   @(subscribe [:plugins])
            raw-items @(subscribe [::mi5e/custom-items])
            char-name @(subscribe [::char5e/character-name id])
            base      (char-url id)]
        ;; Recompute the embedded URL only when inputs change (identical? = O(1)).
        (when (or (not (identical? character (:character @prev)))
                  (not (identical? plugins (:plugins @prev)))
                  (not (identical? raw-items (:raw-items @prev))))
          (reset! prev {:character character :plugins plugins :raw-items raw-items})
          (let [plugins-bundle (sb/extract-bundle character plugins)
                ;; Match by the item's REAL expanded key(s) — via the app's own
                ;; expand, never a hand-rolled name-to-kw — so keys line up on both
                ;; sides by construction.
                items (sb/used-custom-items character (or raw-items [])
                                            #(mi5e/expand-magic-items [%]))
                container {:plugins plugins-bundle :custom-items items}]
            (if (and (empty? plugins-bundle) (empty? items))
              (swap! state assoc :tier :plain :url base)
              (do
                (swap! state assoc :tier :working :url base)
                (-> (share-url/build-share-payload container)
                    (.then (fn [{:keys [tier payload]}]
                             (swap! state assoc
                                    :tier tier
                                    :url (if payload (str base "#c=" payload) base)))))))))
        (let [{:keys [tier url copied?]} @state
              url  (or url base)
              working? (= tier :working)
              ;; Size caveats, shown once on a successful action. The full content
              ;; always rides along — we never strip it — so notices are only about
              ;; link length / transport, never about lost data.
              note (fn []
                     (case tier
                       :long (dispatch [:show-message
                                        "Link copied. It's a long link — some apps (Discord, SMS) can cut it off; if the recipient sees missing content, send them the .orcbrew file instead."])
                       :file (dispatch [:show-message
                                        "This character has too much custom content to fit in a link. A plain link was copied — share the .orcbrew file so the recipient gets the homebrew."])
                       nil))]
          [:span.flex.align-items-c
           [:button.form-button.h-40.m-l-5.m-t-5.m-b-5
            {:title "Copy a link to this character (custom content included)"
             :disabled working?
             :on-click (fn [_]
                         (copy-to-clipboard!
                          url
                          (fn [ok]
                            (when ok
                              (swap! state assoc :copied? true)
                              (note)
                              (js/setTimeout #(swap! state assoc :copied? false) 1800)))))}
            [:span [:i.fa.f-s-18 {:class (cond copied? "fa-check" working? "fa-spinner" :else "fa-link")}]]
            [:span.m-l-5.header-button-text (cond copied? "Copied!" working? "Preparing…" :else "Copy link")]]
           (when (native-share?)
             [:button.form-button.h-40.m-l-5.m-t-5.m-b-5
              {:title "Share this character (custom content included)"
               :disabled working?
               :on-click (fn [_]
                           (-> (.share js/navigator
                                       #js {:title (str (or char-name "D&D character") " — " branding/app-name)
                                            :url url})
                               (.then (fn [_] (note)))
                               ;; user-cancelled / permission rejections are expected — swallow.
                               (.catch (fn [_] nil))))}
              [:span [:i.fa.f-s-18.fa-share-alt]]
              [:span.m-l-5.header-button-text "Share"]])])))))

(defn share-links
  "Header share cluster (Copy link + native Share) carrying the character's
   homebrew embedded in the link. Returned as a single button-cfg element."
  [id _character-name]
  [[share-controls id]])

(defn share-link-www
  "Single-element share cluster for the character-sheet header."
  [id]
  [share-controls id])
