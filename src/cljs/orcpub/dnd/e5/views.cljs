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
            [orcpub.dnd.e5.character.random :as char-random]
            [orcpub.dnd.e5.character.equipment :as char-equip]
            [cljs.pprint :refer [pprint]]
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
            [clojure.string :as s]
            [cljs.reader :as reader]
            [orcpub.user-agent :as user-agent]
            [cljs.core.async :refer [<! timeout]]
            [bidi.bidi :as bidi])
  (:require-macros [cljs.core.async.macros :refer [go]]))

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
  (if messages
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
      {:auto-complete :off})]]])

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
         :class-name (if (and @blurred? (seq messages))
                       "b-red"
                       "b-gray")
         :on-focus (fn [_] (reset! blurred? false))
         :on-change on-change
         :on-blur (fn [e] (reset! blurred? true))}]
       (if @blurred? (validation-messages messages))])))

(defn export-pdf [built-char id & [options]]
  (fn [_]
    (let [field (.getElementById js/document "fields-input")]
      (aset field "value" (str (pdf-spec/make-spec built-char id options)))
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
      :class-name (if light-theme? " opacity-7")
      :src (str (if light-theme? "/image/black/" "/image/") icon-name ".svg")}]))

(defn facebook-share-button-comp [url]
  [:div.fb-share-button
   {:data-layout "button"
    :data-href url}])

(defn on-fb-login [logged-in?]
  (if (not logged-in?)
    (do (go (<! (timeout 2000))
            (if (not @(subscribe [:login-message-shown?]))
              (dispatch [:show-login-message "You must enable popups to allow Facebook login."])))
        (if js/FB
          (.login js/FB events/fb-login-callback (clj->js {:scope "email"}))))))

(defn fb-login-button-comp []
  [:div.flex.justify-cont-s-a
   [:button.form-button.flex.align-items-c
    (let [logged-in? @(subscribe [:fb-logged-in?])]
      {:on-click #(if logged-in?
                    (dispatch [:fb-logout])
                    (on-fb-login logged-in?))})
    [:i.fa.fa-facebook.f-s-18]
    [:span.m-l-10.f-s-14
     "Login with Facebook"]]])

(defn dispatch-init-fb []
  (dispatch [:init-fb]))

(defn add-facebook-init [comp]
  (with-meta
    comp
    {:component-did-mount dispatch-init-fb}))

(def facebook-login-button
  (add-facebook-init
   fb-login-button-comp))

(def facebook-share-button
  (add-facebook-init
    facebook-share-button-comp))

(defn character-page-fb-button [id]
  [facebook-share-button
   (str
    "http://"
    js/window.location.hostname
    (routes/path-for routes/dnd-e5-char-page-route :id id))])

(def login-style
  {:color "#f0a100"})

(defn dispatch-logout []
  (dispatch [:logout]))

(defn dispatch-route-to-login [e]
  (.stopPropagation e)
  (dispatch [:route-to-login]))

(defn dispatch-route-to-my-account [e]
  (dispatch [:route :my-account]))

(def header-tab-style
  {:width "85px"})

(def active-style {:background-color "rgba(240, 161, 0, 0.7)"})

(def menu-color "#2c3445")

(def header-menu-item-style
  {:position :absolute
   :background-color "#2c3445"
   :z-index 10000
   :top 84
   :right 0})

(def desktop-menu-item-style
  (assoc header-menu-item-style
         :width "100%"))

(def mobile-header-menu-item-style
  (assoc header-menu-item-style
         :top 46))

(def user-menu-style
  {:background-color menu-color
   :z-index 10000
   :position :fixed
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
    (set! (.-right style) (str (- window-width right) "px"))
    (set! (.-top style) (str bottom "px"))
    (set! (.-display style) "block")))

(defn hide-user-menu [e]
  (let [user-menu (js/document.getElementById "user-menu")
        style (.-style user-menu)]
    (set! (.-display style) "none")))

(defn user-header-view []
  (let [username @(subscribe [:username])
        mobile? @(subscribe [:mobile?])]
    [:div#user-header.pointer
     (if username
       {:on-click hide-user-menu
        :on-mouse-over handle-user-menu
        :on-mouse-out hide-user-menu})
     [:div.flex.align-items-c
      [:div.user-icon [svg-icon "orc-head" 40 ""]]
      (if username
        [:span.f-w-b.t-a-r
         (if (not @(subscribe [:mobile?])) [:span.m-r-5 username])]
        [:span.pointer.flex.flex-column.align-items-end
         [:span.orange.underline.f-w-b.m-l-5
          {:style login-style
           :on-click dispatch-route-to-login}
          [:span "LOGIN"]]])
      (if username
        [:i.fa.m-l-5.fa-caret-down])]
     [:div#user-menu.shadow.f-w-b
      {:style user-menu-style}
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

(defn header-tab []
  (let [hovered? (r/atom false)]
    (fn [title icon on-click disabled active device-type & buttons]
      (let [mobile? (= :mobile device-type)]
        [:div.f-w-b.f-s-14.t-a-c.header-tab.m-5.posn-rel
         {:on-click (fn [e] (if (seq buttons)
                              #(swap! hovered? not)
                              (on-click e)))
          :on-mouse-over #(reset! hovered? true)
          :on-mouse-out #(reset! hovered? false)
          :style (if active active-style)
          :class-name (str (if disabled "disabled" "pointer")
                           " "
                           (if (not mobile?) " w-110"))}
         [:div.p-10
          {:class-name (if (not active) (if disabled "opacity-2" "opacity-6 hover-opacity-full"))}
          (let [size (if mobile? 24 48)] (svg-icon icon size ""))
          (if (not mobile?)
            [:div.title.uppercase title])]
         (if (and (seq buttons)
                  @hovered?)
           [:div.uppercase.shadow
            {:style (if mobile? mobile-header-menu-item-style header-menu-item-style)}
            (doall
             (map
              (fn [{:keys [name route]}]
                ^{:key name}
                [:div.p-10.opacity-5.hover-opacity-full
                 (let [current-route @(subscribe [:route])]
                   {:style (if (or (= route current-route)
                                   (= route (get current-route :handler))) active-style)
                    :on-click (route-handler route)})
                 name])
              buttons))])]))))


(def social-icon-style
  {:color :white
   :font-size "20px"})

(defn social-icon [icon link]
  [:a.p-5.opacity-5.hover-opacity-full.main-text-color
   {:style social-icon-style
    :href link :target :_blank}
   [:i.fa
    {:class-name (str "fa-" icon)}]])

(def search-input-style
  {:height "60px"
   :margin-top "0px"
   :border :none
   :font-size "28px"
   :background-color :transparent
   :color :white})

(def search-icon-style
  {:top 6
   :right 25})

(def search-input-parent-style
  {:background-color "rgba(0,0,0,0.15)"})

(def transparent-search-input-style
  (assoc search-input-style :color :transparent))

(defn route-to-default-route []
  (dispatch [:route routes/default-route]))

(defn search-input-keypress [e]
  (if (= "Enter" (.-key e)) (dispatch [:set-search-text @(subscribe [:search-text])])))

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

(def logo [:img.orcpub-logo.h-32.w-120.pointer
           {:src "/image/orcpub-logo.svg"
            :on-click route-to-default-route}])

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
            {:class-name (if mobile? "p-l-10 p-r-10" "p-l-20 p-r-20 flex-grow-1")}
            [:div.b-rad-5.flex.align-items-c
             {:style search-input-parent-style}
             (if (not mobile?)
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
        {:class-name (if mobile? "justify-cont-s-b" "justify-cont-s-b")}
        [:div
         [:a {:href "https://www.patreon.com/orcpub" :target :_blank}
          [:img.h-32.m-l-10.m-b-5.pointer.opacity-7.hover-opacity-full
           {:src (if mobile?
                   "https://c5.patreon.com/external/logo/downloads_logomark_color_on_navy.png"
                   "https://c5.patreon.com/external/logo/become_a_patron_button.png")}]]
         (if (not mobile?)
           [:div.main-text-color.p-10
            (social-icon "facebook" "https://www.facebook.com/orcpub")
            (social-icon "twitter" "https://twitter.com/OrcPub")
            (social-icon "reddit-alien" "https://www.reddit.com/r/orcpub/")])]
        [:div.flex.m-b-5.m-r-5
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
           :route routes/dnd-e5-encounter-builder-page-route}
          ]
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
          {:name "Elritch Invocation Builder"
           :route routes/dnd-e5-invocation-builder-page-route}
          {:name "Selection Builder"
           :route routes/dnd-e5-selection-builder-page-route}]]]]]]))

(def registration-content-style
  {:background-color :white
   :border "1px solid white"
   :color text-color})

(def registration-page-style
  {:background-image "url(/image/shutterstock_432001912.jpg)"
   :background-clip :content-box
   :width "350px"
   :min-height "600px"})

(def registration-logo-style
  {:height "25.3px"})

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
  [:div.sans.h-100-p.flex
   {:style {:flex-direction :column}}
   [:div.flex.justify-cont-s-a.align-items-c.flex-grow-1.h-100-p
    [:div.registration-content
     {:style registration-content-style}
     [:div.flex.h-100-p
      [:div.flex {:style registration-left-column-style}
       [:div.flex.justify-cont-s-a.align-items-c
        {:style registration-header-style}
        [:img.pointer
         {:src "/image/orcpub-logo.svg"
          :style registration-logo-style
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

(def message-style
  {:padding "10px"
   :border-radius "5px"
   :display :flex
   :justify-content :space-between})

(defn message [message-type message-text close-handler]
  [:div.pointer.f-w-b ;;.h-0.opacity-0.fade-out
   {:on-click close-handler}
   [:div.white
    {:style message-style
     :class-name (case message-type
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
           (if error-message [:div.red.m-b-20 error-message])
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
           (if @(subscribe [:login-message-shown?])
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
             :class-name (if bad-email? "disabled opacity-5 hover-no-shadow")
             :on-click (if (not bad-email?) (make-event-handler :send-password-reset @params))}
            "SUBMIT"]]])))))

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
                        :messages (if different? ["Passwords do not match"])
                        :on-change (fn [e] (swap! params assoc :verify-password (event-value e)))}]
           (if @(subscribe [:login-message-shown?])
             [:div.m-t-5.p-r-5.p-l-5 [message
                                      :error
                                      @(subscribe [:login-message])
                                      hide-login-message]])
           [:button.form-button.m-l-20.m-t-10
            {:style {:height "40px"
                     :width "174px"
                     :font-size "16px"
                     :font-weight "600"}
             :class-name (if invalid? "opacity-5 hover-no-shadow cursor-disabled")
             :on-click (if (not invalid?) (make-event-handler :password-reset @params))}
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
    [:span ". You must verify to complete registration and the link we sent will only be valid for 24 hours."]]))

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
             :class-name color}]
           [:div.b-rad-5.password-strength-meter
            {:style {:top 0
                     :left 0
                     :position :absolute
                     :height "30px"
                     :transition "width 1s"
                     :width (str (* 100 (float (/ password-strength 5))) "%")}
             :class-name color}]
           [:div.main-text-color.p-l-10.b-rad-5
            {:style {:position :absolute
                     :padding-top "6px"}}
             [:span "Password Strength:"]
             [:span.f-w-b.m-l-5 text]]]])
       [:div.m-t-20
        {:style {:text-align :left
                 :margin-left "15px"}}
        [:i.fa.fa-check.f-s-14.pointer
         {:class-name (if send-updates? "orange" "white")
          :style {:margin-top "-3px"
                  :border-color "#f0a100"
                  :border-style :solid
                  :border-width "1px"
                  :border-bottom-width "3px"}
          :on-click #(dispatch [:registration-send-updates? (not send-updates?)])}]
        [:span.m-l-5 "Yes! Send me updates about OrcPub."]]
       [:div.m-t-30
        [:div.p-10
         [:span "Already have an account?"]
         (login-link)]
        [:button.form-button
         {:style {:height "40px"
                  :width "174px"
                  :font-size "16px"
                  :font-weight "600"}
          :class-name (if (seq registration-validation) "opacity-5 hover-no-shadow cursor-disabled")
          :on-click #(if (empty? registration-validation)
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
          [:div.m-t-10
           [facebook-login-button]]
          [:div
           {:style {:margin-top "50px"}}
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
           (if login-message-shown?
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
             [:span "Don't have a login? "]
             [:span.orange.underline.pointer
              {:on-click route-to-register-page}
              "REGISTER NOW"]]
            [:div.m-t-20
             [:span "Forgot your password? "]
             [:span.orange.underline.pointer
              {:on-click route-to-reset-password-page}
              "RESET PASSWORD"]]]]])))))

(def loading-style
  {:position :absolute
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
     (if (:pre cfg)
       ((:pre cfg)))
     (dispatch [:confirm (:event cfg)])))

(def confirm-handler (memoize confirm-fn))

