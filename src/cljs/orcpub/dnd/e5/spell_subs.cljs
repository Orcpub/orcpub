(ns orcpub.dnd.e5.spell-subs
  (:require [re-frame.core :refer [reg-sub]]
            [orcpub.common :as common]
            [orcpub.template :as t]
            [orcpub.modifiers :as mod]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.backgrounds :as bg5e]
            [orcpub.dnd.e5.languages :as langs5e]
            [orcpub.dnd.e5.races :as races5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.feats :as feats5e]
            [orcpub.dnd.e5.modifiers :as mod5e]
            [orcpub.dnd.e5.magic-items :as mi5e]
            [orcpub.dnd.e5.units :as units5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.weapons :as weapon5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.monsters :as monsters5e]
            [orcpub.dnd.e5.selections :as selections5e]
            [orcpub.dnd.e5.encounters :as encounters5e]
            [orcpub.dnd.e5.combat :as combat5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.armor :as armor5e]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.equipment :as equipment5e]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.route-map :as routes]
            [orcpub.dnd.e5.event-utils]
            [orcpub.dnd.e5.template-base :as t-base]
            [reagent.ratom :as ra]
            [clojure.string :as s]
            [cljs-http.client :as http]))

;; =============================================================================
;; Version: 1.05 - Fix: preserve map keys for classes/subclasses (enables renamed keys)
;; =============================================================================

(reg-sub
 ::e5/plugins
 (fn [db _]
   (get db :plugins)))

;; Ephemeral overlay for a SHARED character being viewed: content that arrived
;; embedded in a share link (view-once) lives here, NOT in :plugins, so it is
;; never persisted to the recipient's library and vanishes on reload without the
;; link. The content-lookup subs below fold it in (last, so it wins key
;; collisions for the shared view); the library manager / export read :plugins
;; directly and never see it. See orcpub.dnd.e5.share-url / share-bundle.
(reg-sub
 ::e5/shared-plugins
 (fn [db _]
   (get db :shared-plugins)))

;; Summary for the shared-content banner: {:count n :collisions [...]} while a
;; shared character with embedded homebrew is being viewed, else nil.
(reg-sub
 ::e5/shared-content-info
 (fn [db _]
   (get db :shared-content-info)))

;; The name-keyed quarantine map ({source-name → bad-source}) loaded at
;; boot, kept in sync by the repair event. Drives the quarantine repair panel.
(reg-sub
 ::e5/quarantined-plugins
 (fn [db _]
   (get db :quarantined-plugins)))

(defn- process-plugin-vals
  "Filter out malformed/disabled plugin data so a bad entry can't break the
   subscription chain (e.g. the class dropdown). Returns a seq of clean
   {content-type {key def}} maps."
  [plugins]
  (keep
   (fn [p]
     (try
       (when (map? p)
         (into
          {}
          (keep
           (fn [[type-k type-m]]
             (when (and type-k (or (nil? type-m) (map? type-m)))
               [type-k
                (if (map? type-m)
                  (into
                   {}
                   (keep
                    (fn [[k v]]
                      ;; Only include if v is a map and not disabled
                      (when (and (map? v) (not (:disabled? v)))
                        [k v]))
                    type-m))
                  type-m)]))
           p)))
       (catch js/Error e
         (js/console.warn "Skipping malformed plugin data:" (pr-str p) e)
         nil)))
   (filter (fn [p] (and (map? p) (not (:disabled? p))))
           (vals plugins))))

(reg-sub
 ::e5/plugin-vals
 :<- [::e5/plugins]
 :<- [::e5/shared-plugins]
 (fn [[plugins shared] _]
   ;; Shared (view-once) content is appended LAST so it wins key collisions
   ;; against the recipient's own library for the shared view only.
   (concat (process-plugin-vals plugins)
           (process-plugin-vals shared))))

;; Subscription that preserves source names when extracting content from plugins.
;; This is needed for disambiguation when multiple sources have same-named content.
(defn- process-plugins-with-sources [plugins]
  ;; Returns seq of [source-name plugin-data] pairs, skipping disabled/malformed.
  (keep
   (fn [[source-name plugin-data]]
     (when (and (map? plugin-data) (not (:disabled? plugin-data)))
       [source-name plugin-data]))
   plugins))

(reg-sub
 ::e5/plugins-with-sources
 :<- [::e5/plugins]
 :<- [::e5/shared-plugins]
 (fn [[plugins shared] _]
   ;; Shared (view-once) sources appended LAST so they win key collisions for
   ;; the shared view only.
   (concat (process-plugins-with-sources plugins)
           (process-plugins-with-sources shared))))

(reg-sub
 ::bg5e/plugin-backgrounds
 :<- [::e5/plugin-vals]
 (fn [plugins _]
   (map
    (fn [background]
      (assoc background :edit-event [::bg5e/edit-background background]))
    (mapcat (comp vals ::e5/backgrounds) plugins))))

(reg-sub
 ::langs5e/plugin-languages
 :<- [::e5/plugin-vals]
 (fn [plugins _]
   (mapcat (comp vals ::e5/languages) plugins)))

(reg-sub
 ::selections5e/plugin-selections
 :<- [::e5/plugin-vals]
 (fn [plugins _]
   (mapcat (comp vals ::e5/selections) plugins)))

(reg-sub
 ::selections5e/selection-map
 :<- [::selections5e/plugin-selections]
 (fn [selections]
   (common/map-by-key selections)))

(defn spell-modifiers [{:keys [spells]} class-name]
  (map
   (fn [{:keys [level value]}]
     (let [{:keys [ability key]} value]
       (mod5e/spells-known (or (:level value) 0)
                           key
                           (when (keyword? ability)
                             (keyword "orcpub.dnd.e5.character" (name ability)))
                           class-name
                           level)))
   spells))

(reg-sub
 ::races5e/plugin-races
 :<- [::e5/plugin-vals]
 (fn [plugins _]
   (map
    (fn [race]
      (assoc race
             :modifiers
             (concat (opt5e/plugin-modifiers (:props race)
                                             (:key race))
                     (spell-modifiers race (:name race)))
             :edit-event [::races5e/edit-race race]))
    (mapcat (comp vals ::e5/races) plugins))))

(reg-sub
 ::races5e/plugin-subraces
 :<- [::e5/plugin-vals]
 (fn [plugins _]
   (map
    (fn [subrace]
      (assoc subrace
             :modifiers (concat (opt5e/plugin-modifiers (:props subrace)
                                                        (:key subrace))
                                (spell-modifiers subrace (:name subrace)))
             :edit-event [::races5e/edit-subrace subrace]))
    (mapcat (comp vals ::e5/subraces) plugins))))

