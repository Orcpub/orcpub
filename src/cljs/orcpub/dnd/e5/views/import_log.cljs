(ns orcpub.dnd.e5.views.import-log
  "Import log slide-out panel and floating button.
   Displays import results: errors, skipped items, and auto-fixes applied."
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as r]
            [clojure.string :as str]))

(def ^:private code-style
  {:background "rgba(0,0,0,0.3)" :padding "2px 6px" :border-radius "3px"})

(def ^:private code-style-sm
  (assoc code-style :font-size "11px"))

(defn format-change-item
  "Render a single change entry from the import log.
   Dispatches on :type to produce the appropriate icon + description.
   :filled-required-fields entries expand their :details into per-item sub-rows."
  [{:keys [type path field from to description] :as change}]
  [:div.import-log-item
   {:style {:padding "8px 12px"
            :border-bottom "1px solid rgba(255,255,255,0.15)"
            :font-size "12px"}}
   (case type
     :renamed-plugin-key
     [:span [:i.fa.fa-tag.m-r-5 {:style {:color "#47eaf8"}}]
      "Renamed empty plugin key " [:code {:style code-style} (pr-str from)]
      " \u2192 " [:code {:style code-style} to]]

     :fixed-option-pack
     [:span [:i.fa.fa-wrench.m-r-5 {:style {:color "#47eaf8"}}]
      "Fixed empty option-pack at " [:code {:style code-style-sm} (str path)]]

     :removed-nil
     [:span [:i.fa.fa-minus-circle.m-r-5 {:style {:color "rgba(255,255,255,0.35)"}}]
      "Removed nil " [:code {:style code-style} (name field)]
      " at " [:code {:style code-style-sm} (str path)]]

     :replaced-nil
     [:span [:i.fa.fa-exchange.m-r-5 {:style {:color "#47eaf8"}}]
      "Replaced " [:code {:style code-style} (str (name field) " nil")]
      " \u2192 " [:code {:style code-style} (str to)]]

     :preserved-nil
     [:span {:style {:color "rgba(255,255,255,0.35)"}}
      [:i.fa.fa-check.m-r-5 {:style {:color "#70a800"}}]
      "Preserved " [:code {:style code-style} (name field)]
      " nil at " [:code {:style code-style-sm} (str path)]]

     :string-fix
     [:span [:i.fa.fa-code.m-r-5 {:style {:color "#f0a100"}}] description]

     :removed-nil-key
     [:span [:i.fa.fa-minus-circle.m-r-5 {:style {:color "rgba(255,255,255,0.35)"}}]
      "Removed nil key at " [:code {:style code-style-sm} (str path)]]

     :text-normalization
     [:span [:i.fa.fa-font.m-r-5 {:style {:color "#47eaf8"}}]
      (or description "Normalized Unicode characters to ASCII")]

     :filled-required-fields
     (let [details (:details change)]
       [:div
        [:span [:i.fa.fa-pencil.m-r-5 {:style {:color "#f0a100"}}]
         (or description "Filled missing required fields with placeholders")]
        (when (seq details)
          [:div {:style {:margin-top "4px" :padding-left "20px"}}
           (for [[idx {:keys [key content-type plugin changes]}] (map-indexed vector details)]
             ^{:key idx}
             [:div {:style {:padding "2px 0" :font-size "11px"
                            :color "rgba(255,255,255,0.6)"}}
              [:code {:style code-style-sm} (name key)]
              (when content-type
                [:span " (" (-> (name content-type) (.replace "orcpub.dnd.e5/" "")) ")"])
              (when plugin
                [:span {:style {:color "rgba(255,255,255,0.35)"}} (str " in " plugin)])
              (let [{:keys [fields traits-fixed options-fixed]} changes
                    parts (cond-> []
                            (seq fields)
                            (conj (str "filled " (str/join ", " (map name fields))))
                            (and traits-fixed (pos? traits-fixed))
                            (conj (str traits-fixed " trait(s) named"))
                            (and options-fixed (pos? options-fixed))
                            (conj (str options-fixed " option(s) filled")))]
                (when (seq parts)
                  [:span " \u2014 " (str/join "; " parts)]))])])])

     :key-renamed
     [:span [:i.fa.fa-tag.m-r-5 {:style {:color "#47eaf8"}}]
      "Renamed key " [:code {:style code-style} (pr-str from)]
      " \u2192 " [:code {:style code-style} (pr-str to)]]

     :export-missing-fields
     (let [details (:details change)]
       [:div
        [:span [:i.fa.fa-exclamation-triangle.m-r-5 {:style {:color "#f0a100"}}]
         (or description "Missing fields detected during export")]
        (when (seq details)
          [:div {:style {:margin-top "4px" :padding-left "20px"}}
           (for [[idx {:keys [key content-type plugin missing-fields
                              traits-missing-names]}] (map-indexed vector details)]
             ^{:key idx}
             [:div {:style {:padding "2px 0" :font-size "11px"
                            :color "rgba(255,255,255,0.6)"}}
              [:code {:style code-style-sm} (name key)]
              (when content-type
                [:span " (" (-> (name content-type) (.replace "orcpub.dnd.e5/" "")) ")"])
              (when plugin
                [:span {:style {:color "rgba(255,255,255,0.35)"}} (str " in " plugin)])
              (let [parts (cond-> []
                            (seq missing-fields)
                            (conj (str "missing: " (str/join ", " (map name missing-fields))))
                            (and traits-missing-names (pos? traits-missing-names))
                            (conj (str traits-missing-names " trait(s) without name")))]
                (when (seq parts)
                  [:span " \u2014 " (str/join "; " parts)]))])])])

     :dedup-selection-options
     ;; Duplicate options within one Selection's choice list. An option's key is
     ;; derived from its name, so same-named options collide (the second silently
     ;; overrides the first downstream). Report the fix \u2014 identical copies dropped,
     ;; same-name-different-content renamed \u2014 so the user can rename more clearly.
     (let [details (:details change)]
       [:div
        [:span [:i.fa.fa-clone.m-r-5 {:style {:color "#47eaf8"}}]
         (or description "Resolved duplicate selection options")]
        (when (seq details)
          [:div {:style {:margin-top "4px" :padding-left "20px"}}
           (for [[idx {:keys [type item-key content-type plugin] d-desc :description}]
                 (map-indexed vector details)]
             ^{:key idx}
             [:div {:style {:padding "2px 0" :font-size "11px"
                            :color "rgba(255,255,255,0.6)"}}
              [:i {:class (str "fa m-r-5 "
                               (if (= type :dedup-renamed) "fa-pencil" "fa-minus-circle"))
                   :style {:color "rgba(255,255,255,0.45)"}}]
              d-desc
              (when content-type
                [:span " (" (-> (name content-type) (.replace "orcpub.dnd.e5/" "")) ")"])
              (when item-key
                [:code {:style code-style-sm} (name item-key)])
              (when plugin
                [:span {:style {:color "rgba(255,255,255,0.35)"}} (str " in " plugin)])])])])

     ;; Default
     [:span (pr-str change)])])

