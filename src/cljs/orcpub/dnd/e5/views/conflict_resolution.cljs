(ns orcpub.dnd.e5.views.conflict-resolution
  "Conflict resolution modal, export warning modal, and combined overlay.
   Handles import key conflicts and missing-field warnings during orcbrew export."
  (:require [re-frame.core :refer [subscribe dispatch]]
            [clojure.string :as s]
            [orcpub.dnd.e5.views.import-log :as import-log]))

(defn- radio-option
  "Styled radio option with color-coded left border for visual identity.
   variant is one of :rename, :keep, :skip — maps to CSS class."
  [selected? on-click label variant]
  [:div {:class (str "conflict-radio conflict-radio-" (name variant)
                     (when selected? " selected"))
         :on-click on-click}
   [:label.flex.align-items-c.pointer
    [:i {:class (str "fa radio-icon "
                     (if selected? "fa-dot-circle-o" "fa-circle-o"))}]
    label]])

(defn conflict-resolution-item
  "Renders a single conflict with resolution options."
  [{:keys [id type key content-type-name sources
           import-source import-name existing-source existing-name
           suggested-renames suggested-new-key] :as conflict}
   decision]
  (let [selected-action (:action decision)]
    [:div.conflict-item

     ;; Conflict description
     [:div.conflict-item-header
      [:span.f-w-b.f-s-14.conflict-item-key
       (str ":" (clojure.core/name key))]
      [:span.conflict-item-type
       (str "(" content-type-name ")")]]

     (if (= type :internal)
       ;; Internal conflict: same key in multiple sources within import
       [:div
        [:div.f-s-12.conflict-item-desc
         "This key appears in multiple sources within the import file:"]
        [:div.conflict-item-detail
         (for [{:keys [source name]} sources]
           ^{:key source}
           [:div.f-s-12.conflict-source-row
            [:strong.conflict-source-import source]
            (when name [:span.conflict-source-label (str " - " name)])])]]

       ;; External conflict: imported key conflicts with existing
       [:div
        [:div.f-s-12.conflict-item-desc
         "This key conflicts with existing content:"]
        [:div.f-s-12.conflict-item-detail
         [:div.conflict-source-row
          [:span.conflict-source-label "Import: "]
          [:strong.conflict-source-import import-name]
          [:span.conflict-source-origin (str " from " import-source)]]
         [:div.conflict-source-row
          [:span.conflict-source-label "Existing: "]
          [:strong.conflict-source-existing existing-name]
          [:span.conflict-source-origin (str " from " existing-source)]]]])

     ;; Resolution options
     [:div.conflict-options
      [:div.conflict-options-label "Choose resolution:"]

      ;; Option: Rename import
      [radio-option
       (= selected-action :rename-import)
       #(dispatch [:set-conflict-decision id
                   {:action :rename-import
                    :source (or import-source (-> sources first :source))
                    :new-key (or suggested-new-key
                                 (-> suggested-renames first :new-key))}])
       [:span
        [:span "Rename imported key to: "]
        [:code.conflict-code
         (str ":" (clojure.core/name (or suggested-new-key (-> suggested-renames first :new-key))))]]
       :rename]

      ;; Option: Keep both (override)
      [radio-option
       (= selected-action :keep-both)
       #(dispatch [:set-conflict-decision id {:action :keep-both}])
       [:span "Keep both (imported will override existing)"]
       :keep]

      ;; Option: Skip
      [radio-option
       (= selected-action :skip)
       #(dispatch [:set-conflict-decision id {:action :skip}])
       [:span "Skip this item (don't import)"]
       :skip]]]))

