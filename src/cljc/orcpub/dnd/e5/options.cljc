(ns orcpub.dnd.e5.options
  (:require [clojure.string :as s]
            [clojure.set :as sets]
            [orcpub.common :as common]
            [orcpub.template :as t]
            [orcpub.entity :as entity]
            [orcpub.dice :as dice]
            [orcpub.entity-spec :as es]
            [orcpub.modifiers :as mods]
            [orcpub.dnd.e5.character :as character]
            [orcpub.dnd.e5.character.equipment :as char-equip]
            [orcpub.dnd.e5.modifiers :as modifiers]
            [orcpub.dnd.e5.weapons :as weapons]
            [orcpub.dnd.e5.units :as units5e]
            [orcpub.dnd.e5.races :as races]
            [orcpub.dnd.e5.armor :as armor]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.equipment :as equipment]
            [orcpub.dnd.e5.spell-lists :as sl]
            [orcpub.dnd.e5.display :as disp]
            [orcpub.dnd.e5.skills :as skills]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.event-handlers :as eh]
            [orcpub.components :as comps]
            [re-frame.core :refer [dispatch subscribe]])
  #?(:cljs (:require-macros [orcpub.dnd.e5.modifiers :as modifiers])))

#?(:cljs (enable-console-print!))

(def alignment-titles
  ["Lawful Good" "Lawful Neutral" "Lawful Evil" "Neutral Good" "Neutral" "Neutral Evil" "Chaotic Good" "Chaotic Neutral" "Chaotic Evil"])

(def xps [0 300 900 2700 6500 14000 23000 34000 48000 64000 85000 100000 120000 140000 165000 195000 225000 265000 305000 355000])

(def levels
  (map-indexed
   (fn [i xp] {:level (inc i) :min-xp xp})
   xps))

(def level-xps
  (zipmap
   (map inc (range))
   xps))

(def alignments
  (map
   (fn [alignment]
     {:name alignment
      :key (common/name-to-kw alignment)})
   alignment-titles))

(def abilities
  [{:key ::character/str
    :name "Strength"
    :abbr "STR"}
   {:key ::character/dex
    :name "Dexterity"
    :abbr "DEX"}
   {:key ::character/con
    :name "Constitution"
    :abbr "CON"}
   {:key ::character/int
    :name "Intelligence"
    :abbr "INT"}
   {:key ::character/wis
    :name "Wisdom"
    :abbr "WIS"}
   {:key ::character/cha
    :name "Charisma"
    :abbr "CHA"}])

(def abilities-map
  (common/map-by-key abilities))

(def conditions
  [{:name "Blinded"
    :key :blinded
    :icon "sight-disabled"}
   {:name "Charmed"
    :key :charmed
    :icon "smitten"}
   {:name "Deafened"
    :key :deafened
    :icon "hearing-disabled"}
   {:name "Exhausted"
    :key :exhausted
    :icon "knockout"}
   {:name "Frightened"
    :key :frightened
    :icon "terror"}
   {:name "Grappled"
    :key :grappled
    :icon "grab"}
   {:name "Incapacitated"
    :key :incapacitated
    :icon "cement-shoes"}
   {:name "Invisible"
    :key :invisible
    :icon "invisible"}
   {:name "Paralyzed"
    :key :paralyzed
    :icon "oppression"}
   {:name "Petrified"
    :key :petrified
    :icon "stone-block"}
   {:name "Poisoned"
    :key :poisoned
    :icon "vomiting"}
   {:name "Prone"
    :key :prone
    :icon "despair"}
   {:name "Restrained"
    :key :restrained
    :icon "imprisoned"}
   {:name "Stunned"
    :key :stunned
    :icon "knockout"}
   {:name "Unconscious"
    :key :unconscious
    :icon "coma"}])

(def damage-types
  [:acid
   :bludgeoning
   :cold
   :fire
   :force
   :lightning
   :necrotic
   :piercing
   :poison
   :psychic
   :radiant
   :slashing
   :thunder])

(def conditions-map
  (common/map-by-key (common/add-keys conditions)))

(defn skill-option [skill]
  (t/option-cfg
   {:name (:name skill)
    :icon (:icon skill)
    :key (:key skill)
    :help (:description skill)
    :prereqs [(t/option-prereq
               "You already have this skill"
               (fn [c]
                 (let [skill-profs @(subscribe [::character/skill-profs nil c])]
                   (not (get skill-profs (:key skill))))))]
    :modifiers [(modifiers/skill-proficiency (:key skill))]}))

(defn weapon-proficiency-option [{:keys [name key]}]
  (t/option-cfg
   {:name name
    :modifiers [(modifiers/weapon-proficiency key)]}))

(defn tool-option [tool]
  (t/option-cfg
   {:name (:name tool)
    :key (:key tool)
    :icon (:icon tool)
    :modifiers [(modifiers/tool-proficiency (:key tool))]}))

(defn weapon-option [weapon & [num]]
  (t/option-cfg
   {:name (:name weapon)
    :key (:key weapon)
    :help (:description weapon)
    :modifiers [(modifiers/weapon (:key weapon) {::char-equip/equipped? true
                                                 ::char-equip/quantity (or num 1)})]}))

(defn weapon-options [weapons & [num]]
  (map
   #(weapon-option % num)
   weapons))

(defn simple-melee-weapon-options [num weapons]
  (weapon-options
   (filter
    #(and (= :simple (::weapons/type %)) (::weapons/melee? %))
    weapons)
   num))

(defn martial-weapon-options [num weapons]
  (weapon-options
   (filter
    #(= :martial (::weapons/type %))
    weapons)
   num))

(defn simple-weapon-options [num weapons]
  (weapon-options
   (filter
    #(= :simple (::weapons/type %))
    weapons)
   num))

(defn skill-options [skills]
  (map
   skill-option
   skills))

(defn weapon-proficiency-options [weapons]
  (map
   weapon-proficiency-option
   weapons))

(defn tool-options [tools]
  (map
   tool-option
   tools))

(defn ability-bonus [ability-value]
  (- (int (/ ability-value 2)) 5))

(defn ability-bonus-str [ability-value]
  (common/bonus-str (ability-bonus ability-value)))

(defn get-raw-abilities [character]
  (get-in character [::entity/options :ability-scores ::entity/value]))

(defn ability-increase-selection-2 [{:keys [ability-keys num-increases min max different? modifier-fn modifier-fns]}]
  (t/selection-cfg
   {:name "Ability Score Improvement"
    :key :asi
    :min (or num-increases min)
    :max (or num-increases max)
    :tags #{:ability-scores}
    :different? different?
    :multiselect? true
    :options (map
              (fn [k]
                (t/option-cfg
                 {:name (:name (abilities-map k))
                  :key k
                  :modifiers (concat
                              [(if modifier-fn
                                 (modifier-fn k)
                                 (modifiers/level-ability-increase k 1))]
                              (map
                               #(% k)
                               modifier-fns))}))
              (or ability-keys
                  character/ability-keys))}))

(defn ability-increase-selection [ability-keys num-increases & [different? modifier-fns]]
  (ability-increase-selection-2 {:ability-keys ability-keys
                                 :num-increases num-increases
                                 :different? different?
                                 :modifier-fns modifier-fns}))

(defn ability-increase-option [num-increases different? ability-keys]
  (t/option-cfg
   {:name "Ability Score Improvement"
    :key :ability-score-improvement
    :selections [(ability-increase-selection ability-keys num-increases different?)]
    :modifiers [(modifiers/deferred-ability-increases)]}))

(defn min-ability [ability-kw min-value]
  (fn [c] (>= (ability-kw @(subscribe [::character/abilities nil c])) min-value)))

(defn ability-prereq [ability-kw min-value]
  (t/option-prereq (str "Requires " (s/upper-case (name ability-kw)) " " min-value " or higher")
                   (min-ability ability-kw min-value)))

(defn armor-prereq [armor-kw]
  (t/option-prereq (str "Requires proficiency with " (name armor-kw) " armor")
                   (fn [c] (let [prof-keys @(subscribe [::character/armor-profs nil c])]
                             (boolean (and prof-keys (prof-keys armor-kw)))))))

(def elemental-disciplines
  [(t/option-cfg
    {:name "Breath of Winter"
     :modifiers [(modifiers/action
                  {:name "Breath of Winter"
                   :level 17
                   :page 81
                   :summary "spend 6 ki to cast cone of cold"})]})
   (t/option-cfg
    {:name "Clench of the North Wind"
     :modifiers [(modifiers/action
                  {:name "Clench of the North Wind"
                   :page 81
                   :level 6
                   :summary "spend 3 ki to cast hold person"})]})
   (t/option-cfg
    {:name "Eternal Mountain Defense"
     :modifiers [(modifiers/action
                  {:name "Eternal Mountain Defense"
                   :level 17
                   :page 81
                   :summary "spend 5 ki to cast stoneskin on yourself"})]})
   (t/option-cfg
    {:name "Fangs of the Fire Snake"
     :modifiers [(modifiers/trait-cfg
                  {:name "Fangs of the Fire Snake"
                   :page 81
                   :summary "spend 1 ki point when you use Attack action to increase your unarmed strike reach by 10 ft. You unarmed strike deals fire damage and if you spend 1 more ki it deals an extra 2d10 damage"})]})
   (t/option-cfg
    {:name "Fist of Four Thunders"
     :modifiers [(modifiers/action
                  {:name "Fist of Four Thunders"
                   :page 81
                   :summary "spend 2 ki to cast thunderwave"})]})
   (t/option-cfg
    {:name "Fist of Unbroken Air"
     :modifiers [(modifiers/action
                  {:name "Fist of Unbroken Air"
                   :page 81
                   :summary (str "spend 2 + X ki, a creature within 30 ft. takes 3d10 + Xd10 damage on failed DC " (?spell-save-dc ::character/wis) " STR save, is pushed up to 20 ft., and is knocked prone. On successful save it just takes half damage.")})]})
   (t/option-cfg
    {:name "Flames of the Phoenix"
     :modifiers [(modifiers/action
                  {:name "Flames of the Phoenix"
                   :level 11
                   :page 81
                   :summary "spend 4 ki to cast fireball"})]})
   (t/option-cfg
    {:name "Gong of the Summit"
     :modifiers [(modifiers/action
                  {:name "Gong of the Summit"
                   :page 81
                   :level 6
                   :summary "spend 3 ki to cast shatter"})]})
   (t/option-cfg
    {:name "Mist Stance"
     :modifiers [(modifiers/action
                  {:name "Mist Stance"
                   :page 81
                   :level 11
                   :summary "spend 4 ki to cast gaseous form on yourself"})]})
   (t/option-cfg
    {:name "Ride the Wind"
     :modifiers [(modifiers/action
                  {:name "Ride the Wind"
                   :page 81
                   :level 11
                   :summary "spend 4 ki to cast fly on yourself"})]})
   (t/option-cfg
    {:name "River of Hungry Flame"
     :modifiers [(modifiers/action
                  {:name "River of Hungry Flame"
                   :page 81
                   :level 17
                   :summary "spend 5 ki to cast wall of fire"})]})
   (t/option-cfg
    {:name "Rush of the Gale Spirits"
     :modifiers [(modifiers/action
                  {:name "Rush of the Gale Spirits"
                   :page 81
                   :summary "spend 2 ki to cast gust of wind"})]})
   (t/option-cfg
    {:name "Shape of the Flowing River"
     :modfiers [(modifiers/action
                 {:name "Shape of the Flowing River"
                  :page 81
                  :summary "spend 1 ki to transform ice to water, and vice versa, reshape ice"})]})
   (t/option-cfg
    {:name "Sweeping Cinder Strike"
     :modifiers [(modifiers/action
                  {:name "Sweeping Cinder Strike"
                   :page 81
                   :summary "spend 2 ki to cast burning hands"})]})
   (t/option-cfg
    {:name "Water Whip"
     :modifiers [(modifiers/bonus-action
                  {:name "Water Whip"
                   :page 81
                   :summary (str "spend 2 + X ki, a creature within 30 ft. takes 3d10 + Xd10 damage on failed DC " (?spell-save-dc ::character/wis) " DEX save, is pulled up to 25 ft. or knocked prone. On successful save it just takes half damage.")})]})
   (t/option-cfg
    {:name "Wave of Rolling Earth"
     :modifiers [(modifiers/action
                  {:name "Wave of Rolling Earth"
                   :level 17
                   :page 81
                   :summary "spend 6 ki to cast wall of stone"})]})])

(defn monk-elemental-disciplines []
  (t/selection-cfg
   {:name "Elemental Disciplines"
    :tags #{:class}
    :ref [:class :monk :elemental-disciplines]
    :multiselect? true
    :options elemental-disciplines}))

(defn language-option [{:keys [name key]}]
  (t/option-cfg
   {:name name
    :modifiers [(modifiers/language key)]
    :prereqs [(t/option-prereq
               "You already have this language"
               (fn [c] (not (get @(subscribe [::character/languages nil c]) key))))]}))