(defn collapsible-section
  "A collapsible section with header and content.
   Pass :default-expanded? false to start collapsed."
  [{:keys [title icon icon-color bg-color border-color default-expanded?]} content]
  (let [expanded? (r/atom (if (some? default-expanded?) default-expanded? true))]
    (fn [{:keys [title icon icon-color bg-color border-color]} content]
      [:div {:style {:margin "10px"}}
       [:div.flex.align-items-c.f-w-b.pointer
        {:style {:padding "10px"
                 :background bg-color
                 :border-left (str "3px solid " border-color)
                 :border-radius "5px"}
         :on-click #(swap! expanded? not)}
        [:i {:class (str "fa fa-chevron-" (if @expanded? "down" "right"))
             :style {:font-size "12px" :color "rgba(255,255,255,0.35)" :margin-right "8px" :width "12px"}}]
        [:i {:class (str "fa " icon " m-r-5")
             :style {:color icon-color}}]
        title]
       (when @expanded?
         [:div {:style {:margin-top "4px"}}
          content])])))

(defn import-log-panel []
  (let [log @(subscribe [:import-log])
        shown? (:panel-shown? log)]
    [:div.import-log-panel
     {:style {:position "fixed"
              :top 0
              :right (if shown? 0 "-420px")
              :width "400px"
              :max-width "100vw"
              :height "100vh"
              :background "#1a1e28"
              :color "white"
              :transition "right 0.3s ease"
              :z-index 950
              :overflow-y "auto"
              :box-shadow (if shown? "-4px 0 20px rgba(0,0,0,0.5)" "none")}}

     ;; Header
     [:div.flex.align-items-c
      {:style {:padding "15px 20px"
               :background "#2c3445"
               :border-bottom "1px solid rgba(255,255,255,0.15)"}}
      [:i.fa.fa-chevron-right.pointer
       {:style {:font-size "16px" :color "rgba(255,255,255,0.35)" :margin-right "12px"}
        :on-click #(dispatch [:close-import-log-panel])}]
      [:div
       [:span.f-w-b.f-s-16 "Import Log"]
       (when (:import-name log)
         [:div.f-s-12 {:style {:color "rgba(255,255,255,0.35)" :margin-top "4px"}}
          (:import-name log)])]]

     ;; Content
     [:div {:style {:padding "10px 0"}}

      ;; Errors section
      (when (seq (:errors log))
        [collapsible-section
         {:title (str "Errors (" (count (:errors log)) ")")
          :icon "fa-exclamation-circle"
          :icon-color "#d94b20"
          :bg-color "rgba(217, 75, 32, 0.1)"
          :border-color "#d94b20"}
         [:div
          (for [[idx error] (map-indexed vector (:errors log))]
            ^{:key idx}
            [:div {:style {:padding "5px 12px" :font-size "12px" :color "#d94b20"}} error])]])

      ;; Skipped items section
      (when (seq (:skipped-items log))
        [collapsible-section
         {:title (str "Skipped Items (" (count (:skipped-items log)) ")")
          :icon "fa-exclamation-triangle"
          :icon-color "#f0a100"
          :bg-color "rgba(240, 161, 0, 0.1)"
          :border-color "#f0a100"}
         [:div
          (for [item (:skipped-items log)]
            ^{:key (:key item)}
            [:div {:style {:padding "8px 12px" :border-bottom "1px solid rgba(255,255,255,0.15)"}}
             [:div.f-w-b {:style {:color "#f0a100"}} (name (:key item))]
             [:div.f-s-12 {:style {:color "rgba(255,255,255,0.35)" :margin-top "4px"}} (:errors item)]])]])

      ;; Grouped change sections
      (let [changes (:changes log)
            user-types #{:key-renamed :filled-required-fields :export-missing-fields
                         :string-fix :text-normalization :renamed-plugin-key
                         :dedup-selection-options}
            sections [{:types #{:key-renamed}
                       :title-fn #(str "Key Renames (" (count %) ")")
                       :icon "fa-tag" :icon-color "#47eaf8"
                       :bg-color "rgba(71, 234, 248, 0.08)" :border-color "#47eaf8"}
                      {:types #{:filled-required-fields}
                       :title-fn (fn [items]
                                   (let [detail-count (reduce + 0 (map #(count (:details %)) items))]
                                     (if (pos? detail-count)
                                       (str "Field Fixes (" detail-count " items)")
                                       (str "Field Fixes (" (count items) ")"))))
                       :icon "fa-pencil" :icon-color "#f0a100"
                       :bg-color "rgba(240, 161, 0, 0.1)" :border-color "#f0a100"}
                      {:types #{:export-missing-fields}
                       :title-fn (fn [items]
                                   (let [detail-count (reduce + 0 (map #(count (:details %)) items))]
                                     (if (pos? detail-count)
                                       (str "Export Issues (" detail-count " items)")
                                       (str "Export Issues (" (count items) ")"))))
                       :icon "fa-exclamation-triangle" :icon-color "#f0a100"
                       :bg-color "rgba(240, 161, 0, 0.1)" :border-color "#f0a100"}
                      {:types #{:string-fix :text-normalization :renamed-plugin-key
                                :dedup-selection-options}
                       :title-fn #(str "Data Cleanup (" (count %) ")")
                       :icon "fa-wrench" :icon-color "#47eaf8"
                       :bg-color "rgba(71, 234, 248, 0.08)" :border-color "#47eaf8"}]
            ;; Debug section: known debug types + any unknown types (catch-all)
            debug-items (filterv #(not (user-types (:type %))) changes)]
        [:div
         (for [{:keys [types title-fn icon icon-color bg-color border-color]} sections
               :let [items (filterv #(types (:type %)) changes)]
               :when (seq items)]
           ^{:key (str (first types))}
           [collapsible-section
            {:title (title-fn items)
             :icon icon :icon-color icon-color
             :bg-color bg-color :border-color border-color}
            [:div
             (for [[idx change] (map-indexed vector items)]
               ^{:key idx}
               [format-change-item change])]])
         (when (seq debug-items)
           [collapsible-section
            {:title (str "Advanced Details (" (count debug-items) ")")
             :icon "fa-cog" :icon-color "rgba(255,255,255,0.35)"
             :bg-color "rgba(255,255,255,0.04)" :border-color "rgba(255,255,255,0.15)"
             :default-expanded? false}
            [:div
             (for [[idx change] (map-indexed vector debug-items)]
               ^{:key idx}
               [format-change-item change])]])])

      ;; Empty state
      (when (and (empty? (:errors log))
                 (empty? (:skipped-items log))
                 (empty? (:changes log)))
        [:div.t-a-c {:style {:padding "40px" :color "rgba(255,255,255,0.35)"}}
         [:i.fa.fa-check-circle {:style {:font-size "48px" :color "#70a800" :margin-bottom "15px"}}]
         [:div.f-s-16 "No issues found"]
         [:div.f-s-12 {:style {:margin-top "5px"}} "Import completed cleanly"]])]]))

(defn import-log-button []
  (let [has-content? @(subscribe [:import-log-has-content?])
        shown? @(subscribe [:import-log-shown?])
        log @(subscribe [:import-log])
        total-count (+ (count (:changes log))
                       (count (:errors log))
                       (count (:skipped-items log)))]
    [:div.flex.align-items-c.justify-cont-c.pointer
     {:style {:position "fixed"
              :bottom "20px"
              :right "20px"
              :width "40px"
              :height "40px"
              :border-radius "50%"
              :background (cond
                            shown? "#f0a100"
                            has-content? "#2c3445"
                            :else "#1a1e28")
              :color (if has-content? "white" "rgba(255,255,255,0.25)")
              :box-shadow "0 2px 6px 0 rgba(0,0,0,0.5)"
              :z-index 900
              :opacity (if has-content? 1 0.6)
              :transition "all 0.2s ease"}
      :on-click #(dispatch [:toggle-import-log-panel])}
     [:i.fa.fa-list-alt {:style {:font-size "14px"}}]
     (when (pos? total-count)
       [:div.flex.align-items-c.justify-cont-c
        {:style {:position "absolute"
                 :top "-4px"
                 :right "-4px"
                 :background "#d94b20"
                 :color "white"
                 :font-size "10px"
                 :font-weight "bold"
                 :min-width "16px"
                 :height "16px"
                 :border-radius "8px"
                 :padding "0 4px"}}
        total-count])]))