(defn level-modifier [class-key {:keys [type value] :as modifier}]
  (case type
    :weapon-prof (mod5e/weapon-proficiency value)
    :num-attacks (mod5e/num-attacks value)
    :damage-resistance (mod5e/damage-resistance value)
    :damage-immunity (mod5e/damage-immunity value)
    :saving-throw-advantage (mod5e/saving-throw-advantage value)
    :skill-prof (mod5e/skill-proficiency value)
    :armor-prof (mod5e/armor-proficiency value)
    :tool-prof (mod5e/tool-proficiency value)
    :flying-speed (mod5e/flying-speed-override value)
    :swimming-speed (mod5e/swimming-speed-override value)
    :flying-speed-equals-walking-speed (mod5e/flying-speed-equal-to-walking)
    :spell (mod5e/spells-known (:level value)
                               (:key value)
                               (:ability value)
                               (when (keyword? class-key)
                                 (common/safe-capitalize-kw class-key)))
    ;; Default case: log warning and return nil modifier for unknown types
    (do
      (js/console.warn "Unknown level-modifier type:" type "for class:" class-key "modifier:" (pr-str modifier))
      nil)))

(defn eldritch-knight-spell? [s]
    (let [school (:school s)]
      (or (= school "evocation")
          (= school "abjuration"))))

(defn arcane-trickster-spell? [s]
    (let [school (:school s)]
      (or (= school "enchantment")
          (= school "illusion"))))


(defn subclass-wizard-spell-selection [spell-lists spells-map title ref class-key class-name num spell-levels & [filter-fn]]
  (opt5e/spell-selection spell-lists
                         spells-map
                         {:title title
                          :class-key class-key
                          :ref ref
                          :spellcasting-ability ::char5e/int
                          :class-name class-name
                          :num num
                          :prepend-level? true
                          :spell-keys (let [spell-keys
                                            (mapcat
                                             (fn [lvl] (get-in spell-lists [:wizard lvl]))
                                             spell-levels)]
                                        (if filter-fn
                                          (filter
                                           (fn [spell-key]
                                             (filter-fn (spells-map spell-key)))
                                           spell-keys)
                                          spell-keys))}))

(defn eldritch-knight-ref [subclass-key subpath]
    (concat
     [:class :fighter :levels :level-3 :martial-archetype subclass-key]
     subpath))

(defn arcane-trickster-ref [subclass-key subpath]
    (concat
     [:class :rogue :levels :level-3 :roguish-archetype subclass-key]
     subpath))

(defn eldritch-knight-spell-selection [subclass-key spell-lists spells-map num spell-levels]
  (subclass-wizard-spell-selection spell-lists
                                   spells-map
                                   "Fighter Abjuration or Evocation Spells"
                                   (eldritch-knight-ref subclass-key [:abjuration-or-evocation-spells-known])
                                   :fighter
                                   "Fighter"
                                   num
                                   spell-levels
                                   eldritch-knight-spell?))

(defn arcane-trickster-spell-selection [subclass-key spell-lists spells-map num spell-levels]
  (subclass-wizard-spell-selection spell-lists
                                   spells-map
                                   "Rogue Enchantment or Illusion Spells"
                                     (arcane-trickster-ref subclass-key [:enchantment-or-illusion-spells-known])
                                     :rogue
                                     "Rogue"
                                     num
                                     spell-levels
                                     arcane-trickster-spell?))

(defn eldritch-knight-any-spell-selection [subclass-key spell-lists spells-map num spell-levels]
  (subclass-wizard-spell-selection spell-lists
                                   spells-map
                                   "Fighter Spells: Any School"
                                     (eldritch-knight-ref subclass-key [:spells-known-any-school])
                                     :fighter
                                     "Fighter"
                                     num
                                     spell-levels))


(defn arcane-trickster-any-spell-selection [subclass-key spell-lists spells-map num spell-levels]
  (subclass-wizard-spell-selection spell-lists
                                   spells-map
                                   "Rogue Spells: Any School"
                                     (arcane-trickster-ref subclass-key [:spells-known-any-school])
                                     :rogue
                                     "Rogue"
                                     num
                                     spell-levels))

(defn eldritch-knight-cantrip [subclass-key spell-lists spells-map num]
  (opt5e/spell-selection spell-lists
                         spells-map
                         {:class-key :fighter
                          :level 0
                          :ref (eldritch-knight-ref subclass-key [:cantrips-known])
                          :spellcasting-ability ::char5e/int
                          :class-name "Fighter"
                          :num num
                          :spell-keys (get-in spell-lists [:wizard 0])}))

(defn arcane-trickster-cantrip [subclass-key spell-lists spells-map num]
  (opt5e/spell-selection spell-lists
                         spells-map
                         {:class-key :rogue
                          :level 0
                          :ref (arcane-trickster-ref subclass-key [:cantrips-known])
                          :spellcasting-ability ::char5e/int
                          :class-name "Rogue"
                          :num num
                          :spell-keys (get-in spell-lists [:wizard 0])}))

