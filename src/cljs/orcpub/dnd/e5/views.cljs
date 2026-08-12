(ns orcpub.dnd.e5.views
  (:require [re-frame.core :refer [subscribe dispatch dispatch-sync]]
            [reagent.core :as r]
            [orcpub.route-map :as routes]
            [orcpub.common :as common]
            [orcpub.entity :as entity]
            [orcpub.components :as comps]
            [orcpub.entity-spec :as es]
            [orcpub.pdf-spec :as pdf-spec]
            [orcpub.dice :as dice]
            [orcpub.entity.strict :as se]
            [orcpub.dnd.e5.subs :as subs]
            [re-frame.db :refer [app-db]]
            [orcpub.dnd.e5.equipment-subs]
            [orcpub.dnd.e5.character :as char]
            [orcpub.dnd.e5.backgrounds :as bg]
            [orcpub.dnd.e5.languages :as langs]
            [orcpub.dnd.e5.selections :as selections]
            [orcpub.dnd.e5.races :as races]
            [orcpub.dnd.e5.classes :as classes]
            [orcpub.dnd.e5.feats :as feats]
            [orcpub.dnd.e5.units :as units]
            [orcpub.dnd.e5.party :as party]
            [orcpub.dnd.e5.folder :as folder]
            [orcpub.dnd.e5.character.random :as char-random]
            [orcpub.dnd.e5.character.equipment :as char-equip]
            [orcpub.registration :as registration]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.damage-types :as damage-types]
            [orcpub.dnd.e5.monsters :as monsters]
            [orcpub.dnd.e5.encounters :as encounters]
            [orcpub.dnd.e5.combat :as combat]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.skills :as skills]
            [orcpub.dnd.e5.equipment :as equip]
            [orcpub.dnd.e5.weapons :as weapon]
            [orcpub.dnd.e5.armor :as armor]
            [orcpub.dnd.e5.display :as disp]
            [orcpub.dnd.e5.template :as t]
            [orcpub.dnd.e5.views-2 :as views-2]
            [orcpub.template :as template]
            [orcpub.dnd.e5.options :as opt]
            [orcpub.dnd.e5.events :as events]
            [orcpub.fork.integrations :as integrations]
            [orcpub.fork.branding :as branding]
            [orcpub.fork.splash :as splash]
            [orcpub.fork.user-tier]
            [orcpub.ver :as v]
            [clojure.string :as s]
            [cljs.reader :as reader]
            [orcpub.user-agent :as user-agent]
            [bidi.bidi :as bidi]
            [camel-snake-kebab.core :as csk])
  #_(:require-macros [cljs.core.async.macros :refer [go]]))

;; the `amount` of "uses" an action may have before it warrants
;; using a dropdown instead of a list of checkboxes
(def actions-amount-many 5)

(def text-color "#484848")

(def orange "#f0a100")

(def input-style
  {:height "38px"
   :border-style "solid"
   :border-width "1px"
   :border-radius "3px"
   :font-size "14px"
   :padding-left "10px"
   :color text-color})

(def default-input-style
  (merge
   input-style
   {:border-color "rgba(72,72,72,0.37)"}))

(defn event-value [e]
  (.. e -target -value))

(defn set-value [atom key e]
  (swap! atom assoc key (event-value e)))

(defn validation-messages [messages]
  (when messages
    [:ul.t-a-l.p-l-20.p-r-20.m-b-10
     (doall
      (map-indexed
       (fn [i msg]
         ^{:key i}
         [:li.red (str common/dot-char " " msg)])
       messages))]))

(defn base-input [attrs]
  [:div.m-b-10
   [:div.f-s-10.t-a-l.m-l-10 (:placeholder attrs)]
   [:div.flex.p-l-10.p-l-10.p-r-10
    [:input.flex-grow-1
     (merge
      attrs
      ;; Rem'd out to allow auto fill on use/password
      ;;{:auto-complete :off}
     )]]])

(defn form-input []
  (let [blurred? (r/atom false)]
    (fn [{:keys [title key value messages type on-change]}]
      [:div
       [base-input
        {:name key
         :type type
         :value value
         :placeholder title
         :style input-style
         :class (if (and @blurred? (seq messages))
                       "b-red"
                       "b-gray")
         :on-focus (fn [_] (reset! blurred? false))
         :on-change on-change
         :on-blur (fn [e] (reset! blurred? true))}]
       (when @blurred? (validation-messages messages))])))

(defn export-pdf
  "Returns an onClick handler that generates and submits the PDF.
   plugin-data map is pre-subscribed by the calling component."
  [built-char id plugin-data & [options]]
  (fn [_]
    (let [field (.getElementById js/document "fields-input")]
      (aset field "value" (str (pdf-spec/make-spec built-char id options plugin-data)))
      (.submit (.getElementById js/document "download-form")))))

(defn download-form [built-char]
  [:form.download-form
   {:id "download-form"
    :action (if (and js/window.location
                     (s/starts-with? js/window.location.href "http://localhost"))
              "http://localhost:8890/character.pdf"
              "/character.pdf")
    :method "POST"
    :target "_blank"}
   [:input {:type "hidden" :name "body" :id "fields-input"}]])


(defn svg-icon [icon-name & [size theme-override]]
  (let [theme (or theme-override @(subscribe [:theme]))
        light-theme? (= "light-theme" theme)
        size (or size 32)]
    [:img.svg-icon
     {:style {:height (str size "px")
              :width (str size "px")}
      :class (when light-theme? " opacity-7")
      :src (str (if light-theme? "/image/black/" "/image/") icon-name ".svg")}]))

(def login-style
  {:color "#f0a100"})

(def login-style-menu
  {:background-color "rgba(0,0,0,0.4)"})

(defn dispatch-logout []
  (dispatch [:logout]))

(defn dispatch-route-to-login [e]
  (.stopPropagation e)
  (dispatch [:route-to-login]))

(defn dispatch-route-to-my-account [e]
  (dispatch [:route :my-account]))

;; dead — zero callers
#_(def header-tab-style
  {:width "85px"})

(def active-style {:background-color "rgba(240, 161, 0, 0.7)"})

(def menu-color "#2c3445")

(def header-menu-item-style
  {:position :absolute
   :background-color "#2c3445"
   :z-index 10000
   :top 84
   :right 0})

;; dead — zero callers
#_(def desktop-menu-item-style
  (assoc header-menu-item-style
         :width "100%"))

(def mobile-header-menu-item-style
  (assoc header-menu-item-style
         :top 46))

(def user-menu-style
  {:background-color menu-color
   :z-index 10000
   :position :absolute
   :right 0
   :display :none})

(defn handle-user-menu [e]
  (let [user-header (js/document.getElementById "user-header")
        user-menu (js/document.getElementById "user-menu")
        bounding-rect (.getBoundingClientRect user-header)
        width (.-offsetWidth user-header)
        bottom (.-bottom bounding-rect)
        right (.-right bounding-rect)
        style (.-style user-menu)
        window-width js/document.documentElement.clientWidth]
    (set! (.-display style) "block")))

(defn hide-user-menu [e]
  (let [user-menu (js/document.getElementById "user-menu")
        style (.-style user-menu)]
    (set! (.-display style) "none")))

(defn user-header-view []
  (let [username @(subscribe [:username])
        mobile? @(subscribe [:mobile?])]
    [:div#user-header.pointer.posn-rel
     (when username
       {:on-mouse-over handle-user-menu
        :on-mouse-out hide-user-menu})
     [:div.b-rad-5.flex.align-items-c.p-l-10.p-r-10.p-t-5.p-b-5.f-s-16 {:style login-style-menu }
      [:div.user-icon [svg-icon "orc-head" 35 ""]]
      (if username
        [:span.f-w-b.t-a-r
         (when (not @(subscribe [:mobile?])) [:span.m-r-5 username])]
        [:span.pointer.flex.flex-column.align-items-end
         [:span.white.f-w-b.m-l-5
          {:on-click dispatch-route-to-login}
          [:span "LOGIN"]]])
      (when username
        [:i.fa.m-l-5.fa-caret-down])]
     [:div#user-menu.shadow.f-w-b
      {:style user-menu-style
       :on-click hide-user-menu}
      [:div.p-10.opacity-5.hover-opacity-full
       {:on-click dispatch-logout}
       "LOG OUT"]
      [:div.p-10.opacity-5.hover-opacity-full
       {:on-click dispatch-route-to-my-account}
       "ACCOUNT"]]
     #_(if username
         [:span.f-w-b.t-a-r
          (if (not @(subscribe [:mobile?])) [:span.m-r-5 username])
          [:span.underline.pointer
           {:style login-style
            :on-click dispatch-logout}
           "LOG OUT"]]
         [:span.pointer.flex.flex-column.align-items-end
          [:span.orange.underline.f-w-b.m-l-5
           {:style login-style
            :on-click dispatch-route-to-login}
           [:span "LOGIN"]]])]))

(defn route-fn [route]
  (fn [e]
    (dispatch [:route route])
    (.stopPropagation e)))

(def route-handler (memoize route-fn))

(defn header-tab [title icon on-click disabled active device-type & buttons]
  (let [mobile? (= :mobile device-type)]
    [:div.f-w-b.f-s-14.t-a-c.header-tab.m-l-2.m-r-2.posn-rel
     (cond-> {:on-mouse-down (fn [e]
                               (when (seq buttons)
                                 (let [tab (.. e -currentTarget)]
                                   (when (= tab (.-activeElement js/document))
                                     (.preventDefault e)
                                     (.blur tab)))))
              :on-click (fn [e]
                          (when-not (seq buttons)
                            (on-click e)))
              :style (when active active-style)
              :class (str (if disabled "disabled" "pointer")
                          " "
                          (when (not mobile?) " w-110"))}
       (seq buttons) (assoc :tab-index 0))
     [:div.p-10
      {:class (when (not active) (if disabled "opacity-2" "opacity-6 hover-opacity-full"))}
      (let [size (if mobile? 24 48)] (svg-icon icon size ""))
      (when (not mobile?)
        [:div.title.uppercase title])]
     (when (seq buttons)
       [:div.uppercase.shadow.header-flyout
        {:style (if mobile? mobile-header-menu-item-style header-menu-item-style)}
        (doall
         (map
          (fn [{:keys [name route]}]
            ^{:key name}
            [:div.p-10.opacity-5.hover-opacity-full
             (let [current-route @(subscribe [:route])]
               {:style (when (or (= route current-route)
                               (= route (get current-route :handler))) active-style)
                :on-click (fn [e]
                            (.stopPropagation e)
                            (when-let [tab (.. e -currentTarget -parentElement -parentElement)]
                              (.blur tab))
                            ((route-handler route) e))})
             name])
          buttons))])]))

(defn header-tab2 [title icon on-click disabled active device-type & buttons]
  (let [mobile? (= :mobile device-type)]
    [:div.f-w-b.f-s-14.t-a-c.header-tab.m-l-2.m-r-2.posn-rel
     (cond-> {:on-mouse-down (fn [e]
                               (when (seq buttons)
                                 (let [tab (.. e -currentTarget)]
                                   (when (= tab (.-activeElement js/document))
                                     (.preventDefault e)
                                     (.blur tab)))))
              :on-click (fn [e]
                          (when-not (seq buttons)
                            (when (fn? on-click) (on-click e))))
              :style (if active active-style)
              :class-name (str (if disabled "disabled" "pointer")
                               " "
                               (if (not mobile?) "w-110"))}
       (seq buttons) (assoc :tab-index 0))
     [:div.p-10
      {:class-name (if (not active) (if disabled "opacity-2" "opacity-6 hover-opacity-full"))}
      (let [size (if mobile? 24 48)] (svg-icon icon size ""))
      (if (not mobile?)
        [:div.title.uppercase title])]
     (when (seq buttons)
       [:div.uppercase.shadow.header-flyout
        {:style (if mobile? mobile-header-menu-item-style header-menu-item-style)}
        (doall
         (map
          (fn [{:keys [name route]}]
            ^{:key name}
            [:div.p-10.opacity-5.hover-opacity-full.a-white
             {:on-click (fn [e]
                          (.stopPropagation e)
                          (when-let [tab (.. e -currentTarget -parentElement -parentElement)]
                            (.blur tab)))
              :style (let [current-route @(subscribe [:route])]
                       (when (or (= route current-route)
                                 (= route (get current-route :handler))) active-style))}
             [:a.no-text-decoration {:href route} name]])
          buttons))])]))

(def social-icon-style
  {:color :white
   :font-size "20px"})

(defn social-icon
  "Render a Font Awesome brand icon as a social link."
  [icon link]
  [:a.p-5.opacity-5.hover-opacity-full.main-text-color
   {:style social-icon-style
    :href link :target :_blank}
   [:i.fab
    {:class (str "fa-" icon)}]])

(defn bluesky-icon
  "Bluesky butterfly icon (inline SVG — FA 5.13.1 has no fa-bluesky)."
  [link]
  [:a.p-5.opacity-5.hover-opacity-full.main-text-color
   {:style social-icon-style
    :href link :target :_blank}
   [:svg {:xmlns "http://www.w3.org/2000/svg"
          :viewBox "0 0 568 501"
          :width "20" :height "18"
          :style {:vertical-align "middle" :fill "currentColor"}}
    [:path {:d "M123.121 33.664C188.241 82.553 258.281 181.68 284 234.873c25.719-53.192 95.759-152.32 160.879-201.21C491.866-1.611 568-28.906 568 57.947c0 17.346-9.945 145.713-15.778 166.555-20.275 72.453-94.155 90.933-159.875 79.748C507.222 323.8 536.444 388.56 473.333 453.32c-119.86 122.992-172.272-30.859-185.702-70.281-2.462-7.227-3.614-10.608-3.631-7.733-.017-2.875-1.169.506-3.631 7.733-13.43 39.422-65.842 193.273-185.702 70.281-63.111-64.76-33.89-129.52 80.986-149.07-65.72 11.185-139.6-7.295-159.875-79.748C10.945 203.659 1 75.291 1 57.946 1-28.906 76.135-1.612 123.121 33.664Z"}]]])

(def search-input-style
  {:height "60px"
   :margin-top "0px"
   :border :none
   :font-size "28px"
   :background-color :transparent
   :color :white})

;; dead — zero callers
#_(def search-icon-style
  {:top 6
   :right 25})

(def search-input-parent-style
  {:background-color "rgba(0,0,0,0.3)"})

;; dead — zero callers
#_(def transparent-search-input-style
  (assoc search-input-style :color :transparent))

(defn route-to-default-route []
  (dispatch [:route routes/default-route]))

(defn search-input-keypress [e]
  (when (= "Enter" (.-key e)) (dispatch [:set-search-text (.. e -target -value)])))

(defn set-search-text [e]
  (dispatch [:set-search-text (event-value e)]))

(defn set-search-text-empty [e]
  (dispatch [:set-search-text ""]))

(defn open-orcacle []
  (dispatch [:open-orcacle]))

(defn route-to-character-list-page []
  (dispatch [:route routes/dnd-e5-char-list-page-route]))

(defn route-to-spell-list-page []
  (dispatch [:route routes/dnd-e5-spell-list-page-route]))

(defn route-to-monster-list-page []
  (dispatch [:route routes/dnd-e5-monster-list-page-route]))

(defn route-to-item-list-page []
  (dispatch [:route routes/dnd-e5-item-list-page-route]))

(defn route-to-my-content-page []
  (dispatch [:route routes/dnd-e5-my-content-route]))

(defn route-to-my-encounters-page []
  (dispatch [:route routes/dnd-e5-my-encounters-route]))

(def logo [:a {:href "/" } [:img.h-60.pointer
           {:src branding/logo-path}]])

(defn app-header []
  (let [device-type @(subscribe [:device-type])
        mobile? (= :mobile device-type)
        active-route @(subscribe [:route])]
      [:div#app-header.app-header.flex.flex-column.justify-cont-s-b.white
       [:div.app-header-bar.container
        [:div.content
         [:div.flex.align-items-c.h-100-p
          [:div.flex.justify-cont-s-b.align-items-c.w-100-p.p-l-20.p-r-20.h-100-p
           logo
           (let [search-text @(subscribe [:search-text])
                 search-text? @(subscribe [:search-text?])]
             [:div
              {:class (if mobile? "p-l-10 p-r-10" "p-l-20 p-r-20 flex-grow-1")}
              [:div.b-rad-5.flex.align-items-c
               {:style search-input-parent-style}
               (when (not mobile?)
                 [:div.p-l-20.flex-grow-1
                  [:input.w-100-p.main-text-color
                   {:style search-input-style
                    :value search-text
                    :on-key-press search-input-keypress
                    :on-change set-search-text
                    :placeholder "search"}]])
               [:div.p-r-10.pointer
                {:on-click open-orcacle}
                [svg-icon "magnifying-glass" (if mobile? 32 48) ""]]]])
           [user-header-view]]]]]
       [:div.container
        [:div.content
         [:div.flex.w-100-p.align-items-end
          {:class (if mobile? "justify-cont-s-b" "justify-cont-s-b")}
          [:div
           {:style {:min-width "53px"}}
           [integrations/supporter-link @(subscribe [:user-tier]) mobile? svg-icon]
           (when (not mobile?)
             [:div.main-text-color.p-10
              (when-let [url (not-empty (:facebook branding/social-links))]
                (social-icon "facebook-f" url))
              (when-let [url (not-empty (:bluesky branding/social-links))]
                (bluesky-icon url))
              (when-let [url (not-empty (:twitter branding/social-links))]
                (social-icon "twitter" url))
              (when-let [url (not-empty (:reddit branding/social-links))]
                (social-icon "reddit-alien" url))
              (when-let [url (not-empty (:discord branding/social-links))]
                (social-icon "discord" url))])]
          [:div.flex.m-b-5.m-t-5.justify-cont-s-b.app-header-menu
           [header-tab
            "characters"
            "battle-gear"
            route-to-character-list-page
            false
            (routes/dnd-e5-char-page-routes (or (:handler active-route) active-route))
            device-type
            {:name "Character List"
             :route routes/dnd-e5-char-list-page-route}
            {:name "Character Builder"
             :route routes/dnd-e5-char-builder-route}
            {:name "Parties"
             :route routes/dnd-e5-char-parties-page-route}]
           [header-tab
            "spells"
            "spell-book"
            route-to-spell-list-page
            false
            (routes/dnd-e5-spell-page-routes (or (:handler active-route) active-route))
            device-type
            {:name "Spell List"
             :route routes/dnd-e5-spell-list-page-route}
            {:name "Spell Builder"
             :route routes/dnd-e5-spell-builder-page-route}]
           [header-tab
            "monsters"
            "spiked-dragon-head"
            route-to-monster-list-page
            false
            (routes/dnd-e5-monster-page-routes (or (:handler active-route) active-route))
            device-type
            {:name "Monster List"
             :route routes/dnd-e5-monster-list-page-route}
            {:name "Monster Builder"
             :route routes/dnd-e5-monster-builder-page-route}]
           [header-tab
            "items"
            "all-for-one"
            route-to-item-list-page
            false
            (routes/dnd-e5-item-page-routes
             (or (:handler active-route)
                 active-route))
            device-type
            {:name "Item List"
             :route routes/dnd-e5-item-list-page-route}
            {:name "Item Builder"
             :route routes/dnd-e5-item-builder-page-route}]
           [header-tab
            "encounters"
            "dungeon-gate"
            route-to-my-encounters-page
            false
            (routes/dnd-e5-my-encounters-routes
             (or (:handler active-route)
                 active-route))
            device-type
            {:name "Combat Tracker"
             :route routes/dnd-e5-combat-tracker-page-route}
            {:name "Encounter Builder"
             :route routes/dnd-e5-encounter-builder-page-route}]
           (when (seq splash/header-generator-entries)
             (into [header-tab2
                    "generators"
                    "elven-castle"
                    ""
                    false
                    false
                    device-type]
                   splash/header-generator-entries))
           [header-tab
            "My Content"
            "beer-stein"
            route-to-my-content-page
            false
            (routes/dnd-e5-my-content-routes
             (or (:handler active-route)
                 active-route))
            device-type
            {:name "Content List"
             :route routes/dnd-e5-my-content-route}
            {:name "Feat Builder"
             :route routes/dnd-e5-feat-builder-page-route}
            {:name "Background Builder"
             :route routes/dnd-e5-background-builder-page-route}
            {:name "Language Builder"
             :route routes/dnd-e5-language-builder-page-route}
            {:name "Race Builder"
             :route routes/dnd-e5-race-builder-page-route}
            {:name "Subrace Builder"
             :route routes/dnd-e5-subrace-builder-page-route}
            {:name "Class Builder"
             :route routes/dnd-e5-class-builder-page-route}
            {:name "Subclass Builder"
             :route routes/dnd-e5-subclass-builder-page-route}
            {:name "Eldritch Invocation Builder"
             :route routes/dnd-e5-invocation-builder-page-route}
            {:name "Pact Boon Builder"
             :route routes/dnd-e5-boon-builder-page-route}
            {:name "Selection Builder"
             :route routes/dnd-e5-selection-builder-page-route}]]]]]]))

(def registration-content-style
  {:background-color :white
   :border "1px solid white"
   :color text-color})

(def registration-page-style
  {:background-image "url(/image/login-side.jpg)"
   :background-clip :content-box
   :width "350px"
   :min-height "600px"})

(def registration-left-column-style
  {:flex-direction :column
   :width "435px"})

(def registration-header-style
  {:height "65px"
   :background-color "#1a2532"
   :border-right "1px solid white"})

(defn route-to-default-page []
  (dispatch [:route :default]))

(defn registration-page [content]
  [:div.sans.h-full.flex
   {:style {:flex-direction :column}}
   [:div.flex.justify-cont-s-a.align-items-c.flex-grow-1.h-100-p
    [:div.registration-content
     {:style registration-content-style}
     [:div.flex.h-100-p
      [:div.flex {:style registration-left-column-style}
       [:div.flex.justify-cont-s-a.align-items-c
        {:style registration-header-style}
        [:img.pointer
         {:class branding/registration-logo-class
          :src branding/logo-path
          :on-click route-to-default-page}]]
       [:div.flex-grow-1 content]
       [views-2/legal-footer]]
      [:div.registration-image
       {:style registration-page-style}]]]]])

(def make-event-handler
  (memoize
   (fn [event-kw & args]
     #(dispatch (vec (cons event-kw args))))))

(def make-stop-prop-event-handler
  (memoize
   (fn [event-kw & args]
     (fn [e]
       (dispatch (vec (cons event-kw args)))
       (.stopPropagation e)))))

(defn verify-failed []
  (let [params (r/atom {})]
    (fn []
      (registration-page
       [:div.flex.justify-cont-s-b {:style {:text-align :center
                           :flex-direction :column}}
        [:div.p-20
         [:div.f-w-b.f-s-24.p-b-10
          "Your key has expired."]
         [:div "You must verify your email within 24 hours of registering. Send another verification email by submitting you address here:"]
         [base-input
          {:name :email
           :value (:email @params)
           :type :email
           :placeholder "Email"
           :style default-input-style
           :on-change (partial set-value params :email)}]
         [:button.form-button.m-l-20.m-t-10
          {:style {:height "40px"
                   :width "174px"
                   :font-size "16px"
                   :font-weight "600"}
           :on-click (make-event-handler :re-verify @params)}
          "RESEND"]]]))))

(defn message [message-type message-text close-handler]
  [:div.pointer.f-w-b ;;.h-0.opacity-0.fade-out
   {:on-click close-handler}
   [:div.message
    {:class (case message-type
              :error "bg-red"
              :warning "bg-orange"
              "bg-green")}
    [:span message-text]
    [:i.fa.fa-times]]])

(defn hide-login-message []
  (dispatch [:hide-login-message]))

(defn send-password-reset-page []
  (let [params (r/atom {})]
    (fn [error-message]
      (let [email (:email @params)
            bad-email? (registration/bad-email? email)]
        (registration-page
         [:div.flex.justify-cont-s-b.w-100-p
          {:style {:text-align :center
                   :flex-direction :column}}
          [:div.p-t-10
           (when error-message [:div.red.m-b-20 error-message])
           [:div.f-w-b.f-s-24.p-b-10
            "Send Password Reset Email"]
           [:div.m-b-10 "Submit your email address here and we will send you a link to reset your password."]
           [form-input
            {:title "Email"
             :key :email
             :messages (if bad-email?
                           ["Not a valid email address"]
                           [])
             :type :email
             :value email
             :on-change (partial set-value params :email)}]
           (when @(subscribe [:login-message-shown?])
             [:div.m-t-5.p-r-5.p-l-5
              [message
               :error
               @(subscribe [:login-message])
               hide-login-message]])
           [:button.form-button.m-t-10
            {:style {:height "40px"
                     :width "174px"
                     :font-size "16px"
                     :font-weight "600"}
             :class (when bad-email? "disabled opacity-5 hover-no-shadow")
             :on-click (when (not bad-email?) (make-event-handler :send-password-reset @params))}
            "SUBMIT"]
           [:div.m-t-20
            [:span "Didn't receive reset email? " [:br] [:a.orange {:href "/help/im-not-getting-my-signup-password-reset-email/" :target "_blank"} "whitelist"] " our domain then try it again."]]
           ]])))))

(defn password-reset-expired-page []
  [send-password-reset-page "Your reset link has expired, you must complete the reset within 24 hours. Please use the form below to send another reset email."])

(defn password-reset-used-page []
  [send-password-reset-page "Your reset link has already been used. Please use the form below to send another reset email."])

(defn password-validation-messages [password]
  (-> password
      registration/validate-password
      :password))

(defn password-reset-page []
  (let [params (r/atom {})]
    (fn []
      (let [password (:password @params)
            verify-password (:verify-password @params)
            password-messages (password-validation-messages password)
            different? (not= password verify-password)
            invalid? (or (seq password-messages)
                         different?)]
        (registration-page
         [:div.flex.justify-cont-s-b {:style {:text-align :center
                                              :flex-direction :column}}
          [:div.p-20
           [:div.f-w-b.f-s-24.p-b-10
            "Reset Password"]
           [:div "Create a new password."]
           [form-input {:title "Password"
                        :key :password
                        :value password
                        :type :password
                        ;;:messages password-messages
                        :on-change (fn [e] (swap! params assoc :password (event-value e)))}]
           [form-input {:title "Verify Password"
                        :key :verify-password
                        :value verify-password
                        :type :password
                        :messages (when different? ["Passwords do not match"])
                        :on-change (fn [e] (swap! params assoc :verify-password (event-value e)))}]
           (when @(subscribe [:login-message-shown?])
             [:div.m-t-5.p-r-5.p-l-5 [message
                                      :error
                                      @(subscribe [:login-message])
                                      hide-login-message]])
           [:button.form-button.m-l-20.m-t-10
            {:style {:height "40px"
                     :width "174px"
                     :font-size "16px"
                     :font-weight "600"}
             :class (when invalid? "opacity-5 hover-no-shadow cursor-disabled")
             :on-click (when (not invalid?) (make-event-handler :password-reset @params))}
            "SUBMIT"]]])))))

(defn login-link []
  [:span.underline.f-w-b.m-l-10.pointer.orange
   {:on-click dispatch-route-to-login}
   "LOGIN"])

(defn verify-success []
  (registration-page
   [:div {:style {:text-align :center}}
    [:div {:style {:color orange
                   :font-weight :bold
                   :font-size "36px"
                   :text-transform :uppercase
                   :text-shadow "1px 2px 1px rgba(0,0,0,0.37)"
                   :margin-top "100px"}}
     "Success! Registration is complete"]
    [:div.m-t-20 "You can now"]
    [login-link]]))

(defn password-reset-success []
  (registration-page
   [:div {:style {:text-align :center}}
    [:div {:style {:color orange
                   :font-weight :bold
                   :font-size "36px"
                   :text-transform :uppercase
                   :text-shadow "1px 2px 1px rgba(0,0,0,0.37)"
                   :margin-top "100px"}}
     "Your password has been successfully reset"]
    [:div.m-t-20 "You can now log in"]
    [login-link]]))

(defn unsubscribe-success []
  (registration-page
   [:div {:style {:text-align :center}}
    [:div {:style {:color orange
                   :font-weight :bold
                   :font-size "36px"
                   :text-transform :uppercase
                   :text-shadow "1px 2px 1px rgba(0,0,0,0.37)"
                   :margin-top "100px"}}
     "Unsubscribed"]
    [:div.m-t-20 "You have been successfully unsubscribed from email updates."]
    [:div.m-t-10 "You can re-enable updates at any time from your account settings."]]))

(defn email-sent [text]
  (registration-page
   [:div {:style {:text-align :center}}
    [:div {:style {:color orange
                   :font-weight :bold
                   :font-size "36px"
                   :text-transform :uppercase
                   :text-shadow "1px 2px 1px rgba(0,0,0,0.37)"
                   :margin-top "100px"}}
     "Check your email"]
    [:div.p-20
     text]]))

(defn verify-sent []
  (email-sent
   [:div
    [:span "We sent a verification email to "]
    [:span.f-w-b.red.f-s-18 @(subscribe [:temp-email])]
    [:span ". You must verify to complete registration and the link we sent will only be valid for 24 hours."]
    [:span " "]
    [:span "Remember to check your spam folder."]]))

(defn password-reset-sent []
  (email-sent
   (str "We sent an email to "
        @(subscribe [:temp-email])
        " with a link to reset your password.")))

(defn register-form []
  (let [registration-validation @(subscribe [:registration-validation])
        registration-form @(subscribe [:registration-form])
        send-updates? (not= false (:send-updates? registration-form))
        password-strength (registration/password-strength (:password registration-form))]
    (registration-page
     [:div {:style {:text-align :center}}
      [:div {:style {:color orange
                     :font-weight :bold
                     :font-size "36px"
                     :text-transform :uppercase
                     :text-shadow "1px 2px 1px rgba(0,0,0,0.37)"
                     :margin-top "20px"}}
       "join for free"]
      [:div.f-s-16.m-t-20 "Join now to save your characters and more!"]
      [:div.m-t-10
       [form-input {:title "Username"
                    :key :username
                    :value (:username registration-form)
                    :messages (:username registration-validation)
                    :type :username
                    :on-change (fn [e] (dispatch [:registration-username (event-value e)]))}]
       [form-input {:title "Email"
                    :key :email
                    :value (:email registration-form)
                    :messages (:email registration-validation)
                    :type :email
                    :on-change (fn [e] (dispatch [:registration-email (event-value e)]))}]
       [form-input {:title "Verify Email"
                    :key :verify-email
                    :value (:verify-email registration-form)
                    :messages (:verify-email registration-validation)
                    :type :email
                    :on-change (fn [e] (dispatch [:registration-verify-email (event-value e)]))}]
       [form-input {:title "Password"
                    :key :password
                    :value (:password registration-form)
                    :messages (:password registration-validation)
                    :type :password
                    :on-change (fn [e] (dispatch [:registration-password (event-value e)]))}]
       (let [[color text]
              (cond
                (= 5 password-strength) ["bg-green" "Strong"]
                (< 1 password-strength 5) ["bg-orange" "Moderate"]
                :else ["bg-red" "Weak"])]
         [:div.p-r-10.p-l-10.p-t-5
          [:div
           {:style {:position :relative
                    :height "30px"}}
           [:div.b-rad-5
            {:style {:top 0
                     :left 0
                     :height "30px"
                     :opacity "0.7"
                     :width "100%"
                     :position :absolute}
             :class color}]
           [:div.b-rad-5.password-strength-meter
            {:style {:top 0
                     :left 0
                     :position :absolute
                     :height "30px"
                     :transition "width 1s"
                     :width (str (* 100 (float (/ password-strength 5))) "%")}
             :class color}]
           [:div.main-text-color.p-l-10.b-rad-5
            {:style {:position :absolute
                     :padding-top "6px"}}
             [:span "Password Strength:"]
             [:span.f-w-b.m-l-5 text]]]])
       [:div.m-t-20
        {:style {:text-align :left
                 :margin-left "15px"}}
        [:i.fa.fa-check.f-s-14.pointer
         {:class (if send-updates? "orange" "white")
          :style {:margin-top "-3px"
                  :border-color "#f0a100"
                  :border-style :solid
                  :border-width "1px"
                  :border-bottom-width "3px"}
          :on-click #(dispatch [:registration-send-updates? (not send-updates?)])}]
        [:span.m-l-5 (str "Yes! Send me updates about " branding/app-name)]]
       [:div.m-t-10
        [:div.p-10
         [:span "Already have an account?"]
         (login-link)]
        [:div.m-t-10.m-b-20 [:span "After clicking JOIN A validation email will be sent to the above email address."]]
        [:button.form-button
         {:style {:height "40px"
                  :width "174px"
                  :font-size "16px"
                  :font-weight "600"}
          :class (when (seq registration-validation) "opacity-5 hover-no-shadow cursor-disabled")
          :on-click #(when (empty? registration-validation)
                       (dispatch [:register]))}
         "JOIN"]]]
      [:div.m-t-5.p-r-10.p-l-10
       [:span.f-s-14
        "By clicking JOIN you agree to our"
        [:a.m-l-5 {:href "/terms-of-use" :target :_blank
                   :style {:color text-color}} "Terms of Use"]
        [:span.m-l-5 "and that you've read our"]
        [:a.m-l-5 {:href "/privacy-policy" :target :_blank
                   :style {:color text-color}} "Privacy Policy"]]]])))

(defn route-to-register-page []
  (dispatch [:route routes/register-page-route {:secure true :no-return? true}]))

(defn route-to-reset-password-page []
  (dispatch [:route routes/send-password-reset-page-route {:secure? true :no-return? true}]))

(defn login-page []
  (let [params (r/atom {})]
    (fn []
      (let [login-message-shown? @(subscribe [:login-message-shown?])
            login-message @(subscribe [:login-message])]
        (registration-page
         [:div {:style {:text-align :center}}
          [:div {:style {:color orange
                         :font-weight :bold
                         :font-size "36px"
                         :text-transform :uppercase
                         :text-shadow "1px 2px 1px rgba(0,0,0,0.37)"
                         :margin-top "20px"}}
           "LOGIN"]
          [:div.m-t-10]
          [:div.login-form-inputs
           [form-input {:title "Username or Email"
                        :key :username
                        :value (:username @params)
                        :type :username
                        :on-change #(swap! params assoc :username (event-value %))}]
           [form-input {:title "Password"
                        :key :password
                        :value (:password @params)
                        :type :password
                        :on-change #(swap! params assoc :password (event-value %))}]
           (when login-message-shown?
             [:div.m-t-5.p-r-5.p-l-5 [message
                                      :error
                                      login-message
                                      hide-login-message]])
           [:div.m-t-10
            
            [:button.form-button
             {:style {:height "40px"
                      :width "174px"
                      :font-size "16px"
                      :font-weight "600"}
              :on-click #(dispatch [:login @params true])}
             "LOGIN"]
            [:div.m-t-20
             [:span "Don't have a login? "][:br][:br]
             [:span.orange.underline.pointer
              {:on-click route-to-register-page}
              "REGISTER NOW"]]
            [:div.m-t-20
             [:span "Forgot your password? "][:br][:br]
             [:span.orange.underline.pointer
              {:on-click route-to-reset-password-page}
              "RESET PASSWORD"]]
            
            [:div.m-t-20
             [:span "Didn't receive validation the email? " [:br] [:a.orange {:href "/help/im-not-getting-my-signup-password-reset-email/" :target "_blank"} "Whitelist"] " our domain then reset your password." ]]]]])))))

(def loading-style
  {:position :fixed
   :height "100%"
   :width "100%"
   :top 0
   :bottom 0
   :right 0
   :left 0
   :z-index 100
   :background-color "rgba(0,0,0,0.6)"})

(defn hide-confirmation []
  (dispatch [:hide-confirmation]))

(defn hide-message []
  (dispatch [:hide-message]))

(defn confirm-fn [cfg]
  #(do
     (when (:pre cfg)
       ((:pre cfg)))
     (dispatch [:confirm (:event cfg)])))

(def confirm-handler (memoize confirm-fn))

(defn header [title button-cfgs & {:keys [frame?]}]
  (let [device-type @(subscribe [:device-type])]
    [:div.w-100-p
     [:div.flex.align-items-c.justify-cont-s-b.flex-wrap
      [:div.flex
       [:h1.f-s-36.f-w-b.m-t-5.m-l-10
        {:class (when (not= :mobile device-type) "m-t-21 m-b-20")}
        title]
       (when frame?
         logo)]
      [:div.flex.align-items-c.justify-cont-end.flex-wrap.m-r-10.m-l-10
       (map-indexed
        (fn [i {:keys [title icon on-click style class-name] :as cfg}]
          (if (vector? cfg)
            (with-meta
              cfg
              {:key i})
            ^{:key i}
            [:button.form-button.h-40.m-l-5.m-t-5.m-b-5
             {:on-click on-click
              :class class-name
              :style style}
             [:span
              [:i.fa.f-s-18
               (when icon {:class (str "fa-" icon)})]]
             [:span.m-l-5.header-button-text title]]
              ))
        button-cfgs)]]
     (when @(subscribe [:confirmation-shown?])
       [:div.flex.justify-cont-end.m-r-10.m-b-20.m-l-10
        (let [cfg @(subscribe [:confirmation-cfg])]
          [:div
           [:div.f-w-b (:question cfg)]
           [:div.flex.justify-cont-end.m-t-5
            [:button.form-button
             {:on-click hide-confirmation}
             "CANCEL"]
            [:button.link-button.underline.f-w-b
             {:on-click (confirm-handler cfg)}
             (:confirm-button-text cfg)]]])])
     (when @(subscribe [::char/options-shown?])
       [:div.bg-light.m-b-10 @(subscribe [::char/options-component])])
     (when @(subscribe [:message-shown?])
       [:div.p-b-10.p-r-10.p-l-10.white
        [message
         @(subscribe [:message-type])
         @(subscribe [:message])
         hide-message]])]))

(def debug-data-style {:width "400px" :height "450px"})

(defn clj->json
  [ds]
  (.stringify js/JSON (clj->js ds) nil 2))

(defn debug-data []
  (let [expanded? (r/atom false)]
    (fn []
      [:div.t-a-r
       [:div.orange.pointer.underline
        {:on-click (make-event-handler ::e5/export-all-plugins-pretty-print)
         :title "Development - Download all Orcbrews as Pretty Print, if you click this button it will take a long time to generate the orcbrew.  Click and wait."}
        [:i.fa.fa-cloud-download-alt]]
       [:div.orange.pointer.underline
        {:on-click #(swap! expanded? not)
         :title "Development - Debug Info" }
        [:i.fa.fa-bug {:class (when @expanded? "white")}]]
       (when @expanded?
         [:textarea.m-t-5
          {:read-only true
           :style debug-data-style
           :value (str {:browser (user-agent/browser)
                        :browser-version (user-agent/browser-version)
                        :device-type (user-agent/device-type)
                        :platform (user-agent/platform)
                        :platform-version (user-agent/platform-version)
                        :character (char/to-strict @(subscribe [:character]))})}])
       (when @expanded?
         [:textarea.m-t-5
          {:read-only true
           :style debug-data-style
           :value (clj->json {:browser (user-agent/browser)
                              :browser-version (user-agent/browser-version)
                              :device-type (user-agent/device-type)
                              :platform (user-agent/platform)
                              :platform-version (user-agent/platform-version)
                              :character (char/to-strict @(subscribe [:character]))})}])
       ])))

(defn dice-roll-result [{:keys [total rolls mod raw-mod plus-minus]}]
  [:div.white.f-s-32.flex.align-items-c
   (svg-icon "rolling-dices" 36 "")
   [:div.m-l-10
    [:span.f-w-b total]
    [:span.m-l-10.m-r-10 "="]
    [:span (s/join " + " rolls)]
    (when (not= 0 raw-mod) [:span (if (pos? plus-minus) " + " " - ")])
    (when (not= 0 raw-mod) [:span raw-mod])]])

(defn spell-field [name value]
  [:div
   [:span.f-w-b name ":"]
   [:span.m-l-10 value]])

(defn columns-style [num]
  {:line-height "19px"
   :column-count num
   :-webkit-column-count num
   :-moz-column-count num})

(def two-columns-style
  (columns-style 2))

(def three-columns-style
  (columns-style 3))

(def two-columns-second-empty-style
  {:width "50%"})

(defn paragraphs [str & [single-column?]]
  (let [mobile? @(subscribe [:mobile?])
        ps (s/split str #"\n")
        p-els (doall
               (map-indexed
                (fn [i p]
                  ^{:key i} [:p p])
                ps))]
    (if (or mobile?
            single-column?)
      [:div
       p-els]
      [:div
       {:style (if (= 1 (count ps))
                 two-columns-second-empty-style
                 two-columns-style)}
       p-els])))

(defn requires-attunement [attunement]
  (str
   " (requires attunement"
   (when (-> attunement set :any not)
     (str " by a "
          (common/list-print
           (map
            (fn [kw]
              (case kw
                :good " creature of good alignment"
                :evil " creature of evil alignment"
                (common/kw-to-name kw)))
            attunement) "or")))
   ")"))

(defn item-summary [{:keys [::mi/owner ::mi/name ::mi/type ::mi/item-subtype ::mi/rarity ::mi/attunement] :as item}]
  (when item
    [:div.p-b-20.flex.align-items-c
     (when owner
       [:div.m-r-5 [svg-icon "beer-stein" 24]])
     [:div
      [:span.f-s-24.f-w-b (or (:name item) name)]
      [:div.f-s-16.i.f-w-b.opacity-5
       (str (when type (common/safe-capitalize-kw type))
            (when (keyword? item-subtype)
              (str " (" (common/safe-capitalize-kw item-subtype) ")"))
            ", "
            (if (string? rarity)
              rarity
              (common/kw-to-name rarity))
            (when attunement
              (requires-attunement attunement)))]]]))

(defn item-details [{:keys [::mi/summary ::mi/description ::mi/attunment]} single-column?]
  (when (or summary description)
    (paragraphs (or summary description) single-column?)))

(defn item-component [item & [hide-summary? single-column?]]
  [:div.m-l-10.l-h-19
   (when (not hide-summary?)
     [:div [item-summary item]])
   [:div [item-details item single-column?]]])

(defn magic-item-result [item]
  [:div.white
   [:div.flex
    (svg-icon "orb-wand" 36 "")
    [item-component item]]])

(defn name-result [{:keys [sex race subrace] :as result}]
  [:div.white
   [:span.f-s-24.f-w-b (:name result)]
   [:div
    [:span.f-s-14.opacity-5.i (s/join " " (map (fn [k] (when k (name k))) [sex race subrace]))]]])

(defn tavern-name-result [name]
  [:span.f-s-24.f-w-b.white name])

(defn spell-summary [name level school ritual include-name? & [subheader-size]]
  [:div.p-b-20
   (when include-name? [:span.f-s-24.f-w-b name])
   [:div.i.f-w-b.opacity-5
    {:class (str "f-s-" (or subheader-size 18))}
    (str (when (pos? level)
           (str (common/ordinal level) "-level"))
         " "
         (common/safe-capitalize school) (if ritual " (can be cast as ritual)" "")
         (when (zero? level)
           " cantrip"))]])

(defn spell-component [{:keys [name level school casting-time ritual range duration components description summary page source] :as spell} include-name? & [subheader-size]]
  [:div.m-l-10.l-h-19
   [spell-summary name level school ritual include-name? subheader-size]
   (spell-field "Casting Time" (str casting-time (if ritual " (ritual)" "")))
   (spell-field "Range" range)
   (spell-field "Duration" duration)
   (let [{:keys [verbal somatic material material-component]} components]
     (spell-field "Components" (str (s/join ", " (remove
                                                  nil?
                                                  [(when verbal "V")
                                                   (when somatic "S")
                                                   (when material "M")]))
                                    (when material-component
                                      (str " (" material-component ")")))))
   [:div.m-t-10
    (if description
      (paragraphs description)
      [:div
       (when summary (paragraphs summary))
       #_[:span (str "(" (disp/source-description source page) " for more details)")]])]])

(defn spell-result [spell]
  [:div.white
   [:div.flex
    (svg-icon "spell-book" 36 "")
    [spell-component spell true]]])

(defn spell-results [results]
  [:div.white
   [:div.flex
    (svg-icon "spell-book" 36 36)
    [:div.m-l-10
     (doall
      (map
       (fn [{:keys [key name level school ritual casting-time range duration components description summary page source]}]
         ^{:key name}
         [:div.pointer
          {:on-click (let [spell-page-path (routes/path-for routes/dnd-e5-spell-page-route :key key)
                           spell-page-route (routes/match-route spell-page-path)]
                       (make-event-handler :route spell-page-route))}
          [spell-summary name level school ritual true 14]])
       results))]]])

(defn monster-summary [name size type subtypes alignment]
  [:div.m-r-10
   [:div name]
   [:div.f-s-14.i.opacity-5 (monsters/monster-subheader size type subtypes alignment)]])

(defn monster-results [results]
  [:div.white
   [:div.flex
    (svg-icon "hydra" 36 "")
    [:div.m-l-10
     (doall
      (map
       (fn [{:keys [key name size type subtypes alignment]}]
         ^{:key name}
         [:div.pointer.f-s-24.f-w-600.m-b-20
          {:on-click (fn [_]
                       (let [monster-page-path (routes/path-for routes/dnd-e5-monster-page-route :key key)
                             monster-page-route (routes/match-route monster-page-path)]
                         (dispatch [:route monster-page-route])))}
          [monster-summary name size type subtypes alignment]])
       results))]]])


(defn print-bonus-map [m]
  (s/join ", "
          (map
           (fn [[k v]] (str (common/safe-capitalize-kw k) " " (common/bonus-str v)))
           m)))

(def max-width-300
  {:max-width "300px"})


(defn monster-component [{:keys [name description size type subtypes hit-points alignment armor-class armor-notes speed saving-throws skills damage-vulnerabilities damage-resistances damage-immunities condition-immunities senses languages challenge traits actions legendary-actions source page] :as monster}]
  (let [traits-by-type (group-by :type traits)
        traits (traits-by-type nil)
        actions (concat actions (traits-by-type :action))
        legendary (traits-by-type :legendary-action)
        legendary-actions (if (seq legendary)
                            (update legendary-actions :actions concat legendary)
                            legendary-actions)]
    [:div.m-l-10.l-h-19
     (when (not @(subscribe [:mobile?])) {:style two-columns-style})
     [:span.f-s-24.f-w-b.m-b-20 name]
     [:div.f-s-18.i.f-w-b (monsters/monster-subheader size type subtypes alignment)]
     (spell-field "Armor Class" (str armor-class (when armor-notes (str " (" armor-notes ")"))))
     (let [{:keys [mean die-count die modifier]} hit-points]
       (spell-field "Hit Points" (str die-count
                                      "d"
                                      die
                                      (when modifier (common/mod-str modifier))
                                      (let [mean
                                            (or mean
                                                (when (and die die-count)
                                                  (dice/dice-mean-round-down
                                                   die-count
                                                   die
                                                   (or modifier 0))))]
                                        (when mean (str " (" mean ")"))))))
     (spell-field "Speed" speed)
     [:div.m-t-10.flex.justify-cont-s-a.m-b-10
      {:style max-width-300}
      (doall
       (map
        (fn [ability-key]
          ^{:key ability-key}
          [:div.t-a-c.p-5
           [:div.f-w-b.f-s-14 (s/upper-case (common/safe-name ability-key))]
           (let [ability-value (get monster ability-key)]
             [:div ability-value " (" (common/bonus-str (opt/ability-bonus ability-value)) ")"])])
        [:str :dex :con :int :wis :cha]))]
     (when (seq saving-throws)
       (spell-field "Saving Throws" (print-bonus-map saving-throws)))
     (when skills (spell-field "Skills" (print-bonus-map skills)))
     (when damage-vulnerabilities (spell-field "Damage Vulnerabilities" damage-vulnerabilities))
     (when damage-resistances (spell-field "Damage Resistances" damage-resistances))
     (when damage-immunities (spell-field "Damage Immunities" damage-immunities))
     (when condition-immunities (spell-field "Condition Immunities" condition-immunities))
     (when senses (spell-field "Senses" senses))
     (when languages (spell-field "Languages" languages))
     (when challenge (spell-field "Challenge" (str
                                             (case challenge
                                               0.125 "1/8"
                                               0.25 "1/4"
                                               0.5 "1/2"
                                               challenge)
                                             " ("
                                             (monsters/challenge-ratings challenge)
                                             " XP)")))
     (when traits
       [:div.m-t-20
        (doall
         (map-indexed
          (fn [i {:keys [name description]}]
            ^{:key i}
            [:div.m-t-10.wsp-prw (spell-field name description)])
          traits))])
     (when actions
       [:div.m-t-20
        [:div.i.f-w-b.f-s-18 "Actions"]
        [:div
         (doall
          (map-indexed
           (fn [i {:keys [name notes description]}]
             ^{:key i}
             [:div.m-t-10.wsp-prw (spell-field (str name " " notes) description)])
           actions))]])
     (when legendary-actions
       [:div.m-t-20
        [:div.i.f-w-b.f-s-18 "Legendary Actions"]
        (when (:description legendary-actions)
          [:div (:description legendary-actions)])
        (when (:actions legendary-actions)
          [:div
           (doall
            (map-indexed
             (fn [i {:keys [name notes description]}]
               ^{:key i}
               [:div.m-t-10 (spell-field (str name " " notes) description)])
             (:actions legendary-actions)))])])
     (when description
       [:div.m-t-10 (str description)])]))

(defn monster-result [monster]
  [:div.white
   [:div.flex
    (svg-icon "hydra" 36 "")
    [monster-component monster]]])

(defn search-results []
  (when-let [{{:keys [result] :as top-result} :top-result
            results :results
            :as search-results}
           @(subscribe [:search-results])]
    [:div
     (when top-result
       [:div.p-20.m-b-20
        (let [type (:type top-result)]
          (case type
            :dice-roll (dice-roll-result result)
            :spell (spell-result result)
            :monster (monster-result result)
            :magic-item (magic-item-result result)
            :name (name-result result)
            :tavern-name (tavern-name-result result)
            nil))])
     (when (seq results)
       (doall
        (map
         (fn [{:keys [type results]}]
           ^{:key type}
           [:div.p-20
            (case type
              :spell (spell-results results)
              :monster (monster-results results))])
         results)))]))

(def oracle-frame-style
  {:overflow-y :scroll
   :position :fixed
   :z-index 1
   :background-color "rgba(0,0,0,0.95)"
   :top 0
   :left 0
   :right 0
   :bottom 0})

(def close-icon-style
  {:top 0
   :right 0
   :padding "17px"})

(def close-button-style
  {:position :fixed
   :top 20
   :right 40})

(def orcacle-input-style
  (merge search-input-style
         {:background-color "rgba(255,255,255,0.1)"}))

(defn close-orcacle []
  (dispatch [:close-orcacle]))



(defn orcacle []
  (let [search-text @(subscribe [:search-text])]
    [:div.flex.flex-column.h-100-p.white
     {:style oracle-frame-style}
     [:i.fa.fa-times-circle.f-s-24.orange.pointer
      {:on-click close-orcacle
       :style close-button-style}]
     [:div
      [:div.flex.justify-cont-s-a.m-t-10
       [:div.flex.align-items-c.pointer
        {:on-click close-orcacle}
        [:span.f-s-32 "Orcacle"]
        [:div.m-l-10 (svg-icon "hood" 48 "")]]]]
     [:div
      [:div.p-10
       [:div.posn-rel
        [:input.input.orcacle-input
         {:value search-text
          :on-change set-search-text
          :on-key-press search-input-keypress
          :style orcacle-input-style}]
        [:i.fa.fa-times.posn-abs.f-s-24.pointer
         {:style close-icon-style
          :on-click set-search-text-empty}]]
       [:span.f-s-14.i.opacity-5 "\"8d10 + 2\", \"magic missile\", \"kobold\", \"female calishite name\", \"tavern name\", etc."]]
      [:div.flex-grow-1
       [search-results]]]]))

(def srd-link
  [:a.orange {:href "/dnld/SRD-OGL_V5.1.pdf" :target "_blank"} "the 5e SRD-OGL 5.1"])

(defn current-year []
  (.getFullYear (js/Date.))) 

;; ------------------------------------------------------------------
;; A helper that lets us embed a raw string of HTML inside Reagent.
;; (If you’re using reagent‑core ≥1.2, `:raw` is built‑in; otherwise
;; you can use the small shim below.)
;; ------------------------------------------------------------------
;; Render *any* raw HTML string.  
(defn raw-html [html]
  ;; Note: no `:>` or `js/React.createElement`.  
  [:div {:dangerouslySetInnerHTML #js {:__html html}}])

(defn content-page [title button-cfgs content & {:keys [hide-header-message? frame?]}]
  ;; Plain atom (not r/atom) mirrors the :orcacle-open? subscription value
  ;; for the scroll handler, which runs as a DOM event listener outside
  ;; Reagent's reactive context. Synced from the render fn below.
  (let [orcacle-open?* (atom false)
        on-scroll (fn [e]
                    (when-not @orcacle-open?*
                      (let [app-header (js/document.getElementById "app-header")
                            header-height (.-offsetHeight app-header)
                            scroll-top (.-scrollTop (.-documentElement (.-target e)))
                            sticky-header (js/document.getElementById "sticky-header")]
                        (if (>= scroll-top header-height)
                          (set! (.-display (.-style sticky-header)) "block")
                          (set! (.-display (.-style sticky-header)) "none")))))]
    
    (r/create-class
     {:component-did-mount (fn [comp]
                             ;; Read directly from app-db — lifecycle methods are
                             ;; not reactive contexts, so subscribe warns here.
                             (let [user-data (-> @app-db :user-data :user-data)]
                               (integrations/on-app-mount!
                                {:user-tier (if (:patron user-data)
                                             (or (some-> user-data :patron-tier keyword) :patron)
                                             :free)
                                 :username  (:username user-data)
                                 :email     (:email user-data)}))
                             (when-not frame?
                               (js/window.addEventListener "scroll" on-scroll))
                             (js/window.scrollTo 0,0))

      :component-will-unmount (fn [comp]
                                (when-not frame?
                                  (js/window.removeEventListener "scroll" on-scroll)))
      :reagent-render
      (fn [title button-cfgs content & {:keys [hide-header-message? frame?]}]
        (let [srd-message-closed? @(subscribe [:srd-message-closed?])
              orcacle-open? @(subscribe [:orcacle-open?])
              theme @(subscribe [:theme])
              mobile? @(subscribe [:mobile?])
              username? @(subscribe [:username])]
          (reset! orcacle-open?* orcacle-open?)
          [:div.app.min-h-full
           {:class theme
            :on-scroll (when-not frame?
                         (fn [e]))}
           (when-not frame?
             [download-form])
           (when (pos? (or @(subscribe [:loading]) 0))
             [:div {:style loading-style}
              [:div.flex.justify-cont-s-a.align-items-c.h-100-p
               [:img.h-200.w-200.m-t-200 {:src "/image/spiral.gif"}]]])
           (when-not frame?
             [app-header])
           (when orcacle-open?
             [orcacle])
           (let [hdr [header title button-cfgs :frame? frame?]]
             [:div
              [:div#sticky-header.sticky-header.w-100-p.posn-fixed
               [:div.flex.justify-cont-c
                [:div#header-container.f-s-14.main-text-color.content
                 hdr]]]              
              [:div.flex.justify-cont-c.main-text-color
               [:div.content hdr]]

              ;; Support banner (integrations-gated)
              [:div.m-l-20.m-r-20.f-w-b.f-s-18.container.m-b-10.main-text-color
               [integrations/support-banner
                {:srd-message-closed? srd-message-closed?
                 :hide-header-message? hide-header-message?
                 :frame? frame?
                 :user-tier @(subscribe [:user-tier])
                 :on-dismiss #(dispatch [:close-srd-message])}]]

              ;; Content slot (integrations-gated)
              [integrations/content-slot @(subscribe [:user-tier])]

              [:div#app-main.container
               [:div.content.w-100-p content]]

              [:div.main-text-color.flex.justify-cont-c
               [:div.content.f-w-n.f-s-12
                ;; Content slot (integrations-gated)
                [integrations/content-slot @(subscribe [:user-tier])]

                [:div.flex.justify-cont-s-b.align-items-c.flex-wrap.p-10
                 [:div
                  [:div.m-b-5 "Icons made by Lorc, Caduceus, and Delapouite. Available on " [:a.orange {:href "http://game-icons.net"} "http://game-icons.net"]]
                  [:div.m-b-5 "Artwork provided by the talented Sandra. Available on " [:a.orange {:href "https://www.deviantart.com/sandara" :target :_blank} "Deviantart"]]]
                 [:div.m-l-10
                  [:div.m-b-5.justify-cont-c
                   (when-let [url (not-empty (:patreon branding/social-links))]
                     [:a.orange {:href url :target :_blank} "Support this site on Patreon"])
                   (when (seq branding/help-url)
                     [:a.orange.m-l-5 {:href branding/help-url :target :_blank} "Help"])
                   [:a.orange.m-l-5 {:href "https://github.com/Orcpub/orcpub/issues" :target :_blank} "Feedback/Bug Reports"]]]
                 [:div.m-l-10.m-r-10.p-10
                  [:div.m-b-5
                   (for [{:keys [label href]} splash/legal-footer-links]
                     ^{:key href}
                     [:a.orange.m-l-5 {:href href :target :_blank} label])]]
                 [:div.legal-footer
                  [:p "© " (current-year) " "
                   (if (seq branding/copyright-url)
                     [:a.orange {:href branding/copyright-url :target :_blank} branding/copyright-holder]
                     branding/copyright-holder)]
                  [:p "This site is based on " srd-link " - Wizards of the Coast, Dungeons & Dragons, D&D, and their logos are trademarks of Wizards of the Coast LLC in the United States and other countries. © 2025 Wizards. All Rights Reserved."]
                  [:p branding/app-name " is not affiliated with, endorsed, sponsored, or specifically approved by Wizards of the Coast LLC."]
                  [:p "Version " (v/version) " (" (v/date) ") " (v/description) " edition"]]]
                [debug-data]]]])]))})))

;; dead — zero callers (4 style defs)
#_(def row-style
  {:border-bottom "1px solid rgba(255,255,255,0.5)"})

#_(def light-row-style
  {:border-bottom "1px solid rgba(0,0,0,0.5)"})

#_(def list-style
  {:border-top "2px solid rgba(255,255,255,0.5)"})
#_(def thumbnail-style
  {:height "100px"
   :max-width "200px"
   :border-radius "5px"})

(defn other-user-component [owner & [text-classes show-follow?]]
  (let [following-users @(subscribe [:following-users])
        following? (get following-users owner)
        username @(subscribe [:username])]
    [:div.flex.m-l-10.align-items-c
     (svg-icon "orc-head" 32)
     [:div.f-s-18.m-l-5
      {:class text-classes}
      owner]
     (when (and show-follow? username (not= username owner))
       [:button.form-button.m-l-10.p-6
        {:on-click #(dispatch [(if following?
                                 :unfollow-user
                                 :follow-user)
                               owner])}
        (if following?
          "unfollow"
          "follow")])]))

(defn character-display-name
  "Return the character's name, or a descriptive fallback like 'High Elf Ranger 3'
   built from race/class/level when the name is blank. Works on summary maps."
  [{:keys [::char/character-name ::char/race-name ::char/subrace-name ::char/classes]}]
  (if (s/blank? character-name)
    (let [race-part (or subrace-name race-name)
          class-str (when (seq classes)
                      (s/join "/"
                              (map (fn [{:keys [::char/class-name ::char/level]}]
                                     (str class-name " " level))
                                   classes)))]
      (cond
        (and race-part class-str) (str race-part " " class-str)
        race-part race-part
        class-str class-str
        :else "New Character"))
    character-name))

(defn character-summary-2 [{:keys [::char/character-name
                                   ::char/image-url
                                   ::char/race-name
                                   ::char/subrace-name
                                   ::char/age
                                   ::char/sex
                                   ::char/height
                                   ::char/weight
                                   ::char/hair
                                   ::char/eyes
                                   ::char/skin
                                   ::char/classes
                                   ::char/alignment
                                   ::char/background]
                            :as summary}
                           include-name?
                           owner
                           show-owner?
                           show-follow?]
  (let [username @(subscribe [:username])
        display-name (when include-name? (character-display-name summary))]
    [:div.flex.justify-cont-s-b.w-100-p.align-items-c
     [:div.flex.align-items-c.align-items-t
      (when image-url
        [:img.m-r-20.m-t-10.m-b-10.image-character-thumbnail {:src image-url }])
      [:div.flex.character-summary.m-t-20.m-b-20
       (when display-name [:span.m-r-20.m-b-5
                                               [:span.character-name display-name]
                                               [:div.f-s-12.m-t-5.opacity-6.character-background background]
                                               [:div.f-s-12.m-t-5.opacity-6.character-alignment alignment]
                                               (when (not (s/blank? age)) [:div.f-s-12.m-t-5.opacity-6.character-age "Age: " age])
                                               (when (not (s/blank? sex)) [:div.f-s-12.m-t-5.opacity-6.character-sex "Sex: " sex])
                                               (when (not (s/blank? height)) [:div.f-s-12.m-t-5.opacity-6.character-height "Height: " height])
                                               (when (not (s/blank? weight)) [:div.f-s-12.m-t-5.opacity-6.character-weight "Weight: " weight])])
       [:span.m-r-10.m-b-5
        [:span.character-race-name race-name]
        [:div.f-s-12.m-t-5.opacity-6.character-subrace-name subrace-name]
        (when (not (s/blank? hair)) [:div.f-s-12.m-t-5.opacity-6.character-hair "Hair: " hair])
        (when (not (s/blank? eyes)) [:div.f-s-12.m-t-5.opacity-6.character-eyes "Eyes: " eyes])
        (when (not (s/blank? skin)) [:div.f-s-12.m-t-5.opacity-6.character-skin "Skin: " skin])]
       (when (seq classes)
         [:span.flex
          (map-indexed
           (fn [i v]
             (with-meta v {:key i}))
           (interpose
            [:span.m-l-5.m-r-5 "/"]
            (map
             (fn [{:keys [::char/class-name ::char/level ::char/subclass-name]}]
               [:span
                [:div.class-name (str class-name)] [:div.level (str "(" level ")")]
                [:div.f-s-12.m-t-5.opacity-6.sub-class-name (when subclass-name subclass-name)]])
             classes)))])]]
     (when (and show-owner?
              (some? owner)
              (some? username)
              (not= username owner))
       [:div.m-l-10 [other-user-component owner nil show-follow?]])]))

(defn character-summary [id & [include-name?]]
  (let [character-name @(subscribe [::char/character-name id])
        age @(subscribe [::char/age id])
        sex @(subscribe [::char/sex id])
        height @(subscribe [::char/height id])
        weight @(subscribe [::char/weight id])
        hair @(subscribe [::char/hair id])
        eyes @(subscribe [::char/eyes id])
        skin @(subscribe [::char/skin id])
        image-url @(subscribe [::char/image-url id])
        race @(subscribe [::char/race id])
        subrace @(subscribe [::char/subrace id])
        levels @(subscribe [::char/levels id])
        classes @(subscribe [::char/classes id])
        alignment  @(subscribe [::char/alignment id])
        background  @(subscribe [::char/background id])
        {:keys [::se/owner] :as strict-character} @(subscribe [::char/character id])]
    (character-summary-2
     {::char/character-name character-name
      ::char/age age
      ::char/sex sex
      ::char/height height
      ::char/weight weight
      ::char/hair hair
      ::char/eyes eyes
      ::char/skin skin
      ::char/image-url image-url
      ::char/race-name race
      ::char/subrace-name subrace
      ::char/alignment alignment
      ::char/background background
      ::char/classes (map
                      (fn [class-kw]
                        (let [{:keys [class-name class-level subclass-name] :as cfg}
                              (get levels class-kw)]
                          {::char/class-name class-name
                           ::char/level class-level
                           ::char/subclass-name subclass-name}))
                      classes)}
     include-name?
     owner
     true
     true)))

;; dead — character_builder.cljs has its own realize-char
#_(defn realize-char [built-char]
  (reduce-kv
   (fn [m k v]
     (let [realized-value (es/entity-val built-char k)]
       (if (fn? realized-value)
         m
         (assoc m k realized-value))))
   (sorted-map)
   built-char))

;; dead — zero callers
#_(def summary-style
  {:padding "33px 0"})


(declare error-boundary)

(defn locate-hint
  "Best-effort human locator for a malformed feature, built only from fields that
   actually point at a place in the builder — which class/subclass or homebrew
   source it came from, and the level it's gained. Never its :name (usually the
   missing part), and never its :page: the page is a physical-rulebook reference,
   not a place to fix anything in the app. Returns nil when there's nothing
   builder-locating to go on (the raw data dump still shows the page etc.)."
  [{:keys [class-key source level]}]
  (let [where (cond
                (string? source) (str "the “" source "” source")
                source           (str "the “" (common/kw-to-name source) "” source")
                class-key        (str "your " (s/capitalize (common/kw-to-name class-key)))
                :else            nil)
        bits (cond-> []
               where (conj (str "from " where))
               level (conj (str "gained at level " level)))]
    (when (seq bits) (s/join ", " bits))))

(defn guard-fallback
  "Inline fallback for a single item that threw while rendering: say where it
   likely came from (so the user can find and fix it in the builder) and dump its
   raw data so the exact malformed value is visible. No per-bug detector required;
   works for whatever was being rendered."
  [data _error]
  (let [hint (try (locate-hint data) (catch :default _ nil))]
    [:div.m-t-10.p-5.f-s-12
     {:style {:border "1px dashed #e9a227" :border-radius "4px"}}
     [:div.f-w-b "⚠ A feature here couldn’t be displayed"]
     (when hint [:div.m-t-2 (str "Likely " hint " — open it in the builder to fix.")])
     [:pre.wsp-prw.f-s-11.m-t-5 (pr-str data)]]))

(defn render-guard
  "Wrap one piece of UI so a render exception is contained to just this item
   (showing its raw data via guard-fallback) instead of blanking the whole page.
   General — catches any error type, not a specific known failure mode."
  [data child]
  [error-boundary
   (fn [error _stack _retry] [guard-fallback data error])
   child])

(defn- without-nth
  "items with index i removed (nil-safe, unlike keep-indexed)."
  [items i]
  (into (subvec items 0 i) (subvec items (inc i))))

(defn isolate-culprit
  "Fault isolation by re-execution — the active counterpart to render-guard.
   `render-coll` renders/processes a whole collection and may throw; `items` is
   that collection. Return the item(s) NECESSARY for the failure: an item is a
   culprit if removing it makes render-coll stop throwing. Catches AGGREGATE
   failures (e.g. a sort over a nil key) that a per-item guard never sees — and
   pinpoints the culprit even when the failure only manifests in combination
   (a nil sort key never throws alone, but removing the item fixes the whole list,
   so leave-one-out finds it where 'does this item alone fail?' cannot).

   Returns nil if render-coll doesn't throw. Honest limit: with two INDEPENDENT
   faulty items (each breaks things by itself), removing one still throws, so
   neither looks necessary — we then fall back to the smallest failing subset
   found by bisection rather than claim a single culprit."
  [render-coll items]
  (letfn [(throws? [coll]
            (try (doall (render-coll coll)) false (catch :default _ true)))
          (self-bad [coll]
            ;; items that still break when paired with a copy of themselves — i.e.
            ;; that individually carry the bad value, distinct from an innocent
            ;; neighbour only implicated because the failure needs ≥2 elements.
            (filterv #(throws? [% %]) coll))
          (bisect [its]
            (if (<= (count its) 1)
              its
              (let [mid (quot (count its) 2)
                    l (subvec its 0 mid) r (subvec its mid)]
                (cond (throws? l) (recur l)
                      (throws? r) (recur r)
                      :else its))))]
    (let [items (vec items)]
      (when (throws? items)
        (let [necessary (mapv items (filterv #(not (throws? (without-nth items %)))
                                             (range (count items))))]
          (cond
            (= 1 (count necessary)) necessary
            ;; >1 "necessary" is the comparison-failure trap: removing ANY of a
            ;; small set drops below the ≥2 elements the comparator needs, so each
            ;; looks necessary. Keep only the ones that break paired with themselves.
            (seq necessary) (let [sb (self-bad necessary)]
                              (if (seq sb) sb necessary))
            ;; nothing individually necessary (independent multi-fault): the items
            ;; that break self-paired, else bisect to a smallest failing subset.
            :else (let [sb (self-bad items)]
                    (if (seq sb) sb (bisect items)))))))))

(defn guarded-feature-list
  "Render `items` via `render-coll`. If the whole render throws, LEAD with the
   problem — surface the item(s) the isolator traced the failure to, with where to
   fix them — then render everything that DID work below. This is the auditing
   version of render-guard: it handles failures in the collection-level logic, not
   just per-item render throws."
  [render-coll items]
  ;; Render once: the realized result is reused on the happy path, so the sort and
  ;; hiccup build don't run twice on every render — only the error path pays extra.
  (let [rendered (try (doall (render-coll items)) (catch :default _ ::failed))]
    (if-not (= rendered ::failed)
      (into [:<>] rendered)
      (let [culprits (isolate-culprit render-coll items)
            culprit? (set culprits)
            survivors (remove culprit? items)]
        [:div
         ;; the problem, first and prominent — that's the thing the user must act on
         [:div.p-10.m-b-10.f-s-14
          {:style {:border "1px solid #e9a227" :border-radius "5px"
                   :background "rgba(233,162,39,0.12)"}}
          [:div.f-w-b.f-s-16
           (if (= 1 (count culprits))
             "⚠ A feature in this section couldn’t be displayed"
             (str "⚠ " (count culprits) " features in this section couldn’t be displayed"))]
          (doall
           (map-indexed
            (fn [i c]
              (let [hint (try (locate-hint c) (catch :default _ nil))]
                ^{:key i}
                [:div.m-t-5
                 (when hint [:div.f-w-600 (str "Likely " hint " — open it in the builder to fix.")])
                 [:pre.wsp-prw.f-s-11.m-t-2 {:style {:user-select "text"}} (pr-str c)]]))
            culprits))]
         ;; everything that rendered fine, below
         (when (seq survivors) (render-coll survivors))]))))

(defn- vec-remove [v i]
  (vec (concat (subvec v 0 i) (subvec v (inc i)))))

(defn prune-sites
  "Every removable selection site within the ::entity/options map at `path` (inside
   the root options). Each: {:depth :sel :choice :remove}, where :remove takes the
   ROOT options and returns it with this site pruned. Recurses into nested
   ::entity/options so deeper (more specific) selections are also enumerated."
  [opts-map path]
  (when (map? opts-map)
    (mapcat
     (fn [[sk entry]]
       (cond
         (map? entry)
         (cons {:depth (count path) :sel sk :choice (::entity/key entry)
                :remove #(update-in % path dissoc sk)}
               (prune-sites (::entity/options entry) (conj path sk ::entity/options)))
         (vector? entry)
         (cons {:depth (count path) :sel sk :choice nil
                :remove #(update-in % path dissoc sk)}
               (mapcat
                (fn [i el]
                  (cons {:depth (inc (count path)) :sel sk :choice (::entity/key el)
                         :remove #(update-in % (conj path sk) vec-remove i)}
                        (prune-sites (::entity/options el) (conj path sk i ::entity/options))))
                (range) entry))
         :else nil))
     opts-map)))

(defn isolate-culprit-selection
  "Selection-level fault isolation by re-execution. Prune each selection from the
   character's ::entity/options, rebuild with the pure entity/build, and re-test
   with `fails?` (a predicate on a freshly-built character). Return the DEEPEST
   selection whose removal clears the failure — the builder choice to re-pick —
   as the prune-site map, or nil if none isolates it. Only runs on the error path."
  [character template fails?]
  (let [opts0 (::entity/options character)
        build-fails? (fn [opts]
                       (try (fails? (entity/build (assoc character ::entity/options opts) template))
                            (catch :default _ true)))]
    (when (build-fails? opts0)
      (->> (prune-sites opts0 [])
           (filter #(not (build-fails? ((:remove %) opts0))))
           (sort-by :depth)
           last))))

(defn- title-case [s]
  (when s (->> (s/split (str s) #"\s+") (map s/capitalize) (s/join " "))))

(defn culprit-selection-label
  "Human breadcrumb for the isolated selection: the class for context plus the
   selection and the chosen option — built only from real keys on the tree."
  [opts0 {:keys [sel choice]}]
  (let [class-nm (some-> (first (:class opts0)) ::entity/key common/kw-to-name title-case)
        sel-nm (when (keyword? sel) (title-case (common/kw-to-name sel)))
        choice-nm (when (keyword? choice) (title-case (common/kw-to-name choice)))]
    (->> [(when class-nm (str "your " class-nm))
          (when (or sel-nm choice-nm)
            (str sel-nm (when (and sel-nm choice-nm) ": ") choice-nm))]
         (remove nil?)
         (s/join " → "))))

(defn features-section-fails?
  "Re-test for the Features section on a freshly-built character: the same name
   sort the section runs — throws iff a feature still has a nil/blank name."
  [built]
  (try
    (doall (common/aloof-sort-by :name
                                 (concat (char/traits built) (char/actions built)
                                         (char/bonus-actions built) (char/reactions built))))
    false
    (catch :default _ true)))

(defn display-section [title icon-name value & [list? buttons]]
  [:div.m-t-20
   [:div.flex.justify-cont-s-b
    [:div.flex.align-items-c
     (when icon-name (svg-icon icon-name 32))
     [:span.m-l-5.f-s-16.f-w-600 title]]
    (when (seq buttons)
      (apply
       conj
       [:div]
       buttons))]
   [:div {:class-name (if list? "m-t-0" "m-t-4") }
    [:span.f-s-24.f-w-600 {:class (csk/->camelCase (str title))}
     value]]])

(defn list-display-section [title image-name values]
  (when (seq values)
    (display-section
     title
     image-name
     [:span.m-t-5.f-s-14.f-w-n.i
      (s/join
       ", "
       values)]
     true)))

;; dead — zero callers
#_(defn svg-icon-section [title icon-name content]
  [:div.m-t-20
   [:span.f-s-16.f-w-600 title]
   [:div.flex.align-items-c
    (svg-icon icon-name 32)
    [:div.f-s-24.m-l-10.f-w-b content]]])

(defn list-item-section [list-name icon-name items & [name-fn]]
  [list-display-section list-name icon-name
   (map
    (fn [item]
      ((or name-fn :name) item))
    items)])

;; dead — zero callers
#_(defn compare-spell [spell-1 spell-2]
  (let [key-fn (juxt :key :ability)]
    (compare (key-fn spell-1) (key-fn spell-2))))

(defn details-button [expanded? on-click]
  [:span.orange.underline.pointer
   {:on-click on-click}
   [:span (if expanded? "hide" "details")]
   [:i.fa.m-l-5
    {:class (if expanded? "fa-caret-up" "fa-caret-down")}]])


(defn spellcaster-levels-table []
  (let [expanded? (r/atom false)]
    (fn [spell-slot-factors total-spellcaster-levels levels mobile?]
      [:div.f-s-14.f-w-n
       [:div.flex.justify-cont-s-b
        [:div
         [:span.f-w-b.f-s-16 "Total Spellcaster Levels: "]
         [:span.f-s-16.f-w-n total-spellcaster-levels]]
        (details-button @expanded? #(swap! expanded? not))]
       (when @expanded?
         [:div:div.f-s-14
          [:table.w-100-p.t-a-l.striped
           [:tbody
            [:tr.f-w-b
             [:th.p-10 "Class"]
             [:th.p-10 (if mobile? "Sub." "Subclass")]
             [:th.p-10 (if mobile? "Lvl." "Level")]
             [:th.p-10 (if mobile? "Mult." "Multiplier")]
             [:th.p-10 (if mobile? "Tot." "Total")]]
            (doall
             (map
              (fn [[class-key factor]]
                (let [{:keys [class-name class-level subclass-name]} (levels class-key)]
                  ^{:key class-key}
                  [:tr
                   [:td.p-10 class-name]
                   [:td.p-10 subclass-name]
                   [:td.p-10 class-level]
                   [:td.p-10 (if (= 1 factor) 1 (str "1/" factor))]
                   [:td.p-10 (int (/ class-level factor))]]))
              spell-slot-factors))
            [:tr
             [:td.p-10 "Total"]
             [:td]
             [:td]
             [:td]
             [:td.p-10 total-spellcaster-levels]]]]])])))

(def highlight-spell-slot-row-style
  {:background-color "rgba(255,255,255,0.3)"})

(defn spell-slots-table []
  (let [expanded? (r/atom false)
        checkboxes-expanded? (r/atom false)]
    (fn [id spell-slots spell-slot-factors total-spellcaster-levels levels mobile? pact-magic?]
      (let [multiclass? (> (count spell-slot-factors) 1)
            first-factor-key (when spell-slot-factors (-> spell-slot-factors first key))
            first-class-level (when first-factor-key (-> levels first-factor-key :class-level))]
        [:div.f-s-14.f-w-n
         [:div.flex.justify-cont-s-b
          [:div
           [:span.f-w-b.f-s-16 (str "Slots" (when multiclass? " (Multiclass)"))]]
          (when (not pact-magic?)
            (details-button @expanded? #(swap! expanded? not)))]
         [:div.f-w-n.f-s-14
          [:table.w-100-p.t-a-l.striped
           [:tbody
            [:tr.f-w-b.f-s-12
             [:th.p-5 (if mobile? "Lvl." "Caster Levels")]
             (doall
              (map
               (fn [i]
                 ^{:key i}
                 [:th.p-5 (if (and mobile?
                                   (or @expanded?
                                       (> (count spell-slots) 8)))
                            (inc i)
                            (common/ordinal (inc i)))])
               (range (if @expanded?
                        9
                        (apply max (keys spell-slots))))))
             [:th]]
            (if (and (not pact-magic?) @expanded?)
              (doall
               (map
                (fn [lvl]
                  (let [highlight? (or (and multiclass?
                                            (= total-spellcaster-levels lvl))
                                       (and (not multiclass?)
                                            (= first-class-level lvl)))]
                    ^{:key lvl}
                    [:tr
                     {:class (when highlight?
                                    "f-w-b")
                      :style (when highlight?
                               highlight-spell-slot-row-style)}
                     [:td.p-10 lvl]
                     (let [total-slots (opt/total-slots lvl (if multiclass? 1 (-> spell-slot-factors first val)))]
                       (doall
                        (map
                         (fn [spell-lvl]
                           ^{:key spell-lvl}
                           [:td.p-10 (get total-slots spell-lvl)])
                         (range 1 10))))]))
                (range 1 21)))
              [:tr.pointer
               {:on-click #(swap! checkboxes-expanded? not)}
               [:td.p-10 (if multiclass?
                           total-spellcaster-levels
                           first-class-level)]
               (doall
                (map
                 (fn [level]
                   ^{:key level}
                   [:td.p-10 (spell-slots (inc level))])
                 (range (apply max (keys spell-slots)))))
               [:td.p-r-5
                [:i.fa.orange
                 {:class (if @checkboxes-expanded? "fa-caret-up" "fa-caret-down")}]]])]]]
         (when @checkboxes-expanded?
           [:div.bg-light.p-5
            (doall
             (map
              (fn [[level slots]]
                ^{:key level}
                [:div.p-10.flex.justify-cont-s-b
                 [:span.f-w-b (str (common/ordinal level) " level")]
                 [:div
                  (doall
                   (for [i (range slots)]
                     ^{:key i}
                     [:span.m-l-5
                      {:on-click #(dispatch [::char/toggle-spell-slot-used id level i])}
                      (comps/checkbox @(subscribe [::char/spell-slot-used? id level i]) false)]))]])
              spell-slots))])]))))

(defn dropdown [{:keys [items value on-change]}]
  ;; Dedup items by :value as a safety net — duplicate option values from
  ;; homebrew plugins can slip through if imported before dedup was added.
  (let [unique-items (->> items
                          (reduce (fn [{:keys [seen result]} item]
                                    (let [v (:value item)]
                                      (if (contains? seen v)
                                        {:seen seen :result result}
                                        {:seen (conj seen v) :result (conj result item)})))
                                  {:seen #{} :result []})
                          :result)]
    [:select.builder-option.builder-option-dropdown.m-t-0
     {:value (or value "")
      :on-change #(on-change (event-value %))}
     (doall
      (map-indexed
       (fn [i {:keys [value title disabled?]}]
         ^{:key (str i "-" (or value title))}
         [:option.builder-dropdown-item
          (cond-> {:value value}
            disabled? (assoc :disabled true))
          title])
       unique-items))]))

(defn labeled-dropdown [label cfg]
  [:div
   [:div.f-w-b.m-b-5 label]
   [dropdown cfg]])

(defn button-roll-fn [message roll]
  (fn [e]
    (if (.-shiftKey e)
      (dispatch [:show-message-2 (str message " w/ Disadvantage: " (dice/dice-roll-text-2 roll) "  |  " (dice/dice-roll-text-2 roll))]) 
      (if (or (.-ctrlKey e) (.-metaKey e))
        (dispatch [:show-message-2 (str message " w/ Advantage: " (dice/dice-roll-text-2 roll) "  |  " (dice/dice-roll-text-2 roll))])
        (dispatch [:show-message-2 (str message " " (dice/dice-roll-text-2 roll))])))))

(def button-roll-handler (memoize button-roll-fn))

(defn roll-button [message roll & {:keys [text disable-tooltip style]}]
  (let [mobile? @(subscribe [:mobile?])
        button [:button.roll-button
                {:on-click (fn [e]
                             (.stopPropagation e)
                             ((button-roll-handler message roll) e))
                 :style style}
                (or text "Roll")]]
    (if (or mobile? disable-tooltip)
      button
      [:div.tooltip
       button
       [:span.tooltiptext "ctrl+click for advantage shift+click for disadvantage"]])))

(defn cast-spell-component []
  (let [selected-level (r/atom nil)]
    (fn [id lvl]
      (let [slot-levels-available @(subscribe [::char/slot-levels-available id])
            usable-slot-levels (drop-while
                                (partial > lvl)
                                slot-levels-available)]
        [:div.flex.justify-cont-end.align-items-c
         [:div.w-80
          [:span "Cast at level"]
          [dropdown
           {:items (map
                    (fn [i]
                      {:value i
                       :title i})
                    usable-slot-levels)
            :value (or @selected-level lvl)
            :on-change #(reset! selected-level (js/parseInt %))}]]
         [:div.m-l-5
          [:button.form-button.p-10
           {:class (when (empty? usable-slot-levels) "disabled")
            :on-click #(when (seq usable-slot-levels)
                         (dispatch [::char/use-spell-slot id (or @selected-level (first usable-slot-levels))]))}
           "cast spell"]]]))))

(def expanded-spell-background-style
  {:background-color "rgba(0,0,0,0.1)"})

(defn spell-row [id lvl spell-modifiers prepares-spells prepared-spells-by-class {:keys [key ability qualifier class always-prepared?]} expanded? on-click prepare-spell-count prepared-spell-count]
  (let [spell-map @(subscribe [::spells/spells-map])
        spell (spell-map key)
        cls-mods (get spell-modifiers class)
        spell-dc (get cls-mods :spell-save-dc)
        remaining-preps (- prepare-spell-count
                           prepared-spell-count)]
    [[:tr.spell.pointer
      {:on-click on-click}
      [:td.p-l-10.p-b-5.p-t-5.f-w-b
       (when (and (pos? lvl)
                (get prepares-spells class))
         [:span.m-r-5
          {:class (when always-prepared?
                         "cursor-disabled")
           :on-click (fn [e]
                       (when (not always-prepared?)
                         (dispatch [::char/toggle-spell-prepared id class key]))
                       (.stopPropagation e))}
          (let [selected? (or always-prepared?
                              (get-in prepared-spells-by-class [class key]))]
            (comps/checkbox
             selected?
             (and (not selected?)
                  (or always-prepared?
                      (not (pos? remaining-preps))))))])
       (:name spell)]
      [:td.p-l-10.p-b-5.p-t-5 class]
      [:td.p-l-10.p-b-5.p-t-5 (when ability (s/upper-case (common/safe-name ability)))]
      [:td.p-l-10.p-b-5.p-t-5 (get cls-mods :spell-save-dc)]
      [:td.p-l-10.p-b-5.p-t-5 (common/bonus-str (get cls-mods :spell-attack-modifier))]
      [:td.p-l-10.p-b-5.p-t-5
       (roll-button
        (str (:name spell) " attack: ")
        (str "1d20" (common/mod-str (get cls-mods :spell-attack-modifier)))
        :text (str "1d20" (common/mod-str (get cls-mods :spell-attack-modifier))))]
      [:td.p-l-10.p-b-5.p-t-5.pointer.orange
       [:i.fa
        {:class (if expanded? "fa-caret-up" "fa-caret-down")}]]]
     (when expanded?
       [:tr {:style expanded-spell-background-style}
        [:td {:col-span 7}
         [:div.p-10
          (when (pos? lvl)
            [cast-spell-component id lvl])
          [spell-component spell false 14]]]])]))

(defn toggle-spell-expanded-fn [expanded-spells k]
  #(swap! expanded-spells update k not))

(def toggle-spell-expanded! (memoize toggle-spell-expanded-fn))

(defn spells-table []
  (let [expanded-spells (r/atom {})
        mobile? @(subscribe [:mobile?])]
    (fn [id lvl spells spell-modifiers hide-unprepared? prepare-spell-count-fn]
      (let [prepares-spells @(subscribe [::char/prepares-spells id])
            prepared-spells-by-class @(subscribe [::char/prepared-spells-by-class id])]
        [:div.m-t-10.m-b-30
         [:div.flex.justify-cont-s-b
          [:div
           [:span.f-w-b.i (if (pos? lvl)
                            (str (common/ordinal lvl) " Level")
                            "Cantrip")]
           (when hide-unprepared?
             [:span.i.opacity-5.m-l-5 "(unprepared hidden)"])]
          (when (pos? lvl)
            [:span.f-w-b (str @(subscribe [::char/spell-slots-remaining id lvl]) " remaining")])]
         [:table.w-100-p.t-a-l.striped
          [:tbody.spells
           [:tr.f-w-b.f-s-12
            [:th.p-l-10.p-b-5.p-t-5 (if (and (not (zero? lvl))
                                               (seq prepares-spells))
                                        "Prepared? / Name"
                                        "Name")]
            [:th.p-l-10.p-b-5.p-t-5 (if mobile? "Src" "Source")]
            [:th.p-l-10.p-b-5.p-t-5 (if mobile? "Aby" "Ability")]
            [:th.p-l-10.p-b-5.p-t-5 "DC"]
            [:th
             {:class (when (not mobile?) "p-b-10 p-t-10")}
             "Mod."]
            [:th.p-l-10.p-b-5.p-t-5 "Attack"]
            [:th.p-l-10.p-b-5.p-t-5]]
           (doall
            (map-indexed
             (fn [i r]
               (with-meta r {:key i}))
             (mapcat
              (fn [{:keys [key class always-prepared?] :as spell}]
                (let [k (str key class)
                      prepared-spell-count (or (some->> class
                                                        (get prepared-spells-by-class)
                                                        count)
                                               0)
                      prepare-spell-count (prepare-spell-count-fn class)]
                  (when (char/spell-prepared? {:hide-unprepared? hide-unprepared?
                                             :always-prepared? always-prepared?
                                             :lvl lvl
                                             :key key
                                             :class class
                                             :prepares-spells prepares-spells
                                             :prepared-spells-by-class prepared-spells-by-class})
                    (spell-row id
                               lvl
                               spell-modifiers
                               prepares-spells
                               prepared-spells-by-class
                               spell
                               (@expanded-spells k)
                               (toggle-spell-expanded! expanded-spells k)
                               prepare-spell-count
                               prepared-spell-count))))
              (sort-by :key spells))))]]]))))

(defn toggle-hide-unprepared-fn [hide-unprepared?]
  #(swap! hide-unprepared? not))

(def toggle-hide-unprepared! (memoize toggle-hide-unprepared-fn))

(defn spells-tables []
  (let [hide-unprepared? (r/atom false)]
    (fn [id spells-known spell-slots spell-modifiers]
      [:div.f-s-14.f-w-n
       [:div.flex.justify-cont-s-b
        [:span.f-w-b.f-s-16 "Spells By Level"]
        [:button.form-button.p-5
         {:on-click (toggle-hide-unprepared! hide-unprepared?)}
         (if @hide-unprepared?
           "Show All"
           "Hide Unprepared")]]
       (let [prepare-spell-count-fn (memoize @(subscribe [::char/prepare-spell-count-fn id]))]
         (doall
          (map
           (fn [[lvl spells]]
             ^{:key lvl}
             [spells-table id lvl (vals spells) spell-modifiers @hide-unprepared? prepare-spell-count-fn])
           spells-known)))])))

(defn finish-long-rest-fn [id]
  #(dispatch [::char/finish-long-rest id]))

(defn finish-short-rest-fn [id]
  #(dispatch [::char/finish-short-rest id]))

(defn finish-short-rest-warlock-fn [id]
  #(dispatch [::char/finish-short-rest-warlock id]))

(def finish-long-rest-handler (memoize finish-long-rest-fn))

(def finish-short-rest-handler (memoize finish-short-rest-fn))

(def finish-short-rest-handler-warlock (memoize finish-short-rest-warlock-fn))

(defn finish-long-rest-button [id]
  [:button.form-button.p-5
   {:on-click (finish-long-rest-handler id)}
   "finish long rest"])

(defn finish-short-rest-button [id]
  [:button.form-button.p-5.m-l-5
   {:on-click (finish-short-rest-handler id)}
   "finish short rest"])

(defn finish-short-rest-button-warlock [id]
  [:button.form-button.p-5.m-l-5
   {:on-click (finish-short-rest-handler-warlock id)}
   "finish short rest"])

(defn spells-known-section [id spells-known spell-slots spell-modifiers spell-slot-factors total-spellcaster-levels levels]
  (let [mobile? @(subscribe [:mobile?])
        multiclass? (> (count spell-slot-factors) 1)
        prepares-spells @(subscribe [::char/prepares-spells id])
        pact-magic? @(subscribe [::char/pact-magic? id])
        prepare-spell-count-fn @(subscribe [::char/prepare-spell-count-fn id])
        classes (set @(subscribe [::char/classes id]))]
    [display-section
     "Spells"
     "spell-book"
     [:div.m-t-20
      (when multiclass?
        [:div.m-b-20
         [spellcaster-levels-table spell-slot-factors total-spellcaster-levels levels mobile?]])
      (when (or pact-magic? spell-slot-factors)
        [:div.m-b-20
         [spell-slots-table id spell-slots spell-slot-factors total-spellcaster-levels levels mobile? pact-magic?]])
      [:div.m-b-20
       [:span.f-w-b.f-s-16 "Spell Preparation"]
       (if (seq prepares-spells)
         [:table.w-100-p.t-a-l.striped.f-s-12
          [:tbody
           [:tr.f-w-b
            [:th.p-10 "Class"]
            [:th.p-10 "Can Prepare"]]
           (doall
            (map
             (fn [[class-nm]]
               ^{:key class-nm}
               [:tr.f-w-n
                [:td.p-10 class-nm]
                [:td.p-10 (str (prepare-spell-count-fn class-nm) "/day")]])
             prepares-spells))]]
         [:div.f-s-14.f-w-n.i.m-t-5 "You don't need to prepare spells"])]
      [:div.m-b-20
       [spells-tables id spells-known spell-slots spell-modifiers]]]
     nil
     [[finish-long-rest-button id]
      (when (contains? classes :warlock) [finish-short-rest-button-warlock id])]]))

;; dead — zero callers
#_(defn equipment-section [title icon-name equipment equipment-map]
  [list-display-section title icon-name
   (map
    (fn [[equipment-kw {item-qty ::char-equip/quantity
                        equipped? ::char-equip/equipped?
                        :as num}]]
      (str (disp/equipment-name equipment-map equipment-kw)
           " (" (or item-qty num) ")"))
    equipment)])

#_(defn add-links [desc]
  desc
  (let [{:keys [abbr url]} (some (fn [[_ source]]
                                   (if (and (:abbr source)
                                            (re-matches (re-pattern (str ".*" (:abbr source) ".*")) desc))
                             source))
                 disp/sources)
        [before after] (if abbr (s/split desc (re-pattern abbr)))]
    (if abbr
      [:span
       [:span before]
       [:a {:href url :target :_blank} abbr]
       [:span after]]
      desc)))

(defn attack-comp [name description]
  [:p.m-t-10
   [:span.f-w-600.i name "."]
   [:span.f-w-n.m-l-10 description]])

(defn weapon-name [weapon]
  (or (:name weapon)
      (::mi/name weapon)))

(defn weapon-attack-description-short [{:keys [::weapon/ranged?] :as weapon}]
  (disp/attack-description-short (-> weapon
                                     (assoc :attack-type (if ranged? :ranged :melee))
                                     (dissoc :description))))

(defn weapon-attack-description [{:keys [::weapon/ranged?] :as weapon} damage-modifier attack-modifier]
  (disp/attack-description (-> weapon
                               (assoc :attack-type (if ranged? :ranged :melee))
                               (assoc :damage-modifier damage-modifier)
                               (assoc :attack-modifier attack-modifier)
                               (dissoc :description))))

(defn weapon-attack-comp [weapon off-hand? weapon-attack-modifier weapon-damage-modifier]
  [attack-comp
   (str (weapon-name weapon) (when off-hand? " (off hand)"))
   (weapon-attack-description weapon
                              (weapon-damage-modifier weapon off-hand?)
                              (weapon-attack-modifier weapon))])

(defn attacks-section [id]
  (let [attacks @(subscribe [::char/attacks id])
        all-weapons-map @(subscribe [::mi/all-weapons-map])
        main-hand-weapon-kw @(subscribe [::char/main-hand-weapon id])
        main-hand-weapon (when main-hand-weapon-kw (all-weapons-map main-hand-weapon-kw))
        off-hand-weapon-kw @(subscribe [::char/off-hand-weapon id])
        weapon-attack-modifier @(subscribe [::char/best-weapon-attack-modifier-fn id])
        weapon-damage-modifier @(subscribe [::char/best-weapon-damage-modifier-fn id])
        off-hand-weapon (when off-hand-weapon-kw (all-weapons-map off-hand-weapon-kw))]
    (when (or (seq attacks)
            main-hand-weapon)
      (display-section
       "Attacks"
       "pointy-sword"
       [:div.f-s-14
        (when main-hand-weapon
          [:div [weapon-attack-comp main-hand-weapon false weapon-attack-modifier weapon-damage-modifier]])
        (when off-hand-weapon
          [:div [weapon-attack-comp off-hand-weapon true weapon-attack-modifier weapon-damage-modifier]])
        [:div
         (doall
          (map
           (fn [{:keys [name area-type description damage-die damage-die-count damage-type save save-dc] :as attack}]
             ^{:key name}
             [attack-comp name (common/sentensize (disp/attack-description attack))])
           attacks))]]))))

(defn toggle-feature-used-fn [id units k]
  #(dispatch [::char/toggle-feature-used id units k]))

(def toggle-feature-used-handler (memoize toggle-feature-used-fn))

(defn actions-indicators [id nm units amount]
  (if (< amount actions-amount-many)
    ;; small, manageable number of uses
    [:span.m-l-10
     (doall
       (for [i (range amount)]
         (let [k (str nm "-" i)]
           ^{:key i}
           [:span
            {:on-click (toggle-feature-used-handler id units k)}
            [:span.m-r-5 (comps/checkbox @(subscribe [::char/feature-used? id units k]) false)]])))]

    ;; larger number of uses
    (let [initial-value @(subscribe [::char/feature-used-count id units nm amount])]
      [:span.m-l-10
       [comps/selection
        (for [i (range (inc amount))]
          (let [k (str nm "-" i)]
            {:key k
             :name (str i)}))

        (fn on-change [e]
          (let [v (-> e .-target .-value)]
            (when initial-value
              ; we have to dispatch-sync because in the case where id is nil,
              ; this event handler dispatches, so the call below gets
              ; a stale DB value and overwrites this one. It ought be
              ; possible to make it affect the :db directly, but I don't
              ; know what sort of side effects that could have....
              ; See: update-character-fx, and its use of :dispatch; might
              ; be able to replace that with:
              ;  {:db (set-character db (update-fn (:character db)))}
              (dispatch-sync [::char/toggle-feature-used id units initial-value]))
            ((toggle-feature-used-handler id units v))))

        ; if no initial value, assume "all uses available"
        (or initial-value
            (str nm "-" amount))]])))

(defn actions-section [id title icon-name actions]
  (when (seq actions)
    (let [render-coll
          ;; render the whole collection (sort + per-item). The aloof-sort-by is
          ;; eager, so if a malformed item breaks the sort this throws when called
          ;; — which is exactly what guarded-feature-list re-executes to isolate.
          (fn [coll]
            (doall
              (map
                (fn [{{:keys [units amount]} :frequency :as action}]
                  ;; show (and key/sort by) the safe display name: an obvious
                  ;; "[Unnamed feature]" placeholder instead of a blank when missing.
                  (let [nm (common/feature-name action)]
                    ^{:key action}
                    [render-guard action
                     [:p.m-t-10
                      [:span.f-w-600.i nm]
                      [:span.f-w-n.m-l-10.wsp-prw (common/sentensize (disp/action-description action))]
                      (when (and amount units)
                        (actions-indicators id nm units amount))]]))
                (common/aloof-sort-by common/feature-name coll))))]
      (display-section
        title
        icon-name
        [:div.f-s-14.l-h-19
         ;; happy path: identical output; on a section-level throw, isolate the
         ;; real offending item by re-execution and surface it — no stack, no guess.
         [guarded-feature-list render-coll actions]]))))

(defn prof-name [prof-map prof-kw]
  (or (-> prof-kw prof-map :name) (common/kw-to-name prof-kw)))

(defn resistance-str [{:keys [value qualifier]}]
  (str (name value)
       (when qualifier (str " (" qualifier ")"))))

;; dead — zero callers
#_(def no-https-images "Sorry, we don't currently support images that start with https")

;; dead — zero callers
#_(defn default-image [race classes]
  (when (and (or (= "Human" race)
               (nil? race))
           (= :barbarian (first classes)))
    "/image/barbarian.png"))

(defn section-header-2 [title icon]
  [:div
   (when icon (svg-icon icon 24))
   [:div.f-s-18.f-w-b.m-b-5 title]])

(defn armor-class-section-2 [id]
  [:div
   [:div.p-10.flex.flex-column.align-items-c
    (section-header-2 "Armor Class" "checked-shield")
    [:div.f-s-24.f-w-b.armor-class @(subscribe [::char/current-armor-class id])]]])

(defn basic-section [title icon v show-button]
  [:div
   [:div.p-10.flex.flex-column.align-items-c
    (section-header-2 title icon)
    [:div.f-s-24.f-w-b
     {:class (orcpub.common/->kebab-case title)}
     (if (boolean show-button)
       (roll-button
        (str title " check: ")
        (str "1d20" v)
        :text v
        :style {:font-size "24px" :padding "2px 8px"})
       v)]]])

(def current-hit-points-editor-style
  {:width "60px"
   :margin-top 0})

(defn hit-dice-section-2 [id]
  (let [levels @(subscribe [::char/levels id])]
    (basic-section "Hit Dice"
                   nil
                   (s/join
                    " / "
                    (map
                     (fn [{:keys [class-level hit-die]}] (str class-level "d" hit-die))
                     (vals levels))) false)))

(defn set-current-hit-points-fn [id]
  #(dispatch [::char/set-current-hit-points
              id
              (or (-> %
                      event-value
                      js/parseInt)
                  0)]))

(def set-current-hit-points-handler (memoize set-current-hit-points-fn))

(defn hit-points-section-2 [id]
  (basic-section "Max Hit Points"
                 "health-normal"
                 [:div.flex.align-items-c
                  [:input.input
                   {:style current-hit-points-editor-style
                    :type :number
                    :value (or @(subscribe [::char/current-hit-points id])
                               @(subscribe [::char/max-hit-points id]))
                    :on-change (set-current-hit-points-handler id)}]
                  [:span.m-l-5 "/"]
                  [:span.m-l-5 @(subscribe [::char/max-hit-points id])]] false))

(defn initiative-section-2 [id]
  (basic-section "Initiative" "sprint" (common/mod-str @(subscribe [::char/initiative id])) true))

(defn darkvision-section-2 [id]
  (basic-section "Darkvision" "night-vision" (str @(subscribe [::char/darkvision id]) " ft.") false))

(defn critical-hits-section-2 [id]
  (let [crit-values-str @(subscribe [::char/crit-values-str id])]
    (basic-section "Critical Hits" nil crit-values-str false)))

(defn number-of-attacks-section-2 [id]
  (basic-section "Number of Attacks" nil @(subscribe [::char/number-of-attacks id]) false))

(defn passive-perception-section-2 [id]
  (basic-section "Passive Perception" "awareness" @(subscribe [::char/passive-perception id]) false))

(defn proficiency-bonus-section-2 [id]
  (basic-section "Proficiency Bonus" nil (common/bonus-str @(subscribe [::char/proficiency-bonus id])) false))

(defn skills-section-2 [id]
  (let [skill-profs (or @(subscribe [::char/skill-profs id]) #{})
        skill-bonuses @(subscribe [::char/skill-bonuses id])]
    [:div
     [proficiency-bonus-section-2 id]
     [passive-perception-section-2 id]
     [:div.p-10.flex.flex-column.align-items-c.skills
      (section-header-2 "Skills" "juggler")
      [:table
       [:tbody
        (doall
         (map
          (fn [{skill-name :name skill-key :key icon :icon :as skill}]
            ^{:key skill-key}
            [:tr.t-a-l
             {:class (if (skill-profs skill-key) "f-w-b" "opacity-7")}
             [:td [:div.skill-name
                   (svg-icon icon 18)
                   [:span.m-l-5 skill-name]]]
             [:td.p-1 (roll-button
                   (str skill-name " check: ")
                   (str "1d20" (common/mod-str (skill-bonuses skill-key)))
                   :text (common/bonus-str (skill-bonuses skill-key)))]])
          skills/skills))]]]]))

(defn ability-scores-section-2 [id]
  (let [abilities @(subscribe [::char/abilities id])
        ability-bonuses @(subscribe [::char/ability-bonuses id])
        theme @(subscribe [:theme])]
    [:div
     [:div.f-s-18.f-w-b "Ability Scores"]
     [:div.flex.justify-cont-s-a.m-t-10.ability-scores
      (doall
       (map
        (fn [k]
          ^{:key k}
          [:div
           (t/ability-icon k 24 theme)
           [:div.ability-score-name
            [:span.f-s-20.uppercase (name k)]]
           [:div.f-s-24.f-w-b.ability-score (abilities k)]
           [:div.f-s-12.opacity-5.m-b-2.m-t-2 " mod"]
           [:div.f-s-18.ability-score-modifier (roll-button
                                                (str (clojure.string/upper-case (name k)) " check: ")
                                                (str "1d20 " (common/mod-str (ability-bonuses k)))
                                                :text (common/bonus-str (ability-bonuses k)))]])
        char/ability-keys))]]))

(defn saving-throws-section-2 [id]
  (let [save-bonuses @(subscribe [::char/save-bonuses id])
        saving-throws @(subscribe [::char/saving-throws id])
        theme @(subscribe [:theme])]
    [:div.p-10.flex.flex-column.align-items-c
     (section-header-2 "Saving Throws" "dodging")
     [:table
      [:tbody
       (doall
        (map
         (fn [k]
           ^{:key k}
            [:tr.t-a-l
             {:class (if (saving-throws k) "f-w-b" "opacity-7")}
             [:td [:div
                   (t/ability-icon k 18 theme)
                   [:span.m-l-5.saving-throw-name (s/upper-case (name k))]]]
             [:td.p-1 (roll-button
                   (str (s/upper-case (name k)) " check: ")
                   (str "1d20" (common/mod-str (save-bonuses k)))
                   :text (common/bonus-str (save-bonuses k)))]])
         char/ability-keys))]]]))

(defn feet-str [num]
  (str num " ft."))

(defn speed-section-2 [id]
  (let [speed @(subscribe [::char/base-land-speed id])
        swim-speed @(subscribe [::char/base-swimming-speed id])
        flying-speed @(subscribe [::char/base-flying-speed id])
        speed-with-armor @(subscribe [::char/speed-with-armor id])
        unarmored-speed-bonus @(subscribe [::char/unarmored-speed-bonus id])
        equipped-armor @(subscribe [::char/armor id])
        all-armor @(subscribe [::char/all-armor-inventory id])]
    [:div.p-10
     (section-header-2 "Speed" "walking-boot")
     [:span.f-s-24.f-w-b
      [:span
       [:span [:div.speed (feet-str (+ (or unarmored-speed-bonus 0)
                      (if speed-with-armor
                        (speed-with-armor nil)
                        speed)))]
       (when (or unarmored-speed-bonus
               speed-with-armor)
         [:span.display-section-qualifier-text "(unarmored)"])]]
      (if speed-with-armor
        [:div.f-s-18
         (doall
          (map
           (fn [[armor-kw _]]
             (let [armor (mi/all-armor-map armor-kw)
                   speed (speed-with-armor armor)]
               ^{:key armor-kw}
               [:div
                [:div.speed
                 [:span (feet-str speed)]]
                 [:span.display-section-qualifier-text (str "(" (:name armor) " armor)")]]))
           (dissoc all-armor :shield)))]
        (when unarmored-speed-bonus
          [:div.f-s-18
           [:span
            [:div.speed
            [:span (feet-str speed)]]
            [:span.display-section-qualifier-text "(armored)"]]]))
      (when (and swim-speed (pos? swim-speed))
        [:div.f-s-18
         [:div.speed
         [:span (feet-str swim-speed)]] [:span.display-section-qualifier-text "(swim)"]])
      (when (and flying-speed (pos? flying-speed))
        [:div.f-s-18
         [:div.speed
         [:span (feet-str flying-speed)]] [:span.display-section-qualifier-text "(fly)"]])]]))

(defn personality-section [title & descriptions]
  (when (and (seq descriptions)
           (some (complement s/blank?) descriptions))
    [:div.m-t-20.t-a-l
     [:div.f-w-b.f-s-18 title]
     [:div {:class (csk/->camelCase (str title))}
      (doall
       (map-indexed
        (fn [i description]
          ^{:key i}
          [:div
           (doall
            (map-indexed
             (fn [j p]
               ^{:key j}
               [:p p])
             (s/split
              description
              #"\n")))])
        descriptions))]]))

(defn description-section [id]
  (let [personality-trait-1 @(subscribe [::char/personality-trait-1 id])
        personality-trait-2 @(subscribe [::char/personality-trait-2 id])
        ideals @(subscribe [::char/ideals id])
        bonds @(subscribe [::char/bonds id])
        flaws @(subscribe [::char/flaws id])
        description @(subscribe [::char/description id])]
    [:div.p-5
     (personality-section "Personality Traits" personality-trait-1 personality-trait-2)
     (personality-section "Ideals" ideals)
     (personality-section "Bonds" bonds)
     (personality-section "Flaws" flaws)
     (personality-section "Description" description)]))

(def notes-style
  {:height "400px"
   :width "100%"})

(def stroke-style
  {:stroke-width "1"})

(def bar-stroke-style
  {:stroke-width "5"
   :stroke orange
   :opacity "0.8"})

(defn set-notes-fn [id]
  #(dispatch [::char/set-notes id %]))

(def set-notes-handler (memoize set-notes-fn))

(defn summary-details [num-columns id]
  (let [built-char @(subscribe [:built-character id])
        {:keys [::entity/owner] :as character} @(subscribe [::char/character id])
        username @(subscribe [:username])
        race @(subscribe [::char/race id])
        classes @(subscribe [::char/classes id])
        background @(subscribe [::char/background id])
        alignment @(subscribe [::char/alignment id])
        all-armor @(subscribe [::char/all-armor id])
        image-url-failed @(subscribe [::char/image-url-failed id])
        image-url @(subscribe [::char/image-url id])
        faction-image-url @(subscribe [::char/faction-image-url id])
        faction-image-url-failed @(subscribe [::char/faction-image-url-failed id])
        armor-class @(subscribe [::char/armor-class id])
        armor-class-with-armor @(subscribe [::char/armor-class-with-armor id])
        total-levels @(subscribe [::char/total-levels id])
        current-level-xps (opt/level-xps total-levels)
        next-level-xps (opt/level-xps (inc total-levels))
        xps (or @(subscribe [::char/xps id])
                current-level-xps)
        fraction (/ (- xps current-level-xps)
                    (- next-level-xps current-level-xps))
        line-length 160
        buffer 10
        progress-length (double (* line-length fraction))
        current-route @(subscribe [:route])
        max-levels? (>= total-levels 20)]
    [:div
     [:div
      [:div.w-100-p.t-a-c
       [:div
        [:div.m-b-20
         [:div.f-w-b.f-s-18 "Experience Points"]
         [:div.flex.justify-cont-s-a
          [:div.flex.flex-wrap.align-items-c
           [:div
            [comps/input-field
             :input
             xps
             #(dispatch [::char/set-current-xps id (js/parseInt %)])
             {:class "input"
              :type :number}]]
           [:div.p-5
            [:div
             [:svg {:width "250px"
                    :view-box "0 0 200 40"}
              [:line.stroke-color {:x1 "10"
                      :y1 "20"
                      :x2 "190"
                      :y2 "20"
                      :style stroke-style}]
              [:line.stroke-color {:x1 "20"
                      :y1 "10"
                      :x2 "20"
                      :y2 "25"
                      :style stroke-style}]
              (when (not max-levels?)
                [:line.stroke-color {:x1 "180"
                                     :y1 "10"
                                     :x2 "180"
                                     :y2 "25"
                                     :style stroke-style}])
              (let [x2 (if max-levels?
                         (if (>= xps (opt/level-xps 20))
                           20
                           0)
                         (+ progress-length buffer 10))]
                (when (and (not (js/isNaN x2))
                         (> x2 buffer))
                  [:line {:x1 (if (pos? current-level-xps)
                                "10"
                                "20")
                          :y1 "17"
                          :x2 (str x2)
                          :y2 "17"
                          :style bar-stroke-style}]))
              [:text.main-text-color {:x "8"
                      :y "30"
                      :fill "white"
                      :font-size "8"}
               (str "Level " total-levels)]
              [:text.main-text-color {:x "9"
                      :y "36"
                      :fill "white"
                      :font-size "6"}
               current-level-xps]
              (when (not max-levels?)
                [:text.main-text-color {:x "165"
                                        :y "30"
                                        :fill "white"
                                        :font-size "8"}
                 (str "Level " (inc total-levels))])
              (when (not max-levels?)
                [:text.main-text-color {:x "165"
                                        :y "36"
                                        :fill "white"
                                        :font-size "6"}
                 next-level-xps])]]]
           (when (and (>= xps next-level-xps)
                    (= (or (:handler current-route)
                           current-route) routes/dnd-e5-char-builder-route))
             [:button.form-button
              {:on-click #(dispatch [::char/level-up id])}
              [:div.flex.align-items-c
               [svg-icon "muscle-up" 24 "white"]
               [:span.m-l-5 "Level Up"]]])]]]
        [ability-scores-section-2 id]
        [:div.flex.p-10.justify-cont-s-a
         [skills-section-2 id]
         [:div
          [armor-class-section-2 id]
          [hit-points-section-2 id]
          [speed-section-2 id]
          [saving-throws-section-2 id]
          [darkvision-section-2 id]]]
        [description-section id]
        [:span.f-s-18.f-w-b.m-b-5 "Notes"]
        [:div.p-l-20.p-r-20
         [:div.w-100-p
          [comps/input-field
           :textarea
           @(subscribe [::char/notes id])
           (set-notes-handler id)
           {:style notes-style
            :maxLength (:notes branding/field-limits)
            :class-name "input"}]]]]]]]))

(defn weapon-details-field [nm value]
  [:div.p-2
   [:span.f-w-b nm ":"]
   [:span.m-l-5 value]])

(defn yes-no [v]
  (if v "yes" "no"))

(defn weapon-details [{:keys [::weapon/description
                              ::weapon/type
                              ::weapon/damage-type
                              ::mi/magical-damage-bonus
                              ::mi/magical-attack-bonus
                              ::mi/magical-damage-type
                              ::weapon/ranged?
                              ::weapon/melee?
                              ::weapon/range
                              ::weapon/two-handed?
                              ::weapon/finesse?
                              ::mi/magical-finesse?
                              ::weapon/link
                              ::weapon/versatile
                              ::weapon/thrown]
                       :as weapon}
                      damage-modifier-fn]
  [:div.m-t-10.i
   (weapon-details-field "Type" (common/safe-name type))
   (if magical-damage-type
     (weapon-details-field "Damage Type" (common/safe-name magical-damage-type))
     (weapon-details-field "Damage Type" (common/safe-name damage-type)))
   (when magical-damage-bonus
     (weapon-details-field "Magical Damage Bonus" magical-damage-bonus))
   (when magical-attack-bonus
     (weapon-details-field "Magical Attack Bonus" magical-attack-bonus))
   (weapon-details-field "Melee/Ranged" (if melee? "melee" "ranged"))
   (when range
     (weapon-details-field "Range" (str (::weapon/min range) "/" (::weapon/max range) " ft.")))
   (if magical-finesse?
     (weapon-details-field "Finesse?" (yes-no magical-finesse?))
     (weapon-details-field "Finesse?" (yes-no finesse?)))
   (weapon-details-field "Two-handed?" (yes-no two-handed?))
   (weapon-details-field "Versatile" (if versatile
                                       (str (::weapon/damage-die-count versatile)
                                            "d"
                                            (::weapon/damage-die versatile)
                                            (common/mod-str (damage-modifier-fn weapon false))
                                            " damage")
                                       "no"))
   (when description
     [:div.m-t-10 description])])

(defn armor-details-section [{:keys [type
                                     base-ac
                                     weight
                                     description
                                     max-dex-mod
                                     min-str
                                     ::mi/magical-ac-bonus
                                     stealth-disadvantage?]
                              :or {magical-ac-bonus 0
                                   base-ac 10}}
                             {shield-magic-bonus ::magical-ac-bonus :or {shield-magic-bonus 0} :as shield}
                             expanded?]
  [:div
   [:div (str (when type (str (common/safe-name type) ", ")) "base AC " (+ magical-ac-bonus shield-magic-bonus base-ac (if shield 2 0)) (when stealth-disadvantage? ", stealth disadvantage"))]
   (when expanded?
     [:div
      [:div.m-t-10.i
       (when type
         (weapon-details-field "Type" (common/safe-name type)))
       (weapon-details-field "Base AC" base-ac)
       (when (not= magical-ac-bonus 0)
         (weapon-details-field "Magical AC Bonus" magical-ac-bonus))
       (when shield
         (weapon-details-field "Shield Base AC Bonus" 2))
       (when (and shield
                (not= shield-magic-bonus 0))
         (weapon-details-field "Shield Magical AC Bonus" shield-magic-bonus))
       (when max-dex-mod
         (weapon-details-field "Max DEX AC Bonus" max-dex-mod))
       (when min-str
         (weapon-details-field "Min Strength" min-str))
       (weapon-details-field "Stealth Disadvantage?" (yes-no stealth-disadvantage?))
       (when weight
         (weapon-details-field "Weight" (str weight " lbs.")))
       (when description
         [:div.m-t-10 (str "Armor: " description)])
       (when (:description shield)
         [:div.m-t-10 (str "Shield: " (:description shield))])]])])

(defn boolean-icon [v]
  [:i.fa {:class (if v "fa-check green" "fa-times red")}])

(defn toggle-details-expanded-fn [expanded-details k]
  #(swap! expanded-details (fn [d] (update d k not))))

(def toggle-details-expanded-handler (memoize toggle-details-expanded-fn))

(defn armor-section-2 []
  (let [expanded-details (r/atom {})]
    (fn [id]
      (let [all-armor @(subscribe [::char/all-armor id])
            ac-with-armor @(subscribe [::char/armor-class-with-armor id])
            armor-profs (set @(subscribe [::char/armor-profs id]))
            device-type @(subscribe [:device-type])
            mobile? (= :mobile device-type)
            proficiency-bonus @(subscribe [::char/proficiency-bonus id])
            all-armor-details (map @(subscribe [::mi/all-armor-map]) (keys all-armor))
            armor-details (armor/non-shields all-armor-details)
            shield-details (armor/shields all-armor-details)]
        [:div
         [:div.flex.align-items-c
          (svg-icon "breastplate" 32)
          [:span.m-l-5.f-w-b.f-s-18 "Armor"]]
         [:div
          [:table.w-100-p.t-a-l.striped
           [:tbody.armor
            [:tr.f-w-b
             {:class (when mobile? "f-s-12")}
             [:th.p-10 "Name"]
             (when (not mobile?) [:th.p-10 "Proficient?"])
             [:th.p-10 "Details"]
             [:th.p-10 "AC"]
             [:th.p-10]]
            (doall
             (for [{:keys [name description type key] :as armor} (conj armor-details nil)
                   shield (conj shield-details nil)]
               (let [k (str key (:key shield))
                     ac (ac-with-armor armor shield)
                     proficient? (and
                                  (or (nil? shield)
                                      (armor-profs :shields))
                                  (or
                                   (nil? armor)
                                   (armor-profs key)
                                   (armor-profs type)))
                     expanded? (@expanded-details k)]
                 ^{:key (str key (:key shield))}
                 [:tr.item.pointer
                  {:on-click (toggle-details-expanded-handler expanded-details k)}
                  [:td.p-10.f-w-b.armor (str (or (::mi/name armor) (:name armor) "unarmored")
                                       (when shield (str " + " (:name shield))))]
                  (when (not mobile?)
                    [:td.p-10.proficient (boolean-icon proficient?)])
                  [:td.p-10.w-100-p
                   [:div
                    (armor-details-section armor shield expanded?)]]
                  [:td.p-10.f-w-b.f-s-18 ac]
                  [:td.pointer
                   [:div.orange
                    #_(if (not mobile?)
                        [:span.underline (if expanded? "less" "more")])
                    [:i.fa.m-l-5
                     {:class (if expanded? "fa-caret-up" "fa-caret-down")}]]]])))]]]]))))

(defn section-header [icon title]
  [:div.flex.align-items-c
   (svg-icon icon 32)
   [:span.m-l-5.f-w-b.f-s-18 title]])

(defn weapons-section-2 []
  (let [expanded-details (r/atom {})]
    (fn [id]
      (let [all-weapons @(subscribe [::char/all-weapons id])
            weapon-profs (set @(subscribe [::char/weapon-profs id]))
            weapon-attack-modifier @(subscribe [::char/best-weapon-attack-modifier-fn id])
            weapon-damage-modifier @(subscribe [::char/best-weapon-damage-modifier-fn id])
            has-weapon-prof @(subscribe [::char/has-weapon-prof id])
            device-type @(subscribe [:device-type])
            mobile? (= :mobile device-type)
            proficiency-bonus @(subscribe [::char/proficiency-bonus id])
            all-weapons-map @(subscribe [::mi/all-weapons-map])]
        [:div
         [section-header "crossed-swords" "Weapons"]
         [:div
          [:table.w-100-p.t-a-l.striped
           [:tbody.weapons
            [:tr.f-w-b
             {:class (when mobile? "f-s-12")}
             [:th.p-10 "Name"]
             (when (not mobile?) [:th.p-10 "Proficient?"])
             [:th.p-10 "Details"]
             [:th.t-a-c (if mobile? "Atk" [:div.w-60 "Attack"])]
             [:th.t-a-c (if mobile? "Dmg" [:div.w-60 "Damage"])]
             [:th.p-10]]
            (doall
             (map
              (fn [[weapon-key {:keys [equipped?]}]]
                (let [{:keys [name description ranged? ::weapon/type ::weapon/damage-die-count ::weapon/damage-die ::weapon/versatile] :as weapon} (all-weapons-map weapon-key)
                      proficient? (when has-weapon-prof (has-weapon-prof weapon))
                      expanded? (@expanded-details weapon-key)
                      damage-modifier (weapon-damage-modifier weapon)
                      versatile-damage-die-count (:orcpub.dnd.e5.weapons/damage-die-count versatile)
                      versatile-damage-die (:orcpub.dnd.e5.weapons/damage-die versatile)
                      droll (str damage-die-count "d" damage-die)]
                  (when (not= type :ammunition)
                    ^{:key weapon-key}
                    [:tr.weapon.pointer
                     {:on-click (toggle-details-expanded-handler expanded-details weapon-key)}
                     [:td.p-10.f-w-b (or (:name weapon)
                                         (::mi/name weapon))]
                     (when (not mobile?)
                       [:td.p-10 (boolean-icon proficient?)])
                     [:td.p-10.w-100-p
                      [:div
                       (weapon-attack-description-short weapon)]
                      (when expanded?
                        (weapon-details weapon weapon-damage-modifier))]
                     [:td (roll-button
                           (str name " attack: ")
                           (str "1d20" (common/mod-str (weapon-attack-modifier weapon)))
                           :text (str "1d20" (common/mod-str (weapon-attack-modifier weapon))))]
                     [:td (roll-button
                           (str name " damage: ")
                           (str damage-die-count "d" damage-die (common/mod-str (weapon-damage-modifier weapon)))
                           :text (str damage-die-count "d" damage-die (common/mod-str (weapon-damage-modifier weapon)))
                           :style {:width "100%"})
                      (when versatile
                        (roll-button
                         (str name " versatile damage: ")
                         (str versatile-damage-die-count "d" versatile-damage-die (common/mod-str (weapon-damage-modifier weapon)))
                         :text (str "v " versatile-damage-die-count "d" versatile-damage-die (common/mod-str (weapon-damage-modifier weapon)))
                         :style {:width "100%"}))]
                     [:td.pointer
                      [:div.orange
                       #_(if (not mobile?)
                           [:span.underline (if expanded? "less" "more")])
                       [:i.fa.m-l-5
                        {:class (if expanded? "fa-caret-up" "fa-caret-down")}]]]])))
              all-weapons))]]]]))))

(defn magic-item-rows [expanded-details magic-item-cfgs magic-weapon-cfgs magic-armor-cfgs]
  (let [magic-item-map @(subscribe [::mi/all-magic-items-map])
        mobile? @(subscribe [:mobile?])]
    (mapcat
     (fn [[item-kw item-cfg]]
       (let [{:keys [::mi/name ::mi/type ::mi/item-subtype ::mi/rarity ::mi/attunement ::mi/description ::mi/summary] :as item} (magic-item-map item-kw)
             expanded? (@expanded-details item-kw)]
         [[:tr.pointer
           {:on-click (toggle-details-expanded-handler expanded-details item-kw)}
           [:td.p-10.f-w-b (or (:name item) name)]
           [:td.p-10 (str (common/kw-to-name type)
                          ", "
                          (common/kw-to-name rarity))]
           [:td.p-r-5.pointer
            [:div.orange
             #_(if (not mobile?)
                 [:span.underline (if expanded? "less" "more")])
             [:i.fa.m-l-5
              {:class (if expanded? "fa-caret-up" "fa-caret-down")}]]]]
          (when expanded?
            [:tr
             [:td.p-10
              {:col-span 3}
              [item-component item true true]]])]))
     (merge
      magic-item-cfgs
      magic-weapon-cfgs
      magic-armor-cfgs))))

(defn magic-items-section-2 []
  (let [expanded-details (r/atom {})]
    (fn [id]
      (let [mobile? @(subscribe [:mobile?])
            magic-item-cfgs @(subscribe [::char/magic-items id])
            magic-weapon-cfgs @(subscribe [::char/magic-weapons id])
            magic-armor-cfgs @(subscribe [::char/magic-items id])]
        [:div
         [:div.flex.align-items-c
          (svg-icon "orb-wand" 32)
          [:span.m-l-5.f-w-b.f-s-18 "Other Magic Items"]]
         [:div.f-s-14
          [:table.w-100-p.t-a-l.striped
           [:tbody.other-magic-items
            [:tr.f-w-b
             {:class (when mobile? "f-s-12")}
             [:th.p-10 "Name"]
             [:th.p-10 "Details"]
             [:th]]
            (doall
             (map-indexed
              (fn [i row]
                (with-meta
                  row
                  {:key i}))
              (magic-item-rows expanded-details
                               magic-item-cfgs
                               magic-weapon-cfgs
                               magic-armor-cfgs)))]]]]))))

(defn other-equipment-section-2 []
  (let [expanded-details (r/atom {})]
    (fn [id]
      (let [mobile? @(subscribe [:mobile?])
            equipment-cfgs (merge
                            @(subscribe [::char/equipment id])
                            (zipmap (range) @(subscribe [::char/custom-equipment id])))]
        [:div
         [:div.flex.align-items-c
          (svg-icon "backpack" 32)
          [:span.m-l-5.f-w-b.f-s-18 "Other Equipment"]]
         [:div
          [:table.w-100-p.t-a-l.striped
           [:tbody.equipment
            [:tr.f-w-b
             {:class (when mobile? "f-s-12")}
             [:th.p-10 "Name"]
             [:th.p-10 "Qty."]
             [:th.p-10 "Details"]
             [:th]]
            (doall
             (map
              (fn [[item-kw item-cfg]]
                (let [item-name (::char-equip/name item-cfg)
                      {:keys [name cost weight] :as item} (equip/equipment-map item-kw)
                      ;;expanded? (@expanded-details item-kw)
                      ]
                  ^{:key item-kw}
                  [:tr.item
                   [:td.p-10.f-w-b (or (:name item) item-name)]
                   [:td.p-10 (::char-equip/quantity item-cfg)]
                   [:td.p-10
                    [:div
                     [:div
                      (str (when cost
                             (str (:num cost)
                                  " "
                                  (common/safe-name (:type cost))
                                  ", "))
                           weight)]]]]))
              equipment-cfgs))]]]]))))

(defn treasure-section []
  (r/with-let [expanded-details (r/atom {})]
    (fn [id]
      (let [mobile? @(subscribe [:mobile?])
            treasure-cfgs (merge
                             @(subscribe [::char/treasure id])
                             (zipmap (range) @(subscribe [::char/custom-treasure id])))]
        [:div
         [:div.flex.align-items-c
          (svg-icon "cash" 32)
          [:span.m-l-5.f-w-b.f-s-18 "Treasure"]]
         [:div
          [:table.w-100-p.t-a-l.striped
           [:tbody
            [:tr.f-w-b
             {:class (when mobile? "f-s-12")}
             [:th.p-10 "Name"]
             [:th.p-10 "Qty."]
             [:th]]
            (doall
             (map
              (fn [[treasure-kw treasure-cfg]]
                (let [treasure-name (::char-equip/name treasure-cfg)
                      {:keys [::equip/name] :as treasure} (equip/treasure-map treasure-kw)]
                  ^{:key treasure-kw}
                  [:tr
                   [:td.p-10.f-w-b (or (:name treasure) treasure-name)]
                   [:td.p-10 (::char-equip/quantity treasure-cfg)]]))
              treasure-cfgs))]]]]))))


(defn skill-details-section-2 []
  (let [expanded-details (r/atom {})]
    (fn [id]
      (let [skill-profs (or @(subscribe [::char/skill-profs id]) #{})
            skill-bonuses @(subscribe [::char/skill-bonuses id])
            skill-expertise @(subscribe [::char/skill-expertise id])
            device-type @(subscribe [:device-type])
            mobile? (= :mobile device-type)]
        [:div
         [:div.flex.align-items-c
          (svg-icon "juggler" 32)
          [:span.m-l-5.f-w-b.f-s-18 "Skills"]]
         [:div
          [:table.w-100-p.t-a-l.striped
           [:tbody
            [:tr.f-w-b
             {:class (when mobile? "f-s-12")}
             [:th.p-5 "Name"]
             [:th.p-5 (if mobile? "Prof?" "Proficient?")]
             (when skill-expertise
               [:th.p-5 "Expertise?"])
             [:th.p-5 (when (not mobile?) [:div.w-40 "Bonus"])]]
            (doall
             (map
              (fn [{:keys [key name]}]
                (let [proficient? (key skill-profs)
                      expertise? (key skill-expertise)]
                  ^{:key key}
                  [:tr
                   [:td.p-5.f-w-b name]
                   [:td.p-5 (boolean-icon proficient?)]
                   (when skill-expertise
                     [:td.p-5 (boolean-icon expertise?)])
                   [:td.p-5.f-s-18.f-w-b (roll-button
                         (str name " check: ")
                         (str "1d20" (common/mod-str (key skill-bonuses)))
                         :text (common/bonus-str (key skill-bonuses)))]]))
              skills/skills))]]]]))))

(defn tool-prof-details-section-2 []
  (let [expanded-details (r/atom {})]
    (fn [id]
      (let [tool-profs (or @(subscribe [::char/tool-profs id]) #{})
            tool-expertise @(subscribe [::char/tool-expertise id])
            tool-bonus-fn @(subscribe [::char/tool-bonus-fn id])
            device-type @(subscribe [:device-type])
            mobile? (= :mobile device-type)]
        (when (seq tool-profs)
          [:div
           [:div.flex.align-items-c
            (svg-icon "stone-crafting" 32)
            [:span.m-l-5.f-w-b.f-s-18 "Tools"]]
           [:div
            [:table.w-100-p.t-a-l.striped
             [:tbody
              [:tr.f-w-b
               {:class (when mobile? "f-s-12")}
               [:th.p-10 "Name"]
               [:th.p-10 (if mobile? "Prof?" "Proficient?")]
               (when tool-expertise
                 [:th.p-10 "Expertise?"])
               [:th.p-10 (when (not mobile?) [:div.w-40 "Bonus"])]]
              (doall
               (map
                (fn [[kw]]
                  (let [name (-> equip/tools-map kw :name)
                        proficient? (kw tool-profs)
                        expertise? (kw tool-expertise)]
                    ^{:key kw}
                    [:tr
                     [:td.p-10.f-w-b name]
                     [:td.p-10 (boolean-icon proficient?)]
                     (when tool-expertise
                       [:td.p-10 (boolean-icon expertise?)])
                     [:td.p-10.f-s-18.f-w-b (common/bonus-str (tool-bonus-fn kw))]
                     [:td (roll-button (str name " check: ") (str "1d20" (common/mod-str (tool-bonus-fn kw))))]]))
                tool-profs))]]]])))))


(defn proficiency-details [num-columns id]
  (let [ability-bonuses @(subscribe [::char/ability-bonuses id])
        language-map @(subscribe [::langs/language-map])]
    [:div.details-columns
     {:class (when (= 2 num-columns) "flex")}
     [:div.flex-grow-1.details-column-2
      {:class (when (= 2 num-columns) "w-50-p m-l-20")}
      [skill-details-section-2 id]
      [:div.m-t-20
       [tool-prof-details-section-2 id]]
      [list-item-section "Languages" "lips" @(subscribe [::char/languages id]) (partial prof-name language-map)]
      [list-item-section "Tool Proficiencies" "stone-crafting" @(subscribe [::char/tool-profs id]) (fn [[kw]] (prof-name equip/tools-map kw))]
      [list-item-section "Weapon Proficiencies" "bowman" @(subscribe [::char/weapon-profs id]) (partial prof-name @(subscribe [::mi/custom-and-standard-weapons-map]))]
      [list-item-section "Armor Proficiencies" "mailed-fist" @(subscribe [::char/armor-profs id]) (partial prof-name armor/armor-map)]]]))

(defn equipped-section-dropdown [label cfg]
  [:div.m-t-10.m-r-5
   [labeled-dropdown
    label
    cfg]])

(def none-item
  {:value :none
   :title "<none>"})

(defn wield-fn [event-kw id]
  #(dispatch [event-kw id (keyword %)]))

(def wield-handler (memoize wield-fn))

(defn equipped? [v]
  (and (some? v)
       (not= :none v)))

(defn obj-to-item [{:keys [name key]}]
  {:title name
   :value key})

(defn equipped-section [id]
  [:div
   [section-header "battle-gear" "Equipped Items"]
   [:div
    (let [all-armor-map @(subscribe [::mi/all-armor-map])
          worn-armor @(subscribe [::char/worn-armor id])
          wielded-shield @(subscribe [::char/wielded-shield id])
          best-armor-combo @(subscribe [::char/best-armor-combo])]
      [:div.flex.flex-wrap
       (let [carried-armor @(subscribe [::char/carried-armor id])]
         [equipped-section-dropdown
          "Worn Armor"
          {:items (cons
                   none-item
                   (map
                    (fn [[key]]
                      (let [{:keys [name] :as item} (all-armor-map key)]
                        {:title (weapon-name item)
                         :value key}))
                    carried-armor))
           :value (or worn-armor (-> best-armor-combo :armor :key))
           :on-change (wield-handler ::char/don-armor id)}])
       (let [carried-shields @(subscribe [::char/carried-shields id])]
         [equipped-section-dropdown
          "Wielded Shield"
          {:items (cons
                   none-item
                   (map
                    (fn [[key]]
                      (let [{:keys [name] :as item} (all-armor-map key)]
                        {:title (weapon-name item)
                         :value key}))
                    carried-shields))
           :value (or wielded-shield (-> best-armor-combo :shield :key))
           :on-change (wield-handler ::char/wield-shield id)}])])
    (let [all-weapons-map @(subscribe [::mi/all-weapons-map])
          carried-weapons @(subscribe [::char/carried-weapons id])
          main-hand-weapon-kw @(subscribe [::char/main-hand-weapon id])
          main-hand-weapon (all-weapons-map main-hand-weapon-kw)
          off-hand-weapon-kw @(subscribe [::char/off-hand-weapon id])
          dual-wield-weapon? @(subscribe [::char/dual-wield-weapon-fn id])]
      [:div.flex.flex-wrap
       [equipped-section-dropdown
        "Main Hand Weapon"
        {:items (cons
                 none-item
                 (map
                  (fn [[key]]
                    (let [{:keys [name] :as item} (all-weapons-map key)]
                      {:title (weapon-name item)
                       :value key}))
                  carried-weapons))
         :value main-hand-weapon-kw
         :on-change (wield-handler ::char/wield-main-hand-weapon id)}]
       (when (or (equipped? off-hand-weapon-kw)
               (and (equipped? main-hand-weapon-kw)
                    (dual-wield-weapon? main-hand-weapon)))
         [equipped-section-dropdown
          "Off Hand Weapon"
          {:items (cons
                   none-item
                   (sequence
                    (comp
                     (filter
                      (fn [[key]]
                        (-> all-weapons-map
                            key
                            dual-wield-weapon?)))
                     (map
                      (fn [[key]]
                        (let [{:keys [name] :as item} (all-weapons-map key)]
                          {:title (weapon-name item)
                           :value key}))))
                    carried-weapons))
           :value off-hand-weapon-kw
           :on-change (wield-handler ::char/wield-off-hand-weapon id)}])
       #_[:div.flex.flex-wrap
          [equipped-section-dropdown
           "Attuned Magic Item 1"
           {:items [none-item]
            :value nil
            :on-change (fn [])}]
          [equipped-section-dropdown
           "Attuned Magic Item 2"
           {:items [none-item]
            :value nil
            :on-change (fn [])}]
          [equipped-section-dropdown
           "Attuned Magic Item 3"
           {:items [none-item]
            :value nil
            :on-change (fn [])}]]])]])

(defn combat-details [num-columns id]
  (let [weapon-profs @(subscribe [::char/weapon-profs id])
        armor-profs @(subscribe [::char/armor-profs id])
        resistances @(subscribe [::char/resistances id])
        damage-immunities @(subscribe [::char/damage-immunities id])
        damage-vulnerabilities @(subscribe [::char/damage-vulnerabilities id])
        condition-immunities @(subscribe [::char/condition-immunities id])
        immunities @(subscribe [::char/immunities id])
        weapons @(subscribe [::char/weapons id])
        armor @(subscribe [::char/armor id])
        magic-weapons @(subscribe [::char/magic-weapons id])
        magic-armor @(subscribe [::char/magic-armor id])
        critical-hit-values @(subscribe [::char/critical-hit-values id])
        non-standard-crits? (> (count critical-hit-values) 1)
        number-of-attacks @(subscribe [::char/number-of-attacks id])
        non-standard-attack-number? (> number-of-attacks 1)]
    [:div
     [:div.flex.flex-wrap.justify-cont-s-a.t-a-c
      [armor-class-section-2 id]
      [hit-points-section-2 id]
      [speed-section-2 id]
      [initiative-section-2 id]]
     (when (or non-standard-crits?
             non-standard-attack-number?)
       [:div.flex.justify-cont-s-a.t-a-c
        [critical-hits-section-2 id]
        [hit-dice-section-2 id]
        [number-of-attacks-section-2 id]])
     [:div.m-t-30
      [attacks-section id]]
     [:div.m-t-30
      [equipped-section id]]
     [:div.m-t-30
      [list-item-section "Damage Resistances" "surrounded-shield" resistances resistance-str]]
     [:div.m-t-30
      [list-item-section "Damage Vulnerabilities" nil damage-vulnerabilities resistance-str]]
     [:div.m-t-30
      [list-item-section "Damage Immunities" nil damage-immunities resistance-str]]
     [:div.m-t-30
      [list-item-section "Condition Immunities" nil condition-immunities resistance-str]]
     [:div.m-t-30
      [list-item-section "Immunities" nil immunities resistance-str]]
     [:div.m-t-30
      [weapons-section-2 id]]
     [:div.m-t-30
      [armor-section-2 id]]
     [:div
      {:class (when (= 2 num-columns) "w-50-p m-l-20")}
      [list-item-section "Weapon Proficiencies" "bowman" weapon-profs (partial prof-name @(subscribe [::mi/custom-and-standard-weapons-map]))]
      [list-item-section "Armor Proficiencies" "mailed-fist" armor-profs (partial prof-name armor/armor-map)]]]))

(defn has-frequency-units? [trait]
  (some-> trait :frequency :units))

(defn features-details [num-columns id]
  (let [resistances @(subscribe [::char/resistances id])
        damage-immunities @(subscribe [::char/damage-immunities id])
        damage-vulnerabilities @(subscribe [::char/damage-vulnerabilities id])
        condition-immunities @(subscribe [::char/condition-immunities id])
        immunities @(subscribe [::char/immunities id])
        traits-by-type (group-by :type @(subscribe [::char/traits id]))
        actions (concat @(subscribe [::char/actions id])
                        (traits-by-type :action))
        bonus-actions (concat @(subscribe [::char/bonus-actions id])
                              (traits-by-type :b-action))
        reactions (concat @(subscribe [::char/reactions id])
                          (traits-by-type :reaction))
        traits (concat (traits-by-type nil)
                       (traits-by-type :other))
        attacks @(subscribe [::char/attacks id])
        all-traits (concat actions bonus-actions reactions traits attacks)
        freqs (set (map has-frequency-units? all-traits))]
    [:div.details-columns
     {:class (when (= 2 num-columns) "flex")}

     [:div.flex-grow-1.details-column-2
      {:class (when (= 2 num-columns) "w-50-p m-l-20")}
      [list-item-section "Damage Resistances" "surrounded-shield" resistances resistance-str]
      [list-item-section "Damage Vulnerabilities" nil damage-vulnerabilities resistance-str]
      [list-item-section "Damage Immunities" nil damage-immunities resistance-str]
      [list-item-section "Condition Immunities" nil condition-immunities resistance-str]
      [list-item-section "Immunities" nil immunities resistance-str]
      [:div.flex.justify-cont-end.align-items-c
       (when (or (freqs ::units/long-rest)
               (freqs ::units/rest))
         [finish-long-rest-button id])
       (when (or (freqs ::units/short-rest)
               (freqs ::units/rest))
          [finish-short-rest-button id])
       (when (freqs ::units/round)
         [:button.form-button.p-5.m-l-5
          {:on-click (make-event-handler ::char/new-round id)}
          "new round"])
       (when (freqs ::units/turn)
         [:button.form-button.p-5.m-l-5
          {:on-click (make-event-handler ::char/new-turn id)}
          "new turn"])]
      [attacks-section id]
      [actions-section id "Actions" "beams-aura" actions]
      [actions-section id "Bonus Actions" "run" bonus-actions]
      [actions-section id "Reactions" "van-damme-split" reactions]
      [actions-section id "Features, Traits, and Feats" "vitruvian-man" traits]]]))

(defn spell-details [num-columns id]
  (let [spells-known @(subscribe [::char/spells-known id])
        spell-slots @(subscribe [::char/spell-slots id])
        spell-modifiers @(subscribe [::char/spell-modifiers id])
        spell-slot-factors @(subscribe [::char/spell-slot-factors id])
        total-spellcaster-levels @(subscribe [::char/total-spellcaster-levels id])
        levels @(subscribe [::char/levels id])]
    [:div.details-columns
     {:class (when (= 2 num-columns) "flex")}
     [:div.flex-grow-1.details-column-2
      {:class (when (= 2 num-columns) "w-50-p m-l-20")}
      (when (seq spells-known) [spells-known-section
                              id
                              spells-known
                              spell-slots
                              spell-modifiers
                              spell-slot-factors
                              total-spellcaster-levels
                              levels])]]))

(defn equipment-details [num-columns id]
  [:div
   [:div.m-t-30
    [equipped-section id]]
   [:div.m-t-30
    [weapons-section-2 id]]
   [:div.m-t-30
    [armor-section-2 id]]
   [:div.m-t-30
    [magic-items-section-2 id]]
   [:div.m-t-30
    [other-equipment-section-2 id]]
   [:div.m-t-30
    [treasure-section id]]])

(defn details-tab [title icon device-type selected? on-select]
  [:div.b-b-2.f-w-b.pointer.p-10.hover-opacity-full
   {:class (if selected? "b-orange" "b-gray")
    :on-click on-select}
   [:div.hover-opacity-full
    {:class (when (not selected?) "opacity-5")}
    [:div (svg-icon icon 24)]
    (when (= device-type :desktop)
      [:div.uppercase.f-s-10
       title])]])


(def details-tabs
  {"summary" {:icon "stabbed-note"
              :view summary-details}
   "combat" {:icon "sword-clash"
             :view combat-details}
   "proficiencies" {:icon "juggler"
                    :view proficiency-details}
   "spells" {:icon "spell-book"
             :view spell-details}
   "features" {:icon "vitruvian-man"
               :view features-details}
   "equipment" {:icon "backpack"
                :view equipment-details}})

(defn option-title [kw]
  (str common/dot-char " " (common/kw-to-name kw)))

(declare options-display)

(defn option-display [{:keys [::entity/key ::entity/options]}]
  [:div
   [:span (option-title key)]
   (when (seq options)
     [:div.p-l-20
      [options-display options]])])

(defn options-display [options]
  [:div
   (doall
    (map
     (fn [[k v]]
       ^{:key k}
       [:div
        [:span (option-title k)]
        [:div.p-l-20
         (if (vector? v)
           (doall
            (map
             (fn [option]
               ^{:key (::entity/key option)}
               [option-display option])
             v))
           (option-display v))]])
     options))])

(defn character-selections [id]
  (let [character @(subscribe [::char/character id])]
    [:div.p-20
     [:span.f-w-b.f-s-24 "Selections"]
     [:div
      (options-display (::entity/options character))]]))

(defn blank-feature-name?
  "True when a feature/action name is missing or whitespace-only — the data
   shape that most often chokes the character display. Public so it can be
   unit-tested directly."
  [n]
  (or (nil? n)
      (and (string? n) (s/blank? n))))

(defn suspected-broken-features
  "Best-effort diagnosis for the recovery panel. The character-section crash is
   almost always a feature/action/reaction whose definition is missing a :name
   (frequently imported/homebrew content). Return short descriptions of any such
   items so we can tell the user exactly what to look for and fix, instead of
   leaving them to guess. Safe to call even when nothing is wrong (returns [])."
  [id]
  (->> (concat @(subscribe [::char/traits id])
               @(subscribe [::char/actions id])
               @(subscribe [::char/bonus-actions id])
               @(subscribe [::char/reactions id]))
       (filter #(blank-feature-name? (:name %)))
       (map (fn [{:keys [summary description page]}]
              (let [d (or summary description "(no description available)")
                    d (if (> (count d) 160) (str (subs d 0 160) "…") d)]
                (if page (str d " (book p." page ")") d))))
       distinct
       vec))

(defn feature-render-error
  "Recovery panel shown in place of a character section that failed to render.
   General fail-soft: we do NOT try to guess the cause — by design this catches
   failures we did not anticipate and cannot name. What we CAN always tell the
   user is which section broke, that their data is safe, and how to recover
   (try again / reload / open it in the builder to locate it). The exact error
   is one click away for a bug report. The specific malformed item, when a single
   item is at fault, is surfaced in place by the per-item render-guard / the
   bisection isolator (see isolate-culprit)."
  [_section _id _error _stack _retry]
  (let [show-details? (r/atom false)
        copied? (r/atom false)
        culprit-cache (r/atom ::unset)]      ; isolation is expensive — run it once
    (fn [section id error stack retry]
      (let [character @(subscribe [::char/character id])   ; nil id resolves to the active/builder character
            section (when (and (string? section) (not (s/blank? section))) section)
            report (str (or (some-> error .-stack) (str error))
                        (when stack (str "\n\nComponent stack:" stack)))
            ;; trace the failure to the builder selection behind it (Features only
            ;; for now — its re-test is the name sort). Cached: many rebuilds.
            culprit (if (not= ::unset @culprit-cache)
                      @culprit-cache
                      (reset! culprit-cache
                              (when (and character (= section "Features"))
                                (try
                                  (let [template @(subscribe [::char/built-template id])]
                                    (isolate-culprit-selection character template features-section-fails?))
                                  (catch :default _ nil)))))
            culprit-label (when culprit
                            (culprit-selection-label (::entity/options character) culprit))]
        [:div.p-20.f-s-14
         [:div.f-w-b.f-s-18.m-b-10
          (if section
            (str "The " section " section couldn’t be displayed")
            "This section couldn’t be displayed")]
         (when (and culprit-label (not (s/blank? culprit-label)))
           [:div.m-b-15.p-10.l-h-19
            {:style {:border "1px solid #e9a227" :border-radius "5px"
                     :background "rgba(233,162,39,0.12)"}}
            [:div.f-w-b "We traced it to one choice:"]
            [:div.m-t-5.f-w-600 culprit-label]
            [:div.m-t-5 "Open the builder and re-pick that option."]])
         [:div.m-b-15.l-h-19
          "Something in this character’s data stopped "
          (if section (str "the " section " section") "this section")
          " from loading. This is a display error — it didn’t change or delete your "
          "character. The fix is in the builder: open it and re-select the option "
          "behind this section. If the data looks fine, this may be a glitch — reload the page."]
         [:div.flex.flex-wrap.align-items-c
          ;; the editor is the only thing that actually fixes a data problem, so it
          ;; leads. Reload is secondary (only helps a transient glitch).
          (when character
            [:button.form-button.m-r-10.m-b-5
             {:on-click #(dispatch [:edit-character character])}
             "Edit this character"])
          [:button.form-button.m-b-5
           {:on-click #(.reload js/window.location)}
           "Reload page"]]
         [:div.m-t-10
          [:span.orange.underline.pointer
           {:on-click #(swap! show-details? not)}
           (if @show-details? "Hide technical details" "Show technical details")]
          (when @show-details?
            [:div
             [:div.flex.align-items-c.m-t-5
              [:span.f-s-12.m-r-10 "Include this when reporting the problem:"]
              [:button.form-button.f-s-11
               {:on-click (fn []
                            (some-> js/navigator .-clipboard (.writeText report))
                            (reset! copied? true))}
               (if @copied? "Copied!" "Copy")]]
             ;; selectable + copy-button so the user can actually grab it
             [:pre.f-s-12.m-t-5.wsp-prw {:style {:user-select "text"}} report]])]]))))

(defn error-boundary
  "React error boundary. If a child throws while rendering, render
   (fallback error retry) instead of letting the exception unmount the whole app
   (the black screen). Give the boundary a :key that changes with its content
   (e.g. the selected tab) so navigating away clears the error automatically.

   Recovery is driven by React's static getDerivedStateFromError, which is the
   only React-18 hook that re-renders the boundary to show a fallback (a
   setState/atom reset in componentDidCatch alone does NOT). component-did-catch
   runs in the commit phase and is where React hands us the component stack — the
   one generically-available 'where' for an error we can't otherwise diagnose — so
   we both log it and stash it in state for the fallback to surface.

   The fallback is called as (fallback error component-stack retry)."
  [_fallback _child]
  (r/create-class
   {:display-name "error-boundary"
    :constructor (fn [this _props]
                   (set! (.-state this) #js {:error nil :componentStack nil}))
    :get-derived-state-from-error (fn [error] #js {:error error})
    :component-did-catch (fn [this e info]
                           (let [stack (some-> info .-componentStack)]
                             (js/console.error "[character render error]" e stack)
                             ;; Stash the component stack so the recovery panel can show
                             ;; *which* component threw — the only location info we can
                             ;; offer for a failure we didn't anticipate.
                             (.setState this #js {:componentStack stack})))
    :reagent-render
    (fn [fallback child]
      (let [this (r/current-component)]
        (if-let [e (.. this -state -error)]
          (fallback e
                    (.. this -state -componentStack)
                    #(.setState this #js {:error nil :componentStack nil}))
          child)))}))

(defn app-error-fallback
  "Top-level catch-all shown when ANY page throws below the app-root boundary —
   the guarantee that the app never black-screens (goal #1). Generic by design (it
   can be any page): say something broke, reassure the data is safe, offer recovery,
   and expose the error for a report. Finer boundaries below this one give better,
   more specific messages where they apply; this is the last line of defense."
  [_error _stack _retry]
  (let [show? (r/atom false)
        copied? (r/atom false)]
    (fn [error stack retry]
      (let [report (str (or (some-> error .-stack) (str error))
                        (when stack (str "\n\nComponent stack:" stack)))]
        [:div.p-20.f-s-14.main-text-color
         [:div.f-w-b.f-s-24.m-b-10 "Something went wrong on this page"]
         [:div.m-b-15.l-h-19
          "This page hit an unexpected error and couldn’t finish loading. Your saved "
          "data wasn’t affected. Head back to your characters, or reload to try again — "
          "if it keeps happening, copy the details below and send them to us."]
         [:div.flex.flex-wrap.align-items-c
          [:button.form-button.m-r-10.m-b-5
           {:on-click #(set! (.-href js/window.location) "/pages/dnd/5e/characters")}
           "Go to my characters"]
          [:button.form-button.m-b-5 {:on-click #(.reload js/window.location)} "Reload page"]]
         [:div.m-t-10
          [:span.orange.underline.pointer
           {:on-click #(swap! show? not)}
           (if @show? "Hide details" "Show details")]
          (when @show?
            [:div
             [:button.form-button.f-s-11.m-t-5
              {:on-click (fn []
                           (some-> js/navigator .-clipboard (.writeText report))
                           (reset! copied? true))}
              (if @copied? "Copied!" "Copy")]
             [:pre.f-s-12.m-t-5.wsp-prw {:style {:user-select "text"}} report]])]]))))

(defn has-nameless-feature?
  "Does the built character still carry a feature/action/reaction with no usable
   name? Used as the re-test for selection-isolation on the proactive banner."
  [built]
  (boolean
   (some #(blank-feature-name? (:name %))
         (concat (char/traits built) (char/actions built)
                 (char/bonus-actions built) (char/reactions built)))))

(defn character-health-warning
  "Proactive banner shown when the character has a feature missing its name (which
   can blank a section). Instead of dumping the feature's rules text, it traces the
   problem to the builder selection behind it (re-execution over the options tree)
   so the user knows exactly which choice to re-pick. Cached — isolation rebuilds
   the character many times. Renders nothing for healthy characters."
  [_id]
  (let [culprit-cache (r/atom ::unset)]
    (fn [id]
      (when (seq (try (suspected-broken-features id) (catch :default _ nil)))
        (let [character @(subscribe [::char/character id])
              culprit (if (not= ::unset @culprit-cache)
                        @culprit-cache
                        (reset! culprit-cache
                                (when character
                                  (try
                                    (let [template @(subscribe [::char/built-template id])]
                                      (isolate-culprit-selection character template has-nameless-feature?))
                                    (catch :default _ nil)))))
              label (when culprit (culprit-selection-label (::entity/options character) culprit))]
          [:div.p-10.m-b-10.f-s-14.l-h-19
           {:style {:border "1px solid #e9a227"
                    :border-radius "5px"
                    :background "rgba(233,162,39,0.12)"}}
           [:div.f-w-b.m-b-5 "⚠ A feature on this character is missing its name"]
           (if (and label (not (s/blank? label)))
             [:div.m-b-5 "We traced it to this choice: "
              [:span.f-w-600 label] ". Re-pick it in the builder so the feature has a name."]
             [:div.m-b-5 "Open the character in the builder and re-select the option that grants it so it has a proper name."])
           (when character
             [:button.form-button.m-t-5
              {:on-click #(dispatch [:edit-character character])}
              "Open in the builder"])])))))

(defn character-display []
  (let [show-selections? (r/atom false)]
    (fn [id show-summary? num-columns]
      (let [device-type @(subscribe [:device-type])
            selected-tab @(subscribe [::char/selected-display-tab])
            two-columns? (= 2 num-columns)
            tab (or selected-tab
                    (if two-columns?
                      "combat"
                      "summary"))]
        [:div.w-100-p
         [:div
          [character-health-warning id]
          (when show-summary?
            [:div.f-s-24.f-w-600.m-b-16.m-l-20.text-shadow.flex
             [character-summary id true true]])
          [:div.flex.w-100-p
           (when two-columns?
             [:div.w-50-p
              [summary-details num-columns id]])
           [:div
            {:class (if two-columns? "w-50-p" "w-100-p")}
            [:div.flex.p-l-10.m-b-10.m-r-10
             (doall
              (map
               (fn [[title {:keys [view icon]}]]
                 ^{:key title}
                 [:div.flex-grow-1.t-a-c
                  [details-tab
                   title
                   icon
                   device-type
                   (= title tab)
                   (make-event-handler ::char/set-selected-display-tab title)]])
               (if two-columns?
                 (rest details-tabs)
                 details-tabs)))]
            ^{:key tab}
            [error-boundary
             (fn [error stack retry] [feature-render-error (s/capitalize tab) id error stack retry])
             [(-> tab details-tabs :view) num-columns id]]]]
          [:div.p-10
           [:span.orange.underline.pointer
            {:on-click #(swap! show-selections? not)}
            [:span (if @show-selections?
                     "hide selections"
                     "show selections")]
            [:i.fa.m-l-5
             {:class (if @show-selections? "fa-caret-up" "fa-caret-down")}]]
           (when @show-selections?
             [character-selections id])]]]))))

;; TODO: re-enable when email sharing is wired up
#_(defn share-link-email [id]
  [:a.m-r-5.f-s-14
   {:href (str "mailto:?subject=My%20D%26D%20Character%20-%20"
               @(subscribe [::char/character-name id])
               "&body=" js/window.location.protocol "//" js/window.location.hostname "" js/window.location.port
               (str (routes/path-for routes/dnd-e5-char-page-route :id id) "?frame=true"))}
   [:i.fa.fa-envelope.m-r-5]
   "share"])

(def character-display-style
  {:padding "20px 5px"
   :background-color "rgba(0,0,0,0.15)"})

(defn add-to-party-component []
  (let [party-id (r/atom nil)]
    (fn [character-id]
      [:div.m-l-10.f-w-b
       [:span "Add to Party:"]
       [:div.flex
        [:select.builder-option.builder-option-dropdown
         {:on-change (fn [e] (let [value (event-value e)
                                   id (when (not (s/blank? value))
                                        (js/parseInt value))]
                               (when id
                                 (reset! party-id id))))}
         [:option.builder-dropdown-item
          "<new party>"]
         (doall
          (map
           (fn [{:keys [:db/id ::party/name]}]
             ^{:key id}
             [:option.builder-dropdown-item
              {:value id}
              name])
           @(subscribe [::party/parties])))]
        [:button.form-button.m-t-5.m-l-5
         {:on-click #(if @party-id
                       (dispatch [::party/add-character-remote @party-id character-id true])
                       (dispatch [::party/make-party #{character-id}]))}
         "ADD"]]])))

(defn labeled-checkbox [label selected?]
  [:div.flex.align-items-c.pointer.m-b-10
   (comps/checkbox selected? false)
   [:span.m-l-5.f-s-14 label]])

(defn export-pdf-fn [built-char
                     id
                     plugin-data
                     print-character-sheet?
                     print-spell-cards?
                     print-prepared-spells?
                     print-large-abilities?
                     print-character-sheet-style?
                     print-spell-card-dc-mod?
                     print-card-back-logo?
                     card-back-logo-faded?
                     print-bw?
                     bw-faded?]
  #(let [export-fn (export-pdf built-char
                               id
                               plugin-data
                               {:print-character-sheet? print-character-sheet?
                                :print-spell-cards? print-spell-cards?
                                :print-prepared-spells? print-prepared-spells?
                                :print-large-abilities? print-large-abilities?
                                :print-character-sheet-style? print-character-sheet-style?
                                :print-spell-card-dc-mod? print-spell-card-dc-mod?
                                :print-card-back-logo? print-card-back-logo?
                                :card-back-logo-faded? card-back-logo-faded?
                                :print-bw? print-bw?
                                :bw-faded? bw-faded?})]
     (export-fn)
     (dispatch [::char/hide-options])))

(def export-pdf-handler (memoize export-pdf-fn))

(def make-arg-event-handler
  (memoize
   (fn [event-kw & [arg-fn]]
     #(dispatch [event-kw (if arg-fn (arg-fn %) %)]))))

(defn delete-item-handler [item-key]
  (fn []
    (dispatch [::mi/delete-custom-item item-key])
    (dispatch [::mi/hide-delete-confirmation item-key])
    (dispatch [::mi/reset-item])))

(defn print-button-style [print-button-enabled]
  (if print-button-enabled
    {}
    {:opacity 0.5
     :cursor :not-allowed
     :pointer-events "none"}))


(defn print-options [id built-char]
  (let [print-character-sheet? @(subscribe [::char/print-character-sheet?])
        print-spell-cards? @(subscribe [::char/print-spell-cards?])
        print-prepared-spells? @(subscribe [::char/print-prepared-spells?])
        print-large-abilities? @(subscribe [::char/print-large-abilities?])
        print-character-sheet-style? @(subscribe [::char/print-character-sheet-style?])
        print-spell-card-dc-mod? @(subscribe [::char/print-spell-card-dc-mod?])
        print-card-back-logo? @(subscribe [::char/print-card-back-logo?])
        card-back-logo-faded? @(subscribe [::char/card-back-logo-faded?])
        print-bw? @(subscribe [::char/print-bw?])
        bw-faded? @(subscribe [::char/bw-faded?])
        plugin-data {:spells-map @(subscribe [::spells/spells-map])
                     :plugin-spells-map @(subscribe [::spells/plugin-spells-map])
                     :language-map @(subscribe [::langs/language-map])
                     :all-weapons-map @(subscribe [::mi/all-weapons-map])
                     :all-magic-items-map @(subscribe [::mi/all-magic-items-map])
                     :current-armor-class @(subscribe [::char/current-armor-class id])}
        has-spells? (seq (char/spells-known built-char))
        print-button-enabled (if (or (= print-character-sheet-style? nil)
                                     (= (str print-character-sheet-style?) "NaN"))
                               false true)
        ]
    [:div.flex.justify-cont-end
     [:div.p-20
      [:div.f-s-20.f-w-b.m-b-10 "PDF Options"]
      [:div.m-b-2
       [:div.flex.m-b-10
        [:div.m-t-5
         [labeled-dropdown
          "Select Character sheet"
          {:items (into [{:title "Select" :value " "}]
                        (integrations/sheet-styles @(subscribe [:user-tier])))
           :value print-character-sheet-style?
           :on-change (make-arg-event-handler ::char/set-print-character-sheet-style? js/parseInt)}]]]
       [integrations/pdf-options-slot @(subscribe [:user-tier])]
       [:div.flex
        [:div
         {:on-click (make-event-handler ::char/toggle-large-abilities-print)}
         [labeled-checkbox
          "Print Abilities Large (and Bonuses Small)"
          print-large-abilities?]]]]
      (when has-spells?
        [:div.m-b-2
         [:div.flex
          [:div
           {:on-click (make-event-handler ::char/toggle-spell-cards-print)}
           [labeled-checkbox
            "Print Spell Cards"
            print-spell-cards?]]]])
      (when print-spell-cards?
        [:div.m-b-2
         [:div.flex
          [:div
           {:on-click (make-event-handler ::char/toggle-spell-cards-by-dc-mod)}
           [labeled-checkbox
            "Print Spell DC and MOD"
            print-spell-card-dc-mod?]]]])
      (when has-spells?
        [:div.m-b-10
         [:div.m-b-10
          [:span.f-w-b "Spells Printed"]]
         [:div.flex
          [:div
           {:on-click (make-event-handler ::char/toggle-known-spells-print)}
           [labeled-checkbox
            "Known"
            (not print-prepared-spells?)]]
          [:div.m-l-20
           {:on-click (make-event-handler ::char/toggle-known-spells-print)}
           [labeled-checkbox
            "Prepared"
            print-prepared-spells?]]]])
      ;; Appearance group — cosmetic/output options, set apart from the data
      ;; options above (layout B). Only meaningful when spell cards are printed.
      (when print-spell-cards?
        [:div.m-b-10
         [:div.m-b-5 [:span.f-w-b "Appearance"]]
         [:div.flex
          [:div
           {:on-click (make-event-handler ::char/toggle-print-bw)}
           [labeled-checkbox
            "Printer-friendly (black & white)"
            print-bw?]]]
         ;; Under B&W: default is solid-black icons with white-halo labels;
         ;; opt into faded grayscale icons for a softer look.
         (when print-bw?
           [:div.m-l-20
            [:div.flex
             [:div
              {:on-click (make-event-handler ::char/toggle-bw-faded)}
              [labeled-checkbox
               "Faded grayscale icons (else solid black)"
               bw-faded?]]]])
         [:div.flex
          [:div
           {:on-click (make-event-handler ::char/toggle-print-card-back-logo)}
           [labeled-checkbox
            "Print logo on card backs"
            print-card-back-logo?]]]
         ;; Treatment only matters when the logo is on AND B&W isn't forcing black.
         (when (and print-card-back-logo? (not print-bw?))
           [:div.m-l-20
            [:div.flex
             [:div
              {:on-click (make-event-handler ::char/toggle-card-back-logo-faded)}
              [labeled-checkbox
               "Faded color (else solid black)"
               card-back-logo-faded?]]]])])
      [:button.form-button.p-10.m-l-5
       {:style (print-button-style print-button-enabled)
        :on-click (export-pdf-handler built-char
                                      id
                                      plugin-data
                                      print-character-sheet?
                                      print-spell-cards?
                                      print-prepared-spells?
                                      print-large-abilities?
                                      print-character-sheet-style?
                                      print-spell-card-dc-mod?
                                      print-card-back-logo?
                                      card-back-logo-faded?
                                      print-bw?
                                      bw-faded?)}
       "Create PDF"]
      [:div.f-s-20.f-w-b.m-b-10.m-t-10 "Other PDFs"]
      [:a.orange {:href "/dnld/5eActionsReferencePage.pdf" :target "_blank"} "5e Actions Reference"]]
     [:span.orange.underline.pointer.uppercase.m-l-10.f-s-12
      {:on-click (make-event-handler ::char/hide-options)}
      "Cancel"]]))

(defn make-print-handler [id built-char]
  #(dispatch
    [::char/show-options
     [print-options id built-char]]))


(defn abilities-spec [vals suffix bonus?]
  (reduce-kv
   (fn [m k v]
     (let [new-k (if suffix
                   (keyword (str (name k) "-mod"))
                   k)
           new-v (if bonus? (common/bonus-str v) v)]
       (assoc m new-k new-v)))
   {}
   vals))

(defn character-load-recovery
  "Shown in place of the character sheet when the character's server data could
   not be read even after self-heal. Never a blank sheet, never a dead-end: the
   user's data is safe, and they can delete this character (no build required —
   :delete-character works from the id alone) or return to their list. Also
   offers a copyable diagnostic report the user can send to support themselves,
   which works even when no support address / auto-send is configured."
  [_id]
  (let [copied? (r/atom false)]
    (fn [id]
      (let [{:keys [error raw]} @(subscribe [::char/character-load-error-info id])
            report (str "OrcPub — character could not be loaded\n"
                        "Character ID: " id "\n"
                        (when error (str "Error: " error "\n"))
                        "\n--- raw character data (for support) ---\n"
                        raw)]
        [content-page
         "Character"
         []
         [:div.p-20.main-text-color
          [:div.f-w-b.f-s-24.m-b-10 "This character couldn’t be loaded"]
          [:div.m-b-15.l-h-19
           "This character’s data appears to be corrupted and couldn’t be read. "
           "Your saved data is safe — you can delete this character, or head back to your characters."]
          [:div.flex.flex-wrap.m-b-15
           [:button.form-button.m-r-10.m-b-5
            {:on-click #(do (dispatch [:delete-character id])
                            (dispatch [:route routes/dnd-e5-char-list-page-route]))}
            "Delete this character"]
           [:button.form-button.m-b-5
            {:on-click #(dispatch [:route routes/dnd-e5-char-list-page-route])}
            "My characters"]]
          ;; Auto-send: only when a support address is configured AND the user is
          ;; signed in (so we have an account email to cc). Otherwise the copyable
          ;; report below is the fallback.
          (when (and (seq branding/support-email) @(subscribe [:username]))
            (let [status @(subscribe [::char/character-report-status id])]
              [:div.m-b-15
               [:button.form-button.m-b-5
                {:disabled (= status :sending)
                 :on-click #(dispatch [:report-character-problem id error raw])}
                (case status
                  :sending "Sending…"
                  :sent    "✓ Report sent — thank you"
                  :failed  "Couldn’t send automatically — copy the report below"
                  "Email this report to support")]]))
          [:div.m-t-10
           [:div.m-b-5.f-s-14 "Or copy this report and send it to support yourself:"]
           [:button.form-button.f-s-12.m-b-5
            {:on-click (fn []
                         (some-> js/navigator .-clipboard (.writeText report))
                         (reset! copied? true))}
            (if @copied? "Copied!" "Copy report")]
           [:pre.f-s-12.m-t-5.wsp-prw
            {:style {:user-select "text" :max-height "260px" :overflow "auto"
                     :background "rgba(0,0,0,0.25)" :padding "8px" :border-radius "3px"}}
            report]]]]))))

(defn shared-content-banner
  "Shown when viewing a character whose homebrew arrived embedded in the share
   link. The content is loaded view-only (:shared-plugins, never persisted); this
   offers to Keep it in the library, and flags any entries that collide by name
   with the viewer's own content (the shared version wins on this sheet, their
   copy is untouched)."
  [id]
  (when-let [{:keys [count item-count collisions]} @(subscribe [::e5/shared-content-info])]
    (let [char-name @(subscribe [::char/character-name id])
          n-coll (clojure.core/count collisions)
          item-count (or item-count 0)
          parts (cond-> []
                  (pos? count)      (conj (str count " homebrew piece" (when (not= 1 count) "s")))
                  (pos? item-count) (conj (str item-count " custom item" (when (not= 1 item-count) "s"))))]
      [:div.m-b-10.flex.align-items-c.justify-cont-s-b.flex-wrap
       {:style {:padding "12px 16px"
                :background "rgba(217,165,32,0.09)"
                :border-left "4px solid #d9a520"
                :border-radius "6px"
                :gap "12px"}}
       [:div {:style {:min-width "260px" :flex "1 1 300px"}}
        [:div.f-w-b.f-s-16
         [:i.fa.fa-info-circle.m-r-5.orange]
         (str "Shared with " (s/join " and " parts) ".")]
        [:div.f-s-12.m-t-5 {:style {:opacity 0.8}}
         "Loaded for viewing only — not saved to your library."]
        (when (pos? n-coll)
          [:div.f-s-12.m-t-5 {:style {:color "#d9a520"}}
           (str n-coll " differ from same-named content you own — the shared version shows "
                "here, yours is untouched"
                (when-let [names (seq (map :name (take 4 collisions)))]
                  (str " (" (s/join ", " names) (when (> n-coll 4) ", …") ")"))
                ".")])
        (when (pos? item-count)
          [:div.f-s-12.m-t-5 {:style {:opacity 0.8}}
           "Custom magic items are shown for this view only (keeping items isn't supported yet)."])]
       [:div.flex.align-items-c {:style {:flex "0 0 auto"}}
        (when (pos? count)
          [:button.form-button.m-r-5
           {:on-click #(dispatch [::e5/keep-shared-content char-name])}
           "Keep homebrew in my library"])
        [:button.form-button
         {:on-click #(dispatch [::e5/dismiss-shared-content])}
         "Dismiss"]]])))

(defn character-page []
  (let [expanded? (r/atom false)]
    (fn [{:keys [id] :as arg}]
      (if @(subscribe [::char/character-load-error? (js/parseInt id)])
        [character-load-recovery (js/parseInt id)]
        (let [id (js/parseInt id)
            frame? (= "true" (get-in arg [:query "frame"]))
            {:keys [::entity/owner] :as character} @(subscribe [::char/character id])
            built-template (subs/built-template
                            @(subscribe [::char/template])
                            (subs/selected-plugin-options
                             character))
            built-character (subs/built-character character built-template)
            device-type @(subscribe [:device-type])
            username @(subscribe [:username])]
        (prn "FRAME?" frame?)
        [content-page
         (when (not frame?)
           "Character Page")
         (into
          (vec (integrations/share-links id @(subscribe [::char/character-name id])))
          (remove nil?
           [#_[:div.m-l-5.hover-shadow.pointer
               {:on-click #(swap! expanded? not)}
               [:img.h-32 {:src "/image/world-anvil.jpeg"}]]
            (when (and username
                     owner
                     (= owner username))
              {:title "Edit"
               :icon "pencil"
               :on-click (make-event-handler :edit-character character)})
            {:title "Export"
             :icon "download"
             :on-click (make-print-handler id built-character)}
            (when (and username owner (not= owner username))
              [add-to-party-component id])]))
         [:div.p-10.main-text-color
          (when @expanded?
            (let [url js/window.location.href]
              [:div.p-10.flex.justify-cont-end
               [:input.input.w-500.bg-white.black
                {:value (str url
                             (when (not (s/ends-with? url "?frame=true"))
                               "?frame=true"))}]]))
          [shared-content-banner id]
          [character-display id true (if (= :mobile device-type) 1 2)]]
         :frame? frame?])))))

(defn monster-page [{:keys [key] :as arg}]
  (let [monster @(subscribe [::monsters/monster (keyword key)])]
    [content-page
     "Monster Page"
     []
     [:div.p-10.main-text-color
      [monster-component monster]]]))

(defn spell-page [{:keys [key] :as arg}]
  (let [spell-map @(subscribe [::spells/spells-map])
        spell (spell-map (common/name-to-kw key))]
    [content-page
     "Spell Page"
     (remove
      nil?
      [])
     [:div.p-10.main-text-color
      [spell-component spell true]]]))

(defn item-page [{:keys [key] :as arg}]
  (let [item-key (if (re-matches #"\d+" key)
                   (js/parseInt key)
                   (keyword key))
        item @(subscribe [::mi/custom-item item-key])
        username @(subscribe [:username])
        owner? (= username (::mi/owner item))]
    [content-page
     "Item Page"
     (remove
      nil?
      [(when owner?
         {:title "Delete"
          :icon "trash"
          :on-click (delete-item-handler item-key)})
       (when owner?
         {:title "Edit"
          :icon "pencil"
          :on-click (make-event-handler ::mi/edit-custom-item item)})])
     [:div.p-10.main-text-color
      [item-component item]
      ]]))

(defn base-builder-field [name comp]
  [:div.field.main-text-color.m-t-0
   [:div.personality-label.f-s-16 name]
   comp])

(defn builder-field [el-type name value on-change attrs & [children]]
  (base-builder-field
   name
   [comps/input-field
    el-type
    value
    on-change
    attrs]))

;; dead — zero callers
#_(defn select-builder-field [name value on-change children]
  (base-builder-field
   name
   (cond-> [:select.builder-option.builder-option-dropdown
            [:option {:value :none}
             "None"]]
     children (concat children))))

(defn input-builder-field [name value on-change attrs]
  [builder-field :input name value on-change attrs])

(defn textarea-field [{:keys [value on-change]}]
  [comps/input-field
   :textarea
   value
   on-change
   {:class-name "input"
    :maxLength (:notes branding/field-limits)}])

(defn number-field [{:keys [value on-change]}]
  [comps/input-field
   :input
   value
   (fn [v]
     (on-change
      (when (re-matches #"\d+" v) (js/parseInt v))))
   {:class "input"
    :type :number
    :maxLength (:number branding/field-limits)}])

(defn attunement-value [attunement key name]
  [:div
   {:on-click (make-event-handler ::mi/toggle-attunement-value key)}
   [labeled-checkbox name ((set attunement) key)]])

(defn attunement-selector [attunement]
  (base-builder-field
   "Attunement"
   [:div
    [:div.flex.align-items-c.m-b-10
     {:on-click (make-event-handler ::mi/toggle-attunement)}
     (comps/checkbox attunement false)
     [:span.f-s-24.f-w-b.m-l-5 "Attunement"]]
    (when attunement
      [:div
       [labeled-checkbox "Any" (= #{:any} (set attunement))]
       [:div.flex.flex-wrap
        [:div.flex-grow-1
         (base-builder-field
          [:div.f-w-b.m-b-5 "Class"]
          [:div
           (doall
            (map
             (fn [{:keys [::template/key ::template/name]}]
               ^{:key key}
               [attunement-value attunement key name])
             (cons
              {::template/key :spellcaster
               ::template/name "Spellcaster"}
              @(subscribe [::classes/classes]))))])]
        [:div.flex-grow-1
         (base-builder-field
          [:div.f-w-b.m-b-5 "Alignment"]
          [:div
           [:div.m-b-5]
           (doall
            (map
             (fn [{:keys [key name]}]
               ^{:key key}
               [attunement-value attunement key name])
             (concat
              [{:name "Good"
                :key :good}
               {:name "Evil"
                :key :evil}]
              opt/alignments)))])]]])]))

(defn base-armor-selector []
  (let [mobile? @(subscribe [:mobile?])]
    [:div.m-b-20
     [:div.main-text-color.m-b-10
      [:span.f-s-24.f-w-b "Base Armor"]]
     [:div.flex.flex-wrap
      [:div.flex-grow-1
       (base-builder-field
        [:div.f-w-b.m-b-5 "Armor Type"]
        [:div
         {:style (if mobile?
                   two-columns-style
                   three-columns-style)}
         (doall
          (map
           (fn [{:keys [:key :name]}]
             ^{:key key}
             [:div
              {:on-click (make-event-handler ::mi/toggle-subtype key)}
              [labeled-checkbox name @(subscribe [::mi/has-subtype? key])]])
           (concat
            [{:name "All"
              :key :all}]
            (map
             (fn [type]
               {:name (str "All " (clojure.core/name type))
                :key type})
             armor/armor-types)
            armor/armor)))])]]]))

(defn value-to-item [v]
  {:title v
   :value v})

(defn base-weapon-selector []
  (let [mobile? @(subscribe [:mobile?])
        other? @(subscribe [::mi/has-subtype? :other])
        versatile? @(subscribe [::mi/item-versatile?])
        melee-ranged @(subscribe [::mi/item-melee-ranged])]
    [:div.m-b-20
     [:div.main-text-color.m-b-10
      [:span.f-s-24.f-w-b "Base Weapon"]]
     [:div.flex.flex-wrap
      [:div.flex-grow-1
       (base-builder-field
        [:div.f-w-b.m-b-5 "Weapon Type"]
        [:div
         {:style (if mobile?
                   two-columns-style
                   three-columns-style)}
         (doall
          (map
           (fn [{:keys [key name]}]
             ^{:key key}
             [:div
              {:on-click (make-event-handler ::mi/toggle-subtype key)}
              [labeled-checkbox name @(subscribe [::mi/has-subtype? key])]])
           (concat
            [{:name "Custom" :key :other}
             {:name "All" :key :all}
             {:name "All Swords" :key :sword}
             {:name "All Axes" :key :axe}]
            (common/aloof-sort-by :name 
              @(subscribe [::mi/custom-and-standard-weapons])
            )
            )))])]]
     (when other?
       [:div.main-text-color.m-b-10.m-t-10
        [:span.f-s-18.f-w-b "Base Weapon Details"]
        [:div.flex.flex-wrap.m-t-10
         [:div
          {:on-click (make-event-handler ::mi/toggle-item-finesse?)}
          [labeled-checkbox "Finesse?" @(subscribe [::mi/item-finesse?])]]
         [:div.m-l-10
          {:on-click (make-event-handler ::mi/toggle-item-versatile?)}
          [labeled-checkbox "Versatile?" versatile?]]
         [:div.m-l-10
          {:on-click (make-event-handler ::mi/toggle-item-reach?)}
          [labeled-checkbox "Reach?" @(subscribe [::mi/item-reach?])]]
         [:div.m-l-10
          {:on-click (make-event-handler ::mi/toggle-item-two-handed?)}
          [labeled-checkbox "Two-Handed?" @(subscribe [::mi/item-two-handed?])]]
         [:div.m-l-10
          {:on-click (make-event-handler ::mi/toggle-item-thrown?)}
          [labeled-checkbox "Thrown?" @(subscribe [::mi/item-thrown?])]]
         [:div.m-l-10
          {:on-click (make-event-handler ::mi/toggle-item-heavy?)}
          [labeled-checkbox "Heavy?" @(subscribe [::mi/item-heavy?])]]
         [:div.m-l-10
          {:on-click (make-event-handler ::mi/toggle-item-light?)}
          [labeled-checkbox "Light?" @(subscribe [::mi/item-light?])]]
         [:div.m-l-10
          {:on-click (make-event-handler ::mi/toggle-item-ammunition?)}
          [labeled-checkbox "Ammunition?" @(subscribe [::mi/item-ammunition?])]]
         [:div.m-l-10
          {:on-click (make-event-handler ::mi/toggle-item-special?)}
          [labeled-checkbox "Special?" @(subscribe [::mi/item-special?])]]
         [:div.m-l-10
          {:on-click (make-event-handler ::mi/toggle-item-loading?)}
          [labeled-checkbox "Loading?" @(subscribe [::mi/item-loading?])]]]
        [:div.flex.flex-wrap
         [:div.m-t-10
          [labeled-dropdown
           "Damage Die Number"
           {:items (map
                    value-to-item
                    (range 1 10))
            :value @(subscribe [::mi/item-damage-die-count])
            :on-change (make-arg-event-handler ::mi/set-item-damage-die-count js/parseInt)}]]
         [:div.m-l-10.m-t-10
          [labeled-dropdown
           "Damage Die"
           {:items (map
                    (fn [v]
                      {:value v
                       :title (str "d" v)})
                    [4 6 8 10 12 20 100])
            :value @(subscribe [::mi/item-damage-die])
            :on-change (make-arg-event-handler ::mi/set-item-damage-die js/parseInt)}]]
         (when versatile?
           [:div.m-l-10.m-t-10
            [labeled-dropdown
             "Versatile Damage Die Number"
             {:items (map
                      value-to-item
                      (range 1 10))
              :value @(subscribe [::mi/item-versatile-damage-die-count])
              :on-change (make-arg-event-handler ::mi/set-item-versatile-damage-die-count js/parseInt)}]])
         (when versatile?
           [:div.m-l-10.m-t-10
            [labeled-dropdown
             "Versatile Damage Die"
             {:items (map
                      (fn [v]
                        {:value v
                         :title (str "d" v)})
                      [4 6 8 10 12 20 100])
              :value @(subscribe [::mi/item-versatile-damage-die])
              :on-change (make-arg-event-handler ::mi/set-item-versatile-damage-die js/parseInt)}]])
         [:div.m-l-10.m-t-10
          [labeled-dropdown
           "Simple / Martial?"
           {:items [{:value :simple
                     :title "Simple"}
                    {:value :martial
                     :title "Martial"}]
            :value @(subscribe [::mi/item-weapon-type])
            :on-change (make-arg-event-handler ::mi/set-item-weapon-type)}]]
         [:div.m-l-10.m-t-10
          [labeled-dropdown
           "Melee / Ranged?"
           {:items [{:value :melee
                     :title "Melee"}
                    {:value :ranged
                     :title "Ranged"}]
            :value melee-ranged
            :on-change (make-arg-event-handler ::mi/set-item-melee-ranged)}]]
         (when (= :ranged melee-ranged)
           [:div.m-l-10.m-t-10
            [:div.f-w-b.m-b-5 "Range Min"]
            [number-field
             {:value @(subscribe [::mi/item-range-min])
              :on-change (make-arg-event-handler ::mi/set-item-range-min)}]])
         (when (= :ranged melee-ranged)
           [:div.m-l-10.m-t-10
            [:div.f-w-b.m-b-5 "Range Max"]
            [number-field
             {:value @(subscribe [::mi/item-range-max])
              :on-change (make-arg-event-handler ::mi/set-item-range-max)}]])
         [:div.m-l-10.m-t-10
          [labeled-dropdown
           "Damage Type"
           {:items (map
                    (fn [type]
                      {:value type
                       :title (name type)})
                    damage-types/damage-types)
            :value @(subscribe [::mi/item-damage-type])
            :on-change (make-arg-event-handler ::mi/set-item-damage-type)
            }]]]]
            )]))


(defn item-ability-bonuses []
  (base-builder-field
   [:div.f-w-b.m-b-5 "Ability Bonus"]
   [:div
    (doall
     (map
      (fn [ability-kw]
        ^{:key ability-kw}
        [:div.flex.align-items-c
         [:div.w-40 (s/upper-case (name ability-kw))]
         [:div
          [dropdown
           {:value @(subscribe [::mi/ability-mod-type ability-kw])
            :on-change #(dispatch [::mi/set-ability-mod-type ability-kw %])
            :items [{:value :becomes-at-least
                     :title "Becomes At Least"}
                    {:value :increases-by
                     :title "Increases By"}]}]]
         [:div.w-60.m-l-5
          [number-field
           {:value @(subscribe [::mi/ability-mod-value ability-kw])
            :on-change #(dispatch [::mi/set-ability-mod-value ability-kw %])}]]])
      char/ability-keys))]))

(defn item-saving-throw-bonuses []
  (base-builder-field
   [:div.f-w-b.m-b-5 "Saving Throw Bonus"]
   [:div
    (doall
     (map
      (fn [ability-kw]
        ^{:key ability-kw}
        [:div.flex.align-items-c
         [:div.w-40 (str (s/upper-case (name ability-kw)) " Save")]
         [:div
          [dropdown
           {:value :increases-by
            :items [{:value :increases-by
                     :title "Increases By"}]}]]
         [:div.w-60.m-l-5
          [number-field
           {:value @(subscribe [::mi/save-mod-value ability-kw])
            :on-change #(dispatch [::mi/set-save-mod-value ability-kw %])}]]])
      char/ability-keys))]))

(defn item-speed-bonuses []
  (base-builder-field
   [:div.f-w-b.m-b-5 "Speed Bonus"]
   [:div
    (doall
     (map
      (fn [type-kw]
        (let [speed-mod-type @(subscribe [::mi/speed-mod-type type-kw])]
          ^{:key type-kw}
          [:div.flex.align-items-c
           [:div.w-100 (common/safe-capitalize-kw type-kw)]
           [:div
            [dropdown
             {:value speed-mod-type
              :on-change #(dispatch [::mi/set-speed-mod-type type-kw %])
              :items (let [items [{:value :becomes-at-least
                                   :title "Becomes At Least"}
                                  {:value :increases-by
                                   :title "Increases By"}]]
                       (if (= :speed type-kw)
                         items
                         (conj items
                               {:value :equals-walking-speed
                                :title "Equals Walking Speed"})))}]]
           (when (not= :equals-walking-speed speed-mod-type)
             [:div.w-60.m-l-5
              [number-field
               {:value @(subscribe [::mi/speed-mod-value type-kw])
                :on-change #(dispatch [::mi/set-speed-mod-value type-kw %])}]])]))
      [:speed :flying-speed :swimming-speed :climbing-speed]))]))

(defn item-modifier-toggles [title item-kws toggle-event has-sub]
  (base-builder-field
   [:div.f-w-b.m-b-5 title]
   [:div
    (doall
     (map
      (fn [type-kw]
        ^{:key type-kw}
        [:div
         {:on-click #(dispatch [toggle-event type-kw])}
         [labeled-checkbox (common/safe-capitalize-kw type-kw) @(subscribe [has-sub type-kw])]])
      item-kws))]))

(defn item-damage-resistances []
  [item-modifier-toggles
   "Damage Resistances"
   opt/damage-types
   ::mi/toggle-damage-resistance
   ::mi/has-damage-resistance?])

(defn item-damage-vulnerabilities []
  [item-modifier-toggles
   "Damage Vulnerabilities"
   opt/damage-types
   ::mi/toggle-damage-vulnerability
   ::mi/has-damage-vulnerability?])

(defn item-damage-immunities []
  [item-modifier-toggles
   "Damage Immunities"
   opt/damage-types
   ::mi/toggle-damage-immunity
   ::mi/has-damage-immunity?])

(defn item-condition-immunities []
  [item-modifier-toggles
   "Condition Immunities"
   (keys opt/conditions-map)
   ::mi/toggle-condition-immunity
   ::mi/has-condition-immunity?])

(defn item-bonuses [{:keys [::mi/magical-damage-bonus
                            ::mi/magical-attack-bonus
                            ::mi/magical-ac-bonus
                            ::mi/type] :as item}]
  [:div.m-b-20
   [:div.m-b-10
    [:span.f-s-24.f-w-b "Item Properties"]]
   [:div.flex.m-b-20
    (when (= type :weapon)
      [:div
       [:div.f-w-b.m-b-5 "Magical Damage Bonus"]
       [number-field
        {:value magical-damage-bonus
         :on-change #(dispatch [::mi/set-item-damage-bonus %])}]])
    (when (= type :weapon)
      [:div.m-l-20.m-r-20
       [:div.f-w-b.m-b-5 "Magical Attack Bonus"]
       [number-field
        {:value magical-attack-bonus
         :on-change #(dispatch [::mi/set-item-attack-bonus %])}]])
    [:div
     [:div.f-w-b.m-b-5 "Magical AC Bonus"]
     [number-field
      {:value magical-ac-bonus
       :on-change #(dispatch [::mi/set-item-ac-bonus %])}]]]
   [:div.flex.flex-wrap.m-b-20
    [:div.flex-grow-1
     [item-ability-bonuses]]
    [:div.flex-grow-1
     [item-saving-throw-bonuses]]
    [:div.flex-grow-1
     [item-speed-bonuses]]]
   [:div.flex.flex-wrap
    [:div.flex-grow-1
     [item-damage-resistances]]
    [:div.flex-grow-1
     [item-damage-vulnerabilities]]
    [:div.flex-grow-1
     [item-damage-immunities]]
    [:div.flex-grow-1
     [item-condition-immunities]]]])

(defn builder-field-cue
  "Cue class for a required field (or a set of related keys) rendered by a
   component other than the text inputs — reads the same :builder-field-errors
   map. Red if any related key is invalid, amber if any is missing."
  [field-or-keys]
  (let [errors @(subscribe [:builder-field-errors])
        ks (if (coll? field-or-keys) field-or-keys [field-or-keys])
        statuses (keep #(get errors %) ks)]
    (cond
      (some #{:invalid} statuses) " builder-field-invalid"
      (some #{:missing} statuses) " builder-field-unfilled"
      :else "")))

(defn builder-input-field [title prop item prop-event & [class-names type]]
  ;; Flag a required field that failed the last save: amber if it was empty,
  ;; red if it was filled but rejected (e.g. a name not starting with a letter).
  ;; Editing the field clears its own save-cue.
  (let [v (prop item)
        ;; Live: a name that's present but doesn't start with a letter is wrong
        ;; the instant it's typed — flag it red immediately, before any save.
        live-invalid? (and (= prop :name)
                           (string? v) (not (s/blank? v))
                           (not (common/starts-with-letter? v)))
        cue (cond
              live-invalid? " builder-field-invalid"
              :else (case (get @(subscribe [:builder-field-errors]) prop)
                      :missing " builder-field-unfilled"
                      :invalid " builder-field-invalid"
                      ""))]
    [:div.flex-grow-1
     {:class class-names
      :name prop}
     [input-builder-field
      [:span.f-w-b title]
      v
      #(do (dispatch [prop-event prop %])
           (dispatch [:clear-builder-field-error prop]))
      {:class (str "input h-40" cue)
       :type type
       :maxLength (:text branding/field-limits)}]
     (when live-invalid?
       [:div.f-s-12.m-t-2 {:style {:color "#d9534f"}}
        "Name must start with a letter."])]))

#_(defn item-input-field [title prop item & [class-names]]
  (builder-input-field title prop item ::mi/set-item-name class-names))

(defn spell-input-field [title prop spell & [class-names]]
  (builder-input-field title prop spell ::spells/set-spell-prop class-names))

(defn monster-input-field [title prop monster & [class-names type]]
  (builder-input-field title prop monster ::monsters/set-monster-prop class-names type))

(defn encounter-input-field [title prop encounter & [class-names type]]
  (builder-input-field title prop encounter ::encounters/set-encounter-prop class-names type))

(defn language-input-field [title prop language & [class-names]]
  (builder-input-field title prop language ::langs/set-language-prop class-names))

(defn invocation-input-field [title prop invocation & [class-names]]
  (builder-input-field title prop invocation ::classes/set-invocation-prop class-names))

(defn boon-input-field [title prop boon & [class-names]]
  (builder-input-field title prop boon ::classes/set-boon-prop class-names))

(defn selection-input-field [title prop selection & [class-names]]
  (builder-input-field title prop selection ::selections/set-selection-prop class-names))

(defn background-input-field [title prop bg & [class-names]]
  (builder-input-field title prop bg ::bg/set-background-prop class-names))

(defn race-input-field [title prop race & [class-names]]
  (builder-input-field title prop race ::races/set-race-prop class-names))

(defn subrace-input-field [title prop subrace & [class-names]]
  (builder-input-field title prop subrace ::races/set-subrace-prop class-names))

(defn subclass-input-field [title prop subclass & [class-names]]
  (builder-input-field title prop subclass ::classes/set-subclass-prop class-names))

(defn class-input-field [title prop class & [class-names]]
  (builder-input-field title prop class ::classes/set-class-prop class-names))

(defn feat-input-field [title prop feat & [class-names]]
  (builder-input-field title prop feat ::feats/set-feat-prop class-names))

(defn component-checkbox [component spell]
  [:span.m-r-20.m-b-10
   [comps/labeled-checkbox
    (common/kw-to-name component)
    (get-in spell [:components component])
    false
    #(dispatch [::spells/toggle-component component])]])

(defn tool-prof-checkboxes [background tools]
  [:div.flex.flex-wrap
   (doall
    (map
     (fn [{:keys [name key]}]
       ^{:key key}
       [:span.m-r-20.m-b-10
        [comps/labeled-checkbox
         name
         (get-in background [:profs :tool key])
         false
         #(dispatch [::bg/toggle-tool-prof key])]])
     tools))])

(defn language-checkboxes [race languages]
  [:div
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [{:keys [name key]}]
        ^{:key key}
        [:span.m-r-20.m-b-10
         [comps/labeled-checkbox
          name
          (get-in race [:languages name])
          false
          #(dispatch [::races/toggle-language name])]])
      (sort-by
       :name
       languages)))]
   [:div.pointer.m-t-10
    [:span.bg-lighter.p-5
     {:on-click #(dispatch [:route routes/dnd-e5-language-builder-page-route])}
     [:i.fa.fa-plus]
     [:span.orange.underline.m-l-5 "Add Language"]]]])

(defn tool-choice-checkboxes [background key]
  [:div.flex.flex-wrap
   (doall
    (map
     (fn [num]
       ^{:key num}
       [:span.m-r-20.m-b-10
        [comps/labeled-checkbox
         (str "Any " num)
         (= num (get-in background [:profs :tool-options key]))
         false
         #(dispatch [::bg/toggle-choice-tool-prof key num])]])
     (range 1 4)))])

(defn language-choice-checkboxes [background]
  [:div.flex.flex-wrap
   (doall
    (map
     (fn [num]
       ^{:key num}
       [:span.m-r-20.m-b-10
        [comps/labeled-checkbox
         (str "Any " num)
         (= num (get-in background [:profs :language-options :choose]))
         false
         #(dispatch [::bg/toggle-choice-language-prof num])]])
     (range 1 4)))])

(defn starting-equipment-choice-checkboxes [background equipment equipment-name]
  [:div.m-r-20.m-b-10
   [comps/labeled-checkbox
    "Any 1"
    (some
     (fn [{:keys [name]}]
       (= name equipment-name))
     (:equipment-choices background))
    false
    #(dispatch [::bg/toggle-starting-equipment-choice equipment equipment-name])]])

(defn starting-equipment-checkboxes [background equipment]
  [:div.flex.flex-wrap
   (doall
    (map
     (fn [{:keys [name key]}]
       ^{:key key}
       [:span.m-r-20.m-b-10
        [comps/labeled-checkbox
         name
         (get-in background [:equipment key])
         false
         #(dispatch [::bg/toggle-starting-equipment key])]])
     equipment))])

(def option-source-name-label
  [:span
   [:span "Option Source Name"]
   [:span.f-w-n.f-s-12.m-l-5 "(e.g. "
    [:span.i "Player's Manual"]
    [:span ", "]
    [:span.i "Hodor's Guide to Hodors"]
    [:span ")"]]])

(defn option-proficiency-choice [title
                                 proficiency-choice-key
                                 proficiency-options
                                 option
                                 set-path-prop-event
                                 toggle-path-prop-event]
  [:div.m-b-20
   [:div.f-s-24.f-w-b.m-b-20 title]
   [:div.m-b-10
    [labeled-dropdown
     "Choose"
     {:items (map
              value-to-item
              (range 1 6))
      :value (get-in option [:profs proficiency-choice-key :choose] 1)
      :on-change #(dispatch [set-path-prop-event [:profs proficiency-choice-key :choose] (js/parseInt %)])}]]
   [:div.f-s-18.f-w-b.m-b-20 "Options"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [{:keys [name key]}]
        ^{:key key}
        [:span.m-r-20.m-b-10
         [comps/labeled-checkbox
          name
          (get-in option [:profs proficiency-choice-key :options key])
          false
          #(dispatch [toggle-path-prop-event [:profs proficiency-choice-key :options key]])]])
      proficiency-options))]])

(defn option-weapon-proficiency-choice [option
                                        set-path-prop-event
                                        toggle-path-prop-event]
  (option-proficiency-choice
   "Weapon Proficiency Choice"
   :weapon-proficiency-options
   @(subscribe [::mi/custom-and-standard-weapons])
   option
   set-path-prop-event
   toggle-path-prop-event))

(def option-skill-expertise-choice
  (partial option-proficiency-choice
           "Skill Expertise (Double Proficiency) Choice"
           :skill-expertise-options
           skills/skills))

(defn option-language-proficiency-choice
  "Wraps option-proficiency-choice; subscribes inside render context."
  [option set-path-prop-event toggle-path-prop-event]
  (option-proficiency-choice
   "Language Proficiency Choice"
   :language-options
   @(subscribe [::langs/languages])
   option
   set-path-prop-event
   toggle-path-prop-event))

(def option-skill-proficiency-choice
  (partial option-proficiency-choice
           "Skill Proficiency Choice"
           :skill-options
           skills/skills))

(defn option-skill-proficiency [option toggle-event]
  [:div.m-b-20
   [:div.f-s-24.f-w-b.m-b-20 "Skill Proficiencies"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [{:keys [name key]}]
        ^{:key key}
        [:span.m-r-20.m-b-10
         [comps/labeled-checkbox
          name
          (get-in option [:props :skill-prof key])
          false
          #(dispatch [toggle-event :skill-prof key])]])
      skills/skills))]])

(defn option-languages [option toggle-map-prop-event]
  (let [languages @(subscribe [::langs/languages])]
    [:div.m-b-20
     [:div.f-s-24.f-w-b.m-b-20 "Languages"]
     [:div.flex.flex-wrap
      (doall
       (map
        (fn [{:keys [name key]}]
          ^{:key key}
          [:span.m-r-20.m-b-10
           [comps/labeled-checkbox
            name
            (get-in option [:props :language key])
            false
            #(dispatch [toggle-map-prop-event :language key])]])
        (sort-by
         :name
         languages)))]
     [:div.pointer.m-t-10
      [:span.bg-lighter.p-5
       {:on-click #(dispatch [:route routes/dnd-e5-language-builder-page-route])}
       [:i.fa.fa-plus]
       [:span.orange.underline.m-l-5 "Add Language"]]]]))

(defn option-skill-proficiency-or-expertise [option toggle-event]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-20 "Skill Proficiency or Expertise"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [{:keys [name key]}]
        ^{:key key}
        [:span.m-r-20.m-b-10
         [comps/labeled-checkbox
          name
          (get-in option [:props :skill-prof-or-expertise key])
          false
          #(dispatch [toggle-event :skill-prof-or-expertise key])]])
      skills/skills))]])

(defn option-tool-proficiency [option toggle-path-prop-event]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-20 "Tool Proficiency"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [{:keys [name key]}]
        ^{:key key}
        [:span.m-r-20.m-b-10
         [comps/labeled-checkbox
          name
          (get-in option [:profs :tool key])
          false
          #(dispatch [toggle-path-prop-event [:profs :tool key]])]])
      equip/tools))]])

(defn option-tool-proficiency-or-expertise [option toggle-event]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-20 "Tool Proficiency or Expertise"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [{:keys [name key]}]
        ^{:key key}
        [:span.m-r-20.m-b-10
         [comps/labeled-checkbox
          name
          (get-in option [:props :tool-prof-or-expertise key])
          false
          #(dispatch [toggle-event :tool-prof-or-expertise key])]])
      equip/tools))]])

(defn background-skill-proficiencies [background]
  [:div.m-b-20
   [:div.f-s-24.f-w-b.m-b-20 "Skill Proficiencies"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [{:keys [name key]}]
        ^{:key key}
        [:span.m-r-20.m-b-10
         [comps/labeled-checkbox
          name
          (get-in background [:profs :skill key])
          false
          #(dispatch [::bg/toggle-skill-prof key])]])
      skills/skills))]])

(defn background-languages [background]
  [:div.m-t-20.m-b-20
   [:div.f-s-24.f-w-b.m-b-20 "Languages"]
   [:div
    [language-choice-checkboxes background]]])

(defn background-tool-proficiencies [background]
  [:div.m-t-20.m-b-20
   [:div.f-s-24.f-w-b.m-b-10 "Tool Proficiencies"]
   [:div.m-b-10
    [:div.f-s-18.f-w-b.m-b-10 "Artisans Tools"]
    [:div
     [tool-choice-checkboxes background :artisans-tool]]
    [:div
     [tool-prof-checkboxes background equip/artisans-tools]]]
   [:div.m-b-10
    [:div.f-s-18.f-w-b.m-b-10 "Musical Instruments"]
    [tool-choice-checkboxes background :musical-instrument]]
   [:div.m-b-10
    [:div.f-s-18.f-w-b.m-b-10 "Gaming Set"]
    [tool-choice-checkboxes background :gaming-set]]
   [:div.m-b-10
    [:div.f-s-18.f-w-b.m-b-10 "Vehicles"]
    [tool-prof-checkboxes background equip/vehicle-types]]
   [:div.m-b-10
    [:div.f-s-18.f-w-b.m-b-10 "Other Tools"]
    [tool-prof-checkboxes background equip/misc-tools]]])

(defn background-starting-equipment [background]
  [:div.m-t-20.m-b-20
   [:div.f-s-24.f-w-b.m-b-10 "Starting Equipment"]
   [:div.m-b-10
    [:div.f-s-18.f-w-b.m-b-10 "Treasure"]
    [input-builder-field
     [:span.f-w-b "Gold"]
     (get-in background [:treasure :gp])
     #(dispatch [::bg/set-background-gold %])
     {:class "input h-40"
      :type :number}]]
   [:div.m-b-10
    [:div.f-s-18.f-w-b.m-b-10 "Clothing"]
    [:div [starting-equipment-checkboxes background equip/clothes]]]
   [:div.m-b-10
    [:div.f-s-18.f-w-b.m-b-10 "Artisan's Tools"]
    [:div [starting-equipment-choice-checkboxes background equip/artisans-tools "Artisan's Tools"]]
    [:div [starting-equipment-checkboxes background equip/artisans-tools]]]
   [:div.m-b-20
    [:div.f-s-18.f-w-b.m-b-10 "Musical Instruments"]
    [starting-equipment-choice-checkboxes background equip/musical-instruments "Musical Instruments"]]
   [:div.m-b-10
    [:div.f-s-18.f-w-b.m-b-10 "Other Tools"]
    [starting-equipment-checkboxes background equip/misc-tools]]
   [:div.m-b-10
    [:div.f-s-18.f-w-b.m-b-10 "Holy Symbols"]
    [starting-equipment-checkboxes background equip/holy-symbols]]
   [:div.m-b-10
    [:div.f-s-18.f-w-b.m-b-10 "Other Equipment"]
    [starting-equipment-checkboxes background equip/misc-equipment]]])

(defn feat-prereqs [feat]
  [:div.m-b-20
   [:div.f-s-24.f-w-b.m-b-10 "Prerequisites"]
   [:div.flex.flex-wrap
    [:div.m-r-20.m-b-10
     [comps/labeled-checkbox
      "The ability to cast at least one spell"
      (get-in feat [:prereqs :spellcasting])
      false
      #(dispatch [::feats/toggle-spellcasting-prereq])]]
    [:div
     (doall
      (map
       (fn [{:keys [name key]}]
         ^{:key key}
         [:div.m-r-20.m-b-10
          [comps/labeled-checkbox
           (str name " 13 or higher")
           (get-in feat [:prereqs key])
           false
           #(dispatch [::feats/toggle-ability-prereq key])]])
       opt/abilities))]
    [:div
     (doall
      (map
       (fn [key]
         ^{:key key}
         [:div.m-r-20.m-b-10
          (let [prop-key key]
            [comps/labeled-checkbox
             (str "Proficiency with " (name key) " armor")
             (get-in feat [:prereqs prop-key])
             false
             #(dispatch [::feats/toggle-ability-prereq prop-key])])])
       armor/armor-types))]
    [:div
     (doall
      (map
       (fn [{:keys [key name] :as race}]
         ^{:key key}
         [:div.m-r-20.m-b-10
          [comps/labeled-checkbox
           (str name " race")
           (get-in feat [:path-prereqs :race key])
           false
           #(dispatch [::feats/toggle-path-prereq [:race key]])]])
       @(subscribe [::races/races])))]]])

(defn feat-ability-increase-options [feat]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Ability Increase Options"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [{:keys [name key]}]
        ^{:key key}
        [:div.m-r-20.m-b-10
         [comps/labeled-checkbox
          name
          (get-in feat [:ability-increases key])
          false
          #(dispatch [::feats/toggle-feat-ability-increase key])]])
      opt/abilities))]
   [:div.m-r-20.m-b-10
    [comps/labeled-checkbox
     "You also gain proficiency in saving throws with the above chosen abilities"
     (get-in feat [:ability-increases :saves?])
     false
     #(dispatch [::feats/toggle-feat-ability-increase :saves?])]]
   [:div (let [increases (:ability-increases feat)
               non-save (disj increases :saves?)]
           (when (seq non-save)
             (str "= \"Increase your "
                  (common/list-print
                   (map
                    (comp :name opt/abilities-map)
                    non-save)
                   "or")
                  " score by 1, to a maximum of 20."
                  (when (increases :saves?)
                    " You gain proficiency in the saves using the chosen ability.\""))))]])

(defn feat-skill-proficiency [feat]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Skill or Tool Proficiency"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [num]
        ^{:key num}
        [:div.m-r-20.m-b-10
         (let [kw :skill-tool-choice]
           [comps/labeled-checkbox
            (str "You gain proficiency in " num " skills or tools of your choice")
            (= num (get-in feat [:props kw]))
            false
            #(dispatch [::feats/toggle-feat-value-prop kw num])])])
      (range 1 4)))]])

(defn feat-weapon-proficiency [feat]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Weapon Proficiency"]
   [:div.flex.flex-wrap
    [:div.m-r-20.m-b-10
     (let [kw :improvised-weapons-prof]
       [comps/labeled-checkbox
        "You gain proficiency with improvised weapons"
        (get-in feat [:props kw])
        false
        #(dispatch [::feats/toggle-feat-prop kw])])]
    (doall
     (map
      (fn [num]
        ^{:key num}
        [:div.m-r-20.m-b-10
         (let [kw :weapon-prof-choice]
           [comps/labeled-checkbox
            (str "You gain proficiency with " num " weapons of your choice")
            (= num (get-in feat [:props kw]))
            false
            #(dispatch [::feats/toggle-feat-value-prop kw num])])])
      (range 3 5)))]])

(defn option-armor-proficiency [option toggle-map-prop-event]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Armor Proficiency"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [armor-type]
        ^{:key armor-type}
        [:div.m-r-20.m-b-10
         (let [kw :armor-prof]
           [comps/labeled-checkbox
            (str "You gain proficiency with " (name armor-type) (when (not= armor-type :shields) " armor"))
            (get-in option [:props kw armor-type])
            false
            #(dispatch [toggle-map-prop-event kw armor-type])])])
      (conj armor/armor-types :shields)))]])

(defn feat-armor-proficiency [feat]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Armor Proficiency"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [armor-type]
        ^{:key armor-type}
        [:div.m-r-20.m-b-10
         (let [kw :armor-prof]
           [comps/labeled-checkbox
            (str "You gain proficiency with " (name armor-type) (when (not= armor-type :shields) " armor"))
            (get-in feat [:props kw armor-type])
            false
            #(dispatch [::feats/toggle-feat-map-prop kw armor-type])])])
      (conj armor/armor-types :shields)))
    [:div.m-r-20.m-b-10
     (let [kw :medium-armor-stealth]
       [comps/labeled-checkbox
        "Wearing medium armor doesn't give disadvantage on Stealth checks"
        (get-in feat [:props kw])
        false
        #(dispatch [::feats/toggle-feat-prop kw])])]
    [:div.m-r-20.m-b-10
     (let [kw :medium-armor-max-dex-3]
       [comps/labeled-checkbox
        "When wearing medium armor, you can add 3 to your AC if your Dexterity is 16+"
        (get-in feat [:props kw])
        false
        #(dispatch [::feats/toggle-feat-prop kw])])]]])

(defn option-hps [option toggle-value-prop-event]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Hit Points"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [num]
        ^{:key num}
        [:div.m-r-20.m-b-10
         (let [kw :max-hp-bonus]
           [comps/labeled-checkbox
            (str "Your hit point maximum increases by " num " for each of your levels")
            (= (get-in option [:props kw]) num)
            false
            #(dispatch [toggle-value-prop-event kw num])])])
      (range 1 3)))]])

(defn feat-hps [feat]
  (option-hps feat ::feats/toggle-feat-value-prop))

(defn feat-speed-bonuses [feat]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Speed Bonuses"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [v]
        ^{:key v}
        [:div.m-r-20.m-b-10
         (let [kw :speed]
           [comps/labeled-checkbox
            (str "Your speed is increased by " v " ft.")
            (= v (get-in feat [:props kw]))
            false
            #(dispatch [::feats/toggle-feat-value-prop kw v])])])
      (range 5 20 5)))]])

(defn feat-initiative-bonuses [feat]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Initiative Bonuses"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [v]
        ^{:key v}
        [:div.m-r-20.m-b-10
         (let [kw :initiative]
           [comps/labeled-checkbox
            (str "You gain a +" v " bonus to initiative")
            (= v (get-in feat [:props kw]))
            false
            #(dispatch [::feats/toggle-feat-value-prop kw v])])])
      (range 1 6)))]])

(defn feat-languages [feat]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Languages"]
   [:div.flex.flex-wrap
    (doall
     (map
      (fn [v]
        ^{:key v}
        [:div.m-r-20.m-b-10
         (let [kw :language-choice]
           [comps/labeled-checkbox
            (str "You learn " v " languages of your choice.")
            (= v (get-in feat [:props kw]))
            false
            #(dispatch [::feats/toggle-feat-value-prop kw v])])])
      (range 1 4)))]])

(defn option-damage-resistance [option toggle-map-prop-event]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Damage Resistances"]
   (let [kw :damage-resistance]
     [:div.flex.flex-wrap
      [:div.m-r-20.m-b-10
       [comps/labeled-checkbox
        "Resistance to damage from traps"
        (get-in option [:props kw :traps])
        false
        #(dispatch [toggle-map-prop-event kw :traps])]]
      (doall
       (map
        (fn [damage-type]
          ^{:key damage-type}
          [:div.m-r-20.m-b-10
           [comps/labeled-checkbox
            (str "Resistance to " (name damage-type) " damage")
            (get-in option [:props kw damage-type])
            false
            #(dispatch [toggle-map-prop-event kw damage-type])]])
        opt/damage-types))])])

(defn option-damage-immunity [option toggle-map-prop-event]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Damage Immunities"]
   (let [kw :damage-immunity]
     [:div.flex.flex-wrap
      (doall
       (map
        (fn [damage-type]
          ^{:key damage-type}
          [:div.m-r-20.m-b-10
           [comps/labeled-checkbox
            (str "Immunity to " (name damage-type) " damage")
            (get-in option [:props kw damage-type])
            false
            #(dispatch [toggle-map-prop-event kw damage-type])]])
        opt/damage-types))])])

(defn option-damage-vulnerability [option toggle-map-prop-event]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Damage Vulnerabilities"]
   (let [kw :damage-vulnerability]
     [:div.flex.flex-wrap
      (doall
       (map
        (fn [damage-type]
          ^{:key damage-type}
          [:div.m-r-20.m-b-10
           [comps/labeled-checkbox
            (str "Vulnerability to " (name damage-type) " damage")
            (get-in option [:props kw damage-type])
            false
            #(dispatch [toggle-map-prop-event kw damage-type])]])
        opt/damage-types))])])

(defn option-condition-immunity [option toggle-map-prop-event]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Condition Immunities"]
   (let [kw :condition-immunity]
     [:div.flex.flex-wrap
      (doall
       (map
        (fn [{:keys [name key]}]
          ^{:key key}
          [:div.m-r-20.m-b-10
           [comps/labeled-checkbox
            (str "Immunity to being " name)
            (get-in option [:props kw key])
            false
            #(dispatch [toggle-map-prop-event kw key])]])
        opt/conditions))])])

(defn option-weapon-proficiency [option toggle-map-prop-event]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Weapon Proficiencies"]
   (let [kw :weapon-prof]
     [:div.flex.flex-wrap
      (doall
       (concat
        (map
         (fn [weapon-type]
           ^{:key weapon-type}
           [:div.m-r-20.m-b-10
            [comps/labeled-checkbox
             (str "All " (common/safe-capitalize-kw weapon-type) " Weapons")
             (get-in option [:props kw weapon-type])
             false
             #(dispatch [toggle-map-prop-event kw weapon-type])]])
         [:simple :martial])
        (map
         (fn [{:keys [key name]}]
           ^{:key key}
           [:div.m-r-20.m-b-10
            [comps/labeled-checkbox
             name
             (get-in option [:props kw key])
             false
             #(dispatch [toggle-map-prop-event kw key])]])
         @(subscribe [::mi/custom-and-standard-weapons]))))])])

(defn option-traits [option
                     add-trait-event
                     edit-trait-name-event
                     edit-trait-type-event
                     edit-trait-description-event
                     delete-trait-event
                     & {:keys [edit-trait-level-event types title button-title]}]
  [:div.m-b-20
   [:div.p-t-10.p-b-10.f-w-b.flex.justify-cont-s-b.align-items-c
    [:div.f-s-24.f-w-b.m-b-10 (or title "Features/Traits")]
    [:div
     [:button.form-button.m-l-5
      {:on-click (make-event-handler add-trait-event)}
      (or button-title "add feature / trait")]]]
   [:div
    (if (seq (:traits option))
      (doall
       (map-indexed
        (fn [i {:keys [name type description level]}]
          ^{:key i}
          [:div.m-b-30
           [:div.flex.align-items-end.m-b-10
            [:div.flex-grow-1
             [input-builder-field
              [:span.f-w-b "Name"]
              name
              #(dispatch [edit-trait-name-event i %])
              {:class "input h-40"}]]
            (when types
              [:div.flex-grow-1.m-l-5
               [labeled-dropdown
                "Type"
                {:items types
                 :value type
                 :on-change #(dispatch [edit-trait-type-event i (keyword %)])}]])
            (when edit-trait-level-event
              [:div.m-l-5
               [labeled-dropdown
                "Unlocked at Level"
                {:items (map
                         (fn [lvl]
                           {:title lvl
                            :value lvl})
                         (range 1 21))
                 :value level
                 :on-change #(dispatch [edit-trait-level-event i (js/parseInt %)])}]])
            [:div
             [:button.form-button.m-l-5
              {:on-click #(dispatch [delete-trait-event i])}
              "delete"]]]
           [:div.w-100-p
            [:div.f-w-b
             "Description"]
            [textarea-field
             {:value description
              :on-change #(dispatch [edit-trait-description-event i %])}]]])
        (:traits option)))
      [:div.p-10.bg-lighter.pointer
       {:on-click #(dispatch [add-trait-event])}
       [:span "There are currently no features/traits, click "]
       [:span.orange.underline "here"]
       [:span " or on the button above to add one."]])]])

(defn option-saving-throw-advantages [option toggle-map-prop-event]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Saving Throw Advantage"]
   (let [kw :saving-throw-advantage]
     [:div.flex.flex-wrap
      (doall
       (map
        (fn [{:keys [name key]}]
          ^{:key key}
          [:div.m-r-20.m-b-10
           [comps/labeled-checkbox
            (str "You have advantage on saving throws against being " name)
            (get-in option [:props kw key])
            false
            #(dispatch [toggle-map-prop-event kw key])]])
        opt/conditions))])])

(defn feat-damage-resistance [feat]
  (option-damage-resistance feat ::feats/toggle-feat-map-prop))

;; dead — zero callers
#_(defn subrace-damage-resistance [subrace]
  (option-damage-resistance subrace ::feats/toggle-subrace-map-prop))

(defn feat-misc-modifiers [feat]
  [:div.m-b-20
   [:div.f-s-18.f-w-b.m-b-10 "Misc. Modifiers"]
   [:div.flex.flex-wrap
    [:div.m-r-20.m-b-10
     (let [kw :two-weapon-ac-1]
       [comps/labeled-checkbox
        "+1 AC Bonus while wielding two melee weapons"
        (get-in feat [:props kw])
        false
        #(dispatch [::feats/toggle-feat-prop kw])])]
    [:div.m-r-20.m-b-10
     (let [kw :two-weapon-any-one-handed]
       [comps/labeled-checkbox
        "You can use two-weapon fighting with any one-handed melee weapon"
        (get-in feat [:props kw])
        false
        #(dispatch [::feats/toggle-feat-prop kw])])]
    [:div.m-r-20.m-b-10
     (let [kw :saving-throw-advantage-traps]
       [comps/labeled-checkbox
        "Advantage on saving throws against traps"
        (get-in feat [:props kw])
        false
        #(dispatch [::feats/toggle-feat-prop kw])])]
    [:div.m-r-20.m-b-10
     (let [kw :passive-perception-5]
       [comps/labeled-checkbox
        "You gain a +5 to your passive Perception"
        (get-in feat [:props kw])
        false
        #(dispatch [::feats/toggle-feat-prop kw])])]
    [:div.m-r-20.m-b-10
     (let [kw :passive-investigation-5]
       [comps/labeled-checkbox
        "You gain a +5 to your passive Investigation"
        (get-in feat [:props kw])
        false
        #(dispatch [::feats/toggle-feat-prop kw])])]]])

(defn feat-spellcasting [feat]
  [:div.m-b-30
   [:div.f-s-18.f-w-b.m-b-10 "Spellcasting"]
   [:div.flex.flex-wrap
    [:div.m-r-20.m-b-10
     (let [kw :magic-novice]
       [comps/labeled-checkbox
        "Choose a class, gain (2) cantrips and (1) 1st-level spell from that class's spell list"
        (get-in feat [:props kw])
        false
        #(dispatch [::feats/toggle-feat-prop kw])])]
    [:div.m-r-20.m-b-10
     (let [kw :ritual-casting]
       [comps/labeled-checkbox
        "Choose a class, gain (2) 1st-level ritual spells from that class's spell list"
        (get-in feat [:props kw])
        false
        #(dispatch [::feats/toggle-feat-prop kw])])]
    [:div.m-r-20.m-b-10
     (let [kw :attack-spell]
       [comps/labeled-checkbox
        "Choose a class, gain a cantrip requiring an attack roll from that class's spell list"
        (get-in feat [:props kw])
        false
        #(dispatch [::feats/toggle-feat-prop kw])])]]])

;;;; TODO remove when no longer useful for debugging
#_(defn print-plugin-names []
  (let [plugins @(subscribe [::e5/plugins])]
    (doseq [plugin-name (keys plugins)]
      (js/console.log plugin-name))))

;; dead — zero callers
#_(def option-pack-styles
  {:class "flex-grow-1 m-l-5 m-b-20"
   :name "option-pack"})

(defn get-plugin-names []
  (let [plugins @(subscribe [::e5/plugins])]
    (map (fn [plugin-name]
           {:value plugin-name
            :title plugin-name})
         ;; safe fold: a nil/non-string plugin key sorts as "" instead of crashing
         (sort-by common/lower-case (keys plugins)))))

;;; Create a datalist element and load the plugin names
(defn plugin-datalist [label plugin-val dispatch-event] 
  (let [selected-value (atom (or (:option-pack plugin-val) "")) ;TODO: reframe functions may or may not help handle this more efficiently
        ]
    (fn []
      [:div.flex-grow-1
       {:class "m-l-5 m-b-20"
        :name "option-pack"}
       [:div.f-w-b.m-b-5 label]
       [:input {:type "text"
                :list "plugins-list"
                :name "plugins-choice"
                :id "plugins-choice"
                :class (str "input h-40"
                            (case (get @(subscribe [:builder-field-errors]) :option-pack)
                              :missing " builder-field-unfilled"
                              :invalid " builder-field-invalid"
                              ""))
                :placeholder "Default Option Source"
                :value @selected-value
                :onChange #(do
                             ; When user types in input field:
                             ; 1. Update the local state of the component with the new value
                             ; 2. Dispatch event to update the state of the entire app
                             ;    w/ new value to save to app db (can be used elsewhere in app)
                             (reset! selected-value (-> % .-target .-value))
                             (dispatch [dispatch-event :option-pack @selected-value])
                             (dispatch [:clear-builder-field-error :option-pack])
                             )
                }]
       [:datalist {:id "plugins-list" :class "width-100-p"}
        (for [{:keys [title value]} (get-plugin-names)]
          [:option {:key title :value value}])]
      ]
       )))

(defn feat-builder []
  (let [feat @(subscribe [::feats/builder-item])
        plugins @(subscribe [::e5/plugins])]
    [:div.p-20.main-text-color
     [:div.m-b-20.flex.flex-wrap
      [feat-input-field
       "Name"
       :name
       feat]
      [plugin-datalist 
         option-source-name-label 
         feat
         ::feats/set-feat-prop
       ]
       ;"m-l-5 m-b-20"]
      [:div.w-100-p
       [:div.f-w-b
        "Description"]
       [textarea-field
        {:value (get feat :description)
         :on-change #(dispatch [::feats/set-feat-prop :description %])}]]]
     [:div [feat-prereqs feat]]
     [:div.f-s-24.f-w-b.m-b-10 "Modifiers"]
     [:div [feat-ability-increase-options feat]]
     [:div [feat-skill-proficiency feat]]
     [:div [feat-languages feat]]
     [:div [feat-weapon-proficiency feat]]
     [:div [feat-armor-proficiency feat]]
     [:div [feat-hps feat]]
     [:div [feat-damage-resistance feat]]
     [:div [feat-speed-bonuses feat]]
     [:div [feat-initiative-bonuses feat]]
     [:div [feat-misc-modifiers feat]]
     [:div [feat-spellcasting feat]]
     [:div [option-skill-proficiency-or-expertise feat ::feats/toggle-feat-map-prop]]
     [:div [option-tool-proficiency-or-expertise feat ::feats/toggle-feat-map-prop]]]))

;; dead — zero callers
#_(defn selection-selector [index selection-cfg value-change-event]
  (let [selections @(subscribe [::selections/plugin-selections])]
    [:div.flex
     [:div.m-r-5
      [labeled-dropdown
       "Selection Name"
       {:items (cons
                {:title "<select selection>"
                 :value nil}
                (map
                 obj-to-item
                 selections))
        :value (get selection-cfg :key)
        :on-change #(dispatch [value-change-event index (assoc selection-cfg :key (keyword %))])}]]
     (when (:key selection-cfg)
       [:div
        [labeled-dropdown
         "Amount to Select"
         {:items (map
                  value-to-item
                  (range 1 31))
          :value (get selection-cfg :choose)
          :on-change #(dispatch [value-change-event index (assoc selection-cfg :choose (js/parseInt %))])}]])]))

(defn spell-selector [index spell-cfg value-change-event]
  (let [spells @(subscribe [::spells/spells-for-level (or (:level spell-cfg) 0)])
        spells-map @(subscribe [::spells/spells-map])
        spell-kw (get spell-cfg :key)
        spell (get spells-map spell-kw)]
    [:div.flex
     [:div
      [labeled-dropdown
       "Spell Level"
       {:items (map
                (fn [lvl]
                  {:title lvl
                   :value lvl})
                (range 0 10))
        :value (:level spell-cfg)
        :on-change #(dispatch [value-change-event index (assoc spell-cfg :level (js/parseInt %))])}]]
     [:div.m-l-5
      [labeled-dropdown
       "Spellcasting Ability"
       {:items (cons
                {:title "<select ability>"
                 :value :select
                 :disabled? true}
                (map
                 obj-to-item
                 opt/abilities))
        :value (or (:ability spell-cfg) :select)
        :on-change #(dispatch [value-change-event
                               index
                               (assoc spell-cfg
                                 :ability
                                 (keyword 'orcpub.dnd.e5.character %))])}]]
     [:div.m-l-5
      [labeled-dropdown
       "Spell"
       {:items (cons
                {:title "<select spell>"
                 :value :select
                 :disabled? true}
                (map
                 obj-to-item
                 spells))
        :value (or (:key spell-cfg) :select)
        :on-change #(dispatch [value-change-event index (assoc spell-cfg :key (keyword %))])}]]]))

(def damage-dropdown-values
  (map (fn [kw]
         {:title (name kw)
          :value kw})
       opt/damage-types))

(defn modifier-values []
  (sorted-map-by
   <
   :weapon-prof {:name "Weapon Proficiency"
                 :value-fn keyword
                 :values (common/aloof-sort-by :title (concat
                          (map
                           (fn [type]
                             {:title (str "All " (name type))
                              :value type})
                           [:simple :martial])
                          (map
                           obj-to-item
                           @(subscribe [::mi/custom-and-standard-weapons]))
                          ))}
   :num-attacks {:name "Number of Attacks"
                 :value-fn js/parseInt
                 :values (map
                          value-to-item
                          (range 2 5))}
   :damage-resistance {:name "Damage Resistance"
                       :value-fn keyword
                       :values damage-dropdown-values}
   :damage-immunity {:name "Damage Immunity"
                     :value-fn keyword
                     :values damage-dropdown-values}
   :saving-throw-advantage {:name "Saving Throw Advantage"
                            :value-fn keyword
                            :values (map
                                     obj-to-item
                                     opt/conditions)}
   :skill-prof {:name "Skill Proficiency"
                :value-fn keyword
                :values (map
                         obj-to-item
                         skills/skills)}
   :armor-prof {:name "Armor Proficiency"
                :value-fn keyword
                :values (concat
                         (map
                          (fn [armor-type]
                            {:title armor-type
                             :value (name armor-type)})
                          [:light :medium :heavy :shields]))}
   :tool-prof {:name "Tool Proficiency"
               :value-fn keyword
               :values (map
                        obj-to-item
                        equip/tools)}
   :flying-speed {:name "Flying Speed"
                  :value-fn js/parseInt
                  :values (map
                           (fn [speed]
                             {:title (str speed " ft.")
                              :value speed})
                           (range 10 130 10))}
   :flying-speed-equals-walking-speed {:name "Flying Speed Equals Walking Speed"}
   :swimming-speed {:name "Swimming Speed"
                    :value-fn js/parseInt
                    :values (map
                             (fn [speed]
                               {:title (str speed " ft.")
                                :value speed})
                             (range 10 130 10))}
   :spell {:name "Spell"
           :component spell-selector}))

(defn modifier-level-selector [index level edit-modifier-level-event]
  [labeled-dropdown
   "Unlock at Level"
   {:items (map
            (fn [lvl]
              {:title lvl
               :value lvl})
            (range 1 21))
    :value level
    :on-change #(dispatch [edit-modifier-level-event index (js/parseInt %)])}])

(defn option-level-selection [{:keys [type level num]}
                             index
                             edit-selection-type-event
                             edit-selection-num-event
                             edit-selection-level-event
                             delete-selection-event]
  (let [selections @(subscribe [::selections/plugin-selections])]
    [:div
     [:div.flex.flex-wrap.align-items-end.m-b-20
      [:div.m-t-10
       [labeled-dropdown
        "Selection Type"
        {:items (sort-by :title (concat
                 [{:title "<select type to add>"
                   :disabled? true
                   :value :select}]
                 (map
                  obj-to-item
                  selections)
                 [{:title "<create new selection>"
                   :value :new-selection}]))
         :value (or type :select)
         :on-change #(if (= "new-selection" %)
                       (dispatch [::selections/new-selection])
                       (dispatch [edit-selection-type-event index (keyword %)]))}]]
      (when type
        [:div.m-t-10.m-l-5
         [modifier-level-selector index level edit-selection-level-event]])
      (when type
        [:div.m-t-10.m-l-5
         [labeled-dropdown
          "Amount to Select at this Level"
          {:items (map
                   value-to-item
                   (range 1 11))
           :value (or num 1)
           :on-change #(dispatch [edit-selection-num-event index (js/parseInt %)])}]])
      (when (or type level num)
        [:div.m-t-10
         [:button.form-button.m-l-5
          {:on-click #(dispatch [delete-selection-event index])}
          "delete"]])]]))

(defn option-level-modifier [{:keys [type value level]}
                             index
                             edit-modifier-type-event
                             edit-modifier-value-event
                             edit-modifier-level-event
                             delete-modifier-event]
  (let [mod-values (modifier-values)
        {:keys [name values component value-fn]} (when type (mod-values type))]
    [:div
     [:div.flex.flex-wrap.align-items-end.m-b-20
      [:div.m-t-10
       [labeled-dropdown
        "Modifier Type"
        {:items (sort-by :title (cons
                 {:title "<select type to add>"
                  :disabled? true
                  :value :select}
                 (map
                  (fn [[kw {:keys [name]}]]
                    {:title name
                     :value kw})
                  mod-values)))
         :value (if type (clojure.core/name type) :select)
         :on-change #(dispatch [edit-modifier-type-event index (keyword %)])}]]
      (when type
        [:div.m-t-10.m-l-5
         [modifier-level-selector index level edit-modifier-level-event]])
      (if (and type values)
        [:div.m-l-5.m-t-10
         [labeled-dropdown
          name
          {:items (cons
                   {:title "<select value>"
                    :disabled? true
                    :value :select}
                   values)
           :value (or value :select)
           :on-change #(dispatch [edit-modifier-value-event index (value-fn %)])}]]
        (when component
          [:div.m-l-5 [component index value edit-modifier-value-event]]))
      (when (or type level value)
        [:div.m-t-10
         [:button.form-button.m-l-5
          {:on-click #(dispatch [delete-modifier-event index])}
          "delete"]])]]))

(defn option-level-modifiers [{:keys [level-modifiers]}
                              add-modifier-event
                              edit-modifier-type-event
                              edit-modifier-value-event
                              edit-modifier-level-event
                              delete-modifier-event]
  [:div
   [:div
    (doall
     (map-indexed
      (fn [index modifier]
        ^{:key index}
        [option-level-modifier
         modifier
         index
         edit-modifier-type-event
         edit-modifier-value-event
         edit-modifier-level-event
         delete-modifier-event])
      level-modifiers))]
   [:div
    [option-level-modifier
     nil
     (count level-modifiers)
     edit-modifier-type-event
     edit-modifier-value-event
     edit-modifier-level-event
     delete-modifier-event]]])

(def selection-help
  [:div.p-20
   "Selections provide options that one can pick when building a character, typically associated with a class and given at a certain level. The class and subclass builders allow selections to be added. Examples of selections one might want to build are 'Martial Maneuvers' or 'Totem Spirit'"])

(defn title-with-help []
  (let [expanded? (r/atom false)]
    (fn [title help]
      [:div
       [:div
        title
        [:span.orange.pointer.f-s-18.m-l-5
         {:on-click #(swap! expanded? not)}
         [:i.fa.fa-question-circle.m-r-2]
         [:i.fa {:class (if @expanded? "fa-caret-up" "fa-caret-down")}]]]
       (when @expanded?
         [:div.bg-light.f-s-18
          help])])))

(defn cantrip-num-selector [level cantrips-known]
  [:div.flex.m-b-5
   [:div.w-150
    [dropdown
     {:items (cons
              {:title "<select level>"}
              (map
               (fn [v]
                 {:title v
                  :value v})
               (range 2 21)))
      :value level
      :on-change #(dispatch [::classes/set-class-path-prop
                             [:spellcasting :cantrips-known]
                             (-> cantrips-known
                                 (dissoc level)
                                 (assoc (js/parseInt %) 1))])}]]
   [:button.form-button.m-l-5
    {:on-click #(dispatch [::classes/set-class-path-prop
                           [:spellcasting :cantrips-known]
                           (dissoc cantrips-known level)])}
    "remove"]])

(defn option-level-selections [{:keys [level-selections]}
                              add-selection-event
                              edit-selection-type-event
                              edit-selection-num-event
                              edit-selection-level-event
                              delete-selection-event]
  [:div
   [title-with-help
    [:span.f-w-b.f-s-24 "Selections"]
    selection-help]
   [:div
    (doall
     (map-indexed
      (fn [index selection]
        ^{:key index}
        [option-level-selection
         selection
         index
         edit-selection-type-event
         edit-selection-num-event
         edit-selection-level-event
         delete-selection-event])
      level-selections))]
   [:div
    [option-level-selection
     nil
     (count level-selections)
     edit-selection-type-event
     edit-selection-num-event
     edit-selection-level-event
     delete-selection-event]]])

(defn class-builder []
  (let [class @(subscribe [::classes/builder-item])
        spell-lists @(subscribe [::spells/spell-lists])
        class-key (get class :class)
        classes @(subscribe [::classes/classes])
        class-map @(subscribe [::classes/class-map])
        mobile? @(subscribe [:mobile?])]
    [:div.p-20.main-text-color
     [:div.flex.flex-wrap
      [:div.m-b-20.flex-grow-1
       [class-input-field
        "Name"
        :name
        class]]
      [plugin-datalist
       option-source-name-label
       class
       ::classes/set-class-prop]
      ]
     [:div.m-b-20
      [:div.f-w-b
       "Description"]
      [textarea-field
       {:value (get class :help)
        :on-change #(dispatch [::classes/set-class-prop :help %])}]]
     [:div.m-b-20.flex.flex-wrap
      [:div.m-l-5.flex-grow-1
       [labeled-dropdown
        "Hit Die"
        {:items (map
                 (fn [sides]
                   {:title sides
                    :value sides})
                 [6 8 10 12])
         :value (:hit-die class)
         :on-change #(dispatch [::classes/set-class-prop :hit-die (js/parseInt %)])}]]
      [:div.m-l-5.flex-grow-1
       [labeled-dropdown
        "Pick Subclass at Level"
        {:items (map
                 (fn [level]
                   {:title level
                    :value level})
                 (range 1 4))
         :value (:subclass-level class)
         :on-change #(dispatch [::classes/set-class-prop :subclass-level (js/parseInt %)])}]]
      [:div.flex-grow-1
       [class-input-field
        "Subclass Title"
        :subclass-title
        class
        "m-l-5"]]]
     #_[:div.m-b-20
        [:div.f-w-b
         "Subclass Description"]
        [textarea-field
         {:value (get class :subclass-help)
          :on-change #(dispatch [::classes/set-class-prop :subclass-help %])}]]
     [:div.m-b-20
      [class-input-field
       "Subclass Flavor"
       :subclass-help
       class]]
     [:div.m-b-30
      [:div.f-s-24.f-w-b.m-b-10 "Saving Throws"]
      [:div.flex.flex-wrap
       (doall
        (map
         (fn [{:keys [name key]}]
           ^{:key key}
           [:div.m-r-20.m-b-10
            [comps/labeled-checkbox
             name
             (get-in class [:profs :save key])
             false
             #(dispatch [::classes/toggle-save-prof key])]])
         opt/abilities))]]
     [:div.m-b-30
      [:div.f-s-24.f-w-b.m-b-10 "Ability Increase Levels"]
      [:div.flex.flex-wrap
       (let [asi-levels-set (set (:ability-increase-levels class))]
         (doall
          (map
           (fn [level]
             ^{:key level}
             [:div.m-r-20.m-b-10
              [comps/labeled-checkbox
               level
               (asi-levels-set level)
               false
               #(dispatch [::classes/toggle-ability-increase-level level])]])
           (range 4 21))))]]
     (let [spellcaster? (boolean (get class :spellcasting))]
       [:div.m-b-30
        [:div.f-s-24.f-w-b.m-b-10 "Spellcasting"]
        [:div.flex.flex-wrap.m-b-20
         [labeled-dropdown
          "Does this class have spell slots?"
          {:items [{:title "No"
                    :value false}
                   {:title "Yes"
                    :value true}]
           :value spellcaster?
           :on-change #(dispatch [::classes/set-class-prop
                                  :spellcasting
                                  (when (= "true" %)
                                    {:level-factor 3
                                     :known-mode :schedule
                                     :ability ::char/cha
                                     :spells-known classes/third-caster-spells-known-schedule})])}]
         (when spellcaster?
           [:div.m-l-5
            [labeled-dropdown
             "What spell list does this class use?"
             {:items (cons
                      {:title "Custom"
                       :value "custom"}
                      (map
                       (fn [[class-kw]]
                         ^{:key class-kw}
                         {:title (get-in class-map [class-kw ::template/name])
                          :value class-kw})
                       spell-lists))
              :value (get-in class [:spellcasting :spell-list-kw])
              :on-change #(dispatch [::classes/set-class-path-prop
                                     [:spellcasting :spell-list-kw] (when (not= "custom" %)
                                                                      (keyword %))])}]])
         (when spellcaster?
           [:div.m-l-5
            [labeled-dropdown
             "Spellcasting ability"
             {:items (cons
                      {:title "<select ability>"
                       :value nil}
                      (map
                       obj-to-item
                       opt/abilities))
              :value (get-in class [:spellcasting :ability])
              :on-change #(dispatch [::classes/set-class-path-prop [:spellcasting :ability] (keyword "orcpub.dnd.e5.character" %)])}]])
         (when spellcaster?
           [:div.m-l-5
            [labeled-dropdown
             "At what level does this class first gain spell slots?"
             {:items (map
                      value-to-item
                      (range 1 4))
              :value (get-in class [:spellcasting :level-factor] 1)
              :on-change #(let [level-factor (js/parseInt %)]
                            (dispatch [::classes/set-class-path-prop
                                       [:spellcasting :level-factor] level-factor
                                       [:spellcasting :spells-known] (case level-factor
                                                                       1 classes/full-caster-spells-known-schedule
                                                                       2 classes/half-caster-spells-known-schedule
                                                                       3 classes/third-caster-spells-known-schedule)]))}]])]
        (when (and spellcaster?
                 (not (get-in class [:spellcasting :spell-list-kw])))
          (let [cantrips? (get-in class [:spellcasting :cantrips?])]
            [:div
             [:div.f-s-18.f-w-b "Cantrips"]
             [:div.flex.flex-wrap.m-b-20
              [:div
               [labeled-dropdown
                "Does this class gain cantrips?"
                {:items [{:title "No"
                          :value false}
                         {:title "Yes"
                          :value true}]
                 :value cantrips?
                 :on-change #(dispatch [::classes/set-class-path-prop
                                        [:spellcasting :cantrips?]
                                        (= % "true")])}]]
              (when cantrips?
                [:div.m-l-5
                 [labeled-dropdown
                  "How many cantrips does this class know at first level?"
                  {:items (map
                           (fn [v]
                             {:title v
                              :value v})
                           (range 0 6))
                   :value (get-in class [:spellcasting :cantrips-known 1])
                   :on-change #(dispatch [::classes/set-class-path-prop
                                          [:spellcasting :cantrips-known 1]
                                          (js/parseInt %)])}]])]
             (when cantrips?
               [:div.m-b-20
                [:div.f-s-18.f-w-b.m-b-5 "At what other levels does this class gain cantrips?"]
                (let [cantrips-known (get-in class [:spellcasting :cantrips-known])]
                  [:div
                   (map
                    (fn [[level]]
                      ^{:key level}
                      [cantrip-num-selector level cantrips-known])
                    (sort-by first (dissoc cantrips-known 1)))
                   [cantrip-num-selector nil cantrips-known]])])
             [:div.f-s-18.f-w-b "Select spells from which this class can choose"]
             [:div
              (doall
               (map
                (fn [level]
                  ^{:key level}
                  [:div.m-t-10
                   [:div.f-s-16.f-w-b.m-b-10 (if (zero? level)
                                               "Cantrips"
                                               (str "Level " level))]
                   [:div.flex.flex-wrap
                    (doall
                     (map
                      (fn [{:keys [name key]}]
                        ^{:key key}
                        [:div.m-r-20.m-b-10
                         [comps/labeled-checkbox
                          name
                          (get-in class [:spellcasting :spell-list level key])
                          false
                          #(dispatch [::classes/toggle-class-spell-list level key])]])
                      @(subscribe [::spells/spells-for-level level])))]])
                (range (if cantrips? 0 1)
                       (inc (case (get-in class [:spellcasting :level-factor])
                              2 5
                              3 4
                              9)))))]]))])
     [:div.m-b-10
      [option-skill-proficiency-choice
       class
       ::classes/set-class-path-prop
       ::classes/toggle-class-path-prop]]
     [:div.m-b-10
      [option-skill-expertise-choice
       class
       ::classes/set-class-path-prop
       ::classes/toggle-class-path-prop]]
     [:div.m-b-20
      [:div.f-s-24.f-w-b.m-b-10 "Modifiers"]
      [option-level-modifiers
       class
       ::e5/add-class-modifier
       ::e5/edit-class-modifier-type
       ::e5/edit-class-modifier-value
       ::e5/edit-class-modifier-level
       ::e5/delete-class-modifier]]
     [:div.m-b-20.m-t-30
      [option-level-selections
       class
       ::e5/add-class-selection
       ::e5/edit-class-selection-type
       ::e5/edit-class-selection-num
       ::e5/edit-class-selection-level
       ::e5/delete-class-selection]]
     [:div
      [option-traits
       class
       ::e5/add-class-trait
       ::e5/edit-class-trait-name
       ::e5/edit-class-trait-type
       ::e5/edit-class-trait-description
       ::e5/delete-class-trait
       :edit-trait-level-event ::e5/edit-class-trait-level
       :types [{:title "Other"
                :value :other}
               {:title "Action"
                :value :action}
               {:title "Bonus Action"
                :value :b-action}
               {:title "Reaction"
                :value :reaction}]]]]))

(defn subclass-spells [subclass spells-title spells-kw]
  [:div
   [:div.f-s-18.f-w-b.m-b-10 (str (:name subclass) " " spells-title)]
   [:table
    [:tbody
     [:tr.f-w-b
      [:th.p-5 "Spell Level"]
      [:th.p-5.t-a-l "Spells"]]
     (doall
      (map
       (fn [level]
         ^{:key level}
         [:tr
          [:th.p-5 (common/ordinal level)]
          (let [spells-for-level @(subscribe [::spells/spells-for-level level])]
            [:th.p-5
             [:div.flex.flex-wrap
              (doall
               (map
                (fn [i]
                  ^{:key i}
                  [:div.m-l-5.m-b-10
                   [dropdown
                    {:items (cons
                             {:title "<select spell>"
                              :value :select
                              :disabled? true}
                             (map
                              obj-to-item
                              spells-for-level))
                     :value (or (get-in subclass [spells-kw level i])
                                :select)
                     :on-change #(dispatch [::classes/set-class-spell spells-kw level i (keyword %)])}]])
                (range 2)))]])])
       (range 1 6)))]]])

(defn subclass-builder []
  (let [subclass @(subscribe [::classes/subclass-builder-item])
        spell-lists @(subscribe [::spells/spell-lists])
        class-key (get subclass :class)
        classes @(subscribe [::classes/classes])
        mobile? @(subscribe [:mobile?])]
    [:div.p-20.main-text-color
     [:div.flex.flex-wrap
      [:div.m-b-20
       [subclass-input-field
        "Name"
        :name
        subclass]]
      [:div.m-l-5.m-b-20
       [labeled-dropdown
        "Class"
        {:items (map
                 (fn [{:keys [:orcpub.template/name :orcpub.template/key]}]
                   {:title name
                    :value (clojure.core/name key)})
                 classes)
         :value (get subclass :class)
         :on-change #(dispatch [::classes/set-subclass-prop :class (keyword %)])}]]
      [plugin-datalist 
         option-source-name-label 
         subclass
         ::classes/set-subclass-prop
       ]
      ]
     (when (#{:fighter :rogue :warlock :cleric :paladin} class-key)
       (let [spellcasting (get subclass :spellcasting)
             spellcasting? (some? spellcasting)]
         [:div.m-b-20
          [:div.f-s-24.f-w-b.m-b-10 "Spellcasting"]
          (cond
            (#{:fighter :rogue} class-key)
            [:div.flex.flex-wrap
             [labeled-dropdown
              "Does this subclass cast wizard spells?"
              {:items (map
                       (fn [v]
                         {:title (if v "Yes" "No")
                          :value v})
                       [false true])
               :value spellcasting?
               :on-change #(dispatch [::classes/toggle-subclass-spellcasting])}]]

            (= :paladin class-key)
            [:div [subclass-spells subclass "Spells" :paladin-spells]]

            (= :cleric class-key)
            [:div [subclass-spells subclass "Domain Spells" :cleric-spells]]

            (= :warlock class-key)
            [:div [subclass-spells subclass "Expanded Spells" :warlock-spells]])]))
     [:div
      [option-skill-proficiency-choice
       subclass
       ::classes/set-subclass-path-prop
       ::classes/toggle-subclass-path-prop]]
     [:div
      [option-skill-expertise-choice
       subclass
       ::classes/set-subclass-path-prop
       ::classes/toggle-subclass-path-prop]]
     [:div.m-b-20
      [:div.f-s-24.f-w-b.m-b-10 "Modifiers"]
      [option-level-modifiers
       subclass
       ::e5/add-subclass-modifier
       ::e5/edit-subclass-modifier-type
       ::e5/edit-subclass-modifier-value
       ::e5/edit-subclass-modifier-level
       ::e5/delete-subclass-modifier]]
     [:div.m-b-20.m-t-30
      [option-level-selections
       subclass
       ::e5/add-subclass-selection
       ::e5/edit-subclass-selection-type
       ::e5/edit-subclass-selection-num
       ::e5/edit-subclass-selection-level
       ::e5/delete-subclass-selection]]
     [option-traits
      subclass
      ::e5/add-subclass-trait
      ::e5/edit-subclass-trait-name
      ::e5/edit-subclass-trait-type
      ::e5/edit-subclass-trait-description
      ::e5/delete-subclass-trait
      :edit-trait-level-event ::e5/edit-subclass-trait-level
       :types [{:title "Other"
                :value :other}
               {:title "Action"
                :value :action}
               {:title "Bonus Action"
                :value :b-action}
               {:title "Reaction"
                :value :reaction}]]]))

(defn option-spell [index
                     {:keys [level value] :as spell-cfg}
                     set-spell-level-event
                     set-spell-value-event
                     delete-spell-event]
  [:div.flex.flex-wrap.m-b-10.align-items-end
   [modifier-level-selector
    index
    level
    set-spell-level-event]
   [:div.m-l-5
    [spell-selector
     index
     value
     set-spell-value-event]]
   (when (or level value)
     [:div.m-t-10
      [:button.form-button.m-l-5
       {:on-click #(dispatch [delete-spell-event index])}
       "delete"]])])

(defn option-spells [option
                     set-spell-level-event
                     set-spell-value-event
                     delete-spell-event]
  [:div
   [:div
    (doall
     (map-indexed
      (fn [i spell-cfg]
        ^{:key i}
        [option-spell
         i
         spell-cfg
         set-spell-level-event
         set-spell-value-event
         delete-spell-event])
      (:spells option)))]
   [:div [option-spell
          (count (:spells option))
          {}
          set-spell-level-event
          set-spell-value-event
          delete-spell-event]]])

(defn subrace-builder []
  (let [subrace @(subscribe [::races/subrace-builder-item])
        race-key (get subrace :race)
        race @(subscribe [::races/race race-key])
        races @(subscribe [::races/races])
        mobile? @(subscribe [:mobile?])]
    [:div.p-20.main-text-color
     [:div.flex.flex-wrap
      [:div.m-b-20
       [subrace-input-field
        "Name"
        :name
        subrace]]
      [:div.m-l-5.m-b-20
       [labeled-dropdown
        "Race"
        {:items (map
                 (fn [{:keys [name key]}]
                   {:title name
                    :value (clojure.core/name key)})
                 races)
         :value (get subrace :race)
         :on-change #(dispatch [::races/set-subrace-prop :race (keyword %)])}]]
       [plugin-datalist
        option-source-name-label
        subrace
        ::races/set-subrace-prop]
      ]
     [:div.m-b-20.flex.flex-wrap
      [:div.m-r-5
       [labeled-dropdown
        "Size"
        {:items (map
                 (fn [kw]
                   {:title (name kw)
                    :value (name kw)})
                 ["small" "medium" "large"])
         :value (name (or (get subrace :size)
                          (get race :size)))
         :on-change #(dispatch [::races/set-subrace-prop :size (keyword %)])}]]
      [:div.m-r-5
       [labeled-dropdown
        "Speed"
        {:items (map
                 value-to-item
                 (range 5 55 5))
         :value (or (get subrace :speed)
                    (get race :speed))
         :on-change #(dispatch [::races/set-subrace-speed %])}]]
      [:div.m-r-5
       [labeled-dropdown
        "Darkvision"
        {:items (map
                 value-to-item
                 [0 60 120])
         :value (or (get subrace :darkvision)
                    (get race :darkvision))
         :on-change #(dispatch [::races/set-subrace-prop :darkvision (js/parseInt %)])}]]]
     [:div.m-b-20
      [:div.f-s-24.f-w-b.m-b-10 "Ability Score Increases"]
      [:table.t-a-c
       [:tbody
        [:tr.f-w-b
         [:th.p-2.t-a-l "Ability"]
         [:th.p-2 "Race Bonus"]
         [:th.p-2]
         [:th.p-2 "Subrace Bonus"]
         [:th.p-2]
         [:th.p-2 "Total"]]
        (doall
         (map
          (fn [{:keys [name key abbr]}]
            (let [race-bonus (get-in race [:abilities key] 0)
                  subrace-bonus (get-in subrace [:abilities key] 0)]
              ^{:key key}
              [:tr
               [:td.p-2.f-w-b.t-a-l (if mobile? abbr name)]
               [:td.p-2 race-bonus]
               [:td.p-2 "+"]
               [:td.p-2 [dropdown
                         {:items (map
                                  (fn [bonus]
                                    {:title (common/bonus-str bonus)
                                     :value bonus})
                                  (range -2 3 1))
                          :value subrace-bonus
                          :on-change #(dispatch [::races/set-subrace-ability-increase key %])}]]
               [:td.p-2 "="]
               [:td.p-2 (+ race-bonus subrace-bonus)]]))
          opt/abilities))]]]
     [:div.m-b-20
      [:div.f-s-24.f-w-b.m-b-10 "Modifiers"]
      [:div [option-hps subrace ::races/toggle-subrace-value-prop]]
      [:div [option-damage-resistance subrace ::races/toggle-subrace-map-prop]]
      [:div [option-damage-immunity subrace ::races/toggle-subrace-map-prop]]
      [:div [option-saving-throw-advantages subrace ::races/toggle-subrace-map-prop]]
      [:div [option-weapon-proficiency subrace ::races/toggle-subrace-map-prop]]
      [:div [option-armor-proficiency subrace ::races/toggle-subrace-map-prop]]
      [:div [option-tool-proficiency subrace ::races/toggle-subrace-path-prop]]
      [:div [option-skill-proficiency subrace ::races/toggle-subrace-map-prop]]
      [:div
       [option-skill-proficiency-choice
        subrace
        ::races/set-subrace-path-prop
        ::races/toggle-subrace-path-prop]]
      [:div [option-languages subrace ::races/toggle-subrace-map-prop]]]
     [:div.m-b-20
      [:div.f-s-24.f-w-b.m-b-10 "Spells"]
      [option-spells
       subrace
       ::races/set-subrace-spell-level
       ::races/set-subrace-spell-value
       ::races/delete-subrace-spell]]
     [option-traits
      subrace
      ::e5/add-subrace-trait
      ::e5/edit-subrace-trait-name
      ::e5/edit-subrace-trait-type
      ::e5/edit-subrace-trait-description
      ::e5/delete-subrace-trait
      :types [{:title "Other"
               :value :other}
              {:title "Action"
               :value :action}
              {:title "Bonus Action"
               :value :b-action}
              {:title "Reaction"
               :value :reaction}]]]))

(defn race-builder []
  (let [race @(subscribe [::races/builder-item])]
    [:div.p-20.main-text-color
     [:div.m-b-20.flex.flex-wrap
      [race-input-field
       "Name"
       :name
       race]
      [plugin-datalist
        option-source-name-label
        race
        ::races/set-race-prop]
      ]
     [:div.m-b-20
       [:div.f-w-b
        "Description"]
       [textarea-field
        {:value (get race :help)
         :on-change #(dispatch [::races/set-race-prop :help %])}]]
     [:div.m-b-20.flex.flex-wrap
      [:div.m-r-5
       [labeled-dropdown
        "Size"
        {:items (map
                 (fn [kw]
                   {:title (name kw)
                    :value (name kw)})
                 ["small" "medium" "large"])
         :value (common/safe-name (get race :size :medium))
         :on-change #(dispatch [::races/set-race-prop :size (keyword %)])}]]
      [:div.m-r-5
       [labeled-dropdown
        "Speed"
        {:items (map
                 value-to-item
                 (range 0 55 5))
         :value (get race :speed)
         :on-change #(dispatch [::races/set-race-speed %])}]]
      [:div.m-r-5
       [labeled-dropdown
        "Flying Speed"
        {:items (map
                 value-to-item
                 (range 0 55 5))
         :value (or (get-in race [:props :flying-speed]) 0)
         :on-change #(dispatch [::races/set-race-value-prop :flying-speed (js/parseInt %)])}]]
      [:div.m-r-5
       [labeled-dropdown
        "Swimming Speed"
        {:items (map
                 value-to-item
                 (range 0 55 5))
         :value (or (get-in race [:props :swimming-speed]) 0)
         :on-change #(dispatch [::races/set-race-value-prop :swimming-speed (js/parseInt %)])}]]
      [:div.m-r-5
       [labeled-dropdown
        "Darkvision"
        {:items (map
                 value-to-item
                 [0 60 120])
         :value (get race :darkvision)
         :on-change #(dispatch [::races/set-race-prop :darkvision (js/parseInt %)])}]]]
     [:div.m-b-20
      [:div.f-s-24.f-w-b.m-b-10 "Armor Class"]
      [:div.flex.flex-wrap
       [comps/labeled-checkbox
        "Without armor your AC becomes 13 + your DEX modifier."
        (get-in race [:props :lizardfolk-ac])
        false
        #(dispatch [::races/toggle-race-prop :lizardfolk-ac])]
       [:div.m-l-20
        [comps/labeled-checkbox
         "Your AC is 17, regardless of your DEX modifier or armor."
         (get-in race [:props :tortle-ac])
         false
         #(dispatch [::races/toggle-race-prop :tortle-ac])]]]]
     [:div.m-b-20
      [:div.f-s-24.f-w-b.m-b-10 "Ability Score Increases"]
      [:div.flex.flex-wrap
       (doall
        (map
         (fn [{:keys [name key]}]
           ^{:key key}
           [:div.m-l-5
            [labeled-dropdown
             name
             {:items (map
                      (fn [bonus]
                        {:title (common/bonus-str bonus)
                         :value bonus})
                      (range -2 3 1))
              :value (get-in race [:abilities key] 0)
              :on-change #(dispatch [::races/set-race-ability-increase key %])}]])
         opt/abilities))]]
     [:div.m-b-20
      [:div.f-s-24.f-w-b.m-b-10 "Modifiers"]
      [:div.m-b-20
       [:div.f-s-18.f-w-b.m-b-10 "Languages"]
       [:div [language-checkboxes race @(subscribe [::langs/languages])]]]
      [:div.m-b-20
       [:div [option-weapon-proficiency race ::races/toggle-race-map-prop]]]
      [:div.m-b-20
       [:div [option-armor-proficiency race ::races/toggle-race-map-prop]]]
      [:div.m-b-20
       [option-tool-proficiency race ::races/toggle-race-path-prop]]
      [:div.m-b-20
       [:div [option-damage-resistance race ::races/toggle-race-map-prop]]]
      [:div.m-b-20
       [:div [option-damage-immunity race ::races/toggle-race-map-prop]]]
      [:div.m-b-20
       [:div [option-skill-proficiency race ::races/toggle-race-map-prop]]]
      [:div
       [option-skill-proficiency-choice
        race
        ::races/set-race-path-prop
        ::races/toggle-race-path-prop]]
      [:div
       [option-language-proficiency-choice
        race
        ::races/set-race-path-prop
        ::races/toggle-race-path-prop]]
      [:div
       [option-weapon-proficiency-choice
        race
        ::races/set-race-path-prop
        ::races/toggle-race-path-prop]]]
     [:div.m-b-20
      [:div.f-s-24.f-w-b.m-b-10 "Spells"]
      [option-spells
       race
       ::races/set-race-spell-level
       ::races/set-race-spell-value
       ::races/delete-race-spell]]
     [option-traits
      race
      ::e5/add-race-trait
      ::e5/edit-race-trait-name
      ::e5/edit-race-trait-type
      ::e5/edit-race-trait-description
      ::e5/delete-race-trait
      :types [{:title "Other"
               :value :other}
              {:title "Action"
               :value :action}
              {:title "Bonus Action"
               :value :b-action}
              {:title "Reaction"
               :value :reaction}]]]))

(defn background-builder []
  (let [background @(subscribe [::bg/builder-item])]
    [:div.p-20.main-text-color
     [:div.m-b-20.flex.flex-wrap
      [background-input-field
       "Name"
       :name
       background]
      [plugin-datalist
       option-source-name-label
       background
       ::bg/set-background-prop]
      ]
     [:div.m-b-20
       [:div.f-w-b
        "Description"]
       [textarea-field
        {:value (get background :help)
         :on-change #(dispatch [::bg/set-background-prop :help %])}]]
     [:div [background-skill-proficiencies background]]
     [:div [background-languages background]]
     [:div [background-tool-proficiencies background]]
     [:div [background-starting-equipment background]]
     [:div
      [option-traits
       background
       ::e5/add-background-trait
       ::e5/edit-background-trait-name
       ::e5/edit-background-trait-type
       ::e5/edit-background-trait-description
       ::e5/delete-background-trait]]]))

(defn- find-duplicate-option-names
  "Returns a set of option names (lowercased via name-to-kw) that appear more than once.
   Used by selection-builder to highlight duplicate names inline."
  [options]
  (let [keys (map #(when-let [n (:name %)]
                     (when-not (s/blank? n)
                       (common/name-to-kw n)))
                  options)
        freqs (frequencies (remove nil? keys))]
    (set (map first (filter #(> (val %) 1) freqs)))))

(defn selection-builder []
  (let [selection @(subscribe [::selections/builder-item])
        options (:options selection)
        dupe-keys (find-duplicate-option-names options)
        has-dupes? (seq dupe-keys)
        ;; Collect the display names of duplicate options for the summary
        dupe-names (when has-dupes?
                     (->> options
                          (filter #(and (not (s/blank? (:name %)))
                                        (contains? dupe-keys (common/name-to-kw (:name %)))))
                          (map :name)
                          distinct
                          sort))
        ;; Check for empty/blank option names
        has-empty? (some #(s/blank? (:name %)) options)]
    [:div.p-20.main-text-color
     [:div.flex.w-100-p.flex-wrap
      [selection-input-field
       "Name"
       :name
       selection
       "m-b-20"]
      [plugin-datalist
       option-source-name-label
       selection
       ::selections/set-selection-prop]
      ]
     [:div
      [:div.flex.justify-cont-s-b
       [:div.f-s-24.f-w-b "Options"]
       [:button.form-button
        {:on-click #(dispatch [::selections/add-option])}
        "Add Option"]]
      ;; Summary warning for duplicate names
      (when has-dupes?
        [:div.p-10.m-b-10.red
         {:style {:background-color "rgba(255,0,0,0.1)"
                  :border "1px solid red"
                  :border-radius "4px"}}
         [:span.f-w-b "Duplicate names found: "]
         [:span (s/join ", " dupe-names)]
         [:div.f-s-12 "Each option must have a unique name. Rename duplicates before saving."]])
      ;; Warning for empty option names
      (when has-empty?
        [:div.p-10.m-b-10.red
         {:style {:background-color "rgba(255,0,0,0.1)"
                  :border "1px solid red"
                  :border-radius "4px"}}
         "One or more options have no name. All options must be named."])
      [:div
       (doall
        (map-indexed
         (fn [i {:keys [name description]}]
           (let [is-dupe? (and (not (s/blank? name))
                               (contains? dupe-keys (common/name-to-kw name)))
                 is-empty? (s/blank? name)]
             ^{:key i}
             [:div.m-b-30
              [:div.flex.align-items-end.m-b-10
               [:div.f-w-b.f-s-24.m-r-10 (str (inc i) ".")]
               [:div.flex-grow-1
                [input-builder-field
                 [:span.f-w-b "Name"]
                 name
                 #(dispatch [::selections/set-selection-path-prop [:options i :name] %])
                 ;; Shared cue: amber when empty (missing), red when a duplicate
                 ;; (invalid) — same language as every other builder field.
                 {:class (str "input h-40"
                              (cond is-dupe? " builder-field-invalid"
                                    is-empty? " builder-field-unfilled"
                                    :else ""))}]
                (when is-dupe?
                  [:div.red.f-s-12.m-t-2
                   "Duplicate name \u2014 rename to a unique name before saving"])
                (when is-empty?
                  [:div.red.f-s-12.m-t-2
                   "Option name is required"])]
               [:div
                [:button.form-button.m-l-5
                 {:on-click #(dispatch [::selections/delete-option i])}
                 "delete"]]]
              [:div.w-100-p
               [:div.f-w-b
                "Description"]
               [textarea-field
                {:value description
                 :on-change #(dispatch [::selections/set-selection-path-prop [:options i :description] %])}]]]))
         options))]]]))

(defn language-builder []
  (let [language @(subscribe [::langs/builder-item])]
    [:div.p-20.main-text-color
     [:div.flex.w-100-p.flex-wrap
      [language-input-field
       "Name"
       :name
       language
       "m-b-20"]
      [plugin-datalist
       option-source-name-label
       language
       ::langs/set-language-prop]
      ]
     [:div.w-100-p
      [:div.f-s-24.f-w-b
       "Description"]
      [textarea-field
       {:value (get language :description)
        :on-change #(dispatch [::langs/set-language-prop :description %])}]]]))

(defn boon-builder []
  (let [boon @(subscribe [::classes/boon-builder-item])]
    [:div.p-20.main-text-color
     [:div.flex.w-100-p.flex-wrap
      [boon-input-field
       "Name"
       :name
       boon
       "m-b-20"]
      [plugin-datalist
       option-source-name-label
       boon
       ::classes/set-boon-prop]
      ]
     [:div.w-100-p
      [:div.f-s-24.f-w-b
       "Description"]
      [textarea-field
       {:value (get boon :description)
        :on-change #(dispatch [::classes/set-boon-prop :description %])}]]]))

(defn invocation-builder []
  (let [invocation @(subscribe [::classes/invocation-builder-item])]
    [:div.p-20.main-text-color
     [:div.flex.w-100-p.flex-wrap
      [invocation-input-field
       "Name"
       :name
       invocation
       "m-b-20"]
      [plugin-datalist
       option-source-name-label
       invocation
       ::classes/set-invocation-prop]
      ]
     [:div.w-100-p
      [:div.f-s-24.f-w-b
       "Description"]
      [textarea-field
       {:value (get invocation :description)
        :on-change #(dispatch [::classes/set-invocation-prop :description %])}]]]))

(defn monster-builder []
  (let [{:keys [name
                key
                size
                type
                alignment
                armor-class
                armor-notes
                hit-points
                speed
                str
                dex
                con
                int
                wis
                cha
                saving-throws
                skills
                senses
                languages
                challenge
                traits
                actions
                legendary-actions] :as monster}
        @(subscribe [::monsters/builder-item])]
    [:div.p-20.main-text-color
     [:div.flex.w-100-p.flex-wrap
      [monster-input-field
       "Name"
       :name
       monster
       "m-b-20 flex-grow-1"]
      [plugin-datalist
       option-source-name-label
       monster
       ::monsters/set-monster-prop]
      ]
     [:div.flex.w-100-p.flex-wrap

      [:div.flex-grow-1.m-b-20.m-l-5
       [labeled-dropdown
        "Size"
        {:items (map
                 (fn [kw]
                   {:title (monsters/monster-sizes kw)
                    :value kw})
                 monsters/monster-size-order)
         :value (or size :tiny)
         :on-change #(dispatch [::monsters/set-monster-prop :size (keyword %)])}]]
      [:div.flex-grow-1.m-b-20.m-l-5
       [labeled-dropdown
        "Type"
        {:items (map
                 (fn [kw]
                   {:title (common/kw-to-name kw)
                    :value kw})
                 monsters/monster-types)
         :value (or type :aberration)
         :on-change #(dispatch [::monsters/set-monster-prop :type (keyword %)])}]]
      [:div.flex-grow-1.m-b-20.m-l-5
       [labeled-dropdown
        "Alignment"
        {:items (map
                 (fn [nm]
                   {:title nm
                    :value nm})
                 @(subscribe [::monsters/alignments]))
         :value (or alignment "neutral")
         :on-change #(dispatch [::monsters/set-monster-prop :alignment %])}]]]
     [:div.flex.w-100-p.flex-wrap
      [:div.flex-grow-1.m-b-20.m-l-5
       [labeled-dropdown
        "Armor Class"
        {:items (map
                  value-to-item
                  (range 5 25))
         :value (or armor-class 10)
         :on-change #(dispatch [::monsters/set-monster-prop :armor-class (js/parseInt %)])}]]
      [monster-input-field
       "Armor Notes"
       :armor-notes
       monster
       "m-b-5 m-l-5 flex-grow-1"]]
     [:div.flex.w-100-p.flex-wrap
       [:div.m-l-5.m-b-20.p-5.b-rad-5
        {:class (builder-field-cue #{:hit-points :die-count})}
        [labeled-dropdown
         "HP Die Count"
         {:items (cons
                  {:title "-"}
                  (map
                   value-to-item
                   (range 1 36)))
          :value (get hit-points :die-count)
          :on-change #(let [v (js/parseInt %)]
                        (dispatch [::monsters/set-monster-path-prop [:hit-points :die-count] (when (not (js/isNaN v)) v)])
                        (dispatch [:clear-builder-field-error :die-count])
                        (dispatch [:clear-builder-field-error :hit-points]))}]]
       [:div.m-l-5.m-b-20.p-5.b-rad-5
        {:class (builder-field-cue #{:hit-points :die})}
        [labeled-dropdown
         "HP Die"
         {:items (cons
                  {:title "-"}
                  (map
                   value-to-item
                   dice/dice-sides))
          :value (get hit-points :die)
          :on-change #(let [v (js/parseInt %)]
                        (dispatch [::monsters/set-monster-path-prop [:hit-points :die] (when (not (js/isNaN v)) v)])
                        (dispatch [:clear-builder-field-error :die])
                        (dispatch [:clear-builder-field-error :hit-points]))}]]
       [:div.m-l-5.m-b-20
        [input-builder-field
         [:span.f-w-b.m-b-5.f-s-16 "HP Modifier"]
         (get hit-points :modifier 0)
         #(let [v (js/parseInt %)]
            (dispatch [::monsters/set-monster-path-prop [:hit-points :modifier] (when (not (js/isNaN v)) v)]))
         {:class "input h-40"}]]
      [monster-input-field
       "Speed"
       :speed
       monster
       "m-l-5 m-b-5 flex-grow-1"]
      ]
     [:div
      [:div.f-s-24.f-w-b "Abilities"]
      [:div.flex.w-100-p.flex-wrap
       (doall
        (map
         (fn [{:keys [key name]}]
           ^{:key key}
           [:div.flex-grow-1.m-b-20.m-r-5
            (let [simple-kw (-> key clojure.core/name keyword)]
              [labeled-dropdown
               name
               {:items (map
                        value-to-item
                        (range 1 31))
                :value (or (simple-kw monster) 10)
                :on-change #(dispatch [::monsters/set-monster-prop simple-kw (js/parseInt %)])}])])
         opt/abilities))]]
     [:div
      [:div.f-s-24.f-w-b "Saving Throws"]
      [:div.flex.w-100-p.flex-wrap
       (doall
        (map
         (fn [{:keys [key name]}]
           ^{:key key}
           [:div.flex-grow-1.m-b-20.m-r-5
            (let [simple-kw (-> key clojure.core/name keyword)]
              [labeled-dropdown
               name
               {:items (cons
                        {:title "-"}
                        (map
                         value-to-item
                         (range 0 18)))
                :value (get saving-throws simple-kw)
                :on-change #(dispatch [::monsters/set-monster-path-prop [:saving-throws simple-kw] (let [parsed (js/parseInt %)] (when (not (js/isNaN parsed)) parsed))])}])])
         opt/abilities))]]
     [:div.m-b-20
      [:div.f-s-24.f-w-b. "Skills"]
      [:div.flex.w-100-p.flex-wrap
       (doall
        (map
         (fn [{:keys [key name]}]
           ^{:key key}
           [:div.m-b-20.m-r-5
            (let [simple-kw (-> key clojure.core/name keyword)]
              [labeled-dropdown
               name
               {:items (cons
                        {:title "-"}
                        (map
                         value-to-item
                         (range 1 21)))
                :value (get skills key 0)
                :on-change #(dispatch [::monsters/set-monster-path-prop [:skills key] (js/parseInt %)])}])])
         skills/skills))]
      [:div [option-damage-vulnerability monster ::monsters/toggle-monster-map-prop]]
      [:div [option-damage-resistance monster ::monsters/toggle-monster-map-prop]]
      [:div [option-damage-immunity monster ::monsters/toggle-monster-map-prop]]
      [:div [option-condition-immunity monster ::monsters/toggle-monster-map-prop]]
      [monster-input-field
       "Senses"
       :senses
       monster
       "m-l-5 m-b-5 flex-grow-1"]
      [:div.m-t-20 [option-languages monster ::monsters/toggle-monster-map-prop]]
      ]
     [:div.w-100-p.m-b-20
     [:div.f-s-24.f-w-b.w-20-p "Challenge Rating"
      [labeled-dropdown
       ""
       {:items (map
                 (fn [v]
                   {:title (if (< 0 v 1)
                             (clojure.core/str "1/" (/ 1 v))
                             v)
                    :value v})
                 @(subscribe [::monsters/challenge-ratings]))
        :value (or challenge 0)
        :on-change #(dispatch [::monsters/set-monster-prop :challenge (js/parseFloat %)])}]]]
     [:div.w-100-p
      [:div.f-s-24.f-w-b
       "Special Traits"]
      [textarea-field
       {:value (get monster :description)
        :on-change #(dispatch [::monsters/set-monster-prop :description %])}]]
     [:div.m-t-30
      [option-traits
       monster
       ::e5/add-monster-trait
       ::e5/edit-monster-trait-name
       ::e5/edit-monster-trait-type
       ::e5/edit-monster-trait-description
       ::e5/delete-monster-trait
       :title "Actions / Features"
       :button-title "Add Action / Feature"
       :types [{:title "Other"}
               {:title "Action"
                :value :action}
               {:title "Legendary Action"
                :value :legendary-action}]]]
     [:div.w-100-p.m-t-30
      [:div.f-s-20.f-w-b
       "Legendary Actions"]
      [textarea-field
       {:value (get-in monster [:legendary-actions :description])
        :on-change #(dispatch [::monsters/set-monster-path-prop [:legendary-actions :description] %])}]]
     ]))

(defn monster-selector [index {:keys [monster num]} on-key-change on-num-change]
  (let [monsters @(subscribe [::monsters/sorted-monsters])]
    [:div.flex.flex-wrap.m-l-5
     [labeled-dropdown
      "Monster Name"
      {:items (cons
               {:title "<select monster>"}
               (map
                obj-to-item
                monsters))
       :value monster
       :on-change on-key-change}]
     (when monster
       [:div.m-l-5.m-b-10
        [labeled-dropdown
         "Number"
         {:items (map
                  value-to-item
                  (range 0 21))
          :value (or num 0)
          :on-change on-num-change}]])]))

(defn character-selector [index {:keys [character]} on-change]
  (let [characters @(subscribe [::char/characters true])]
    [:div.flex.flex-wrap.m-l-5
     [:div.m-b-10
      [labeled-dropdown
       "Character Name"
       {:items (cons
                {:title "<select character>"}
                (map
                 (fn [{:keys [::char/race-name
                              ::char/classes] :as character-summary}]
                   {:title (str (character-display-name character-summary)
                                " - "
                                race-name
                                " "
                                (s/join
                                 "/"
                                 (map
                                  ::char/class-name
                                  classes)))
                    :value (:db/id character-summary)})
                 characters))
        :value character
        :on-change on-change}]]]))

(defn creature-selector [index {:keys [type creature] :as details}]
  [:div.flex.flex-wrap.align-items-c
   [:div.m-b-10
    [labeled-dropdown
     "Type"
     {:items [{:title "<select type>"}
              {:title "Monster"
               :value :monster}
              {:title "Non-Player Character"
               :value :character}]
      :value type
      :on-change #(dispatch [::encounters/set-encounter-path-prop [:creatures index :type] (keyword %)])}]]
   (case type
     :monster [monster-selector
               index
               creature
               #(dispatch [::encounters/set-encounter-path-prop
                          [:creatures index :creature :monster]
                           (keyword %)])
               #(dispatch [::encounters/set-encounter-path-prop
                           [:creatures index :creature :num]
                           (js/parseInt %)])]
     :character [character-selector
                 index
                 creature
                 #(dispatch [::encounters/set-encounter-path-prop
                             [:creatures index :creature :character]
                             (js/parseInt %)])]
     nil)
   [:button.form-button.m-l-5.m-b-10
    {:on-click #(dispatch [::encounters/delete-creature index])}
    "delete"]])

(defn party-selector [index party]
  (let [parties @(subscribe [::party/parties true])]
    [:div
     [labeled-dropdown
      (str "Party " (inc index))
      {:items (cons
               {:title "<select party>"}
               (map
                (fn [{:keys [:db/id ::party/name]}]
                  {:title name
                   :value id})
                parties))
       :value party
       :on-change #(dispatch [::combat/set-combat-path-prop [:parties index] (js/parseInt %)])}]]))

(defn encounter-selector [index encounter]
  (let [encounters @(subscribe [::encounters/encounters])]
    [:div
     [labeled-dropdown
      (str "Encounter " (inc index))
      {:items (cons
               {:title "<select encounter>"}
               (map
                obj-to-item
                encounters))
       :value encounter
       :on-change #(dispatch [::combat/set-combat-path-prop [:encounters index] (keyword %)])}]]))

(def char-name #(-> % :character ::char/character-name))

(defn on-character-change [index]
  #(dispatch [::combat/set-combat-path-prop
              [:characters index]
              (js/parseInt %)]))

(defn on-monster-change [index]
  #(dispatch [::combat/set-combat-path-prop
              [:monsters index :monster]
              (keyword %)]))

(defn on-monster-num-change [index]
  #(dispatch [::combat/set-combat-path-prop
              [:monsters index :num]
              (js/parseInt %)]))

;; dead — duplicates of common.cljc defs, never referenced from views
#_(def rounds-per-minute 10)
#_(def minutes-per-hour 60)
#_(def hours-per-day 24)

(def w-155 {:style {:width "155px"}})
(def w-160 {:style {:width "160px"}})

(defn duration-selector [title
                         key
                         max
                         monster
                         individual-index
                         duration
                         index]
  (let [value (get duration key 0)]
    [:div.m-r-5
     [:div.f-w-b.f-s-12 title]
     [dropdown
      {:items (map
               value-to-item
               (range 0 max))
       :value value
       :on-change #(dispatch [::combat/set-monster-condition-duration monster individual-index index key (js/parseInt %)])}]]))

(defn condition-selector [current-round
                          monster
                          individual-index
                          {:keys [type duration] :as condition}
                          index
                          deletable?
                          used-conditions]
  (let [current-round (or current-round 0)
        remaining-conditions (remove
                              (comp (disj used-conditions type) :key)
                              opt/conditions)]
    (when (seq remaining-conditions)
      [:div.flex.align-items-end
       [:div.m-r-5
        w-155
        [:div.f-w-b.f-s-12 "Condition"]
        [dropdown
         {:items (cons
                  {:title "<select condition>"}
                  (map
                   obj-to-item
                   remaining-conditions))
          :value type
          :on-change #(dispatch [::combat/set-monster-condition-type monster individual-index index (keyword %)])}]]
       (when type
         [:div.flex.flex-wrap
          [duration-selector "Hours" :hours 24 monster individual-index duration index]
          [duration-selector "Minutes" :minutes 60 monster individual-index duration index]
          [duration-selector "Rounds" :rounds 10 monster individual-index duration index]])
       (when deletable?
         [:button.form-button.f-s-14
          {:on-click #(dispatch [::combat/delete-monster-condition monster individual-index index])}
          [:i.fa.fa-trash]])])))

(defn combat-tracker []
  (let [expanded-rows (r/atom {})]
    (fn []
      (let [mobile? @(subscribe [:mobile?])
            {:keys [parties encounters characters monsters monster-data] :as tracker-item} @(subscribe [::combat/tracker-item])
            encounter-map @(subscribe [::encounters/encounter-map])
            encounter-creatures (mapcat (comp :creatures encounter-map) encounters)
            by-type (group-by :type encounter-creatures)
            encounter-monsters (by-type :monster)
            character-summary-map @(subscribe [::char/summary-map true])
            encounter-characters (remove
                                  #(-> % :character nil?)
                                  (map
                                   (fn [{:keys [creature]}]
                                     {:type :npc
                                      :character (-> creature :character character-summary-map)})
                                   (by-type :character)))
            monster-map @(subscribe [::monsters/monster-map])
            encounter-monsters (map
                                (fn [{:keys [creature]}]
                                  {:type :monster
                                   :num (:num creature)
                                   :monster (:monster creature)})
                                (by-type :monster))
            party-map @(subscribe [::party/party-map true])
            party-characters (into
                              (sorted-set-by #(compare (char-name %1) (char-name %2)))
                              (mapcat
                               (fn [party]
                                 (->> party
                                      party-map
                                      ::party/character-ids
                                      (map
                                       (fn [character]
                                         {:type :pc
                                          :character character}))))
                               parties))
            other-characters (into
                              (sorted-set-by #(compare (char-name %1) (char-name %2)))
                              (map
                               (fn [char-id]
                                 {:type :pc
                                  :character (character-summary-map char-id)})
                               characters))
            other-monsters (map
                            (fn [{:keys [monster num monster-data]}]
                              {:type :monster
                               :num num
                               :monster monster})
                            monsters)
            all-monsters (vals
                          (reduce
                           (fn [m {:keys [num monster] :as v}]
                             (update m
                                     monster
                                     (fn [x]
                                       (if x
                                         (assoc x :num (+ (get x :num 1) (or num 1)))
                                         (assoc v :monster (monster-map monster))))))
                           {}
                           (concat encounter-monsters
                                   other-monsters)))
            combatants (concat party-characters
                               other-characters
                               encounter-characters
                               all-monsters)]
        [:div.p-20.main-text-color
         [:div.flex.flex-wrap
          [:div.m-b-20.m-r-20
           [:div.f-s-24.f-w-b "Parties"]
           [:div
            (doall
             (map-indexed
              (fn [index party]
                ^{:key index}
                [:div.m-t-10.flex.align-items-end
                 [party-selector index party]
                 [:button.form-button.m-l-5.m-b-10
                  {:on-click #(dispatch [::combat/delete-party index])}
                  "delete"]])
              parties))]
           [:div.m-t-10.flex
            [party-selector (count parties) {}]]]
          [:div.m-b-20.m-r-20
           [:div.f-s-24.f-w-b "Encounters"]
           [:div
            (doall
             (map-indexed
              (fn [index encounter]
                ^{:key index}
                [:div.m-t-10.flex.align-items-end
                 [encounter-selector index encounter]
                 [:button.form-button.m-l-5.m-b-10
                  {:on-click #(dispatch [::combat/delete-encounter index])}
                  "delete"]])
              encounters))]
           [:div.m-t-10.flex
            [encounter-selector (count encounters) {}]]]
          [:div.m-b-20.m-r-20
           [:div.f-s-24.f-w-b "Characters"]
           [:div
            (doall
             (map-indexed
              (fn [index character]
                ^{:key index}
                [:div.m-t-10.flex.align-items-end
                 [character-selector
                  index
                  {:character character}
                  (on-character-change index)]
                 [:button.form-button.m-l-5.m-b-10
                  {:on-click #(dispatch [::combat/delete-character index])}
                  "delete"]])
              characters))]
           [:div.m-t-10.flex
            [character-selector
             (count characters)
             {}
             (on-character-change (count characters))]]]
          [:div.m-b-20.m-r-20
           [:div.f-s-24.f-w-b "Monsters"]
           [:div
            (doall
             (map-indexed
              (fn [index {:keys [monster num] :as cfg}]
                ^{:key index}
                [:div.m-t-10.flex.align-items-end
                 [monster-selector
                  index
                  cfg
                  (on-monster-change index)
                  (on-monster-num-change index)]
                 [:button.form-button.m-l-5.m-b-10
                  {:on-click #(dispatch [::combat/delete-monster index])}
                  "delete"]])
              monsters))]
           [:div.m-t-10.flex
            (let [monster-count (count monsters)]
              [monster-selector
               monster-count
               {}
               (on-monster-change monster-count)
               (on-monster-num-change monster-count)])]]]
         [:div.m-b-20
          [:div.flex.justify-cont-s-b
           [:div.f-s-24.f-w-b.m-b-10 "Initiative"]
           [:div.flex
            [:button.form-button.m-l-5.m-b-10
             {:on-click #(dispatch [::combat/next-initiative monster-map])}
             [:i.fa.fa-play]
             (when (not mobile?)
               [:span.m-l-5
                "next initiative"])]
            [:button.form-button.m-l-5.m-b-10
             {:on-click #(dispatch [::combat/set-combat-prop :ordered? true])}
             [:i.fa.fa-arrow-down]
             (when (not mobile?)
               [:span.m-l-5 "order"])]]]
          [:div.flex.flex-wrap
           [:div.m-b-20.m-r-20.t-a-c
            [:div.f-s-18.f-w-b "Current Initiative"]
            [:div.f-s-36.f-w-b
             (get tracker-item
                  :current-initiative
                  (->> tracker-item
                       :initiative
                       vals
                       (mapcat vals)
                       (apply max)))]]
           [:div.m-b-20.t-a-c
            [:div.f-s-18.f-w-b "Round"]
            [:div.f-s-36.f-w-b
             (get tracker-item :round 1)]]]
          [:div.f-s-18.f-w-b.m-b-10 "Combatants"]
          [:div.item-list
           (let [current-initiative (:current-initiative tracker-item)]
             (doall
              (map-indexed
               (fn [index {:keys [type character monster num] :as combatant}]
                 (let [path [:initiative type (or (:db/id character) (:key monster))]
                       initiative (get-in tracker-item path)]
                   ^{:key index}
                   [:div.item-list-item
                    [:div.flex.justify-cont-s-b.align-items-c
                     [:div.f-s-18.f-w-b.flex.flex-wrap.align-items-c.pointer.w-100-p
                      {:on-click #(swap! expanded-rows update path not)}
                      (when (and current-initiative
                               (= current-initiative initiative))
                        [:i.fa.fa-play.f-s-24.m-r-10])
                      [input-builder-field
                       [:span.f-w-b.f-s-12 "Initiative"]
                       initiative
                       #(dispatch [::combat/set-combat-path-prop path (js/parseInt %)])
                       {:class "input h-40 w-80 f-s-24 f-w-b m-r-10 m-t-10 m-b-10"
                        :type :number}]
                      [:div.m-r-10
                       [svg-icon
                        (case type
                          :pc "orc-head"
                          :npc "overlord-helm"
                          :monster "hydra")
                        48]]
                      (if character
                        [:div [character-summary-2 character true "bob" false]]
                        [:div.flex.align-items-c
                         [:div.p-t-20.p-b-20
                          [monster-summary
                           (:name monster)
                           (:size monster)
                           (:type monster)
                           (:subtypes monster)
                           (:alignment monster)]]
                         [:div.f-w-b.f-s-24 (str "(" (or num 0) ")")]
                         [:div.flex.flex-wrap
                          (doall
                           (map
                            (fn [i]
                              (let [{:keys [hit-points conditions]} (get-in monster-data [(:key monster) i])]
                                ^{:key i}
                                [:div.flex.flex-wrap.align-items-c.m-l-20.m-t-10.m-b-10
                                 [:div
                                  [:div.f-s-12 "hps"]
                                  [:div.m-r-5.f-s-24.f-w-b
                                   (or hit-points (get-in monster [:hit-points :mean]))]]
                                 [:div.flex.flex-wrap.align-items-c
                                  (doall
                                   (map
                                    (fn [{:keys [type]}]
                                      ^{:key type}
                                      [:div.m-l--5
                                       [svg-icon (get-in opt/conditions-map [type :icon]) 36]])
                                    conditions))]]))
                            (range num)))]])]
                     [:i.fa {:class (if (get @expanded-rows path)
                                           "fa-caret-up"
                                           "fa-caret-down")}]]
                    (when (get @expanded-rows path)
                      (if character
                        [character-display (:db/id character) false (if mobile? 1 2)]
                        [:div.p-t-10.p-b-10
                         [:div.w-100-p.m-b-20
                          [:div.flex.justify-cont-end
                           [:button.form-button.m-t-5
                            {:on-click #(dispatch [::combat/randomize-monster-hit-points combatant monster-map])}
                            "Randomize Hit Points"]]
                          [:div.flex.flex-wrap
                           (doall
                            (map
                             (fn [x]
                               ^{:key x}
                               [:div.m-r-5.p-20
                                [:div.f-w-b.f-s-18 (str "Monster " (inc x))]
                                [:div
                                 [:div.f-s-12.f-w-b "Hit Points"]
                                 [comps/input-field
                                  :input
                                  (get-in monster-data
                                          [(:key monster) x :hit-points]
                                          (get-in monster [:hit-points :mean]))
                                  #(dispatch [::combat/set-monster-hit-points combatant x (js/parseInt %)])
                                  {:type :number
                                   :class "input w-80"}]]
                                (let [current-round (dec (get tracker-item :round 1))
                                      conditions (get-in monster-data [(:key monster) x :conditions])
                                      used-conditions (into #{} (map :type) conditions)]
                                  [:div.m-t-10
                                   [:div.flex.w-100-p
                                    [:div.f-s-16.f-w-b
                                     w-160
                                     "Conditions"]
                                    (when (seq conditions)
                                      [:div.f-s-16.f-w-b.m-l-60 "Duration"])]
                                   (doall
                                    (map-indexed
                                     (fn [i condition]
                                       ^{:key i}
                                       [:div.m-b-10
                                        [condition-selector
                                         current-round
                                         (:key monster)
                                         x
                                         condition
                                         i
                                         true
                                         used-conditions]])
                                     conditions))
                                   [condition-selector
                                    current-round
                                    (:key monster)
                                    x
                                    {}
                                    (count conditions)
                                    false
                                    used-conditions]])])
                             (range (or num 1))))]]
                         [monster-component (monster-map (:key monster))]]))]))
               (if (:ordered? tracker-item)
                 (sort-by
                  (fn [{:keys [type character monster]}]
                    (let [key (or (:db/id character) (:key monster))]
                      (get-in tracker-item [:initiative type key])))
                  >
                  combatants)
                 combatants))))]]]))))

(defn encounter-builder []
  (let [{:keys [creatures] :as encounter} @(subscribe [::encounters/builder-item])]
    [:div.p-20.main-text-color
     [:div.flex.w-100-p.flex-wrap
      [encounter-input-field
       "Name"
       :name
       encounter
       "m-b-20"]
      [plugin-datalist
       option-source-name-label
       encounter
       ::encounters/set-encounter-prop]]
     [:div.m-t-20
      [:div.f-s-24.f-w-b "Creatures"]
      [:div
       (doall
        (map-indexed
         (fn [index details]
           ^{:key index}
           [:div.m-t-10 [creature-selector index details]])
         creatures))]
      [:div.m-t-10
       [creature-selector (count creatures) {}]]]]))

(defn spell-builder []
  (let [{:keys [:level :school] :as spell} @(subscribe [::spells/builder-item])]
    [:div.p-20.main-text-color
     [:div.flex.w-100-p.flex-wrap
      [spell-input-field
       "Name"
       :name
       spell
       "m-b-20"]
      [plugin-datalist
       option-source-name-label
       spell
       ::spells/set-spell-prop]
      ]

     [:div.flex.w-100-p.flex-wrap
      [:div.flex-grow-1.m-b-20
       [labeled-dropdown
        "Level"
        {:items (map
                 (fn [level] {:title (if (zero? level)
                                       "Cantrip"
                                       (str (common/ordinal level) "-level"))
                              :value level})
                 (range 10))
         :value level
         :on-change #(dispatch [::spells/set-spell-level %])}]]
      [:div.flex-grow-1.m-l-5
       [labeled-dropdown
        "School"
        {:items (map
                 (fn [school] {:title school
                               :value school})
                 (sort spells/schools))
         :value school
         :on-change #(dispatch [::spells/set-spell-prop :school %])}]]
      [
       :div.flex-grow-1.m-l-5
       [:div.m-t-20.m-r-20.m-b-10
        [comps/labeled-checkbox
         "Ritual?"
         (get spell :ritual)
         false
         #(dispatch [::spells/toggle-spell-prop :ritual])]]
       [:div.m-r-20.m-b-10
        [comps/labeled-checkbox
         "Requires Attack Roll?"
         (get spell :attack-roll?)
         false
         #(dispatch [::spells/toggle-spell-prop :attack-roll?])]]]]
     [:div.flex.w-100-p.flex-wrap
      [spell-input-field "Casting Time" :casting-time spell "m-b-20"]
      [spell-input-field "Range" :range spell "m-l-5 m-b-20"]]
     [:div [:h2.f-s-24.f-w-b.m-b-10 "Components"]]
     [:div.flex.w-100-p.flex-wrap
      [component-checkbox :verbal spell]
      [component-checkbox :somatic spell]
      [component-checkbox :material spell]]
     [:div.m-b-20
      [textarea-field
       {:value (get-in spell [:components :material-component])
        :on-change #(dispatch [::spells/set-material-component %])}]]
     [:div.m-b-20
      [spell-input-field "Duration" :duration spell "m-b-20"]]
     [:div.w-100-p
      [:div.f-s-24.f-w-b
       "Description"]
      [:div.m-b-20
       [textarea-field
        {:value (get spell :description)
         :on-change #(dispatch [::spells/set-spell-prop :description %])}]]]
     [:div.m-b-20
      [:div.f-w-b.m-b-10 "Add This Spell to Which Class Spell Lists?"]
      [:div.flex.flex-wrap.p-5.b-rad-5
       {:class (builder-field-cue :spell-lists)}
       (map
        (fn [{:keys [key name]}]
          ^{:key key}
          [:div.m-r-10.pointer.m-b-10
           {:on-click #(do (dispatch [::spells/toggle-spell-list key])
                           (dispatch [:clear-builder-field-error :spell-lists]))}
           [comps/checkbox (get-in spell [:spell-lists key])]
           [:span.m-l-5 name]])
        @(subscribe [::spells/spellcasting-classes]))]]]))

(defn validate-name [name]
  (if (nil? name)
    [] ;if nil, no error
    (if (common/starts-with-letter? (str name))
      nil
      "Name must start with a letter"
      )
    ))

;; dead — zero callers
#_(def invalid-styling
  {:color "red"
   :font-size "12px"
   :display "block"})

(defn valid-wel [name]
  (when-let [messages (validate-name name)]
  [:span {:required true
          :id "verify-name"
          :class "warntiptext"
          :data-tip messages
          :aria-live "polite"
          }
  messages]
  )
  )

(defn item-builder []
  (let [{:keys [::mi/name ::mi/type ::mi/rarity ::mi/description ::mi/attunement] :as item}
        @(subscribe [::mi/builder-item])
        item-types @(subscribe [::mi/item-types])
        item-rarities @(subscribe [::mi/rarities])]
    [:div.p-20.main-text-color
     [:div.flex.w-100-p.flex-wrap
      [:div.flex-grow-1.m-b-20.warntip
       [input-builder-field
          "Item Name" ;:name
          name
          #(dispatch [::mi/set-item-name %])
          {:class "input h-40"}]
       (when (seq name) 
         (valid-wel name))
       #_(when-let [messages (validate-name name)]
           (validation-messages messages))
       ]
      [:div.flex-grow-1.m-l-5
       (base-builder-field
        "Type"
        [:div.m-t-5
         [dropdown
          {:items (map
                   (fn [type-kw]
                     {:value type-kw
                      :title (common/kw-to-name type-kw)})
                   item-types)
           :value type
           :on-change #(dispatch [::mi/set-item-type %])}]])]
      [:div.flex-grow-1.m-l-5
       (base-builder-field
        "Rarity"
        [:div.m-t-5
         [dropdown
          {:items (map
                   (fn [rarity]
                     {:value rarity
                      :title (clojure.core/name rarity)})
                   item-rarities)
           :value rarity
           :on-change #(dispatch [::mi/set-item-rarity %])}]])]]
     [:div.m-b-40 (base-builder-field "Description" [textarea-field
                                                     {:value description
                                                      :on-change #(dispatch [::mi/set-item-description %])}])]
     (when (= :armor type)
       [:div.m-b-40 [base-armor-selector]])
     (when (= :weapon type)
       [:div.m-b-40 [base-weapon-selector]])
     [:div.m-b-40
      [attunement-selector attunement]]
     [item-bonuses item]]))

(defn import-file [e]
  (let [reader (js/FileReader.)
        file (.. e -target -files (item 0))
        filename (.-name file)
        nm (first (s/split filename #".orcbrew"))]
    (.addEventListener
     reader
     "load"
     (fn [e]
       (let [text (.. e -target -result)]
         (dispatch [::e5/import-plugin nm text]))))
    (.readAsText reader file)))

(defn capitalize-words
  [s]
  (->> (s/split (str s) #"\b")
       (map s/capitalize)
       s/join))

(defn my-content-type []
  (let [expanded? (r/atom false)]
    (fn [source-name plugin type-name type-key icon add-event edit-event delete-event plural]
      (let [items (sort (type-key plugin))]
        [:div.pointer.item-list-item
         [:div.flex.justify-cont-s-b.align-items-c.p-10
          {:on-click #(swap! expanded? not)}
          [:div.flex.align-items-c
           [:div.h-48.flex.align-items-c
            (if (vector? icon)
              (doall
               (map-indexed
                (fn [index ico]
                  ^{:key index}
                  [svg-icon ico (/ 48 (count icon)) @(subscribe [:theme])])
                icon))
              [svg-icon icon 48 @(subscribe [:theme])])]
           [:span.m-l-10.f-s-24 (let [num (count items)
                                      final-type-name (if plural
                                                        (if (not= 1 num) plural type-name)
                                                        (str type-name (when (not= 1 num) "s")))]
                                  (str num " " (capitalize-words final-type-name)))]]
          [:div.orange.pointer
           [:i.fa.m-r-5
            {:class (if @expanded? "fa-caret-up" "fa-caret-down")}]
           [:span.underline (if @expanded? "collapse" "expand")]]]
         (when @expanded?
           [:div.bg-lighter.p-10
            [:div.flex.justify-cont-end
             [:button.form-button.m-l-5
              {:on-click (make-event-handler add-event source-name)}
              (str "add " type-name)]]
            [:div
             (doall
              (map-indexed
               (fn [i [key {:keys [name disabled?] :as item}]]
                 ^{:key key}
                 [:div.p-t-10.p-b-10.f-w-b.flex.justify-cont-s-b.align-items-c
                  [:div.m-r-10.flex.align-items-c.flex-column
                   {:on-click (make-stop-prop-event-handler ::e5/toggle-plugin-item source-name type-key key)}
                   [:div.f-s-10 "enabled?"]
                   [comps/checkbox
                    (not (get-in plugin [type-key key :disabled?]))
                    false]]
                  [:span.flex-grow-1 name]
                  [:div
                   [:button.form-button.m-l-5
                    {:on-click (make-event-handler edit-event item)}
                    "edit"]
                   [:button.form-button.m-l-5
                    {:on-click (make-stop-prop-event-handler delete-event item)}
                    "delete"]]])
               items))]])]))))

(defn my-selections [name plugin]
  [my-content-type
   name
   plugin
   "selection"
   ::e5/selections
   "checklist"
   ::selections/new-selection
   ::selections/edit-selection
   ::selections/delete-selection])

(defn my-spells [name plugin]
  [my-content-type
   name
   plugin
   "spell"
   ::e5/spells
   "spell-book"
   ::spells/new-spell
   ::spells/edit-spell
   ::spells/delete-spell])

(defn my-monsters [name plugin]
  [my-content-type
   name
   plugin
   "monster"
   ::e5/monsters
   "hydra"
   ::monsters/new-monster
   ::monsters/edit-monster
   ::monsters/delete-monster])

(defn my-encounters [name plugin]
  [my-content-type
   name
   plugin
   "encounter"
   ::e5/encounters
   "hydra"
   ::encounters/new-encounter
   ::encounters/edit-encounter
   ::encounters/delete-encounter])

(defn my-backgrounds [name plugin]
  [my-content-type
   name
   plugin
   "background"
   ::e5/backgrounds
   "ages"
   ::bg/new-background
   ::bg/edit-background
   ::bg/delete-background])

(defn my-races [name plugin]
  [my-content-type
   name
   plugin
   "race"
   ::e5/races
   "woman-elf-face"
   ::races/new-race
   ::races/edit-race
   ::races/delete-race])

(defn my-subraces [name plugin]
  [my-content-type
   name
   plugin
   "subrace"
   ::e5/subraces
   ["woman-elf-face"
    "woman-elf-face"]
   ::races/new-subrace
   ::races/edit-subrace
   ::races/delete-subrace])


(defn my-classes [name plugin]
  [my-content-type
   name
   plugin
   "class"
   ::e5/classes
   "mounted-knight"
   ::classes/new-class
   ::classes/edit-class
   ::classes/delete-class
   "classes"])


(defn my-subclasses [name plugin]
  [my-content-type
   name
   plugin
   "subclass"
   ::e5/subclasses
   ["mounted-knight"
    "mounted-knight"]
   ::classes/new-subclass
   ::classes/edit-subclass
   ::classes/delete-subclass
   "subclasses"])

(defn my-invocations [name plugin]
  [my-content-type
   name
   plugin
   "eldritch invocation"
   ::e5/invocations
   "warlock-eye"
   ::classes/new-invocation
   ::classes/edit-invocation
   ::classes/delete-invocation])

(defn my-boons [name plugin]
  [my-content-type
   name
   plugin
   "pact boon"
   ::e5/boons
   "cursed-star"
   ::classes/new-boon
   ::classes/edit-boon
   ::classes/delete-boon])

(defn my-feats [name plugin]
  [my-content-type
   name
   plugin
   "feat"
   ::e5/feats
   "vitruvian-man"
   ::feats/new-feat
   ::feats/edit-feat
   ::feats/delete-feat])

(defn my-languages [name plugin]
  [my-content-type
   name
   plugin
   "language"
   ::e5/languages
   "vitruvian-man"
   ::langs/new-language
   ::langs/edit-language
   ::langs/delete-language])

(defn my-content-item []
  (let [expanded? (r/atom false)]
    (fn [name plugin]
      [:div.item-list-item
       [:div.p-20.pointer.flex.justify-cont-s-b.align-items-c.main-text-color
        {:on-click #(swap! expanded? not)}
        [:div.m-r-10.flex.align-items-c.flex-column
         {:on-click (make-stop-prop-event-handler ::e5/toggle-plugin name)}
         [:div.f-s-10 "enabled?"]
         [comps/checkbox
          (not (get plugin :disabled?))
          false]]
        [:span.f-s-24.flex-grow-1 name]
        [:div.orange
         [:i.fa.m-r-5
          {:class (if @expanded? "fa-caret-up" "fa-caret-down")}]
         [:span.pointer.underline (if @expanded? "collapse" "expand")]]]
       (when @expanded?
         [:div.bg-lighter.p-10
          [:div.flex.justify-cont-end.uppercase.align-items-c.m-b-10
           [:button.form-button.m-l-5
            {:on-click (make-event-handler ::e5/export-plugin-pretty-print name plugin)}
            "export"]
           [:button.form-button.m-l-5
            {:on-click (make-event-handler ::e5/delete-plugin name)}
            "delete"]]
          [:div.item-list
           [my-spells name plugin]
           [my-monsters name plugin]
           [my-encounters name plugin]
           [my-backgrounds name plugin]
           [my-races name plugin]
           [my-subraces name plugin]
           [my-classes name plugin]
           [my-subclasses name plugin]
           [my-invocations name plugin]
           [my-boons name plugin]
           [my-feats name plugin]
           [my-languages name plugin]
           [my-selections name plugin]]])])))

(defn my-content []
  [:div.main-text-color
   [:div.flex.justify-cont-end
    [:button.form-button.m-r-10.m-b-10
     {:on-click (make-event-handler ::char/show-delete-plugin-confirmation)}
     "Delete All"]
    [:button.form-button.m-r-10.m-b-10
     {:on-click (make-event-handler ::e5/export-all-plugins)}
     "Export All"]]
   [:div.flex.justify-cont-end
    (when @(subscribe [::char/delete-plugin-confirmation-shown?])
      [:div.p-20.flex.justify-cont-end
       [:div
        [:div.m-b-10 "Are you sure you want to delete ALL Option sources?"]
        [:div.flex
         [:button.form-button
          {:on-click (make-event-handler ::char/hide-delete-plugin-confirmation)}
          "cancel"]
         [:span.link-button
          {:on-click (make-event-handler ::char/delete-all-plugins)}
          "delete"]]]])]
   [:div.item-list
    (let [plugins (sort @(subscribe [::e5/plugins]))]
      (doall
       (map
        (fn [[name plugin]]
          ^{:key name}
          [my-content-item name plugin])
        plugins)))]])

(defn quarantine-source-repair
  "Repair UI for ONE source's set-aside ENTRIES. Lists each broken item with
   editable Name + Option-source fields; 'Fix & Restore' applies the edits, fills
   any remaining gaps with placeholders, re-keys keyword-trap names, and restores
   the now-valid entries into the live library (::e5/repair-quarantined-source) —
   entries that still can't validate stay set aside. Raw-export + discard hatches
   are always offered."
  [src-name plugin]
  (let [entries (vec (for [[ct items] plugin
                           :when (and (qualified-keyword? ct) (map? items))
                           [ik item] items]
                       {:ct ct :ik ik :item item}))
        edits (r/atom (into {} (mapcat (fn [{:keys [ct ik item]}]
                                         [[[ct ik :name] (or (:name item) "")]
                                          [[ct ik :option-pack] (or (:option-pack item) "")]])
                                       entries)))]
    (fn [src-name plugin]
      (let [current @edits]
        [:div.p-10.m-t-10.bg-lighter.b-rad-5
         [:div.f-w-b.f-s-18.orange src-name]
         (if (seq entries)
           [:div
            [:div.f-s-12.m-t-5.m-b-5
             "These entries couldn't load. Just hit Fix & Restore and each gets a "
             "valid placeholder name — or set your own name and option source first."]
            (doall
             (for [{:keys [ct ik]} entries
                   :let [nk [ct ik :name]
                         ok [ct ik :option-pack]
                         nm (get current nk "")]]
               ^{:key (str ct "/" ik)}
               [:div.m-t-5.m-b-5
                [:div.f-s-12.orange (str (name ct) " / " (name ik))]
                [:div.m-t-5.flex.align-items-c
                 [:span.f-s-12.m-r-5 {:style {:min-width "90px"}} "Name"]
                 [:input.input {:type "text" :value nm
                                :on-change #(swap! edits assoc nk (.. % -target -value))}]
                 (when-not (common/starts-with-letter? nm)
                   [:span.m-l-5.f-s-12 {:style {:color "#d9a520"}}
                    "will be auto-named on restore"])]
                [:div.m-t-5.flex.align-items-c
                 [:span.f-s-12.m-r-5 {:style {:min-width "90px"}} "Option source"]
                 [:input.input {:type "text" :value (get current ok "")
                                :on-change #(swap! edits assoc ok (.. % -target -value))}]]]))]
           [:div.f-s-12.m-t-5 "No entries to repair."])
         [:div.m-t-10
          (when (seq entries)
            [:button.form-button.m-r-5
             {:on-click #(dispatch [::e5/repair-quarantined-source src-name
                                    (into {} (map (fn [[[ct ik field] v]]
                                                    [[src-name ct ik field] v])
                                                  @edits))])}
             "Fix & Restore"])
          [:button.form-button.m-r-5
           {:on-click #(dispatch [::e5/export-quarantined-raw src-name])}
           "Export raw"]
          ;; Escape hatch for entries that can't be repaired and won't self-clear
          ;; (e.g. stale ones from an earlier bad import). Confirms — only copy.
          [:button.form-button
           {:on-click #(when (js/confirm
                             (str "Permanently discard the set-aside entries in \""
                                  src-name "\"? Export raw first if you might want them."))
                        (dispatch [::e5/discard-quarantined-source src-name]))}
           "Discard"]]]))))

(defn quarantine-panel
  "Surfaces the ENTRIES the loader set aside (grouped by their source). The rest of
   each source loaded normally; these are preserved, not discarded, so you can fix
   them back into the library or export the raw data."
  []
  (let [quarantined @(subscribe [::e5/quarantined-plugins])
        n-entries (reduce + 0 (for [[_ plugin] quarantined
                                    [ct items] plugin
                                    :when (and (qualified-keyword? ct) (map? items))]
                                (count items)))]
    (when (seq quarantined)
      [:div.p-20.main-text-color.m-b-10.m-l-10.m-r-10.b-rad-5
       {:style {:border "2px solid #d94b20"}}
       [:div.f-w-b.f-s-24.m-b-5
        [:i.fa.fa-exclamation-triangle.m-r-5]
        (str n-entries " entr" (if (= 1 n-entries) "y" "ies")
             " couldn't load — needs attention")]
       [:div.f-s-12
        "The rest of each source loaded fine. Fix these back in, or export/discard them."]
       (doall
        (for [[src-name plugin] quarantined]
          ^{:key src-name}
          [quarantine-source-repair src-name plugin]))])))

(defn my-content-page []
  [content-page
   "My Content"
   []
   [:div
    [quarantine-panel]
    [:div.p-20.bg-lighter.main-text-color.m-b-10.m-l-10.m-r-10.b-rad-5
     [:div.f-w-b.f-s-24.m-b-5 "Import Option Source"]
     [:input {:type "file"
              :accept ".orcbrew"
              :on-change import-file}]]
    [my-content]]])

(defn my-account-page []
    (r/with-let [editing? (r/atom false)
                 new-email (r/atom "")
                 confirm-email (r/atom "")]
      (let [current-email @(subscribe [:email])
            pending-email @(subscribe [:pending-email])
            sent? @(subscribe [:email-change-sent?])
            error @(subscribe [:email-change-error])
            ;; Client-side validation: format check + confirm match
            bad-format? (and (seq @new-email)
                            (registration/bad-email? @new-email))
            emails-dont-match? (and (seq @confirm-email)
                                    (not= @new-email @confirm-email))
            can-submit? (and (seq @new-email)
                             (not bad-format?)
                             (= @new-email @confirm-email))]
        [content-page
         "My Account"
         [{:title "Delete Account"
           :icon "trash"
           :on-click #(dispatch
                      [:show-confirmation
                       {:confirm-button-text "DELETE ACCOUNT"
                        :question "Are you sure you want to delete your account, characters, and associated data?"
                        :event [:delete-account]}])}]
         [:div.f-s-24.p-10.white
          [:div.p-5
           [:span.f-w-b "Username: "]
           [:span @(subscribe [:username])]]
          [:div.p-5
           [:span.f-w-b "Email: "]
           (cond
             sent?
             [:div
              [:span current-email]
              [:div.m-t-5.f-s-14 "A verification email has been sent to " [:strong pending-email] ". Click the link in that email to confirm the change."]
              [:button.link-button.m-t-5.f-s-14
               {:on-click #(do (reset! editing? true)
                               (reset! new-email "")
                               (reset! confirm-email "")
                               (dispatch [:change-email-clear]))}
               "Change again"]]

             @editing?
             [:div.m-t-5
              [:input.input
               {:type :email
                :value @new-email
                :placeholder "New email address"
                :on-change #(reset! new-email (event-value %))}]
              (when bad-format?
                [:div.m-t-5.red "Not a valid email format"])
              ;; Confirm field to prevent typo-induced lockout
              [:input.input.m-t-5
               {:type :email
                :value @confirm-email
                :placeholder "Confirm new email address"
                :on-change #(reset! confirm-email (event-value %))}]
              (when emails-dont-match?
                [:div.m-t-5.red "Email addresses don't match"])
              [:div.m-t-5
               [:button.form-button
                {:disabled (not can-submit?)
                 :on-click #(when can-submit?
                              (dispatch [:change-email @new-email]))}
                "Save"]
               [:button.link-button.m-l-10
                {:on-click #(do (reset! editing? false)
                                (reset! new-email "")
                                (reset! confirm-email "")
                                (dispatch [:change-email-clear]))}
                "Cancel"]]
              (when error
                [:div.m-t-5.red error])]

             :else
             [:<>
              [:span current-email]
              [:button.link-button.m-l-10
               {:on-click #(do (reset! editing? true)
                               (reset! new-email "")
                               (reset! confirm-email "")
                               (dispatch [:change-email-clear]))}
               "Change"]
              (when pending-email
                [:div.m-t-5.f-s-14
                 "Pending: " pending-email " — check your email to verify the change. "
                 ;; Resend uses the same change-email flow; server enforces 3-zone rate limit
                 ;; (0–1 min blocked, 1–5 min free resend, 5+ min open)
                 [:button.link-button.f-s-14
                  {:on-click #(dispatch [:change-email pending-email])}
                  "Resend"]
                 (when error
                   [:span.m-l-5.red.f-s-14 error])])])]
          ;; ─── Email Updates Toggle ─────────────────────────────────
          [:div.p-5
           [:span.f-w-b "Email Updates: "]
           (let [send-updates? @(subscribe [:send-updates?])]
             [:span
              [:i.fa.fa-check.f-s-14.pointer.m-r-5
               {:class (if send-updates? "orange" "white")
                :style {:border-color "#f0a100"
                        :border-style :solid
                        :border-width "1px"
                        :border-bottom-width "3px"}
                :on-click #(dispatch [:toggle-send-updates (not send-updates?)])}]
              (if send-updates?
                (str "Receiving updates from " branding/app-name)
                "Not receiving updates")])]]])))


(defn newb-character-builder-page []
  [content-page
   "Character Builder for Newbs"
   []
   (let [{:keys [key question answers] :as q} @(subscribe [::char/current-question])
         newb-char-data @(subscribe [::char/newb-char-data])
         current-answer (get-in newb-char-data [:answers key])
         has-history? @(subscribe [::char/has-question-history?])]
     [:div.p-20.main-text-color
      (if (some? q)
        [:div
         [:div
          [:div.f-w-b.f-s-24
           question]]
         [:div.m-t-5
          (doall
           (map
            (fn [{:keys [answer tag] :as a}]
              ^{:key tag}
              [:div.p-5.f-s-16.f-w-b
               [comps/labeled-checkbox
                answer
                (= tag (get-in newb-char-data [:answers key]))
                false
                #(dispatch [::char/add-answer q a])]])
            answers))]]
        [:div.p-20.main-text-color
         [:div.f-s-18.f-w-b "Your character is complete, click the button below to view it"]
         [:button.form-button
          {:on-click #(dispatch [::char/open-character (:char newb-char-data)])}
          "View Character"]])
      [:div.m-t-20
       [:button.link-button
        {:class (when (not has-history?) "disabled")
         :on-click #(when has-history? (dispatch [::char/previous-question]))}
        "Back"]
       [:button.form-button
        {:on-click #(when current-answer (dispatch [::char/next-question]))
         :class (when (nil? current-answer) "disabled")}
        "Next"]]])
   :hide-header-message? true])

;; events are set and passed by the individual pages defined below this
(defn builder-page [item-title reset-event save-event builder & [title]]
  ;; Draft event is derived from save-event (events/draft-event-for) and registered
  ;; from events/builder-drafts, so the Export-draft hatch needs no per-builder wiring.
  (let [export-draft-event (events/draft-event-for save-event)]
    [content-page
     (or title (str item-title " Builder"))
     [{:title (str "New " item-title)
       :icon "plus"
       :on-click #(dispatch [reset-event])}
      {:title "Save to Browser Storage"
       :icon "save"
       :on-click #(dispatch [save-event])}
      ;; Escape hatch: export the in-progress builder-item as a draft .orcbrew with
      ;; no validation, so imperfect WIP is never trapped.
      {:title "Export draft"
       :icon "download"
       :on-click #(dispatch [export-draft-event])}]
     [builder]]))

(defn combat-tracker-page []
  [content-page
   "Combat Tracker"
   [{:title "Reset"
     :icon "undo"
     :on-click #(dispatch [::combat/reset-combat])}]
   [combat-tracker]])

#_(defn item-builder-page []
  (builder-page "Item" ::mi/reset-item ::mi/save-item item-builder))

#_(defn item-page [{:keys [key] :as arg}]
  (let [item-key (if (re-matches #"\d+" key)
                   (js/parseInt key)
                   (keyword key))
        item @(subscribe [::mi/item item-key])
        username @(subscribe [:username])
        owner? (= username (::mi/owner item))]
    [content-page
     "Item Page"
     (remove
      nil?
      [(if owner?
         {:title "Delete"
          :icon "trash"
          :on-click (delete-item-handler item-key)})
       (if owner?
         {:title "Edit"
          :icon "pencil"
          :on-click (make-event-handler [::mi/edit-custom-item item])})])
     [:div.p-10.main-text-color
      [item-component item]]]))

(defn get-owner? [item-key] ;item-key could be other things that need ownership with a refactor
  (let [username @(subscribe [:username])
        item @(subscribe [::mi/custom-item item-key])
        builder-item @(subscribe [::mi/builder-item item-key])]
    #_(println "username" username "item" item "item-key" item-key "ownitem" (::mi/owner item) "ownbuild" (::mi/owner builder-item) "time" common/ptime)
    (= username (or (::mi/owner item) (::mi/owner builder-item)))
    ))

(defn deletion-modal-with [builder-page item-key]
  (let [show? @(subscribe [::mi/delete-confirmation-shown? item-key])
        ]
    [:span
     [:div {:class (if show? "modal-container" "modal-container hidden")}
      [:div.modal
       [:div.modal_content
        [:div.m-b-10 "Are you sure you want to delete this item?"]
        [:div
         [:button.form-button
          {:on-click (make-event-handler ::mi/hide-delete-confirmation item-key)}
          "cancel"]
         [:span.link-button
          {:on-click (delete-item-handler item-key)}
          "delete"]
         ]]]]
     [builder-page]]
   )
  )

(defn item-builder-buttons [item-key item]
  (let [owner? (get-owner? item-key)
        base-buttons [{:title "New Item"
                       :icon "plus"
                       :on-click #(dispatch [::mi/reset-item])}
                      {:title "Save to Browser Storage"
                       :icon "save"
                       :on-click #(dispatch [::mi/save-item])}]
        ]
    (if (and item-key owner?) ;Show if we have an item key and the user owns it
      (conj base-buttons {:title "Delete"
                          :icon "trash"
                          :on-click (make-event-handler ::mi/show-delete-confirmation item-key)})
      base-buttons)
    ))

(defn item-builder-page []
  (let [item (subscribe [::mi/builder-item])
        item-key (:db/id @item)
        buttons (item-builder-buttons item-key item)
        ]
    [content-page
     "Item Builder"
     buttons
     [deletion-modal-with
      item-builder
      item-key]
     ]))

(defn spell-builder-page []
  (builder-page "Spell" ::spells/reset-spell ::spells/save-spell spell-builder))

(defn monster-builder-page []
  (builder-page "Monster" ::monsters/reset-monster ::monsters/save-monster monster-builder))

(defn encounter-builder-page []
  (builder-page "Encounter" ::encounters/reset-encounter ::encounters/save-encounter encounter-builder))

(defn language-builder-page []
  (builder-page "Language" ::langs/reset-language ::langs/save-language language-builder))

(defn invocation-builder-page []
  (builder-page "Eldritch Invocation" ::classes/reset-invocation ::classes/save-invocation invocation-builder))

(defn boon-builder-page []
  (builder-page "Pact Boon" ::classes/reset-boon ::classes/save-boon boon-builder))

(defn selection-builder-page []
  (builder-page "Selection" ::selections/reset-selection ::selections/save-selection selection-builder [title-with-help "Selection Builder" selection-help]))

(defn background-builder-page []
  (builder-page "Background" ::bg/reset-background ::bg/save-background background-builder))

(defn race-builder-page []
  (builder-page "Race" ::races/reset-race ::races/save-race race-builder))

(defn subrace-builder-page []
  (builder-page "Subrace" ::races/reset-subrace ::races/save-subrace subrace-builder))

(defn subclass-builder-page []
  (builder-page "Subclass" ::classes/reset-subclass ::classes/save-subclass subclass-builder))

(defn class-builder-page []
  (builder-page "Class" ::classes/reset-class ::classes/save-class class-builder))

(defn feat-builder-page []
  (builder-page "Feat" ::feats/reset-feat ::feats/save-feat feat-builder))

(defn expanded-character-list-item [id owner username char-page-route]
  (let [built-char @(subscribe [::char/built-character id])
        character @(subscribe [::char/character id])
        plugin-data {:spells-map @(subscribe [::spells/spells-map])
                     :plugin-spells-map @(subscribe [::spells/plugin-spells-map])
                     :language-map @(subscribe [::langs/language-map])
                     :all-weapons-map @(subscribe [::mi/all-weapons-map])
                     :all-magic-items-map @(subscribe [::mi/all-magic-items-map])
                     :current-armor-class @(subscribe [::char/current-armor-class id])}
        folders @(subscribe [::folder/folders])
        char-folder-map @(subscribe [::folder/character-folder-map])
        current-folder-id (get char-folder-map id)]
    [:div
     {:style character-display-style}
     [:div.flex.justify-cont-end.uppercase.align-items-c
      [integrations/share-link-www id]
      (when (= username owner)
        [:button.form-button
         {:on-click (make-event-handler :edit-character character)}
         "edit"])
      (when (= username owner)
        [:button.form-button.m-l-5
         {:on-click (make-event-handler ::char/save-character id)}
         "save"])
      [:button.form-button.m-l-5
       {:on-click (make-event-handler :route char-page-route)}
       "view"]
      (when (or (not branding/restrict-print-to-owner?) (= username owner))
        [:button.form-button.m-l-5
         {:on-click (export-pdf
                     built-char
                     id
                     plugin-data
                     {:print-character-sheet? true
                      :print-spell-cards? true
                      :print-prepared-spells? false
                      :print-character-sheet-style? 1
                      :print-spell-card-dc-mod? true})}
         "print"])
      (when (and (= username owner) (seq folders))
        [:select.form-button.m-l-5.builder-option-dropdown
         {:style {:width "auto" :align-self "stretch" :box-sizing "border-box"}
          :value (or current-folder-id "")
          :on-change (fn [e]
                       (let [val (.-value (.-target e))]
                         (if (= val "")
                           (when current-folder-id
                             (dispatch [::folder/remove-character current-folder-id id]))
                           (dispatch [::folder/add-character (js/parseInt val) id]))))}
         [:option.builder-dropdown-item {:value ""} "No folder"]
         (doall
          (map (fn [f]
                 ^{:key (:db/id f)}
                 [:option.builder-dropdown-item {:value (:db/id f)} (::folder/name f)])
               (sort-by ::folder/name folders)))])
      (when (= username owner)
        [:button.form-button.m-l-5
         {:on-click (make-event-handler ::char/show-delete-confirmation id)}
         "delete"])]
     (when @(subscribe [::char/delete-confirmation-shown? id])
       [:div.p-20.flex.justify-cont-end
        [:div
         [:div.m-b-10 "Are you sure you want to delete this character?"]
         [:div.flex
          [:button.form-button
           {:on-click (make-event-handler ::char/hide-delete-confirmation id)}
           "cancel"]
          [:span.link-button
           {:on-click (make-event-handler :delete-character id)}
           "delete"]]]])
     [character-display id false (if (= :mobile @(subscribe [:device-type])) 1 2)]]))

(defn character-list-item [expanded-characters
                           selected-ids
                           id
                           owner
                           username
                           summary]
  (let [expanded? (get expanded-characters id)
        char-page-path (routes/path-for routes/dnd-e5-char-page-route :id id)
        char-page-route (routes/match-route char-page-path)]
    [:div.main-text-color.item-list-item
     [:div
      [:div.flex.justify-cont-s-b.align-items-c.pointer
       {:on-click (make-event-handler :toggle-character-expanded id)}
       [:div.m-l-10.flex.align-items-c
        [:div.p-5
         {:on-click (fn [e]
                      (dispatch [::char/toggle-selected id])
                      (.stopPropagation e))}
         (comps/checkbox (get selected-ids id) false)]
        [:div.f-s-24.f-w-600
         [:div.list-character-summary
          [character-summary-2 summary true owner false]]]]
       [:div.orange.pointer.m-r-10
        (when (not= @(subscribe [:device-type]) :mobile) [:span.underline (if expanded?
                                                            "collapse"
                                                            "open")])
        [:i.fa.m-l-5
         {:class (if expanded? "fa-caret-up" "fa-caret-down")}]]]
      (when expanded?
        [expanded-character-list-item id owner username char-page-route])]]))

(defn folder-item [f expanded-characters selected-ids username filtered-char-ids]
  (let [edit-name      (r/atom (::folder/name f))
        confirm-delete? (r/atom false)]
    (fn [f expanded-characters selected-ids username filtered-char-ids]
      (let [folder-id (:db/id f)
            folder-name (::folder/name f)
            expanded? @(subscribe [::folder/expanded])
            folder-expanded? (get expanded? folder-id)
            renaming? @(subscribe [::folder/renaming])
            folder-renaming? (get renaming? folder-id)
            chars (::folder/character-ids f)
            visible-chars (filter #(filtered-char-ids (:db/id %)) chars)
            save-fn (fn []
                      (dispatch [::folder/rename-folder folder-id @edit-name])
                      (dispatch [::folder/toggle-renaming folder-id]))]
    [:div.main-text-color.item-list-item.m-b-5
     ^{:key folder-id}
     [:div
      ;; Entire row is clickable to expand/collapse folder
      [:div.flex.justify-cont-s-b.align-items-c.pointer
       {:on-click (when (not folder-renaming?)
                    #(dispatch [::folder/toggle-expanded folder-id (mapv :db/id chars)]))}
       [:div.flex.align-items-c.m-l-10
        (when (not folder-renaming?)
          [:i.fa.m-r-10.orange
           {:class (if folder-expanded? "fa-folder-open" "fa-folder")}])
        (if folder-renaming?
          [:div.flex.align-items-c
           [:input.input
            {:auto-focus true
             :value @edit-name
             :style {:width "160px"}
             :on-change #(reset! edit-name (.-value (.-target %)))
             :on-key-down (fn [e]
                            (when (= "Enter" (.-key e))
                              (save-fn)))}]
           [:button.form-button.m-l-5
            {:on-click (fn [e]
                         (.stopPropagation e)
                         (save-fn))}
            "save"]]
          [:span.f-s-18.f-w-b
           folder-name])
        (when (not folder-renaming?)
          [:span.m-l-10.f-s-12.opacity-5
           (str "(" (count visible-chars) ")")])]
       (when (not folder-renaming?)
         [:div.flex.align-items-c.m-r-10
          [:span.link-button.m-r-10.f-s-12
           {:on-click (fn [e]
                        (.stopPropagation e)
                        (reset! edit-name folder-name)
                        (dispatch [::folder/toggle-renaming folder-id]))}
           "rename"]
          [:span.link-button.f-s-12
           {:on-click (fn [e]
                        (.stopPropagation e)
                        (reset! confirm-delete? true))}
           "delete"]
          [:i.fa.m-l-10.orange
           {:class (if folder-expanded? "fa-caret-up" "fa-caret-down")}]])]
      (when @confirm-delete?
        [:div.p-20.flex.justify-cont-end
         [:div
          [:div.m-b-10 "Are you sure you want to delete this folder? Characters will not be deleted."]
          [:div.flex
           [:button.form-button
            {:on-click (fn [e]
                         (.stopPropagation e)
                         (reset! confirm-delete? false))}
            "cancel"]
           [:span.link-button.m-l-10
            {:on-click (fn [e]
                         (.stopPropagation e)
                         (dispatch [::folder/delete-folder folder-id]))}
            "delete"]]]])
      (when folder-expanded?
        [:div.item-list
         (doall
          (map
           (fn [{:keys [:db/id ::se/owner] :as summary}]
             ^{:key (:db/id summary)}
             [character-list-item
              expanded-characters
              selected-ids
              (:db/id summary)
              owner
              username
              summary])
           (sort-by ::char/character-name visible-chars)))])]]))))

(defn orcacle-page []
  [content-page
   "Orcacle"
   []
   [orcacle]])

(defn character-filter-bar []
  (let [classes-open? (r/atom false)
        levels-open? (r/atom false)]
    (fn []
      (let [name-filter      @(subscribe [::char/char-name-filter])
            level-filters    @(subscribe [::char/char-level-filters])
            class-filters    @(subscribe [::char/char-class-filters])
            has-portrait?    @(subscribe [::char/char-has-portrait?])
            has-faction-pic? @(subscribe [::char/char-has-faction-pic?])
            avail-classes    @(subscribe [::char/char-classes-available])
            avail-levels     @(subscribe [::char/char-levels-available])
            any-filter?      (or (not (s/blank? name-filter))
                                 (seq level-filters)
                                 (seq class-filters)
                                 has-portrait?
                                 has-faction-pic?)]
        [:div.main-text-color.m-b-10.char-filter-bar
         (when @classes-open?
           [:div.posn-fixed
            {:style    {:top 0 :left 0 :right 0 :bottom 0 :z-index 100}
             :on-click (fn [e] (.stopPropagation e) (reset! classes-open? false))}])
         (when @levels-open?
           [:div.posn-fixed
            {:style    {:top 0 :left 0 :right 0 :bottom 0 :z-index 100}
             :on-click (fn [e] (.stopPropagation e) (reset! levels-open? false))}])
         [:div.flex.align-items-c.flex-wrap.p-5
          ;; Name search
          [:div.posn-rel.m-r-5.m-b-5
           [:input.input
            {:placeholder "Search by name..."
             :style       {:width "200px"}
             :value       name-filter
             :on-change   #(dispatch [::char/set-char-name-filter (.. % -target -value)])}]
           (when (not (s/blank? name-filter))
             [:i.fa.fa-times.posn-abs.pointer.orange.f-s-14
              {:style    {:right "8px" :top "8px"}
               :on-click #(dispatch [::char/set-char-name-filter ""])}])]

          ;; Classes multi-select dropdown
          [:div.posn-rel.m-r-5.m-b-5
           {:style {:z-index (if @classes-open? 200 1)}}
           [:button.form-button
            {:on-click (fn [e] (.stopPropagation e) (swap! classes-open? not))}
            (str "Classes" (when (seq class-filters) (str " (" (count class-filters) ")")))
            [:i.fa.m-l-5 {:class (if @classes-open? "fa-caret-up" "fa-caret-down")}]]
           (when @classes-open?
             [:div.filter-dropdown.main-text-color
              {:style {:min-width "160px"}}
              (if (seq avail-classes)
                (doall
                 (map (fn [cls]
                        ^{:key cls}
                        [:div.filter-dropdown-item
                         [comps/labeled-checkbox cls (contains? class-filters cls) false
                          (fn [] (dispatch [::char/toggle-char-class-filter cls]))]])
                      avail-classes))
                [:span.opacity-5.f-s-12.p-5 "No classes yet"])])]

          ;; Levels multi-select dropdown
          [:div.posn-rel.m-r-5.m-b-5
           {:style {:z-index (if @levels-open? 200 1)}}
           [:button.form-button
            {:on-click (fn [e] (.stopPropagation e) (swap! levels-open? not))}
            (str "Levels" (when (seq level-filters) (str " (" (count level-filters) ")")))
            [:i.fa.m-l-5 {:class (if @levels-open? "fa-caret-up" "fa-caret-down")}]]
           (when @levels-open?
             [:div.filter-dropdown.main-text-color
              {:style {:min-width "120px"}}
              (if (seq avail-levels)
                (doall
                 (map (fn [lvl]
                        ^{:key lvl}
                        [:div.filter-dropdown-item
                         [comps/labeled-checkbox (str "Level " lvl) (contains? level-filters lvl) false
                          (fn [] (dispatch [::char/toggle-char-level-filter lvl]))]])
                      avail-levels))
                [:span.opacity-5.f-s-12.p-5 "No levels yet"])])]

          ;; Portrait toggle (nil=all, true=with portrait, false=without portrait)
          [:button.form-button.m-r-5.m-b-5
           {:on-click #(dispatch [::char/toggle-char-has-portrait])}
           [:i.fa.fa-user.m-r-5]
           (cond (true? has-portrait?)  "Portrait: Has"
                 (false? has-portrait?) "Portrait: None"
                 :else                  "Portrait: All")]

          ;; Faction Pic toggle (nil=all, true=with faction pic, false=without faction pic)
          [:button.form-button.m-r-5.m-b-5
           {:on-click #(dispatch [::char/toggle-char-has-faction-pic])}
           [:i.fa.fa-flag.m-r-5]
           (cond (true? has-faction-pic?)  "Faction Pic: Has"
                 (false? has-faction-pic?) "Faction Pic: None"
                 :else                     "Faction Pic: All")]

          ;; Clear button — only shown when any filter is active
          (when any-filter?
            [:button.form-button.m-b-5
             {:on-click #(dispatch [::char/clear-char-filters])}
             [:i.fa.fa-times.m-r-5]
             "Clear"])]]))))

(defn character-list []
  (let [characters @(subscribe [::char/filtered-characters])
        folders @(subscribe [::folder/folders])
        char-folder-map @(subscribe [::folder/character-folder-map])
        expanded-characters @(subscribe [:expanded-characters])
        username @(subscribe [:username])
        selected-ids @(subscribe [::char/selected])
        has-selected? @(subscribe [::char/has-selected?])
        user-tier @(subscribe [:user-tier])]
    (integrations/track-character-list! (count characters) user-tier)
    #_(prn (str (count characters) " characters - " user-tier))
[content-page
     "Characters"
     [#_(if (>= (count characters) 5) {:title "Free accounts are limited to 5 characters"
                                       :icon "plus"
                                       :class-name "cursor-disabled"}
            {:title "New"
             :icon "plus"
             :on-click #(dispatch [:new-character])})
      {:title "New"
       :icon "plus"
       :on-click #(dispatch [:new-character])}
      {:title "Make Party"
       :icon "users"
       :class (when (not has-selected?) "opacity-5 cursor-disabled")
       :on-click (when has-selected? (make-event-handler ::party/make-party selected-ids))}
      {:title "New Folder"
       :icon "folder"
       :on-click #(dispatch [::folder/create-folder])}]
     [:div.p-5
      [character-filter-bar]
      [:div
       (let [filtered-char-ids (into #{} (map :db/id) characters)
             grouped-characters (group-by ::se/owner characters)
             user-characters (find grouped-characters username)
             other-characters (sort-by key (dissoc grouped-characters username))
             user-chars-list (second user-characters)
             unfiled-user-chars (remove #(get char-folder-map (:db/id %)) user-chars-list)
             user-folders (sort-by ::folder/name folders)]
         [:div
          (when (and username (seq user-folders))
            [:div.m-b-20
             (doall
              (map
               (fn [f]
                 ^{:key (:db/id f)}
                 [folder-item f expanded-characters selected-ids username filtered-char-ids])
               user-folders))])
          (when (and username (seq unfiled-user-chars))
            [:div.m-b-40
             (when (seq user-folders)
               [:div.m-b-10.main-text-color.f-s-14.opacity-5 "Unfiled"])
             [:div.item-list
              (doall
               (map
                (fn [{:keys [:db/id ::se/owner] :as summary}]
                  ^{:key (:db/id summary)}
                  [character-list-item
                   expanded-characters
                   selected-ids
                   (:db/id summary)
                   owner
                   username
                   summary])
                (sort-by ::char/character-name unfiled-user-chars)))]])
          (doall
           (map
            (fn [[owner owner-characters]]
              ^{:key owner}
              [:div.m-b-40
               [:div.m-b-10.main-text-color.f-w-b.f-s-16
                [other-user-component owner "f-s-24 m-l-10 m-r-20 i" true]]
               [:div.item-list
                (doall
                 (map
                  (fn [{:keys [:db/id ::se/owner] :as summary}]
                    ^{:key (:db/id summary)}
                    [character-list-item
                     expanded-characters
                     selected-ids
                     (:db/id summary)
                     owner
                     username
                     summary])
                  (sort-by ::char/character-name owner-characters)))]])
            (sort-by key other-characters)))])]]]))

(def party-name-editor-style
  {:width "200px"
   :height "42px"})

(defn set-editing-party-fn [editing-parties id]
  #(swap! editing-parties assoc id (event-value %)))

(def set-editing-party-handler (memoize set-editing-party-fn))

(defn parties []
  (let [editing-parties (r/atom {})
        expanded-characters (r/atom {})]
    (fn []
      (let [parties (sort-by ::party/name @(subscribe [::party/parties]))
            device-type @(subscribe [:device-type])
            username @(subscribe [:username])]
        [content-page
         "Parties"
         [{:title "Create Party"
           :icon "users"
           :on-click (make-event-handler ::party/make-empty-party)}]
         [:div.p-5
           [:div
           (doall
            (map
             (fn [{:keys [:db/id ::party/name] characters ::party/character-ids}]
               (let [editing? (get @editing-parties id)
                     character-ids (into #{} (map :db/id) characters)]
                 ^{:key id}
                 [:div.m-b-40
                  [:div.m-b-10.main-text-color.f-w-b.f-s-16
                   [:div.flex.align-items-c
                    [:i.fa.fa-users.m-l-10]
                    (if editing?
                      [:div.flex.align-items-c.flex-wrap
                       [:input.input.m-l-10
                        {:value (or (@editing-parties id) name)
                         :style party-name-editor-style
                         :on-change (set-editing-party-handler editing-parties id)}]
                       [:div.m-l-10.w-200
                        [comps/selection-adder
                         (sequence
                          (comp
                           (remove
                            (fn [{:keys [:db/id]}]
                              (character-ids id)))
                           (map
                            (fn [{:keys [:db/id] :as char-summary}]
                              {:name (character-display-name char-summary)
                               :key id})))
                          @(subscribe [::char/characters]))
                         (fn [e]
                           (let [selected-id (js/parseInt (.. e -target -value))]
                             (dispatch [::party/add-character id selected-id])))]]
                       [:div.m-t-5
                        [:button.form-button.m-l-10
                         {:on-click #(do (dispatch [::party/rename-party id (@editing-parties id)])
                                         (swap! editing-parties assoc id nil))}
                         "save"]
                        [:button.form-button.m-l-10
                         {:on-click #(dispatch [::party/delete-party id])}
                         "delete"]
                        [:button.form-button.m-l-10
                         {:on-click #(swap! editing-parties assoc id nil)}
                         "cancel"]]]
                      [:div.flex.align-items-c
                       [:span.m-l-5 name]
                       [:i.fa.fa-pencil-alt.m-l-10.opacity-5.hover-opacity-full.pointer
                        {:on-click #(swap! editing-parties assoc id name)}]])]]
                  [:div.item-list
                   (doall
                    (map
                     (fn [{:keys [::se/owner] :as summary}]
                       (let [character-id (:db/id summary)
                             character @(subscribe [::char/character character-id])
                             expanded? (get-in @expanded-characters [id character-id])
                             char-page-path (routes/path-for routes/dnd-e5-char-page-route :id character-id)
                             char-page-route (routes/match-route char-page-path)]
                         ^{:key character-id}
                         [:div.main-text-color.item-list-item
                          [:div.pointer
                           [:div.flex.justify-cont-s-b.align-items-c
                            {:on-click #(swap! expanded-characters update-in [id character-id] not)}
                            [:div.m-l-10.flex.align-items-c
                             [:div.f-s-24.f-w-600
                              [:div.list-character-summary
                               [character-summary-2 summary true owner true false]]]]
                            [:div.orange.pointer.m-r-10
                             (when (not= device-type :mobile) [:span.underline (if expanded?
                                                                               "collapse"
                                                                               "open")])
                             [:i.fa.m-l-5
                              {:class (if expanded? "fa-caret-up" "fa-caret-down")}]]]
                           (when expanded?
                             [:div
                              {:style character-display-style}
                              [:div.flex.justify-cont-end.uppercase.align-items-c
                               (when (= username owner)
                                 [:button.form-button
                                  {:on-click #(dispatch [:edit-character character])}
                                  "edit"])
                               [:button.form-button.m-l-5
                                {:on-click #(dispatch [:route char-page-route])}
                                "view"]
                               [:button.form-button.m-l-5
                                {:on-click #(dispatch [::party/remove-character id character-id])}
                                "remove from party"]]
                              [character-display character-id false (if (= :mobile device-type) 1 2)]])]]))
                     (sort-by :orcpub.dnd.e5.character/character-name characters)))]]))
             parties))]]]))))

(defn monster-list-item [{:keys [name size type subtypes alignment key] :as monster}]
  (r/with-let [device-type? (subscribe [:device-type])]
    (let [homebrew? (:option-pack monster)
          expanded? @(subscribe [:monster-expanded? name])]
      [:div.main-text-color.item-list-item
       [:div.pointer
        [:div.flex.justify-cont-s-b.align-items-c
         {:on-click #(dispatch [:toggle-monster-expanded name])}
         [:div.m-l-10
          [:div.f-s-24.f-w-600.p-b-20.p-t-20.flex
           (when homebrew?
             [:div.m-r-10 (svg-icon "beer-stein" 24 @(subscribe [:theme]))])
           [monster-summary name size type subtypes alignment]]]
         [:div.orange.pointer.m-r-10
          (when (not= @device-type? :mobile)
            [:span.underline (if expanded?
                               "collapse"
                               "open")])
          [:i.fa.m-l-5
           {:class (if expanded? "fa-caret-up" "fa-caret-down")}]]]
        (when expanded?
          [:div.p-10
           {:style character-display-style}
           [:div.flex.justify-cont-end.uppercase.align-items-c
            [:button.form-button.m-l-5
             {:on-click #(dispatch [:route (routes/match-route (routes/path-for routes/dnd-e5-monster-page-route :key key))])}
             "view"]
            (when homebrew?
              [:button.form-button.m-l-5
               {:on-click (make-event-handler ::monsters/edit-monster monster)}
               "edit"])
            (when homebrew?
              [:button.form-button.m-l-5
               {:on-click (make-event-handler ::monsters/delete-monster monster)}
               "delete"])]
           [monster-component monster]])]])))

(defn monster-list-items []
  (let [filtered-monsters @(subscribe [::monsters/filtered-monsters])]
    [:div.item-list
     (doall
       (map
         (fn [{:keys [name] :as monster}]
           ^{:key name}
           [monster-list-item monster])
         filtered-monsters))]))

(defn clear-monsters-filter []
  (dispatch [::char/filter-monsters ""]))

(def toggle-handler
  (memoize
   (fn [a]
     #(swap! a not))))

(defn- sort-toggle
  "Resembles an underlined link. Uses the FA arrows to indicate sorting direction.

   Should be generalized or moved."
  [label value sort-event sort-criteria sort-direction]
  [:div.orange.pointer.m-r-10
   {:on-click #(dispatch [sort-event value (if (= sort-direction "asc") "desc" "asc")])}
   [:span.underline label]
   [:i.fa.m-l-5
    {:class (s/join " "
                    [(when (not= sort-criteria value)
                       "invisible")
                     (if (= sort-direction "asc")
                       "fa-caret-up"
                       "fa-caret-down")])}]])

(defn monster-trait-filters
  []
  [:div.flex.flex-wrap
   [:div.main-text-color.p-20
    [:div.f-s-16.f-w-b "Size"]
    [:div
     (doall
       (map
         (fn [size]
           ^{:key size}
           [:div.p-5.pointer
            {:on-click (make-event-handler ::char/toggle-monster-filter-hidden :size size)}
            (comps/checkbox (not @(subscribe [::char/monster-filter-hidden? :size size])) false)
            (str " " (s/capitalize (common/kw-to-name size)))])
         @(subscribe [::char/monster-sizes])))]]
   [:div.main-text-color.p-20
    [:div.f-s-16.f-w-b "Type"]
    [:div
     (doall
       (map
         (fn [type]
           ^{:key type}
           [:div.p-5.pointer
            {:on-click (make-event-handler ::char/toggle-monster-filter-hidden :type type)}
            (comps/checkbox (not @(subscribe [::char/monster-filter-hidden? :type type])) false)
            (str " " (s/capitalize (common/kw-to-name type)))])
         @(subscribe [::char/monster-types])))]]
   (let [subtypes @(subscribe [::char/monster-subtypes])]
     [:div.main-text-color.p-20
      [:div.f-s-16.f-w-b "Subtype"]
      [:div
       (doall
         (map
           (fn [subtype]
             ^{:key subtype}
             [:div.p-5.pointer
              {:on-click (make-event-handler ::char/toggle-monster-filter-hidden :subtype subtype)}
              (comps/checkbox (not @(subscribe [::char/monster-filter-hidden? :subtype subtype])) false)
              (str " " (s/capitalize (common/kw-to-name subtype)))])
           subtypes))]])])

(defn monster-filter-toggle
  [filters-expanded?]
  [:div.orange.pointer.m-r-10
   {:on-click (toggle-handler filters-expanded?)}
   (when (not= @(subscribe [:device-type]) :mobile)
     [:span.underline (if @filters-expanded?
                        "hide"
                        "filters")])
   [:i.fa.m-l-5
    {:class (if @filters-expanded? "fa-caret-up" "fa-caret-down")}]])

(defn monster-list []
  (r/with-let [filters-expanded? (r/atom false)
               sort-criteria (subscribe [::char/monster-sort-criteria])
               sort-direction (subscribe [::char/monster-sort-direction])]
    [content-page
     "Monsters"
     []
     [:div.p-l-5.p-r-5.p-b-10
      [:div.p-b-10.p-l-10.p-r-10
       [:div.posn-rel
        [:input.input.f-s-24.p-l-20.w-100-p.h-60
         {:value     @(subscribe [::char/monster-text-filter])
          :on-change (make-arg-event-handler ::char/filter-monsters event-value)}]
        [:i.fa.fa-times.posn-abs.f-s-24.pointer.main-text-color
         {:style    close-icon-style
          :on-click clear-monsters-filter}]]]
      [:div
       [:div.flex.justify-cont-s-b.m-b-10
        [:div.orange.m-l-10.m-r-10 "Sort by:"]
        [sort-toggle "Name" "name" ::char/sort-monsters @sort-criteria @sort-direction]
        [sort-toggle "Challenge Rating" "cr" ::char/sort-monsters @sort-criteria @sort-direction]
        [:div.flex-grow-1]
        [monster-filter-toggle filters-expanded?]]
       (when @filters-expanded?
         [monster-trait-filters])]
      [monster-list-items]]]))

(defn spell-list-item [{:keys [name level school ritual key] :as spell}]
  (let [expanded? @(subscribe [:spell-expanded? name])
        device-type @(subscribe [:device-type])
        spell-page-path (routes/path-for routes/dnd-e5-spell-page-route :key key)
        spell-page-route (routes/match-route spell-page-path)
        homebrew? (:option-pack spell)]
    [:div.main-text-color.item-list-item
     [:div.pointer
      [:div.flex.justify-cont-s-b.align-items-c
       {:on-click (make-event-handler :toggle-spell-expanded name)}
       [:div.m-l-10
        [:div.f-s-24.f-w-600.p-t-20.flex
         (when homebrew?
           [:div.m-r-10 (svg-icon "beer-stein" 24 @(subscribe [:theme]))])
         [spell-summary name level school ritual true 12]]]
       [:div.orange.pointer.m-r-10
        (when (not= device-type :mobile) [:span.underline (if expanded?
                                                          "collapse"
                                                          "open")])
        [:i.fa.m-l-5
         {:class (if expanded? "fa-caret-up" "fa-caret-down")}]]]
      (when expanded?
        [:div.p-10
         {:style character-display-style}
         [:div.flex.justify-cont-end.uppercase.align-items-c
          [:button.form-button.m-l-5
           {:on-click (make-event-handler :route spell-page-route)}
           "view"]
          (when homebrew?
            [:button.form-button.m-l-5
             {:on-click (make-event-handler ::spells/edit-spell spell)}
             "edit"])
          (when homebrew?
            [:button.form-button.m-l-5
             {:on-click (make-event-handler ::spells/delete-spell spell)}
             "delete"])
          ]
         [spell-component spell true]])]]))

(defn spell-list-items [device-type]
  [:div.item-list
   (doall
    (map
     (fn [{:keys [name level school key] :as spell}]
       ^{:key name}
       [spell-list-item spell])
     @(subscribe [::char/filtered-spells])))])

(defn spell-list []
  (let [device-type @(subscribe [:device-type])]
    [content-page
     "Spells"
     []
     [:div.p-l-5.p-r-5.p-b-10
      [:div.p-b-10.p-l-10.p-r-10
       [:div.posn-rel
        [:input.input.f-s-24.p-l-20.w-100-p.h-60
         {:value @(subscribe [::char/spell-text-filter])
          :on-change (make-arg-event-handler ::char/filter-spells event-value)}]
        [:i.fa.fa-times.posn-abs.f-s-24.pointer.main-text-color
         {:style close-icon-style
          :on-click (make-event-handler ::char/filter-spells "")}]]]
      [spell-list-items device-type]]]))

(defn item-list-item [{:keys [key name ::mi/owner :db/id] :as item} expanded?]
  (let [expanded-key (or name (::mi/name item))
        device-type @(subscribe [:device-type])
        expanded? @(subscribe [:item-expanded? expanded-key])
        username @(subscribe [:username])
        item-page-path (routes/path-for routes/dnd-e5-item-page-route :key (or id key))
        item-page-route (routes/match-route item-page-path)]
    [:div.main-text-color.item-list-item
     [:div.pointer
      [:div.flex.justify-cont-s-b.align-items-c
       {:on-click (make-event-handler :toggle-item-expanded expanded-key)}
       [:div.m-l-10
        [:div.f-s-24.f-w-600.p-t-20
         [item-summary item]]]
       [:div.orange.pointer.m-r-10
        (when (not= device-type :mobile) [:span.underline (if expanded?
                                                          "collapse"
                                                          "open")])
        [:i.fa.m-l-5
         {:class (if expanded? "fa-caret-up" "fa-caret-down")}]]]
      (when expanded?
        [:div.p-10
         {:style character-display-style}
         [:div.flex.justify-cont-end.uppercase.align-items-c
          [:button.form-button.m-l-5
           {:on-click (make-event-handler :route item-page-route)}
           "view"]
          (when (= username owner)
            [:button.form-button.m-l-5
             {:on-click (make-event-handler ::mi/edit-custom-item @(subscribe [::mi/custom-item id]))}
             "edit"])
          (when (= username owner)
            [:button.form-button.m-l-5
             {:on-click (make-event-handler ::mi/show-delete-confirmation id)}
             "delete"])]
            (when @(subscribe [::mi/delete-confirmation-shown? id])
              [:div.p-20.flex.justify-cont-end
               [:div
                [:div.m-b-10 "Are you sure you want to delete this item?"]
                [:div.flex
                 [:button.form-button
                  {:on-click (make-event-handler ::mi/hide-delete-confirmation id)}
                  "cancel"]
                 [:span.link-button
                  {:on-click  (delete-item-handler id)}
                  "delete"]]]])
            [item-component item]
        ])
      ]]))

(defn item-list-items []
  [:div.item-list
   (doall
    (map
     (fn [{:keys [:db/id key] :as item}]
       ^{:key (or key id)}
       [item-list-item item])
     @(subscribe [::char/filtered-items])))])

(defn item-list []
  (let [device-type @(subscribe [:device-type])]
    [content-page
     "Items"
     [[:button.form-button
       {:on-click (make-event-handler ::mi/new-item)}
       [:div.flex.align-items-c.white
        [svg-icon "beer-stein" 18 ""]
        [:span.m-l-5 "New Item"]]]]
     [:div.p-l-5.p-r-5.p-b-10
      [:div.p-b-10.p-l-10.p-r-10
       [:div.posn-rel
        [:input.input.f-s-24.p-l-20.w-100-p.h-60
         {:value @(subscribe [::char/item-text-filter])
          :on-change (make-arg-event-handler ::char/filter-items event-value)}]
        [:i.fa.fa-times.posn-abs.f-s-24.pointer.main-text-color
         {:style close-icon-style
          :on-click (make-event-handler ::char/filter-items "")}]]]
      [item-list-items]]]))