(defn key-to-name [key]
  (s/join " " (map s/capitalize (s/split (name key) #"-"))))

(defn spell-field [name value]
  [:div.m-b-2
   [:span.f-w-b (str name ": ")]
   [:span.f-w-n value]])

(defn spell-help [{:keys [school casting-time range duration description summary source page]}]
  [:div
   [:div.m-b-5
    (spell-field "School" school)
    (spell-field "Casting Time" casting-time)
    (spell-field "Range" range)
    (spell-field "Duration" duration)]
   [:div.f-w-n (if (or description summary)
                 (doall
                  (map-indexed
                   (fn [i p]
                     ^{:key i} [:p.m-t-5 p])
                   (s/split (or description summary) #"\n"))))]
   #_(if source
     (let [{:keys [abbr url]} (disp/sources source)]
       [:div.f-w-n
        [:span "(see"]
        [:a.m-l-5 {:href url :target :_blank} abbr]
        [:span.m-l-5 (str "page " page)]
        [:span " for more details)"]]))])

(defn using-source? [option-sources source]
  (or (nil? source)
      (= :phb source)
      (get option-sources source)))

(defn spell-option [spells-map spellcasting-ability class-name key & [prepend-level? qualifier]]
  (let [{:keys [name level source edit-event] :as spell} (spells-map key)]
    (t/option-cfg
     {:name (if prepend-level? (str level " - " name) name)
      :key key
      :edit-event edit-event
      :help (spell-help spell)
      :prereqs [(t/option-prereq
                 "You already know this spell"
                 (fn [c] (let [spells-known @(subscribe [::character/spells-known nil c])]
                           (or (not spells-known)
                               (not-any?
                                (fn [[[_ kw]]]
                                  (= key kw))
                                (get spells-known level))))))]
      :modifiers [(modifiers/spells-known level key spellcasting-ability class-name nil qualifier)]})))


(def memoized-spell-option (memoize spell-option))

(defn spell-options [spells-map spells spellcasting-ability class-name & [prepend-level? qualifier]]
  (map
   #(memoized-spell-option spells-map spellcasting-ability class-name % prepend-level? qualifier)
   (sort spells)))

(defn spell-level-title [class-name level]
  (str class-name (if (and level (zero? level)) " Cantrips Known" (str " Spells Known" (if level (str " " level))))))

(defn spell-selection [spell-lists spells-map {:keys [title class-key level spellcasting-ability class-name num prepend-level? spell-keys options min max exclude-ref? ref]}]
  (let [title (or title (spell-level-title class-name level))
        kw (common/name-to-kw title)
        ref (or ref (if (not exclude-ref?) [:class class-key kw]))]
     (t/selection-cfg
      {:name title
       :key kw
       :ref ref
       :order (if (and level (zero? level)) 0 1)
       :multiselect? true
       :options (or options
                    (spell-options
                     spells-map
                     (or spell-keys (get-in spell-lists [class-key level]))
                     spellcasting-ability
                     class-name
                     prepend-level?))
       :min (or min num)
       :max (or max num)
       :tags #{:spells}})))

(defn spell-slot-schedule [level-factor]
  (case level-factor
    1 {1 {1 2}
       2 {1 1}
       3 {1 1
          2 2}
       4 {2 1}
       5 {3 2}
       6 {3 1}
       7 {4 1}
       8 {4 1}
       9 {4 1
          5 1}
       10 {5 1}
       11 {6 1}
       13 {7 1}
       15 {8 1}
       17 {9 1}
       18 {5 1}
       19 {6 1}
       20 {7 1}}
    2 {2 {1 2}
       3 {1 1}
       5 {1 1
          2 2}
       7 {2 1}
       9 {3 2}
       11 {3 1}
       13 {4 1}
       15 {4 1}
       17 {4 1
           5 1}
       19 {5 1}}
    3 {3 {1 2}
       4 {1 1}
       7 {1 1
          2 2}
       10 {2 1}
       13 {3 2}
       16 {3 1}
       19 {4 1}}
    {}))

(defn total-slots [level level-factor]
  (let [schedule (spell-slot-schedule level-factor)]
    (reduce
     (fn [m lvl]
       (merge-with + m (schedule lvl)))
     {}
     (range 1 (inc level)))))

(defn spell-tags [cls-key-nm level]
  #{:spells (keyword (str cls-key-nm "-spells")) (keyword (str "level-" level))})

(defn bard-magical-secrets [spells-map min-level]
  (let [max-level (key (last (total-slots min-level 1)))
        spells-by-level (group-by :level (vals spells-map))
        filtered-spells-by-level (select-keys spells-by-level (range 0 (inc max-level)))]
    (t/selection-cfg
     {:name "Bard Magical Secrets"
      :tags #{:spells}
      :min 2
      :max 2
      :ref [:class :bard :magical-secrets]
      :options (mapcat
                (fn [[lvl spells]]
                  (map
                   (fn [{:keys [name] :as spell}]
                     (let [key (or (:key spell) (common/name-to-kw name))]
                       (spell-option spells-map ::character/cha "Bard" key true)))
                   spells))
                filtered-spells-by-level)})))

(defn cantrip-selections [spell-lists spells-map class-key class-name ability cantrips-known]
  (reduce
   (fn [m [k v]]
     (assoc m k [(spell-selection spell-lists
                                  spells-map
                                  {:class-key class-key
                                   :level 0
                                   :spellcasting-ability ability
                                   :class-name class-name
                                   :num v})]))
   {}
   cantrips-known))

(defn apply-spell-restriction [spells-map spell-keys restriction]
  (if restriction
    (filter
     (fn [spell-key]
       (restriction (spells-map spell-key)))
     spell-keys)
    spell-keys))

(defn class-key-name [cls-key cls-nm]
  (if cls-key
    (name cls-key)
    (common/name-to-kw cls-nm)))

(defn spell-selection-key [cls-key-nm]
  (keyword (str cls-key-nm "-spells-known")))


(defn spells-known-selections [spell-lists
                               spells-map
                               {:keys [class-key
                                       level-factor
                                       spells-known
                                       spell-list-kw
                                       known-mode
                                       spells
                                       ability
                                       slot-schedule] :as cfg}
                               cls-cfg]
  (reduce
   (fn [m [cls-lvl v]]
     (let [[num restriction] (if (number? v) [v] ((juxt :num :restriction) v))
           slots (or (if slot-schedule (slot-schedule cls-lvl)) (total-slots cls-lvl level-factor))
           all-spells (select-keys
                       (or spells (spell-lists (or spell-list-kw class-key)))
                       (keys slots))
           acquire? (= :acquire known-mode)]
       (let [options (flatten
                      (map
                       (fn [[lvl spell-keys]]
                         (let [spell-keys (vec spell-keys)
                               filtered-keys (apply-spell-restriction spells-map spell-keys restriction)]
                           (map
                            (fn [spell-key]
                              (let [spell (spells-map spell-key)]
                                #?@(:cljs
                                    [(if (nil? spell) (js/console.warn (str "No spell found for key: " spell-key)))
                                     (if (nil? (:name spell)) (js/console.warn (str "Spell is missing name: " spell-key)))])
                                (memoized-spell-option
                                 spells-map
                                 ability
                                 (:name cls-cfg)
                                 spell-key
                                 true)))
                            filtered-keys)))
                       all-spells))]
         (assoc m cls-lvl
                [(let [cls-key-nm (class-key-name (:key cls-cfg) (:name cls-cfg))
                       kw (spell-selection-key cls-key-nm)
                       cls-nm (:name cls-cfg)]
                   (spell-selection
                    spell-lists
                    spells-map
                    {:class-key class-key
                     :class-name cls-nm
                     :min num
                     :max (if (not acquire?) num)
                     :options options}))]))))
   {}
   spells-known))

(defn spellcasting-template [spell-lists
                             spells-map
                             {:keys [class-key
                                     level-factor
                                     cantrips-known
                                     spells-known
                                     known-mode
                                     ability
                                     spell-list] :as cfg}
                             cls-cfg]
  (let [spell-lists (if spell-list
                      (assoc spell-lists class-key spell-list)
                      spell-lists)
        spell-selections (spells-known-selections spell-lists spells-map cfg cls-cfg)
        cantrip-selections (cantrip-selections spell-lists spells-map class-key (:name cls-cfg) ability cantrips-known)]
    {:selections (merge-with
                  concat
                  cantrip-selections
                  spell-selections)}))

(defn magic-initiate-option [spells-map class-key class-name spellcasting-ability spell-lists]
  (t/option-cfg
   {:name (name class-key)
    :selections [(t/selection-cfg
                  {:name "Cantrip"
                   :order 1
                   :tags #{:spells}
                   :options (spell-options spells-map (get-in spell-lists [class-key 0]) spellcasting-ability class-name)
                   :min 2
                   :max 2})
                 (t/selection-cfg
                  {:name "Level 1 Spell"
                   :order 2
                   :tags #{:spells}
                   :options (spell-options spells-map (get-in spell-lists [class-key 1]) spellcasting-ability class-name)
                   :min 1
                   :max 1})]}))

(defn ritual-spell? [spell]
  (:ritual spell))

(defn ritual-caster-option [spells-map class-key class-name spellcasting-ability spell-lists]
  (t/option-cfg
   {:name (name class-key)
    :key class-key
    :selections [(t/selection-cfg
                  {:name "Level 1 Ritual Spells"
                   :tags #{:spells}
                   :options (spell-options
                             spells-map
                             (filter (fn [spell-kw] (ritual-spell? (spells-map spell-kw))) (get-in spell-lists [class-key 1]))
                             spellcasting-ability
                             class-name
                             false
                             "Ritual Only")
                   :min 2
                   :max 2
                   :order 7})
                 (t/selection-cfg
                  {:name "Additional Ritual Spells"
                   :tags #{:spells}
                   :show-if-zero? true
                   :multiselect? true
                   :min 0
                   :max nil
                   :order 8
                   :options (spell-options
                             spells-map
                             (filter
                              (fn [spell-kw] (ritual-spell? (spells-map spell-kw)))
                              (apply concat
                                     (vals (get spell-lists class-key))))
                             spellcasting-ability
                             class-name
                             false
                             "Ritual Only")})]}))

(defn spell-sniper-option [spells-map class-key class-name spellcasting-ability spell-lists]
  (let [options (spell-options spells-map (filter (fn [spell-kw] (:attack-roll? (spells-map spell-kw))) (get-in spell-lists [class-key 0])) spellcasting-ability class-name)]
    (t/option-cfg
     {:name (name class-key)
      :key class-key
      :prereqs [(t/option-prereq
                 "There are no attack cantrips for this class"
                 (fn [_] (seq options)))]
      :selections [(t/selection-cfg
                    {:name "Attack Cantrip"
                     :tags #{:spells}
                     :options options})]})))

(defn weapon-proficiency-selection-2 [weapon-map weapon-proficiency-options]
  (let [{num :choose options :options} weapon-proficiency-options
        weapons (if (:any options)
                  (vals weapon-map)
                  (map weapon-map (keys options)))]
    (t/selection-cfg
     {:name "Weapon Proficiency"
      :options (map
                (fn [{:keys [name key :db/id] :as item}]
                  (t/option-cfg
                   {:name (or name (::mi/name item))
                    :key key}))
                weapons)
      :multiselect? true
      :tags #{:profs}
      :min (or num 1)
      :max (or num 1)})))

(defn language-selection-aux [languages num]
  (t/selection-cfg
   {:name "Languages"
    :options (map
              (fn [lang]
                (language-option lang))
              languages)
    :ref [:languages]
    :multiselect? true
    :tags #{:profs :language-profs}
    :min (or num 0)
    :max num}))

(defn language-selection [language-map language-options]
  (let [{lang-num :choose lang-options :options} language-options
        languages (if (:any lang-options)
                    (vals language-map)
                    (map language-map (keys lang-options)))]
    (language-selection-aux languages lang-num)))

(defn any-language-selection [language-map & [num]]
  (language-selection-aux (vals language-map) num))

#_(defn maneuver-option [name & [desc]]
  (t/option-cfg
   {:name name
    :modifiers [(modifiers/trait (str name " Maneuver")
                      desc)]}))

#_(defn mod-maneuver-option [name mods]
  (t/option-cfg
   {:name name
    :modifiers mods}))

(defn proficiency-help [num singular plural]
  (str "Select additional " (if (> num 1) plural singular) " for which you are proficient."))

(defn skill-selection-2 [{:keys [options num min max order key prereq-fn]}]
  (t/selection-cfg
   {:name "Skill Proficiency"
    :key key
    :order (or order 0)
    :help (proficiency-help (or num min) "a skill" "skills")
    :options (let [key-set (set options)]
               (skill-options
                (filter
                 (comp key-set :key)
                 skills/skills)))
    :min (or min num)
    :max (or max num)
    :multiselect? true
    ;;:ref [:skill-profs]
    :tags #{:skill-profs :profs}
    :prereq-fn prereq-fn}))

(defn skill-prof-or-expertise [skill-kw source]
  [(modifiers/skill-proficiency skill-kw source)
   (modifiers/skill-expertise skill-kw [(some
                                         (fn [[k v]]
                                           (not= k source))
                                         (?skill-profs skill-kw))])])

(defn tool-prof-or-expertise [tool-kw source]
  [(modifiers/tool-proficiency tool-kw false nil source)
   (modifiers/tool-expertise tool-kw [(some
                                         (fn [[k v]]
                                           (not= k source))
                                         (?tool-profs tool-kw))])])

(defn skill-or-expertise-selection [num skill-kws option-source]
  (t/selection-cfg
   {:name "Skill Proficiency"
    :order 0
    :tags #{:skill-profs :profs}
    :options (map
              (fn [skill-kw]
                (let [{:keys [name icon]} (skills/skills-map skill-kw)]
                  (t/option-cfg
                   {:name name
                    :icon icon
                    :modifiers [(skill-prof-or-expertise skill-kw option-source)]})))
              skill-kws)}))

(defn skill-expertise-selection [skill-kws num]
  (t/selection-cfg
   {:name "Skill Expertise (Double Proficiency)"
    :tags #{:profs}
    :min num
    :max num
    :options (map
              (fn [skill-kw]
                (let [{:keys [name icon]} (skills/skills-map skill-kw)]
                  (t/option-cfg
                   {:name name
                    :icon icon
                    :modifiers [(modifiers/skill-proficiency skill-kw)
                                (modifiers/skill-expertise skill-kw)]})))
              skill-kws)}))

(defn skill-selection
  ([num]
   (skill-selection-2 {:num num
                       :options (map :key skills/skills)}))
  ([options num & [order key prereq-fn]]
   (skill-selection-2 {:options options
                       :num num
                       :order order
                       :key key
                       :prereq-fn prereq-fn})))

(defn tool-proficiency-selection-2 [{:keys [num min max] :as cfg}]
  (t/selection-cfg
   (merge
    {:name "Tool Proficiency"
     :help (proficiency-help (or num min) "a tool" "tools")
     :multiselect 2
     :tags #{:tool-profs :profs}}
    (if num {:min num :max num})
    cfg)))

(defn tool-proficiency-selection [cfg]
  (tool-proficiency-selection-2
   cfg))

(defn tool-selection
  ([num]
   (tool-proficiency-selection
    {:options (tool-options equipment/tools)
     :num num}))
  ([options num]
   (tool-proficiency-selection
    {:options (tool-options
               (filter
                (comp (set options) :key)
                equipment/tools))
     :num num})))


(defn weapon-proficiency-selection
  ([num custom-and-standard-weapons]
   (t/selection-cfg
    {:name "Weapon Proficiency"
     :help (proficiency-help num "a weapon" "weapons")
     :options (weapon-proficiency-options custom-and-standard-weapons)
     :min num
     :max num
     :tags #{:weapon-profs :profs}}))
  ([options num custom-and-standard-weapons]
   (t/selection-cfg
    {:name "Weapon Proficiency"
     :help (proficiency-help num "a weapon" "weapons")
     :options (weapon-proficiency-options
               (filter
                (comp (set options) :key)
                custom-and-standard-weapons))
     :min num
     :max num
     :tags #{:weapon-profs :profs}})))

(defn skilled-selection [title]
  (t/selection-cfg
   {:name title
    :tags #{:profs}
    :options [(t/option-cfg
              {:name "Skill"
               :selections [(skill-selection 1)]})
             (t/option-cfg
              {:name "Tool"
               :selections [(tool-selection 1)]})]}))

#_(def maneuver-options
  [(maneuver-option "Commander's Strike"
                    "When you take Attack action, forgo one attack, expend a superiority die, give a creature an immediate reaction attack, adding superiority die to damage")
   (maneuver-option "Disarming Attack"
                    "When you hit with a weapon attack, expend a superiority die and force the target to drop an item of your choice on failed STR save")
   (maneuver-option "Distracting Strike"
                    "When you hit with a weapon attack, expend a superiority die, add die to damage, give advantage to next attack roll by someone else against the creature")
   (maneuver-option "Evasive Footwork"
                    "Add superiority die to AC when moving")
   (mod-maneuver-option
    "Feinting Attack"
    [(modifiers/bonus-action
      {:name "Feinting Attack Maneuver"
       :page 74
       :summary "feint attack on a creature and gain advantage on next attack against it, adding superiority die to damage"})])
   (mod-maneuver-option
    "Goading Attack"
    [(modifiers/dependent-trait
      {:name "Goading Attack Maneuver"
       :page 74
       :summary (str "add superiority die to a successful attack's damage, if target fails DC " ?maneuver-save-dc " WIS save, the next attack it makes must be against you or have disadvantage")})])
   (maneuver-option "Lunging Attack"
                    "increase melee attack reach by 5 ft., add superiority die to damage")
   (maneuver-option "Manuevering Attack"
                    "add superiority die to a successful attack's damage, choose a friendly creature that can move half it's speed as a reaction without opportunity attack from attack target")
   (mod-maneuver-option
    "Menacing Attack"
    [(modifiers/dependent-trait
      {:name "Menacing Attack Maneuver"
       :page 74
       :summary (str "add superiority die to a successful attack's damage, if target fails DC " ?maneuver-save-dc " WIS save, it becomes frightened of you until your next turn")})])
   (mod-maneuver-option
    "Parry"
    [(modifiers/reaction
      {:name "Parry Maneuver"
       :page 74
       :summary (str "reduce melee attack damage dealt to you by superiority die roll " (common/mod-str (?ability-bonuses ::character/dex)))})])
   (maneuver-option "Precision Attack"
                    "add superiority die to weapon attack roll")
   (mod-maneuver-option
    "Pushing Attack"
    [(modifiers/dependent-trait
      {:name "Pushing Attack Maneuver"
       :page 74
       :summary (str "add superiority die to a successful attack's damage, if target is Large or smaller and fails a DC " ?maneuver-save-dc " STR save, it is pushed 15 ft. away")})])
   (mod-maneuver-option
    "Rally"
    [(modifiers/bonus-action
      {:name "Rally Maneuver"
       :page 74
       :summary (str "give superiority die "
                     (common/mod-str (?ability-bonuses ::character/cha))
                     " temp HPs to a friendly creature")})])
   (mod-maneuver-option
    "Riposte"
    [(modifiers/reaction
      {:name "Riposte Maneuver"
       :page 74
       :summary "if a creature misses you with a melee attack, attack as a reaction and add superiority die to damage"})])
   (maneuver-option "Sweeping Attack"
                    "if you hit a creature with an attack roll, choose another creature within 5 ft., if the roll would hit the creature, it takes superiority die worth of damage")
   (mod-maneuver-option
    "Trip Attack"
    [(modifiers/dependent-trait
      {:name "Trip Attack Maneuver"
       :page 74
       :summary (str "add superiority die to successful attack's damage, if target fails a DC " ?maneuver-save-dc " STR save, it is knocked prone")})])])

(def can-cast-spell-prereq
  (t/option-prereq "Requires the ability to cast at least one spell."
                   (fn [c] (some (fn [[k v]] (seq v)) @(subscribe [::character/spells-known nil c])))))

(defn does-not-have-feat-prereq [kw]
  {::t/label "You already have this feat."
   ::t/prereq-fn (fn [c] (let [feats @(subscribe [::character/feats nil c])]
                           (not (and feats (feats kw)))))})

(defn feat-option [cfg & [multiselect?]]
  (let [kw (common/name-to-kw (:name cfg))
        summary (:summary cfg)]
    (t/option-cfg
     (cond-> cfg
       true (assoc :key kw :help summary)
       (not (:exclude-trait? cfg)) (update :modifiers
                                           conj
                                           (modifiers/trait-cfg
                                            {:name (str (:name cfg) " Feat")
                                             :page (:page cfg)
                                             :source (:source cfg)
                                             :summary summary}))
       true (update :modifiers
                    conj
                    (mods/set-mod ?feats kw))
       (not multiselect?) (update :prereqs conj (does-not-have-feat-prereq kw))))))

(def charge-summary "when you Dash, you can make 1 melee attack or shove as a bonus action; if you move 10 ft. before taking this bonus action you gain +5 damage to attack or shove 10 ft.")

(def defensive-duelist-summary "when you are hit with a melee attack, you can add your prof bonus to AC for the attack if you are wielding a finesse weapon you are proficient with")

#_(defn homebrew-spell-selection [spell-lists spells-map]
  (spell-selection
   spell-lists
   spells-map
   {:class-key :homebrew
    :class-name "Homebrew"
    :ref [:optional-content :homebrew :spells-known]
    :min 0
    :max nil
    :spell-keys (keys spells-map)}))

(def homebrew-tool-prof-selection
  (tool-proficiency-selection-2
   {:min 0
    :max nil
    :multiselect? true
    :ref [:tool-profs]
    :options (tool-options equipment/tools)}))

(def homebrew-skill-prof-selection
  (skill-selection-2 {:min 0
                      :max nil
                      :options (map :key skills/skills)}))

(defn homebrew-language-selection [language-map & [min max]]
  (t/selection-cfg
   {:name "Languages"
    :options (map
              (fn [lang]
                (language-option lang))
              (vals language-map))
    :multiselect? true
    :tags #{:profs :language-profs}
    :min (or min 0)
    :max max}))

(def homebrew-armor-prof-selection
  (t/selection-cfg
   {:name "Armor Proficiency"
    :key :armor-prof
    :tags #{:profs}
    :min 0
    :max nil
    :multiselect? true
    :options (map
              (fn [armor-type]
                (t/option-cfg
                 {:name (s/capitalize (name armor-type))
                  :key armor-type
                  :modifiers [(modifiers/armor-proficiency armor-type)]}))
              [:light :medium :heavy :shields])}))

(defn homebrew-weapon-prof-selection [weapon-map]
  (t/selection-cfg
   {:name "Weapon Proficiency"
    :key :weapon-prof
    :tags #{:profs}
    :min 0
    :max nil
    :multiselect? true
    :options (map
              (fn [{:keys [name key]}]
                (t/option-cfg
                 {:name name
                  :key key
                  :modifiers [(modifiers/weapon-proficiency key)]}))
              (conj
               (vals weapon-map)
               {:name "Simple"
                :key :simple}
               {:name "Martial"
                :key :martial}))}))

(def dual-wield-ac-mod
  (mods/vec-mod ?ac-bonus-fns
                (fn [_ _] 1)
                nil
                nil
                [(let [main-hand-weapon ?orcpub.dnd.e5.character/main-hand-weapon
                       off-hand-weapon ?orcpub.dnd.e5.character/off-hand-weapon
                       all-weapons-map @(subscribe [::mi/all-weapons-map])]
                   (and (and main-hand-weapon
                             (-> all-weapons-map
                                 main-hand-weapon
                                 ::weapons/melee?))
                        (and off-hand-weapon
                             (-> all-weapons-map
                                 off-hand-weapon
                                 ::weapons/melee?))))]))

(def dual-wield-weapon-mod
  (mods/modifier ?dual-wield-weapon? weapons/one-handed-weapon?))

(def medium-armor-master-max-bonus
  (mods/modifier ?max-medium-armor-bonus 3))

(def medium-armor-master-stealth
  (mods/fn-mod ?armor-stealth-disadvantage?
               (fn [armor]
                 (if (= :medium (:type armor))
                   false
                   (?armor-stealth-disadvantage? armor)))))

(defn custom-option-builder [name-sub name-event]
  [:div.m-t-10
   [:span "Name"]
   [comps/input-field
    :input
    @(subscribe name-sub)
    (fn [value]
      (dispatch (conj name-event value)))
    {:class-name "input"}]])

(defn feat-options [spell-lists spells-map]
  [#_(feat-option
      {:name "Alert"
       :icon "look-at"
       :page 165
       :summary "+5 initiative; can't be surprised; creatures don't gain advantage on attacks against you for being hidden"
       :modifiers [(modifiers/initiative 5)]})
   #_(feat-option
      {:name "Athlete"
       :icon "weight-lifting-up"
       :page 165
       :summary "increase STR or DEX by 1; standing up only uses 5 ft movement; climbing doesn't cost extra movement; make running long or high jump after moving only 5 ft."
       :selections [(ability-increase-selection [::character/str ::character/dex] 1 false)]})
   #_(feat-option
      {:name "Actor"
       :icon "drama-masks"
       :page 165
       :summary "increase CHA by 1; advantage on Deception and Performance when trying to pass as someone else; mimic the speech of a person you have heard"
       :modifiers [(modifiers/ability ::character/cha 1)]})
   #_(feat-option
      {:name "Charger"
       :icon "charging-bull"
       :page 165
       :summary charge-summary
       :modifiers [(modifiers/bonus-action
                    {:name "Charge"
                     :page 165
                     :summary charge-summary})]})
   #_(feat-option
      {:name "Crossbow Expert"
       :icon "crossbow"
       :page 165
       :summary "ignore loading property of crossbows you are proficient with; don't have disadvantage from being within 5 ft of hostile creature; when you Attack with 1 hand weapon, you can attack with a hand crossbow as bonus action"
       :modifiers [(modifiers/bonus-action
                    {:name "Crossbow Expert"
                     :page 165
                     :summary "when you Attack with 1 hand weapon, you can attack with a hand crossbow"})]})
   #_(feat-option
      {:name "Defensive Duelist"
       :icon "spinning-sword"
       :page 165
       :exclude-trait? true
       :summary defensive-duelist-summary
       :modifiers [(modifiers/reaction
                    {:name "Defensive Duelist"
                     :page 165
                     :summary defensive-duelist-summary})]
       :prereqs [(ability-prereq ::character/dex 13)]})
   #_(feat-option
      {:name "Dual Wielder"
       :icon "rogue"
       :page 165
       :summary "+1 AC bonus when wielding two melee weapons; two-weapon fighting with any one-handed melee weapon"
       :modifiers [dual-wield-weapon-mod
                   dual-wield-ac-mod]})
   #_(feat-option
      {:name "Dungeon Delver"
       :icon "dungeon-gate"
       :page 166
       :summary "advantage to detect secret doors; advantage on saves against and resistance to trap damage; search for traps at normal pace"
       :modifiers [(modifiers/damage-resistance :trap)
                   (modifiers/saving-throw-advantage [:traps])]})
   #_(feat-option
      {:name "Durable"
       :icon "hospital-cross"
       :page 166
       :exclude-trait? true
       :summary "increase CON by 1; when you roll Hit Die to regain HPs, the min points regained is 2X your CON modifier"
       :modifiers [(modifiers/ability ::character/con 1)
                   (modifiers/dependent-trait
                    {:name "Durable"
                     :page 166
                     :summary (str "when you roll Hit Die to regain HPs, the min points regained is " (* 2 (?ability-bonuses ::character/con)))})]})
   #_(feat-option
      {:name "Elemental Adept"
       :icon "wind-hole"
       :page 166
       :summary "select a damage type, your spells ignore resistance to that type and min damage die roll is 2"
       :prereqs [can-cast-spell-prereq]}
      true)
   (feat-option
    {:name "Grappler"
     :icon "muscle-up"
     :page 167
     :summary "advantage on attacks against creature you grapple; can use an action to pin the creature"
     :modifiers [(modifiers/action
                  {:name "Grappler"
                   :page 167
                   :summary "restrain a creature you are grappling"})]
     :prereqs [(ability-prereq ::character/str 13)]})
   #_(feat-option
      {:name "Great Weapon Master"
       :icon "broadsword"
       :page 167
       :summary "When you critical or reduce a creature to 0 HPs with melee weapon, make one melee weapon attack as bonus action. When you melee Attack with heavy weapon, you can take -5 on attack to deal +10 damage."
       :modifiers [(modifiers/bonus-action
                    {:name "Great Weapon Master"
                     :page 167
                     :summary "When you critical or reduce a creature to 0 HPs with melee weapon, make one melee weapon attack"})]})
   #_(feat-option
      {:name "Healer"
       :icon "medical-pack-alt"
       :page 167
       :summary "When you stabilize with healer's kit, the creature regains 1 HP; use a healer's kit to restore 1d6 + 4 + creature's max hit dice HPs"
       :modifiers [(modifiers/action
                    {:name "Healer Feat"
                     :page 167
                     :summary "use a healer's kit to restore 1d6 + 4 + creature's max hit dice HPs"})]})
   #_(feat-option
      {:name "Heavily Armored"
       :icon "lamellar"
       :summary "increase STR by 1; proficiency in heavy armor"
       :page 167
       :modifiers [(modifiers/heavy-armor-proficiency)
                   (modifiers/ability ::character/str 1)]
       :prereqs [(armor-prereq :medium)]})
   #_(feat-option
      {:name "Heavy Armor Master"
       :icon "gauntlet"
       :page 167
       :summary "increase STR by 1; when wearing heavy armor, slashing, piercing, and bludgeoning damage from non-magical weapons is 3 less"
       :modifiers [(modifiers/ability ::character/str 1)]
       :prereqs [(armor-prereq :heavy)]})
   #_(feat-option
      {:name "Inspiring Leader"
       :icon "public-speaker"
       :page 167
       :summary "give 6 friendly creatures within 30 ft. temp HPs equal to you CHA mod + your level"
       :prereqs [(ability-prereq ::character/cha 13)]})
   #_(feat-option
      {:name "Keen Mind"
       :icon "brain"
       :page 167
       :summary "increase INT by 1; always know which direction is north; know hours before sunset or sunrise; recall anything heard or seen within a month"
       :modifiers [(modifiers/ability ::character/int 1)]})
   #_(feat-option
      {:name "Lightly Armored"
       :icon "scale-mail"
       :page 167
       :summary "increase STR or DEX by 1; proficiency in light armor"
       :selections [(ability-increase-selection [::character/str ::character/dex] 1 false)]
       :modifiers [(modifiers/light-armor-proficiency)]})
   #_(feat-option
      {:name "Linguist"
       :icon "lips"
       :page 167
       :summary "increase INT by 1; learn 3 languages; create written ciphers"
       :selections [(language-selection languages 3)]
       :modifiers [(modifiers/ability ::character/int 1)]})
   #_(feat-option
      {:name "Lucky"
       :icon "clover"
       :page 167
       :summary "3 luck points, which you can use to roll an additional d20 when rolling an attack, save, or ability check, and choose which one to use"})
   #_(feat-option
      {:name "Mage Slayer"
       :icon "zeus-sword"
       :page 168
       :summary "use reaction to attack a caster within 5 ft.; impose disadvantage to a caster's concentration check when you attack; advantage on saves against spells cast within 5ft."
       :modifiers [(modifiers/reaction
                    {:name "Mage Slayer"
                     :range units5e/ft-5
                     :page 168
                     :summary "attack a creature that casts a spell"})]})
   #_(feat-option
      {:name "Magic Initiate"
       :icon "magic-palm"
       :page 168
       :summary "gain 2 cantrips and 1 1st level spell from a chosen class"
       :selections [(t/selection-cfg
                     {:name "Spell Class"
                      :order 0
                      :tags #{:spells}
                      :options [(magic-initiate-option :bard "Bard" ::character/cha sl/spell-lists)
                                (magic-initiate-option :cleric "Cleric" ::character/wis sl/spell-lists)
                                (magic-initiate-option :druid "Druid" ::character/wis sl/spell-lists)
                                (magic-initiate-option :sorcerer "Sorcerer" ::character/cha sl/spell-lists)
                                (magic-initiate-option :warlock "Warlock" ::character/cha sl/spell-lists)
                                (magic-initiate-option :wizard "Wizard" ::character/int sl/spell-lists)]})]})
   #_(feat-option
      {:name "Martial Adept"
       :icon "visored-helm"
       :page 168
       :summary "learn two Battle Master martial maneuvers using 1 d6 superiority die"
       :selections [(t/selection-cfg
                     {:name "Martial Maneuvers"
                      :tags #{:class}
                      :options maneuver-options
                      :min 2
                      :max 2})]})
   #_(feat-option
      {:name "Medium Armor Master"
       :icon "bracers"
       :page 168
       :summary "medium armor doesn't give disadvantage to Stealth; max DEX bonus to AC is 3 for medium armor"
       :modifiers [medium-armor-master-max-bonus
                   medium-armor-master-stealth]
       :prereqs [(armor-prereq :medium)]})
   #_(feat-option
      {:name "Mobile"
       :icon "move"
       :page 168
       :summary "speed increases by 10 ft.; Dash through difficult terrain doesn't cost extra movement; don't provoke opportunity attacks from a creature you made a melee attack against"
       :modifiers [(modifiers/speed 10)]})
   #_(feat-option
      {:name "Moderately Armored"
       :icon "shoulder-armor"
       :page 168
       :summary "increase STR or DEX by 1; gain proficiency with shields and medium armor"
       :selections [(ability-increase-selection [::character/str ::character/dex] 1 false)]
       :modifiers [(modifiers/medium-armor-proficiency)
                   (modifiers/shield-armor-proficiency)]
       :prereqs [(armor-prereq :light)]})
   #_(feat-option
      {:name "Mounted Combatant"
       :icon "cavalry"
       :page 168
       :summary "while mounted: advantage on attacks against unmounted creatures smaller than mount, force attack on mount to target you; mount takes no damage on sucessful DEX saves and half on failed"})
   #_(feat-option
      {:name "Observant"
       :icon "surrounded-eye"
       :page 168
       :summary "increase INT or WIS by 1; read lips; +5 bonus to passive Perception and passive Investigation"
       :selections [(ability-increase-selection [::character/int ::character/wis] 1 false)]
       :modifiers [(modifiers/passive-perception 5)
                   (modifiers/passive-investigation 5)]})
   #_(feat-option
      {:name "Polearm Master"
       :icon "halberd"
       :page 168
       :exclude-trait? true
       :summary "bonus attack with opposite end of quarterstaff, glaive, or halberd; opportunity attacks have the reach of glaive, pike, halberd, or quarterstaff"
       :modifiers [(modifiers/bonus-action
                    {:name "Polearm Master"
                     :page 168
                     :summary "when you make an Attack with a glaive, quarterstaff, or halberd, make an additionaal melee attack with the other end of the weapon, dealing d4 bludgeoning damage"})]})
   #_(feat-option
      {:name "Resilient"
       :icon "dodging"
       :page 168
       :summary "increase ability by 1 and gain proficiency in saves with that ability"
       :selections [(ability-increase-selection
                     character/ability-keys
                     1
                     false
                     [(fn [k] (modifiers/saving-throws nil k))])]})
   #_(feat-option
      {:name "Ritual Caster"
       :icon "gift-of-knowledge"
       :page 169
       :summary "choose a spellcaster class and learn 2 rituals from that class"
       :selections [(t/selection-cfg
                     {:name "Ritual Caster: Spell Class"
                      :tags #{:spells}
                      :options [(ritual-caster-option :bard "Bard" ::character/cha sl/spell-lists)
                                (ritual-caster-option :cleric "Cleric" ::character/wis sl/spell-lists)
                                (ritual-caster-option :druid "Druid" ::character/wis sl/spell-lists)
                                (ritual-caster-option :sorcerer "Sorcerer" ::character/cha sl/spell-lists)
                                (ritual-caster-option :warlock "Warlock" ::character/cha sl/spell-lists)
                                (ritual-caster-option :wizard "Wizard" ::character/int sl/spell-lists)]})]
       :prereqs [(t/option-prereq "Requires Intelligence or Wisdom 13 or higher"
                                  (fn [c]
                                    (let [{:keys [::character/wis ::character/int] :as abilities} @(subscribe [::character/abilities nil c])]
                                      (or (and wis (>= wis 13))
                                          (and int (>= int 13))))))]})
   #_(feat-option
      {:name "Savage Attacker"
       :icon "saber-slash"
       :page 169
       :summary "reroll melee weapon attack damage and use either total"})
   #_(feat-option
      {:name "Sentinal"
       :icon "guards"
       :page 169
       :summary "reduce target's speed to 0 when you hit with opportunity attack; opportunity attacks even when target Disengages; use reaction to make a weapon attack against a creature within 5 ft. that attacks another target"})
   #_(feat-option
      {:name "Sharpshooter"
       :icon "bullseye"
       :page 170
       :summary "no disadvantage for long range; ignore half and 3/4 cover; take -5 to ranged attack to gain +10 on damage"})
   #_(feat-option
      {:name "Shield Master"
       :icon "attached-shield"
       :page 170
       :summary "when Attacking use bonus action to shove; add shield's AC bonus to saves that target just you; take no damage on a sucessful save"
       :modifiers [(modifiers/bonus-action
                    {:name "Shield Master: Shove"
                     :page 170
                     :summary "make a shove with shield when taking the Attack action"})]})
   #_(feat-option
      {:name "Skilled"
       :icon "juggler"
       :page 170
       :summary "proficiency in three skills and/or tools"
       :selections [(skilled-selection "Skill/Tool 1")
                    (skilled-selection "Skill/Tool 2")
                    (skilled-selection "Skill/tool 3")]})
   #_(feat-option
      {:name "Skulker"
       :icon "ghost-ally"
       :page 170
       :summary "hide when lightly obscured; when hiding, missing an attack doesn't reveal you; no disadvantage on Perception checks in dim light"
       :prereqs [(ability-prereq ::character/dex 13)]})
   #_(feat-option
      {:name "Spell Sniper"
       :icon "laser-precision"
       :page 170
       :summary "attack spells have double range; ignore half and 3/4 cover; learn a cantrip that requires an attack roll"
       :prereqs [can-cast-spell-prereq]
       :selections [(t/selection-cfg
                     {:name "Spell Sniper: Spell Class"
                      :tags #{:spells}
                      :options [(spell-sniper-option :bard "Bard" ::character/cha sl/spell-lists)
                                (spell-sniper-option :cleric "Cleric" ::character/wis sl/spell-lists)
                                (spell-sniper-option :druid "Druid" ::character/wis sl/spell-lists)
                                (spell-sniper-option :sorcerer "Sorcerer" ::character/cha sl/spell-lists)
                                (spell-sniper-option :warlock "Warlock" ::character/cha sl/spell-lists)
                                (spell-sniper-option :wizard "Wizard" ::character/int sl/spell-lists)]})]})
   #_(feat-option
      {:name "Tavern Brawler"
       :icon "broken-bottle"
       :page 170
       :summary "increase STR or CON by 1; improvised weapon proficiency; d4 damage on unarmed strike; grapple as bonus action"
       :selections [(ability-increase-selection [::character/str ::character/con] 1 false)]
       :modifiers [(modifiers/weapon-proficiency :improvised)
                   (modifiers/bonus-action
                    {:name "Tavern Brawler: Grapple"
                     :page 170
                     :summary "attempt grapple when you hit with improvised weapon or unarmed strike"})]})
   #_(feat-option
      {:name "Tough"
       :icon "defensive-wall"
       :page 170
       :summary "2 extra HPs per level"
       :modifiers [(mods/modifier ?hit-point-level-bonus (+ 2 ?hit-point-level-bonus))]})
   #_(feat-option
      {:name "War Caster"
       :icon "deadly-strike"
       :page 170
       :summary "adv. on CON saves for spell concentration; somatic components with weapons or shield in hand; cast spell as opporunity attack"
       :prereqs [can-cast-spell-prereq]})
   #_(feat-option
      {:name "Weapon Master"
       :icon "sword-slice"
       :page 170
       :summary "increase STR or DEX by 1; proficiency with 4 weapons"
       :selections [(ability-increase-selection [::character/str ::character/dex] 1 false)
                    (weapon-proficiency-selection 4)]})]
  #_(map
   (fn [i]
     (t/option-cfg
      (let [kw (keyword (str "custom-feat-" i))]
        {:name (str "Custom Feat " (inc i))
         :key kw
         :icon "beer-stein"
         :order (inc i)
         :ui-fn #(custom-option-builder
                  [:custom-feat-name [:feats kw]]
                  [:set-custom-feat-name [:feats kw]])
         :selections [(t/selection-cfg
                       {:name "Feat Modifiers"
                        :min 0
                        :max nil
                        :multiselect? true
                        :order 2
                        :tags #{:feats}
                        :options [(t/option-cfg
                                   {:name "Tool Proficiency or Expertise"
                                    :help "Gain proficiency in a particular tool or expertise if you already have a proficiency in the tool (select on the 'Proficiencies' tab)."
                                    :selections [(t/selection-cfg
                                                  {:name "Tool Proficiency or Expertise"
                                                   :tags #{:profs}
                                                   :options (map
                                                             (fn [{:keys [name key]}]
                                                               (t/option-cfg
                                                                {:name name
                                                                 :key key
                                                                 :modifiers [(tool-prof-or-expertise key kw)]}))
                                                             equipment/tools)})]})
                                  (t/option-cfg
                                   {:name "Skill Proficiency or Expertise"
                                    :help "Gain proficiency in a particular skill or expertise if you already have proficiency in it"
                                    :selections [(t/selection-cfg
                                                  {:name "Skill Proficiency or Expertise"
                                                   :tags #{:profs}
                                                   :options (map
                                                             (fn [{:keys [name key]}]
                                                               (t/option-cfg
                                                                {:name name
                                                                 :key key
                                                                 :modifiers [(skill-prof-or-expertise key kw)]}))
                                                             skills/skills)})]})
                                  (t/option-cfg
                                   {:name "Ability Score Increase"
                                    :help "This will allow you to select and ability score to increase by 1 (see the 'Abilities Variant' section above)"
                                    :selections [(ability-increase-selection character/ability-keys 1 false)]})
                                  (t/option-cfg
                                   {:name "Extra 2 HPs Per Level"
                                    :help "This will give you an extra 2 HPs per level"
                                    :modifiers [(mods/modifier ?hit-point-level-bonus (+ 2 ?hit-point-level-bonus))]})
                                  (t/option-cfg
                                   {:name "Speed +10"
                                    :help "Increase your speed by 10 ft."
                                    :modifiers [(modifiers/speed 10)]})
                                  (t/option-cfg
                                   {:name "Passive Perception +5"
                                    :help "Increase your passive perception by 5"
                                    :modifiers [(modifiers/passive-perception 5)]})
                                  (t/option-cfg
                                   {:name "Passive Investigation +5"
                                    :help "Increase your passive investigation by 5"
                                    :modifiers [(modifiers/passive-perception 5)]})
                                  (t/option-cfg
                                   {:name "Save Proficiency"
                                    :help "Select proficiency in saving throws with a particular ability (select on the 'Proficiencies' tab)"
                                    :selections [(t/selection-cfg
                                                  {:name "Saving Throw Proficiency"
                                                   :tags #{:profs}
                                                   :options (map
                                                             (fn [k]
                                                               (t/option-cfg
                                                                {:name (:name (abilities-map k))
                                                                 :key k
                                                                 :modifiers [(modifiers/saving-throws nil k)]}))
                                                             character/ability-keys)})]})
                                  (t/option-cfg
                                   {:name "Initiative +5"
                                    :help "This will increase your initiative by 5."
                                    :modifiers [(modifiers/initiative 5)]})
                                  (t/option-cfg
                                   {:name "Weapon Proficiency"
                                    :help "This will allow you to select weapon proficiencies, from 'Simple', 'Martial', or specific weapons (select on the 'Proficiencies' tab)."
                                    :selections [homebrew-weapon-prof-selection]})
                                  (t/option-cfg
                                   {:name "Improvised Weapons Proficiency"
                                    :help "Gain proficiency in improvised weapons, such as broken bottles"})
                                  (t/option-cfg
                                   {:name "Armor Proficiency"
                                    :help "This will allow you to select armor proficiencies, from 'Shields', 'Light', 'Medium', or 'Heavy' (select on the 'Proficiencies' tab)."
                                    :selections [homebrew-armor-prof-selection]})
                                  (t/option-cfg
                                   {:name "Medium Armor: Max DEX Bonus of 3"
                                    :help "This will set your max dexterity bonus with medium armor to 3 instead of 2"
                                    :modifiers [medium-armor-master-max-bonus]})
                                  (t/option-cfg
                                   {:name "Ritual Spells"
                                    :help "Learn 2 ritual spells from a particular class"
                                    :selections [(t/selection-cfg
                                                  {:name "Spellaster Class"
                                                   :tags #{:spells}
                                                   :options [(ritual-caster-option spells-map :bard "Bard" ::character/cha spell-lists)
                                                             (ritual-caster-option spells-map :cleric "Cleric" ::character/wis spell-lists)
                                                             (ritual-caster-option spells-map :druid "Druid" ::character/wis spell-lists)
                                                             (ritual-caster-option spells-map :sorcerer "Sorcerer" ::character/cha spell-lists)
                                                             (ritual-caster-option spells-map :warlock "Warlock" ::character/cha spell-lists)
                                                             (ritual-caster-option spells-map :wizard "Wizard" ::character/int spell-lists)]})]})
                                  (t/option-cfg
                                   {:name "Three Skills or Tools"
                                    :help "Select proficiency in three skills or tools"
                                    :selections [(skilled-selection "Skill/Tool 1")
                                                 (skilled-selection "Skill/Tool 2")
                                                 (skilled-selection "Skill/tool 3")]})
                                  (t/option-cfg
                                   {:name "Attack Cantrip"
                                    :help "Select a cantrip that requires an attack roll"
                                    :selections [(t/selection-cfg
                                                  {:name "Attack Cantrip Class"
                                                   :tags #{:spells}
                                                   :options [(spell-sniper-option spells-map :bard "Bard" ::character/cha spell-lists)
                                                             (spell-sniper-option spells-map :cleric "Cleric" ::character/wis spell-lists)
                                                             (spell-sniper-option spells-map :druid "Druid" ::character/wis spell-lists)
                                                             (spell-sniper-option spells-map :sorcerer "Sorcerer" ::character/cha spell-lists)
                                                             (spell-sniper-option spells-map :warlock "Warlock" ::character/cha spell-lists)
                                                             (spell-sniper-option spells-map :wizard "Wizard" ::character/int spell-lists)]})]})
                                  (t/option-cfg
                                   {:name "Medium Armor: Stealthy"
                                    :help "This will allow you to use medium armor without stealth disadvantage"
                                    :modifiers [medium-armor-master-stealth]})
                                  (t/option-cfg
                                   {:name "Language Proficiency"
                                    :help "This will allow you to select language proficiencies"
                                    :selections [(homebrew-language-selection)]})
                                  (t/option-cfg
                                   {:name "Dual Wielding: AC +1"
                                    :help "When wielding two-weapons, this will give you a +1 bonus to AC."
                                    :key :dual-wield-ac-mod
                                    :modifiers [dual-wield-ac-mod]})
                                  (t/option-cfg
                                   {:name "Dual Wielding: Any One-Handed Melee Weapon"
                                    :help "This will allow you to engage in two-weapon fighting with any two single-handed melee weapons"
                                    :key :dual-wield-weapon-mod
                                    :modifiers [dual-wield-weapon-mod]})
                                  (t/option-cfg
                                   {:name "Spellcasting"
                                    :help "Select low-level spells from a particular class"
                                    :selections [(t/selection-cfg
                                                  {:name "Spell Class"
                                                   :order 0
                                                   :tags #{:spells}
                                                   :options [(magic-initiate-option spells-map :bard "Bard" ::character/cha spell-lists)
                                                             (magic-initiate-option spells-map :cleric "Cleric" ::character/wis spell-lists)
                                                             (magic-initiate-option spells-map :druid "Druid" ::character/wis spell-lists)
                                                             (magic-initiate-option spells-map :sorcerer "Sorcerer" ::character/cha spell-lists)
                                                             (magic-initiate-option spells-map :warlock "Warlock" ::character/cha spell-lists)
                                                             (magic-initiate-option spells-map :wizard "Wizard" ::character/int spell-lists)]})]})]})]})))
   (range 10)))