(defn header [title button-cfgs & {:keys [frame?]}]
  (let [device-type @(subscribe [:device-type])]
    [:div.w-100-p
     [:div.flex.align-items-c.justify-cont-s-b.flex-wrap
      [:div.flex
       [:h1.f-s-36.f-w-b.m-t-5.m-l-10
        {:class-name (if (not= :mobile device-type) "m-t-21 m-b-20")}
        title]
       (if frame?
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
              :class-name class-name
              :style style}
             [:span
              [:i.fa.f-s-18
               (if icon {:class-name (str "fa-" icon)})]]
             [:span.m-l-5.header-button-text title]]))
        button-cfgs)]]
     (if @(subscribe [:confirmation-shown?])
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
     (if @(subscribe [::char/options-shown?])
       [:div.bg-light.m-b-10 @(subscribe [::char/options-component])])
     (if @(subscribe [:message-shown?])
       [:div.p-b-10.p-r-10.p-l-10.white
        [message
         @(subscribe [:message-type])
         @(subscribe [:message])
         hide-message]])]))

(def debug-data-style {:width "400px" :height "400px"})

(defn debug-data []
  (let [expanded? (r/atom false)]
    (fn []
      [:div.t-a-r
       [:div.orange.pointer.underline
        {:on-click #(swap! expanded? not)
         :title "Development - Debug Info" }
        [:i.fa.fa-bug {:class-name (if @expanded? "white")}]]
       [:div.orange.pointer.underline
        {:on-click (make-event-handler ::e5/export-all-plugins-pretty-print)
         :title "Development - Download all Orcbrews as Pretty Print, if you click this button it will take a long time to generate the orcbrew.  Click and wait."}
        [:i.fa.fa-cloud-download]]
       (if @expanded?
         [:textarea.m-t-5
          {:read-only true
           :style debug-data-style
           :value (str {:browser (user-agent/browser)
                        :browser-version (user-agent/browser-version)
                        :device-type (user-agent/device-type)
                        :platform (user-agent/platform)
                        :platform-version (user-agent/platform-version)
                        :character (char/to-strict @(subscribe [:character]))})}])])))

(defn dice-roll-result [{:keys [total rolls mod raw-mod plus-minus]}]
  [:div.white.f-s-32.flex.align-items-c
   (svg-icon "rolling-dices" 36 "")
   [:div.m-l-10
    [:span.f-w-b total]
    [:span.m-l-10.m-r-10 "="]
    [:span (s/join " + " rolls)]
    (if (not= 0 raw-mod) [:span (if (pos? plus-minus) " + " " - ")])
    (if (not= 0 raw-mod) [:span raw-mod])]])

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
   (if (-> attunement set :any not)
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
  (if item
    [:div.p-b-20.flex.align-items-c
     (if owner
       [:div.m-r-5 [svg-icon "beer-stein" 24]])
     [:div
      [:span.f-s-24.f-w-b (or (:name item) name)]
      [:div.f-s-16.i.f-w-b.opacity-5
       (str (if type (common/safe-capitalize-kw type))
            (if (keyword? item-subtype)
              (str " (" (common/safe-capitalize-kw item-subtype) ")"))
            ", "
            (if (string? rarity)
              rarity
              (common/kw-to-name rarity))
            (if attunement
              (requires-attunement attunement)))]]]))

(defn item-details [{:keys [::mi/summary ::mi/description ::mi/attunment]} single-column?]
  (if (or summary description)
    (paragraphs (or summary description) single-column?)))

(defn item-component [item & [hide-summary? single-column?]]
  [:div.m-l-10.l-h-19
   (if (not hide-summary?)
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
    [:span.f-s-14.opacity-5.i (s/join " " (map (fn [k] (if k (name k))) [sex race subrace]))]]])

(defn tavern-name-result [name]
  [:span.f-s-24.f-w-b.white name])

(defn spell-summary [name level school include-name? & [subheader-size]]
  [:div.p-b-20
   (if include-name? [:span.f-s-24.f-w-b name])
   [:div.i.f-w-b.opacity-5
    {:class-name (str "f-s-" (or subheader-size 18))}
    (str (if (pos? level)
           (str (common/ordinal level) "-level"))
         " "
         (common/safe-capitalize school)
         (if (zero? level)
           " cantrip"))]])

(defn spell-component [{:keys [name level school casting-time range duration components description summary page source] :as spell} include-name? & [subheader-size]]
  [:div.m-l-10.l-h-19
   [spell-summary name level school include-name? subheader-size]
   (spell-field "Casting Time" casting-time)
   (spell-field "Range" range)
   (spell-field "Duration" duration)
   (let [{:keys [verbal somatic material material-component]} components]
     (spell-field "Components" (str (s/join ", " (remove
                                                  nil?
                                                  [(if verbal "V")
                                                   (if somatic "S")
                                                   (if material "M")]))
                                    (if material-component
                                      (str " (" material-component ")")))))
   [:div.m-t-10
    (if description
      (paragraphs description)
      [:div
       (if summary (paragraphs summary))
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
       (fn [{:keys [key name level school casting-time range duration components description summary page source]}]
         ^{:key name}
         [:div.pointer
          {:on-click (let [spell-page-path (routes/path-for routes/dnd-e5-spell-page-route :key key)
                           spell-page-route (routes/match-route spell-page-path)]
                       (make-event-handler :route spell-page-route))}
          [spell-summary name level school true 14]])
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


(defn monster-component [{:keys [name size type subtypes hit-points alignment armor-class armor-notes speed saving-throws skills damage-vulnerabilities damage-resistances damage-immunities condition-immunities senses languages challenge traits actions legendary-actions source page] :as monster}]
  (let [traits-by-type (group-by :type traits)
        traits (traits-by-type nil)
        actions (concat actions (traits-by-type :action))
        legendary (traits-by-type :legendary-action)
        legendary-actions (if (seq legendary)
                            (update legendary-actions :actions concat legendary)
                            legendary-actions)]
    [:div.m-l-10.l-h-19
     (if (not @(subscribe [:mobile?])) {:style two-columns-style})
     [:span.f-s-24.f-w-b name]
     [:div.f-s-18.i.f-w-b (monsters/monster-subheader size type subtypes alignment)]
     (spell-field "Armor Class" (str armor-class (if armor-notes (str " (" armor-notes ")"))))
     (let [{:keys [mean die-count die modifier]} hit-points]
       (spell-field "Hit Points" (str die-count
                                      "d"
                                      die
                                      (if modifier (common/mod-str modifier))
                                      (let [mean
                                            (or mean
                                                (if (and die die-count)
                                                  (dice/dice-mean die
                                                                  die-count
                                                                  (or modifier 0))))]
                                        (if mean (str " (" mean ")"))))))
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
     (if (seq saving-throws)
       (spell-field "Saving Throws" (print-bonus-map saving-throws)))
     (if skills (spell-field "Skills" (print-bonus-map skills)))
     (if damage-vulnerabilities (spell-field "Damage Vulnerabilities" damage-vulnerabilities))
     (if damage-resistances (spell-field "Damage Resistances" damage-resistances))
     (if damage-immunities (spell-field "Damage Immunities" damage-immunities))
     (if condition-immunities (spell-field "Condition Immunities" condition-immunities))
     (if senses (spell-field "Senses" senses))
     (if languages (spell-field "Languages" languages))
     (if challenge (spell-field "Challenge" (str
                                             (case challenge
                                               0.125 "1/8"
                                               0.25 "1/4"
                                               0.5 "1/2"
                                               challenge)
                                             " ("
                                             (monsters/challenge-ratings challenge)
                                             " XP)")))
     (if traits
       [:div.m-t-20
        (doall
         (map-indexed
          (fn [i {:keys [name description]}]
            ^{:key i}
            [:div.m-t-10 (spell-field name description)])
          traits))])
     (if actions
       [:div.m-t-20
        [:div.i.f-w-b.f-s-18 "Actions"]
        [:div
         (doall
          (map-indexed
           (fn [i {:keys [name description]}]
             ^{:key i}
             [:div.m-t-10 (spell-field name description)])
           actions))]])
     (if legendary-actions
       [:div.m-t-20
        [:div.i.f-w-b.f-s-18 "Legendary Actions"]
        (if (:description legendary-actions)
          [:div (:description legendary-actions)])
        (if (:actions legendary-actions)
          [:div
           (doall
            (map-indexed
             (fn [i {:keys [name description]}]
               ^{:key i}
               [:div.m-t-10 (spell-field name description)])
             (:actions legendary-actions)))])])]))

(defn monster-result [monster]
  [:div.white
   [:div.flex
    (svg-icon "hydra" 36 "")
    [monster-component monster]]])

(defn search-results []
  (if-let [{{:keys [result] :as top-result} :top-result
            results :results
            :as search-results}
           @(subscribe [:search-results])]
    [:div
     (if top-result
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
     (if (seq results)
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

(def srd-link
  [:a.orange {:href "/SRD-OGL_V5.1.pdf" :target "_blank"} "the 5e SRD"])

(defn amazon-link [title url]
  [:a.orange {:href url :target "_blank"} title])

(def phb-link (amazon-link "PHB" "https://www.amazon.com/gp/product/0786965606/ref=as_li_tl?ie=UTF8&tag=orcpub-20&camp=1789&creative=9325&linkCode=as2&creativeASIN=0786965606&linkId=d0e5f5e84d625c00ca1e9f9086e5d7c4"))

(def dmg-link (amazon-link "DMG" "https://www.amazon.com/gp/product/0786965622/ref=as_li_tl?ie=UTF8&tag=orcpub-20&camp=1789&creative=9325&linkCode=as2&creativeASIN=0786965622&linkId=067760cfb629d77c7162287b06684bf4"))

(def mm-link (amazon-link "MM" "https://www.amazon.com/gp/product/0786965614/ref=as_li_tl?ie=UTF8&tag=orcpub-20&camp=1789&creative=9325&linkCode=as2&creativeASIN=0786965614&linkId=6d27c01945d45ceff0204eade602f998"))

(def xge-link (amazon-link "XGE" "https://www.amazon.com/gp/product/0786966114/ref=as_li_tl?ie=UTF8&tag=orcpub-20&camp=1789&creative=9325&linkCode=as2&creativeASIN=0786966114&linkId=df71445fe4cab418c535a38d55c3b761"))

(def scag-link (amazon-link "SCAG" "https://www.amazon.com/gp/product/0786965800/ref=as_li_tl?ie=UTF8&tag=orcpub-20&camp=1789&creative=9325&linkCode=as2&creativeASIN=0786965800&linkId=deac8f6d8ceeb243cc29931992bcde52"))

(def vgm-link (amazon-link "VGM" "https://www.amazon.com/gp/product/0786966017/ref=as_li_tl?ie=UTF8&tag=orcpub-20&camp=1789&creative=9325&linkCode=as2&creativeASIN=0786966017&linkId=1db974c1bc5c3f2f49971ad2e1fc7906"))

(def toa-link (amazon-link "TOA" "https://www.amazon.com/gp/product/0786966106/ref=as_li_tl?ie=UTF8&tag=orcpub-20&camp=1789&creative=9325&linkCode=as2&creativeASIN=0786966106&linkId=83a1e9d255d69267b0b441e8a7bcde80"))

(def skt-link (amazon-link "SKT" "https://www.amazon.com/gp/product/0786966009/ref=as_li_tl?ie=UTF8&tag=orcpub-20&camp=1789&creative=9325&linkCode=as2&creativeASIN=0786966009&linkId=191904c4573b03a08afbc490e1c94fd9"))

(def oota-link (amazon-link "OOTA" "https://www.amazon.com/gp/product/0786965819/ref=as_li_tl?ie=UTF8&tag=orcpub-20&camp=1789&creative=9325&linkCode=as2&creativeASIN=0786965819&linkId=e00c6e6016c6d319a0191c35cde3d24b"))

(def pota-link (amazon-link "POTA" "https://www.amazon.com/gp/product/0786965789/ref=as_li_tl?ie=UTF8&tag=orcpub-20&camp=1789&creative=9325&linkCode=as2&creativeASIN=0786965789&linkId=7db71e3ac545c311e302d15692072b74"))

(def yp-link (amazon-link "YP" "https://www.amazon.com/gp/product/0786966092/ref=as_li_tl?ie=UTF8&tag=orcpub-20&camp=1789&creative=9325&linkCode=as2&creativeASIN=0786966092&linkId=09d5160452645b8c9cac1e66735120f3"))

(def cos-link (amazon-link "COS" "https://www.amazon.com/gp/product/0786965983/ref=as_li_tl?ie=UTF8&tag=orcpub-20&camp=1789&creative=9325&linkCode=as2&creativeASIN=0786965983&linkId=b47dbcb3687fd053c365d68c7132937e"))

#_(defn amazon-frame [link]
  [:iframe {:style {:width "120px" :height "240px"}
            :margin-width 0
            :margin-height 0
            :scrolling :no
            :frame-border 0
            :src link}])

#_(def scag-amazon-frame
  (amazon-frame "//ws-na.amazon-adsystem.com/widgets/q?ServiceVersion=20070822&OneJS=1&Operation=GetAdHtml&MarketPlace=US&source=ac&ref=tf_til&ad_type=product_link&tracking_id=orcpub-20&marketplace=amazon&region=US&placement=0786965800&asins=0786965800&linkId=f35402a86dd0851190d952228fab36e9&show_border=false&link_opens_in_new_window=true&price_color=ffffff&title_color=f0a100&bg_color=2c3445"))

#_(def volos-amazon-frame
  (amazon-frame "//ws-na.amazon-adsystem.com/widgets/q?ServiceVersion=20070822&OneJS=1&Operation=GetAdHtml&MarketPlace=US&source=ac&ref=tf_til&ad_type=product_link&tracking_id=orcpub-20&marketplace=amazon&region=US&placement=0786966017&asins=0786966017&linkId=8c552e7b980d7d944bd12dec57e002e8&show_border=false&link_opens_in_new_window=true&price_color=ffffff&title_color=f0a100&bg_color=2c3445"))

#_(def phb-amazon-frame
  (amazon-frame "//ws-na.amazon-adsystem.com/widgets/q?ServiceVersion=20070822&OneJS=1&Operation=GetAdHtml&MarketPlace=US&source=ac&ref=qf_sp_asin_til&ad_type=product_link&tracking_id=orcpub-20&marketplace=amazon&region=US&placement=0786965606&asins=0786965606&linkId=3b5b686390559c31dbc3c20d20f37ec4&show_border=false&link_opens_in_new_window=true&price_color=ffffff&title_color=f0a100&bg_color=2c3445"))

#_(def dmg-amazon-frame
  (amazon-frame "//ws-na.amazon-adsystem.com/widgets/q?ServiceVersion=20070822&OneJS=1&Operation=GetAdHtml&MarketPlace=US&source=ac&ref=tf_til&ad_type=product_link&tracking_id=orcpub-20&marketplace=amazon&region=US&placement=0786965622&asins=0786965622&linkId=01922a9aafc4ea52eb90aed12bbeac04&show_border=false&link_opens_in_new_window=true&price_color=ffffff&title_color=f0a100&bg_color=2c3445"))

#_(def mm-amazon-frame
  (amazon-frame "//ws-na.amazon-adsystem.com/widgets/q?ServiceVersion=20070822&OneJS=1&Operation=GetAdHtml&MarketPlace=US&source=ac&ref=qf_sp_asin_til&ad_type=product_link&tracking_id=orcpub-20&marketplace=amazon&region=US&placement=0786965614&asins=0786965614&linkId=5300756d865067bd552325212c176447&show_border=false&link_opens_in_new_window=true&price_color=ffffff&title_color=f0a100&bg_color=2c3445"))

#_(def xanathars-amazon-frame
  (amazon-frame "//ws-na.amazon-adsystem.com/widgets/q?ServiceVersion=20070822&OneJS=1&Operation=GetAdHtml&MarketPlace=US&source=ac&ref=tf_til&ad_type=product_link&tracking_id=orcpub-20&marketplace=amazon&region=US&placement=0786966114&asins=0786966114&linkId=b1241c813fda22ff1b5ba56ba52cee50&show_border=false&link_opens_in_new_window=true&price_color=ffffff&title_color=f0a100&bg_color=2c3445"))

#_(def toa-amazon-frame
  (amazon-frame "//ws-na.amazon-adsystem.com/widgets/q?ServiceVersion=20070822&OneJS=1&Operation=GetAdHtml&MarketPlace=US&source=ac&ref=tf_til&ad_type=product_link&tracking_id=orcpub-20&marketplace=amazon&region=US&placement=0786966106&asins=0786966106&linkId=8ef050066313a6092678df98f18401f7&show_border=false&link_opens_in_new_window=true&price_color=ffffff&title_color=f0a100&bg_color=2c3445"))

#_(def yawning-portal-amazon-frame
  (amazon-frame "//ws-na.amazon-adsystem.com/widgets/q?ServiceVersion=20070822&OneJS=1&Operation=GetAdHtml&MarketPlace=US&source=ac&ref=qf_sp_asin_til&ad_type=product_link&tracking_id=orcpub-20&marketplace=amazon&region=US&placement=0786966092&asins=0786966092&linkId=df092b3840d56523be6c3626966a0e47&show_border=false&link_opens_in_new_window=true&price_color=ffffff&title_color=f0a100&bg_color=2c3445"))

#_(def cos-amazon-frame
  (amazon-frame "//ws-na.amazon-adsystem.com/widgets/q?ServiceVersion=20070822&OneJS=1&Operation=GetAdHtml&MarketPlace=US&source=ac&ref=qf_sp_asin_til&ad_type=product_link&tracking_id=orcpub-20&marketplace=amazon&region=US&placement=0786965983&asins=0786965983&linkId=91dfcae14b0c8ecd3795eaf375104ca5&show_border=false&link_opens_in_new_window=true&price_color=ffffff&title_color=f0a100&bg_color=2c3445"))