(defn spellcaster-subclass-levels [subclass-key spell-lists spells-map class-name]
  (case class-name
    :rogue {3 {:selections [(arcane-trickster-cantrip subclass-key spell-lists spells-map 2)
                            (arcane-trickster-spell-selection subclass-key spell-lists spells-map 2 [1])
                            (arcane-trickster-any-spell-selection subclass-key spell-lists spells-map 1 [1])]
               :modifiers [(mod5e/spells-known 0 :mage-hand ::char5e/int "Arcane Trickster")]}
            4 {:selections [(arcane-trickster-spell-selection subclass-key spell-lists spells-map 1 [1])]}
            7 {:selections [(arcane-trickster-spell-selection subclass-key spell-lists spells-map 1 [1 2])]}
            8 {:selections [(arcane-trickster-any-spell-selection subclass-key spell-lists spells-map 1 [1 2])]}
            10 {:selections [(arcane-trickster-cantrip subclass-key spell-lists spells-map 1)
                             (arcane-trickster-spell-selection subclass-key spell-lists spells-map 1 [1 2])]}
            11 {:selections [(arcane-trickster-spell-selection subclass-key spell-lists spells-map 1 [1 2])]}
            13 {:selections [(arcane-trickster-spell-selection subclass-key spell-lists spells-map 1 [1 2 3])]}
            14 {:selections [(arcane-trickster-any-spell-selection subclass-key spell-lists spells-map 1 [1 2 3])]}
            16 {:selections [(arcane-trickster-spell-selection subclass-key spell-lists spells-map 1 [1 2 3])]}
            19 {:selections [(arcane-trickster-spell-selection subclass-key spell-lists spells-map 1 [1 2 3 4])]}
            20 {:selections [(arcane-trickster-any-spell-selection subclass-key spell-lists spells-map 1 [1 2 3 4])]}}
    :fighter {3 {:selections [(eldritch-knight-cantrip subclass-key spell-lists spells-map 2)
                              (eldritch-knight-spell-selection subclass-key spell-lists spells-map 2 [1])
                              (eldritch-knight-any-spell-selection subclass-key spell-lists spells-map 1 [1])]}
              4 {:selections [(eldritch-knight-spell-selection subclass-key spell-lists spells-map 1 [1])]}
              7 {:selections [(eldritch-knight-spell-selection subclass-key spell-lists spells-map 1 [1 2])]}
              8 {:selections [(eldritch-knight-any-spell-selection subclass-key spell-lists spells-map 1 [1 2])]}
              10 {:selections [(eldritch-knight-cantrip subclass-key spell-lists spells-map 1)
                               (eldritch-knight-spell-selection subclass-key spell-lists spells-map 1 [1 2])]}
              11 {:selections [(eldritch-knight-spell-selection subclass-key spell-lists spells-map 1 [1 2])]}
              13 {:selections [(eldritch-knight-spell-selection subclass-key spell-lists spells-map 1 [1 2 3])]}
              14 {:selections [(eldritch-knight-any-spell-selection subclass-key spell-lists spells-map 1 [1 2 3])]}
              16 {:selections [(eldritch-knight-spell-selection subclass-key spell-lists spells-map 1 [1 2 3])]}
              19 {:selections [(eldritch-knight-spell-selection subclass-key spell-lists spells-map 1 [1 2 3 4])]}
              20 {:selections [(eldritch-knight-any-spell-selection subclass-key spell-lists spells-map 1 [1 2 3 4])]}}
    nil))

(defn merge-level [level-1 level-2]
  (merge-with
   concat
   level-1
   level-2))

(defn merge-levels [& level-specs]
  (apply
   merge-with
   merge-level
   level-specs))

(defn to-class-level [spell-level]
  (dec (* 2 spell-level)))

(defn level-selection [class-key selection-map {:keys [type num]}]
  (let [{:keys [name options]} (selection-map type)]
    (t/selection-cfg
     {:name name
      :key type
      :tags #{:class}
      :min (or num 1)
      :max (or num 1)
      :options (map
                (fn [{:keys [name description]}]
                  (t/option-cfg
                   {:name name
                    :modifiers [(mod5e/trait-cfg
                                 {:name name
                                  :summary description})]}))
                options)})))

(defn make-level-selections [class selections selection-map]
  (reduce
   (fn [levels {:keys [level] :as s}]
     (update-in levels
               [(or level 1) :selections]
               conj
               (level-selection class selection-map s)))
   {}
   selections))

(defn make-cleric-spell-mods [cleric-spells]
  (vec
   (reduce-kv
    (fn [mods spell-level spells]
      (concat
       mods
       (let [spell-kws (vals spells)]
         (mapv
          (fn [spell-kw]
            (opt5e/cleric-spell spell-level spell-kw (to-class-level spell-level)))
          spell-kws))))
    []
    cleric-spells)))