(def fighting-style-options
  [(t/option-cfg
    {:name "Archery"
     :modifiers [(modifiers/ranged-attack-bonus 2)
      (modifiers/trait-cfg
       {:name "Archery Fighting Style"
        :page 72
        :description "You gain a +2 bonus to attack rolls you make with ranged weapons."})]})
   (t/option-cfg
    {:name "Defense"
     :modifiers [(modifiers/armored-ac-bonus 1)
      (modifiers/trait-cfg
       {:name "Defense Fighting Style"
        :page 72
        :description "While you are wearing armor, you gain a +1 bonus to AC."})]})
   (t/option-cfg
    {:name "Dueling"
     :modifiers [(modifiers/trait-cfg
       {:name "Dueling Fighting Style"
        :page 72
        :description "When you are wielding a melee weapon in one hand and no other weapons, you gain a +2 bonus to damage rolls with that weapon."})]})
   (t/option-cfg
    {:name "Great Weapon Fighting"
     :modifiers [(modifiers/trait-cfg
       {:name "Great Weapon Fighting Style"
        :page 72
        :description "When you roll a 1 or 2 on a damage die for an attack you make with a melee weapon that you are wielding with two hands, you can reroll the die and must use the new roll, even if the new roll is a 1 or a 2. The weapon must have the two-handed or versatile property for you to gain this benefit."})]})
   (t/option-cfg
    {:name "Protection"
     :modifiers [(modifiers/reaction
       {:name "Protection Fighting Style"
        :page 72
        :description "When a creature you can see attacks a target other than you that is within 5 feet of you, you can use your reaction to impose disadvantage on the attack roll. You must be wielding a shield."})]})
   (t/option-cfg
    {:name"Two Weapon Fighting"
     :modifiers [(modifiers/trait-cfg
                  {:name "Two Weapon Fighting"
                   :description "When you engage in two-weapon fighting, you can add your ability modifier to the damage of the second attack."})
                 (mods/modifier ?weapon-ability-damage-modifier
                                (fn [weapon finesse? _]
                                  (?weapon-ability-modifier weapon finesse?)))]})])