#_(def skt-amazon-frame
  (amazon-frame "//ws-na.amazon-adsystem.com/widgets/q?ServiceVersion=20070822&OneJS=1&Operation=GetAdHtml&MarketPlace=US&source=ac&ref=qf_sp_asin_til&ad_type=product_link&tracking_id=orcpub-20&marketplace=amazon&region=US&placement=0786966009&asins=0786966009&linkId=b0fe41c5ff03ada5d23ebd4a176abcf6&show_border=false&link_opens_in_new_window=true&price_color=ffffff&title_color=f0a100&bg_color=2c3445"))

#_(def oota-amazon-frame
  (amazon-frame "//ws-na.amazon-adsystem.com/widgets/q?ServiceVersion=20070822&OneJS=1&Operation=GetAdHtml&MarketPlace=US&source=ac&ref=qf_sp_asin_til&ad_type=product_link&tracking_id=orcpub-20&marketplace=amazon&region=US&placement=0786965819&asins=0786965819&linkId=125c478897a63892c24d0ca46c198848&show_border=false&link_opens_in_new_window=true&price_color=ffffff&title_color=f0a100&bg_color=2c3445"))

#_(def pota-amazon-frame
    (amazon-frame "//ws-na.amazon-adsystem.com/widgets/q?ServiceVersion=20070822&OneJS=1&Operation=GetAdHtml&MarketPlace=US&source=ac&ref=qf_sp_asin_til&ad_type=product_link&tracking_id=orcpub-20&marketplace=amazon&region=US&placement=0786965789&asins=0786965789&linkId=a2c9018a5e1260f518fa6b0fd0812350&show_border=false&link_opens_in_new_window=true&price_color=ffffff&title_color=f0a100&bg_color=2c3445"))

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