(defn make-levels [spell-lists spells-map selection-map {:keys [key class spellcasting] :as option}]
  (let [modifiers (:level-modifiers option)
        selections (:level-selections option)
        by-level (group-by :level modifiers)
        add-spellcasting? (and spellcasting
                               (#{:fighter :rogue} class))
        spellcaster-levels (spellcaster-subclass-levels key spell-lists spells-map class)
        selections-levels (make-level-selections class selections selection-map)]
    (reduce-kv
     (fn [levels level level-modifiers]
       (update-in levels
                  [(or level 1) :modifiers]
                  concat
                  ;; Filter out nil modifiers (from unknown types)
                  (keep (partial level-modifier class) level-modifiers)))
     (merge-levels
      selections-levels
      (when add-spellcasting?
        spellcaster-levels)
      (when (and (= class :paladin)
               (:paladin-spells option))
        {1 {:modifiers (reduce-kv
                        (fn [mods spell-level spells]
                          (concat
                           mods
                           (map
                            (fn [spell-kw]
                              (opt5e/paladin-spell spell-level
                                                   spell-kw))
                            (vals spells))))
                        []
                        (:paladin-spells option))}})
      (let [cleric-spells (:cleric-spells option)]
        (when (and (= class :cleric)
                 cleric-spells)
          (let [cleric-spell-mods (make-cleric-spell-mods cleric-spells)]
            {1 {:modifiers cleric-spell-mods}})))
      (when (and (= class :warlock)
               (:warlock-spells option))
        (reduce-kv
         (fn [levels spell-level spells]
           (let [level (to-class-level spell-level)]
             (when (and spell-level (seq (vals spells)))
               (assoc-in levels
                         [level :selections]
                         [(opt5e/warlock-subclass-spell-selection spell-lists spells-map (vals spells))]))))
         {}
         (:warlock-spells option))))
     by-level)))

(reg-sub
 ::classes5e/plugin-subclasses
 :<- [::e5/plugins-with-sources]
 :<- [::spells5e/spell-lists]
 :<- [::spells5e/spells-map]
 :<- [::selections5e/selection-map]
 (fn [[plugins-with-sources spell-lists spells-map selection-map] _]
   (keep
    (fn [[source-name subclass-key subclass]]
      (try
        (when (and (map? subclass) subclass-key)
          ;; Ensure the subclass has its key set (the map key is authoritative)
          (let [subclass-with-key (assoc subclass :key subclass-key)
                levels (make-levels spell-lists spells-map selection-map subclass-with-key)]
            (assoc subclass-with-key
                   :modifiers (opt5e/plugin-modifiers (:props subclass)
                                                      subclass-key)
                   :levels levels
                   :plugin-source source-name
                   :edit-event [::classes5e/edit-subclass subclass-with-key])))
        (catch js/Error e
          (js/console.warn "Skipping malformed subclass:" subclass-key e)
          nil)))
    ;; Extract subclasses from each plugin with the map key
    (for [[source-name plugin-data] plugins-with-sources
          [subclass-key subclass-data] (::e5/subclasses plugin-data)
          :when (and (map? subclass-data) (not (:disabled? subclass-data)))]
      [source-name subclass-key subclass-data]))))

;; :name on plugin classes is canonical; do not mutate it to embed source
;; (the source label is carried separately as :plugin-source). Mutating :name
;; causes downstream name-to-kw to derive shifted keys.
(reg-sub
 ::classes5e/plugin-classes
 :<- [::e5/plugins-with-sources]
 :<- [::spells5e/spell-lists]
 :<- [::spells5e/spells-map]
 :<- [::selections5e/selection-map]
 (fn [[plugins-with-sources spell-lists spells-map selection-map]]
   ;; Defensive handling: skip malformed classes rather than breaking
   ;; Source name carried as :plugin-source for display; never folded into :name.
   (keep
    (fn [[source-name class-key class]]
      (try
        (when (and (map? class) class-key)
          (let [class-with-key (assoc class :key class-key)
                levels (make-levels spell-lists spells-map selection-map class-with-key)]
            (assoc class-with-key
                   :plugin-source source-name
                   :modifiers (opt5e/plugin-modifiers (:props class)
                                                      class-key)
                   :levels levels)))
        (catch js/Error e
          (js/console.warn "Skipping malformed class:" class-key e)
          nil)))
    ;; Extract classes from each plugin with their source name AND the map key
    ;; The map key (e.g., :artificer-kibbles-tasty) is the authoritative key
    (for [[source-name plugin-data] plugins-with-sources
          [class-key class-data] (::e5/classes plugin-data)
          :when (and (map? class-data) (not (:disabled? class-data)))]
      [source-name class-key class-data]))))

(reg-sub
 ::feats5e/plugin-feats
 :<- [::e5/plugin-vals]
 (fn [plugins _]
   (mapcat (comp vals ::e5/feats) plugins)))

(reg-sub
 ::classes5e/plugin-invocations
 :<- [::e5/plugin-vals]
 (fn [plugins _]
   (mapcat (comp vals ::e5/invocations) plugins)))

(reg-sub
 ::classes5e/plugin-boons
 :<- [::e5/plugin-vals]
 (fn [plugins _]
   (mapcat #(-> % ::e5/boons vals) plugins)))

(def acolyte-bg
  {:name "Acolyte"
   :help "Your life has been devoted to serving a god or gods."
   :profs {:skill {:insight true, :religion true}
           :language-options {:choose 2 :options {:any true}}}
   :equipment {:clothes-common 1
               :pouch 1
               :incense 5
               :vestements 1}
   :selections [(opt5e/new-starting-equipment-selection
                 nil
                 {:name "Holy Symbol"
                  :options (map
                            #(opt5e/starting-equipment-option % 1)
                            equipment5e/holy-symbols)})
                ]
   :equipment-choices [{:name "Prayer Book/Wheel"
                        :options {:prayer-book 1
                                  :prayer-wheel 1}}]
   :treasure {:gp 15}
   :traits [{:name "Shelter the Faithful"
             :page 127
             :summary "You and your companions can expect free healing at an establishment of your faith."}]})

(reg-sub
 ::bg5e/backgrounds
 :<- [::bg5e/plugin-backgrounds]
 (fn [plugin-backgrounds]
   (cons
    acolyte-bg
    plugin-backgrounds)))

(def languages
  [{:name "Common"
    :key :common}
   {:name "Dwarvish"
    :key :dwarvish}
   {:name "Elvish"
    :key :elvish}
   {:name "Giant"
    :key :giant}
   {:name "Gnomish"
    :key :gnomish}
   {:name "Goblin"
    :key :goblin}
   {:name "Halfling"
    :key :halfling}
   {:name "Orc"
    :key :orc}
   {:name "Abyssal"
    :key :abyssal}
   {:name "Celestial"
    :key :celestial}
   {:name "Draconic"
    :key :draconic}
   {:name "Deep Speech"
    :key :deep-speech}
   {:name "Infernal"
    :key :infernal}
   {:name "Primordial"
    :key :primordial}
   {:name "Sylvan"
    :key :sylvan}
   {:name "Undercommon"
    :key :undercommon}])

(reg-sub
 ::langs5e/languages
 :<- [::langs5e/plugin-languages]
 (fn [plugin-languages]
   (concat
    languages
    plugin-languages)))

(reg-sub
 ::langs5e/language-map
 :<- [::langs5e/languages]
 (fn [languages]
   (common/map-by-key languages)))

(def elf-weapon-training-mods
  (opt5e/weapon-prof-modifiers [:longsword :shortsword :shortbow :longbow]))

(defn sunlight-sensitivity [page & [source]]
  {:name "Sunlight Sensitivity"
   :summary "Disadvantage on attack and perception rolls in direct sunlight"
   :source (or source :phb)
   :page 24})

(def mask-of-the-wild-mod
  (mod5e/trait-cfg
   {:name "Mask of the Wild"
    :page 24
    :summary "Hide when lightly obscured by natural phenomena."}))

(defn high-elf-cantrip-selection [spell-lists spells-map]
  (opt5e/spell-selection
   spell-lists
   spells-map
   {:class-key :wizard
    :level 0
    :exclude-ref? true
    :spellcasting-ability ::char5e/int
    :class-name "High Elf"
    :num 1}))

#_(def drow-magic-mods
  [(mod5e/spells-known 0 :dancing-lights ::char5e/cha "Dark Elf")
   (mod5e/spells-known 1 :faerie-fire ::char5e/cha "Dark Elf" 3)
   (mod5e/spells-known 2 :darkness ::char5e/cha "Dark Elf" 5)])

(defn elf-option-cfg [spell-lists spells-map language-map]
  {:name "Elf"
   :key :elf
   :help "Elves are graceful, magical creatures, with a slight build."
   :abilities {::char5e/dex 2}
   :size :medium
   :speed 30
   :languages ["Elvish" "Common"]
   :darkvision 60
   :modifiers [(mod5e/saving-throw-advantage [:charmed])
               (mod5e/immunity :magical-sleep)
               (mod5e/skill-proficiency :perception)]
   :subraces
   [{:name "High Elf"
     :abilities {::char5e/int 1}
     :selections [(high-elf-cantrip-selection spell-lists spells-map)
                  (opt5e/language-selection-aux (vals language-map) 1)]
     :modifiers [elf-weapon-training-mods]}
    #_{:name "Wood Elf"
     :abilities {::char5e/wis 1}
     :modifiers [(mod5e/speed 5)
                 mask-of-the-wild-mod
                 elf-weapon-training-mods]}
    #_{:name "Dark Elf (Drow)"
     :abilities {::char5e/cha 1}
     :traits [(sunlight-sensitivity 24)]
     :modifiers (conj drow-magic-mods
                      (mod5e/weapon-proficiency :rapier)
                      (mod5e/weapon-proficiency :shortsword)
                      (mod5e/weapon-proficiency :crossbow-hand)
                      (mod5e/darkvision 120))}]
   :traits [{:name "Fey Ancestry"
             :page 23
             :summary "advantage on charmed saves and immune to sleep magic"}
            {:name "Trance"
             :page 23
             :summary "Trance 4 hrs. instead of sleep 8"}]})

(def dwarf-option-cfg
  {:name "Dwarf",
   :key :dwarf
   :help "Dwarves are short and stout and tend to be skilled warriors and craftmen in stone and metal."
   :abilities {::char5e/con 2},
   :size :medium
   :speed 25,
   :darkvision 60
   :languages ["Dwarvish" "Common"]
   :weapon-proficiencies [:handaxe :battleaxe :light-hammer :warhammer]
   :selections [(opt5e/tool-selection [:smiths-tools :brewers-supplies :masons-tools] 1)]
   :traits [{:name "Dwarven Resilience"
             :summary "Advantage on poison saves, resistance to poison damage"
             :page 20},
            {:name "Stonecunning"
             :summary "2X prof bonus on stonework-related history checks"
             :page 20}]
   :subraces [{:name "Hill Dwarf",
               :abilities {::char5e/wis 1}
               :modifiers [(mod/modifier ?hit-point-level-bonus (+ 1 ?hit-point-level-bonus))]}
              #_{:name "Mountain Dwarf"
               :abilities {::char5e/str 2}
               :armor-proficiencies [:light :medium]}]
   :modifiers [(mod5e/damage-resistance :poison)
               (mod5e/saving-throw-advantage [:poisoned])]})

(def halfling-option-cfg
  {:name "Halfling"
   :key :halfling
   :help "Halflings are small and nimble, half the height of a human, but fairly stout. They are cheerful and practical."
   :abilities {::char5e/dex 2}
   :size :small
   :speed 25
   :languages ["Halfling" "Common"]
   :modifiers [(mod5e/saving-throw-advantage [:frightened])]
   :subraces
   [{:name "Lightfoot"
     :abilities {::char5e/cha 1}
     :traits [{:name "Naturally Stealthy"
               :page 28
               :summary "Hide behind creatures larger than you"}]}
    #_{:name "Stout"
     :abilities {::char5e/con 1}
     :modifiers [(mod5e/damage-resistance :poison)
                 (mod5e/saving-throw-advantage [:poisoned])]}]
   :traits [{:name "Lucky"
             :page 28
             :summary "Reroll 1s on d20"}
            {:name "Halfling Nimbleness"
             :page 28
             :summary "move through the space of larger creatures"}
            {:name "Brave"
             :page 28
             :summary "you have advantage on saves against being frightened"}]})