(defn fighting-style-selection-2 [class-kw num options]
  (t/selection-cfg
   {:name "Fighting Style"
    :tags #{:class}
    :ref [:class class-kw :fighting-style]
    :multiselect? true
    :min num
    :max num
    :options options}))

(defn fighting-style-selection [class-kw & [restrictions additional-options]]
  (fighting-style-selection-2
   class-kw
   1
   (if restrictions
     (filter
      (fn [o]
        (restrictions (::t/key o)))
      fighting-style-options)
     fighting-style-options)))

(defn feat-selection [spell-lists spells-map num]
  (t/selection-cfg
   {:name "Feats"
    :options (feat-options spell-lists spells-map)
    :multiselect? true
    :tags #{:feats}
    :ref [:feats]
    :show-if-zero? true
    :min num
    :max num}))

(defn ability-score-improvement-selection [spell-lists spells-map cls lvl]
  (t/selection-cfg
   {:name "Ability Score Improvement or Feat"
    :key :asi-or-feat
    :tags #{:ability-scores}
    :options [(ability-increase-option 2 false character/ability-keys)
              (t/option-cfg
               {:name "Feat"
                :selections [(feat-selection spell-lists spells-map 1)]})]}))

(defn expertise-selection [num & [key]]
  (t/selection-cfg
   {:name "Skill Expertise"
    :key (or key :skill-expertise)
    :order 2
    :options (map
              (fn [{:keys [name key icon]}]
                (t/option-cfg
                 {:name name
                  :key key
                  :icon icon
                  :modifiers [(modifiers/skill-expertise key)]
                  :prereqs [(t/option-prereq (str "Requires proficiency in " name)
                                             (fn [built-char]
                                               (let [skill-profs @(subscribe [::character/skill-profs nil built-char])]
                                                 (and skill-profs (skill-profs key)))))]}))
              skills/skills)
    :min num
    :max num
    :multiselect? true
    :ref [:skill-expertise]
    :tags #{:profs :expertise}}))

(def rogue-expertise-selection
  (t/selection-cfg
   {:name "Expertise"
    :tags #{:profs :skill-profs :expertise}
    :order 1
    :options [(t/option-cfg
               {:name "Two Skills"
                :selections [(expertise-selection 2 :two-skills)]})
              (t/option-cfg
               {:name "One Skill/Thieves' Tools"
                :selections [(expertise-selection 1 :one-skill-thieves-tools)]
                :modifiers [(modifiers/tool-proficiency :thieves-tools)
                            (modifiers/tool-expertise :thieves-tools)]})]}))

(defn cleric-spell [spell-level spell-key min-level]
  (modifiers/spells-known-cfg
   spell-level
   {:key spell-key
    :ability ::character/wis
    :class "Cleric"
    :qualifier "Domain"
    :class-key :cleric
    :always-prepared? true}
   min-level
   nil))


(defn potent-spellcasting [page & [source]]
  (modifiers/dependent-trait
   {:level 8
    :page page
    :source source
    :summary (str "Add "
                  (common/bonus-str (?ability-bonuses ::character/wis))
                  " to damage from cantrips you cast")
    :name "Potent Spellcasting"}))

(def monk-base-cfg
  {:name "Monk"
   :subclass-level 3
   :subclass-title "Monastic Tradition"})

(def paladin-base-cfg
  {:name "Paladin"
   :subclass-level 3
   :subclass-title "Sacred Oath"})

(def ua-al-illegal (modifiers/al-illegal "Unearthed Arcana options are not allowed"))

(defn subclass-plugin [class-base-cfg source subclasses ua-al-illegal?]
  (merge
   class-base-cfg
   {:source source
    :plugin? true
    :subclasses (if ua-al-illegal?
                  (map
                   (fn [subclass]
                     (update subclass :modifiers conj ua-al-illegal))
                   subclasses)
                  subclasses)}))

(defn paladin-spell [spell-level key]
  (modifiers/spells-known-cfg spell-level
                              {:key key
                               :ability ::character/cha
                               :class "Paladin"
                               :always-prepared? true
                               :class-key :paladin}
                              (case spell-level
                                1 3
                                2 5
                                3 9
                                4 13
                                5 17)
                              nil))

(defn subclass-spell-selection [spell-lists spells-map class-key class-name ability spells num]
  (spell-selection
   spell-lists
   spells-map
   {:class-key class-key
    :spell-keys spells
    :spellcasting-ability ability
    :class-name class-name
    :num num
    :prepend-level? true}))

(defn subclass-cantrip-selection [spell-lists spells-map class-key class-name ability spells num]
  (spell-selection
   spell-lists
   spells-map
   {:class-key class-key
    :level 0
    :spellcasting-ability ability
    :class-name class-name
    :spell-keys spells
    :num num}))

(defn warlock-subclass-spell-selection [spell-lists spells-map spells]
  (subclass-spell-selection spell-lists spells-map :warlock "Warlock" ::character/cha spells 0))

(defn traits-modifiers [traits & [class-key source]]
  (map
   (fn [trait]
     (modifiers/trait-cfg (merge {:source source
                                  :class-key class-key}
                                 trait)))
   traits))

(defn armor-prof-modifiers [armor-proficiencies & [cls-kw]]
  (map
   (fn [armor-prof]
     (let [[armor-kw first-class?] (if (keyword? armor-prof) [armor-prof false] armor-prof)]
       (modifiers/armor-proficiency armor-kw first-class? cls-kw)))
   armor-proficiencies))

(defn tool-prof-modifiers [tool-proficiencies & [cls-kw]]
  (map
   (fn [tool-prof]
     (let [[tool-kw first-class?] (if (keyword? tool-prof) [tool-prof false] tool-prof)]
       (modifiers/tool-proficiency tool-kw first-class? cls-kw)))
   tool-proficiencies))

(defn weapon-prof-modifiers [weapon-proficiencies & [cls-kw]]
  (map
   (fn [weapon-prof]
     (let [[weapon-kw first-class?] (if (keyword? weapon-prof) [weapon-prof false] weapon-prof)]
       (if (#{:simple :martial} weapon-kw)
         (modifiers/weapon-proficiency weapon-kw first-class? cls-kw)
         (modifiers/weapon-proficiency weapon-kw first-class? cls-kw))))
   weapon-proficiencies))


(defn subrace-option [race
                      spell-lists
                      spells-map
                      languages
                      source
                      {:keys [name
                              abilities
                              profs
                              size
                              speed
                              darkvision
                              subrace-options
                              armor-proficiencies
                              weapon-proficiencies
                              modifiers
                              selections
                              traits
                              source
                              edit-event]}]
  (let [{:keys [skill-options tool]} profs
        {skill-num :choose options :options} skill-options
        skill-kws (if (:any options)
                    (map :key skills/skills)
                    (map
                     clojure.core/key
                     (filter val options)))]
    (t/option-cfg
     {:name name
      :edit-event edit-event
      :selections (concat
                   (if (seq skill-kws)
                     [(skill-selection skill-kws (or skill-num 1))])
                   selections)
      :modifiers (concat
                  [(modifiers/subrace name)]
                  (if (and speed
                           (not= speed (:speed race)))
                    [(modifiers/speed (- speed (:speed race)))])
                  (if (and darkvision
                           (not= darkvision (:darkvision race)))
                    [(modifiers/darkvision darkvision)])
                  modifiers
                  (armor-prof-modifiers armor-proficiencies)
                  (weapon-prof-modifiers weapon-proficiencies)
                  (tool-prof-modifiers (common/true-keys tool))
                  (map
                   (fn [[k v]]
                     (modifiers/subrace-ability k v))
                   abilities)
                  (traits-modifiers traits nil source)
                  (if source [(modifiers/used-resource source name)]))})))

(defn ability-modifiers [abilities]
  (map
   (fn [[k v]]
     (modifiers/ability k v))
   abilities))

(defn darkvision-modifiers [range]
  [(modifiers/darkvision range)])

(defn feat-selection-2 [cfg]
  (t/selection-cfg
   (merge
    {:name "Feats"
     :ref [:feats]
     :show-if-zero? true
     :tags #{:feats}
     :order 1
     :multiselect? true}
    cfg)))

(def homebrew-ability-increase-selection
  (ability-increase-selection-2
   {:min 0}))

(defn homebrew-feat-selection [spell-lists spells-map]
  (feat-selection-2
   {:min 0
    :max nil
    :options (feat-options spell-lists spells-map)}))

(def homebrew-al-illegal
  (modifiers/al-illegal "Homebrew options are not allowed"))

(defn none-option [path]
  (t/option-cfg
   {:name "<none>"
    :key :none
    :order 1001
    :prereqs [(t/option-prereq
               nil
               (fn [_] @(subscribe [:homebrew? path]))
               true)]}))


(defn custom-subrace-builder []
  (custom-option-builder
   [:custom-subrace-name]
   [:set-custom-subrace]))

(def homebrew-speed-selection
  (t/selection-cfg
   {:name "Speed"
    :tags #{:race}
    :min 0
    :max 1
    :options (map
              (fn [speed]
                (t/option-cfg
                 {:name (str speed " ft.")
                  :key (keyword (str "ft-" speed))
                  :modifiers [(modifiers/speed speed)]}))
              (range -10 55 5))}))

(def homebrew-darkvision-selection
  (t/selection-cfg
   {:name "Darkvision"
    :tags #{:race}
    :min 0
    :max 1
    :options (map
              (fn [distance]
                (t/option-cfg
                 {:name (str distance " ft.")
                  :key (keyword (str "ft-" distance))
                  :modifiers [(modifiers/darkvision distance)]}))
              (range 0 150 30))}))

(defn custom-subrace-option [spell-lists spells-map language-map weapon-map path]
  (t/option-cfg
   {:name "Custom"
    :icon "beer-stein"
    :ui-fn custom-subrace-builder
    :help "Homebrew subrace. This allows you to use a subrace that is not on the list. This will allow unrestricted access to skill and tool proficiencies, racial ability increases, and feats."
    :modifiers [(modifiers/deferred-subrace)
                homebrew-al-illegal]
    :order 1000
    :selections [homebrew-skill-prof-selection
                 homebrew-tool-prof-selection
                 homebrew-ability-increase-selection
                 (homebrew-feat-selection spell-lists spells-map)
                 homebrew-speed-selection
                 homebrew-darkvision-selection
                 homebrew-armor-prof-selection
                 (homebrew-weapon-prof-selection weapon-map)
                 (homebrew-language-selection language-map)]}))

(defn custom-race-builder []
  (custom-option-builder
   [:custom-race-name]
   [:set-custom-race]))

(defn subrace-selection [race spell-lists spells-map language-map weapon-map plugin? source subraces path]
  (let [subrace-path (conj path :subrace)]
    (t/selection-cfg
     {:name "Subrace"
      :tags #{:subrace}
      :min (if subraces 1 0)
      :options (cond->
                (if (seq subraces)
                  (map
                   (partial subrace-option race spell-lists spells-map language-map source)
                   (if source
                     (map (fn [sr] (assoc sr :source source)) subraces)
                     subraces))
                  [(none-option subrace-path)])

                 (not plugin?)
                 (conj (custom-subrace-option spell-lists spells-map language-map weapon-map subrace-path)))})))

(defn custom-race-option [spell-lists spells-map language-map weapon-map]
  (t/option-cfg
   {:name "Custom"
    :icon "beer-stein"
    :ui-fn custom-race-builder
    :help "Homebrew race. This allows you to use a race that is not on the list. This will allow unrestricted access to skill and tool proficiencies, racial ability increases, and feats."
    :modifiers [(modifiers/deferred-race)
                homebrew-al-illegal]
    #_:prereqs #_[(t/option-prereq
               nil
               (fn [_] @(subscribe [:homebrew? [:race]]))
               true)]
    :order 1000
    :selections [(subrace-selection {} spell-lists spells-map language-map weapon-map false nil nil [:race :custom])
                 homebrew-skill-prof-selection
                 homebrew-tool-prof-selection
                 homebrew-ability-increase-selection
                 (homebrew-feat-selection spell-lists spells-map)
                 homebrew-speed-selection
                 homebrew-darkvision-selection
                 homebrew-armor-prof-selection
                 (homebrew-weapon-prof-selection weapon-map)
                 (homebrew-language-selection language-map)]}))

(defn custom-background-builder []
  (custom-option-builder
   [:custom-background-name]
   [:set-custom-background]))

(defn custom-background-option [language-map]
  (t/option-cfg
   {:name "Custom"
    :ui-fn custom-background-builder
    :order 1000
    :modifiers [(modifiers/deferred-background)]
    :selections [(skill-selection 2)
                 (t/selection-cfg
                  {:name "Tool / Language Proficiencies"
                   :tags #{:profs}
                   :options [(t/option-cfg
                              {:name "Two Tools"
                               :selections [(tool-selection 2)]})
                             (t/option-cfg
                              {:name "One Tool / One Language"
                               :selections [(tool-selection 1)
                                            (homebrew-language-selection language-map 1 1)]})
                             (t/option-cfg
                              {:name "Two Languages"
                               :selections [(homebrew-language-selection language-map 2 2)]})]})]}))

(defn race-option [spell-lists
                   spells-map
                   language-map
                   weapon-map
                   {:keys [name
                           icon
                           key
                           help
                           abilities
                           size
                           speed
                           darkvision
                           subraces
                           modifiers
                           selections
                           traits
                           source
                           languages
                           language-options
                           armor-proficiencies
                           weapon-proficiencies
                           profs
                           source
                           plugin?
                           edit-event]
                    :as race}]
  (let [key (or key (common/name-to-kw name))
        {:keys [armor weapon save skill-options weapon-proficiency-options tool-options tool language-options]} profs
        {skill-num :choose options :options} skill-options
        skill-kws (if (:any options)
                    (map :key skills/skills)
                    (map
                     clojure.core/key
                     (filter val options)))]
    (t/option-cfg
     {:name name
      :icon icon
      :key key
      :help help
      :edit-event edit-event
      :selections (concat
                   (if (seq skill-kws)
                     [(skill-selection skill-kws (or skill-num 1))])
                   (if (seq subraces)
                     [(subrace-selection race spell-lists spells-map language-map weapon-map plugin? source subraces [:race key])])
                   (if (seq language-options) [(language-selection language-map language-options)])
                   (if (seq weapon-proficiency-options)
                     [(weapon-proficiency-selection-2 weapon-map weapon-proficiency-options)])
                   selections)
      :modifiers (concat
                  (if (not plugin?)
                    (remove
                     nil?
                     [(modifiers/race name)
                      (if size (modifiers/size size))
                      (if speed (modifiers/speed speed))]))
                  (if darkvision
                    (darkvision-modifiers darkvision))
                  (map
                   (fn [language]
                     (modifiers/language (common/name-to-kw language)))
                   languages)
                  (map
                   (fn [[k v]]
                     (modifiers/race-ability k v))
                   abilities)
                  modifiers
                  (tool-prof-modifiers (common/true-keys tool))
                  (traits-modifiers traits nil source)
                  (armor-prof-modifiers armor-proficiencies)
                  (weapon-prof-modifiers weapon-proficiencies)
                  (if source [(modifiers/used-resource source name)]))})))

(defn add-sources [source background]
  (-> background
      (assoc :source source)
      (update :traits (fn [traits] (map (fn [t] (assoc t :source source)) traits)))))

(def artisans-tools-choice-cfg
  {:name "Artisan's Tool"
   :options (zipmap (map :key equipment/artisans-tools) (repeat 1))})

(defn starting-equipment-option [equipment num]
  (t/option-cfg
   {:name (:name equipment)
    :key (:key equipment)
    :modifiers [(modifiers/equipment (:key equipment) num)]}))

(defn class-starting-equipment-entity-options [key items]
  (eh/starting-equipment-entity-options ::char-equip/class-starting-equipment? key items))

(defn tool-prof-selection-aux [tool num & [key prereq-fn]]
  (t/selection-cfg
   {:name (str "Tool Proficiency: " (:name tool))
    :key (if key (keyword (str (name key) "--" (common/name-to-kw (:name tool)))))
    :help (str "Select " (s/lower-case (:name tool)) " for which you are proficient.")
    :options (map
              (fn [{:keys [name key icon]}]
                (t/option-cfg
                 {:name name
                  :key key
                  :icon icon
                  :modifiers [(modifiers/tool-proficiency key)]}))
              (:values tool))
    :min num
    :max num
    :prereq-fn prereq-fn
    :tags #{:tool-profs :profs}}))

(defn tool-prof-selection [tool-options & [key prereq-fn]]
  (let [[first-key first-num] (-> tool-options first)
        first-option (equipment/tools-map first-key)]
    (if (and (= 1 (count tool-options))
             (seq (:values first-option)))
      (tool-prof-selection-aux first-option first-num key prereq-fn)
      (t/selection-cfg
       {:name "Tool Proficiencies"
        :key key
        :options (map
                  (fn [[k num]]
                    (let [tool (equipment/tools-map k)]
                      (if (:values tool)
                        (t/option-cfg
                         {:name (:name tool)
                          :selections [(tool-prof-selection-aux tool num key prereq-fn)]})
                        (t/option-cfg
                         {:name (:name tool)
                          :key (:key tool)
                          :icon (:icon tool)
                          :modifiers [(modifiers/tool-proficiency (:key tool))]}))))
                  tool-options)
        :prereq-fn prereq-fn
        :tags #{:profs :tool-profs}}))))

(defn first-class? [class-kw & [classes]]
  (fn [c] (= class-kw (first (or classes @(subscribe [::character/classes nil c]))))))

(defn new-starting-equipment-selection [class-kw {:keys [name options] :as cfg}]
  (t/selection-cfg
   (merge
    cfg
    {:name (str "Starting Equipment: " name)
     :tags #{:equipment :starting-equipment}
     :order 1
     :options (conj options
                    (t/option-cfg
                     {:name "<none>"
                      :key :none}))
     :prereq-fn (if class-kw (first-class? class-kw))})))

(defn simple-weapon-selection [num class-kw weapon-map]
  (new-starting-equipment-selection
   class-kw
   {:name "Simple Weapon"
    :tags #{:starting-equipment}
    :options (weapon-options (weapons/simple-weapons (vals weapon-map)))
    :min num
    :max num
    :prereq-fn (first-class? class-kw)}))

(defn weapon-option-2 [class-kw weapon-map [k num]]
  (case k
    :simple (t/option-cfg
             {:name "Any Simple Weapon"
              :selections [(simple-weapon-selection num class-kw weapon-map)]})
    :martial (t/option-cfg
              {:name "Any Martial Weapon"
               :selections [(new-starting-equipment-selection
                             class-kw
                             {:name "Martial Weapon"
                              :options (weapon-options (weapons/martial-weapons (vals weapon-map)))
                              :min num
                              :max num})]})
    (t/option-cfg
     {:name (-> k weapon-map :name (str (if (> num 1) (str " (" num ")") "")))
      :modifiers [(modifiers/weapon k num)]})))

(defn class-options [class-kw option-fn choices help]
  (map
   (fn [{:keys [name options]}]
     (new-starting-equipment-selection
      class-kw
      {:name name
       :help help
       :options (map
                 option-fn
                 options)}))
   choices))

(defn class-weapon-options [weapon-choices class-kw weapon-map]
  (class-options class-kw (partial weapon-option-2 class-kw weapon-map) weapon-choices "Select a weapon to begin your adventuring career with."))

(defn armor-option [[k num]]
  (t/option-cfg
     {:name (-> k armor/armor-map :name)
      :modifiers [(modifiers/armor k num)]}))

(defn class-armor-options [armor-choices class-kw]
  (class-options class-kw armor-option armor-choices "Select armor to begin your adventuring career with."))

(defn equipment-option [class-kw [k num]]
  (let [equipment (equipment/equipment-map k)]
    (if (:values equipment)
      (t/option-cfg
       {:name (:name equipment)
        :selections [(t/selection-cfg
                      {:name (:name equipment)
                       :tags #{:equipment :starting-equipment}
                       :options (map
                                 #(equipment-option class-kw %)
                                 (zipmap (map :key (:values equipment)) (repeat num)))
                       :prereq-fn (first-class? class-kw)})]})
      (t/option-cfg
       {:name (-> equipment :name (str (if (> num 1) (str " (" num ")") "")))
        :modifiers (if (:items equipment)
                     (map
                      (fn [[kw num]]
                        (modifiers/equipment kw num))
                      (:items equipment))
                     [(modifiers/equipment k num)])}))))

(defn class-equipment-options [equipment-choices class-kw]
  (class-options class-kw (partial equipment-option class-kw) equipment-choices "Select equipment to start your adventuring career with."))

(defn background-skills-cfg [background-nm skill-kws]
  {:modifiers (map
               (fn [skill-kw]
                 (modifiers/skill-proficiency skill-kw
                                              background-nm
                                              [(not (get ?skill-profs skill-kw))]))
               skill-kws)
   :selections (map
                (fn [skill-kw]
                  (skill-selection (map :key skills/skills)
                                   1
                                   0
                                   nil
                                   (fn [c]
                                     (let [skill-profs @(subscribe [::character/skill-profs nil c])
                                           skill-sources (get skill-profs skill-kw)
                                           passes? (and skill-sources
                                                        (not (skill-sources background-nm)))]
                                       passes?))))
                skill-kws)})

(defn background-option [language-map
                         weapon-map
                         {:keys [name
                                 help
                                 page
                                 profs
                                 selections
                                 modifiers
                                 weapon-choices
                                 weapons
                                 equipment
                                 custom-equipment
                                 equipment-choices
                                 armor
                                 armor-choices
                                 treasure
                                 custom-treasure
                                 traits
                                 source
                                 edit-event]
                          :as background}]
  (let [kw (common/name-to-kw name)
        {:keys [skill skill-options tool-options tool language-options]
         armor-profs :armor weapon-profs :weapon} profs
        {skill-num :choose options :options} skill-options
        skill-kws (if (:any options) (map :key skills/skills) (keys options))]
    (t/option-cfg
     (merge-with
      concat
      (background-skills-cfg name (keys skill))
      {:name name
       :key kw
       :help help
       :edit-event edit-event
       :page page
       :select-fn (fn [_ _]
                    (dispatch [:add-background-starting-equipment background]))
       :selections (concat
                    selections
                    (if (seq tool-options) [(tool-prof-selection tool-options)])
                    (class-weapon-options weapon-choices nil weapon-map)
                    (class-armor-options armor-choices nil)
                    (class-equipment-options equipment-choices nil)
                    (if (seq skill-kws) [(skill-selection skill-kws skill-num)])
                    (if (seq language-options) [(language-selection
                                                 language-map
                                                 language-options)]))
       :modifiers (concat
                   [(modifiers/background name)]
                   (traits-modifiers traits)
                   modifiers
                   (armor-prof-modifiers (keys armor-profs))
                   (weapon-prof-modifiers (keys weapon-profs))
                   (tool-prof-modifiers (keys tool)))}))))

(defn total-levels-prereq [level & [class-key]]
  (fn [c] (>= (if class-key
                (@(subscribe [::character/class-level-fn nil c]) class-key)
                @(subscribe [::character/total-levels nil c]))
              level)))

(defn total-levels-prereq-2 [level & [class-key]]
  (fn [c]
    (and c
         (character/class-level-fn c)
         level
         (>= (or (if class-key
                   ((character/class-level-fn c) class-key)
                   (character/total-levels c))
                 0)
             (or level 0)))))


(defn total-levels-option-prereq [level & [class-key]]
  (t/option-prereq
   (str "You must have at least " level " " (name class-key) " levels")
   (total-levels-prereq level class-key)))

(defn add-mod-total-levels-prereq [lvl cls modifier]
  (if (sequential? modifier)
    (map
     add-mod-total-levels-prereq
     modifier)
    (update
     modifier
     ::mods/conditions
     conj
     (total-levels-prereq-2 lvl (:key cls)))))

(defn subclass-option [spell-lists
                       spells-map
                       language-map
                       cls
                       {:keys [name
                               source
                               edit-event
                               profs
                               selections
                               spellcasting
                               modifiers
                               level-modifiers
                               traits
                               prereqs
                               levels]
                        :as subcls}]
  (let [kw (common/name-to-kw name)
        {:keys [armor weapon save skill-options skill-expertise-options tool-options tool language-options]} profs
        {skill-num :choose options :options} skill-options
        {level-factor :level-factor} spellcasting
        skill-kws (if (:any options) (map :key skills/skills) (keys options))
        skill-expertise-kws (if (get-in skill-expertise-options [:options :any])
                              (map :key skills/skills)
                              (keys (:options skill-expertise-options)))
        armor-profs (keys armor)
        weapon-profs (keys weapon)
        tool-profs (keys tool)
        spellcasting-template (spellcasting-template
                               spell-lists
                               spells-map
                               (assoc
                                spellcasting
                                :class-key
                                (or (:spell-list spellcasting) kw))
                               subcls)
        spell-selections (mapcat
                          (fn [[lvl selections]]
                            (map
                             (fn [selection]
                               (assoc selection
                                      ::t/prereq-fn
                                      (fn [c] (let [total-levels @(subscribe [::character/total-levels nil c])]
                                                (>= lvl total-levels)))))
                             selections))
                          (:selections spellcasting-template))
        level-selections (mapcat
                          (fn [[lvl {selections :selections}]]
                            (map
                             (fn [selection]
                               (assoc
                                selection
                                ::t/prereq-fn
                                (total-levels-prereq lvl (:key cls))))
                             selections))
                          levels)
        level-modifiers (mapcat
                         (fn [[lvl {modifiers :modifiers}]]
                           (map
                            (partial add-mod-total-levels-prereq lvl cls)
                            modifiers))
                         levels)]
    (t/option-cfg
     {:name name
      :edit-event edit-event
      :prereqs prereqs
      :selections (map
                   (fn [selection]
                     (update selection ::t/tags sets/union #{(:key cls) kw}))
                   (concat
                    selections
                    level-selections
                    spell-selections
                    (if (seq tool-options) [(tool-prof-selection tool-options)])
                    (if (seq skill-kws) [(skill-selection skill-kws skill-num)])
                    (if (seq skill-expertise-kws)
                      [(skill-expertise-selection skill-expertise-kws (:choose skill-expertise-options))])
                    (if (seq language-options) [(language-selection language-map language-options)])))
      :modifiers (concat
                  modifiers
                  level-modifiers
                  [(modifiers/subclass (:key cls) kw)
                   (modifiers/subclass-name (:key cls) name)]
                  (if (:known-mode spellcasting)
                    [(modifiers/spells-known-mode name (:known-mode spellcasting))])
                  (armor-prof-modifiers armor-profs)
                  (weapon-prof-modifiers weapon-profs)
                  (tool-prof-modifiers tool-profs)
                  (traits-modifiers traits (:key cls))
                  (if level-factor [(modifiers/spell-slot-factor (:key cls) level-factor)])
                  (if source [(modifiers/used-resource source name)]))})))

(defn level-key [index]
  (keyword (str "level-" index)))

(defn level-name [index]
  (str "Level " index))

(defn subclass-level-option [{:keys [name
                                     levels] :as subcls}
                             kw
                             spellcasting-template
                             i]
  (let [selections (some-> levels (get i) :selections)]
    (t/option-cfg
     {:name (level-name i)
      :key (level-key i)
      :order i
      :selections (concat
                   selections
                   (some-> spellcasting-template :selections (get i)))
      :modifiers (some-> levels (get i) :modifiers)})))

(defn al-illegal-hit-points-mod [reason]
  (modifiers/al-illegal (str reason " The only legal option is 'Average'.")))

(defn hit-points-selection [die class-nm level]
  (t/selection-cfg
   {:name (str "Hit Points: " class-nm " " level)
    :key :hit-points
    :require-value? true
    :help "Select the method with which to determine this level's hit points."
    :tags #{:class}
    :options [{::t/name "Manual Entry"
               ::t/key :manual-entry
               ::t/help "This option allows you to manually type in the value for this level's hit points. Use this if you want to roll dice yourself or if you already have a character with known hit points for this level."
               ::t/modifiers [(modifiers/deferred-max-hit-points)
                              (al-illegal-hit-points-mod "Manual entry for hit points is not legal.")]}
              {::t/name (str "Roll (1D" die ")")
               ::t/key :roll
               ::t/help "This option rolls virtual dice for you and sets that value for this level's hit points. It could pay off with a high roll, but you might also roll a 1."
               ::t/modifiers [(modifiers/deferred-max-hit-points)
                              (al-illegal-hit-points-mod "Rolling for hit points is not legal.")]}
              (let [average (dice/die-mean die)]
                (t/option-cfg
                 {:name "Average"
                  :key :average
                  :help (str "This option just gives you the average value (" average ") for the die roll (1D" die ").")
                  :modifiers [(modifiers/max-hit-points average)]}))]}))

(defn custom-subclass-builder [path]
  (custom-option-builder
   [:custom-subclass-name path]
   [:set-custom-subclass path]))

#_(defn custom-subclass-spell-selection [ability-kw level]
  (t/selection-cfg
   {:name (if (zero? level)
            "Cantrips Known"
            (str (common/ordinal level) "-Level Spells Known"))
    :key (keyword (str "lvl-" level "-spells-known"))
    :min 0
    :max nil
    :multiselect? true
    :tags #{:spells}
    :order level
    :prereq-fn (fn [c] (or (zero? level)
                           (-> @(subscribe [::character/total-levels nil c])
                               (total-slots 3)
                               (get level)
                               pos?)))

    :options (sequence
              (comp
               (filter
                (fn [s]
                  (= level (:level s))))
               (map :key)
               (map (partial memoized-spell-option ability-kw "Custom")))
              spells/spells)}))

#_(defn custom-subclass-spellcasting-selection [cls-key]
  (t/selection-cfg
   {:name "Spellcasting Ability"
    :key :spellcasting-ability
    :min 0
    :max 1
    :tags #{:class}
    :options (conj
              (map
               (fn [{ability-kw :key name :name}]
                 (t/option-cfg
                  {:name name
                   :key ability-kw
                   :modifiers [(modifiers/spell-slot-factor cls-key 3)]
                   :selections (map
                                (fn [level]
                                  (custom-subclass-spell-selection ability-kw level))
                                (range 0 5))}))
               abilities)
              (t/option-cfg
               {:name "<none>"
                :key :none}))}))

(defn custom-subclass-option [spell-lists spells-map weapon-map cls-key level-key subclass-selection-key spellcasting-class?]
  (let [path [:class cls-key :levels level-key subclass-selection-key]]
    (t/option-cfg
     {:name "Custom"
      :icon "beer-stein"
      :ui-fn #(custom-subclass-builder path)
      :help "Homebrew subclass. This allows you to use a subclass that is not on the list. This will allow unrestricted access to skill and tool proficiencies and feats."
      #_:prereqs #_[(t/option-prereq
                     nil
                     (fn [_] @(subscribe [:homebrew? path]))
                     true)]
      :order 1000
      :modifiers [(modifiers/deferred-subclass-name cls-key)
                  homebrew-al-illegal]
      :selections (let [selections
                        [homebrew-skill-prof-selection
                         homebrew-tool-prof-selection
                         (homebrew-feat-selection spell-lists spells-map)
                         homebrew-armor-prof-selection
                         (homebrew-weapon-prof-selection weapon-map)]]
                    selections
                    #_(if spellcasting-class?
                      selections
                      (conj selections
                            (custom-subclass-spellcasting-selection cls-key))))})))

(defn level-option [spell-lists
                    spells-map
                    language-map
                    weapon-map
                    {:keys [name
                            plugin?
                            hit-die
                            profs
                            levels
                            traits
                            spellcasting
                            ability-increase-levels
                            subclass-title
                            subclass-help
                            subclass-level
                            subclasses
                            source] :or {subclass-level 1} :as cls}
                    kw
                    spellcasting-template
                    i]
  (let [ability-inc-set (set ability-increase-levels)
        level-kw (level-key i)]
    (t/option-cfg
     {:name (level-name i)
      :key level-kw
      :order i
      :selections (map
                   (fn [selection]
                     (update selection ::t/tags sets/union #{:level level-kw}))
                   (concat
                    (some-> levels (get i) :selections)
                    (some-> spellcasting-template :selections (get i))
                    (if (= i subclass-level)
                      (let [subclass-selection-key (common/name-to-kw subclass-title)]
                        [(t/selection-cfg
                          {:name (or subclass-title (str name " Archetype"))
                           :adder-key-fn (fn [_] [:subclass])
                           :key subclass-selection-key
                           :help subclass-help
                           :tags #{:subclass}
                           :order 2
                           :options (conj
                                     (map
                                      #(subclass-option spell-lists spells-map language-map (assoc cls :key kw) %)
                                      (if source (map (fn [sc] (assoc sc :source source)) subclasses) subclasses))
                                     (custom-subclass-option spell-lists spells-map weapon-map kw level-kw subclass-selection-key (some? spellcasting)))})]))
                    (if (and (not plugin?) (ability-inc-set i))
                      [(ability-score-improvement-selection spell-lists spells-map name i)])
                    (if (not plugin?)
                      [(assoc
                        (hit-points-selection hit-die name i)
                        ::t/prereq-fn
                        (fn [c] (or (not (= kw (first @(subscribe [::character/classes nil c]))))
                                    (> i 1))))])))
      :modifiers (concat
                  (some-> levels (get i) :modifiers)
                  (traits-modifiers
                   (filter
                    (fn [{level :level :or {level 1}}]
                      (= level i))
                    traits)
                   kw)
                  (if (and (not plugin?)
                           (= i 1))
                    [(mods/cum-sum-mod
                      ?hit-point-level-increases
                      hit-die
                      nil
                      nil
                      [(= kw (first ?classes))])])
                  (if (not plugin?)
                    [(modifiers/level kw name i hit-die)]))})))



(defn class-skill-selection [{skill-num :choose options :options skill-select-order :order} key prereq-fn]
  (let [skill-kws (if (:any options) (map :key skills/skills) (keys options))]
    (skill-selection skill-kws skill-num skill-select-order key prereq-fn)))

(defn class-help-field [name value]
  [:div.m-t-5
    [:span.f-w-b (str name ":")]
   [:span.m-l-10 value]])


(defn class-help [hd saves weapon-profs armor-profs]
  [:div
   (class-help-field "Hit Die" (str "d" hd))
   (class-help-field "Saving Throw Proficiencies" (s/join ", " (map (comp s/upper-case name) saves)))
   (class-help-field "Weapon Proficiencies" (s/join ", " (map (comp name key) weapon-profs)))
   (class-help-field "Armor Proficiencies" (s/join ", " (map (comp name key) armor-profs)))])

(defn class-option [spell-lists
                    spells-map
                    plugin-subclasses-map
                    language-map
                    weapon-map
                    {:keys [name
                            key
                            help
                            hit-die
                            plugin?
                            profs
                            levels
                            ability-increase-levels
                            subclass-title
                            subclass-level
                            subclasses
                            selections
                            modifiers
                            source
                            weapon-choices
                            weapons
                            equipment
                            equipment-choices
                            armor
                            armor-choices
                            spellcasting
                            multiclass-prereqs]
                     :as cls}]
  (let [merged-class (update cls :subclasses #(into (sorted-set-by (fn [x y] (compare (:name x) (:name y)))) (concat (reverse (get plugin-subclasses-map key)) %)))
        kw (or key (common/name-to-kw name))
        {:keys [save skill-options skill-expertise-options multiclass-skill-options tool-options multiclass-tool-options tool]
         armor-profs :armor weapon-profs :weapon} profs
        {level-factor :level-factor} spellcasting
        skill-expertise-kws (if (get-in skill-expertise-options [:options :any])
                              (map :key skills/skills)
                              (keys (:options skill-expertise-options)))
        save-profs (keys save)
        spellcasting-template (spellcasting-template
                               spell-lists
                               spells-map
                               (assoc spellcasting :class-key kw)
                               merged-class)
        first-class? (fn [c] (let [first-class (first @(subscribe [::character/classes nil c]))]
                               (= kw first-class)))]
    (t/option-cfg
     {:name name
      :key kw
      :help [:div.p-t-5.p-l-10.p-r-10
             (class-help hit-die save-profs weapon-profs armor-profs)
             [:div.m-t-10 help]]
      :prereqs multiclass-prereqs
      :selections (map
                   (fn [selection]
                     (update selection ::t/tags sets/union #{kw}))
                   (concat
                    selections
                    (if (seq tool-options)
                      [(tool-prof-selection tool-options :tool-selection first-class?)])
                    (if (seq multiclass-tool-options)
                      [(tool-prof-selection multiclass-tool-options :multiclass-tool-selection (fn [c] (not= kw (first (:classes c)))))])
                    (if weapon-choices (class-weapon-options weapon-choices kw weapon-map))
                    (if armor-choices (class-armor-options armor-choices kw))
                    (if equipment-choices (class-equipment-options equipment-choices kw))
                    (if skill-options
                      [(class-skill-selection skill-options :skill-proficiency first-class?)])
                    (if (seq skill-expertise-kws)
                      [(skill-expertise-selection skill-expertise-kws (:choose skill-expertise-options))])
                    (if multiclass-skill-options
                      [(class-skill-selection multiclass-skill-options :multiclass-skill-proficiency (complement first-class?))])
                    [(t/selection-cfg
                      {:name (str name " Levels")
                       :key :levels
                       :help "These are your levels in the containing class. You can add levels by clicking the 'Add Levels' button below."
                       :new-item-fn (fn [selection options current-values]
                                      {::entity/key (-> current-values count inc level-key)})
                       :tags #{kw}
                       :options (map
                                 (partial level-option spell-lists spells-map language-map weapon-map merged-class kw spellcasting-template)
                                 (range 1 21))
                       :min 1
                       :sequential? true
                       :multiselect? true
                       :max nil})]))
      :associated-options (remove
                           nil?
                           [(class-starting-equipment-entity-options :weapons weapons)
                            (class-starting-equipment-entity-options :armor armor)
                            (class-starting-equipment-entity-options :equipment equipment)])
      :modifiers (concat
                  modifiers
                  (if (:prepares-spells? spellcasting)
                    [(mods/map-mod ?prepares-spells name true)])
                  (if (= :all (:known-mode spellcasting))
                    (let [spell-list (spell-lists kw)]
                      (mapcat
                       (fn [[lvl spell-keys]]
                         (map
                          (fn [spell-key]
                            (modifiers/spells-known-cfg lvl
                                                        {:class-key kw
                                                         :key spell-key
                                                         :class name
                                                         :ability (:ability spellcasting)}
                                                        1
                                                        [(let [slots (?class-spell-slots kw)]
                                                           (slots lvl))
                                                         (let [spell (spells-map spell-key)]
                                                           (using-source? ?option-sources (:source spell)))]))
                          spell-keys))
                       spell-list)))
                  (if armor-profs (armor-prof-modifiers armor-profs kw))
                  (if weapon-profs (weapon-prof-modifiers weapon-profs kw))
                  (if tool (tool-prof-modifiers tool kw))
                  (if level-factor [(modifiers/spell-slot-factor kw level-factor)])
                  (if (and source (not plugin?))
                    [(modifiers/used-resource source name)])
                  (if (:known-mode spellcasting)
                    [(modifiers/spells-known-mode name (:known-mode spellcasting))])
                  (remove
                   nil?
                   [(modifiers/cls kw)
                    (if save-profs (apply modifiers/saving-throws kw save-profs))]))})))

#_(defn source-url [source]
  (some-> source disp/sources :url))

(def ranger-base-cfg
  {:name "Ranger"
   :subclass-level 3
   :subclass-title "Ranger Archetype"})

(defn background-selection [cfg]
  (t/selection-cfg
   (merge
    {:name "Background"
     :tags #{:background}}
    cfg)))

(defn class-selection [cfg]
  (t/selection-cfg
   (merge
    {:name "Class"
     :order 0
     :tags #{:class}
     :multiselect? true
     :min 1
     :max nil}
    cfg)))

(defn race-selection [cfg]
  (t/selection-cfg
   (merge
    {:name "Race"
     :order 0
     :help "Race determines your appearance and helps shape your culture and background. It also affects you ability scores, size, speed, languages, and many other crucial inherent traits."
     :tags #{:race}}
    cfg)))

(def ranger-skills {:animal-handling true :athletics true :insight true :investigation true :nature true :perception true :stealth true :survival true})

(defn evasion [level page]
  {:name "Evasion"
   :page page
   :level level
   :summary "when you succeed on a DEX save to take half damage, you take none, if you fail, you take half"})

(defn uncanny-dodge-modifier [page]
  (modifiers/reaction
   {:name "Uncanny Dodge"
    :page page
    :summary "halve the damage from an attacker you can see that hits you"}))

(defn divine-strike [damage-desc page & [source]]
  (modifiers/dependent-trait
   {:level 8
    :name "Divine Strike"
    :page page
    :source source
    :frequency units5e/turns-1
    :summary (str "Add "
                  (if (>= (?class-level :cleric) 14) 2 1)
                  "d8 "
                  damage-desc
                  " damage to a successful weapon attack's damage")}))

(defn favored-enemy-types [language-map]
  {:aberration [:deep-speech :undercommon :grell :slaad]
   :beast [:giant-elk :giant-eagle :giant-owl]
   :celestial (keys language-map)
   :construct [:modron]
   :dragon [:aquan :draconic :sylvan]
   :elemental [:auran :terran :ignan :aquan]
   :fey [:draconic :elvish :sylvan :abyssal :infernal :primoridial :aquan :giant]
   :fiend (keys language-map)
   :giant [:giant :orc :undercommon]
   :monstrosity [:draconic :sylvan :elvish :hook-horror :abyssal :celestial :infernal :primordial :aquan :sphynx :umber-hulk :yeti :winter-wolf :goblin :worg]
   :ooze []
   :plant [:druidic :elvish :sylvan]
   :undead (keys language-map)})

(def humanoid-enemies
  {:bugbear [:goblin]
   :bullywug [:bullywug]
   :githyanki [:gith]
   :gitzerai [:gith]
   :gnoll [:gnoll :abyssal]
   :goblin [:goblin]
   :grimlock [:undercommon]
   :hobgoblin [:goblin]
   :kobold [:draconic]
   :koa-toa [:undercommon]
   :lizardfolk [:draconic :abyssal]
   :merfolk [:aquan]
   :orc [:orc]
   :thri-kreen [:thri-kreen]
   :troglodyte [:troglodyte]
   :yuan-ti-pureblood {:name "Yuan-Ti Pureblood"
                       :languages [:abyssal :draconic]}})

(defn druid-cantrip-selection [spell-lists spells-map class-nm]
  (t/selection-cfg
   {:name "Druid Cantrip"
    :tags #{:spells}
    :options (spell-options spells-map (get-in spell-lists [:druid 0]) ::character/wis class-nm)}))

(defn eldritch-invocation-selection [cfg]
  (t/selection-cfg
   (merge
    {:name "Eldritch Invocations"
     :multiselect? true
     :ref [:class :warlock :eldritch-invocations]
     :tags #{:spells}}
    cfg)))

(def pact-of-the-tome-name "Pact Boon: Pact of the Tome")
(def pact-of-the-chain-name "Pact Boon: Pact of the Chain")
(def pact-of-the-blade-name "Pact Boon: Pact of the Blade")

(defn has-trait-with-name-prereq [name]
  (t/option-prereq
   (str "You must have " name)
   (fn [c] (some #(= name (:name %)) @(subscribe [::character/traits nil c])))))

(def pact-of-the-tome-prereq
  (has-trait-with-name-prereq pact-of-the-tome-name))

(def pact-of-the-blade-prereq
  (has-trait-with-name-prereq pact-of-the-blade-name))

(def pact-of-the-chain-prereq
  (has-trait-with-name-prereq pact-of-the-chain-name))

(def has-eldritch-blast-prereq
  (t/option-prereq
   "You must know the edritch blast cantrip"
   (fn [c]
     (get-in @(subscribe [::character/spells-known nil c])
             [0 ["Warlock" :eldritch-blast]]))))

(defn deep-gnome-option-cfg [key source page]
  {:name "Gnome"
   :plugin? true
   :subraces
   [{:name (str "Deep Gnome (" (s/upper-case (name source)) ")")
     :key key
     :abilities {::character/dex 1}
     :modifiers [(modifiers/darkvision 120)
                 (modifiers/language :undercommon)]
     :source source
     :traits [{:name "Stone Camouflage"
               :source source
               :page page
               :summary "Advantage on hide checks in rocky terrain"}]}]})

(defmacro eldritch-invocation-option [{:keys [name summary source page prereqs modifiers trait-type frequency range]}]
  `(t/option-cfg
    {:name ~name
     :prereqs ~prereqs
     :modifiers (conj
                 ~modifiers
                 (~(case trait-type
                    :action `modifiers/action
                    :bonus-action `modifiers/bonus-action
                    :reaction `modifiers/reaction
                    `modifiers/dependent-trait)
                  {:name (str "Eldritch Invocation: " ~name)
                   :page ~page
                   :source ~source
                   :summary ~summary
                   :frequency ~frequency
                   :range ~range}))}))

(defn race-prereq [race-nms]
  (let [name-set (if (string? race-nms)
                   #{race-nms}
                   (into #{} race-nms))]
    (t/option-prereq
     (str (common/list-print name-set "or") " Only")
     (fn [c] (name-set @(subscribe [::character/race nil c]))))))

(defn subrace-prereq [race-nm subrace-nm]
  (t/option-prereq
   (str subrace-nm " Only")
   (fn [c] (and (= race-nm @(subscribe [::character/race nil c]))
                (= subrace-nm @(subscribe [::character/subrace nil c]))))))

#_(def deep-gnome-prereq
  (t/option-prereq
   "Deep Gnome only"
   (fn [c] (let [subrace @(subscribe [::character/subrace nil c])]
             (or (= "Deep Gnome (EE)" subrace)
                 (= "Deep Gnome (SCAG)" subrace))))))

#_(defn svirfneblin-magic-feat [source page]
  (feat-option
   {:name (str "Svirfneblin Magic (" (s/upper-case (name source)) ")")
    :page page
    :source source
    :summary "Can cast 'nondetection', 'blindness/deafness', 'blur', and 'disguise self'"
    :prereqs [deep-gnome-prereq]
    :modifiers [(modifiers/spells-known 3 :nondetection ::character/cha "Deep Gnome" 0 "at will")
                (modifiers/spells-known 2 :blindness-deafness ::character/cha "Deep Gnome" 0 "once per long rest")
                (modifiers/spells-known 2 :blur ::character/cha "Deep Gnome" 0 "once per long rest")
                (modifiers/spells-known 1 :disguise-self ::character/cha "Deep Gnome" 0 "once per long rest")]}))


(defn feat-prereqs [prereqs path-prereqs]
  (concat
   (map
    (fn [prereq]
      (cond
        ((into #{} character/ability-keys) prereq)
        (ability-prereq prereq 13)

        (= :spellcasting prereq)
        can-cast-spell-prereq

        :else
        (armor-prereq prereq)))
    prereqs)
   (let [race-prereqs (:race path-prereqs)
         race-keys (sequence
                    (comp
                     (filter
                      val)
                     (map
                      key))
                    race-prereqs)]
     (if (seq race-keys)
       (let [race-map @(subscribe [::races/race-map])
             race-names (map (comp :name race-map) race-keys)]
         [(race-prereq race-names)])))))

(def filter-true (filter val))

(defn magic-initiate-selection [spells-map spell-lists]
  (t/selection-cfg
   {:name "Spell Class"
    :order 0
    :tags #{:spells}
    :options [(magic-initiate-option spells-map :bard "Bard" ::character/cha spell-lists)
              (magic-initiate-option spells-map :cleric "Cleric" ::character/wis spell-lists)
              (magic-initiate-option spells-map :druid "Druid" ::character/wis spell-lists)
              (magic-initiate-option spells-map :sorcerer "Sorcerer" ::character/cha spell-lists)
              (magic-initiate-option spells-map :warlock "Warlock" ::character/cha spell-lists)
              (magic-initiate-option spells-map :wizard "Wizard" ::character/int spell-lists)]}))

(defn ritual-caster-selection [spells-map spell-lists]
  (t/selection-cfg
   {:name "Ritual Caster: Spell Class"
    :tags #{:spells}
    :order 6
    :options [(ritual-caster-option spells-map :bard "Bard" ::character/cha spell-lists)
              (ritual-caster-option spells-map :cleric "Cleric" ::character/wis spell-lists)
              (ritual-caster-option spells-map :druid "Druid" ::character/wis spell-lists)
              (ritual-caster-option spells-map :sorcerer "Sorcerer" ::character/cha spell-lists)
              (ritual-caster-option spells-map :warlock "Warlock" ::character/cha spell-lists)
              (ritual-caster-option spells-map :wizard "Wizard" ::character/int spell-lists)]}))

(defn spell-sniper-selection [spells-map spell-lists]
  (t/selection-cfg
   {:name "Spell Sniper: Spell Class"
    :tags #{:spells}
    :options [(spell-sniper-option spells-map :bard "Bard" ::character/cha spell-lists)
              (spell-sniper-option spells-map :cleric "Cleric" ::character/wis spell-lists)
              (spell-sniper-option spells-map :druid "Druid" ::character/wis spell-lists)
              (spell-sniper-option spells-map :sorcerer "Sorcerer" ::character/cha spell-lists)
              (spell-sniper-option spells-map :warlock "Warlock" ::character/cha spell-lists)
              (spell-sniper-option spells-map :wizard "Wizard" ::character/int spell-lists)]}))

(defn make-feat-selections [language-map spells-map spell-lists proficiency-weapons k v]
  (if v
    (case k
      :weapon-prof-choice [(weapon-proficiency-selection v proficiency-weapons)]
      :language-choice [(language-selection-aux (vals language-map) v)]
      :skill-tool-choice (map
                          (fn [i]
                            (skilled-selection (str "Skill/Tool " (inc i))))
                          (range v))
      :ritual-casting [(ritual-caster-selection spells-map
                                                spell-lists)]
      :magic-novice [(magic-initiate-selection spells-map
                                               spell-lists)]
      :attack-spell [(spell-sniper-selection spells-map
                                             spell-lists)]
      nil)))

(defn collect-map-modifiers [m modifier-fn]
  (sequence
   (comp
    filter-true
    (map
     (fn [[k]]
       (modifier-fn k))))
   m))

(defn make-feat-modifiers [k v option-key]
  (if v
    (case k
      :initiative [(modifiers/initiative v)]
      :two-weapon-ac-1 [dual-wield-ac-mod]
      :two-weapon-any-one-handed [dual-wield-weapon-mod]
      :max-hp-bonus [(mods/modifier ?hit-point-level-bonus (+ v ?hit-point-level-bonus))]
      :passive-investigation-5 [(modifiers/passive-investigation 5)]
      :passive-perception-5 [(modifiers/passive-perception 5)]
      :medium-armor-max-dex-3 [medium-armor-master-max-bonus]
      :medium-armor-stealth [medium-armor-master-stealth]
      :speed [(modifiers/speed v)]
      :flying-speed [(modifiers/flying-speed-override v)]
      :flying-speed-equals-walking-speed [(modifiers/flying-speed-equal-to-walking)]
      :swimming-speed [(modifiers/swimming-speed-override v)]
      :saving-throw-advantage-traps [(modifiers/saving-throw-advantage [:traps])]
      :lizardfolk-ac (if v
                       [(mods/modifier ?natural-ac-bonus 3)
                        (mods/modifier ?armor-class-with-armor
                                      (fn [armor & [shield]]
                                        (max (+ ?base-armor-class
                                                (if shield (?shield-ac-bonus shield) 0))
                                             (?armor-class-with-armor armor shield))))])
      :tortle-ac (if v
                   [(mods/modifier ?natural-ac-bonus 7)
                    (mods/modifier ?armor-class-with-armor
                                  (fn [armor & [shield]]
                                    (+ 17
                                       (if shield (?shield-ac-bonus shield) 0))))])
      :language (collect-map-modifiers
                 v
                 #(modifiers/language %))
      :saving-throw-advantage (collect-map-modifiers
                               v
                               #(modifiers/saving-throw-advantage [%]))
      :skill-prof (collect-map-modifiers
                   v
                   #(modifiers/skill-proficiency %))
      :tool-prof-or-expertise (collect-map-modifiers
                                v
                                #(tool-prof-or-expertise % option-key))
      :skill-prof-or-expertise (collect-map-modifiers
                                v
                                #(skill-prof-or-expertise % option-key))
      :armor-prof (collect-map-modifiers
                   v
                   #(modifiers/armor-proficiency %))
      :weapon-prof (collect-map-modifiers
                   v
                   #(modifiers/weapon-proficiency %))
      :damage-resistance (collect-map-modifiers
                          v
                          #(modifiers/damage-resistance %))
      :damage-immunity (collect-map-modifiers
                        v
                        #(modifiers/damage-immunity %))
      nil)))

(defn plugin-modifiers [props option-key]
  (reduce
   (fn [mods [k v]]
     (let [feat-mods (make-feat-modifiers k v option-key)]
       (if feat-mods
         (concat mods feat-mods)
         mods)))
   []
   props))

(defn feat-modifiers [key name description props ability-increases]
  (let [without-saves (sets/intersection ability-increases
                                         (into #{} character/ability-keys))]
    (concat
     (plugin-modifiers props key)
     (if (= 1 (count without-saves))
       (let [ability-kw (first without-saves)
             ability-mod (modifiers/ability ability-kw 1)]
         (if (:saves? ability-increases)
           [ability-mod
            (modifiers/saving-throws nil ability-kw)]
           [ability-mod]))
       [])
     [(modifiers/trait-cfg
       {:name name
        :description description})])))

(defn feat-selections [language-map spells-map spell-lists proficiency-weapons props ability-increases]
  (let [without-saves (sets/intersection ability-increases
                                         (into #{} character/ability-keys))]
    (reduce
     (fn [selections [k v]]
       (let [feat-selections (make-feat-selections language-map spells-map spell-lists proficiency-weapons k v)]
         (if feat-selections
           (concat selections feat-selections)
           selections)))
     (if (< 1 (count without-saves))
       [(if (:saves? ability-increases)
          (ability-increase-selection
           without-saves
           1
           false
           [(fn [k] (modifiers/saving-throws nil k))])
          (ability-increase-selection
           without-saves
           1
           false))]
       [])
     props)))


(defn feat-option-from-cfg [language-map
                            spells-map
                            spell-lists
                            custom-and-standard-weapons
                            {:keys [name
                                    key
                                    icon
                                    description
                                    prereqs
                                    path-prereqs
                                    props
                                    ability-increases
                                    edit-event]}]
  (let [feat-mods (feat-modifiers key
                                  name
                                  description
                                  props
                                  ability-increases)
        feat-selections (feat-selections language-map
                                         spells-map
                                         spell-lists
                                         custom-and-standard-weapons
                                         props
                                         ability-increases)]
    (t/option-cfg
     {:name name
      :key key
      :icon icon
      :edit-event edit-event
      :modifiers feat-mods
      :selections feat-selections
      :summary description
      :prereqs (feat-prereqs prereqs path-prereqs)})))

(def draconic-ancestries
  [{:name "Black"
    :breath-weapon {:damage-type :acid
                    :area-type :line
                    :line-width 5
                    :line-length 30
                    :save ::character/dex}}
   {:name "Blue"
    :breath-weapon {:damage-type :lightning
                    :area-type :line
                    :line-width 5
                    :line-length 30
                    :save ::character/dex}}
   {:name "Brass"
    :breath-weapon {:damage-type :fire
                    :area-type :line
                    :line-width 5
                    :line-length 30
                    :save ::character/dex}}
   {:name "Bronze"
    :breath-weapon {:damage-type :lightning
                    :area-type :line
                    :line-width 5
                    :line-length 30
                    :save ::character/dex}}
   {:name "Copper"
    :breath-weapon {:damage-type :acid
                    :area-type :line
                    :line-width 5
                    :line-length 30
                    :save ::character/dex}}
   {:name "Gold"
    :breath-weapon {:damage-type :fire
                    :area-type :cone
                    :length 15
                    :save ::character/dex}}
   {:name "Green"
    :breath-weapon {:damage-type :poison
                    :area-type :cone
                    :length 15
                    :save ::character/con}}
   {:name "Red"
    :breath-weapon {:damage-type :fire
                    :area-type :cone
                    :length 15
                    :save ::character/dex}}
   {:name "Silver"
    :breath-weapon {:damage-type :cold
                    :area-type :cone
                    :length 15
                    :save ::character/con}}
   {:name "White"
    :breath-weapon {:damage-type :cold
                    :area-type :cone
                    :length 15
                    :save ::character/con}}])