(defn content-page [title button-cfgs content & {:keys [hide-header-message? frame?]}]
  (let [srd-message-closed? @(subscribe [:srd-message-closed?])
        orcacle-open? @(subscribe [:orcacle-open?])
        theme @(subscribe [:theme])
        mobile? @(subscribe [:mobile?])]
    [:div.app
     {:class-name theme
      :on-scroll (if (not frame?)
                   (fn [e]
                     (if (not orcacle-open?)
                       (let [app-header (js/document.getElementById "app-header")
                             header-height (.-offsetHeight app-header)
                             scroll-top (.-scrollTop (.-target e))
                             sticky-header (js/document.getElementById "sticky-header")
                             app-main (js/document.getElementById "app-main")
                             scrollbar-width (- js/window.innerWidth (.-offsetWidth app-main))
                             header-container (js/document.getElementById "header-container")]
                         (set! (.-paddingRight (.-style header-container)) (str scrollbar-width "px"))
                         (if (>= scroll-top header-height)
                           (set! (.-display (.-style sticky-header)) "block")
                           (set! (.-display (.-style sticky-header)) "none"))))))}
     (if (not frame?)
       [download-form])
     (if @(subscribe [:loading])
       [:div {:style loading-style}
        [:div.flex.justify-cont-s-a.align-items-c.h-100-p
         [:img.h-200.w-200.m-t-200 {:src "/image/spiral.gif"}]]])
     (if (not frame?)
       [app-header])
     (if orcacle-open?
       [orcacle])
     (let [hdr [header title button-cfgs :frame? frame?]]
       [:div
        [:div#sticky-header.sticky-header.w-100-p.posn-fixed
         [:div.flex.justify-cont-c
          [:div#header-container.f-s-14.main-text-color.content
           hdr]]]
        [:div.flex.justify-cont-c.main-text-color
         [:div.content hdr]]
        [:div.m-l-20.m-r-20.f-w-b.f-s-18.container.m-b-10.main-text-color
         (if (and (not srd-message-closed?)
                  (not hide-header-message?))
           [:div
            (if (not frame?)
              [:div.content.bg-lighter.p-10.flex
               [:div.flex-grow-1
                [:div "Due to licensing issues, we were forced to remove all non-SRD content, if you have questions about what is and is not SRD content please see the " srd-link ". If you would like to see the non-SRD content added back to OrcPub please sign our " [:a.orange {:href "https://www.change.org/p/wizards-of-the-coast-wizards-of-the-coast-please-grant-orc-pub-licensing-rights-to-your-content" :target "_blank"}
                                                                                                                                                                                                                                                                           "petition here at change.org"]
                 "."]
                (if (not mobile?)
                  [:div.m-t-10 "You can add content from other sources using the builders in the 'My Content' menu. Here are some compatible sources: "
                   [:div.flex.flex-wrap.m-t-10
                    [:div.m-l-5 phb-link]
                    [:div.m-l-5 dmg-link]
                    [:div.m-l-5 mm-link]
                    [:div.m-l-5 xge-link]
                    [:div.m-l-5 scag-link]
                    [:div.m-l-5 vgm-link]
                    [:div.m-l-5 toa-link]
                    [:div.m-l-5 yp-link]
                    [:div.m-l-5 cos-link]
                    [:div.m-l-5 skt-link]
                    [:div.m-l-5 oota-link]
                    [:div.m-l-5 pota-link]]])]
               [:i.fa.fa-times.p-10.pointer
                {:on-click #(dispatch [:close-srd-message])}]])])]
        [:div#app-main.container
         [:div.content.w-100-p content]]
        [:div.main-text-color.flex.justify-cont-c
         [:div.content.f-w-n.f-s-12
          [:div.flex.justify-cont-s-b.align-items-c.flex-wrap.p-10
           [:div
            [:div.m-b-5 "Icons made by Lorc, Caduceus, and Delapouite. Available on " [:a.orange {:href "http://game-icons.net"} "http://game-icons.net"]]]
           [:div.m-l-10
            [:a.orange {:href "https://github.com/larrychristensen/orcpub/issues" :target :_blank} "Feedback/Bug Reports"]]
           [:div.m-l-10.m-r-10.p-10
            [:a.orange {:href "/privacy-policy" :target :_blank} "Privacy Policy"]
            [:a.orange.m-l-5 {:href "/terms-of-use" :target :_blank} "Terms of Use"]]
           [:div.legal-footer
            [:p "© 2019 OrcPub" [:span.m-l-20 "Contact: " [:a {:href "mailto:redorc@orcpub.com"} "redorc@orcpub.com"]]]
            [:p "Wizards of the Coast, Dungeons & Dragons, D&D, and their logos are trademarks of Wizards of the Coast LLC in the United States and other countries. © 2019 Wizards. All Rights Reserved. OrcPub.com is not affiliated with, endorsed, sponsored, or specifically approved by Wizards of the Coast LLC."]]]
            [debug-data]]]])]))

(def row-style
  {:border-bottom "1px solid rgba(255,255,255,0.5)"})

(def light-row-style
  {:border-bottom "1px solid rgba(0,0,0,0.5)"})

(def list-style
  {:border-top "2px solid rgba(255,255,255,0.5)"})

(def thumbnail-style
  {:height "100px"
   :max-width "200px"})

(defn other-user-component [owner & [text-classes show-follow?]]
  (let [following-users @(subscribe [:following-users])
        following? (get following-users owner)
        username @(subscribe [:username])]
    [:div.flex.m-l-10.align-items-c
     (svg-icon "orc-head" 32)
     [:div.f-s-18.m-l-5
      {:class-name text-classes}
      owner]
     (if (and show-follow? username (not= username owner))
       [:button.form-button.m-l-10.p-6
        {:on-click #(dispatch [(if following?
                                 :unfollow-user
                                 :follow-user)
                               owner])}
        (if following?
          "unfollow"
          "follow")])]))

(defn character-summary-2 [{:keys [::char/character-name
                                   ::char/image-url
                                   ::char/race-name
                                   ::char/subrace-name
                                   ::char/classes]}
                           include-name?
                           owner
                           show-owner?
                           show-follow?]
  (let [username @(subscribe [:username])]
    [:div.flex.justify-cont-s-b.w-100-p.align-items-c
     [:div.flex.align-items-c
      (if image-url
        [:img.m-r-20.m-t-10.m-b-10 {:src image-url
                                    :style thumbnail-style}])
      [:div.flex.character-summary.m-t-20.m-b-20
       (if (and character-name include-name?) [:span.m-r-20.m-b-5 character-name])
       [:span.m-r-10.m-b-5
        [:span race-name]
        [:div.f-s-12.m-t-5.opacity-6 subrace-name]]
       (if (seq classes)
         [:span.flex
          (map-indexed
           (fn [i v]
             (with-meta v {:key i}))
           (interpose
            [:span.m-l-5.m-r-5 "/"]
            (map
             (fn [{:keys [::char/class-name ::char/level ::char/subclass-name]}]
               (let []
                 [:span
                  [:span (str class-name " (" level ")")]
                  [:div.f-s-12.m-t-5.opacity-6 (if subclass-name subclass-name)]]))
             classes)))])]]
     (if (and show-owner?
              (some? owner)
              (some? username)
              (not= username owner))
       [:div.m-l-10 [other-user-component owner nil show-follow?]])]))

(defn character-summary [id & [include-name?]]
  (let [character-name @(subscribe [::char/character-name id])
        image-url @(subscribe [::char/image-url id])
        race @(subscribe [::char/race id])
        subrace @(subscribe [::char/subrace id])
        levels @(subscribe [::char/levels id])
        classes @(subscribe [::char/classes id])
        {:keys [::se/owner] :as strict-character} @(subscribe [::char/character id])]
    (character-summary-2
     {::char/character-name character-name
      ::char/image-url image-url
      ::char/race-name race
      ::char/subrace-name subrace
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

(defn realize-char [built-char]
  (reduce-kv
   (fn [m k v]
     (let [realized-value (es/entity-val built-char k)]
       (if (fn? realized-value)
         m
         (assoc m k realized-value))))
   (sorted-map)
   built-char))

(def summary-style
  {:padding "33px 0"})


(defn display-section [title icon-name value & [list? buttons]]
  [:div.m-t-20
   [:div.flex.justify-cont-s-b
    [:div.flex.align-items-c
     (if icon-name (svg-icon icon-name 32))
     [:span.m-l-5.f-s-16.f-w-600 title]]
    (if (seq buttons)
      (apply
       conj
       [:div]
       buttons))]
   [:div {:class-name (if list? "m-t-0" "m-t-4")}
    [:span.f-s-24.f-w-600
     value]]])

(defn list-display-section [title image-name values]
  (if (seq values)
    (display-section
     title
     image-name
     [:span.m-t-5.f-s-14.f-w-n.i
      (s/join
       ", "
       values)]
     true)))

(defn svg-icon-section [title icon-name content]
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

(defn compare-spell [spell-1 spell-2]
  (let [key-fn (juxt :key :ability)]
    (compare (key-fn spell-1) (key-fn spell-2))))

(defn details-button [expanded? on-click]
  [:span.orange.underline.pointer
   {:on-click on-click}
   [:span (if expanded? "hide" "details")]
   [:i.fa.m-l-5
    {:class-name (if expanded? "fa-caret-up" "fa-caret-down")}]])


(defn spellcaster-levels-table []
  (let [expanded? (r/atom false)]
    (fn [spell-slot-factors total-spellcaster-levels levels mobile?]
      [:div.f-s-14.f-w-n
       [:div.flex.justify-cont-s-b
        [:div
         [:span.f-w-b.f-s-16 "Total Spellcaster Levels: "]
         [:span.f-s-16.f-w-n total-spellcaster-levels]]
        (details-button @expanded? #(swap! expanded? not))]
       (if @expanded?
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
            first-factor-key (if spell-slot-factors (-> spell-slot-factors first key))
            first-class-level (if first-factor-key (-> levels first-factor-key :class-level))]
        [:div.f-s-14.f-w-n
         [:div.flex.justify-cont-s-b
          [:div
           [:span.f-w-b.f-s-16 (str "Slots" (if multiclass? " (Multiclass)"))]]
          (if (not pact-magic?)
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
                     {:class-name (if highlight?
                                    "f-w-b")
                      :style (if highlight?
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
                 {:class-name (if @checkboxes-expanded? "fa-caret-up" "fa-caret-down")}]]])]]]
         (if @checkboxes-expanded?
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
  [:select.builder-option.builder-option-dropdown.m-t-0
   {:value (or value "")
    :on-change #(on-change (event-value %))}
   (doall
    (map
     (fn [{:keys [value title disabled?]}]
       ^{:key (or value title)}
       [:option.builder-dropdown-item
        (cond-> {:value value}
          disabled? (assoc :disabled true))
        title])
     items))])

(defn labeled-dropdown [label cfg]
  [:div
   [:div.f-w-b.m-b-5 label]
   [dropdown cfg]])

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
           {:class-name (if (empty? usable-slot-levels) "disabled")
            :on-click #(if (seq usable-slot-levels)
                         (dispatch [::char/use-spell-slot id (or @selected-level (first usable-slot-levels))]))}
           "cast spell"]]]))))

(def expanded-spell-background-style
  {:background-color "rgba(0,0,0,0.1)"})

(defn spell-row [id lvl spell-modifiers prepares-spells prepared-spells-by-class {:keys [key ability qualifier class always-prepared?]} expanded? on-click prepare-spell-count prepared-spell-count]
  (let [spell-map @(subscribe [::spells/spells-map])
        spell (spell-map key)
        cls-mods (get spell-modifiers class)
        remaining-preps (- prepare-spell-count
                           prepared-spell-count)]
    [[:tr.pointer
      {:on-click on-click}
      [:td.p-l-10.p-b-10.p-t-10.f-w-b
       (if (and (pos? lvl)
                (get prepares-spells class))
         [:span.m-r-5
          {:class-name (if always-prepared?
                         "cursor-disabled")
           :on-click (fn [e]
                       (if (not always-prepared?)
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
      [:td.p-l-10.p-b-10.p-t-10 class]
      [:td.p-l-10.p-b-10.p-t-10 (if ability (s/upper-case (common/safe-name ability)))]
      [:td.p-l-10.p-b-10.p-t-10 (get cls-mods :spell-save-dc)]
      [:td.p-l-10.p-b-10.p-t-10 (common/bonus-str (get cls-mods :spell-attack-modifier))]
      [:td.p-r-10.orange
       [:i.fa
        {:class-name (if expanded? "fa-caret-up" "fa-caret-down")}]]]
     (if expanded?
       [:tr {:style expanded-spell-background-style}
        [:td {:col-span 6}
         [:div.p-10
          (if (pos? lvl)
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
           (if hide-unprepared?
             [:span.i.opacity-5.m-l-5 "(unprepared hidden)"])]
          (if (pos? lvl)
            [:span.f-w-b (str @(subscribe [::char/spell-slots-remaining id lvl]) " remaining")])]
         [:table.w-100-p.t-a-l.striped
          [:tbody
           [:tr.f-w-b.f-s-12
            [:th.p-l-10.p-b-10.p-t-10 (if (and (not (zero? lvl))
                                               (seq prepares-spells))
                                        "Prepared? / Name"
                                        "Name")]
            [:th.p-l-10.p-b-10.p-t-10 (if mobile? "Src" "Source")]
            [:th.p-l-10.p-b-10.p-t-10 (if mobile? "Aby" "Ability")]
            [:th.p-l-10.p-b-10.p-t-10 "DC"]
            [:th.p-l-10
             {:class-name (if (not mobile?) "p-b-10 p-t-10")}
             "Mod."]]
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
                  (if (char/spell-prepared? {:hide-unprepared? hide-unprepared?
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

(def finish-long-rest-handler (memoize finish-long-rest-fn))

(defn finish-long-rest-button [id]
  [:button.form-button.p-5
   {:on-click (finish-long-rest-handler id)}
   "finish long rest"])

(defn spells-known-section [id spells-known spell-slots spell-modifiers spell-slot-factors total-spellcaster-levels levels]
  (let [mobile? @(subscribe [:mobile?])
        multiclass? (> (count spell-slot-factors) 1)
        prepares-spells @(subscribe [::char/prepares-spells id])
        pact-magic? @(subscribe [::char/pact-magic? id])
        prepare-spell-count-fn @(subscribe [::char/prepare-spell-count-fn id])]
    [display-section
     "Spells"
     "spell-book"
     [:div.m-t-20
      (if multiclass?
        [:div.m-b-20
         [spellcaster-levels-table spell-slot-factors total-spellcaster-levels levels mobile?]])
      (if (or pact-magic? spell-slot-factors)
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
     [[finish-long-rest-button id]]]))

(defn equipment-section [title icon-name equipment equipment-map]
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

(defn weapon-attack-description [{:keys [::weapon/ranged?] :as weapon} damage-modifier attack-modifier]
  (disp/attack-description (-> weapon
                               (assoc :attack-type (if ranged? :ranged :melee))
                               (assoc :damage-modifier damage-modifier)
                               (assoc :attack-modifier attack-modifier)
                               (dissoc :description))))

(defn weapon-attack-comp [weapon off-hand? weapon-attack-modifier weapon-damage-modifier]
  [attack-comp
   (str (weapon-name weapon) (if off-hand? " (off hand)"))
   (weapon-attack-description weapon
                              (weapon-damage-modifier weapon off-hand?)
                              (weapon-attack-modifier weapon))])

(defn attacks-section [id]
  (let [attacks @(subscribe [::char/attacks id])
        all-weapons-map @(subscribe [::mi/all-weapons-map])
        main-hand-weapon-kw @(subscribe [::char/main-hand-weapon id])
        main-hand-weapon (if main-hand-weapon-kw (all-weapons-map main-hand-weapon-kw))
        off-hand-weapon-kw @(subscribe [::char/off-hand-weapon id])
        weapon-attack-modifier @(subscribe [::char/best-weapon-attack-modifier-fn id])
        weapon-damage-modifier @(subscribe [::char/best-weapon-damage-modifier-fn id])
        off-hand-weapon (if off-hand-weapon-kw (all-weapons-map off-hand-weapon-kw))]
    (if (or (seq attacks)
            main-hand-weapon)
      (display-section
       "Attacks"
       "pointy-sword"
       [:div.f-s-14
        (if main-hand-weapon
          [:div [weapon-attack-comp main-hand-weapon false weapon-attack-modifier weapon-damage-modifier]])
        (if off-hand-weapon
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
    (display-section
      title
      icon-name
      [:div.f-s-14.l-h-19
       (doall
         (map
           (fn [{{:keys [units amount]} :frequency nm :name :as action}]
             ^{:key action}
             [:p.m-t-10
              [:span.f-w-600.i nm]
              [:span.f-w-n.m-l-10.wsp-prw (common/sentensize (disp/action-description action))]
              (when (and amount units)
                (actions-indicators id nm units amount))])
           (sort-by :name actions)))])))

(defn prof-name [prof-map prof-kw]
  (or (-> prof-kw prof-map :name) (common/kw-to-name prof-kw)))

(defn resistance-str [{:keys [value qualifier]}]
  (str (name value)
       (if qualifier (str " (" qualifier ")"))))

(def no-https-images "Sorry, we don't currently support images that start with https")

(defn default-image [race classes]
  (if (and (or (= "Human" race)
               (nil? race))
           (= :barbarian (first classes)))
    "/image/barbarian.png"))

(defn section-header-2 [title icon]
  [:div
   (if icon (svg-icon icon 24))
   [:div.f-s-18.f-w-b.m-b-5 title]])

(defn armor-class-section-2 [id]
  [:div
   [:div.p-10.flex.flex-column.align-items-c
    (section-header-2 "Armor Class" "checked-shield")
    [:div.f-s-24.f-w-b @(subscribe [::char/current-armor-class id])]]])

(defn basic-section [title icon v]
  [:div
   [:div.p-10.flex.flex-column.align-items-c
    (section-header-2 title icon)
    [:div.f-s-24.f-w-b v]]])

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
                     (vals levels))))))

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
                  [:span.m-l-5 @(subscribe [::char/max-hit-points id])]]))

(defn initiative-section-2 [id]
  (basic-section "Initiative" "sprint" (common/bonus-str @(subscribe [::char/initiative id]))))

(defn darkvision-section-2 [id]
  (basic-section "Darkvision" "night-vision" (str @(subscribe [::char/darkvision id]) " ft.")))

(defn critical-hits-section-2 [id]
  (let [crit-values-str @(subscribe [::char/crit-values-str id])]
    (basic-section "Critical Hits" nil crit-values-str)))

(defn number-of-attacks-section-2 [id]
  (basic-section "Number of Attacks" nil @(subscribe [::char/number-of-attacks id])))

(defn passive-perception-section-2 [id]
  (basic-section "Passive Perception" "awareness" @(subscribe [::char/passive-perception id])))

(defn proficiency-bonus-section-2 [id]
  (basic-section "Proficiency Bonus" nil (common/bonus-str @(subscribe [::char/proficiency-bonus id]))))

(defn skills-section-2 [id]
  (let [skill-profs (or @(subscribe [::char/skill-profs id]) #{})
        skill-bonuses @(subscribe [::char/skill-bonuses id])]
    [:div
     [proficiency-bonus-section-2 id]
     [passive-perception-section-2 id]
     [:div.p-10.flex.flex-column.align-items-c
      (section-header-2 "Skills" "juggler")
      [:table
       [:tbody
        (doall
         (map
          (fn [{skill-name :name skill-key :key icon :icon :as skill}]
            ^{:key skill-key}
            [:tr.t-a-l
             {:class-name (if (skill-profs skill-key) "f-w-b" "opacity-7")}
             [:td [:div
                   (svg-icon icon 18)
                   [:span.m-l-5 skill-name]]]
             [:td [:div.p-5 (common/bonus-str (skill-bonuses skill-key))]]])
          skills/skills))]]]]))

(defn ability-scores-section-2 [id]
  (let [abilities @(subscribe [::char/abilities id])
        ability-bonuses @(subscribe [::char/ability-bonuses id])
        theme @(subscribe [:theme])]
    [:div
     [:div.f-s-18.f-w-b "Ability Scores"]
     [:div.flex.justify-cont-s-a.m-t-10
      (doall
       (map
        (fn [k]
          ^{:key k}
          [:div
           (t/ability-icon k 24 theme)
           [:div
            [:span.f-s-20.uppercase (name k)]]
           [:div.f-s-24.f-w-b (abilities k)]
           [:div.f-s-12.opacity-5.m-b--2.m-t-2 "mod"]
           [:div.f-s-18 (common/bonus-str (ability-bonuses k))]])
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
            {:class-name (if (saving-throws k) "f-w-b" "opacity-7")}
            [:td [:div
                  (t/ability-icon k 18 theme)
                  [:span.m-l-5 (s/upper-case (name k))]]]
            [:td [:div.p-5 (common/bonus-str (save-bonuses k))]]])
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
       [:span (feet-str (+ (or unarmored-speed-bonus 0)
                      (if speed-with-armor
                        (speed-with-armor nil)
                        speed)))]
       (if (or unarmored-speed-bonus
               speed-with-armor)
         [:span.display-section-qualifier-text "(unarmored)"])]
      (if speed-with-armor
        [:div.f-s-18
         (doall
          (map
           (fn [[armor-kw _]]
             (let [armor (mi/all-armor-map armor-kw)
                   speed (speed-with-armor armor)]
               ^{:key armor-kw}
               [:div
                [:div
                 [:span (feet-str speed)]
                 [:span.display-section-qualifier-text (str "(" (:name armor) " armor)")]]]))
           (dissoc all-armor :shield)))]
        (if unarmored-speed-bonus
          [:div.f-s-18
           [:span
            [:span (feet-str speed)]
            [:span.display-section-qualifier-text "(armored)"]]]))
      (if (and swim-speed (pos? swim-speed))
        [:div.f-s-18 [:span (feet-str swim-speed)] [:span.display-section-qualifier-text "(swim)"]])
      (if (and flying-speed (pos? flying-speed))
        [:div.f-s-18 [:span (feet-str flying-speed)] [:span.display-section-qualifier-text "(fly)"]])]]))

(defn personality-section [title & descriptions]
  (if (and (seq descriptions)
           (some (complement s/blank?) descriptions))
    [:div.m-t-20.t-a-l
     [:div.f-w-b.f-s-18 title]
     [:div
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
             {:class-name "input"
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
              (if (not max-levels?)
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
                (if (and (not (js/isNaN x2))
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
              (if (not max-levels?)
                [:text.main-text-color {:x "165"
                                        :y "30"
                                        :fill "white"
                                        :font-size "8"}
                 (str "Level " (inc total-levels))])
              (if (not max-levels?)
                [:text.main-text-color {:x "165"
                                        :y "36"
                                        :fill "white"
                                        :font-size "6"}
                 next-level-xps])]]]
           (if (and (>= xps next-level-xps)
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
                              ::weapon/ranged?
                              ::weapon/melee?
                              ::weapon/range
                              ::weapon/two-handed?
                              ::weapon/finesse?
                              ::weapon/link
                              ::weapon/versatile
                              ::weapon/thrown]
                       :as weapon}
                      damage-modifier-fn]
  [:div.m-t-10.i
   (weapon-details-field "Type" (common/safe-name type))
   (weapon-details-field "Damage Type" (common/safe-name damage-type))
   (if magical-damage-bonus
     (weapon-details-field "Magical Damage Bonus" magical-damage-bonus))
   (if magical-attack-bonus
     (weapon-details-field "Magical Attack Bonus" magical-attack-bonus))
   (weapon-details-field "Melee/Ranged" (if melee? "melee" "ranged"))
   (if range
     (weapon-details-field "Range" (str (::weapon/min range) "/" (::weapon/max range) " ft.")))
   (weapon-details-field "Finesse?" (yes-no finesse?))
   (weapon-details-field "Two-handed?" (yes-no two-handed?))
   (weapon-details-field "Versatile" (if versatile
                                       (str (::weapon/damage-die-count versatile)
                                            "d"
                                            (::weapon/damage-die versatile)
                                            (common/mod-str (damage-modifier-fn weapon false))
                                            " damage")
                                       "no"))
   (if description
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
   [:div (str (if type (str (common/safe-name type) ", ")) "base AC " (+ magical-ac-bonus shield-magic-bonus base-ac (if shield 2 0)) (if stealth-disadvantage? ", stealth disadvantage"))]
   (if expanded?
     [:div
      [:div.m-t-10.i
       (if type
         (weapon-details-field "Type" (common/safe-name type)))
       (weapon-details-field "Base AC" base-ac)
       (if (not= magical-ac-bonus 0)
         (weapon-details-field "Magical AC Bonus" magical-ac-bonus))
       (if shield
         (weapon-details-field "Shield Base AC Bonus" 2))
       (if (and shield
                (not= shield-magic-bonus 0))
         (weapon-details-field "Shield Magical AC Bonus" shield-magic-bonus))
       (if max-dex-mod
         (weapon-details-field "Max DEX AC Bonus" max-dex-mod))
       (if min-str
         (weapon-details-field "Min Strength" min-str))
       (weapon-details-field "Stealth Disadvantage?" (yes-no stealth-disadvantage?))
       (if weight
         (weapon-details-field "Weight" (str weight " lbs.")))
       (if description
         [:div.m-t-10 (str "Armor: " description)])
       (if (:description shield)
         [:div.m-t-10 (str "Shield: " (:description shield))])]])])

(defn boolean-icon [v]
  [:i.fa {:class-name (if v "fa-check green" "fa-times red")}])

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
           [:tbody
            [:tr.f-w-b
             {:class-name (if mobile? "f-s-12")}
             [:th.p-10 "Name"]
             (if (not mobile?) [:th.p-10 "Proficient?"])
             [:th.p-10 "Details"]
             [:th]
             [:th.p-10 "AC"]]
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
                 [:tr.pointer
                  {:on-click (toggle-details-expanded-handler expanded-details k)}
                  [:td.p-10.f-w-b (str (or (::mi/name armor) (:name armor) "unarmored")
                                       (if shield (str " + " (:name shield))))]
                  (if (not mobile?)
                    [:td.p-10 (boolean-icon proficient?)])
                  [:td.p-10.w-100-p
                   [:div
                    (armor-details-section armor shield expanded?)]]
                  [:td
                   [:div.orange
                    (if (not mobile?)
                      [:span.underline (if expanded? "less" "more")])
                    [:i.fa.m-l-5
                     {:class-name (if expanded? "fa-caret-up" "fa-caret-down")}]]]
                  [:td.p-10.f-w-b.f-s-18 ac]])))]]]]))))

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
           [:tbody
            [:tr.f-w-b
             {:class-name (if mobile? "f-s-12")}
             [:th.p-10 "Name"]
             (if (not mobile?) [:th.p-10 "Proficient?"])
             [:th.p-10 "Details"]
             [:th]
             [:th.p-10 (if mobile? "Atk" [:div.w-40 "Attack Bonus"])]]
            (doall
             (map
              (fn [[weapon-key {:keys [equipped?]}]]
                (let [{:keys [name description ranged? ::weapon/type ::weapon/damage-die-count ::weapon/damage-die] :as weapon} (all-weapons-map weapon-key)
                      proficient? (if has-weapon-prof (has-weapon-prof weapon))
                      expanded? (@expanded-details weapon-key)
                      damage-modifier (weapon-damage-modifier weapon)]
                  (if (not= type :ammunition)
                    ^{:key weapon-key}
                   [:tr.pointer
                    {:on-click (toggle-details-expanded-handler expanded-details weapon-key)}
                    [:td.p-10.f-w-b (or (:name weapon)
                                        (::mi/name weapon))]
                    (if (not mobile?)
                      [:td.p-10 (boolean-icon proficient?)])
                    [:td.p-10.w-100-p
                     [:div
                      (weapon-attack-description weapon damage-modifier nil)]
                     (if expanded?
                       (weapon-details weapon weapon-damage-modifier))]
                    [:td
                     [:div.orange
                      (if (not mobile?)
                        [:span.underline (if expanded? "less" "more")])
                      [:i.fa.m-l-5
                       {:class-name (if expanded? "fa-caret-up" "fa-caret-down")}]]]
                    [:td.p-10.f-w-b.f-s-18 (common/bonus-str (weapon-attack-modifier weapon))]])))
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
           [:td.p-r-5
            [:div.orange
             (if (not mobile?)
               [:span.underline (if expanded? "less" "more")])
             [:i.fa.m-l-5
              {:class-name (if expanded? "fa-caret-up" "fa-caret-down")}]]]]
          (if expanded?
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
           [:tbody
            [:tr.f-w-b
             {:class-name (if mobile? "f-s-12")}
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
           [:tbody
            [:tr.f-w-b
             {:class-name (if mobile? "f-s-12")}
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
                  [:tr.pointer
                   #_{:on-click (toggle-details-expanded-handler expanded-details item-kw)}
                   [:td.p-10.f-w-b (or (:name item) item-name)]
                   [:td.p-10 (::char-equip/quantity item-cfg)]
                   [:td.p-10
                    [:div
                     [:div
                      (str (if cost
                             (str (:num cost)
                                  " "
                                  (common/safe-name (:type cost))
                                  ", "))
                           weight)]]]]))
              equipment-cfgs))]]]]))))


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
             {:class-name (if mobile? "f-s-12")}
             [:th.p-10 "Name"]
             [:td.p-10 (if mobile? "Prof?" "Proficient?")]
             (if skill-expertise
               [:th.p-10 "Expertise?"])
             [:th.p-10 (if (not mobile?) [:div.w-40 "Bonus"])]]
            (doall
             (map
              (fn [{:keys [key name]}]
                (let [proficient? (key skill-profs)
                      expertise? (key skill-expertise)]
                  ^{:key key}
                  [:tr
                   [:td.p-10.f-w-b name]
                   [:td.p-10 (boolean-icon proficient?)]
                   (if skill-expertise
                     [:td.p-10 (boolean-icon expertise?)])
                   [:td.p-10.f-s-18.f-w-b (common/bonus-str (key skill-bonuses))]]))
              skills/skills))]]]]))))

(defn tool-prof-details-section-2 []
  (let [expanded-details (r/atom {})]
    (fn [id]
      (let [tool-profs (or @(subscribe [::char/tool-profs id]) #{})
            tool-expertise @(subscribe [::char/tool-expertise id])
            tool-bonus-fn @(subscribe [::char/tool-bonus-fn id])
            device-type @(subscribe [:device-type])
            mobile? (= :mobile device-type)]
        (if (seq tool-profs)
          [:div
           [:div.flex.align-items-c
            (svg-icon "stone-crafting" 32)
            [:span.m-l-5.f-w-b.f-s-18 "Tools"]]
           [:div
            [:table.w-100-p.t-a-l.striped
             [:tbody
              [:tr.f-w-b
               {:class-name (if mobile? "f-s-12")}
               [:th.p-10 "Name"]
               [:td.p-10 (if mobile? "Prof?" "Proficient?")]
               (if tool-expertise
                 [:th.p-10 "Expertise?"])
               [:th.p-10 (if (not mobile?) [:div.w-40 "Bonus"])]]
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
                     (if tool-expertise
                       [:td.p-10 (boolean-icon expertise?)])
                     [:td.p-10.f-s-18.f-w-b (common/bonus-str (tool-bonus-fn kw))]]))
                tool-profs))]]]])))))