(defn human-option-cfg [spell-lists spells-map language-map]
  {:name "Human"
   :key :human
   :help "Humans are physically diverse and highly adaptable. They excel in nearly every profession."
   :size :medium
   :speed 30
   :languages ["Common"]
   :subraces
   [{:name "Calishite"}
    {:name "Chondathan"}
    {:name "Damaran"}
    {:name "Illuskan"}
    {:name "Mulan"}
    {:name "Rashemi"}
    {:name "Shou"}
    {:name "Tethyrian"}
    {:name "Turami"}]
   :selections [(opt5e/language-selection-aux (vals language-map) 1)
                (t/selection-cfg
                 {:name "Variant"
                  :tags #{:subrace}
                  :options [(t/option-cfg
                             {:name "Standard Human"
                              :modifiers [(mod5e/race-ability ::char5e/str 1)
                                          (mod5e/race-ability ::char5e/con 1)
                                          (mod5e/race-ability ::char5e/dex 1)
                                          (mod5e/race-ability ::char5e/int 1)
                                          (mod5e/race-ability ::char5e/wis 1)
                                          (mod5e/race-ability ::char5e/cha 1)]})
                            (t/option-cfg
                             {:name "Variant Human"
                              :selections [(opt5e/feat-selection spell-lists spells-map 1)
                                           (opt5e/skill-selection 1)
                                           (opt5e/ability-increase-selection char5e/ability-keys 2 true)]})]})]})

(defn draconic-ancestry-option [{:keys [name breath-weapon]}]
  (t/option-cfg
   {:name name
    :modifiers [(mod5e/damage-resistance (:damage-type breath-weapon))
                (mod/modifier ?draconic-ancestry-breath-weapon breath-weapon)]}))

(def dragonborn-option-cfg
  {:name "Dragonborn"
   :key :dragonborn
   :help "Kin to dragons, dragonborn resemble humanoid dragons, without wings or tail and standing erect. They tend to make excellent warriors."
   :abilities {::char5e/str 2 ::char5e/cha 1}
   :size :medium
   :speed 30
   :languages ["Draconic" "Common"]
   :modifiers [(mod5e/attack
                (let [breath-weapon ?draconic-ancestry-breath-weapon
                      damage-type (:damage-type breath-weapon)]
                  (merge
                   breath-weapon
                   {:name "Breath Weapon"
                    :summary (when damage-type
                               (common/safe-capitalize-kw damage-type))
                    :attack-type :area
                    :damage-die 6
                    :page 34
                    :damage-die-count (condp <= ?total-levels
                                        16 5
                                        11 4
                                        6 3
                                        2)
                    :save-dc (?spell-save-dc ::char5e/con)})))]
   :selections [(t/selection-cfg
                 {:name "Draconic Ancestry"
                  :tags #{:subrace}
                  :options (map
                            draconic-ancestry-option
                            opt5e/draconic-ancestries)})]})


