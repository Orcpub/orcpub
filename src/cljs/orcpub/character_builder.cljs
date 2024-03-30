(ns orcpub.character-builder
  (:require [goog.dom :as gdom]
            [goog.string :as gs]
            [goog.labs.userAgent.device :as device]
            [cljs.pprint :as pprint]
            [cljs.reader :as reader]
            [clojure.string :as s]
            [clojure.set :as sets]

            [orcpub.common :as common]
            [orcpub.constants :as const]
            [orcpub.template :as t]
            [orcpub.entity :as entity]
            [orcpub.entity-spec :as es]
            [orcpub.dice :as dice]
            [orcpub.modifiers :as mod]
            [orcpub.components :as comps]
            [orcpub.views-aux :as views-aux]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.character.equipment :as char-equip5e]
            [orcpub.dnd.e5.modifiers :as mod5e]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.feats :as feats]
            [orcpub.dnd.e5.backgrounds :as backgrounds]
            [orcpub.dnd.e5.races :as races]
            [orcpub.dnd.e5.classes :as classes]
            [orcpub.dnd.e5.weapons :as weapon5e]
            [orcpub.dnd.e5.armor :as armor5e]
            [orcpub.dnd.e5.magic-items :as mi5e]
            [orcpub.dnd.e5.display :as disp5e]
            [orcpub.dnd.e5.equipment :as equip5e]
            [orcpub.dnd.e5.skills :as skill5e]
            [orcpub.dnd.e5.events :as events5e]
            [orcpub.dnd.e5.db :as db]
            [orcpub.dnd.e5.views :as views5e]
            [orcpub.route-map :as routes]
            [orcpub.pdf-spec :as pdf-spec]
            [orcpub.user-agent :as user-agent]

            [clojure.spec.alpha :as spec]
            [clojure.spec.test.alpha :as stest]
            [clojure.core.match :refer [match]]

            [reagent.core :as r]
            [re-frame.core :refer [subscribe dispatch dispatch-sync]]))
;console-print
(def print-disabled? true)

(def print-enabled? (and (not print-disabled?)
                         js/window.location
                         (s/starts-with? js/window.location.href "http://localhost")))

(defn stop-propagation [e]
  (.stopPropagation e))

(defn stop-prop-fn [func]
  (fn [e]
    (func e)
    (stop-propagation e)))

(defn selector-id [path]
  (s/join "--" (map name path)))

(defn realize-char [built-char]
  (reduce-kv
   (fn [m k v]
     (let [realized-value (es/entity-val built-char k)]
       (if (or (fn? realized-value)
               (and (sequential? realized-value)
                    (every? fn? realized-value)))
         m
         (assoc m k realized-value))))
   (sorted-map)
   built-char))

(defn print-char [built-char]
  (cljs.pprint/pprint
   (dissoc (realize-char built-char) ::es/deps)))

(defn get-template-from-props [x]
  (get (.-argv (.-props x)) 6))