(defn conflict-resolution-modal []
  (let [resolution @(subscribe [:conflict-resolution])
        {:keys [active? import-name conflicts decisions]} resolution
        all-decided? (every? #(contains? decisions (:id %)) conflicts)]
    (when active?
      [:div.conflict-backdrop
       [:div.conflict-modal

        ;; Header
        [:div.conflict-modal-header
         [:div.flex.align-items-c
          [:i.fa.fa-exclamation-triangle.m-r-5.conflict-title-icon]
          [:span.f-s-18.f-w-b.conflict-title "Key Conflicts Detected"]]
         [:div.f-s-12.conflict-subtitle
          (str "Importing: " import-name)]
         [:div.f-s-12.conflict-count
          (str (count conflicts) " conflict(s) need resolution before import can continue.")]]

        ;; Conflict list
        [:div.conflict-modal-body
         (for [conflict conflicts]
           ^{:key (:id conflict)}
           [conflict-resolution-item conflict (get decisions (:id conflict))])]

        ;; Footer with buttons
        [:div.conflict-modal-footer
         [:span.link-button
          {:on-click #(dispatch [:cancel-conflict-resolution])}
          "Cancel Import"]
         [:button.form-button
          {:on-click #(dispatch [:rename-all-conflicts])}
          "Rename All"]
         [:button.form-button
          {:class (when-not all-decided? "disabled")
           :disabled (not all-decided?)
           :on-click #(when all-decided?
                       (dispatch [:apply-conflict-resolutions]))}
          (if all-decided?
            "Apply & Import"
            (str "Resolve All (" (count decisions) "/" (count conflicts) ")"))]]]])))

;; ============================================================================
;; Export Warning Modal — inline editing for missing required fields
;; ============================================================================

(def content-type-display-names
  {:orcpub.dnd.e5/classes "Classes"
   :orcpub.dnd.e5/subclasses "Subclasses"
   :orcpub.dnd.e5/races "Races"
   :orcpub.dnd.e5/subraces "Subraces"
   :orcpub.dnd.e5/backgrounds "Backgrounds"
   :orcpub.dnd.e5/feats "Feats"
   :orcpub.dnd.e5/spells "Spells"
   :orcpub.dnd.e5/monsters "Monsters"
   :orcpub.dnd.e5/invocations "Invocations"
   :orcpub.dnd.e5/languages "Languages"
   :orcpub.dnd.e5/selections "Selections"
   :orcpub.dnd.e5/encounters "Encounters"})

(def spell-schools
  ["abjuration" "conjuration" "divination" "enchantment"
   "evocation" "illusion" "necromancy" "transmutation"])

(def dropdown-fields #{:level :school})

(defn- field-editor
  "Renders the appropriate inline editor for a missing field."
  [edit-path field-key current-value plugin-data]
  (let [edits @(subscribe [:export-warning-edits])
        val (or (get edits edit-path) current-value "")]
    [:div.export-edit-row
     [:span.export-edit-label (clojure.core/name field-key)]
     (cond
       ;; Spell level: dropdown 0-9
       (= field-key :level)
       [:select.export-edit-select
        {:class (when-not (contains? edits edit-path) "unfilled")
         :value (str val)
         :on-change #(let [v (.. % -target -value)]
                       (if (= v "")
                         (dispatch [:remove-export-edit edit-path])
                         (dispatch [:update-export-edit
                                    edit-path
                                    (js/parseInt v)])))}
        [:option {:value ""} "—"]
        (for [n (range 10)]
          ^{:key n}
          [:option {:value (str n)} (if (zero? n) "Cantrip" (str n))])]

       ;; Spell school: dropdown
       (= field-key :school)
       [:select.export-edit-select
        {:class (when-not (contains? edits edit-path) "unfilled")
         :value (str val)
         :on-change #(let [v (.. % -target -value)]
                       (if (= v "")
                         (dispatch [:remove-export-edit edit-path])
                         (dispatch [:update-export-edit edit-path v])))}
        [:option {:value ""} "—"]
        (for [school spell-schools]
          ^{:key school}
          [:option {:value school} (s/capitalize school)])]

       ;; Default: text input (covers :name and anything else)
       :else
       [:input.export-edit-input
        {:type "text"
         :placeholder (str "Enter " (clojure.core/name field-key))
         :value val
         :on-change #(dispatch [:update-export-edit
                                edit-path
                                (.. % -target -value)])}])]))

(defn- item-issue-editor
  "Renders inline editors for a single item's missing fields + traits."
  [plugin-name content-type {:keys [key name missing-fields
                                    traits-missing-names traits-needing-names]}
   plugin-data]
  [:div {:style {:padding "8px 12px"
                 :border-bottom "1px solid rgba(255,255,255,0.1)"}}
   [:div.f-w-b {:style {:margin-bottom "4px"}}
    (or name (str ":" (clojure.core/name key)))]

   ;; Missing top-level fields (dropdowns first — they block export)
   (let [sorted-fields (concat (filter dropdown-fields missing-fields)
                               (remove dropdown-fields missing-fields))]
     (for [field sorted-fields]
       ^{:key field}
       [field-editor
        [plugin-name content-type key field]
        field
        (get-in plugin-data [content-type key field])
        plugin-data]))

   ;; Traits missing :name
   (when (and traits-needing-names (seq traits-needing-names))
     [:div {:style {:margin-top "4px" :padding-left "12px"
                    :border-left "2px solid rgba(255,255,255,0.1)"}}
      [:div {:style {:font-size "11px" :color "rgba(255,255,255,0.4)"
                     :margin-bottom "4px"}}
       (str traits-missing-names " trait(s) missing names:")]
      (for [{:keys [index]} traits-needing-names]
        ^{:key index}
        [field-editor
         [plugin-name content-type key :trait index :name]
         :name
         nil
         plugin-data])])])