(def gnome-option-cfg
  {:name "Gnome"
   :key :gnome
   :help "Gnomes are small, intelligent humanoids who live life with the utmost of enthusiasm."
   :abilities {::char5e/int 2}
   :size :small
   :speed 25
   :darkvision 60
   :languages ["Gnomish" "Common"]
   :modifiers [(mod5e/saving-throw-advantage [:magic] [::char5e/int ::char5e/wis ::char5e/cha])]
   :traits [{:name "Gnome Cunning"
             :page 37
             :summary "Advantage on INT, WIS, and CHA saves against magic"}]
   :subraces
   [{:name "Rock Gnome"
     :abilities {::char5e/con 1}
     :modifiers [(mod5e/tool-proficiency :tinkers-tools)]
     :traits [{:name "Artificer's Lore"
               :page 37
               :summary "Add 2X prof bonus on magical, alchemical, or technological item-related history checks."}
              {:name "Tinker"
               :page 37
               :summary "Construct tiny clockwork devices."}]}
    #_{:name "Forest Gnome"
     :abilities {::char5e/dex 1}
     :modifiers [(mod5e/spells-known 0 :minor-illusion ::char5e/int "Forest Gnome")]
     :traits [{:name "Speak with Small Beasts"
               :page 37
               :summary "Communicate with Small or smaller beasts."}]}]})

(defn half-elf-option-cfg [language-map]
  {:name "Half-Elf"
   :key :half-elf
   :help "Half-elves are charismatic, and bear a resemblance to both their elvish and human parents and share many of the traits of each."
   :abilities {::char5e/cha 2}
   :size :medium
   :speed 30
   :darkvision 60
   :languages ["Common" "Elvish"]
   :selections [(opt5e/ability-increase-selection (disj (set char5e/ability-keys) ::char5e/cha) 2 true)
                (assoc
                 (opt5e/skill-selection 2)
                 ::t/ref
                 [:race :half-elf :skill-proficiency])
                (opt5e/language-selection-aux (vals language-map) 1)]
   :modifiers [(mod5e/saving-throw-advantage [:charmed])]
   :traits [{:name "Fey Ancestry"
             :page 39
             :summary "advantage on charmed saves and immune to sleep magic"}]})

(def half-orc-option-cfg
  {:name "Half-Orc"
   :key :half-orc
   :help "Half-orcs are strong and bear an unmistakable resemblance to their orcish parent. They tend to make excellent warriors, especially Barbarians."
   :abilities {::char5e/str 2 ::char5e/con 1}
   :size :medium
   :speed 30
   :darkvision 60
   :languages ["Common" "Orc"]
   :modifiers [(mod5e/skill-proficiency :intimidation)]
   :traits [{:name "Relentless Endurance"
             :page 41
             :summary "Drop to 1 hp instead of being reduced to 0."}
            {:name "Savage Attacks"
             :page 41
             :summary "On critical hit, add additional damage dice roll"}]})

(def tiefling-option-cfg
  {:name "Tiefling"
   :key :tiefling
   :help "Tieflings bear the distinct marks of their infernal ancestry: horns, a tail, pointed teeth, and solid-colored eyes. They are smart and charismatic."
   :abilities {::char5e/int 1 ::char5e/cha 2}
   :size :medium
   :speed 30
   :darkvision 60
   :languages ["Common" "Infernal"]
   :modifiers [(mod5e/trait-cfg
                {:name "Hellish Resistance"
                 :page 43
                 :summary "Resistance to fire damage"})
               (mod5e/dependent-trait
                {:name "Infernal Legacy"
                 :page 43
                 :summary (str "You know thaumaturgy and can cast "
                               (common/list-print
                                (let [lvl ?total-levels]
                                  (cond-> []
                                    (>= lvl 3) (conj "Hellish Rebuke")
                                    (>= lvl 5) (conj "Darkness"))))
                               " once per day. CHA is the spellcasting ability.")})
               (mod5e/damage-resistance :fire)
               (mod5e/spells-known 0 :thaumaturgy ::char5e/cha "Tiefling")
               (mod5e/spells-known 1 :hellish-rebuke ::char5e/cha "Tiefling" 3)
               (mod5e/spells-known 2 :darkness ::char5e/cha "Tiefling" 5)]})

(reg-sub
 ::races5e/plugin-subraces-map
 :<- [::races5e/plugin-subraces]
 (fn [plugin-subraces]
   (group-by :race plugin-subraces)))

(reg-sub
 ::classes5e/plugin-subclasses-map
 :<- [::classes5e/plugin-subclasses]
 (fn [plugin-subclasses]
   (group-by :class plugin-subclasses)))

(defn compare-keys [x y]
  (compare (:key x) (:key y)))

(reg-sub
 ::races5e/races
 :<- [::races5e/plugin-races]
 :<- [::races5e/plugin-subraces-map]
 :<- [::spells5e/spell-lists]
 :<- [::spells5e/spells-map]
 :<- [::langs5e/language-map]
 (fn [[plugin-races subraces-map spell-lists spells-map language-map]]
   (vec
    (into
     (sorted-set-by compare-keys)
     (map
      (fn [{:keys [key] :as race}]
        (if (subraces-map key)
          (update race :subraces concat (subraces-map key))
          race))
      (concat
       (reverse plugin-races)
       [dwarf-option-cfg
        (elf-option-cfg spell-lists spells-map language-map)
        halfling-option-cfg
        (human-option-cfg spell-lists spells-map language-map)
        dragonborn-option-cfg
        gnome-option-cfg
        (half-elf-option-cfg language-map)
        half-orc-option-cfg
        tiefling-option-cfg]))))))


(defn base-class-options [spell-lists spells-map plugin-subclasses-map language-map weapons-map invocations boons]
  [(classes5e/barbarian-option spell-lists spells-map plugin-subclasses-map language-map weapons-map)
   (classes5e/bard-option spell-lists spells-map plugin-subclasses-map language-map weapons-map)
   (classes5e/cleric-option spell-lists spells-map plugin-subclasses-map language-map weapons-map)
   (classes5e/druid-option spell-lists spells-map plugin-subclasses-map language-map weapons-map)
   (classes5e/fighter-option spell-lists spells-map plugin-subclasses-map language-map weapons-map)
   (classes5e/monk-option spell-lists spells-map plugin-subclasses-map language-map weapons-map)
   (classes5e/paladin-option spell-lists spells-map plugin-subclasses-map language-map weapons-map)
   (classes5e/ranger-option spell-lists spells-map plugin-subclasses-map language-map weapons-map)
   (classes5e/rogue-option spell-lists spells-map plugin-subclasses-map language-map weapons-map)
   (classes5e/sorcerer-option spell-lists spells-map plugin-subclasses-map language-map weapons-map)
   (classes5e/warlock-option spell-lists spells-map plugin-subclasses-map language-map  weapons-map invocations boons)
   (classes5e/wizard-option spell-lists spells-map plugin-subclasses-map language-map weapons-map)])