(defn proficiency-details [num-columns id]
  (let [ability-bonuses @(subscribe [::char/ability-bonuses id])
        language-map @(subscribe [::langs/language-map])]
    [:div.details-columns
     {:class-name (if (= 2 num-columns) "flex")}
     [:div.flex-grow-1.details-column-2
      {:class-name (if (= 2 num-columns) "w-50-p m-l-20")}
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
                      (let [{:keys [name]} (all-armor-map key)]
                        {:title name
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
                      (let [{:keys [name]} (all-armor-map key)]
                        {:title name
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
                    (let [{:keys [name]} (all-weapons-map key)]
                      {:title name
                       :value key}))
                  carried-weapons))
         :value main-hand-weapon-kw
         :on-change (wield-handler ::char/wield-main-hand-weapon id)}]
       (if (or (equipped? off-hand-weapon-kw)
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
                        (let [{:keys [name]} (all-weapons-map key)]
                          {:title name
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
     (if (or non-standard-crits?
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
      {:class-name (if (= 2 num-columns) "w-50-p m-l-20")}
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
        actions @(subscribe [::char/actions id])
        bonus-actions @(subscribe [::char/bonus-actions id])
        reactions @(subscribe [::char/reactions id])
        traits @(subscribe [::char/traits id])
        attacks @(subscribe [::char/attacks id])
        all-traits (concat actions bonus-actions reactions traits attacks)
        freqs (into #{} (map has-frequency-units? all-traits))]
    [:div.details-columns
     {:class-name (if (= 2 num-columns) "flex")}

     [:div.flex-grow-1.details-column-2
      {:class-name (if (= 2 num-columns) "w-50-p m-l-20")}
      [list-item-section "Damage Resistances" "surrounded-shield" resistances resistance-str]
      [list-item-section "Damage Vulnerabilities" nil damage-vulnerabilities resistance-str]
      [list-item-section "Damage Immunities" nil damage-immunities resistance-str]
      [list-item-section "Condition Immunities" nil condition-immunities resistance-str]
      [list-item-section "Immunities" nil immunities resistance-str]
      [:div.flex.justify-cont-end.align-items-c
       (if (or (freqs ::units/long-rest)
               (freqs ::units/rest))
         [finish-long-rest-button id])
       (if (or (freqs ::units/short-rest)
               (freqs ::units/rest))
         [:button.form-button.p-5.m-l-5
          {:on-click (make-event-handler ::char/finish-short-rest id)}
          "finish short rest"])
       (if (freqs ::units/round)
         [:button.form-button.p-5.m-l-5
          {:on-click (make-event-handler ::char/new-round id)}
          "new round"])
       (if (freqs ::units/turn)
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
     {:class-name (if (= 2 num-columns) "flex")}
     [:div.flex-grow-1.details-column-2
      {:class-name (if (= 2 num-columns) "w-50-p m-l-20")}
      (if (seq spells-known) [spells-known-section
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
    [other-equipment-section-2 id]]])

(defn details-tab [title icon device-type selected? on-select]
  [:div.b-b-2.f-w-b.pointer.p-10.hover-opacity-full
   {:class-name (if selected? "b-orange" "b-gray")
    :on-click on-select}
   [:div.hover-opacity-full
    {:class-name (if (not selected?) "opacity-5")}
    [:div (svg-icon icon 24)]
    (if (= device-type :desktop)
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
   (if (seq options)
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

(defn character-display []
  (let [show-selections? (r/atom false)]
    (fn [id show-summary? num-columns]
      (let [device-type @(subscribe [:device-type])
            selected-tab @(subscribe [::char/selected-display-tab])
            two-columns? (= 2 num-columns)
            tab (if selected-tab
                  selected-tab
                  (if two-columns?
                    "combat"
                    "summary"))]
        [:div.w-100-p
         [:div
          (if show-summary?
            [:div.f-s-24.f-w-600.m-b-16.m-l-20.text-shadow.flex
             [character-summary id true true]])
          [:div.flex.w-100-p
           (if two-columns?
             [:div.w-50-p
              [summary-details num-columns id]])
           [:div
            {:class-name (if two-columns? "w-50-p" "w-100-p")}
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
            [(-> tab details-tabs :view) num-columns id]]]
          [:div.p-10
           [:span.orange.underline.pointer
            {:on-click #(swap! show-selections? not)}
            [:span (if @show-selections?
                     "hide selections"
                     "show selections")]
            [:i.fa.m-l-5
             {:class-name (if @show-selections? "fa-caret-up" "fa-caret-down")}]]
           (if @show-selections?
             [character-selections id])]]]))))

(defn share-link [id]
  [:a.m-r-5.f-s-14
   {:href (str "mailto:?subject=My%20OrcPub%20Character%20"
               @(subscribe [::char/character-name id])
               "&body=https://"
               js/window.location.hostname
               (routes/path-for routes/dnd-e5-char-page-route :id id))}
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
                                   id (if (not (s/blank? value))
                                        (js/parseInt value))]
                               (if id
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
                     print-character-sheet?
                     print-spell-cards?
                     print-prepared-spells?
                     print-large-abilities?]
  #(let [export-fn (export-pdf built-char
                               id
                               {:print-character-sheet? print-character-sheet?
                                :print-spell-cards? print-spell-cards?
                                :print-prepared-spells? print-prepared-spells?
                                :print-large-abilities? print-large-abilities?})]
     (export-fn)
     (dispatch [::char/hide-options])))

(def export-pdf-handler (memoize export-pdf-fn))

(defn print-options [id built-char]
  (let [print-character-sheet? @(subscribe [::char/print-character-sheet?])
        print-spell-cards? @(subscribe [::char/print-spell-cards?])
        print-prepared-spells? @(subscribe [::char/print-prepared-spells?])
        print-large-abilities? @(subscribe [::char/print-large-abilities?])
        has-spells? (seq (char/spells-known built-char))]
    [:div.flex.justify-cont-end
     [:div.p-20
      [:div.f-s-24.f-w-b.m-b-10 "Print Options"]
      [:div.m-b-2
       [:div.flex
        [:div
         {:on-click (make-event-handler ::char/toggle-large-abilities-print)}
         [labeled-checkbox
          "Print Abilities Large (and Bonuses Small)"
          print-large-abilities?]]]]
      (if has-spells?
        [:div.m-b-2
         [:div.flex
          [:div
           {:on-click (make-event-handler ::char/toggle-spell-cards-print)}
           [labeled-checkbox
            "Print Spell Cards"
            print-spell-cards?]]]])
      (if has-spells?
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
      [:span.orange.underline.pointer.uppercase.f-s-12
       {:on-click (make-event-handler ::char/hide-options)}
       "Cancel"]
      [:button.form-button.p-10.m-l-5
       {:on-click (export-pdf-handler built-char
                                      id
                                      print-character-sheet?
                                      print-spell-cards?
                                      print-prepared-spells?
                                      print-large-abilities?)}
       "Print"]]]))

(defn make-print-handler [id built-char]
  #(dispatch
    [::char/show-options
     [print-options id built-char]]))

(defn character-page []
  (let [expanded? (r/atom false)]
    (fn [{:keys [id] :as arg}]
      (let [id (js/parseInt id)
            frame? (= "true" (get-in arg [:query "frame"]))
            _ (prn "FRAME?" frame?)
            {:keys [::entity/owner] :as character} @(subscribe [::char/character id])
            built-template (subs/built-template
                            @(subscribe [::char/template])
                            (subs/selected-plugin-options
                             character))
            built-character (subs/built-character character built-template)
            device-type @(subscribe [:device-type])
            username @(subscribe [:username])]
        [content-page
         (if (not frame?)
           "Character Page")
         (remove
          nil?
          [[share-link id]
           [character-page-fb-button id]
           [:div.m-l-5.hover-shadow.pointer
            {:on-click #(swap! expanded? not)}
            [:img.h-32 {:src "/image/world-anvil.jpeg"}]]
           (if (and username
                    owner
                    (= owner username))
             {:title "Edit"
              :icon "pencil"
              :on-click (make-event-handler :edit-character character)})
           {:title "Print"
            :icon "print"
            :on-click (make-print-handler id built-character)}
           (if (and username owner (not= owner username))
             [add-to-party-component id])])
         [:div.p-10.main-text-color
          (if @expanded?
            (let [url js/window.location.href]
              [:div.p-10.flex.justify-cont-end
               [:input.input.w-500.bg-white.black
                {:value (str url
                             (if (not (s/ends-with? url "?frame=true"))
                               "?frame=true"))}]]))
          [character-display id true (if (= :mobile device-type) 1 2)]]
         :frame? frame?]))))

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
          :on-click (make-event-handler ::mi/delete-custom-item item-key)})
       (if owner?
         {:title "Edit"
          :icon "pencil"
          :on-click (make-event-handler [::mi/edit-custom-item item])})])
     [:div.p-10.main-text-color
      [item-component item]]]))

(defn base-builder-field [name comp]
  [:div.field.main-text-color.m-t-0
   [:div.personality-label.f-s-18 name]
   comp])

(defn builder-field [el-type name value on-change attrs & [children]]
  (base-builder-field
   name
   [comps/input-field
    el-type
    value
    on-change
    attrs]))

(defn select-builder-field [name value on-change children]
  (base-builder-field
   name
   (cond-> [:select.builder-option.builder-option-dropdown
            [:option {:value :none}
             "None"]]
     children (concat children))))

(defn input-builder-field [name value on-change attrs]
  [builder-field :input name value on-change attrs])

(defn text-field [{:keys [value on-change]}]
  [comps/input-field
   :input
   value
   on-change
   {:class-name "input"}])

(defn textarea-field [{:keys [value on-change]}]
  [comps/input-field
   :textarea
   value
   on-change
   {:class-name "input"}])

(defn number-field [{:keys [value on-change]}]
  [comps/input-field
   :input
   value
   (fn [v]
     (on-change
      (if (re-matches #"\d+" v) (js/parseInt v))))
   {:class-name "input"
    :type :number}])

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
    (if attunement
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

(def make-arg-event-handler
  (memoize
   (fn [event-kw & [arg-fn]]
     #(dispatch [event-kw (if arg-fn (arg-fn %) %)]))))

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
            @(subscribe [::mi/custom-and-standard-weapons]))))])]]
     (if other?
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
          {:on-click (make-event-handler ::mi/toggle-item-ammunition?)}
          [labeled-checkbox "Ammunition?" @(subscribe [::mi/item-ammunition?])]]]
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
         (if versatile?
           [:div.m-l-10.m-t-10
            [labeled-dropdown
             "Versatile Damage Die Number"
             {:items (map
                      value-to-item
                      (range 1 10))
              :value @(subscribe [::mi/item-versatile-damage-die-count])
              :on-change (make-arg-event-handler ::mi/set-item-versatile-damage-die-count js/parseInt)}]])
         (if versatile?
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
         (if (= :ranged melee-ranged)
           [:div.m-l-10.m-t-10
            [:div.f-w-b.m-b-5 "Range Min"]
            [number-field
             {:value @(subscribe [::mi/item-range-min])
              :on-change (make-arg-event-handler ::mi/set-item-range-min)}]])
         (if (= :ranged melee-ranged)
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
            :on-change (make-arg-event-handler ::mi/set-item-damage-type)}]]]])]))


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
           [:div.w-100 (common/safe-capitalize type-kw)]
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
           (if (not= :equals-walking-speed speed-mod-type)
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
    (if (= type :weapon)
      [:div
       [:div.f-w-b.m-b-5 "Magical Damage Bonus"]
       [number-field
        {:value magical-damage-bonus
         :on-change #(dispatch [::mi/set-item-damage-bonus %])}]])
    (if (= type :weapon)
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

(defn builder-input-field [title prop item prop-event & [class-names type]]
  [:div.flex-grow-1
   {:class-name class-names
    :name prop}
   [input-builder-field
    [:span.f-w-b title]
    (prop item)
    #(dispatch [prop-event prop %])
    {:class-name "input h-40"
     :type type}]])

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

(def option-language-proficiency-choice
  (partial option-proficiency-choice
           "Language Proficiency Choice"
           :language-options
           @(subscribe [::langs/languages])))

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
     [:div.f-s-18.f-w-b.m-b-20 "Languages"]
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
     {:class-name "input h-40"
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
           (if (seq non-save)
             (str "= \"Increase your "
                  (common/list-print
                   (map
                    (comp :name opt/abilities-map)
                    non-save)
                   "or")
                  " score by 1, to a maximum of 20."
                  (if (increases :saves?)
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
            (str "You gain proficiency with " (name armor-type) (if (not= armor-type :shields) " armor"))
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
            (str "You gain proficiency with " (name armor-type) (if (not= armor-type :shields) " armor"))
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
   [:div.f-s-18.f-w-b.m-b-10 "Damage Resistance"]
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
   [:div.f-s-18.f-w-b.m-b-10 "Damage Immunity"]
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
   [:div.f-s-18.f-w-b.m-b-10 "Damage Vulnerability"]
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
   [:div.f-s-18.f-w-b.m-b-10 "Condition Immunity"]
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
   [:div.f-s-18.f-w-b.m-b-10 "Weapon Proficiency"]
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
              {:class-name "input h-40"}]]
            (if types
              [:div.flex-grow-1.m-l-5
               [labeled-dropdown
                "Type"
                {:items types
                 :value type
                 :on-change #(dispatch [edit-trait-type-event i (keyword %)])}]])
            (if edit-trait-level-event
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

(defn subrace-damage-resistance [subrace]
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

(defn feat-builder []
  (let [feat @(subscribe [::feats/builder-item])]
    [:div.p-20.main-text-color
     [:div.m-b-20.flex.flex-wrap
      [feat-input-field
       "Name"
       :name
       feat]
      [feat-input-field
       option-source-name-label
       :option-pack
       feat
       "m-l-5 m-b-20"]
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

(defn selection-selector [index selection-cfg value-change-event]
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
     (if (:key selection-cfg)
       [:div
        [labeled-dropdown
         "Amount to Select"
         {:items (map
                  value-to-item
                  (range 1 11))
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
                 :values (concat
                          (map
                           (fn [type]
                             {:title (str "All " (name type))
                              :value type})
                           [:simple :martial])
                          (map
                           obj-to-item
                           @(subscribe [::mi/custom-and-standard-weapons])))}
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
        {:items (concat
                 [{:title "<select type to add>"
                   :disabled? true
                   :value :select}]
                 (map
                  obj-to-item
                  selections)
                 [{:title "<create new selection>"
                   :value :new-selection}])
         :value (or type :select)
         :on-change #(if (= "new-selection" %)
                       (dispatch [::selections/new-selection])
                       (dispatch [edit-selection-type-event index (keyword %)]))}]]
      (if type
        [:div.m-t-10.m-l-5
         [modifier-level-selector index level edit-selection-level-event]])
      (if type
        [:div.m-t-10.m-l-5
         [labeled-dropdown
          "Amount to Select at this Level"
          {:items (map
                   value-to-item
                   (range 1 11))
           :value (or num 1)
           :on-change #(dispatch [edit-selection-num-event index (js/parseInt %)])}]])
      (if (or type level num)
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
        {:keys [name values component value-fn]} (if type (mod-values type))]
    [:div
     [:div.flex.flex-wrap.align-items-end.m-b-20
      [:div.m-t-10
       [labeled-dropdown
        "Modifier Type"
        {:items (cons
                 {:title "<select type to add>"
                  :disabled? true
                  :value :select}
                 (map
                  (fn [[kw {:keys [name]}]]
                    {:title name
                     :value kw})
                  mod-values))
         :value (if type (clojure.core/name type) :select)
         :on-change #(dispatch [edit-modifier-type-event index (keyword %)])}]]
      (if type
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
        (if component
          [:div.m-l-5 [component index value edit-modifier-value-event]]))
      (if (or type level value)
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
         [:i.fa {:class-name (if @expanded? "fa-caret-up" "fa-caret-down")}]]]
       (if @expanded?
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
      [:div.m-b-20.flex-grow-1
       [class-input-field
        option-source-name-label
        :option-pack
        class
        "m-l-5 m-b-20"]]]
     [:div.m-b-20.flex.flex-wrap
      [:div.m-l-5.m-b-20.flex-grow-1
       [labeled-dropdown
        "Hit Die"
        {:items (map
                 (fn [sides]
                   {:title sides
                    :value sides})
                 [6 8 10 12])
         :value (:hit-die class)
         :on-change #(dispatch [::classes/set-class-prop :hit-die (js/parseInt %)])}]]
      [:div.m-l-5.m-b-20.flex-grow-1
       [labeled-dropdown
        "Pick Subclass at Level"
        {:items (map
                 (fn [level]
                   {:title level
                    :value level})
                 (range 1 4))
         :value (:subclass-level class)
         :on-change #(dispatch [::classes/set-class-prop :subclass-level (js/parseInt %)])}]]
      [:div.m-b-20.flex-grow-1
       [class-input-field
        "Subclass Title"
        :subclass-title
        class
        "m-l-5 m-b-20"]]]
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
       (let [asi-levels-set (into #{} (:ability-increase-levels class))]
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
                                  (if (= "true" %)
                                    {:level-factor 3
                                     :known-mode :schedule
                                     :ability ::char/cha
                                     :spells-known classes/third-caster-spells-known-schedule})])}]
         (if spellcaster?
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
                                     [:spellcasting :spell-list-kw] (if (not= "custom" %)
                                                                      (keyword %))])}]])
         (if spellcaster?
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
         (if spellcaster?
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
        (if (and spellcaster?
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
              (if cantrips?
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
             (if cantrips?
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
       :edit-trait-level-event ::e5/edit-class-trait-level]]]))

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
      [subclass-input-field
       option-source-name-label
       :option-pack
       subclass
       "m-l-5 m-b-20"]]
     (if (#{:fighter :rogue :warlock :cleric :paladin} class-key)
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
      :edit-trait-level-event ::e5/edit-subclass-trait-level]]))

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
   (if (or level value)
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
      [subrace-input-field
       option-source-name-label
       :option-pack
       subrace
       "m-l-5 m-b-20"]]
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
      ::e5/delete-subrace-trait]]))

(defn race-builder []
  (let [race @(subscribe [::races/builder-item])]
    [:div.p-20.main-text-color
     [:div.m-b-20.flex.flex-wrap
      [race-input-field
       "Name"
       :name
       race]
      [race-input-field
       option-source-name-label
       :option-pack
       race
       "m-l-5 m-b-20"]]
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
      ::e5/delete-race-trait]]))

(defn background-builder []
  (let [background @(subscribe [::bg/builder-item])]
    [:div.p-20.main-text-color
     [:div.m-b-20.flex.flex-wrap
      [background-input-field
       "Name"
       :name
       background]
      [background-input-field
       option-source-name-label
       :option-pack
       background
       "m-l-5 m-b-20"]]
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

(defn selection-builder []
  (let [selection @(subscribe [::selections/builder-item])]
    [:div.p-20.main-text-color
     [:div.flex.w-100-p.flex-wrap
      [selection-input-field
       "Name"
       :name
       selection
       "m-b-20"]
      [selection-input-field
       option-source-name-label
       :option-pack
       selection
       "m-l-5 m-b-20"]]
     [:div
      [:div.flex.justify-cont-s-b
       [:div.f-s-24.f-w-b "Options"]
       [:button.form-button
        {:on-click #(dispatch [::selections/add-option])}
        "Add Option"]]
      [:div
       (doall
        (map-indexed
         (fn [i {:keys [name description]}]
           ^{:key i}
           [:div.m-b-30
            [:div.flex.align-items-end.m-b-10
             [:div.f-w-b.f-s-24.m-r-10 (str (inc i) ".")]
             [:div.flex-grow-1
              [input-builder-field
               [:span.f-w-b "Name"]
               name
               #(dispatch [::selections/set-selection-path-prop [:options i :name] %])
               {:class-name "input h-40"}]]
             [:div
              [:button.form-button.m-l-5
               {:on-click #(dispatch [::selections/delete-option i])}
               "delete"]]]
            [:div.w-100-p
             [:div.f-w-b
              "Description"]
             [textarea-field
              {:value description
               :on-change #(dispatch [::selections/set-selection-path-prop [:options i :description] %])}]]])
         (:options selection)))]]]))

(defn language-builder []
  (let [language @(subscribe [::langs/builder-item])]
    [:div.p-20.main-text-color
     [:div.flex.w-100-p.flex-wrap
      [language-input-field
       "Name"
       :name
       language
       "m-b-20"]
      [language-input-field
       option-source-name-label
       :option-pack
       language
       "m-l-5 m-b-20"]]
     [:div.w-100-p
      [:div.f-s-24.f-w-b
       "Description"]
      [textarea-field
       {:value (get language :description)
        :on-change #(dispatch [::langs/set-language-prop :description %])}]]]))

(defn invocation-builder []
  (let [invocation @(subscribe [::classes/invocation-builder-item])]
    [:div.p-20.main-text-color
     [:div.flex.w-100-p.flex-wrap
      [invocation-input-field
       "Name"
       :name
       invocation
       "m-b-20"]
      [invocation-input-field
       option-source-name-label
       :option-pack
       invocation
       "m-l-5 m-b-20"]]
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
       "m-b-20"]
      [monster-input-field
       option-source-name-label
       :option-pack
       monster
       "m-l-5 m-b-20"]]
     [:div.flex.w-100-p.flex-wrap
      [:div.flex-grow-1.m-b-20.m-l-5
       [labeled-dropdown
        "Challenge"
        {:items (map
                 (fn [v]
                   {:title (if (< 0 v 1)
                             (clojure.core/str "1/" (/ 1 v))
                             v)
                    :value v})
                 @(subscribe [::monsters/challenge-ratings]))
         :value (or challenge 0)
         :on-change #(dispatch [::monsters/set-monster-prop :challenge (js/parseFloat %)])}]]
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
       "m-l-5 m-b-20 flex-grow-1 notes"]]
     [:div.flex.w-100-p.flex-wrap
      [monster-input-field
       "Speed"
       :speed
       monster
       "m-l-5 m-b-20 flex-grow-1"]
      [monster-input-field
       "Senses"
       :senses
       monster
       "m-l-5 m-b-20 flex-grow-1 senses"]]
     [:div.m-b-20
      [:div.f-s-24.f-w-b "Hit Points"]
      [:div.flex.w-100-p.flex-wrap
       [:div.m-r-5
        [labeled-dropdown
         "Die Count"
         {:items (cons
                  {:title "-"}
                  (map
                   value-to-item
                   (range 1 36)))
          :value (get hit-points :die-count)
          :on-change #(let [v (js/parseInt %)] (dispatch [::monsters/set-monster-path-prop [:hit-points :die-count] (if (not (js/isNaN v)) v)]))}]]
       [:div.m-r-5
        [labeled-dropdown
         "Die"
         {:items (cons
                  {:title "-"}
                  (map
                   value-to-item
                   dice/dice-sides))
          :value (get hit-points :die)
          :on-change #(let [v (js/parseInt %)]
                        (dispatch [::monsters/set-monster-path-prop [:hit-points :die] (if (not (js/isNaN v)) v)]))}]]
       [:div.m-r-5
        [input-builder-field
         [:span.f-w-b "Modifier"]
         (get hit-points :modifier 0)
         #(let [v (js/parseInt %)]
            (dispatch [::monsters/set-monster-path-prop [:hit-points :modifier] (if (not (js/isNaN v)) v)]))
         {:class-name "input h-40"}]]]]
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
                :on-change #(dispatch [::monsters/set-monster-path-prop [:saving-throws simple-kw] (let [parsed (js/parseInt %)] (if (not (js/isNaN parsed)) parsed))])}])])
         opt/abilities))]]
     [:div
      [:div.f-s-24.f-w-b "Skills"]
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
         skills/skills))]]
     [:div [option-languages monster ::monsters/toggle-monster-map-prop]]
     [:div [option-damage-resistance monster ::monsters/toggle-monster-map-prop]]
     [:div [option-damage-immunity monster ::monsters/toggle-monster-map-prop]]
     [:div [option-damage-vulnerability monster ::monsters/toggle-monster-map-prop]]
     [:div [option-condition-immunity monster ::monsters/toggle-monster-map-prop]]
     [:div.w-100-p
      [:div.f-s-24.f-w-b
       "Description"]
      [textarea-field
       {:value (get monster :description)
        :on-change #(dispatch [::monsters/set-monster-prop :description %])}]]
     [:div.w-100-p.m-t-30
      [:div.f-s-24.f-w-b
       "Legendary Actions Description"]
      [textarea-field
       {:value (get-in monster [:legendary-actions :description])
        :on-change #(dispatch [::monsters/set-monster-path-prop [:legendary-actions :description] %])}]]
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
                :value :legendary-action}]]]]))

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
     (if monster
       [:div.m-l-5.m-b-10
        [labeled-dropdown
         "Number"
         {:items (map
                  value-to-item
                  (range 1 21))
          :value (or num 1)
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
                 (fn [{:keys [::char/character-name
                              ::char/race-name
                              ::char/classes] :as character-summary}]
                   {:title (str character-name
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

(def rounds-per-minute 10)
(def minutes-per-hour 60)
(def hours-per-day 24)

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
    (if (seq remaining-conditions)
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
       (if type
         [:div.flex.flex-wrap
          [duration-selector "Hours" :hours 24 monster individual-index duration index]
          [duration-selector "Minutes" :minutes 60 monster individual-index duration index]
          [duration-selector "Rounds" :rounds 10 monster individual-index duration index]])
       (if deletable?
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
             (if (not mobile?)
               [:span.m-l-5
                "next initiative"])]
            [:button.form-button.m-l-5.m-b-10
             {:on-click #(dispatch [::combat/set-combat-prop :ordered? true])}
             [:i.fa.fa-arrow-down]
             (if (not mobile?)
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
                      (if (and current-initiative
                               (= current-initiative initiative))
                        [:i.fa.fa-play.f-s-24.m-r-10])
                      [input-builder-field
                       [:span.f-w-b.f-s-12 "Initiative"]
                       initiative
                       #(dispatch [::combat/set-combat-path-prop path (js/parseInt %)])
                       {:class-name "input h-40 w-80 f-s-24 f-w-b m-r-10 m-t-10 m-b-10"
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
                         [:div.f-w-b.f-s-24 (str "(" (or num 1) ")")]
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
                     [:i.fa {:class-name (if (get @expanded-rows path)
                                           "fa-caret-up"
                                           "fa-caret-down")}]]
                    (if (get @expanded-rows path)
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
                                   :class-name "input w-80"}]]
                                (let [current-round (dec (get tracker-item :round 1))
                                      conditions (get-in monster-data [(:key monster) x :conditions])
                                      used-conditions (into #{} (map :type) conditions)]
                                  [:div.m-t-10
                                   [:div.flex.w-100-p
                                    [:div.f-s-16.f-w-b
                                     w-160
                                     "Conditions"]
                                    (if (seq conditions)
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
      [encounter-input-field
       option-source-name-label
       :option-pack
       encounter
       "m-l-5 m-b-20"]]
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
      [spell-input-field
       option-source-name-label
       :option-pack
       spell
       "m-l-5 m-b-20"]]
     [:div.m-b-20
      [:div.f-w-b.m-b-10 "Add This Spell to Which Class Spell Lists?"]
      [:div.flex.flex-wrap
       (map
        (fn [{:keys [key name]}]
          ^{:key key}
          [:div.m-r-10.pointer.m-b-10
           {:on-click #(dispatch [::spells/toggle-spell-list key])}
           [comps/checkbox (get-in spell [:spell-lists key])]
           [:span.m-l-5 name]])
        @(subscribe [::spells/spellcasting-classes]))]]
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
                 spells/schools)
         :value school
         :on-change #(dispatch [::spells/set-spell-prop :school %])}]]]
     [:div.flex.flex-wrap
      [:div.m-r-20.m-b-10
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
        #(dispatch [::spells/toggle-spell-prop :attack-roll?])]]]
     [:div.flex.w-100-p.flex-wrap
      [spell-input-field "Casting Time" :casting-time spell "m-b-20"]
      [spell-input-field "Range" :range spell "m-l-5 m-b-20"]
      [spell-input-field "Duration" :duration spell "m-l-5 m-b-20"]]
     [:div [:h2.f-s-24.f-w-b.m-b-10 "Components"]]
     [:div.flex.w-100-p.flex-wrap
      [component-checkbox :verbal spell]
      [component-checkbox :somatic spell]
      [component-checkbox :material spell]]
     [:div.m-b-20
      [textarea-field
       {:value (get-in spell [:components :material-component])
        :on-change #(dispatch [::spells/set-material-component %])}]]
     [:div.w-100-p
      [:div.f-s-24.f-w-b
       "Description"]
      [textarea-field
       {:value (get spell :description)
        :on-change #(dispatch [::spells/set-spell-prop :description %])}]]]))

(defn item-builder []
  (let [{:keys [::mi/name ::mi/type ::mi/rarity ::mi/description ::mi/attunement] :as item}
        @(subscribe [::mi/builder-item])
        item-types @(subscribe [::mi/item-types])
        item-rarities @(subscribe [::mi/rarities])]
    [:div.p-20.main-text-color
     [:div.flex.w-100-p.flex-wrap
      [:div.flex-grow-1.m-b-20
       [input-builder-field
        "Item Name"
        name
        #(dispatch [::mi/set-item-name %])
        {:class-name "input h-40"}]]
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
     (if (= :armor type)
       [:div.m-b-40 [base-armor-selector]])
     (if (= :weapon type)
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
                                                        (str type-name (if (not= 1 num) "s")))]
                                  (str num " " (capitalize-words final-type-name)))]]
          [:div.orange.pointer
           [:i.fa.m-r-5
            {:class-name (if @expanded? "fa-caret-up" "fa-caret-down")}]
           [:span.underline (if @expanded? "collapse" "expand")]]]
         (if @expanded?
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
          {:class-name (if @expanded? "fa-caret-up" "fa-caret-down")}]
         [:span.pointer.underline (if @expanded? "collapse" "expand")]]]
       (if @expanded?
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
           [my-feats name plugin]
           [my-languages name plugin]
           [my-selections name plugin]]])])))

(defn my-content []
  [:div.main-text-color
   [:div.flex.justify-cont-end
    [:button.form-button.m-r-10.m-b-10
     {:on-click (make-event-handler ::e5/export-all-plugins)}
     "Export All"]]
   [:div.item-list
    (let [plugins (sort @(subscribe [::e5/plugins]))]
      (doall
       (map
        (fn [[name plugin]]
          ^{:key name}
          [my-content-item name plugin])
        plugins)))]])

(defn my-content-page []
  [content-page
   "My Content"
   []
   [:div
    [:div.p-20.bg-lighter.main-text-color.m-b-10.m-l-10.m-r-10.b-rad-5
     [:div.f-w-b.f-s-24.m-b-5 "Import Option Source"]
     [:input {:type "file"
              :accept ".orcbrew"
              :on-change import-file}]]
    [my-content]]])

(defn my-account-page []
  [content-page
   "My Account"
   [{:title (str "Delete Account")
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
     [:span @(subscribe [:email])]]]])

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
        {:class-name (if (not has-history?) "disabled")
         :on-click #(if has-history? (dispatch [::char/previous-question]))}
        "Back"]
       [:button.form-button
        {:on-click #(if current-answer (dispatch [::char/next-question]))
         :class-name (if (nil? current-answer) "disabled")}
        "Next"]]])
   :hide-header-message? true])

(defn builder-page [item-title reset-event save-event builder & [title]]
  [content-page
   (or title (str item-title " Builder"))
   [{:title (str "New " item-title)
     :icon "plus"
     :on-click #(dispatch [reset-event])}
    {:title "Save to Browser Storage"
     :icon "save"
     :on-click #(dispatch [save-event])}]
   [builder]])

(defn combat-tracker-page []
  [content-page
   "Combat Tracker"
   [{:title "Reset"
     :icon "undo"
     :on-click #(dispatch [::combat/reset-combat])}]
   [combat-tracker]])

(defn item-builder-page []
  (builder-page "Item" ::mi/reset-item ::mi/save-item item-builder))

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
  [:div
   {:style character-display-style}
   [:div.flex.justify-cont-end.uppercase.align-items-c
    [share-link id]
    [:div.m-r-5 [character-page-fb-button id]]
    (if (= username owner)
      [:button.form-button
       {:on-click (make-event-handler :edit-character @(subscribe [::char/character id]))}
       "edit"])
    (if (= username owner)
      [:button.form-button.m-l-5
       {:on-click (make-event-handler ::char/save-character id)}
       "save"])
    [:button.form-button.m-l-5
     {:on-click (make-event-handler :route char-page-route)}
     "view"]
    [:button.form-button.m-l-5
     {:on-click (export-pdf
                 @(subscribe [::char/built-character id])
                 id
                 {:print-character-sheet? true
                  :print-spell-cards? true
                  :print-prepared-spells? false})}
     "print"]
    (if (= username owner)
      [:button.form-button.m-l-5
       {:on-click (make-event-handler ::char/show-delete-confirmation id)}
       "delete"])]
   (if @(subscribe [::char/delete-confirmation-shown? id])
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
   [character-display id false (if (= :mobile @(subscribe [:device-type])) 1 2)]])

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
        (if (not= @(subscribe [:device-type]) :mobile) [:span.underline (if expanded?
                                                            "collapse"
                                                            "open")])
        [:i.fa.m-l-5
         {:class-name (if expanded? "fa-caret-up" "fa-caret-down")}]]]
      (if expanded?
        [expanded-character-list-item id owner username char-page-route])]]))

(defn orcacle-page []
  [content-page
   "Orcacle"
   []
   [orcacle]])

(defn character-list []
  (let [characters @(subscribe [::char/characters])
        expanded-characters @(subscribe [:expanded-characters])
        device-type @(subscribe [:device-type])
        username @(subscribe [:username])
        selected-ids @(subscribe [::char/selected])
        has-selected? @(subscribe [::char/has-selected?])]
    [content-page
     "Characters"
     [{:title "New"
       :icon "plus"
       :on-click #(dispatch [:new-character])}
      {:title "Make Party"
       :icon "users"
       :class-name (if (not has-selected?) "opacity-5 cursor-disabled")
       :on-click (if has-selected? (make-event-handler ::party/make-party selected-ids))}]
     [:div.p-5
      [:div
       (let [grouped-characters (group-by ::se/owner characters)
             user-characters (find grouped-characters username)
             other-characters (sort-by key (dissoc grouped-characters username))
             sorted-groups (if user-characters
                             (cons user-characters other-characters)
                             other-characters)]
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
                   ^{:key id}
                   [character-list-item
                    expanded-characters
                    selected-ids
                    id
                    owner
                    username
                    summary])
                 (sort-by ::char/character-name owner-characters)))]])
           sorted-groups)))]]]))

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
      (let [parties @(subscribe [::party/parties])
            device-type @(subscribe [:device-type])
            username @(subscribe [:username])]
        [content-page
         "Parties"
         []
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
                            (fn [{:keys [:db/id ::char/character-name]}]
                              {:name character-name
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
                       [:i.fa.fa-pencil.m-l-10.opacity-5.hover-opacity-full.pointer
                        {:on-click #(swap! editing-parties assoc id name)}]])]]
                  [:div.item-list
                   (doall
                    (map
                     (fn [{:keys [::se/owner] :as summary}]
                       (let [character-id (:db/id summary)
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
                             (if (not= device-type :mobile) [:span.underline (if expanded?
                                                                               "collapse"
                                                                               "open")])
                             [:i.fa.m-l-5
                              {:class-name (if expanded? "fa-caret-up" "fa-caret-down")}]]]
                           (if expanded?
                             [:div
                              {:style character-display-style}
                              [:div.flex.justify-cont-end.uppercase.align-items-c
                               (if (= username owner)
                                 [:button.form-button
                                  {:on-click #(dispatch [:edit-character @(subscribe [::char/character character-id])])}
                                  "edit"])
                               [:button.form-button.m-l-5
                                {:on-click #(dispatch [:route char-page-route])}
                                "view"]
                               [:button.form-button.m-l-5
                                {:on-click #(dispatch [::party/remove-character id character-id])}
                                "remove from party"]]
                              [character-display character-id false (if (= :mobile device-type) 1 2)]])]]))
                     characters))]]))
             parties))]]]))))

(defn monster-list-item [{:keys [name size type subtypes alignment key] :as monster}]
  (let [expanded? @(subscribe [:monster-expanded? name])
        device-type @(subscribe [:device-type])
        monster-page-path (routes/path-for routes/dnd-e5-monster-page-route :key key)
        monster-page-route (routes/match-route monster-page-path)
        homebrew? (:option-pack monster)]
    [:div.main-text-color.item-list-item
     [:div.pointer
      [:div.flex.justify-cont-s-b.align-items-c
       {:on-click #(dispatch [:toggle-monster-expanded name])}
       [:div.m-l-10
        [:div.f-s-24.f-w-600.p-b-20.p-t-20.flex
         (if homebrew?
           [:div.m-r-10 (svg-icon "beer-stein" 24 @(subscribe [:theme]))])
         [monster-summary name size type subtypes alignment]]]
       [:div.orange.pointer.m-r-10
        (if (not= device-type :mobile) [:span.underline (if expanded?
                                                          "collapse"
                                                          "open")])
        [:i.fa.m-l-5
         {:class-name (if expanded? "fa-caret-up" "fa-caret-down")}]]]
      (if expanded?
        [:div.p-10
         {:style character-display-style}
         [:div.flex.justify-cont-end.uppercase.align-items-c
          [:button.form-button.m-l-5
           {:on-click #(dispatch [:route monster-page-route])}
           "view"]
          (if homebrew?
            [:button.form-button.m-l-5
             {:on-click (make-event-handler ::monsters/edit-monster monster)}
             "edit"])
          (if homebrew?
            [:button.form-button.m-l-5
             {:on-click (make-event-handler ::monsters/delete-monster monster)}
             "delete"])]
         [monster-component monster]])]]))

(defn monster-list-items [expanded-monsters device-type]
  [:div.item-list
   (doall
    (map
     (fn [{:keys [name] :as monster}]
       ^{:key name}
       [monster-list-item monster])
     @(subscribe [::monsters/filtered-monsters])))])

(defn clear-monsters-filter []
  (dispatch [::char/filter-monsters ""]))

(def toggle-handler
  (memoize
   (fn [a]
     #(swap! a not))))

(defn monster-list []
  (let [filters-expanded? (r/atom false)]
    (fn []
      (let [expanded-monsters @(subscribe [:expanded-monsters])
            device-type @(subscribe [:device-type])]
        [content-page
         "Monsters"
         []
         [:div.p-l-5.p-r-5.p-b-10
          [:div.p-b-10.p-l-10.p-r-10
           [:div.posn-rel
            [:input.input.f-s-24.p-l-20.w-100-p.h-60
             {:value @(subscribe [::char/monster-text-filter])
              :on-change (make-arg-event-handler ::char/filter-monsters event-value)}]
            [:i.fa.fa-times.posn-abs.f-s-24.pointer.main-text-color
             {:style close-icon-style
              :on-click clear-monsters-filter}]]]
          [:div
           [:div.flex.justify-cont-end.m-b-10
            [:div.orange.pointer.m-r-10
             {:on-click (toggle-handler filters-expanded?)}
             (if (not= device-type :mobile)
               [:span.underline (if @filters-expanded?
                                  "hide"
                                  "filters")])
             [:i.fa.m-l-5
              {:class-name (if @filters-expanded? "fa-caret-up" "fa-caret-down")}]]]
           (if @filters-expanded?
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
                     (common/kw-to-name size)])
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
                     (common/kw-to-name type)])
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
                       (common/kw-to-name subtype)])
                    subtypes))]])])]
          [monster-list-items expanded-monsters device-type]]]))))

(defn spell-list-item [{:keys [name level school key] :as spell}]
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
         (if homebrew?
           [:div.m-r-10 (svg-icon "beer-stein" 24 @(subscribe [:theme]))])
         [spell-summary name level school true 12]]]
       [:div.orange.pointer.m-r-10
        (if (not= device-type :mobile) [:span.underline (if expanded?
                                                          "collapse"
                                                          "open")])
        [:i.fa.m-l-5
         {:class-name (if expanded? "fa-caret-up" "fa-caret-down")}]]]
      (if expanded?
        [:div.p-10
         {:style character-display-style}
         [:div.flex.justify-cont-end.uppercase.align-items-c
          [:button.form-button.m-l-5
           {:on-click (make-event-handler :route spell-page-route)}
           "view"]
          (if homebrew?
            [:button.form-button.m-l-5
             {:on-click (make-event-handler ::spells/edit-spell spell)}
             "edit"])
          (if homebrew?
            [:button.form-button.m-l-5
             {:on-click (make-event-handler ::spells/delete-spell spell)}
             "delete"])]
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
        (if (not= device-type :mobile) [:span.underline (if expanded?
                                                          "collapse"
                                                          "open")])
        [:i.fa.m-l-5
         {:class-name (if expanded? "fa-caret-up" "fa-caret-down")}]]]
      (if expanded?
        [:div.p-10
         {:style character-display-style}
         [:div.flex.justify-cont-end.uppercase.align-items-c
          [:button.form-button.m-l-5
           {:on-click (make-event-handler :route item-page-route)}
           "view"]
          (if (= username owner)
            [:button.form-button.m-l-5
             {:on-click (make-event-handler ::mi/edit-custom-item @(subscribe [::mi/custom-item id]))}
             "edit"])]
         [item-component item]])]]))

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