(defn- plugin-issues-section
  "Renders all issues for one plugin, grouped by content type."
  [{:keys [name plugin issues]}]
  [:div
   (for [{:keys [content-type invalid-items]} issues]
     ^{:key content-type}
     [:div {:style {:margin-bottom "8px"}}
      [:div {:style {:font-size "12px" :font-weight "bold"
                     :color "#f0a100" :padding "4px 12px"}}
       (get content-type-display-names content-type
            (clojure.core/name content-type))
       (str " (" (count invalid-items) ")")]
      (for [item invalid-items]
        ^{:key (:key item)}
        [item-issue-editor name content-type item plugin])])])

(defn export-warning-modal []
  (let [warning @(subscribe [:export-warning])
        {:keys [active? mode plugins warnings show-export-as-is? edits]} warning
        multi? (= mode :multi)
        total-items (reduce + 0 (for [p plugins
                                      i (:issues p)]
                                  (count (:invalid-items i))))
        total-dropdowns (reduce + 0
                          (for [p plugins
                                {:keys [content-type invalid-items]} (:issues p)
                                {:keys [missing-fields]} invalid-items
                                field missing-fields
                                :when (dropdown-fields field)]
                            1))
        filled-dropdowns (reduce + 0
                           (for [p plugins
                                 {:keys [content-type invalid-items]} (:issues p)
                                 {:keys [key missing-fields]} invalid-items
                                 field missing-fields
                                 :when (dropdown-fields field)]
                             (if (contains? edits [(:name p) content-type key field]) 1 0)))
        all-filled? (= filled-dropdowns total-dropdowns)]
    (when active?
      [:div.conflict-backdrop
       [:div.conflict-modal

        ;; Header
        [:div.conflict-modal-header
         [:div.flex.align-items-c
          [:i.fa.fa-exclamation-triangle.m-r-5.conflict-title-icon]
          [:span.f-s-18.f-w-b.conflict-title "Missing Required Fields"]]
         [:div.f-s-12.conflict-subtitle
          (if multi?
            (str "Exporting: " (count plugins) " plugin"
                 (when (not= 1 (count plugins)) "s") " with issues")
            (str "Exporting: " (:name (first plugins))))]
         [:div.f-s-12.conflict-count
          (str total-items " item" (when (not= 1 total-items) "s")
               " need attention. Fix inline or export with auto-filled placeholders.")]]

        ;; Body — collapsible per-plugin for multi, flat for single
        [:div.conflict-modal-body {:style {:max-height "400px"}}
         (if multi?
           (for [{:keys [name] :as plugin-info} plugins]
             ^{:key name}
             [import-log/collapsible-section
              {:title name
               :icon "fa-cube"
               :icon-color "#f0a100"
               :bg-color "rgba(240, 161, 0, 0.08)"
               :border-color "#f0a100"
               :default-expanded? (<= (count plugins) 3)}
              [plugin-issues-section plugin-info]])
           ;; Single plugin — render flat
           [plugin-issues-section (first plugins)])]

        ;; Footer
        [:div.conflict-modal-footer
         {:style {:display "flex" :align-items "center" :gap "8px"}}

         ;; Bug icon + hidden export-as-is
         [:div.flex.align-items-c {:style {:flex-shrink 0}}
          [:i.fa.fa-bug.export-bug-toggle
           {:class (when show-export-as-is? "active")
            :title "Developer options"
            :on-click #(dispatch [:toggle-export-as-is])}]
          (when show-export-as-is?
            [:span.export-as-is-link
             {:on-click #(dispatch [:export-as-is])}
             "export raw (no fixes)"])]

         [:div {:style {:flex 1}}]

         ;; Cancel — populates slide-out with issues for manual fixing
         [:span.link-button
          {:on-click #(dispatch [:export-cancel-with-log])}
          "Cancel"]

         ;; Export & Auto-Fix — primary action
         [:button.form-button
          {:on-click #(dispatch [:export-with-auto-fix])
           :disabled (not all-filled?)
           :class (when-not all-filled? "disabled")}
          (if all-filled?
            "Export & Auto-Fix"
            (str "Fill required fields ("
                 filled-dropdowns "/" total-dropdowns ")"))]]]])))

(defn import-log-overlay
  "Composite component rendering all import/export overlay UI.
   Mount this once in the app root."
  []
  [:div
   [import-log/import-log-button]
   [import-log/import-log-panel]
   [conflict-resolution-modal]
   [export-warning-modal]])