(reg-sub
 ::classes5e/classes
 :<- [::spells5e/spell-lists]
 :<- [::spells5e/spells-map]
 :<- [::classes5e/plugin-subclasses-map]
 :<- [::langs5e/language-map]
 :<- [::classes5e/plugin-classes]
 :<- [::classes5e/invocations]
 :<- [::classes5e/boons]
 :<- [::mi5e/custom-and-standard-weapons-map]
 (fn [[spell-lists spells-map plugin-subclasses-map language-map plugin-classes invocations boons weapons-map] _]
   ;; Defensive handling: ensure base classes always render even if plugin classes fail
   (let [base-classes (try
                        (base-class-options spell-lists spells-map plugin-subclasses-map language-map weapons-map invocations boons)
                        (catch js/Error e
                          (js/console.error "Failed to build base classes:" e)
                          []))
         plugin-class-options (keep
                               (fn [plugin-class]
                                 (try
                                   (opt5e/class-option
                                    spell-lists
                                    spells-map
                                    plugin-subclasses-map
                                    language-map
                                    weapons-map
                                    plugin-class)
                                   (catch js/Error e
                                     (js/console.warn "Skipping plugin class due to error:" (:key plugin-class) e)
                                     nil)))
                               plugin-classes)]
     (vec
      (into
       (sorted-set-by #(compare (::t/key %1) (::t/key %2)))
       (concat (reverse plugin-class-options) base-classes))))))

(reg-sub
 ::classes5e/class-map
 :<- [::classes5e/classes]
 (fn [classes]
   (common/map-by ::t/key classes)))

(reg-sub
 ::classes5e/class
 :<- [::classes5e/class-map]
 (fn [class-map [_ key]]
   (class-map key)))

(reg-sub
 ::races5e/race-map
 :<- [::races5e/races]
 (fn [races]
   (common/map-by-key races)))

(reg-sub
 ::races5e/race
 :<- [::races5e/race-map]
 (fn [race-map [_ key]]
   (race-map key)))

(reg-sub
 ::feats5e/feats
 :<- [::feats5e/plugin-feats]
 (fn [plugin-feats]
   (map
    (fn [feat]
      (assoc feat :edit-event [::feats5e/edit-feat feat]))
    plugin-feats)))

(reg-sub
 ::classes5e/invocations
 :<- [::classes5e/plugin-invocations]
 (fn [plugin-invocations]
   (map
    (fn [invocation]
      (assoc invocation :edit-event [::classes5e/edit-invocation invocation]))
    plugin-invocations)))

(reg-sub
 ::classes5e/boons
 :<- [::classes5e/plugin-boons]
 (fn [plugin-boons]
   (map
    (fn [boon]
      (assoc boon :edit-event [::classes5e/edit-boon boon]))
    plugin-boons)))

(reg-sub
 ::spells5e/plugin-spells
 :<- [::e5/plugin-vals]
 (fn [plugins _]
   (map
    (fn [spell]
      (assoc spell :edit-event [::spells5e/edit-spell spell]))
    (mapcat (comp vals ::e5/spells) plugins))))

(reg-sub
 ::spells5e/plugin-spells-map
 :<- [::spells5e/plugin-spells]
 (fn [plugin-spells _]
   (common/map-by-key plugin-spells)))

(reg-sub
 ::monsters5e/plugin-monsters
 :<- [::e5/plugin-vals]
 (fn [plugins _]
   (mapcat (comp vals ::e5/monsters) plugins)))

(reg-sub
 ::encounters5e/plugin-encounters
 :<- [::e5/plugin-vals]
 (fn [plugins _]
   (mapcat (comp vals ::e5/encounters) plugins)))

(defn true-types [m]
  (sequence
   (comp
    (filter val)
    (map key)
    (map common/kw-to-name))
   m))

(defn process-plugin-monster [{:keys [props traits] :as monster}]
  (let [{:keys [damage-resistance
                damage-immunity
                damage-vulnerability
                condition-immunity
                language]} props
        filtered-languages (true-types language)
        filtered-resistances (true-types damage-resistance)
        filtered-damage-immunities (true-types damage-immunity)
        filtered-vulnerabilities (true-types damage-vulnerability)
        filtered-condition-immunities (true-types condition-immunity)]
    (cond-> monster      
      (seq filtered-languages)
      (assoc :languages (s/join ", " filtered-languages))
      
      (seq filtered-resistances)
      (assoc :damage-resistances (s/join ", " filtered-resistances))

      (seq filtered-damage-immunities)
      (assoc :damage-immunities (s/join ", " filtered-damage-immunities))

      (seq filtered-vulnerabilities)
      (assoc :damage-vulnerabilities (s/join ", " filtered-vulnerabilities))

      (seq filtered-condition-immunities)
      (assoc :condition-immunities (s/join ", " filtered-condition-immunities)))))

(reg-sub
 ::monsters5e/monsters
 :<- [::monsters5e/plugin-monsters]
 (fn [plugin-monsters]
   (concat
    monsters5e/monsters
    (map
     process-plugin-monster
     plugin-monsters))))

(reg-sub
 ::encounters5e/encounters
 :<- [::encounters5e/plugin-encounters]
 (fn [plugin-encounters]
   plugin-encounters))

(reg-sub
 ::encounters5e/encounter-map
 :<- [::encounters5e/encounters]
 (fn [encounters]
   (common/map-by-key encounters)))

(reg-sub
 ::monsters5e/monster-map
 :<- [::monsters5e/monsters]
 (fn [monsters]
   (common/map-by-key monsters)))

(reg-sub
 ::monsters5e/monster
 :<- [::monsters5e/monster-map]
 (fn [monster-map [_ key]]
   (get monster-map key)))

(reg-sub
 ::monsters5e/sorted-monsters
 :<- [::monsters5e/monsters]
 :<- [::char5e/monster-sort-criteria]
 :<- [::char5e/monster-sort-direction]
 (fn [[monsters sort-criteria sort-direction]]
   (let [comparator (if (= sort-direction "asc") compare #(compare %2 %1))]
     (case sort-criteria
       "name" (sort-by :name comparator monsters)
       "cr" (sort-by :challenge comparator monsters)))))

(defn all-subtypes-removed? [subtypes hidden-subtypes]
  (and (seq subtypes)
       (seq hidden-subtypes)
       (->> subtypes
            (remove
              hidden-subtypes)
            empty?)))

(defn filter-monsters [monsters filter-text monster-filters]
  (let [lower-case-filter-text (s/lower-case filter-text)]
    (filter
      (fn [{:keys [name type subtypes size]}]
        (and (or (< (count filter-text) 3)
                 (s/includes? (s/lower-case name) lower-case-filter-text))
             (not (or (-> monster-filters :size size)
                      (-> monster-filters :type type)
                      (all-subtypes-removed? subtypes (:subtype monster-filters))))))
      monsters)))

(reg-sub
 ::monsters5e/filtered-monsters
 :<- [::monsters5e/sorted-monsters]
 :<- [::char5e/monster-text-filter]
 :<- [::char5e/monster-filters]
 (fn [[sorted-monsters filter-text monster-filters]]
   (filter-monsters sorted-monsters (or filter-text "") monster-filters)))

;; Unused — pre-derived name set from filtered monsters. Useful for autocomplete/search.
#_(reg-sub
   ::monsters5e/filtered-monster-names
   :<- [::monsters5e/filtered-monsters]
   (fn [filtered-monsters]
     (set (map :name filtered-monsters))))

(reg-sub
 ::spells5e/base-spells
 (fn [_]
   spells5e/spells))

;; Unused — superseded by ::spells5e/spells-map which includes plugin spells.
#_(reg-sub
   ::spells5e/base-spells-map
   :<- [::spells5e/base-spells]
   (fn [spells]
     (common/map-by-key spells)))

(reg-sub
 ::spells5e/spells
 :<- [::spells5e/plugin-spells]
 (fn [plugin-spells]
   (into
    (sorted-set-by compare-keys)
    (concat
     (reverse plugin-spells)
     spells5e/spells))))

(reg-sub
 ::spells5e/spells-for-level
 :<- [::spells5e/spells]
 (fn [spells [_ level]]
   (filter
    #(= (:level %) level)
    spells)))

(reg-sub
 ::spells5e/spells-map
 :<- [::spells5e/spells]
 (fn [spells]
   (reduce
    (fn [m {:keys [name key level] :as spell}]
      (assoc m (or key (common/name-to-kw name)) spell))
    {}
    spells)))

(defn merge-spell-lists [& spell-lists]
  (apply
   merge-with
   concat
   spell-lists))

(reg-sub
 ::spells5e/plugin-spell-lists
 :<- [::spells5e/plugin-spells]
 (fn [plugin-spells _]
   (reduce
    (fn [lists {:keys [key level spell-lists]}]
      (reduce-kv
       (fn [l k v]
         (if v
           (update-in l [k level] conj key)
           l))
       lists
       spell-lists))
    {}
    plugin-spells)))

(reg-sub
 ::spells5e/spell-lists
 :<- [::spells5e/plugin-spell-lists]
 (fn [plugin-spell-lists]
   (merge-with
    merge-spell-lists
    sl5e/spell-lists
    plugin-spell-lists)))

(reg-sub
 ::spells5e/spellcasting-classes
 (fn []
   (map
    (fn [kw]
      {:key kw
       :name (common/kw-to-name kw)})
    [:bard :cleric :druid :paladin :ranger :sorcerer :warlock :wizard])))

(defn spell-option [spells-map [_ spell-key ability-key class-name]]
   (let [spell (spells-map spell-key)
         level (:level spell)]
     (t/option-cfg
      {:name (str level " - " (:name spell))
       :key spell-key
       :modifiers [(mod5e/spells-known
                    (:level spell)
                    spell-key
                    ability-key
                    class-name)]})))

;; Unused — spell-option fn is called directly; these sub wrappers were never wired up.
#_(reg-sub
   ::spells5e/spell-option
   :<- [::spells5e/spells-map]
   spell-option)