(defn help-section [help]
  [:div.m-t-10.f-w-n
   (if (string? help)
     (doall
      (map-indexed
       (fn [i para]
         ^{:key i}
         [:p.m-b-5.m-b-0-last para])
       (s/split help #"\n")))
     help)])

(defn expand-button [expand-text collapse-text expanded?]
  [:span.flex.pointer.align-items-c.justify-cont-end
   {:on-click (fn [e]
                (swap! expanded? not)
                (stop-propagation e))}
   [:span.underline.orange.p-0.m-r-2 (if @expanded? expand-text collapse-text)]
   [:i.fa.orange
    {:class-name (if @expanded? "fa-caret-up" "fa-caret-down")}]])

(defn show-info-button [expanded?]
  [:div.f-w-n.m-l-5 [expand-button "hide info" "show info" expanded?]])

(defn multiselection? [{:keys [::t/multiselect? ::t/min ::t/max]}]
  (or multiselect?
      (and (> min 1)
           (= min max))
      (nil? max)))

(defn get-event-value [e]
  (.-value (.-target e)))

(defn character-state-path [path]
  (concat [:character] path))

(defn update-value-field-fn [prop-name]
  #(dispatch [:update-value-field prop-name %]))

(def update-value-field (memoize update-value-field-fn))

(defn character-field [entity-values prop-name type & [cls-str handler input-type]]
  [comps/input-field
   type
   (get entity-values prop-name)
   (update-value-field prop-name)
   {:type input-type
    :class-name (str "input w-100-p " cls-str)}])

(defn character-input [entity-values prop-name & [cls-str handler type]]
  [character-field entity-values prop-name :input cls-str handler type])


(defn character-textarea [entity-values prop-name & [cls-str]]
  [character-field entity-values prop-name :textarea cls-str])

(defn prereq-failures [option]
  (remove
   nil?
   (map
    (fn [{:keys [::t/prereq-fn ::t/label] :as prereq}]
      (if prereq-fn
        (if (not (prereq-fn))
          label)
        (js/console.warn "NO PREREQ_FN" (::t/name option) prereq)))
    (::t/prereqs option))))

(defn set-class-fn [i options-map]
  (fn [e] (let [new-key (keyword (.. e -target -value))]
            (dispatch [:set-class new-key i options-map]))))

(def set-class (memoize set-class-fn))

(def make-options-map
  (memoize
   (fn [options]
     (zipmap (map ::t/key options) options))))

(defn set-class-level-fn [i]
  (fn [e]
    (let [new-highest-level-str (.. e -target -value)
          new-highest-level (js/parseInt (last (s/split new-highest-level-str #"-")))]
      (dispatch [:set-class-level i new-highest-level]))))

(def set-class-level (memoize set-class-level-fn))

(defn delete-class-fn [key i options-map]
  (fn [_] (dispatch [:delete-class key i options-map])))

(def delete-class (memoize delete-class-fn))

(defn filter-classes-fn [key unselected-classes-set]
  #(or (= key (::t/key %))
       (unselected-classes-set (::t/key %))))

(def filter-classes (memoize filter-classes-fn))

(def levels-selection #(if (= :levels (::t/key %)) %))

(defn class-level-selector []
  (let [expanded? (r/atom false)]
    (fn [i key selected-class options unselected-classes-set]
      (let [options-map (make-options-map options)
            class-template-option (options-map key)
            path [:class-levels key]]
        [:div.m-b-5
         {:class-name (if @expanded? "b-1 b-rad-5 p-5")}
         [:div.flex.align-items-c
          [:select.builder-option.builder-option-dropdown.flex-grow-1.m-t-0
           {:value key
            :on-change (set-class i options-map)}
           (doall
            (map
             (fn [{:keys [::t/key ::t/name] :as option}]
               (let [failed-prereqs (if (pos? i) (prereq-failures option))]
                 ^{:key key}
                 [:option.builder-dropdown-item
                  {:value key
                   :disabled (seq failed-prereqs)}
                  (str name (if (seq failed-prereqs) (str " (" (s/join ", " failed-prereqs) ")")))]))
             (sort-by
              ::t/name
              (filter
               (filter-classes key unselected-classes-set)
               options))))]
          (if (::t/help class-template-option)
            [show-info-button expanded?])
          (let [levels-selection (some levels-selection (::t/selections class-template-option))
                available-levels (::t/options levels-selection)
                last-level-key (str "level-" (:class-level selected-class))]
            [:select.builder-option.builder-option-dropdown.m-t-0.m-l-5.w-100
             {:value last-level-key
              :on-change
              (set-class-level i)}
             (doall
              (map-indexed
               (fn [i {level-key ::t/key}]
                 ^{:key level-key}
                 [:option.builder-dropdown-item
                  {:value level-key}
                  (inc i)])
               available-levels))])
          [:i.fa.fa-minus-circle.orange.f-s-16.m-l-5.pointer
           {:on-click (delete-class key i options-map)}]]
         (if @expanded?
           [:div.m-t-5.m-b-10 (::t/help class-template-option)])]))))

(def select-template-key #(select-keys % [::t/key]))

(defn class-level-data [option]
  (let [levels (some
                (fn [s]
                  (if (= :levels (::t/key s))
                    s))
                (::t/selections option))]
    (assoc
     (select-keys option [::t/key ::t/prereqs ::t/name ::t/help ::t/associated-options])
     ::t/selections
     [{::t/key (::t/key levels)
       ::t/options (map select-template-key (::t/options levels))}])))

(def level-label-style
  {:margin-right "50px"})

(defn add-class-fn [remaining-classes]
  (fn [_]
    (let [first-unselected (::t/key (first remaining-classes))]
      (dispatch [:add-class first-unselected]))))

(def add-class (memoize add-class-fn))

(defn class-levels-selector [{:keys [selection]}]
  (let [options (::t/options selection)
        selected-classes @(subscribe [::char5e/levels])
        unselected-classes (remove
                            (set (keys selected-classes))
                            (map ::t/key options))
        unselected-classes-set (set unselected-classes)
        remaining-classes (filter
                           (fn [option]
                             (and
                              (unselected-classes-set (::t/key option))
                              (entity/meets-prereqs? option)))
                           options)]
    [:div
     [:div
      (doall
       (map-indexed
        (fn [i [key selected-class]]
          ^{:key key}
          [class-level-selector i key selected-class (map class-level-data options) unselected-classes-set])
        selected-classes))]
     (if (seq remaining-classes)
       [:div.orange.p-5.underline.pointer
        [:i.fa.fa-plus-circle.orange.f-s-16]
        [:span.m-l-5
         {:on-click
          (add-class remaining-classes)}
         "Add Levels in Another Class"]])]))

(defn set-custom-item-name-fn [selection-key i]
  #(dispatch [::char5e/set-custom-item-name selection-key i %]))

(def set-custom-item-name (memoize set-custom-item-name-fn))

(defn inventory-item []
  (let [expanded? (r/atom false)]
    (fn [{:keys [selection-key
                 item-key
                 item-name
                 item-qty
                 item-description
                 equipped?
                 user-created?
                 i
                 qty-input-width
                 check-fn
                 qty-change-fn
                 remove-fn]}]
      [:div.p-5
       [:div.f-w-b.flex.align-items-c
        [:div.pointer.m-l-5.m-r-5
         {:on-click check-fn}
         (comps/checkbox equipped? false)]
        (if user-created?
          [comps/input-field
           :input
           item-name
           (set-custom-item-name selection-key i)
           {:class-name "input m-t-0"}]
          [:div.flex-grow-1 item-name])
        (if item-description [:div.w-60 [show-info-button expanded?]])
        [comps/int-field
         item-qty
         qty-change-fn
         {:class-name (str "input m-l-5 m-t-0 w-" (or qty-input-width 60))}]
        [:i.fa.fa-minus-circle.orange.f-s-16.m-l-5.pointer
         {:on-click remove-fn}]]
       (if @expanded? [:div.m-t-5 item-description])])))

(defn add-inventory-item-fn [key]
  (fn [e]
     (let [value (.. e -target -value)
           item-key (keyword value)]
       (dispatch [:add-inventory-item key item-key]))))

(def add-inventory-item (memoize add-inventory-item-fn))

(defn inventory-option-selected-fn [selected-keys]
  #(or (selected-keys (::t/key %))
       (selected-keys (:db/id %))))

(def inventory-option-selected? (memoize inventory-option-selected-fn))

(defn name-and-key [{:keys [:db/id ::t/name ::t/key]}]
  {:name name
   :key (or key id)})

(defn inventory-adder [key options selected-keys]
  [comps/selection-adder
   (sort-by
    :name
    (sequence
     (comp
      (remove
       (inventory-option-selected? selected-keys))
      (map
       name-and-key))
     options))
   (add-inventory-item key)])

(defn inventory-check-fn [key i]
  #(dispatch [:toggle-inventory-item-equipped key i]))

(def inventory-check (memoize inventory-check-fn))

(defn inventory-qty-change-fn [key i]
  #(dispatch [:change-inventory-item-quantity key i %]))

(def inventory-qty-change (memoize inventory-qty-change-fn))

(defn remove-inventory-item-fn [key item-key]
  (fn [_]
    (dispatch [:remove-inventory-item key item-key])))

(def remove-inventory-item (memoize remove-inventory-item-fn))

(defn toggle-custom-inventory-item-equipped-fn [custom-equipment-key i]
  (fn [_]
    (dispatch [:toggle-custom-inventory-item-equipped custom-equipment-key i])))

(def toggle-custom-inventory-item-equipped (memoize toggle-custom-inventory-item-equipped-fn))

(defn change-custom-inventory-item-quantity-fn [custom-equipment-key i]
  (fn [qty]
    (dispatch [:change-custom-inventory-item-quantity custom-equipment-key i qty])))

(def change-custom-inventory-item-quantity (memoize change-custom-inventory-item-quantity-fn))

(defn remove-custom-inventory-item-fn [custom-equipment-key name]
  (fn [_]
    (dispatch [:remove-custom-inventory-item custom-equipment-key name])))

(def remove-custom-inventory-item (memoize remove-custom-inventory-item-fn))

(defn new-custom-item-fn [custom-equipment-key]
  #(dispatch [::char5e/new-custom-item custom-equipment-key]))

(def new-custom-item (memoize new-custom-item-fn))

(defn make-inventory-item-fn [key item-map qty-input-width]
  (fn [i {item-key ::entity/key
          {item-qty ::char-equip5e/quantity
           equipped? ::char-equip5e/equipped?
           item-name ::char-equip5e/name} ::entity/value}]
    (let [item (item-map item-key)
          final-name (or item-name (:name item) (::mi5e/name item))
          item-description (:description item)]
      ^{:key item-key}
      [inventory-item {:selection-key key
                       :item-key item-key
                       :item-name final-name
                       :item-qty item-qty
                       :item-description item-description
                       :equipped? equipped?
                       :i i
                       :qty-input-width qty-input-width
                       :check-fn (inventory-check key i)
                       :qty-change-fn (inventory-qty-change key i)
                       :remove-fn (remove-inventory-item key item-key)}])))

(def make-inventory-item (memoize make-inventory-item-fn))

(defn make-custom-inventory-item [custom-equipment-key qty-input-width]
  (fn [i {:keys [::char-equip5e/name
                 ::char-equip5e/quantity
                 ::char-equip5e/equipped?
                 ::char-equip5e/background-starting-equipment?
                 ::char-equip5e/class-starting-equipment?] :as item}]
    (let [item-key (common/name-to-kw name)]
      ^{:key i}
      [inventory-item {:selection-key custom-equipment-key
                       :item-key item-key
                       :item-name name
                       :item-qty quantity
                       :equipped? equipped?
                       :user-created? (not (or background-starting-equipment?
                                               class-starting-equipment?))
                       :i i
                       :qty-input-width qty-input-width
                       :check-fn (toggle-custom-inventory-item-equipped custom-equipment-key i)
                       :qty-change-fn (change-custom-inventory-item-quantity custom-equipment-key i)
                       :remove-fn (remove-custom-inventory-item custom-equipment-key name)}])))

(defn inventory-selector [item-map-sub qty-input-width {:keys [selection]} & [custom-equipment-key]]
  (let [{:keys [::t/key]} selection
        selected-items @(subscribe [:entity-option key])
        item-map @(subscribe item-map-sub)
        selected-keys (set (map ::entity/key selected-items))
        options (entity/selection-options selection)
        magic-weapons (= key :magic-weapons)]
    [:div
     [inventory-adder key options selected-keys]
     (if (seq selected-items)
       [:div.flex.f-s-12.opacity-5.m-t-10.justify-cont-s-b
        [:div.m-r-10 "Carried?"]
        [:div.m-r-30 "Quantity"]])
     [:div
      (doall
       (map-indexed
        (make-inventory-item key item-map qty-input-width)
        selected-items))]
     (if custom-equipment-key
       [:div
        [:div
         (doall
          (map-indexed
           (make-custom-inventory-item custom-equipment-key qty-input-width)
           @(subscribe [:entity-value custom-equipment-key])))]
        [:div.flex.justify-cont-end
         [:div.orange.pointer.m-t-5.m-r-5
          {:on-click (new-custom-item custom-equipment-key)}
          [:span.underline "Add Custom Item"]
          [:i.fa.fa-plus-circle.m-l-5.f-s-16]]]])]))

(defn option-selector-base []
  (let [expanded? (r/atom false)]
    (fn [{:keys [name key help selected? selectable? option-path select-fn content explanation-text icon classes multiselect? disable-checkbox? edit-event]}]
      [:div.p-10.b-1.b-rad-5.m-5.b-orange
       {:class-name (s/join " " (conj
                                 (remove nil? [(if selected? "b-w-5")
                                               (if selectable? "pointer hover-shadow")
                                               (if (not selectable?) "opacity-5")])
                                 classes))
        :on-click select-fn}
       [:div.flex.align-items-c
        [:div.flex-grow-1
         [:div.flex.align-items-c
          (if multiselect?
            [:span.m-r-5 (comps/checkbox selected? disable-checkbox?)])
          (if icon [:div.m-r-5 (views5e/svg-icon icon 24)])
          [:span.f-w-b.f-s-1.flex-grow-1 name]
          (if edit-event
            [:span.orange.underline.pointer
             {:on-click (apply views5e/make-stop-prop-event-handler edit-event)}
             "edit"])
          (if help
            [show-info-button expanded?])]
         (if (and help @expanded?)
           [help-section help])
         (if (and content selected?)
           content)
         (if explanation-text
           [:div.i.f-s-12.f-w-n 
            explanation-text])]]])))

(defn skill-help [name key ability icon description]
  [:div
   [:div.flex.align-items-c
    (views5e/svg-icon icon 48)
    [:div.f-s-18.f-w-b.m-l-5
     [:div name]
     [:div.i (str "(" (:name (opt5e/abilities-map ability)) ")")]]]
   [:p description]])

(defn remaining-indicator [remaining & [size font-size]]
  [:span.bg-red.t-a-c.p-t-4.b-rad-50-p.inline-block.f-w-b.white
   (let [size (or size 18)
         font-size (or font-size 14)]
     {:class-name (str "h-" size " w-" size " f-s-" font-size)})
   remaining])

(defn validate-selections [built-template character selections]
  (mapcat
   (fn [{:keys [::t/name ::t/tags] :as selection}]
     (if (not (get tags :starting-equipment))
       (let [remaining (entity/count-remaining built-template character selection)]
         (cond
           (pos? remaining) [(str "You have " remaining " more '" name "' selection" (if (> remaining 1) "s") " to make.")]
           (neg? remaining) [(str "You must remove " (Math/abs remaining) " '" name "' selection" (if (< remaining -1) "s") ".")]
           :else nil))))
   (entity/combine-selections selections)))


(defn new-option-selector [option-path
                           selection
                           disable-select-new?
                           homebrew?
                           option]
  (let [{:keys [help has-named-mods? modifiers-str failed-prereqs] :as data}
        (views-aux/option-selector-data option-path
                                        selection
                                        disable-select-new?
                                        homebrew?
                                        option)]
    (if (not-any? ::t/hide-if-fail? failed-prereqs)
      ^{:key (::t/key option)}
      [option-selector-base (assoc data
                                   :help
                                   (if (or help has-named-mods?)
                                        [:div
                                         (if has-named-mods? [:div.i modifiers-str])
                                         [:div {:class-name (if has-named-mods? "m-t-5")} help]])
                                   :edit-event (::t/edit-event option))])))

(defn selection-section-title [title]
  [:span.m-l-5.f-s-18.f-w-b.flex-grow-1 title])

(defn selection-section-parent-title [title]
  [:span.i.f-s-14.f-w-n.m-l-5.m-b-2 title])

(defn remaining-component [max remaining]
  [:div.m-l-10
   (cond
     (pos? remaining)
     [:div.flex.align-items-c
      (remaining-indicator remaining)
      [:span.i.m-l-5 "remaining"]]

     (or (zero? remaining)
         (and (nil? max)
              (neg? remaining)))
     [:div.flex.align-items-c
      [:span.bg-green.t-a-c.w-18.h-18.p-t-2.b-rad-50-p.inline-block.f-w-b
       [:i.fa.fa-check.f-s-12]]
      [:span.i.m-l-5 "complete"]]

     (neg? remaining)
     [:div.flex.align-items-c
      [:span.i.m-r-5 "remove"]
      [:span.bg-red.t-a-c.w-18.h-18.p-t-4.b-rad-50-p.inline-block.f-w-b (Math/abs remaining)]])])

(defn toggle-locked-fn [path]
  #(dispatch [:toggle-locked path]))

(def toggle-locked (memoize toggle-locked-fn))

(defn toggle-homebrew-fn [path]
  #(dispatch [:toggle-homebrew path]))

(def toggle-homebrew (memoize toggle-homebrew-fn))

(defn tooltip [text content]
  (if @(subscribe [:mobile?])
    content
    [:div.tooltip
     content
     [:span.tooltiptext text]]))

(defn selection-section-base []
  (let [expanded? (r/atom false)]
    (fn [{:keys [title path parent-title name icon help max min remaining body hide-lock? hide-homebrew?]}]
      (let [locked? @(subscribe [:locked path])
            homebrew? @(subscribe [:homebrew? path])]
        [:div.p-5.m-b-20.m-b-0-last
         (if (and (or title name) parent-title)
           (selection-section-parent-title parent-title))
         [:div.flex.align-items-c.w-100-p.justify-cont-s-b
          (if icon (views5e/svg-icon icon 24))
          (if (or title name)
            (selection-section-title (or title name))
            (if parent-title
              (selection-section-parent-title parent-title)))
          (if (and path help)
            [show-info-button expanded?])
          (if (not hide-lock?)
            [tooltip
             (if locked?
               "Locked to prevent changes - click to unlock"
               "Unlocked - click to lock the section to prevent changes")
             [:i.fa.f-s-16.m-l-10.m-r-5.pointer
              {:class-name (if locked? "fa-lock" "fa-unlock-alt opacity-5 hover-opacity-full")
               :on-click (toggle-locked path)}]])
          (if (not hide-homebrew?)
            [:span.pointer
             {:class-name (if (not homebrew?) "opacity-5 hover-opacity-full")
              :on-click (toggle-homebrew path)}
             [tooltip
              (if-not homebrew?
                (str "Homebrew is off for " title " - enabling this option allows you select options you would not normally have (turns on homebrew rules)")
                (str "Homebrew is on for " title " - you can select anything and make it homebrew"))
              (views5e/svg-icon "beer-stein" 18)]])]
         (if (and help path @expanded?)
           [help-section help])
         (if (int? min)
           [:div.p-5.f-s-16
            [:div.flex.align-items-c.justify-cont-s-b
             [:span.i.m-r-10 (str "select " (cond
                                              (= min max) min
                                              (zero? min) (if (nil? max)
                                                            "any number"
                                                            (str "up to " max))
                                              :else (str "at least " min)))]
             (remaining-component max remaining)]])
         body]))))

(defn selection-section-column [option-selectors]
  (doall
   (map-indexed
    (fn [i selector]
      ^{:key i}
      [:div selector])
    option-selectors)))

(defn item-adder [title click-fn]
  [:div.m-5.p-10.pointer.bg-lighter.b-rad-5
   {:on-click click-fn}
   [:i.fa.fa-plus]
   [:span.orange.underline.m-l-5 title]])

(defn cantrip-adder []
  (item-adder
   "Add Cantrip"
   #(dispatch [::spells/new-spell "Default Option Source" {:level 0}])))

(defn spell-adder []
  (item-adder
   "Add Spell"
   #(dispatch [::spells/new-spell "Default Option Source" {:level 1}])))

(defn subrace-adder [[_ race]]
  (item-adder
   "Add Subrace"
   #(dispatch [::races/new-subrace "Default Option Source" {:race race}])))

(defn race-adder []
  (item-adder
   "Add Race"
   #(dispatch [::races/new-race "Default Option Source"])))

(defn background-adder []
  (item-adder
   "Add Background"
   #(dispatch [::backgrounds/new-background "Default Option Source"])))

(defn feat-adder []
  (item-adder
   "Add Feat"
   #(dispatch [::feats/new-feat "Default Option Source"])))

(defn subclass-adder []
  (item-adder
   "Add Subclass"
   #(dispatch [::classes/new-subclass "Default Option Source"])))

(defn make-item-adder [{:keys [::entity/path ::t/adder-key-fn] :as s}]
  (let [path (if adder-key-fn (adder-key-fn s) path)]
    (cond
      (-> path last (= :feats)) [feat-adder]
      (-> path last name (s/ends-with? "cantrips-known")) [cantrip-adder]
      (-> path last name (s/ends-with? "spells-known")) [spell-adder]
      (-> path last name (s/ends-with? "-spells")) [spell-adder]
      (-> path last name (s/ends-with? "-any-school")) [spell-adder]
      :else (match path
              [:race _ :subrace] [subrace-adder path]
              [:race] [race-adder]
              [:background] [background-adder]
              [:subclass] [subclass-adder]
              :else nil))))

(defn default-selection-section-body [actual-path
                                      {:keys [::t/options] :as selection}
                                      disable-select-new?
                                      homebrew?
                                      num-columns]
  (let [option-selectors
        (remove
         nil?
         (map
          (fn [option]
            [new-option-selector
             actual-path
             selection
             disable-select-new?
             homebrew?
             option])
          (sort-by (juxt ::t/order ::t/name) options)))
        parts (partition-all
               (common/round-up (/ (count option-selectors)
                                   num-columns))
               option-selectors)
        item-adder (make-item-adder selection)]
    [:div.flex
     (doall
      (map-indexed
       (fn [i part]
         ^{:key i}
         [:div.flex-grow-1
          {:class-name (str "w-" (int (/ 100 num-columns)) "-p")}
          [:div
           (doall
            (map-indexed
             (fn [j selector]
               ^{:key j}
               [:div selector])
             part))]
          (if (and item-adder (= i (dec (count parts))))
            item-adder)])
       parts))]))

(defn selection-section [title
                         built-template
                         option-paths
                         ui-fns
                         selection
                         num-columns
                         remaining
                         & [hide-homebrew?]]
  (let [{:keys [path disable-selection-new homebrew?] :as data}
        (views-aux/selection-section-data
         title
         built-template
         option-paths
         ui-fns
         default-selection-section-body
         selection
         num-columns
         remaining
         hide-homebrew?)]
    [selection-section-base data]))

(defn set-abilities! [abilities]
  (dispatch [:set-abilities abilities]))

(defn reroll-abilities []
  (dispatch [:set-abilities (char5e/standard-ability-rolls)]))

(defn set-standard-abilities []
  (fn []
    (dispatch [:set-abilities (char5e/abilities 15 14 13 12 10 8)])))

(defn reset-point-buy-abilities []
  (fn []
    (dispatch [:set-abilities (char5e/abilities 8 8 8 8 8 8)])))

(defn swap-abilities [i other-i k v]
  (stop-prop-fn
   (fn []
     (dispatch [:swap-ability-values i other-i k v]))))

(defn ability-subtitle [title]
  [:div.t-a-c.f-s-10.opacity-5 title])


(defn ability-modifier [v]
  [:div.f-6-12.f-w-n.h-24
   (ability-subtitle "mod")
   [:div.m-t--1
    (opt5e/ability-bonus-str v)]])

(defn ability-component [k v i controls]
  [:div.t-a-c
   [:div.f-s-18.f-w-b v]
   controls])

(def score-costs
  {8 0
   9 1
   10 2
   11 3
   12 4
   13 5
   14 7
   15 9})

(def point-buy-points 27)

(defn ability-value [v]
  [:div.f-s-18.f-w-b v])

(defn ability-increases-component [built-template asi-selections ability-keys]
  (let [total-abilities @(subscribe [::char5e/abilities])
        character @(subscribe [:character])]
    [:div
     (doall
      (map-indexed
       (fn [i {:keys [::t/name ::t/key ::t/min ::t/max ::t/options ::t/different? ::entity/path] :as selection}]
         (let [increases-path (entity/get-entity-path built-template character path)
               selected-options (get-in character increases-path)
               ability-increases (frequencies (map ::entity/key selected-options))
               num-increased (apply + (vals ability-increases))
               num-remaining (if (or (nil? max)
                                     (<= min num-increased max))
                               0
                               (- min num-increased))
               allowed-abilities (set (map ::t/key options))
               ancestors-title (views-aux/ancestor-names-string built-template path)]
           ^{:key i}
           [:div
            [:div.flex.justify-cont-s-a
             (doall (for [i (range 6)] ^{:key i} [:div.m-t-10 "+"]))]
            [:div.flex.justify-cont-s-b.m-t-10.align-items-c
             [:div
              [:div.m-l-5.i (str "Improvement: " ancestors-title)]]
             (remaining-component 2 num-remaining)]
            [:div.flex.justify-cont-s-a.m-t-5
             (doall
              (map-indexed
               (fn [i k]
                 (let [ability-disabled? (not (allowed-abilities k))
                       increase-disabled? (or ability-disabled?
                                              (and (some? max)
                                                   (zero? (- max num-increased)))
                                              (and different? (pos? (ability-increases k)))
                                              (>= (total-abilities k) 20))
                       decrease-disabled? (or ability-disabled?
                                              (not (pos? (ability-increases k))))]
                   ^{:key k}
                   [:div.t-a-c
                    {:class-name (if ability-disabled? "opacity-5 cursor-disabled")}
                    [:div
                     {:class-name (if (and (not ability-disabled?)
                                           (zero? (ability-increases k 0)))
                                    "opacity-5")}
                     (ability-value (ability-increases k 0))] 
                    [:div.f-s-16
                     [:i.fa.fa-minus-circle.orange
                      {:class-name (if decrease-disabled? "opacity-5 cursor-disabled")
                       :on-click (stop-prop-fn
                                  (fn []
                                    (if (not decrease-disabled?)
                                      (dispatch [:decrease-ability-value increases-path k]))))}]
                     [:i.fa.fa-plus-circle.orange.m-l-5
                      {:class-name (if increase-disabled? "opacity-5 cursor-disabled")
                       :on-click (stop-prop-fn
                                  (fn []
                                    (if (not increase-disabled?)
                                      (dispatch [:increase-ability-value increases-path k]))))}]]]))
               ability-keys))]]))
       asi-selections))]))

(defn race-abilities-component [ability-keys]
  (let [race-ability-increases @(subscribe [::char5e/race-ability-increases])
        subrace-ability-increases @(subscribe [::char5e/subrace-ability-increases])
        ability-increases @(subscribe [::char5e/ability-increases])
        total-abilities @(subscribe [::char5e/abilities])]
    [:div.flex.justify-cont-s-a
     (doall
      (map-indexed
       (fn [i k]
         ^{:key k}
         [:div.t-a-c
          (if (seq race-ability-increases)
            [:div
             [:div.m-t-10.m-b-10 "+"]
             (ability-subtitle "race")
             (let [race-v (get race-ability-increases k 0)]
               [:div
                {:class-name (if (zero? race-v)
                               "opacity-5")}
                (ability-value race-v)])])
          (if (seq subrace-ability-increases)
            [:div
             [:div.m-t-10.m-b-10 "+"]
             (ability-subtitle "subrace")
             (let [subrace-v (get subrace-ability-increases k 0)]
               [:div
                {:class-name (if (zero? subrace-v)
                               "opacity-5")}
                (ability-value subrace-v)])])
          (if (seq ability-increases)
            [:div
             [:div.m-t-10.m-b-10 "+"]
             (ability-subtitle "other")
             (let [other-v (- (get ability-increases k 0)
                              (get race-ability-increases k 0)
                              (get subrace-ability-increases k 0))]
               [:div
                {:class-name (if (zero? other-v)
                               "opacity-5")}
                (ability-value other-v)])])])
       ability-keys))]))

(defn abilities-matrix-footer [ability-keys]
  (let [total-abilities @(subscribe [::char5e/abilities])]
    [:div
     (race-abilities-component ability-keys)
     [:div.flex.justify-cont-s-a
      (doall
       (map-indexed
        (fn [i k]
          ^{:key k}
          [:div.t-a-c
           [:div.m-t-10.m-b-10 "="]
           (ability-subtitle "total")
           [:div.f-s-24.f-w-b (total-abilities k)]
           (ability-modifier (total-abilities k))])
        ability-keys))]]))

(defn abilities-header [ability-keys]
  (let [theme @(subscribe [:theme])]
    [:div.flex.justify-cont-s-a
     (doall
      (map-indexed
       (fn [i k]
         ^{:key k}
         [:div.m-t-10.t-a-c
          (t5e/ability-icon k 24 theme)
          [:div.uppercase (name k)]
          (ability-subtitle "base")])
       ability-keys))]))

(defn abilities-component [built-template
                           asi-selections
                           content]
  (let [total-abilities @(subscribe [::char5e/abilities])]
    [:div
     (abilities-header char5e/ability-keys)
     content
     (ability-increases-component built-template asi-selections char5e/ability-keys)
     (abilities-matrix-footer char5e/ability-keys)]))


(defn point-buy-abilities [built-template asi-selections]
  (let [default-base-abilities (char5e/abilities 8 8 8 8 8 8)
        abilities (or @(subscribe [::char5e/ability-scores-option-value])
                      default-base-abilities)
        points-used (apply + (map (comp score-costs second) abilities))
        points-remaining (- point-buy-points points-used)
        total-abilities @(subscribe [::char5e/abilities])]
    (abilities-component
     built-template
     asi-selections
     [:div
      [:div.flex.justify-cont-s-a
       (for [i (range 6)]
         ^{:key i}
         [:div
          (ability-value 8)
          [:div.m-t-10 "+"]])]
      [:div.flex.justify-cont-s-b.m-t-10.align-items-c
       [:div.m-l-5 "Point Buys"]
       (remaining-component 27 points-remaining)]
      [:div.flex.justify-cont-s-a
       (doall
        (map
         (fn [k]
           (let [v (abilities k)
                 increase-disabled? (or (zero? points-remaining)
                                        (= 15 v)
                                        (>= (total-abilities k) 20)
                                        (> (- (score-costs (inc v))
                                              (score-costs v))
                                           points-remaining))
                 decrease-disabled? (or (<= v 8) (>= points-remaining point-buy-points))]
             ^{:key k}
             [:div.t-a-c
              (ability-subtitle "bought")
              (ability-value (- v 8))
              [:div.f-s-11.f-w-b (str "(" (score-costs v) " pts)")]
              [:div.f-s-16
               [:i.fa.fa-minus-circle.orange
                {:class-name (if decrease-disabled? "opacity-5 cursor-disabled")
                 :on-click (stop-prop-fn
                            (fn [e]
                              (if (not decrease-disabled?)
                                (set-abilities! (update abilities k dec)))))}]
               [:i.fa.fa-plus-circle.orange.m-l-5
                {:class-name (if increase-disabled? "opacity-5 cursor-disabled")
                 :on-click (stop-prop-fn
                            (fn [_]
                              (if (not increase-disabled?) (set-abilities! (update abilities k inc)))))}]]]))
         char5e/ability-keys))]])))

(defn abilities-standard []
  [:div.flex.justify-cont-s-a
   (let [abilities (or @(subscribe [::char5e/ability-scores-option-value])
                       (char5e/abilities 15 14 13 12 10 8))
          abilities-vec (map (fn [k] [k (abilities k)]) char5e/ability-keys)]
      (doall
       (map-indexed
        (fn [i [k v]]
          ^{:key k}
          [ability-component k v i
           [:div.f-s-16
            [:i.fa.fa-chevron-circle-left.orange
             {:on-click (swap-abilities i (dec i) k v)}]
            [:i.fa.fa-chevron-circle-right.orange.m-l-5
             {:on-click (swap-abilities i (inc i) k v)}]]])
        abilities-vec)))])

(defn abilities-roller [built-template asi-selections]
  (abilities-component
   built-template
   asi-selections
   [:div
    (abilities-standard)
    [:button.form-button.m-t-5
     {:on-click (stop-prop-fn
                 (fn [e]
                   (reroll-abilities)))}
     "Re-Roll"]]))

(defn abilities-standard-editor [built-template asi-selections]
  (abilities-component
   built-template
   asi-selections
   (abilities-standard)))

(def ability-input-style
  {:width "65px"})


(defn abilities-entry [built-template asi-selections]
  (let [abilities (or @(subscribe [::char5e/ability-scores-option-value])
                      (char5e/abilities 15 14 13 12 10 8))
        total-abilities @(subscribe [::char5e/abilities])]
    [:div
     (abilities-header char5e/ability-keys)
     [:div.flex.justify-cont-s-a
      (doall
       (map
        (fn [k]
          ^{:key k}
          [:div.p-1.flex-grow-1
           [:input.input.f-s-18.m-b-5.p-l-0.w-100-p
            {:value (k abilities)
             :type :number
             :on-change (fn [e] (let [value (.-value (.-target e))
                                      new-v (if (not (s/blank? value))
                                              (js/parseInt value))]
                                  (dispatch [:set-ability-score k new-v])))}]])
        char5e/ability-keys))]
     (ability-increases-component built-template asi-selections char5e/ability-keys)
     (race-abilities-component char5e/ability-keys)
     [:div.flex.justify-cont-s-a
      (doall
       (map
        (fn [[k v]]
          ^{:key k}
          [:div.t-a-c.p-1.flex-grow-1
           [:div.m-t-10.m-b-10 "="]
           [:div.f-w-b "total"]
           [:input.input.b-3.f-s-18.m-b-5.p-l-0.w-100-p
            {:value (if (abilities k)
                      (total-abilities k))
             :type :number
             :on-change (fn [e] (let [total (total-abilities k)                                     
                                      value (.-value (.-target e))
                                      diff (- total
                                              (abilities k))
                                      new-v (if (not (s/blank? value))
                                              (- (js/parseInt value) (or diff 0)))]
                                  (dispatch [:set-ability-score k new-v])))}]])
        total-abilities))]]))

(defn ability-variant-option-selector [name key selected-key content & [select-fn]]
  [option-selector-base
   {:name name
    :key key
    :selected? (= selected-key key)
    :selectable? true
    :option-path [:ability-scores key]
    :content content
    :select-fn (fn [_]
                 (when (not= selected-key key)
                   (if select-fn (select-fn))
                   (dispatch [:set-ability-score-variant key])))}])

(def point-buy-starting-abilities-fn #(set-abilities! (char5e/abilities 8 8 8 8 8 8)))

(def reroll-abilities-fn reroll-abilities)

(def standard-abilities-fn #(set-abilities! (char5e/abilities 15 14 13 12 10 8)))

(defn abilities-editor [{:keys [built-template option-paths selections]}]
  [:div
   (let [asi-or-feat-selections (filter
                                 (fn [s]
                                   (= :asi-or-feat (::t/key s)))
                                 selections)
         character @(subscribe [:character])]
     (if (seq asi-or-feat-selections)
       [:div
        [:div.m-l-5 (selection-section-title "Ability Score Improvements")]
        (doall
         (map-indexed
          (fn [i {:keys [::t/key ::t/min ::t/max ::t/options ::entity/path] :as selection}]
            (let [remaining (entity/count-remaining built-template character selection)]
              ^{:key i}
              [selection-section-base
               {:path path
                :parent-title (views-aux/ancestor-names-string built-template path)
                :max 1
                :min 1
                :remaining remaining
                :hide-lock? true
                :body (doall
                       (map
                        (fn [option]
                          ^{:key (::t/key option)}
                          (new-option-selector path
                                               selection
                                               (and max (> min 1) (zero? remaining))
                                               false
                                               option))
                        (sort-by ::t/name options)))}]))
          asi-or-feat-selections))]))
   (let [asi-selections (filter (fn [s] (= :asi (::t/key s))) selections)
         selected-variant @(subscribe [::char5e/ability-scores-option-key])]
     [selection-section-base
      {:path [:ability-scores]
       :name "Abilities Variant"
       :min 1
       :max 1
       :hide-homebrew? true
       :body [:div
              (ability-variant-option-selector
               "Point Buy"
               :point-buy
               selected-variant
               (point-buy-abilities built-template asi-selections)
               point-buy-starting-abilities-fn)
              (ability-variant-option-selector
               "Dice Roll"
               :standard-roll
               selected-variant
               (abilities-roller built-template asi-selections)
               reroll-abilities-fn)
              (ability-variant-option-selector
               "Standard Scores"
               :standard-scores
               selected-variant
               (abilities-standard-editor built-template asi-selections)
               standard-abilities-fn)
              (ability-variant-option-selector
               "Manual Entry"
               :manual-entry
               selected-variant
               (abilities-entry built-template asi-selections))]}])])


(defn skills-selector [{:keys [selection]}]
  (let [{:keys [::t/ref ::t/max ::t/options]} selection
        character @(subscribe [:character])
        path (concat [::entity/options] ref)
        selected-skills (get-in character path)
        selected-count (count selected-skills)
        remaining (- max selected-count)
        available-skills (set (map ::t/key options))
        selected-skill-keys (set (map ::entity/key selected-skills))]
    (doall
     (map
      (fn [{:keys [name key ability icon description]}]
        (let [skill-profs @(subscribe [::char5e/skill-profs])
              has-prof? (and skill-profs (skill-profs key))
              selected? (selected-skill-keys key)
              selectable? (available-skills key)
              bad-selection? (and selected? (not selectable?))
              homebrew? @(subscribe [:homebrew? ref])
              allow-select? (or homebrew?
                                selected?
                                (and (not selected?)
                                     (or (pos? remaining)
                                         (nil? max))
                                     selectable?
                                     (not has-prof?))
                                bad-selection?)]
          ^{:key key}
          [option-selector-base {:name name
                                 :key key
                                 :help (skill-help name key ability icon description)
                                 :selected? selected?
                                 :selectable? allow-select?
                                 :option-path [:skill-profs key]
                                 :select-fn (fn [_]
                                              (if allow-select?
                                                (dispatch [:select-skill path selected? key])))
                                 :explanation-text (if (and has-prof?
                                                            (not selected?))
                                                     "You already have this skill proficiency")
                                 :icon icon
                                 :classes (if bad-selection? "b-red")
                                 :multiselect? true}]))
      skill5e/skills))))


(def hit-points-headers
  [:tr.f-w-b.t-a-l
   [:th.p-5 "Level"]
   [:th.p-5 "Base"]
   [:th.p-5 "Con"]
   [:th.p-5 "Misc"]
   [:th.p-5 "Total"]
   [:th.p-5 ""]])

(defn hp-selection-name-level [selection]
  (let [[_ class-kw _ level-kw _] (::entity/path selection)
        class-name (common/safe-capitalize-kw class-kw)]
    {:name class-name
     :level (js/parseInt (last (s/split (name level-kw) #"-")))
     :key class-kw}))

(defn hit-points-entry [character selections built-template]
  (let [classes @(subscribe [::char5e/classes])
        levels @(subscribe [::char5e/levels])
        first-class (if levels (levels (first classes)))
        first-class-hit-die (:hit-die first-class)
        level-bonus @(subscribe [::char5e/hit-point-level-bonus])
        con-bonus (::char5e/con @(subscribe [::char5e/ability-bonuses]))
        con-bonus-str (common/bonus-str con-bonus)
        misc-bonus (- level-bonus con-bonus)
        misc-bonus-str (common/bonus-str misc-bonus)
        all-level-values (map
                          (fn [selection]
                            (let [name-level (hp-selection-name-level selection)
                                  value (get-in character (entity/get-option-value-path built-template character (::entity/path selection)))]
                              {:name (str (:name name-level) (:level name-level))
                               :level (:level name-level)
                               :class (:key name-level)
                               :class-name (:name name-level)
                               :value value
                               :path (::entity/path selection)}))
                          (sort-by (fn [s] ((juxt :name :level)
                                            (hp-selection-name-level s))) selections))
        total-base-hps (apply + (:hit-die first-class) (map :value all-level-values))
        total-con-bonus (* con-bonus (inc (count selections)))
        total-misc-bonus (* misc-bonus (inc (count selections)))
        total-level-bonus (+ total-con-bonus total-misc-bonus)
        by-class (group-by :class all-level-values)
        total-hps (+ total-con-bonus total-misc-bonus total-base-hps)
        class-hit-point-level-bonus @(subscribe [::char5e/class-hit-point-level-bonus])]
    [:div.m-t-5.p-5
     [:div.flex.align-items-c.justify-cont-s-b
      [:div.f-s-16.m-b-5
       [:span.f-w-b "Total:"]
       [:span.m-l-5
        (if (seq selections)
          [:input.input.w-70.b-3.f-w-b.f-s-16
           {:type :number
            :value total-hps
            :on-change (fn [e]
                         (let [value (js/parseInt (.. e -target -value))
                               total-value (if (js/isNaN value) 0 (- value first-class-hit-die total-level-bonus))
                               average-value (int (/ total-value (count selections)))
                               remainder (rem total-value (count selections))
                               first-selection (first selections)]
                           (doseq [selection selections]
                             (let [entity-path (entity/get-entity-path built-template character (::entity/path selection))]
                               (dispatch [:set-total-hps entity-path first-selection selection average-value remainder])))))}]
          total-hps)]]
      (if (seq selections)
        [:button.form-button.p-10
         {:on-click (fn [_]
                      (doseq [selection selections]
                        (let [[_ class-kw :as path] (::entity/path selection)]
                          (dispatch [:randomize-hit-points built-template path levels class-kw]))))}
         "Random"])
      (if (seq selections)
        [:button.form-button.p-10
         {:on-click (fn [_]
                      (doseq [selection selections]
                        (let [[_ class-kw :as path] (::entity/path selection)]
                          (dispatch [:set-hit-points-to-average built-template path levels class-kw]))))}
         "Average"])]
     (doall
      (map-indexed
       (fn [i cls]
         (let [level-values (by-class cls)
               total-base-hps (apply + (if (zero? i)
                                         (:hit-die first-class)
                                         0)
                                     (map :value level-values))
               num-level-values (count level-values)
               level-num (if (zero? i) (inc num-level-values) num-level-values)
               cls-level-bonus (get class-hit-point-level-bonus cls 0)
               total-con-bonus (* con-bonus level-num)
               total-misc-bonus (* (+ misc-bonus cls-level-bonus) level-num)]
           ^{:key i}
           [:div.m-b-20
            [:div.f-s-16.m-l-5.f-w-b
             (str (common/safe-capitalize-kw cls)
                  " ("
                  "D"
                  (-> levels cls :hit-die) ")")]
            [:table.w-100-p.striped
             [:tbody
              hit-points-headers
              (if (zero? i)
                [:tr
                 [:td.p-5 1]
                 [:td.p-5 first-class-hit-die]
                 [:td.p-5 con-bonus-str]
                 [:td.p-5 (common/bonus-str (+ misc-bonus cls-level-bonus))]
                 [:td.p-5 (+ (:hit-die first-class) level-bonus)]
                 [:td.p5]])
              (doall
               (map-indexed
                (fn [j level-value]
                  ^{:key (:name level-value)}
                  [:tr
                   [:td.p-5 (:level level-value)]
                   [:td.p-5 [:input.input.m-t-0
                             {:type :number
                              :class-name (if (or (nil? (:value level-value))
                                                  (not (pos? (:value level-value))))
                                            "b-red b-3")
                              :on-change (fn [e]
                                           (let [value (js/parseInt (.. e -target -value))]
                                             (dispatch [:set-level-hit-points built-template character level-value value])))
                              :value (:value level-value)}]]
                   [:td.p-5 con-bonus-str]
                   [:td.p-5 (common/bonus-str (+ misc-bonus cls-level-bonus))]
                   [:td.p-5 (+ (:value level-value) level-bonus)]
                   [:td.p-5 [:button.roll-button
                             {:on-click (fn [e]
                                          (let [value (js/parseInt (dice/die-roll (-> levels cls :hit-die)))]
                                            (dispatch [:set-level-hit-points built-template character level-value value])))}
                             "d" (-> levels cls :hit-die)]]])
                level-values))
              [:tr
               [:td.p-5 "Total"]
               [:td.p-5 total-base-hps]
               [:td.p-5 (common/bonus-str total-con-bonus)]
               [:td.p-5 (common/bonus-str total-misc-bonus)]
               [:td.p-5 (+ total-base-hps total-con-bonus total-misc-bonus)]
               [:td.p-5]]]]]))
       classes))
     (if (> (count classes) 1)
       [:div.m-t-20
        [:div.f-s-16.m-l-5.f-w-b "Total"]
        [:table.w-100-p.striped
         [:tbody
          hit-points-headers
          [:tr
           [:td.p-5 "Total"]
           [:td.p-5 total-base-hps]
           [:td.p-5 (common/bonus-str total-con-bonus)]
           [:td.p-5 (common/bonus-str total-misc-bonus)]
           [:td.p-5 total-hps]
           [:td.p-5]]]]])]))

(defn remaining-adjustments-fn [built-template character]
  #(Math/abs (entity/count-remaining built-template character %)))

(def remaining-adjustments (memoize remaining-adjustments-fn))

(defn sum-remaining [built-template character selections]
  (apply + (map (remaining-adjustments-fn built-template character) selections)))

(defn hit-points-editor [{:keys [character built-template option-paths selections]}]
  (let [num-selections (count selections)]
    (if @(subscribe [::char5e/levels])
      [selection-section-base
       {:name "Hit Points"
        :hide-lock? true
        :hide-homebrew? true
        :min (if (pos? num-selections) num-selections)
        :max (if (pos? num-selections) num-selections)
        :remaining (if (pos? num-selections) (sum-remaining built-template character selections)) 
        :body (hit-points-entry character selections built-template)}])))

(defn info-block [text]
  [:div.bg-light.b-rad-5.p-10.f-w-b.m-l-5.m-r-5.m-b-5.white
   text])

(defn known-mode-info []
  (let [spells-known-modes @(subscribe [::char5e/spells-known-modes])
        any-mode-class-names (into
                              []
                              (comp
                               (filter (fn [[_ mode]]
                                         (= mode :all)))
                               (map key)
                               (map (fn [nm] (str nm "s"))))
                              spells-known-modes)]
    (if (seq any-mode-class-names)
      (info-block
       (str "Except for cantrips, "
            (common/list-print any-mode-class-names)
            " do not need to select known spells since they can prepare any spell available in their class spell lists.")))))

#_(defn feats-editor [{:keys [built-template option-paths selections]}]
  [:div
   [:div.m-l-5
    [:div (selection-section-title "Feats")]
    [:div
     (doall
      (map
       (fn [i]
         [:div.p-10.b-1.b-rad-5.m-5
          [:div "Name"]
          [:div
           [comps/input-field
            :input.input
            ""
            (fn [])
            {}]]])
       (range (-> selections first ::t/min))))]]])

#_(defn more-selection-info [key name]
  (let [selected-plugin-options @(subscribe [:selected-plugin-options])
        unselected-plugins (remove
                            (comp selected-plugin-options :key)
                            t5e/plugins)]
    (if (some
         key
         unselected-plugins)
      (info-block (str "There are more " name " options available if you click 'select sources' above and add more sources.")))))

(def srd-prefix "The options here are the ones allowed by the SRD. ")

(defn add-item-component [type-name event]
  (info-block [:span
               [:span (str srd-prefix "Don't see a " type-name " here that you want to use? ")]
               [:div.m-t-5
                [:span.pointer.underline.orange
                 {:on-click #(dispatch [:route event])}
                 (str "CLICK HERE TO ADD A " (s/upper-case type-name))]]]))

(defn add-spell-component []
  (add-item-component "spell" routes/dnd-e5-spell-builder-page-route))

(defn add-background-component []
  (add-item-component "background" routes/dnd-e5-background-builder-page-route))

(defn add-race-component []
  (info-block [:span
               [:span (str srd-prefix "Don't see a race or subrace here that you want to use?")]
               [:div.m-t-5
                [:span.pointer.underline.orange
                 {:on-click #(dispatch [:route routes/dnd-e5-race-builder-page-route])}
                 (str "CLICK HERE TO ADD A RACE")]]
               [:div.m-t-5
                [:span.pointer.underline.orange
                 {:on-click #(dispatch [:route routes/dnd-e5-subrace-builder-page-route])}
                 (str "CLICK HERE TO ADD A SUBRACE")]]]))

(defn add-feat-component []
  (add-item-component "feat" routes/dnd-e5-feat-builder-page-route))

(defn add-subclass-component []
  (info-block [:span
               [:span (str srd-prefix "Don't see a class or subclass here that you want to use?")]
               [:div.m-t-5
                [:span.pointer.underline.orange
                 {:on-click #(dispatch [:route routes/dnd-e5-class-builder-page-route])}
                 (str "CLICK HERE TO ADD A CLASS")]]
               [:div.m-t-5
                [:span.pointer.underline.orange
                 {:on-click #(dispatch [:route routes/dnd-e5-subclass-builder-page-route])}
                 (str "CLICK HERE TO ADD A SUBCLASS")]]]))

(def pages
  [{:name "Race"
    :icon "woman-elf-face"
    :tags #{:race :subrace}
    :components [add-race-component]}
   {:name "Class / Level"
    :icon "mounted-knight"
    :tags #{:class :subclass}
    :ui-fns [{:key :class :title "Class / Level" :ui-fn class-levels-selector}
             {:key :hit-points :group? true :ui-fn hit-points-editor}]
    :components [add-subclass-component]}
   {:name "Ability Scores / Feats"
    :icon "strong"
    :tags #{:ability-scores :feats}
    :ui-fns [{:key :ability-scores :group? true :ui-fn abilities-editor}
             #_{:key :feats :group? true :ui-fn feats-editor}]
    :components [add-feat-component]}
   {:name "Background"
    :icon "ages"
    :tags #{:background}
    :components [add-background-component]}
   {:name "Proficiencies"
    :icon "juggler"
    :tags #{:profs}
    ;;:ui-fns [{:key :skill-proficiency :ui-fn skills-selector}]
    }
   {:name "Spells"
    :icon "spell-book"
    :tags #{:spells}
    :components [known-mode-info
                 add-spell-component]}
   {:name "Equipment"
    :icon "backpack"
    :tags #{:equipment :starting-equipment}
    :ui-fns (letfn [(select-selection [v] (select-keys v [:selection]))]
              [{:key :weapons
                :hide-homebrew? true
                :ui-fn (fn [v] [inventory-selector [::equip5e/weapons-map] 70 (select-selection v)])}
               {:key :magic-weapons
                :hide-homebrew? true
                :ui-fn (fn [v] [inventory-selector [::mi5e/magic-weapon-map] 70 (select-selection v)])}
               {:key :armor
                :hide-homebrew? true
                :ui-fn (fn [v] [inventory-selector [::equip5e/armor-map] 70 (select-selection v)])}
               {:key :magic-armor
                :hide-homebrew? true
                :ui-fn (fn [v] [inventory-selector [::mi5e/magic-armor-map] 70 (select-selection v)])}
               {:key :equipment
                :hide-homebrew? true
                :ui-fn (fn [v]
                         [inventory-selector [::equip5e/equipment-map] 70 (select-selection v) ::char5e/custom-equipment])}
               {:key :other-magic-items
                :hide-homebrew? true
                :ui-fn (fn [v] [inventory-selector [::mi5e/other-magic-items-map] 70 (select-selection v)])}
               {:key :treasure
                :hide-homebrew? true
                :ui-fn (fn [v] [inventory-selector [::equip5e/treasure-map] 100 (select-selection v) ::char5e/custom-treasure])}])}])

(defn section-tabs [available-selections built-template character page-index]
  (let [device-type @(subscribe [:device-type])]
    [:div.flex.justify-cont-s-a
     (doall
      (map-indexed
       (fn [i {:keys [name icon tags]}]
         (let [selections (entity/tagged-selections available-selections tags)
               combined-selections (entity/combine-selections selections)
               total-remaining (sum-remaining built-template character combined-selections)
               class-name (if (= i page-index) "selected-tab" "opacity-5 hover-opacity-full")]
           ^{:key name}
           [:div.p-5.hover-opacity-full.pointer.flex.flex-column.align-items-c.t-a-c
            {:class-name (if (= i page-index) "b-b-2 b-orange" "")
             :on-click (fn [_] (dispatch [:set-page i]))}
            [:div
             {:class-name class-name}
             (if (= :desktop device-type)
               [:div.f-s-10.m-b-2
                name])
             [:div.t-a-c
              (views5e/svg-icon icon 32)]]
            (if (not (zero? total-remaining))
              [:div.flex.justify-cont-end.m-t--10.p-l-20 (remaining-indicator total-remaining 12 11)])]))
       pages))]))

(defn matches-group-fn [key]
  (fn [{s-key ::t/key tags ::t/tags}]
    (let [v (or (= key s-key)
                (get tags key))]
      v)))

(defn matches-non-group-fn [key]
  (fn [{s-key ::t/key ref ::t/ref :as s}]
    (let [v (if (or (= s-key key)
                    (= ref [key]))
              s)]
      v)))

(defn option-sources []
  (let [expanded? (r/atom false)]
    (fn []
      [:div.m-b-20
       [:div.flex.align-items-c
        (views5e/svg-icon "bookshelf" 36)
        (selection-section-title "Option Sources")
        [expand-button "collapse" "select sources" expanded?]]
       (if @expanded?
         [:div
          [option-selector-base {:name "5e SRD"
                                 :help [:span
                                        "Base options are from the "
                                        t5e/srd-link]
                                 :selected? true
                                 :selectable? true
                                 :multiselect? true
                                 :disable-checkbox? true}]
          #_(doall
           (map
            (fn [option]
              (new-option-selector [(::t/key t5e/optional-content-selection)]
                                   t5e/optional-content-selection
                                   false
                                   false
                                   option))
            (::t/options t5e/optional-content-selection)))]
         [:div
          (doall
           (map-indexed
            (fn [i el]
              (with-meta el {:key i}))
            (interpose
             [:span.orange ", "]
             (map
              (fn [{:keys [name url]}]
                [:a.orange {:href url :target :_blank} name])
              [{:name "5e SRD"
                :url t5e/srd-url}]))))])])))

(def selection-order-title
  (juxt ::t/order ::t/name ::entity/path))

(defn compare-selections [s1 s2]
  (< (selection-order-title s1)
     (selection-order-title s2)))

(defn compare-paths [s1 s2]
  (< (::entity/path s1)
     (::entity/path s2)))

(defn sorted-selection-set [selections]
  (into (sorted-set-by compare-paths) selections))

(defn new-options-column [num-columns]
  (let [character @(subscribe [:character])
        built-template @(subscribe [:built-template])
        available-selections @(subscribe [:available-selections])
        _ (if print-enabled? (js/console.log "AVAILABLE SELECTIONS" available-selections))
        page @(subscribe [:page])
        page-index (or page 0)
        option-paths @(subscribe [:option-paths])
        {:keys [tags ui-fns components] :as page} (pages page-index)
        selections (entity/tagged-selections available-selections tags)
        combined-selections (entity/combine-selections selections)
        final-selections combined-selections]
    (if print-enabled? (js/console.log "FINAL SELECTIONS" final-selections))
    [:div.w-100-p
     [:div#options-column.b-1.b-rad-5
      [section-tabs available-selections built-template character page-index]
      [:div.flex.justify-cont-s-b.p-t-5.p-10.align-items-t
       [:button.form-button.p-5-10.m-r-5
        {:on-click
         (fn [_]
           (dispatch [:set-page (let [prev (dec page-index)]
                                  (if (neg? prev)
                                    (dec (count pages))
                                    prev))]))}
        "Back"]
       [:div.flex-grow-1
        [:h3.f-w-b.f-s-20.t-a-c (:name page)]]
       [:button.form-button.p-5-10.m-l-5
        {:on-click
         (fn [_]
           (dispatch [:set-page (let [next (inc page-index)]
                                  (if (>= next (count pages))
                                    0
                                    next))]))}
        "Next"]]
      (let [ui-fn-selections (mapcat
                              (fn [{:keys [key group? ui-fn]}]
                                (if group?
                                  (filter
                                   (matches-group-fn key)
                                   final-selections)
                                  [(some
                                    (matches-non-group-fn key)
                                    final-selections)]))
                              ui-fns)
            non-ui-fn-selections (sets/difference (sorted-selection-set final-selections)
                                                  (sorted-selection-set ui-fn-selections))]
        [:div.p-5
         [:div
          (doall
           (map-indexed
            (fn [i component-fn]
              ^{:key i}
              [component-fn])
            components))]
         [:div
          (doall
           (map
            (fn [{:keys [key group? ui-fn hide-homebrew? title]}]
              ^{:key key}
              [:div.m-t-20
               (if group?
                 (let [group (filter
                              (matches-group-fn key)
                              final-selections)]
                   (ui-fn {:character character
                           :built-template built-template
                           :option-paths option-paths
                           :selections group}))
                 (let [selection (some
                                  (matches-non-group-fn key)
                                  final-selections)
                       remaining (entity/count-remaining built-template character selection)]
                   (selection-section
                    (or title (::t/name selection))
                    built-template
                    option-paths
                    {key ui-fn}
                    selection
                    num-columns
                    remaining
                    hide-homebrew?)))])
            ui-fns))]
         (when (seq non-ui-fn-selections)
           [:div.m-t-20
            (let [sorted-selections (into (sorted-set-by compare-selections) non-ui-fn-selections)]
              (doall
               (map
                (fn [{:keys [::t/min ::t/max ::t/show-if-zero?] :as selection}]
                  (let [remaining (entity/count-remaining built-template character selection)]
                    (if (or (nil? max)
                            (pos? max)
                            (not (zero? remaining))
                            show-if-zero?)
                      ^{:key (::entity/path selection)}
                      [:div (selection-section
                             (::t/name selection)
                             built-template
                             option-paths
                             nil
                             selection
                             num-columns
                             remaining)])))
                sorted-selections)))])])]]))

(def image-style
  {:max-height "100px"
   :max-width "200px"})

(defn set-random-name []
  (dispatch [::char5e/set-random-name]))

(defn set-image-url [v]
  (dispatch [:set-image-url v]))

(defn set-faction-image-url [v]
  (dispatch [:set-faction-image-url v]))

(defn image-error-fn [event-key image-url]
  (dispatch [event-key image-url]))

(def image-error (memoize image-error-fn))

(defn image-loaded []
  (dispatch [:loaded-image]))

(defn faction-image-loaded []
  (dispatch [:loaded-faction-image]))

(defn description-fields []
  (let [entity-values @(subscribe [:entity-values])
        image-url @(subscribe [::char5e/image-url])
        image-url-failed @(subscribe [::char5e/image-url-failed])
        faction-image-url @(subscribe [::char5e/faction-image-url])
        faction-image-url-failed @(subscribe [::char5e/faction-image-url-failed])]
    [:div.flex-grow-1
     [:div.m-t-5
      [:span.personality-label.f-s-18 "Character Name"]
      [:div.flex.align-items-c
       [character-input entity-values ::char5e/character-name]
       [:button.form-button.p-10.m-t-5.m-l-5
        {:on-click set-random-name}
        [:i.fa.fa-random.main-text-color.f-s-16]]]]
     [:div.flex.justify-cont-s-b
      [:div.field.flex-grow-1.m-r-2
       [:span.personality-label.f-s-18 "Player Name"]
       [character-input entity-values ::char5e/player-name]]
      [:div.field.flex-grow-1.m-l-2
       [:span.personality-label.f-s-18 "Experience Points"]
       [character-input entity-values ::char5e/xps nil nil :number]]]
     [:div.flex.justify-cont-s-b
      [:div.field.flex-grow-1.m-r-2
       [:span.personality-label.f-s-18 "Age"]
       [character-input entity-values ::char5e/age]]
      [:div.field.flex-grow-1.m-l-2.m-r-2
       [:span.personality-label.f-s-18 "Sex"]
       [character-input entity-values ::char5e/sex]]
      [:div.field.flex-grow-1.m-l-2.m-r-2
       [:span.personality-label.f-s-18 "Height"]
       [character-input entity-values ::char5e/height]]
      [:div.field.flex-grow-1.m-l-2
       [:span.personality-label.f-s-18 "Weight"]
       [character-input entity-values ::char5e/weight]]]
     [:div.flex.justify-cont-s-b
      [:div.field.flex-grow-1.m-r-2
       [:span.personality-label.f-s-18 "Hair Color"]
       [character-input entity-values ::char5e/hair]]
      [:div.field.flex-grow-1.m-1-2.m-r-2
       [:span.personality-label.f-s-18 "Eye Color"]
       [character-input entity-values ::char5e/eyes]]
      [:div.field.flex-grow-1.m-1-2
       [:span.personality-label.f-s-18 "Skin Color"]
       [character-input entity-values ::char5e/skin]]]
     [:div.field
      [:span.personality-label.f-s-18 "Personality Trait 1"]
      [character-textarea entity-values ::char5e/personality-trait-1]]
     [:div.field
      [:span.personality-label.f-s-18 "Personality Trait 2"]
      [character-textarea entity-values ::char5e/personality-trait-2]]
     [:div.field
      [:span.personality-label.f-s-18 "Ideals"]
      [character-textarea entity-values ::char5e/ideals]]
     [:div.field
      [:span.personality-label.f-s-18 "Bonds"]
      [character-textarea entity-values ::char5e/bonds]]
     [:div.field
      [:span.personality-label.f-s-18 "Flaws"]
      [character-textarea entity-values ::char5e/flaws]]
     [:div.flex.align-items-c.w-100-p.m-t-30
      (if image-url
        [:img.m-r-10.image-character-thumbnail {:src image-url
                      :on-error (image-error :failed-loading-image image-url)
                      :on-load (if image-url-failed image-loaded)}])
      [:div.flex-grow-1
       [:span.personality-label.f-s-18 "Image URL"]
       [character-input entity-values ::char5e/image-url nil set-image-url]
       (if image-url-failed
         [:div.red.m-t-5 "Image failed to load, please check the URL"])]]
     [:div.field
      [:span.personality-label.f-s-18 "Faction Name"]
      [character-input entity-values ::char5e/faction-name]]
     [:div.flex.align-items-c.w-100-p.m-t-30
      (if faction-image-url
        [:img.m-r-10.image-faction-thumbnail {:src faction-image-url
                      :on-error (image-error :failed-loading-faction-image faction-image-url)
                      :on-load (if faction-image-url-failed
                                 faction-image-loaded)}])
      [:div.flex-grow-1
       [:span.personality-label.f-s-18 "Faction Image URL"]
       [character-input entity-values ::char5e/faction-image-url nil set-faction-image-url]
       (if faction-image-url-failed
         [:div.red.m-t-5 "Image failed to load, please check the URL"])]]
     [:div.field
      [:span.personality-label.f-s-18 "Description/Backstory"]
      [character-textarea entity-values ::char5e/description "h-800"]]]))

(defn set-builder-tab-fn [key]
  #(dispatch [::char5e/set-builder-tab key]))

(def set-builder-tab (memoize set-builder-tab-fn))

(defn builder-tab [title key current-tab]
  [:span.builder-tab
   {:class-name (if (= current-tab key) "selected-builder-tab")
    :on-click (set-builder-tab key)}
   [:span.builder-tab-text title]])

(defn mobile-columns []
  (let [current-tab (or @(subscribe [::char5e/builder-tab]) :options)]
    [:div.p-r-10.w-100-p
     [:div.flex-grow-1.flex.p-l-10.p-t-10
      [:div.w-100-p
       [:div.builder-tabs
        [builder-tab "Options" :options current-tab]
        [builder-tab "Description" :description current-tab]
        [builder-tab "Details" :details current-tab]]
       (case current-tab
         :options [new-options-column 1]
         :description [description-fields]
         [views5e/character-display nil true 1])]]]))


(defn desktop-or-tablet-columns [device-type]
  (let [current-tab (or @(subscribe [::char5e/builder-tab]) :options)]
    [:div.w-100-p
     [:div.flex-grow-1.flex.p-l-10.p-t-10
      [:div.w-50-p
       [:div.builder-tabs
        [builder-tab "Options" :options current-tab]
        [builder-tab "Description" :details current-tab]]
       (if (= current-tab :options)
         [new-options-column (if (= device-type :desktop) 2 1)]
         [description-fields])]
      [:div.w-50-p.m-l-20.m-r-10
       [views5e/character-display nil true 1]]]]))

(defn builder-columns []
  (let [device-type @(subscribe [:device-type])]
    (case device-type
      :mobile [mobile-columns]
      [desktop-or-tablet-columns device-type])))

(def patreon-link-props
  {:href "https://www.patreon.com/user?u=5892323" :target "_blank"})

(defn al-legality []
  (let [expanded? (r/atom false)]
    (fn [al-illegal-reasons used-resources]
      (let [num-resources (count (set (map :resource-key used-resources)))
            multiple-resources? (> num-resources 1)
            has-homebrew? @(subscribe [:has-homebrew?])
            mobile? @(subscribe [:mobile?])
            al-legal? (and (empty? al-illegal-reasons)
                           (not multiple-resources?)
                           (not has-homebrew?))]
        [:div
         {:class-name (if (not mobile?)
                        "m-l-20 m-b-20"
                        "m-l-10")}
         [:div.flex.align-items-c
          [:div.i
           {:class-name
            (if al-legal?
              "green"
              "red")}
           [:i.fa.f-s-18
            {:class-name
             (if al-legal?
               "fa-check"
               "fa-times")}]
           [:a.m-l-5.f-w-b
            {:href "https://media.wizards.com/2016/dnd/downloads/AL_PH_SKT.pdf" :target :_blank}
            (if mobile?
              "AL"
              (str "AL "
                   (if al-legal?
                     "Legal"
                     "Illegal")))]]
          (if (not al-legal?)
            [:span.m-l-10.f-s-14
             [expand-button "hide reasons" "show reasons" expanded?]])]
         (if (and @expanded?
                  (not al-legal?))
           [:div.i.red.m-t-5
            (map-indexed
             (fn [i reason]
               ^{:key i} [:div (str common/dot-char " " reason)])
             (cond-> al-illegal-reasons
               #_multiple-resources?
               #_(conj (str "You are only allowed to use content from one resource beyond the PHB, you are using "
                          num-resources
                          ": "
                          (s/join
                           ", "
                           (map
                            (fn [{:keys [resource-key option-name]}]
                              (str option-name " from " (:abbr (disp5e/sources resource-key))))
                            used-resources))))
               has-homebrew?
               (conj "Homebrew is not allowed")))])]))))



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

(def unsaved-button-style {:background "#9a031e"})

(defn confirm-handler [character-changed? cfg]
  (fn [_]
    (if character-changed?
      (dispatch [:show-confirmation cfg])
      (do
        (if (:pre cfg) ((:pre cfg)))
        (dispatch (:event cfg))))))

#_(def confirm-handler (memoize confirm-handler-fn))

(defn toggle-theme []
  (dispatch [:toggle-theme]))

(defn theme-toggle []
  (let [theme @(subscribe [:theme])]
    [:div.pointer
     {:on-click toggle-theme}
     [:span.m-r-5 (comps/checkbox (= "light-theme" theme) false)]
     [:span.main-text-color "Light Theme"]]))

(defn set-loading []
  (dispatch-sync [:set-loading true]))

(defn save-character []
  (dispatch [:save-character]))

(defn load-character-page-fn [id]
  (fn [_]
    (let [char-page-path (routes/path-for routes/dnd-e5-char-page-route :id id)
          char-page-route (routes/match-route char-page-path)]
      (dispatch [:route char-page-route]))))

(def load-character-page (memoize load-character-page-fn))

(defn character-builder []
  (let [character @(subscribe [:character])
        _  (if print-enabled? (cljs.pprint/pprint character))
        option-paths @(subscribe [:option-paths])
        built-template @(subscribe [:built-template])
        built-char @(subscribe [:built-character])
        active-tab @(subscribe [:active-tabs])
        view-width (.-width (gdom/getViewportSize js/window))
        plugins @(subscribe [:plugins])
        mobile? @(subscribe [:mobile?])
        all-selections (entity/available-selections character built-char built-template)
        selection-validation-messages (validate-selections built-template character all-selections)
        al-illegal-reasons (concat @(subscribe [::char5e/al-illegal-reasons])
                                   selection-validation-messages)
        used-resources @(subscribe [::char5e/used-resources])
        loading @(subscribe [:loading])
        locked-components @(subscribe [:locked-components])
        character-map @(subscribe [::char5e/character-map])
        character-id (:db/id character)
        saved-character (if (and character-id
                                 character-map)
                          (character-map character-id))
        character-changed? (if character-id
                             @(subscribe [::char5e/character-changed? character-id])
                             (not= db/default-character character))]
    (if print-enabled? (print-char built-char))
    (if (not character-changed?) (js/window.scrollTo 0,0)) ;//Force a scroll to top of page only if we are not editing.
    [views5e/content-page
     "Character Builder"
     (remove
      nil?
      [(if character-id [views5e/share-link character-id])
       {:title "Random"
        :icon "random"
        :on-click (confirm-handler
                   character-changed?
                   {:confirm-button-text "GENERATE RANDOM CHARACTER"
                    :question "You have unsaved changes, are you sure you want to discard them and generate a random character?"
                    :pre set-loading
                    :event [:random-character character built-template locked-components]})}
       {:title "New"
        :icon "plus"
        :on-click (confirm-handler
                   character-changed?
                   {:confirm-button-text "CREATE NEW CHARACTER"
                    :question "You have unsaved changes, are you sure you want to discard them and create a new character?"
                    :event [:reset-character]})}
       {:title "Clone"
        :icon "clone"
        :on-click (confirm-handler
                   character-changed?
                   {:confirm-button-text "CREATE CLONE"
                    :question "You have unsaved changes, are you sure you want to discard them and clone this character? The new character will have the unsaved changes, the original will not."
                    :event [::char5e/clone-character]})}
       {:title "Print"
        :icon "print"
        :on-click (views5e/make-print-handler (:db/id character) built-char)}
       {:title (if (:db/id character)
                 "Update Existing Character"
                 "Save New Character")
        :icon "save"
        :style (if character-changed? unsaved-button-style)
        :on-click save-character}
       (if (:db/id character)
         {:title "View"
          :icon "eye"
          :on-click (load-character-page (:db/id character))})])
     [:div
      [:div.container
       [:div.content
        [:div.flex.justify-cont-s-b.align-items-c.flex-wrap
         [:div
          [al-legality al-illegal-reasons used-resources]]
         [:div.flex
          [theme-toggle]
          (if character-changed? [:div.red.f-w-b.m-r-10.m-l-10.flex.align-items-c
                                  (views5e/svg-icon "thunder-skull" 24 24)
                                  (if (not mobile?)
                                    [:span "You have unsaved changes"])])]]]]
      [:div.flex.justify-cont-c.p-b-40
       [:div.f-s-14.main-text-color.content
        [:div.flex.w-100-p
         [builder-columns]]]]]]))