#_(reg-sub
   ::spells5e/spell-options
   :<- [::spells5e/spells-map]
   :<- [::spells5e/spell-lists]
   (fn [[spells-map spell-lists] [_ ability-key class-name levels]]
     (apply concat
            (sequence
             (comp
              (map spell-lists)
              (map (fn [spell-key]
                     (spell-option spells-map [nil spell-key ability-key class-name]))))
             levels))))

(reg-sub
 ::spells5e/builder-item
 (fn [db _]
   (::spells5e/builder-item db)))

(reg-sub
 ::bg5e/builder-item
 (fn [db _]
   (::bg5e/builder-item db)))

(reg-sub
 ::races5e/builder-item
 (fn [db _]
   (::races5e/builder-item db)))

(reg-sub
 ::races5e/subrace-builder-item
 (fn [db _]
   (::races5e/subrace-builder-item db)))

(reg-sub
 ::classes5e/subclass-builder-item
 (fn [db _]
   (::classes5e/subclass-builder-item db)))

(reg-sub
 ::classes5e/invocation-builder-item
 (fn [db _]
   (::classes5e/invocation-builder-item db)))

(reg-sub
 ::classes5e/boon-builder-item
 (fn [db _]
   (::classes5e/boon-builder-item db)))

(reg-sub
 ::classes5e/builder-item
 (fn [db _]
   (::classes5e/builder-item db)))

(reg-sub
 ::feats5e/builder-item
 (fn [db _]
   (::feats5e/builder-item db)))

(reg-sub
 ::langs5e/builder-item
 (fn [db _]
   (::langs5e/builder-item db)))

(reg-sub
 ::monsters5e/builder-item
 (fn [db _]
   (::monsters5e/builder-item db)))

(reg-sub
 ::encounters5e/builder-item
 (fn [db _]
   (::encounters5e/builder-item db)))

(reg-sub
 ::selections5e/builder-item
 (fn [db _]
   (::selections5e/builder-item db)))

(reg-sub
 ::combat5e/tracker-item
 (fn [db _]
   (::combat5e/tracker-item db)))


(reg-sub
 ::monsters5e/alignments
 (fn [db _]
   (into (sorted-set) (map :alignment) monsters5e/monsters)))

(reg-sub
 ::monsters5e/challenge-ratings
 (fn [db _]
   (into (sorted-set)
         (keep
          (fn [pair]
            (when (pos? (second pair))
            (first pair)))
            monsters5e/challenge-ratings))))

;; Unused — proficiency predicate on builder-item graph. Useful for conditional UI.
#_(reg-sub
   ::classes5e/has-prof?
   :<- [::classes5e/builder-item]
   (fn [class [_ prof-type prof-key]]
     (some? (get-in class [:profs prof-type prof-key]))))
