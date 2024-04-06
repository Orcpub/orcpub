(ns orcpub.dnd.e5.classes
  (:require #?(:clj [clojure.spec.alpha :as spec])
            #?(:cljs [cljs.spec.alpha :as spec])
            [orcpub.common :as common]
            [orcpub.template :as t]
            [orcpub.modifiers :as mod]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.modifiers :as mod5e]
            [orcpub.dnd.e5.weapons :as weapon5e]
            [orcpub.dnd.e5.equipment :as equipment5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.units :as units5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.template-base :as t-base]
            [re-frame.core :refer [reg-sub reg-sub-raw dispatch subscribe]]
            [clojure.string :as s]))

(spec/def ::name (spec/and string? common/starts-with-letter?))
(spec/def ::key (spec/and keyword? common/keyword-starts-with-letter?))
(spec/def ::option-pack string?)
(spec/def ::homebrew-class (spec/keys :req-un [::name ::key ::option-pack]))

(spec/def ::class (spec/and keyword? common/keyword-starts-with-letter?))
(spec/def ::homebrew-subclass (spec/keys :req-un [::name ::key ::class ::option-pack]))

(spec/def ::homebrew-invocation (spec/keys :req-un [::name ::key ::option-pack]))

(defn class-level [levels class-kw]
  (get-in levels [class-kw :class-level]))

(defn extra-attack-trait [page]
  (mod5e/trait-cfg
   {:name "Extra Attack"
    :page page
    :summary "Attack twice when taking Attack action"}))

(defn barbarian-option [spells spells-map plugin-subclasses-map language-map weapon-map]
  (opt5e/class-option
   spells
   spells-map
   plugin-subclasses-map
   language-map
   weapon-map
   {:name "Barbarian"
    :key :barbarian
    :hit-die 12
    :ability-increase-levels [4 8 12 16 19]
    :profs {:armor {:light true :medium true :shields false}
            :weapon {:simple false :martial false}
            :save {::char5e/str true ::char5e/con true}
            :skill-options {:choose 2 :options {:animal-handling true :athletics true :intimidation true :nature true :perception true :survival true}}}
    :multiclass-prereqs [(opt5e/ability-prereq ::char5e/str 13)]
    :weapon-choices [{:name "Martial Weapon"
                      :options {:greataxe 1
                                :martial 1}}
                     {:name "Simple Weapon"
                      :options {:handaxe 2
                                :simple 1}}]
    :weapons {:javelin 4}
    :equipment {:explorers-pack 1}
    :modifiers [(mod/vec-mod ?unarmored-defense :barbarian)
                (mod/cum-sum-mod ?unarmored-ac-bonus (?ability-bonuses ::char5e/con)
                                 nil
                                 nil
                                 [(= :barbarian (first ?unarmored-defense))])
                (mod/cum-sum-mod ?unarmored-with-shield-ac-bonus (?ability-bonuses ::char5e/con)
                                 nil
                                 nil
                                 [(= :barbarian (first ?unarmored-defense))])
                (mod5e/bonus-action
                 {:name "Rage"
                  :page 48
                  :duration units5e/minutes-1
                  :frequency (units5e/long-rests (condp <= (?class-level :barbarian)
                                              17 6
                                              12 5
                                              6 4
                                              3 3
                                              2))
                  :summary (str "Advantage on Strength checks and saves; melee damage bonus "
                                (common/bonus-str (condp <= (?class-level :barbarian)
                                                    16 4
                                                    9 3
                                                    2))
                                "; resistance to bludgeoning, piercing, and slashing damage")})]
    :levels {5 {:modifiers [(extra-attack-trait 49)
                            (mod5e/num-attacks 2)
                            (mod/modifier ?speed-with-armor (fn [armor] (if (not= :heavy (:type armor))
                                                                          (+ 10 ?speed)
                                                                          ?speed)))
                            (mod5e/dependent-trait
                             {:name "Fast Movement"
                              :page 49
                              :summary (str "Your speed increases to " (+ 10 ?speed) " when not heavily armored")})]}
             9 {:modifiers [(mod5e/dependent-trait
                             {:name "Brutal Critical"
                              :page 49
                              :summary (let [die-count (condp <= (?class-level :barbarian)
                                                         17 "three"
                                                         13 "two"
                                                         "one")]
                                         (str die-count
                                              " additional damage "
                                              (if (= "one" die-count)
                                                "die"
                                                "dice")
                                              " for melee criticals"))})]}
             18 {:modifiers [(mod5e/dependent-trait
                              {:name "Indomitable Might"
                               :level 18
                               :page 49
                               :summary (let [str-score (::char5e/str ?abilities)]
                                          (str "Min strength check value is " str-score))})]}
             20 {:modifiers [(mod5e/ability ::char5e/str 4)
                             (mod5e/ability ::char5e/con 4)]}}
    :traits [{:name "Reckless Attack"
              :level 2
              :page 48
              :summary "Advantage on attacks using Strength, attacks against you have advantage as well."}
             {:name "Danger Sense"
              :level 2
              :page 48
              :summary "Advantage on DEX saves against effects you can see."}
             {:name "Feral Instinct"
              :level 7
              :page 49
              :summary "Advantage on initiative, surprise doesn't keep you from attacking if you enter rage"}
             {:name "Relentless Rage"
              :level 11
              :page 49
              :summary "If raging, are reduced to 0 HP, aren't killed, and make a DC 10 save (+5 for each time you've used this feature between rests), you go to 1 HP instead."}
             {:name "Persistent Rage"
              :level 15
              :page 49
              :summary "rage only ends early if you choose to end it or you fall unconscious"}]
    :subclass-level 3
    :subclass-title "Primal Path"
    :subclass-help "Your primal path shapes the nature of your barbarian rage and gives you additional features."
    :subclasses [{:name "Path of the Berserker"
                  :levels {10 {:modifiers [(mod5e/action
                                            {:name "Intimidating Presence"
                                             :level 10
                                             :page 49
                                             :summary (str "Frighten (Wisdom save DC " (?spell-save-dc ::char5e/cha) ") a creature with 30 ft.")})]}
                           14 {:modifiers [(mod5e/reaction
                                            {:name "Retaliation"
                                             :page 49
                                             :level 14
                                             :summary "Make a melee weapon attack against a creature within 5 ft. that deals damage to you."})]}}
                  :traits [{:name "Frenzy"
                            :level 3
                            :page 49
                            :summary "You can frenzy when you rage, affording you a single melee weapon attack as a bonus action on each turn until the rage ends. When the rage ends, you suffer 1 level of exhaustrion"}
                           {:name "Mindless Rage"
                            :level 6
                            :page 49
                            :summary "Can't be charmed or frightened while raging."}]}
                 #_{:name "Path of the Totem Warrior"
                    :levels {3 {:modifiers [(mod5e/spells-known 2 :beast-sense nil "Barbarian" 1 "ritual only")
                                            (mod5e/spells-known 1 :speak-with-animals nil "Barbarian" 1 "ritual only")]
                                :selections [(t/selection-cfg
                                              {:name "Totem Spirit"
                                               :tags #{:class}
                                               :order 2
                                               :options [(t/option-cfg
                                                          {:name "Bear"
                                                           :modifiers [(mod5e/trait-cfg
                                                                        {:name "Totem Spirit: Bear"
                                                                         :page 50
                                                                         :summary "While raging, you have resistance to all damage but psychic damage"})]})
                                                         (t/option-cfg
                                                          {:name "Eagle"
                                                           :modifiers [(mod5e/trait-cfg
                                                                        {:name "Totem Spirit: Eagle"
                                                                         :page 50
                                                                         :summary "While raging and not wearing heavy armor, opportunity attacks against you have disadvantage, and you can Dash as a bonus action."})]})
                                                         (t/option-cfg
                                                          {:name "Wolf"
                                                           :modifiers [(mod5e/trait-cfg
                                                                        {:name "Totem Spirit: Wolf"
                                                                         :page 50
                                                                         :summary "While raging, allies have advantage against enemies within 5 ft."})]})]})]}
                             6 {:selections [(t/selection-cfg
                                              {:name "Aspect of the Beast"
                                               :tags #{:class}
                                               :order 3
                                               :options [(t/option-cfg
                                                          {:name "Bear"
                                                           :modifiers [(mod5e/trait-cfg
                                                                        {:name "Aspect of the Beast: Bear"
                                                                         :page 50
                                                                         :summary "2X carrying capacity, advantage lift, push, pull, or break Strength checks."})]})
                                                         (t/option-cfg
                                                          {:name "Eagle"
                                                           :modifiers [(mod5e/trait-cfg
                                                                        {:name "Aspect of the Beast: Eagle"
                                                                         :page 50
                                                                         :summary "See clearly up to a mile as if no more than 100 ft., no disadvantage on perception checks in dim light."})]})
                                                         (t/option-cfg
                                                          {:name "Wolf"
                                                           :modifiers [(mod5e/trait-cfg
                                                                        {:name "Aspect of the Beast: Wolf"
                                                                         :page 50
                                                                         :summary "Track at fast pace, stealthy at normal pace"})]})]})]}
                             10 {:modifiers [(mod5e/spells-known 5 :commune-with-nature nil "Barbarian" 1 "ritual only")]}
                             14 {:selections [(t/selection-cfg
                                               {:name "Totemic Attunement"
                                                :tags #{:class}
                                                :order 4
                                                :options [(t/option-cfg
                                                           {:name "Bear"
                                                            :modifiers [(mod5e/trait-cfg
                                                                         {:name "Totemic Attunement: Bear"
                                                                          :page 50
                                                                          :summary "While raging, hostile creatures within 5 ft. have disadvantage on attack rolls against anyone but you."})]})
                                                          (t/option-cfg
                                                           {:name "Eagle"
                                                            :modifiers [(mod5e/trait-cfg
                                                                         {:name "Totemic Attunement: Eagle"
                                                                          :page 50
                                                                          :summary "While raging, you gain flying speed equal to your walking speed, falling if you end your turn in the air."})]})
                                                          (t/option-cfg
                                                           {:name "Wolf"
                                                            :modifiers [(mod5e/trait-cfg
                                                                         {:name "Totemic Attunement: Wolf"
                                                                          :page 50
                                                                          :summary "While raging, if you hit a Large or smaller creature, you can use a bonus action to knock it prone."})]})]})]}}}]}))

(defn bardic-inspiration-die [levels]
  (condp <= (class-level levels :bard)
    15 12
    10 10
    5 8
    6))

(def musical-instrument-choice-cfg
  {:name "Musical Instrument"
   :options (zipmap (map :key equipment5e/musical-instruments) (repeat 1))})

(defn bard-option [spells spells-map plugin-subclasses-map language-map weapon-map]
  (opt5e/class-option
   spells
   spells-map
   plugin-subclasses-map
   language-map
   weapon-map
   {:name "Bard"
    :key :bard
    :hit-die 8
    :ability-increase-levels [4 8 12 16 19]
    :multiclass-prereqs [(opt5e/ability-prereq ::char5e/cha 13)]
    :profs {:armor {:light false}
            :weapon {:simple true :crossbow-hand true :longsword true :rapier true :shortsword true}
            :save {::char5e/dex true ::char5e/cha true}
            :skill-options {:choose 3 :options {:any true}}
            :multiclass-skill-options {:choose 1 :options {:any true}}
            :tool-options {:musical-instrument 3}
            :multiclass-tool-options {:musical-instrument 1}}
    :weapon-choices [{:name "Weapon"
                      :options {:rapier 1
                                :longsword 1
                                :simple 1}}]
    :weapons {:dagger 1}
    :equipment-choices [{:name "Equipment Pack"
                         :options {:diplomats-pack 1
                                   :entertainers-pack 1}}
                        musical-instrument-choice-cfg]
    :armor {:leather 1}
    :spellcaster true
    :spellcasting {:level-factor 1
                   :cantrips-known {1 2 4 1 10 1}
                   :spells-known {1 4
                                  2 1
                                  3 1
                                  4 1
                                  5 1
                                  6 1
                                  7 1
                                  8 1
                                  9 1
                                  11 1
                                  13 1
                                  15 1
                                  17 1}
                   :known-mode :schedule
                   :ability ::char5e/cha}
    :modifiers [(mod5e/bonus-action
                 {:name "Bardic Inspiration"
                  :page 53
                  :frequency (units5e/long-rests
                              (max 1 (?ability-bonuses ::char5e/cha)))
                  :summary (str "Inspire another creature with a 1d"
                                (bardic-inspiration-die ?levels)
                                " that it can, within the next 10 min., add to a d20 roll")})]
    :levels {2 {:modifiers [(mod/vec-mod ?default-skill-bonus-fns
                                         (fn [_]
                                           (int (/ ?prof-bonus 2))))
                            (mod/cum-sum-mod ?initiative (int (/ ?prof-bonus 2)))
                            (mod5e/dependent-trait
                             {:name "Jack of All Trades"
                              :page 54
                              :summary (str (common/bonus-str (int (/ ?prof-bonus 2))) " to ability checks that don't already include your proficiency bonus")})
                            (mod5e/dependent-trait
                             {:name "Song of Rest"
                              :page 54
                              :level 2
                              :summary (str "With a song, you and friendly creatures gain 1d"
                                            (mod5e/level-val
                                             (?class-level :bard)
                                             {9 8
                                              13 10
                                              17 12
                                              :default 6})
                                            " additional healing at the end of a short rest")})]}
             3 {:selections [(opt5e/expertise-selection 2)]}
             6 {:modifiers [(mod5e/action
                             {:name "Countercharm"
                              :level 6
                              :page 54
                              :summary "performance during your turn that gives you and friendly creatures within 30 ft. advantage on frightened or charmed saves."})]}
             10 {:selections (conj [(opt5e/bard-magical-secrets spells-map 10)]
                                   (opt5e/expertise-selection 2))}
             14 {:selections [(opt5e/bard-magical-secrets spells-map 14)]}
             18 {:selections [(opt5e/bard-magical-secrets spells-map 18)]}}
    :traits [{:name "Font of Inspiration"
              :level 5
              :page 54
              :summary "regain all uses of Bardic Inspiration at the end of a rest"}
             {:name "Superior Inspiration"
              :level 20
              :page 54
              :summary "regain 1 use of Bardic Inspiration if you have none remaining when rolling initiative"
              }]
    
    :subclass-level 3
    :subclass-title "Bard College"
    :subclass-help "Your bard college is a loose association that preserves bardic traditions and affords additional features"
    :subclasses [{:name "College of Lore"
                  :profs {:skill-options {:choose 3 :options {:any true}}}
                  :modifiers [(mod5e/reaction
                               {:name "Cutting Words"
                                :level 3
                                :page 54
                                :summary (str "expend a use of Bardic Inspiration to subtract 1d"
                                              (bardic-inspiration-die ?levels)
                                              " from an attack, ability, or damage roll made by a creature within 60 ft.")})]
                  
                  :levels {6 {:selections [(opt5e/bard-magical-secrets spells-map 6)]}
                           14 {:modifiers [(mod5e/dependent-trait
                                            {:name "Peerless Skill"
                                             :level 14
                                             :page 55
                                             :summary (str "expend one use of Bardic Inspiration to add 1d"
                                                           (bardic-inspiration-die ?levels)
                                                           " to an ability check")})]}}}
                 #_{:name "College of Valor"
                    :profs {:armor {:medium true
                                    :shields true}
                            :weapon {:martial true}}
                    :levels {3 {:modifiers [(mod5e/trait-cfg
                                             {:name "Combat Inspiration"
                                              :page 55
                                              :summary "a creature can add your bardic inspiration to a damage roll or to it's AC against an attack"})]}
                             6 {:modifiers [(extra-attack-trait 55)
                                            (mod5e/num-attacks 2)]}
                             14 {:modifiers [(mod5e/bonus-action
                                              {:name "Battle Magic"
                                               :page 55
                                               :summary "make a weapon attack when you use your action to cast a bard spell"})]}}}]}))

(defn blessings-of-knowledge-skill [skill-name]
  (let [skill-kw (common/name-to-kw skill-name)]
    (t/option-cfg
     {:name skill-name
      :key skill-kw
      :modifiers [(mod5e/skill-proficiency skill-kw)
                  (mod5e/skill-expertise skill-kw)]})))

(def spell-level-to-cleric-level
  {1 1
   2 3
   3 5
   4 7
   5 9})

(defn cleric-option [spells spells-map plugin-subclasses-map language-map weapon-map]
  (opt5e/class-option
   spells
   spells-map
   plugin-subclasses-map
   language-map
   weapon-map
   {:name "Cleric",
    :key :cleric
    :spellcasting {:level-factor 1
                   :cantrips-known {1 3 4 1 10 1}
                   :known-mode :all
                   :ability ::char5e/wis
                   :prepares-spells? true}
    :multiclass-prereqs [(opt5e/ability-prereq ::char5e/wis 13)]
    :spellcaster true
    :hit-die 8,
    :ability-increase-levels [4 8 12 16 19]
    :profs {:armor {:light false :medium false :shields false}
            :weapon {:simple true}
            :save {::char5e/wis true ::char5e/cha true}
            :skill-options {:choose 2 :options {:history true :insight true :medicine true :persuasion true :religion true}}}
    :equipment-choices [{:name "Equipment Pack"
                         :options {:priests-pack 1
                                   :explorers-pack 1}}]
    :weapon-choices [{:name "Cleric Weapon"
                      :options {:mace 1
                                :warhammer 1}}]
    :armor-choices [{:name "Armor"
                     :options {:scale-mail 1
                               :leather 1
                               :chain-mail 1}}]
    :armor {:shield 1}
    :selections [(opt5e/new-starting-equipment-selection
                  :cleric
                  {:name "Additional Weapon"
                   :options [(t/option-cfg
                              {:name "Light Crossbow and 20 Bolts"
                               :modifiers [(mod5e/weapon :crossbow-light 1)
                                           (mod5e/equipment :crossbow-bolt 20)]})
                             (t/option-cfg
                              {:name "Simple Weapon"
                               :selections [(opt5e/new-starting-equipment-selection
                                             :cleric
                                             {:name "Simple Weapon"
                                              :options (opt5e/simple-weapon-options 1 (vals weapon-map))
                                              :min 1
                                              :max 1})]})]})
                 (opt5e/new-starting-equipment-selection
                  :cleric
                  {:name "Holy Symbol"
                   :options (map
                             #(opt5e/starting-equipment-option % 1)
                             equipment5e/holy-symbols)})]
    :levels {2 {:modifiers [(mod5e/dependent-trait
                             {:page 59
                              :name "Channel Divinity"
                              :summary "Channel divine power using Turn Undead or one of your domain Channel Divinity options."
                              :frequency (units5e/rests (mod5e/level-val
                                                         (?class-level :cleric)
                                                         {6 2
                                                          18 3
                                                          :default 1}))})
                            (mod5e/action
                             {:page 59
                              :name "Channel Divinity: Turn Undead"
                              :summary (str "undead within 30 feet must make a DC "
                                            (?spell-save-dc ::char5e/wis)
                                            " Wisdom save or be turned for 1 min. or until damaged")})]}
             5 {:modifiers [(mod5e/dependent-trait
                             {:level 5
                              :name "Destroy Undead"
                              :page 59
                              :summary (str "Destroy CR "
                                            (let [level (?class-level :cleric)]
                                              (mod5e/level-val
                                               level
                                               {5 "1/2"
                                                8 1
                                                11 2
                                                14 3
                                                17 4}))
                                            " or less creatures who fail turn save.")})]}
             
             10 {:modifiers [(mod5e/dependent-trait
                              {:name "Divine Intervention"
                               :page 59
                               :summary (str
                                         "You call for aid from your deity, succeeding "
                                         (if (= 20 (?class-level :cleric))
                                           "automatically"
                                           "if you make a percentile roll less than or equal to your cleric level"))})]}}
    :subclass-level 1
    :subclass-title "Divine Domain"
    :subclasses [{:name "Life Domain"
                  :profs {:armor {:heavy true}}
                  :modifiers [(opt5e/cleric-spell 1 :bless 1)
                              (opt5e/cleric-spell 1 :cure-wounds 1)
                              (opt5e/cleric-spell 2 :lesser-restoration 3)
                              (opt5e/cleric-spell 2 :spiritual-weapon 3)
                              (opt5e/cleric-spell 3 :beacon-of-hope 5)
                              (opt5e/cleric-spell 3 :revivify 5)
                              (opt5e/cleric-spell 4 :death-ward 7)
                              (opt5e/cleric-spell 4 :guardian-of-faith 7)
                              (opt5e/cleric-spell 5 :mass-cure-wounds 9)
                              (opt5e/cleric-spell 5 :raise-dead 9)]
                  :levels {2 {:modifiers [(mod5e/action
                                           {:name "Channel Divinity: Preserve Life"
                                            :summary (str "Distribute "
                                                          (* 5 (?class-level :cleric))
                                                          " HPs healing among any creatures within 30 ft., each can be restored to at most 1/2 their HP max")})]}
                           8 {:modifiers [(opt5e/divine-strike "radiant" 60)]}}
                  :traits [{:level 1
                            :name "Disciple of Life"
                            :page 60
                            :summary "1st level or greater healing spells increase healing by 2 + spell's level HPs"}
                           {:level 6
                            :name "Blessed Healer"
                            :page 60
                            :summary "When you cast spells that heal a creature other than you, you regain 2 + spell's level HPs"}
                           {:level 17
                            :name "Supreme Healing"
                            :summary "Instead of rolling healing, use max possible roll value." }]}
                 #_{:name "Knowledge Domain"
                    :modifiers [(opt5e/cleric-spell 1 :command 1)
                                (opt5e/cleric-spell 1 :identify 1)
                                (opt5e/cleric-spell 2 :augury 3)
                                (opt5e/cleric-spell 2 :suggestion 3)
                                (opt5e/cleric-spell 3 :nondetection 5)
                                (opt5e/cleric-spell 3 :speak-with-dead 5)
                                (opt5e/cleric-spell 4 :arcane-eye 7)
                                (opt5e/cleric-spell 4 :confusion 7)
                                (opt5e/cleric-spell 5 :legend-lore 9)
                                (opt5e/cleric-spell 5 :scrying 9)]
                    :selections [(opt5e/language-selection opt5e/languages 2)
                                 (t/selection-cfg
                                  {:name "Blessings of Knowledge Skills"
                                   :tags #{:profs :skill-profs}
                                   :options (map
                                             blessings-of-knowledge-skill
                                             ["Arcana" "History" "Nature" "Religion"])
                                   :min 2
                                   :max 2})]
                    :levels {2 {:modifiers [(mod5e/action
                                             {:page 59
                                              :summary "Become proficient in a tool or skill for 10 mins."
                                              :name "Channel Divinity: Knowledge of the Ages"})]}
                             6 {:modifiers [(mod5e/action
                                             {:page 59
                                              :name "Channel Divinity: Read Thoughts"
                                              :summary (str "a creature within 60 ft. must make a DC "
                                                            (?spell-save-dc ::char5e/wis)
                                                            " Wisdom save or you can read it's thoughts for 1 min, use an action to end the effect and cast 'suggestion' without using a slot and with no save")})]}
                             8 {:modifiers [(opt5e/potent-spellcasting 60)]}}
                    :traits [
                             {:level 17
                              :page 60
                              :name "Visions of the Past"
                              :summary "Learn the history of an object you hold or area you are in"}]}
                 #_{:name "Light Domain"
                    :modifiers [(opt5e/cleric-spell 0 :light 1)
                                (opt5e/cleric-spell 1 :burning-hands 1)
                                (opt5e/cleric-spell 1 :faerie-fire 1)
                                (opt5e/cleric-spell 2 :flaming-sphere 3)
                                (opt5e/cleric-spell 2 :scorching-ray 3)
                                (opt5e/cleric-spell 3 :daylight 5)
                                (opt5e/cleric-spell 3 :fireball 5)
                                (opt5e/cleric-spell 4 :guardian-of-faith 7)
                                (opt5e/cleric-spell 4 :wall-of-fire 7)
                                (opt5e/cleric-spell 5 :flame-strike 9)
                                (opt5e/cleric-spell 5 :scrying 9)
                                (mod5e/reaction
                                 {:name "Warding Flare"
                                  :page 61
                                  :summary "impose disadvantage on an attack roll against you"
                                  :frequency (units5e/long-rests
                                              (max 1 (?ability-bonuses ::char5e/wis)))})]
                    :levels {2 {:modifiers [(mod5e/action
                                             {:level 2
                                              :class-key :cleric
                                              :name "Channel Divinity: Radiance of the Dawn"
                                              :page 61
                                              :range {:plural :feet
                                                      :amount 30}
                                              :summary (str "Dispel magical darkness and deal 2d10 + "
                                                            (?class-level :cleric)
                                                            " radiant damage (half on successful DC "
                                                            (?spell-save-dc ::char5e/wis)
                                                            " Constitution save) to hostile creatures")})]}
                             6 {:modifiers [(mod5e/reaction
                                             {:level 6
                                              :name "Improved Flare"
                                              :page 61
                                              :summary "use warding flare when another creature within 30 ft. is attacked"})]}
                             8 {:modifiers [(opt5e/potent-spellcasting 61)]}
                             17 {:modifiers [(mod5e/action
                                              {:level 17
                                               :page 61
                                               :name "Corona of Light"
                                               :summary "emit bright light for 60 ft. and 30 beyond that, enemies in the bright light have disadvantage on saves against spells that deal radiant or fire damage"})]}}}
                 #_{:name "Nature Domain"
                    :profs {:armor {:heavy true}
                            :skill-options {:choose 1 :options {:animal-handling true :nature true :survival true}}}
                    :modifiers [(opt5e/cleric-spell 1 :animal-friendship 1)
                                (opt5e/cleric-spell 1 :speak-with-animals 1)
                                (opt5e/cleric-spell 2 :barkskin 3)
                                (opt5e/cleric-spell 2 :spike-growth 3)
                                (opt5e/cleric-spell 3 :plant-growth 5)
                                (opt5e/cleric-spell 3 :wind-wall 5)
                                (opt5e/cleric-spell 4 :dominate-beast 7)
                                (opt5e/cleric-spell 4 :grasping-vine 7)
                                (opt5e/cleric-spell 5 :insect-plague 9)
                                (opt5e/cleric-spell 5 :tree-stride 9)]
                    :levels {2 {:modifiers [(mod5e/action
                                             {:name "Channel Divinity: Charm Animals and Plants"
                                              :level 2
                                              :page 62
                                              :range {:plural :feet
                                                      :amount 30}
                                              :summary (str "charm beasts and plant creatures unless they succeed on a DC "
                                                            (?spell-save-dc ::char5e/wis)
                                                            " Wisdom save")})]}
                             6 {:modifiers [(mod5e/reaction
                                             {:name "Dampen Elements"
                                              :level 6
                                              :page 62
                                              :range {:plural :feet
                                                      :amount 30}
                                              :summary "to a creature that takes fire, cold, acid, lighting, or thunder damage, grant resistance to that damage"})]}
                             8 {:modifiers [(opt5e/divine-strike "cold, fire, or lighting" 62)]}
                             17 {:modifiers [(mod5e/bonus-action
                                              {:name "Master of Nature"
                                               :level 17
                                               :page 62
                                               :summary "command creatures charmed with your Charm Animals and Plants"})]}}
                    :selections [(opt5e/druid-cantrip-selection "Cleric")]}
                 #_{:name "Tempest Domain"
                    :profs {:armor {:heavy true}
                            :weapon {:martial true}}
                    :modifiers [(opt5e/cleric-spell 1 :fog-cloud 1)
                                (opt5e/cleric-spell 1 :thunderwave 1)
                                (opt5e/cleric-spell 2 :gust-of-wind 3)
                                (opt5e/cleric-spell 2 :shatter 3)
                                (opt5e/cleric-spell 3 :call-lightning 5)
                                (opt5e/cleric-spell 3 :sleet-storm 5)
                                (opt5e/cleric-spell 4 :control-water 7)
                                (opt5e/cleric-spell 4 :ice-storm 7)
                                (opt5e/cleric-spell 5 :destructive-wave 9)
                                (opt5e/cleric-spell 5 :insect-plague 9)
                                (mod5e/reaction
                                 {:name "Wrath of the Storm"
                                  :page 62
                                  :frequency (units5e/long-rests
                                              (max 1 (?ability-bonuses ::char5e/wis)))
                                  :summary (str "When a creature within 5 ft. hits you, you deal 2d8 lightning or thunder damage to them (half that on successful DC "
                                                (?spell-save-dc ::char5e/wis)
                                                " Dexterity save).")})]
                    :levels {2 {:modifiers [(mod5e/trait-cfg
                                             {:name "Channel Divinity: Destructive Wrath"
                                              :page 62
                                              :level 2
                                              :summary "Rather than roll lighting or thunder damage, deal max damage"})]}
                             8 {:modifiers [(opt5e/divine-strike "thunder" 62)]}
                             17 {:modifiers [(mod5e/flying-speed-equal-to-walking)]}}
                    :traits [{:name "Thunderbolt Strike"
                              :page 62
                              :level 6
                              :summary "Push a Large or smaller creature up to 10 ft. when you deal lightning damage to it"}
                             {:name "Stormborn"
                              :page 62
                              :level 17
                              :summary "Flying speed equal to your walking speed"}]}
                 #_{:name "Trickery Domain"
                    :modifiers [(opt5e/cleric-spell 1 :charm-person 1)
                                (opt5e/cleric-spell 1 :disguise-self 1)
                                (opt5e/cleric-spell 2 :mirror-image 3)
                                (opt5e/cleric-spell 2 :pass-without-trace 3)
                                (opt5e/cleric-spell 3 :blink 5)
                                (opt5e/cleric-spell 3 :dispel-magic 5)
                                (opt5e/cleric-spell 4 :dimension-door 7)
                                (opt5e/cleric-spell 4 :polymorph 7)
                                (opt5e/cleric-spell 5 :dominate-person 9)
                                (opt5e/cleric-spell 5 :modify-memory 9)
                                (mod5e/action
                                 {:name "Blessing of the Trickster"
                                  :page 63
                                  :duration units5e/hours-1
                                  :summary "Give another creature advantage on stealth checks"})]
                    :levels {2 {:modifiers [(mod5e/action
                                             {:name "Channel Divinity: Invoke Duplicity"
                                              :level 2
                                              :page 63
                                              :summary "create illusion of yourself for 1 min. or concentration. Move it 30 ft. as a bonus action, cast spells as if in illusion's space, gain advantage on attacks on a creature both you and the illusion are within 5 ft. of"})]}
                             6 {:modifiers [(mod5e/action
                                             {:name "Channel Divinity: Cloak of Shadows"
                                              :level 6
                                              :page 63
                                              :summary "become invisible until end of your next turn"})]}
                             8 {:modifiers [(opt5e/divine-strike "poison" 63)]}
                             17 {:modifiers [(mod5e/action
                                              {:name "Improved Duplicity"
                                               :level 17
                                               :page 63
                                               :summary "when you use Invoke Duplicity, create up to 4 duplicates"})]}}}
                 #_{:name "War Domain"
                    :profs {:armor {:heavy true}
                            :weapon {:martial true}}
                    :modifiers [(opt5e/cleric-spell 1 :divine-favor 1)
                                (opt5e/cleric-spell 1 :shield-of-faith 1)
                                (opt5e/cleric-spell 2 :magic-weapon 3)
                                (opt5e/cleric-spell 2 :spiritual-weapon 3)
                                (opt5e/cleric-spell 3 :crusaders-mantle 5)
                                (opt5e/cleric-spell 3 :spirit-guardians 5)
                                (opt5e/cleric-spell 4 :freedom-of-movement 7)
                                (opt5e/cleric-spell 4 :stoneskin 7)
                                (opt5e/cleric-spell 5 :flame-strike 9)
                                (opt5e/cleric-spell 5 :hold-monster 9)
                                (mod5e/bonus-action
                                 {:name "War Priest"
                                  :level 1
                                  :page 63
                                  :frequency (units5e/long-rests
                                              (max 1 (?ability-bonuses ::char5e/wis)))
                                  :summary "make one extra weapon attack when you use the Attack action"})]
                    :levels {6 {:modifiers [(mod5e/reaction
                                             {:name "Channel Divinity: War God's Blessing"
                                              :level 6
                                              :page 63
                                              :summary "+10 to an attack roll made by a creature within 30 ft."})]}
                             8 {:modifiers [(opt5e/divine-strike nil 63)]}}
                    :traits [{:name "Channel Divinity: Guided Strike"
                              :page 63
                              :level 2
                              :summary "+10 to an attack roll"}
                             {:name "Avatar of Battle"
                              :page 63
                              :level 17
                              :summary "from non-magical weapons, resistance to slashing, bludgeoning, and piercing damage"}]}]}))

(defn druid-spell [spell-level spell-key min-level]
  (mod5e/spells-known-cfg spell-level
                          {:key spell-key
                           :ability ::char5e/wis
                           :class "Druid"
                           :class-key :druid
                           :always-prepared? true}
                          min-level
                          nil))

(defn lands-stride [level]
  {:name "Land's Stride"
   :level level
   :page 69
   :summary "moving through nonmagical difficult terrain costs no extra movement, pass through nonmagical plants without being slowed by them and without taking damage from them"})

(defn druid-option [spell-lists
                    spells-map
                    plugin-subclasses-map
                    language-map
                    weapon-map]
  (opt5e/class-option
   spell-lists
   spells-map
   plugin-subclasses-map
   language-map
   weapon-map
   {:name "Druid"
    :key :druid
    :hit-die 8
    :spellcaster true
    :spellcasting {:level-factor 1
                   :cantrips-known {1 2 4 1 10 1}
                   :known-mode :all
                   :ability ::char5e/wis
                   :prepares-spells? true}
    :multiclass-prereqs [(opt5e/ability-prereq ::char5e/wis 13)]
    :ability-increase-levels [4 8 12 16 19]
    :profs {:armor {:light false :medium false :shields false}
            :weapon {:club true :dagger true :dart true :javelin true :mace true :quarterstaff true :scimitar true :sickle true :sling true :spear true}
            :tool {:herbalism-kit true}
            :save {::char5e/int true ::char5e/wis true}
            :skill-options {:choose 2 :options {:arcana true :animal-handling true :insight true :medicine true :nature true :perception true :religion true :survival true}}}
    :armor {:leather 1}
    :equipment-choices [{:name "Equipment Pack"
                         :options {:priests-pack 1
                                   :explorers-pack 1}}
                        {:name "Druidic Focus"
                         :options {:druidic-focus 1}}]
    :equipment {:explorers-pack 1}
    :modifiers [(mod5e/language :druidic)]
    :levels {2 {:modifiers [(mod/modifier
                             ?wild-shape-cr
                             (mod5e/level-val
                              (?class-level :druid)
                              {1 "1/4"
                               4 "1/2"
                               8 "1"}))
                            (mod/modifier
                             ?wild-shape-limitation
                             (mod5e/level-val
                              (?class-level :druid)
                              {1 "no flying or swimming speed"
                               4 "no flying speed"
                               8 nil}))
                            (mod5e/action
                             {:name "Wild Shape"
                              :page 66
                              :frequency (units5e/rests 2)
                              :duration (units5e/hours (int (/ (?class-level :druid) 2)))
                              :summary (str "You can transform into a beast you have seen with CR "
                                            ?wild-shape-cr
                                            (if ?wild-shape-limitation (str " and " ?wild-shape-limitation)))})]}}
    :selections [(opt5e/new-starting-equipment-selection
                  :druid
                  {:name "Druidic Focus"
                   :options (map
                             #(opt5e/starting-equipment-option % 1)
                             equipment5e/druidic-focuses)})
                 (opt5e/new-starting-equipment-selection
                  :druid
                  {:name "Wooden Shield or Simple Weapon"
                   :options [(t/option-cfg
                              {:name "Wooden Shield"
                               :modifiers [(mod5e/armor :shield 1)]})
                             (t/option-cfg
                              {:name "Simple Weapon"
                               :selections [(opt5e/new-starting-equipment-selection
                                             :druid
                                             {:name "Simple Weapon"
                                              :options (opt5e/simple-weapon-options 1 (vals weapon-map))
                                              :min 1
                                              :max 1})]})]})
                 (opt5e/new-starting-equipment-selection
                  :druid
                  {:name "Melee Weapon"
                   :options [(t/option-cfg
                              {:name "Scimitar"
                               :modifiers [(mod5e/weapon :scimitar 1)]})
                             (t/option-cfg
                              {:name "Simple Melee Weapon"
                               :selections [(opt5e/new-starting-equipment-selection
                                             :druid
                                             {:name "Simple Melee Weapon"
                                              :options (opt5e/simple-melee-weapon-options 1 (vals weapon-map))})]})]})]
    :traits [{:name "Druidic"
              :page 66
              :summary "You can speak Druidic and use it to leave hidden message and automatically spot messages left by others"}
             {:name "Timeless Body"
              :level 18
              :page 67
              :summary "age slowly"}
             {:name "Beast Spells"
              :level 18
              :page 67
              :summary "while in Wild Shape, can perform druid spells' somatic and verbal components"}
             {:name "Archdruid"
              :level 20
              :page 67
              :summary "Wild Shape unlimited times, ignore verbal and somatic spell components, ignore material components with no cost and aren't consumed by spell"}]
    :subclass-level 2
    :subclass-title "Druid Circle"
    :subclasses [{:name "Circle of the Land"
                  :selections [(opt5e/spell-selection
                                spell-lists
                                spells-map
                                {:class-key :druid
                                 :level 0
                                 :spellcasting-ability ::char5e/wis
                                 :class-name "Druid"
                                 :num 1})
                               (t/selection-cfg
                                {:name "Land Type"
                                 :tags #{:class}
                                 :options [(t/option-cfg
                                            {:name "Arctic"
                                             :modifiers [(druid-spell 2 :hold-person 3)
                                                         (druid-spell 2 :spike-growth 3)
                                                         (druid-spell 3 :sleet-storm 5)
                                                         (druid-spell 3 :slow 5)
                                                         (druid-spell 4 :freedom-of-movement 7)
                                                         (druid-spell 4 :ice-storm 7)
                                                         (druid-spell 5 :commune-with-nature 9)
                                                         (druid-spell 5 :cone-of-cold 9)]})
                                           (t/option-cfg
                                            {:name "Coast"
                                             :modifiers [(druid-spell 2 :mirror-image 3)
                                                         (druid-spell 2 :misty-step 3)
                                                         (druid-spell 3 :water-breathing 5)
                                                         (druid-spell 3 :water-walk 5)
                                                         (druid-spell 4 :control-water 7)
                                                         (druid-spell 4 :freedom-of-movement 7)
                                                         (druid-spell 5 :conjure-elemental 9)
                                                         (druid-spell 5 :scrying 9)]})
                                           (t/option-cfg
                                            {:name "Desert"
                                             :modifiers [(druid-spell 2 :blur 3)
                                                         (druid-spell 2 :silence 3)
                                                         (druid-spell 3 :create-food-and-water 5)
                                                         (druid-spell 3 :protection-from-energy 5)
                                                         (druid-spell 4 :blight 7)
                                                         (druid-spell 4 :hallucinatory-terrain 7)
                                                         (druid-spell 5 :insect-plague 9)
                                                         (druid-spell 5 :wall-of-stone 9)]})
                                           (t/option-cfg
                                            {:name "Forest"
                                             :modifiers [(druid-spell 2 :barkskin 3)
                                                         (druid-spell 2 :spider-climb 3)
                                                         (druid-spell 3 :call-lightning 5)
                                                         (druid-spell 3 :plant-growth 5)
                                                         (druid-spell 4 :divination 7)
                                                         (druid-spell 4 :freedom-of-movement 7)
                                                         (druid-spell 5 :commune-with-nature 9)
                                                         (druid-spell 5 :tree-stride 9)]})
                                           (t/option-cfg
                                            {:name "Grassland"
                                             :modifiers [(druid-spell 2 :invisibility 3)
                                                         (druid-spell 2 :pass-without-trace 3)
                                                         (druid-spell 3 :daylight 5)
                                                         (druid-spell 3 :haste 5)
                                                         (druid-spell 4 :divination 7)
                                                         (druid-spell 4 :freedom-of-movement 7)
                                                         (druid-spell 5 :dream 9)
                                                         (druid-spell 5 :insect-plague 9)]})
                                           (t/option-cfg
                                            {:name "Mountain"
                                             :modifiers [(druid-spell 2 :spider-climb 3)
                                                         (druid-spell 2 :spike-growth 3)
                                                         (druid-spell 3 :lightning-bolt 5)
                                                         (druid-spell 3 :meld-into-stone 5)
                                                         (druid-spell 4 :stone-shape 7)
                                                         (druid-spell 4 :stoneskin 7)
                                                         (druid-spell 5 :passwall 9)
                                                         (druid-spell 5 :wall-of-stone 9)]})
                                           (t/option-cfg
                                            {:name "Swamp"
                                             :modifiers [(druid-spell 2 :darkness 3)
                                                         (druid-spell 2 :melfs-acid-arrow 3)
                                                         (druid-spell 3 :water-walk 5)
                                                         (druid-spell 3 :stinking-cloud 5)
                                                         (druid-spell 4 :freedom-of-movement 7)
                                                         (druid-spell 4 :locate-creature 7)
                                                         (druid-spell 5 :insect-plague 9)
                                                         (druid-spell 5 :scrying 9)]})
                                           (t/option-cfg
                                            {:name "Underdark"
                                             :modifiers [(druid-spell 2 :spider-climb 3)
                                                         (druid-spell 2 :web 3)
                                                         (druid-spell 3 :gaseous-form 5)
                                                         (druid-spell 3 :stinking-cloud 5)
                                                         (druid-spell 4 :greater-invisibility 7)
                                                         (druid-spell 4 :stone-shape 7)
                                                         (druid-spell 5 :cloudkill 9)
                                                         (druid-spell 5 :insect-plague 9)]})]})]
                  :modifiers []
                  :levels {2 {:modifiers [(mod5e/dependent-trait
                                           {:name "Natural Recovery"
                                            :level 2
                                            :page 68
                                            :summary (str "During short rest, recover "
                                                          (common/round-up (/ (?class-level :druid) 2))
                                                          " spell slots less than 6th level")})]}
                           6 {:modifiers [(mod5e/saving-throw-advantage ["plants magically created or manipulated to impede movement"])]}
                           10 {:modifiers [(mod5e/damage-immunity :poison)
                                           (mod5e/condition-immunity :poisoned)
                                           (mod5e/condition-immunity :charmed "by elementals or fey")
                                           (mod5e/condition-immunity :frightened "by elementals or fey")
                                           (mod5e/immunity :disease)
                                           (mod5e/trait-cfg
                                            {:name "Nature's Ward"
                                             :page 69
                                             :summary "immune to being charmed by fey or elementals; immune to poison and disease"})]}
                           14 {:modifiers [(mod5e/dependent-trait
                                            {:name "Nature's Santuary"
                                             :level 14
                                             :page 69
                                             :summary (str "beasts or plant creatures must make a DC "
                                                           (?spell-save-dc ::char5e/wis)
                                                           " Wisdom save or they cannot attack you.")})]}}
                  :traits [(lands-stride 6)]}
                 #_{:name "Circle of the Moon"
                    :levels {2 {:modifiers [(mod5e/bonus-action
                                             {:name "Combat Wild Shape"
                                              :page 69
                                              :summary "can Wild Shape as bonus action instead of action, while transformed expend a spell slot and gain 1d8 HP per slot level"})
                                            (mod/modifier
                                             ?wild-shape-cr
                                             (max 1 (int (/ (?class-level :druid) 3))))]}
                             10 {:modifiers [(mod5e/bonus-action
                                              {:name "Elemental Wild Shape"
                                               :level 10
                                               :page 69
                                               :summary "expend two Wild Shape uses to transform into an air, earth, fire, or water elemental"})]}}
                    :traits [{:name "Primal Strike"
                              :level 6
                              :page 69
                              :summary "Your beast form attacks count as magical"}
                             {:name "Thousand Forms"
                              :page 69
                              :summary "cast alter self at will"
                              :level 14}]}]}))



#_(def eldritch-knight-cfg
    {:name "Eldritch Knight"
     :spellcasting {:level-factor 3}
     :modifiers [(mod5e/bonus-action
                  {:name "Summon Bonded Weapon"
                   :page 75
                   :summary "If on the same plane of existence, instantly teleport a bonded weapon into your hand"})]
     :levels {3 {:selections [(eldritch-knight-cantrip 2)
                              (eldritch-knight-spell-selection 2 [1])
                              (eldritch-knight-any-spell-selection 1 [1])]}
              4 {:selections [(eldritch-knight-spell-selection 1 [1])]}
              7 {:selections [(eldritch-knight-spell-selection 1 [1 2])]
                 :modifiers [(mod5e/bonus-action
                              {:name "War Magic"
                               :page 75
                               :summary "make a weapon attack if you used your action to cast a cantrip"})]}
              8 {:selections [(eldritch-knight-any-spell-selection 1 [1 2])]}
              10 {:selections [(eldritch-knight-cantrip 1)
                               (eldritch-knight-spell-selection 1 [1 2])]}
              11 {:selections [(eldritch-knight-spell-selection 1 [1 2])]}
              13 {:selections [(eldritch-knight-spell-selection 1 [1 2 3])]}
              14 {:selections [(eldritch-knight-any-spell-selection 1 [1 2 3])]}
              16 {:selections [(eldritch-knight-spell-selection 1 [1 2 3])]}
              18 {:modifiers [(mod5e/bonus-action
                               {:name "Improved War Magic"
                                :page 75
                                :summary "make a weapon attack if you used your action to cast a spell"})]}
              19 {:selections [(eldritch-knight-spell-selection 1 [1 2 3 4])]}
              20 {:selections [(eldritch-knight-any-spell-selection 1 [1 2 3 4])]}}
     :traits [{:name "Weapon Bond"
               :level 3
               :page 75
               :summary "Bond with up to two weapons (see Summon Bonded Weapon)"}
              {:name "Eldritch Strike"
               :page 75
               :level 10
               :summary "a creature has disadvantage on next saving throw against a spell you cast before the end of your next turn if you hit it with a weapon attack"}
              {:name "Arcane Charge"
               :level 15
               :page 75
               :summary "teleport up to 30 ft. when you use Action Surge"}]})

#_(defn martial-maneuvers-selection [num]
    (t/selection-cfg
     {:name "Martial Maneuvers"
      :options opt5e/maneuver-options
      :ref [:class :fighter :levels :level-3 :martial-archetype :battle-master :martial-maneuvers]
      :tags #{:class}
      :min num
      :max num}))


(defn fighter-option [spells spells-map plugin-subclasses-map language-map weapon-map]
  (opt5e/class-option
   spells
   spells-map
   plugin-subclasses-map
   language-map
   weapon-map
   {:name "Fighter",
    :key :fighter
    :hit-die 10,
    :ability-increase-levels [4 6 8 12 14 16 19]
    :profs {:armor {:light false :medium false :heavy true :shields false}
            :weapon {:simple false :martial false} 
            :save {::char5e/str true ::char5e/con true}
            :skill-options {:choose 2 :options {:acrobatics true :animal-handling true :athletics true :history true :insight true :intimidation true :perception true :survival true}}}
    :multiclass-prereqs [(t/option-prereq "Requires Strength 13 or Dexterity 13"
                                          (fn [c]
                                            (let [abilities @(subscribe [::char5e/abilities nil c])]
                                              (or (>= (::char5e/str abilities) 13)
                                                  (>= (::char5e/dex abilities) 13)))))]
    :equipment-choices [{:name "Equipment Pack"
                         :options {:dungeoneers-pack 1
                                   :explorers-pack 1}}]
    :modifiers [(mod5e/bonus-action
                 {:name "Second Wind"
                  :page 72
                  :frequency units5e/rests-1
                  :summary (str "regain 1d10 "
                                (common/mod-str (?class-level :fighter))
                                " HPs")})]
    :levels {2 {:modifiers [(mod5e/action
                             {:level 2
                              :name "Action Surge"
                              :page 72
                              :frequency (units5e/rests (if (>= (?class-level :fighter) 17)
                                                          2
                                                          1))
                              :summary "take an extra action"})]}
             5 {:modifiers [(mod5e/num-attacks 2)]}
             9 {:modifiers [(mod5e/dependent-trait
                             {:level 9
                              :name "Indomitable"
                              :page 72
                              :frequency (units5e/long-rests
                                          (mod5e/level-val
                                           (?class-level :fighter)
                                           {13 2
                                            17 3
                                            :default 1}))
                              :summary "reroll a save if you fail"})]}
             11 {:modifiers [(mod5e/num-attacks 3)]}
             20 {:modifiers [(mod5e/num-attacks 4)]}}
    :subclass-level 3
    :subclass-title "Martial Archetype"
    :selections [(opt5e/fighting-style-selection :fighter)
                 (opt5e/new-starting-equipment-selection
                  :fighter
                  {:name "Armor"
                   :options [(t/option-cfg
                              {:name "Chain Mail"
                               :modifiers [(mod5e/armor :chain-mail 1)]})
                             (t/option-cfg
                              {:name "Leather Armor, Longbow, 20 Arrows"
                               :modifiers [(mod5e/armor :leather 1)
                                           (mod5e/weapon :longbow 1)
                                           (mod5e/equipment :arrow 20)]})]})
                 (opt5e/new-starting-equipment-selection
                  :fighter
                  {:name "Weapons"
                   :options [(t/option-cfg
                              {:name "Martial Weapon and Shield"
                               :selections [(opt5e/new-starting-equipment-selection
                                             :fighter
                                             {:name "Martial Weapon"
                                              :options (opt5e/martial-weapon-options 1 (vals weapon-map))})]
                               :modifiers [(mod5e/armor :shield 1)]})
                             (t/option-cfg
                              {:name "Two Martial Weapons"
                               :selections [(opt5e/new-starting-equipment-selection
                                             :fighter
                                             {:name "Martial Weapon 1"
                                              :options (opt5e/martial-weapon-options 1 (vals weapon-map))
                                              :min 1
                                              :max 1})
                                            (opt5e/new-starting-equipment-selection
                                             :fighter
                                             {:name "Martial Weapon 2"
                                              :options (opt5e/martial-weapon-options 1 (vals weapon-map))
                                              :min 1
                                              :max 1})]})]})
                 (opt5e/new-starting-equipment-selection
                  :fighter
                  {:name "Additional Weapons"
                   :options [(t/option-cfg
                              {:name "Light Crossbow and 20 Bolts"
                               :modifiers [(mod5e/weapon :crossbow-light 1)
                                           (mod5e/equipment :crossbow-bolt 20)]})
                             (t/option-cfg
                              {:name "Two Handaxes"
                               :modifiers [(mod5e/weapon :handaxe 2)]})]})]
    :subclasses [{:name "Champion"
                  :levels {3 {:modifiers [(mod5e/critical 19)]}
                           7 {:modifiers [(mod/vec-mod ?default-skill-bonus-fns
                                                       (fn [ability-kw]
                                                         (if (#{::char5e/str
                                                                ::char5e/dex
                                                                ::char5e/con}
                                                              ability-kw)
                                                           (common/round-up (/ ?prof-bonus 2))
                                                           0)))
                                          (mod/cum-sum-mod ?initiative (common/round-up (/ ?prof-bonus 2)))
                                          (mod5e/dependent-trait
                                           {:level 7
                                            :name "Remarkable Athlete"
                                            :page 72
                                            :summary (str "+"
                                                          (common/round-up (/ ?prof-bonus 2))
                                                          " to STR, DEX, or CON checks that don't already include prof bonus; running long jump increases by "
                                                          (?ability-bonuses ::char5e/str)
                                                          " ft.")})]}
                           10 {:selections [(opt5e/fighting-style-selection :fighter)]}
                           15 {:modifiers [(mod5e/critical 18)]}
                           18 {:modifiers [(mod5e/dependent-trait
                                            {:page 73
                                             :name "Survivor"
                                             :summary (str "At start of your turns, if you have at most half of your "
                                                           #_(int (/ ?max-hit-points 2))
                                                           " HPs left, regain "
                                                           (+ 5 (?ability-bonuses ::char5e/con)) " HPs")})]}}}
                 #_{:name "Battle Master"
                    :selections [(martial-maneuvers-selection 3)
                                 (opt5e/tool-selection (map :key equipment5e/artisans-tools) 1)]
                    :modifiers [(mod/modifier ?maneuver-save-dc (max (?spell-save-dc ::char5e/dex)
                                                                     (?spell-save-dc ::char5e/str)))
                                (mod5e/dependent-trait
                                 {:name "Combat Superiority"
                                  :page 73
                                  :level 3
                                  :summary (let [[num-maneuvers num-dice die]
                                                 (mod5e/level-val
                                                  (?class-level :fighter)
                                                  {7 [5 5 8]
                                                   10 [7 5 10]
                                                   15 [9 6 10]
                                                   18 [9 6 12]
                                                   :default [3 4 8]})]
                                             (str "You know "
                                                  num-maneuvers
                                                  " martial maneuvers, have "
                                                  num-dice
                                                  " superiority dice (d"
                                                  die
                                                  "s), and maneuver save DC of "
                                                  ?maneuver-save-dc))})]
                    :levels {7 {:selections [(martial-maneuvers-selection 2)]}
                             10 {:selections [(martial-maneuvers-selection 2)]}
                             15 {:selections [(martial-maneuvers-selection 2)]}}
                    :traits [{:name "Know Your Enemy"
                              :level 7
                              :page 73
                              :class-key :fighter
                              :summary "Study a creature outside combat for 1 min. to learn if it is superior, inferior, or equal in STR, DEX, CON, AC, current HP, total levels, fighter levels"}
                             {:name "Relentless"
                              :level 15
                              :page 74
                              :class-key :fighter
                              :summary "you regain 1 superiority die when you roll iniative and have no remaining superiority dice"}]}
                 #_eldritch-knight-cfg]}))

(defn monk-weapon? [{:keys [key ::weapon5e/type ::weapon5e/melee? ::weapon5e/heavy? ::weapon5e/two-handed?]}]
  (or (= key :shortsword)
      (and (= type :simple)
           melee?
           (not heavy?)
           (not two-handed?))))

(defn monk-option [spells spells-map plugin-subclasses-map language-map weapon-map]
  (opt5e/class-option
   spells
   spells-map
   plugin-subclasses-map
   language-map
   weapon-map
   (merge
    opt5e/monk-base-cfg
    {:hit-die 8
     :key :monk
     :ability-increase-levels [4 8 12 16 19]
     :unarmored-abilities [::char5e/wis]
     :profs {:weapon {:simple false :shortsword false}
             :save {::char5e/dex true ::char5e/str true}
             :tool-options {:musical-instrument 1 :artisans-tool 1}
             :skill-options {:choose 2 :options {:acrobatics true :athletics true :history true :insight true :religion true :stealth true}}}
     :multiclass-prereqs [(t/option-prereq "Requires Wisdom 13 and Dexterity 13"
                                           (fn [c]
                                             (let [abilities @(subscribe [::char5e/abilities nil c])]
                                               (and (>= (::char5e/wis abilities) 13)
                                                    (>= (::char5e/dex abilities) 13)))))]
     :equipment-choices [{:name "Equipment Pack"
                          :options {:dungeoneers-pack 1
                                    :explorers-pack 1}}]
     :weapon-choices [{:name "Weapon"
                       :options {:shortsword 1
                                 :simple 1}}]
     :modifiers [(mod/vec-mod ?weapon-ability-modifiers
                              (fn [weapon finesse?]
                                (if (monk-weapon? weapon)
                                  (get ?ability-bonuses ::char5e/dex)
                                  0)))
                 (mod/vec-mod ?unarmored-defense :monk)
                 (mod/cum-sum-mod ?unarmored-ac-bonus
                                  (?ability-bonuses ::char5e/wis)
                                  nil
                                  nil
                                  [(= :monk (first ?unarmored-defense))])
                 (mod/modifier ?martial-arts-die (mod5e/level-val
                                                  (?class-level :monk)
                                                  {5 6
                                                   11 8
                                                   17 10
                                                   :default 4}))
                 (mod5e/attack
                  {:name "Martial Arts"
                   :damage-die ?martial-arts-die
                   :damage-die-count 1
                   :damage-modifier (max (?ability-bonuses ::char5e/str) (?ability-bonuses ::char5e/dex))
                   :summary "Unarmed strike or monk weapon"})
                 (mod5e/bonus-action
                  {:name "Martial Arts"
                   :page 78
                   :summary "Make an extra unarmed strike when you take Attack action"})]
     :levels {2 {:modifiers [(mod5e/unarmored-speed-bonus 10)
                             (mod5e/dependent-trait
                              {:name "Ki"
                               :page 78
                               :level 2
                               :summary (str "You have " (?class-level :monk) " ki points")})
                             (mod5e/bonus-action
                              {:name "Flurry of Blows"
                               :page 78
                               :level 2
                               :summary "After you take Attack action, spend 1 ki to make 2 unarmed strikes"})
                             (mod5e/bonus-action
                              {:name "Patient Defense"
                               :page 78
                               :summary "Spend 1 ki point to take the Dodge action"})
                             (mod5e/bonus-action
                              {:name "Step of the Wind"
                               :page 78
                               :summary "Spend 1 ki point to take the Disengage or Dash action and jump distance is doubled for the turn"})]}
              3 {:modifiers [(mod5e/reaction
                              {:name "Deflect Missiles"
                               :page 78
                               :summary (str "When hit by a ranged attack, reduce the damage by 1d10 " (common/mod-str (+ (?ability-bonuses ::char5e/dex) (?class-level :monk))) ". If you reduce it to 0, you can catch the missile (if you have a free hand and it's small enough to hold) and use it in a ranged attack with proficiency, as a monk weapon, for 1 ki point with range 20/60")})]}
              4 {:modifiers [(mod5e/reaction
                              {:name "Slow Fall"
                               :page 78
                               :level 4
                               :summary (str "reduce falling damage by " (* 5  (?class-level :monk)))})]}
              5 {:modifiers [(mod5e/num-attacks 2)
                             (mod5e/dependent-trait
                              {:name "Stunning Strike"
                               :page 79
                               :level 5
                               :summary (str "when you hit a creature with melee attack, spend 1 ki point to stun the creature if it fails a DC " (?spell-save-dc ::char5e/wis) " CON save")})]}
              6 {:modifiers [(mod5e/unarmored-speed-bonus 5)]}
              7 {:modifiers [(mod5e/action
                              {:name "Stillness of Mind"
                               :page 79
                               :summary "end one effect causing you to be charmed or frightened"})]}
              10 {:modifiers [(mod5e/damage-immunity :poison)
                              (mod5e/immunity :disease)
                              (mod5e/unarmored-speed-bonus 5)]}
              13 {:modifiers (map
                              (fn [{:keys [name key]}]
                                (mod5e/language key))
                              (vals language-map))}
              14 {:modifiers [(apply mod5e/saving-throws nil char5e/ability-keys)
                              (mod5e/unarmored-speed-bonus 5)]}
              18 {:modifiers [(mod5e/unarmored-speed-bonus 5)
                              (mod5e/action
                               {:name "Empty Body: Invisibility"
                                :level 18
                                :page 79
                                :duration units5e/minutes-1
                                :summary "spend 4 ki points to become invisible and have resistance to all damage but force damage"})
                              (mod5e/action
                               {:name "Empty Body: Astral Projection"
                                :page 79
                                :level 18
                                :summary "use 8 ki points to cast the astral projection spell"})]}}
     :weapons {:dart 10}
     :traits [{:name "Ki-Empowered Strikes"
               :page 79
               :level 6
               :summary "your unarmed strikes count as magical"}
              (opt5e/evasion 7 79)
              {:name "Tongue of the Sun and Moon"
               :page 79
               :level 13
               :summary "you understand all languages and can communicate with any creature that can understand a language"}
              {:name "Diamond Soul"
               :level 14
               :page 79
               :summary "you are proficient in all saves. You can spend 1 ki point to reroll failed saves."}
              {:name "Timeless Body"
               :page 79
               :level 15
               :summary "you can't be aged magically and you need no food or water"}
              
              {:name "Perfect Self"
               :page 79
               :level 20
               :summary "regain 4 ki when you have none and roll initiative"}]
     :subclasses [{:name "Way of the Open Hand"
                   :modifiers [(mod5e/dependent-trait
                                {:name "Open Hand Technique"
                                 :page 79
                                 :summary (str "when you hit with Flurry of Blows, you impose one of the effects on the target: 1) must make a DC "(?spell-save-dc ::char5e/wis) " DEX save or be knocked prone. 2) make a DC " (?spell-save-dc ::char5e/wis) " STR save or be pushed 15 ft. 3) can't take reactions until end of your next turn")})]
                   :levels {6 {:modifiers [(mod5e/action
                                            {:name "Wholeness of Body"
                                             :page 79
                                             :level 6
                                             :frequency units5e/long-rests-1
                                             :summary (str "heal yourself " (* 3 (?class-level :monk)) " HPs")})]}
                            11 {:modifiers [(mod5e/dependent-trait
                                             {:name "Tranquility"
                                              :page 80
                                              :level 11
                                              :summary (str "gain effects of sanctuary spell (save DC " (?spell-save-dc ::char5e/wis) ") between rests")})]}
                            17 {:modifiers [(mod5e/dependent-trait
                                             {:name "Quivering Palm"
                                              :level 17
                                              :page 80
                                              :summary (str "when you hit a creature with unarmed strike, set up vibrations that last " (?class-level :monk) " days. Use an action to end the vibrations, reducing the target to 0 HPs on failed DC " (?spell-save-dc ::char5e/wis) " CON save. It takes 10d10 necrotic damage on successful save.")})]}}}
                  #_{:name "Way of Shadow"
                     :modifiers [(mod5e/spells-known 0 :minor-illusion ::char5e/wis "Monk (Way of Shadow)")
                                 (mod5e/action
                                  {:name "Shadow Arts"
                                   :page 80
                                   :summary "spend 2 ki to cast 'darkness', 'darkvision', 'pass without trace', or 'silence' spell"})]
                     :levels {6 {:modifiers [(mod5e/bonus-action
                                              {:name "Shadow Step"
                                               :page 80
                                               :summary "teleport 60 ft. and gain advantage on first melee attack before end of turn"})]}
                              11 {:modifiers [(mod5e/action
                                               {:name "Cloak of Shadows"
                                                :level 11
                                                :page 80
                                                :summary "become invisible"})]}
                              17 {:modifiers [(mod5e/reaction
                                               {:name "Opportunist"
                                                :page 80
                                                :level 17
                                                :summary "when a creature within 5 ft. is hit by attack from someone else, make a melee attack"})]}}}
                  #_{:name "Way of the Four Elements"
                     :modifiers [(mod5e/dependent-trait
                                  {:name "Disciple of the Elements"
                                   :page 80
                                   :summary (str "You learn elemental disciplines with spell save DC " (?spell-save-dc ::char5e/wis) "."
                                                 (if (>= (?class-level :monk) 5)
                                                   (str " You can increase the level of elemental discipline spells you cast by 1 for each additional ki point you spend, up to " (mod5e/level-val (?class-level :monk)
                                                                                                                                                                                                   {9 4 13 5 17 6 :default 3}))))})]
                     :levels {3 {:selections [(opt5e/monk-elemental-disciplines)]}
                              6 {:selections [(opt5e/monk-elemental-disciplines)]}
                              11 {:selections [(opt5e/monk-elemental-disciplines)]}
                              17 {:selections [(opt5e/monk-elemental-disciplines)]}}
                     :traits [{:name "Elemental Attunement"
                               :page 81
                               :summary "create minor elemental effect"}]}]})))


(defn paladin-option [spells spells-map plugin-subclasses-map language-map weapon-map]
  (opt5e/class-option
   spells
   spells-map
   plugin-subclasses-map
   language-map
   weapon-map
   (merge
    opt5e/paladin-base-cfg
    {:name "Paladin"
     :key :paladin
     :spellcaster true
     :spellcasting {:level-factor 2
                    :known-mode :all
                    :ability ::char5e/cha
                    :prepares-spells? true}
     :hit-die 10
     :ability-increase-levels [4 8 12 16 19]
     :profs {:armor {:light false :medium false :heavy true :shields false}
             :weapon {:simple false :martial false}
             :save {::char5e/wis true ::char5e/cha true}
             :skill-options {:choose 2 :options {:athletics true :insight true :intimidation true :medicine true :persuasion true :religion true}}}
     :multiclass-prereqs [(t/option-prereq "Requires Strength 13 and Charisma 13"
                                           (fn [c]
                                             (let [abilities @(subscribe [::char5e/abilities nil c])]
                                               (and (>= (::char5e/str abilities) 13)
                                                    (>= (::char5e/cha abilities) 13)))))]
     :equipment-choices [{:name "Equipment Pack"
                          :options {:priests-pack 1
                                    :explorers-pack 1}}]
     :armor {:chain-mail 1}
     :levels {2 {:selections [(opt5e/fighting-style-selection :paladin #{:defense :dueling :great-weapon-fighting :protection})]}
              3 {:modifiers [(mod5e/immunity :disease)
                             (mod5e/trait-cfg
                              {:name "Divine Health"
                               :page 85
                               :summary "immune to disease"})]}
              5 {:modifiers [(mod5e/num-attacks 2)]}
              6 {:modifiers (conj
                             (map
                              #(mod/modifier ?saving-throw-bonuses
                                             (merge-with +
                                                         ?saving-throw-bonuses
                                                         {% (get ?ability-bonuses ::char5e/cha 0)}))
                              char5e/ability-keys)
                             (mod5e/dependent-trait
                              {:name "Aura of Protection"
                               :page 85
                               :summary (str "you and friendly creatures within " ?paladin-aura " ft. have a " (common/bonus-str (max 1 (?ability-bonuses ::char5e/cha))) " bonus to saves")}))}
              10 {:modifiers [(mod5e/dependent-trait
                               {:name "Aura of Courage"
                                :page 85
                                :summary (str (str "you and friendly creatures within " ?paladin-aura " ft. can't be frightened"))})]}
              14 {:modifiers [(mod5e/action
                               {:name "Cleansing Touch"
                                :page 85
                                :frequency (units5e/long-rests (?ability-bonuses ::char5e/cha))
                                :summary "end a spell on yourself or willing creature"})]}}
     :modifiers [(mod/modifier ?paladin-aura (if (< (?class-level :paladin) 18) 10 30))
                 (mod5e/action
                  {:name "Divine Sense"
                   :page 84
                   :frequency (units5e/long-rests
                               (inc (?ability-bonuses ::char5e/cha)))
                   :summary "within 60 ft., detect presense of undead, celestial, or fiend. Also detect consecrated or desecrated object or place"})
                 (mod5e/action
                  {:name "Lay on Hands"
                   :page 84
                   :frequency (units5e/long-rests
                                (* 5 (?class-level :paladin)))
                   :summary (str "you have a healing pool of " (* 5 (?class-level :paladin)) " HPs, with it you can heal a creature or expend 5 points to cure disease or neutralize poison")})
                 (mod5e/dependent-trait
                  {:name "Channel Divinity"
                   :page 85
                   :level 3
                   :frequency units5e/rests-1
                   :summary "your oath provides specific options"})]
     :selections [(opt5e/new-starting-equipment-selection
                   :paladin
                   {:name "Weapons"
                    :options [(t/option-cfg
                               {:name "Martial Weapon and Shield"
                                :selections [(opt5e/new-starting-equipment-selection
                                              :paladin
                                              {:name "Martial Weapon"
                                               :options (opt5e/martial-weapon-options 1 (vals weapon-map))})]
                                :modifiers [(mod5e/armor :shield 1)]})
                              (t/option-cfg
                               {:name "Two Martial Weapons"
                                :selections [(opt5e/new-starting-equipment-selection
                                              :paladin
                                              {:name "Martial Weapon 1"
                                               :options (opt5e/martial-weapon-options 1 (vals weapon-map))
                                               :min 1
                                               :max 1})
                                             (opt5e/new-starting-equipment-selection
                                              :paladin
                                              {:name "Martial Weapon 2"
                                               :options (opt5e/martial-weapon-options 1 (vals weapon-map))
                                               :min 1
                                               :max 1})]})]})
                  (opt5e/new-starting-equipment-selection
                   :paladin
                   {:name "Melee Weapon"
                    :options [(t/option-cfg
                               {:name "Five Javelins"
                                :modifiers [(mod5e/weapon :javelin 5)]})
                              (t/option-cfg
                               {:name "Simple Melee Weapon"
                                :selections [(opt5e/new-starting-equipment-selection
                                              :paladin
                                              {:name "Simple Melee Weapon"
                                               :options (opt5e/simple-melee-weapon-options 1 (vals weapon-map))})]})]})]
     :traits [{:name "Divine Smite"
               :level 2
               :page 85
               :summary "when you hit with melee weapon attack, you can expend 1 X-th level spell slot to deal extra (X+1)d8 radiant damage, up to 5d8. Additional d8 on fiend or undead."}
              {:name "Improved Divine Smite"
               :level 11
               :page 85
               :summary "whenever you hit with melee weapon, you deal an extra d8 radiant damage"}]
     :subclass-level 3
     :subclass-title "Sacred Oath"
     :subclasses [{:name "Oath of Devotion"
                   :modifiers [(opt5e/paladin-spell 1 :protection-from-evil-and-good)
                               (opt5e/paladin-spell 1 :sanctuary)
                               (opt5e/paladin-spell 2 :lesser-restoration)
                               (opt5e/paladin-spell 2 :zone-of-truth)
                               (opt5e/paladin-spell 3 :beacon-of-hope)
                               (opt5e/paladin-spell 3 :dispel-magic)
                               (opt5e/paladin-spell 4 :freedom-of-movement)
                               (opt5e/paladin-spell 4 :guardian-of-faith)
                               (opt5e/paladin-spell 5 :commune)
                               (opt5e/paladin-spell 5 :flame-strike)
                               (mod5e/action
                                {:name "Channel Divinity: Sacred Weapon"
                                 :page 86
                                 :duration units5e/minutes-1
                                 :summary (str "make a weapon magical, with a " (common/bonus-str (max 1 (?ability-bonuses ::char5e/cha))) " attack bonus and magical light (20 ft./20 ft.)")})
                               (mod5e/action
                                {:name "Channel Divinity: Turn the Unholy"
                                 :page 86
                                 :duration units5e/minutes-1
                                 :summary (str "each undead or fiend within 30 ft. must make a DC " (?spell-save-dc ::char5e/cha) " WIS save or be turned for 1 min.")})]
                   :levels {7 {:modifiers [(mod5e/dependent-trait
                                            {:name "Aura of Devotion"
                                             :page 86
                                             :summary (str "you and friendly creatures within " ?paladin-aura " ft. can't be charmed")})]}
                            20 {:modifiers [(mod5e/action
                                             {:name "Holy Nimbus"
                                              :page 86
                                              :frequency units5e/long-rests-1
                                              :duration units5e/minutes-1
                                              :summary "you emanate a bright light with 30 ft radius, an enemy that starts its turn there takes 10 radiant damage. You also have advantage on saves against spells cast by fiends and undead"})]}}
                   :traits [{:name "Purity of Spirit"
                             :level 15
                             :page 86
                             :summary "always under effects of protection from evil and good spell"}]}
                  #_{:name "Oath of the Ancients"
                     :modifiers [(opt5e/paladin-spell 1 :ensnaring-strike 3)
                                 (opt5e/paladin-spell 1 :speak-with-animals 3)
                                 (opt5e/paladin-spell 2 :misty-step 5)
                                 (opt5e/paladin-spell 2 :moonbeam 5)
                                 (opt5e/paladin-spell 3 :plant-growth 9)
                                 (opt5e/paladin-spell 3 :protection-from-energy 9)
                                 (opt5e/paladin-spell 4 :ice-storm 13)
                                 (opt5e/paladin-spell 4 :stoneskin 13)
                                 (opt5e/paladin-spell 5 :commune-with-nature 17)
                                 (opt5e/paladin-spell 5 :tree-stride 17)
                                 (mod5e/action
                                  {:name "Channel Divinity: Nature's Wrath"
                                   :level 3
                                   :page 87
                                   :summary (str "restrain a creature with vines on a failed DC " (?spell-save-dc ::char5e/cha) " STR or DEX save. It makes the save every turn until freed.")})
                                 (mod5e/action
                                  {:name "Channel Divinity: Turn the Faithless"
                                   :level 3
                                   :page 87
                                   :duration units5e/minutes-1
                                   :summary "turn and reveal the true form of fey and fiends within 30 ft."})]
                     :levels {7 {:modifiers [(mod5e/dependent-trait
                                              {:name "Aura of Warding"
                                               :level 7
                                               :page 87
                                               :summary (str "you and friendly creatures within " ?paladin-aura " have resistance to spell damage")})]}}
                     :traits [{:name "Undying Sentinal"
                               :level 15
                               :page 87
                               :frequency units5e/long-rests-1
                               :summary "when you are reduced to 0 HP without being killed, you drop to 1 instead"}
                              {:name "Elder Champion"
                               :level 20
                               :page 87
                               :frequency units5e/long-rests-1
                               :duration units5e/minutes-1
                               :summary "undergo a tranformation where you 1) regain 10 HPs at start of your turns 2) can cast spells with casting time action as bonus action 3) enemies within 10 ft. have disadvantage on saves against your Channel Divinity and spells"}]}
                  #_{:name "Oath of Vengeance"
                     :modifiers [(opt5e/paladin-spell 1 :bane 3)
                                 (opt5e/paladin-spell 1 :hunters-mark 3)
                                 (opt5e/paladin-spell 2 :hold-person 5)
                                 (opt5e/paladin-spell 2 :misty-step 5)
                                 (opt5e/paladin-spell 3 :haste 9)
                                 (opt5e/paladin-spell 3 :protection-from-energy 9)
                                 (opt5e/paladin-spell 4 :banishment 13)
                                 (opt5e/paladin-spell 4 :dimension-door 13)
                                 (opt5e/paladin-spell 5 :hold-monster 17)
                                 (opt5e/paladin-spell 5 :scrying 17)
                                 (mod5e/action
                                  {:name "Channel Divinity: Abjure Enemy"
                                   :level 3
                                   :page 88
                                   :duration units5e/minutes-1
                                   :summary (str "a creature of your choosing within 60 ft. must succeed on a DC " (?spell-save-dc ::char5e/cha) " WIS save or be frightened and have a speed of 0, speed is halved on successful save")})
                                 (mod5e/bonus-action
                                  {:name "Channel Divinity: Vow of Eternity"
                                   :level 3
                                   :page 88
                                   :duration units5e/minutes-1
                                   :summary "gain advantage on attacks against a creature"})]
                     :levels {15 {:modifiers [(mod5e/reaction
                                               {:name "Soul of Vengeance"
                                                :level 15
                                                :page 88
                                                :summary "when a creature under you Vow of Enmity attacks, make a melee weapon attack against it"})]}
                              20 {:modifiers [(mod5e/action
                                               {:name "Avenging Angel"
                                                :level 20
                                                :page 88
                                                :duration units5e/hours-1
                                                :frequency units5e/long-rests-1
                                                :summary (str "transform, gain flying speed of 60 ft., emanate a 30 ft. aura and creatures within it must succeed on a DC " (?spell-save-dc ::char5e/cha) " WIS or be frightened for 1 min and attacks against them have advantage")})]}}
                     :traits [{:name "Relentless Avenger"
                               :level 7
                               :page 88
                               :summary "when you hit with opportunity attack, you can also move up to half your speed after the attack without provoking opportunity attacks"}]}]})))

(defn favored-enemy-option [language-map [enemy-type info]]
  (let [vec-info? (sequential? info)
        languages (if vec-info? info (:languages info))
        name (if vec-info? (common/kw-to-name enemy-type) (:name info))]
    (let [language-options (zipmap languages (repeat true))]
      (t/option-cfg
       {:name name
        :selections (if (> (count languages) 1)
                      [(opt5e/language-selection
                        language-map
                        {:choose 1
                         :options language-options})])
        :modifiers (remove
                    nil?
                    [(if (= 1 (count languages))
                       (mod5e/language (first languages)))
                     (mod/set-mod ?ranger-favored-enemies enemy-type)])}))))

(defn favored-enemy-selection [language-map order]
  (t/selection-cfg
   {:name (str "Favored Enemy " order)
    :tags #{:class}
    :order 3
    :options [(t/option-cfg
               {:name "Type"
                :selections [(t/selection-cfg
                              {:name "Favored Enemy Type"
                               :tags #{:class}
                               :order 4
                               :multiselect? true
                               :ref [:class :ranger :favored-enemy-type]
                               :options (map
                                         (partial favored-enemy-option language-map)
                                         (opt5e/favored-enemy-types language-map))})]})
              (t/option-cfg
               {:name "Two Humanoid Races"
                :selections [(t/selection-cfg
                              {:name "Favored Enemy Humanoid Race"
                               :tags #{:class}
                               :order 4
                               :min 2
                               :max 2
                               :multiselect? true
                               :ref [:class :ranger :favored-enemy-race]
                               :options (map
                                         (partial favored-enemy-option language-map)
                                         opt5e/humanoid-enemies)})]})]}))

(defn favored-terrain-selection [order]
  (t/selection-cfg
   {:name "Favored Terrain"
    :tags #{:class}
    :order 5
    :ref [:class :ranger :favored-terrain]
    :multiselect? true
    :options (map
              (fn [terrain]
                (t/option-cfg
                 {:name (common/kw-to-name terrain)
                  :modifiers [(mod/set-mod ?ranger-favored-terrain terrain)]}))
              [:arctic :coast :desert :forest :grassland :mountain :swamp :underdark])}))


(def third-caster-spells-known-schedule
  {3 3
   4 1
   7 1
   8 1
   10 1
   11 1
   13 1
   14 1
   16 1
   19 1
   20 1})

(def half-caster-spells-known-schedule
  {2 2
   3 1
   5 1
   7 1
   9 1
   11 1
   13 1
   15 1
   17 1
   19 1})

(def full-caster-spells-known-schedule
  {1 2
   2 1
   3 1
   4 1
   5 1
   6 1
   7 1
   8 1
   9 1
   10 1
   11 1
   13 1
   15 1
   17 1})

(defn ranger-option [spells spells-map plugin-subclasses-map language-map weapon-map]
  (opt5e/class-option
   spells
   spells-map
   plugin-subclasses-map
   language-map
   weapon-map
   (merge
    opt5e/ranger-base-cfg
    {:hit-die 10
     :key :ranger
     :profs {:armor {:light false :medium false :shields false}
             :weapon {:simple false :martial false}
             :save {::char5e/str true ::char5e/dex true}
             :skill-options {:choose 3 :options opt5e/ranger-skills}
             :multiclass-skill-options {:choose 1 :options opt5e/ranger-skills}}
     :multiclass-prereqs [(t/option-prereq "Requires Wisdom 13 and Dexterity 13"
                                           (fn [c]
                                             (let [abilities @(subscribe [::char5e/abilities nil c])]
                                               (and (>= (::char5e/wis abilities) 13)
                                                    (>= (::char5e/dex abilities) 13)))))]
     :ability-increase-levels [4 8 12 16 19]
     :spellcaster true
     :spellcasting {:level-factor 2
                    :known-mode :schedule
                    :spells-known half-caster-spells-known-schedule
                    :ability ::char5e/wis}
     :armor-choices [{:name "Armor"
                      :options {:scale-mail 1
                                :leather 1}}]
     :equipment-choices [{:name "Equipment Pack"
                          :options {:dungeoneers-pack 1
                                    :explorers-pack 1}}]
     :weapons {:longbow 1}
     :equipment {:quiver 1
                 :arrow 20}
     :modifiers [(mod5e/dependent-trait
                  {:name "Favored Enemy"
                   :page 91
                   :summary (str "You have advantage on survival checks to track " (common/list-print (map #(common/kw-to-name % false) ?ranger-favored-enemies)) " creatures and on INT checks to recall info about them")})
                 (mod5e/dependent-trait
                  {:name "Natural Explorer"
                   :page 91
                   :summary (let [favored-terrain ?ranger-favored-terrain
                                  one-terrain? (= 1 (count favored-terrain))]
                              (str "your favored terrain " (if one-terrain? "type is" "types are") " " (if (seq favored-terrain) (common/list-print (map #(common/kw-to-name % false) ?ranger-favored-terrain)) "not selected") ". Related to the terrain type" (if (not one-terrain?) "s") ": 2X proficiency bonus for INT and WIS checks for which you are proficient, difficult terrain doesn't slow your group, always alert for danger, can move stealthily alone at normal pace, 2x food when foraging, while tracking learn exact number, size, and when they passed through"))})]
     :selections [(opt5e/new-starting-equipment-selection
                   :ranger
                   {:name "Melee Weapon"
                    :options [(t/option-cfg
                               {:name "Two Shortswords"
                                :modifiers [(mod5e/weapon :shortsword 2)]})
                              (t/option-cfg
                               {:name "Simple Melee Weapon"
                                :selections [(opt5e/new-starting-equipment-selection
                                              :ranger
                                              {:name "Simple Melee Weapon"
                                               :options (opt5e/simple-melee-weapon-options 1 (vals weapon-map))
                                               :min 2
                                               :max 2})]})]})
                  (favored-enemy-selection language-map 1)
                  (favored-terrain-selection 1)]
     :levels {2 {:selections [(opt5e/fighting-style-selection :ranger #{:archery :defense :dueling :two-weapon-fighting})]}
              3 {:modifiers [(mod5e/action
                              {:name "Primeval Awareness"
                               :level 3
                               :page 92
                               :summary (str "spend an X-level spell slot, for X minutes, you sense the types of creatures within 1 mile" (if (seq ?ranger-favored-terrain) (str "(6 if " (common/list-print (map #(common/kw-to-name % false) ?ranger-favored-terrain))) ")") )})]}
              5 {:modifiers [(mod5e/num-attacks 2)]}
              6 {:selections [(favored-enemy-selection language-map 2)
                              (favored-terrain-selection 2)]}
              10 {:selections [(favored-terrain-selection 3)]}
              14 {:selections [(favored-enemy-selection language-map 3)]}
              20 {:modifiers [(mod5e/dependent-trait
                               {:name "Foe Slayer"
                                :frequency units5e/turns-1
                                :level 20
                                :page 92
                                :summary (str "add " (common/bonus-str (?ability-bonuses ::char5e/wis)) " to an attack or damage roll") })]}}
     :traits [(lands-stride 8)
              {:name "Hide in Plain Sight"
               :level 10
               :page 92
               :summary "spend 1 minute camouflaging yourself to gain +10 to Stealth checks when you don't move"}
              {:name "Vanish"
               :level 14
               :page 92
               :summary "Hide action as a bonus action. You also can't be non-magically tracked"}
              {:name "Feral Senses"
               :level 18
               :page 92
               :summary "no disadvantage on attacks against creature you can't see, you know location of invisible creatures within 30 ft."}]
     :subclasses [{:name "Hunter"
                   :levels {3 {:selections [(t/selection-cfg
                                             {:name "Hunter's Prey"
                                              :tags #{:class}
                                              :options [(t/option-cfg
                                                         {:name "Colossus Slayer"
                                                          :modifiers [(mod5e/trait-cfg
                                                                       {:name "Colossus Slayer"
                                                                        :page 93
                                                                        :frequency units5e/turns-1
                                                                        :summary "deal an extra d8 damage when you hit a creature that is below its HP max with a weapon attack"})]})
                                                        (t/option-cfg
                                                         {:name "Giant Killer"
                                                          :modifiers [(mod5e/reaction
                                                                       {:name "Giant Killer"
                                                                        :page 93
                                                                        :frequency units5e/turns-1
                                                                        :summary "attack a Large or larger creature within 5 ft that misses an attack against you"})]})
                                                        (t/option-cfg
                                                         {:name "Horde Breaker"
                                                          :modifiers [(mod5e/trait-cfg
                                                                       {:name "Horde Breaker"
                                                                        :page 93
                                                                        :frequency units5e/turns-1
                                                                        :summary "when you attack one creature, attack another creature within 5 feet of it with the same action"})]})]})]}
                            7 {:selections [(t/selection-cfg
                                             {:name "Defensive Tactics"
                                              :tags #{:class}
                                              :options [(t/option-cfg
                                                         {:name "Escape the Horde"
                                                          :modifiers [(mod5e/trait-cfg
                                                                       {:name "Escape the Horde"
                                                                        :frequency units5e/turns-1
                                                                        :page 93})]})
                                                        (t/option-cfg
                                                         {:name "Multiattack Defense"
                                                          :modifiers [(mod5e/trait-cfg
                                                                       {:name "Multiattack Defense"
                                                                        :frequency units5e/turns-1
                                                                        :page 93})]})
                                                        (t/option-cfg
                                                         {:name "Steel Will"
                                                          :modifiers [(mod5e/saving-throw-advantage [:frightened])]})]})]}
                            11 {:selections [(t/selection-cfg
                                              {:name "Multiattack"
                                               :tags #{:class}
                                               :options [(t/option-cfg
                                                          {:name "Volley"
                                                           :modifiers [(mod5e/action
                                                                        {:name "Volley"
                                                                         :page 93
                                                                         :summary "make a ranged attack against any creatures within a 10 ft of a point" 
                                                                         })]})
                                                         (t/option-cfg
                                                          {:name "Whirlwind Attack"
                                                           :modifiers [(mod5e/action
                                                                        {:name "Whirlwind Attack"
                                                                         :page 93
                                                                         :summary "melee attack against any creatures within 5 ft. of you"})]})]})]}
                            15 {:selections [(t/selection-cfg
                                              {:name "Superior Hunter's Defense"
                                               :tags #{:class}
                                               :options [(t/option-cfg
                                                          {:name "Evasion"
                                                           :modifiers [(mod5e/trait-cfg
                                                                        (opt5e/evasion 15 93))]})
                                                         (t/option-cfg
                                                          {:name "Stand Against the Tide"
                                                           :modifiers  [(mod5e/reaction
                                                                         {:name "Stand Against the Tide"
                                                                          :page 93
                                                                          :summary "force a creature to repeat its attack on another creature when it misses you"
                                                                          })]})
                                                         (t/option-cfg
                                                          {:name "Uncanny Dodge"
                                                           :modifiers [(opt5e/uncanny-dodge-modifier 93)]})]})]}}}
                  #_{:name "Beast Master"
                     :selections [(t/selection-cfg
                                   {:name "Ranger's Companion"
                                    :tags #{:class}
                                    :options (map
                                              (fn [monster-name]
                                                (t/option-cfg
                                                 {:name monster-name
                                                  :modifiers [(mod5e/action
                                                               {:name "Ranger's Companion"
                                                                :page 93
                                                                :summary (str "You have a " monster-name " as your companion, you can command it to Attack, Dash, Disengage, Dodge, or Help")})]}))
                                              ["Stirge" "Baboon" "Bat" "Badger" "Blood Hawk" "Boar" "Cat" "Crab" "Deer" "Eagle" "Flying Snake" "Frog" "Giant Badger" "Giant Centipede" "Giant Crab" "Giant Fire Beetle" "Giant Frog" "Giant Poisonous Snake" "Giant Rat" "Giant Wolf Spider" "Goat" "Hawk" "Hyena" "Jackal" "Lizard" "Mastiff" "Mule" "Octopus" "Panther" "Owl" "Poisonous Snake" "Pony" "Quipper" "Rat" "Raven" "Scorpion" "Sea Horse" "Spider" "Vulture" "Weasel" "Wolf"])})]
                     :levels {7 {:modifiers [(mod5e/bonus-action
                                              {:name "Exceptional Training"
                                               :page 93
                                               :level 7
                                               :summary "when your companion doesn't attack, you can command it to take the Dash, Disengage, Dodge, or Help action"})]}}
                     :traits [{:name "Bestial Fury"
                               :level 11
                               :page 93
                               :summary "your companion attacks twice when it takes the Attack action"}
                              {:name "Share Spells"
                               :level 15
                               :page 93
                               :summary "when you target yourself with a spell you can also affect your companion if within 30 ft."}]}]})))

(def rogue-skills {:acrobatics true :athletics true :deception true :insight true :intimidation true :investigation true :perception true :performance true :persuasion true :sleight-of-hand true :stealth true})

(defn rogue-option [spells spells-map plugin-subclasses-map language-map weapon-map]
  (opt5e/class-option
   spells
   spells-map
   plugin-subclasses-map
   language-map
   weapon-map
   {:name "Rogue",
    :key :rogue
    :hit-die 8
    :ability-increase-levels [4 8 10 12 16 19]
    :expertise true
    :profs {:armor {:light false}
            :weapon {:simple true :crossbow-hand true :longsword true :rapier true :shortsword true}
            :save {::char5e/dex true ::char5e/int true}
            :tool {:thieves-tools false}
            :skill-options {:order 0 :choose 4 :options rogue-skills}
            :multiclass-skill-options {:order 0 :choose 1 :options rogue-skills}}
    :multiclass-prereqs [(opt5e/ability-prereq ::char5e/dex 13)]
    :weapon-choices [{:name "Melee Weapon"
                      :options {:rapier 1
                                :shortsword 1}}]
    :armor {:leather 1}
    :weapons {:dagger 2}
    :equipment {:thieves-tools 1}
    :equipment-choices [{:name "Equipment Pack"
                         :options {:burglars-pack 1
                                   :dungeoneers-pack 1
                                   :explorers-pack 1}}]
    :modifiers [(mod5e/dependent-trait
                 {:name "Sneak Attack"
                  :page 96
                  :frequency units5e/turns-1
                  :summary (str (common/round-up (/ (?class-level :rogue) 2)) "d6 extra damage on attack where you have advantage or another enemy of creature is within 5 ft.")
                  })]
    :levels {2 {:modifiers [(mod5e/bonus-action
                             {:level 2
                              :name "Cunning Action"
                              :page 96
                              :frequency units5e/turns-1
                              :summary "Dash, Disengage or Hide"
                              })]}
             5 {:modifiers [(opt5e/uncanny-dodge-modifier 96)]}
             6 {:selections [(assoc
                              opt5e/rogue-expertise-selection
                              ::t/order
                              1)]}
             15 {:modifiers [(mod5e/saving-throws nil ::char5e/wis)]}}
    :selections [(opt5e/new-starting-equipment-selection
                  :rogue
                  {:name "Additional Weapon"
                   :options [(t/option-cfg
                              {:name "Shortbow, Quiver, 20 Arrows"
                               :modifiers [(mod5e/weapon :shortbow 5)
                                           (mod5e/equipment :quiver 1)
                                           (mod5e/equipment :arrow 20)]})
                             (t/option-cfg
                              {:name "Shortsword"
                               :modifiers [(mod5e/weapon :shortsword 1)]})]})
                 (assoc
                  opt5e/rogue-expertise-selection
                  ::t/order
                  1)]
    :traits [{:name "Thieves' Cant"
              :page 96
              :summary "convey secret messages hidden in normal conversation"
              }
             (opt5e/evasion 7 96)
             {:level 11
              :name "Reliable Talent"
              :page 96
              :summary "when you make an ability check with proficiency, treat a roll less than 10 as a 10"
              }
             {:level 14
              :name "Blindsense"
              :page 96
              :summary "know location of hidden or invisible creatures within 10 ft."
              }
             {:level 18
              :name "Elusive"
              :page 96
              :summary "attack rolls only have disadvantage on you if you are incapacitated"
              }
             {:level 20
              :name "Stroke of Luck"
              :page 97
              :frequency units5e/rests-1
              :summary "turn missed attack into a hit or a failed ability check roll as 20"
              }]
    :subclass-level 3
    :subclass-title "Roguish Archetype"
    :subclasses [{:name "Thief"
                  :modifiers [(mod5e/bonus-action
                               {:level 3
                                :name "Fast Hands"
                                :page 96
                                :summary "use your Cunning Action to make Sleight of Hand checks, use thieves' tools, or take Use and Object action"
                                })
                              (mod5e/dependent-trait
                               {:level 3
                                :name "Second-Story Work"
                                :page 97
                                :summary (str "climbing costs no extra movement, your running jump distance increases by " (?ability-bonuses ::char5e/dex) " ft.")
                                })]
                  :traits [{:level 9
                            :name "Supreme Sneak"
                            :page 97
                            :summary "advantage on Stealth checks if you move no more than half your speed"}
                           {:level 13
                            :name "Use Magic Device"
                            :page 97
                            :summary "ignore race, class, level requirements to use magic items"}
                           {:level 17
                            :name "Thief's Reflexes"
                            :page 97
                            :summary "when not surprised, take 2 turns in first round of combat, one at your normal initiative and the next at your initiative minus 10"}]}
                 #_{:name "Assassin"
                    :profs {:tool {:disguise-kit true :poisoners-kit true}}
                    :levels {17 {:modifiers [(mod5e/dependent-trait
                                              {:name "Death Strike"
                                               :level 17
                                               :page 97
                                               :summary (str "double damage against a surpised creature if it fails a DC " (?spell-save-dc ::char5e/dex) " CON save")})]}}
                    :traits [{:name "Assassinate"
                              :level 3
                              :page 97
                              :summary "advantage on attack against creatures that haven't taken a turn yet. Hits against surprised creatures are critical"}
                             {:name "Infiltration Expertise"
                              :level 9
                              :page 97
                              :summary "spend 25 gp and 7 days to establish a false identity"}
                             {:name "Impostor"
                              :level 13
                              :page 97
                              :summary "accurately mimic the behavior, speech, and writing of another person"}]}
                 #_{:name "Arcane Trickster"
                    :spellcasting {:level-factor 3}
                    :modifiers [(mod5e/spells-known 0 :mage-hand ::char5e/int "Arcane Trickster")]
                    :levels {3 {:selections [(arcane-trickster-cantrip 2)
                                             (arcane-trickster-spell-selection 2 [1])
                                             (arcane-trickster-any-spell-selection 1 [1])]}
                             4 {:selections [(arcane-trickster-spell-selection 1 [1])]}
                             7 {:selections [(arcane-trickster-spell-selection 1 [1 2])]}
                             8 {:selections [(arcane-trickster-any-spell-selection 1 [1 2])]}
                             10 {:selections [(arcane-trickster-cantrip 1)
                                              (arcane-trickster-spell-selection 1 [1 2])]}
                             11 {:selections [(arcane-trickster-spell-selection 1 [1 2])]}
                             13 {:selections [(arcane-trickster-spell-selection 1 [1 2 3])]
                                 :modifiers [(mod5e/bonus-action
                                              {:name "Versatile Trickster"
                                               :level 13
                                               :page 98
                                               :summary "use mage hand to gain advantage on attack rolls against a creature within 5 ft. of the hand"})]}
                             14 {:selections [(arcane-trickster-any-spell-selection 1 [1 2 3])]}
                             16 {:selections [(arcane-trickster-spell-selection 1 [1 2 3])]}
                             17 {:modifiers [(mod5e/reaction
                                              {:name "Spell Thief"
                                               :level 17
                                               :page 98
                                               :summary (str "steal a spell for 8 hours if it is cast on you and the spellcaster fails a DC " (?spell-save-dc ::char5e/int) " save with its spellcasting ability")})]}
                             19 {:selections [(arcane-trickster-spell-selection 1 [1 2 3 4])]}
                             20 {:selections [(arcane-trickster-any-spell-selection 1 [1 2 3 4])]}}
                    :traits [{:name "Mage Hand Legerdemain"
                              :level 3
                              :page 98
                              :summary "when you cast mage hand, you can make it invisible and perform Sleight of Hand tasks"}
                             {:name "Magical Ambush"
                              :level 9
                              :page 98
                              :summary "creatures have disadvantage on saves against your spells (only on the turn you cast them) if you are hidden from them"}]}]}))

(defn metamagic-selection [num]
  (t/selection-cfg
   {:name "Metamagic"
    :tags #{:class}
    :min num
    :max num
    :ref [:class :sorcerer :metamagic]
    :options [(t/option-cfg
               {:name "Careful Spell"
                :modifiers [(mod5e/dependent-trait
                             {:name "Careful Spell"
                              :page 102
                              :class-key :sorcerer
                              :summary (str "When you cast a spell that requires a save, spend 1 sorcery pt. to allow up to " (?ability-bonuses ::char5e/cha) " creatures to automatically succeed")})]})
              (t/option-cfg
               {:name "Distant Spell"
                :modifiers [(mod5e/trait-cfg
                             {:name "Distant Spell"
                              :page 102
                              :summary "spend 1 sorcery pt. double the range of a spell with range 5 ft. or greater or make the range of a touch spell 30 ft."})]})
              (t/option-cfg
               {:name "Empowered Spell"
                :modifiers [(mod5e/dependent-trait
                             {:name "Empowered Spell"
                              :page 102
                              :summary (str "spend 1 sorcery pt. to reroll up to " (?ability-bonuses ::char5e/cha) " spell damage dice")})]})
              (t/option-cfg
               {:name "Extended Spell"
                :modifiers [(mod5e/trait-cfg
                             {:name "Extended Spell"
                              :page 102
                              :summary "spend 1 sorcery pt. to double the duration of a spell to a max 24 hrs."})]})
              (t/option-cfg
               {:name "Heightened Spell"
                :modifiers [(mod5e/trait-cfg
                             {:name "Heightened Spell"
                              :page 102
                              :summary "when you cast a spell with a save to resist it's effects, spend 3 sorcery pts. to give one target disadvantage on its first save against it"})]})
              (t/option-cfg
               {:name "Quickened Spell"
                :modifiers [(mod5e/trait-cfg
                             {:name "Quickened Spell"
                              :page 102
                              :summary "spend 2 sorcery pts. to convert a casting of a spell with 1 action casting time to 1 bonus-action"})]})
              (t/option-cfg
               {:name "Subtle Spell"
                :modifiers [(mod5e/trait-cfg
                             {:name "Subtle Spell"
                              :page 102
                              :summary "spend 1 sorcery pt. to cast a spell without somatic or verbal components"})]})
              (t/option-cfg
               {:name "Twinned Spell"
                :modifiers [(mod5e/trait-cfg
                             {:name "Twinned Spell"
                              :page 102
                              :summary "spend X sorcery pts. (min 1) to target two creatures with a single target spell, where X is the spell level"})]})]}))

(defn sorcerer-option [spells spells-map plugin-subclasses-map language-map weapon-map]
  (opt5e/class-option
   spells
   spells-map
   plugin-subclasses-map
   language-map
   weapon-map
   {:name "Sorcerer"
    :key :sorcerer
    :spellcasting {:level-factor 1
                   :cantrips-known {1 4 4 1 10 1}
                   :known-mode :schedule
                   :spells-known full-caster-spells-known-schedule
                   :ability ::char5e/cha}
    :multiclass-prereqs [(opt5e/ability-prereq ::char5e/cha 13)]
    :spellcaster true
    :hit-die 6
    :ability-increase-levels [4 8 12 16 19]
    :profs {:weapon {:dagger true :dart true :sling true :quarterstaff true :crossbow-light true}
            :save {::char5e/con true ::char5e/cha true}
            :skill-options {:choose 2 :options {:arcana true :deception true :insight true :intimidation true :persuasion true :religion true}}}
    :selections [(opt5e/new-starting-equipment-selection
                  :sorcerer
                  {:name "Weapon"
                   :options [(t/option-cfg
                              {:name "Light Crossbow"
                               :modifiers [(mod5e/weapon :crossbow-light 1)
                                           (mod5e/equipment :crossbow-bolt 20)]})
                             (t/option-cfg
                              {:name "Simple Weapon"
                               :selections [(opt5e/new-starting-equipment-selection
                                             :sorcerer
                                             {:name "Simple Weapon"
                                              :options (opt5e/simple-weapon-options 1 (vals weapon-map))
                                              :min 1
                                              :max 1})]})]})]
    :levels {2 {:modifiers [(mod5e/dependent-trait
                             {:name "Sorcery Points"
                              :level 2
                              :page 101
                              :frequency (units5e/long-rests
                                           (?class-level :sorcerer))
                              :summary (str "You have " (?class-level :sorcerer) " sorcery points")})
                            (mod5e/bonus-action
                             {:name "Flexible Casting"
                              :level 2
                              :page 101
                              :summary "you can convert sorcery points into spell slots (level - point cost: 1st - 2, 2nd - 3, 3rd - 5, 4th - 6, 5th - 7). You can also convert spell slots into sorcery points equal to the slot's level"})]}
             3 {:selections [(metamagic-selection 2)]}
             10 {:selections [(metamagic-selection 1)]}
             17 {:selections [(metamagic-selection 1)]}}
    :equipment-choices [{:name "Equipment Pack"
                         :options {:dungeoneers-pack 1
                                   :explorers-pack 1}}
                        {:name "Spellcasting Equipment"
                         :options {:component-pouch 1
                                   :arcane-focus 1}}]
    :weapons {:dagger 2}
    :subclass-title "Sorcerous Origin"
    :subclass-level 1
    :subclasses [{:name "Draconic Bloodline"
                  :modifiers [(mod/map-mod ?class-hit-point-level-bonus
                                           :sorcerer
                                           1)
                              (mod/modifier ?natural-ac-bonus 3)
                              (mod5e/language :draconic)]
                  :selections [(t/selection-cfg
                                {:name "Draconic Ancestry Type"
                                 :tags #{:class}
                                 :options (map
                                           (fn [{:keys [name] :as ancestry}]
                                             (t/option-cfg
                                              {:name name
                                               :modifiers [(mod/modifier ?sorcerer-draconic-ancestry ancestry)]}))
                                           opt5e/draconic-ancestries)})]
                  :traits [{:name "Draconic Resilience"
                            :page 102
                            :summary "+1 HP/level, unarmored AC 13 + DEX modifier"}]
                  :levels {6 {:modifiers [(mod5e/dependent-trait
                                           {:name "Elemental Affinity"
                                            :page 102
                                            :summary (str "Add CHA mod to one damage roll of a spell that deals "
                                                          (if ?sorcerer-draconic-ancestry
                                                            (str (common/safe-name
                                                                  (get-in
                                                                   ?sorcerer-draconic-ancestry
                                                                   [:breath-weapon :damage-type]))
                                                                 " damage")
                                                            "damage of type associated with your draconic ancestry")
                                                          ", you may also spend 1 sorcery pt. to gain resistance to that damage type for an hr.")})]}
                           14 {:modifiers [(mod5e/bonus-action
                                            {:name "Dragon Wings"
                                             :page 103
                                             :summary "Sprout wings and gain flying speed equal to land speed"})]}
                           18 {:modifiers [(mod5e/action
                                            {:name "Draconic Presence"
                                             :page 103
                                             :summary (str "Spend 5 sorcery pts. and create an aura that causes hostile creatures that start their turn within it to be charmed or afraid if they fail a DC " (?spell-save-dc ::char5e/cha) " Wisdom save.")})]}}}
                 #_{:name "Wild Magic"
                    :levels {6 {:modifiers [(mod5e/reaction
                                             {:name "Bend Luck"
                                              :level 6
                                              :page 103
                                              :summary "spend 2 sorcery pts. to add or subtract 1d4 from a creature's d20 roll"})]}}
                    :traits [{:name "Wild Magic Surge"
                              :level 1
                              :summary "Do a d20 check when casting sorcerer spells, on a 1 roll on the Wild Magic Surge table"
                              :page 103}
                             {:name "Tides of Chaos"
                              :level 1
                              :summary "Gain advantage on a roll"
                              :page 103
                              :frequency units5e/long-rests-1}
                             {:name "Controlled Chaos"
                              :level 14
                              :page 103
                              :summary "When rolling on Wild Magic Surge table, roll twice and use either roll"}
                             {:name "Spell Bombardment"
                              :level 18
                              :page 103
                              :summary "When you roll a die for spell damage, roll max rolls an additional time"
                              :frequency units5e/turns-1}]}]}))

(defn spell-school-savant [school page]
  {:level 2
   :name (str (s/capitalize school) " Savant")
   :page page
   :description (str "time and money to copy an " school " spell is halved")})

(defn spell-in-spells-known? [known level spell-key]
  (and known (some #(= spell-key (:key %)) (known level))))

(defn spell-mastery-selection [level]
  (t/selection-cfg
   {:name (str "Spell Mastery Level " level " Spell")
    :tags #{:spells}
    :options (map
              (fn [spell-kw]
                (let [{:keys [name] :as spell} (spells5e/spell-map spell-kw)]
                  (t/option-cfg
                   {:name name
                    :help (opt5e/spell-help spell)
                    :modifiers [(mod/set-mod ?spell-mastery name)]
                    :prereqs [(t/option-prereq
                               nil
                               (fn [c]
                                 (let [spells-known @(subscribe [::char5e/spells-known nil c])]
                                   (get-in spells-known [level ["Wizard" spell-kw]])))
                               true)]})))
              (get-in sl5e/spell-lists [:wizard level]))}))

(defn signature-spells-selection []
  (t/selection-cfg
   {:name "Signature Spells"
    :tags #{:spells}
    :min 2
    :max 2
    :options (map
              (fn [spell-kw]
                (let [{:keys [name] :as spell} (spells5e/spell-map spell-kw)]
                  (t/option-cfg
                   {:name name
                    :help (opt5e/spell-help spell)
                    :modifiers [(mod/set-mod ?signature-spells name)]
                    :prereqs [(t/option-prereq
                               nil
                               (fn [c]
                                 (let [spells-known @(subscribe [::char5e/spells-known nil c])]
                                   (get-in spells-known [3 ["Wizard" spell-kw]])))
                               true)]})))
              (get-in sl5e/spell-lists [:wizard 3]))}))

(defn wizard-option [spells spells-map plugin-subclasses-map language-map weapon-map] 
  (opt5e/class-option
   spells
   spells-map
   plugin-subclasses-map
   language-map
   weapon-map
   {:name "Wizard",
    :key :wizard
    :spellcasting {:level-factor 1
                   :cantrips-known {1 3 4 1 10 1}
                   :known-mode :acquire
                   :spells-known (zipmap (range 1 21) (cons 6 (repeat 2)))
                   :ability ::char5e/int
                   :prepares-spells? true}
    :spellcaster true
    :multiclass-prereqs [(opt5e/ability-prereq ::char5e/int 13)]
    :hit-die 6
    :ability-increase-levels [4 8 12 16 19]
    :equipment-choices [{:name "Equipment Pack"
                         :options {:scholars-pack 1
                                   :explorers-pack 1}}
                        {:name "Spellcasting Equipment"
                         :options {:component-pouch 1
                                   :arcane-focus 1}}]
    :weapon-choices [{:name "Melee Weapon"
                      :options {:quarterstaff 1
                                :dagger 1}}]
    :equipment {:spellbook 1}
    :profs {:weapon {:dagger true :dart true :sling true :quarterstaff true :crossbow-light true}
            :save {::char5e/int true ::char5e/wis true}
            :skill-options {:choose 2 :options {:arcana true :history true :insight true :investigation true :medicine true :religion true}}}
    :modifiers [(mod5e/dependent-trait
                 {:name "Arcane Recovery"
                  :page 115
                  :frequency units5e/days-1
                  :summary (str "When you finish a short rest, regain spell slots totalling no more than " (common/round-up (/ ?wizard-level 2)) ", and each must be 5th level or lower.")})]
    :levels {18 {:selections [(spell-mastery-selection 1)
                              (spell-mastery-selection 2)]
                 :modifiers [(mod5e/dependent-trait
                              {:name "Spell Mastery"
                               :page 115
                               :summary (str (if (seq ?spell-mastery)
                                               (str "Cast " (common/list-print ?spell-mastery))
                                               "Choose a 1st and 2nd level spell, cast those")
                                             " at lowest level without expending a slot if you have them prepared")})]}
             20 {:selections [(signature-spells-selection)]
                 :modifiers [(mod5e/dependent-trait
                              {:name "Signature Spells"
                               :page 115
                               :summary (str (if (seq ?signature-spells)
                                               (str "Your signature spells are " (common/list-print ?signature-spells))
                                               "Choose two 3rd level spells")
                                             ", you always have them prepared and can cast them once without expending a slot")})]}}
    :subclass-level 2
    :subclass-title "Arcane Tradition"
    :subclasses [{:name "School of Evocation"
                  :levels {10 {:modifiers [(mod5e/dependent-trait
                                            {:level 10
                                             :name "Empowered Evocation"
                                             :page 117
                                             :summary (str "add your INT mod (" (?ability-bonuses ::char5e/int) ") to one damage roll of evocation spell you cast")})]}}
                  :traits [(spell-school-savant "evocation" 117)
                           {:level 2
                            :name "Sculpt Spells"
                            :page 117
                            :summary "When you cast an evocation spell that affects other creatures that you can see, you can choose a number of them equal to 1 + the spell’s level. The chosen creatures automatically succeed on their saving throws against the spell, and they take no damage if they would normally take half damage on a successful save."}
                           {:level 6
                            :name "Potent Cantrip"
                            :page 117
                            :summary "creature take half damage on sucessful saves against your cantrips"}
                           {:level 14
                            :name "Overchannel"
                            :page 118
                            :summary "deal max damage with evocation spells 1st-5th level. You take necrotic damage if you use this feature more than once per long rest"}]}
                 #_{:name "School of Abjuration"
                    :modifiers [(mod5e/dependent-trait
                                 {:name "Arcane Ward"
                                  :page 115
                                  :summary (str "magical ward with HP max " (+ (?class-level :wizard) (?ability-bonuses ::char5e/int)) ", casting X-th level abjuration spells restores 2X HPs to it")})]
                    :levels {6 {:modifiers [(mod5e/reaction
                                             {:name "Projected Ward"
                                              :page 115
                                              :range units5e/ft-30
                                              :summary "shield a creature using your ward"})]}
                             10 {:modifiers [(mod5e/dependent-trait
                                              {:name "Improved Abjuration"
                                               :page 115
                                               :summary (str "add your proficiency bonus (" (common/bonus-str ?prof-bonus) ") to ability checks required by abjuration spells")})]}
                             14 {:modifiers [(mod5e/saving-throw-advantage [:spells])]}}
                    :traits [(spell-school-savant "abjuration" 115)
                             {:name "Spell Resistance"
                              :level 14
                              :page 116
                              :summary "advantage on saves against spells, resistance to damage from spells"}]}
                 #_{:name "School of Conjuration"
                    :levels {6 {:modifiers [(mod5e/action
                                             {:name "Benign Transposition"
                                              :page 116
                                              :range units5e/ft-30
                                              :summary "Teleport to unoccupied space or swap spaces with willing Small or Medium creature"})]}}
                    :traits [(spell-school-savant "conjuration" 116)
                             {:name "Minor Conjuration"
                              :level 2
                              :page 116
                              :duration units5e/hours-1
                              :summary "conjure an inanimate object 3 ft per side or less and 15 lbs or less, it radiates dim light to 5 ft."}
                             {:name "Focused Conjuration"
                              :level 10
                              :page 116
                              :summary "concentration on conjuration spells cannot be broken by taking damage"}
                             {:name "Durable Summons"
                              :level 14
                              :summary "creatures you conjure have 30 temp hit points"}]}
                 #_{:name "School of Divination"
                    :levels {10 {:modifiers [(mod5e/action
                                              {:name "The Third Eye"
                                               :level 10
                                               :page 117
                                               :frequency units5e/long-rests-1
                                               :summary "gain one: 1) darkvision 60 ft., 2) see etherial plane 60 ft. 3) read any language 4) see invisible within 10 ft."})]}}
                    :traits [(spell-school-savant "divination" 116)
                             {:name "Portent"
                              :level 2
                              :frequency units5e/long-rests-1
                              :summary "roll 2 d20s after long rest, can replace rolls you or a creature you can see make with these"}
                             {:name "Expert Divination"
                              :level 6
                              :page 117
                              :summary "when you cast divination spell 2nd level or higher, regain a spell slot of lower level (max 5th level)"}
                             {:name "Greater Portent"
                              :level 14
                              :page 117
                              :summary "roll 3 d20s for your Portent feature"}]}
                 #_{:name "School of Enchantment"
                    :levels {2 {:modifiers [(mod5e/action
                                             {:name "Hypnotic Gaze"
                                              :level 2
                                              :page 117
                                              :range units5e/ft-5
                                              :summary (str "charm a creature until end of your next turn unless it succeeds on a DC " (?spell-save-dc ::char5e/int) " WIS save, it is incapacitated and dazed")})]}
                             6 {:modifiers [(mod5e/reaction
                                             {:name "Instinctive Charm"
                                              :page 117
                                              :range units5e/ft-30
                                              :frequency units5e/long-rests-1
                                              :summary (str "redirect a creature's attack against you to the creature closest to it, not including you, if it fails a DC " (?spell-save-dc ::char5e/int) " WIS save")})]}
                             14 {:modifiers [(mod5e/dependent-trait
                                              {:name "Alter Memories"
                                               :level 14
                                               :page 117
                                               :summary (str "make a creature unaware of your charm on it, can also use your action to erase up to " (inc (?ability-bonuses ::char5e/cha)) " hours from it's memory if it fails a DC " (?spell-save-dc ::char5e/int) " INT check")})]}}
                    :traits [(spell-school-savant "enchantment" 117)
                             {:name "Split Enchantment"
                              :level 10
                              :page 117
                              :summary "target 2 creatures with an enchantment spell that normally targets 1"}]}
                 #_{:name "School of Illusion"
                    :modifiers [(mod5e/spells-known-cfg 0
                                                        {:key :minor-illusion
                                                         :ability ::char5e/int
                                                         :class "Wizard"
                                                         :illusionist-cantrip? true}
                                                        0
                                                        [(not (spell-in-spells-known? ?spells-known 0 :minor-illusion))])]
                    :selections [(t/selection-cfg
                                  {:name "Illusionist Cantrip"
                                   :order 0
                                   :tags (opt5e/spell-tags :wizard 0)
                                   :options (opt5e/spell-options (get-in sl/spell-lists [:wizard 0]) ::char5e/int "Wizard")
                                   :prereq-fn (fn [c]
                                                (let [spells-known @(subscribe [::char5e/spells-known nil c])
                                                      passes? (or (nil? spells-known)
                                                                  (some
                                                                   (fn [s]
                                                                     (and (= :minor-illusion (:key s))
                                                                          (not (:illusionist-cantrip? s))))
                                                                   (vals (spells-known 0))))]
                                                  passes?))})]
                    :levels {6 {:modifiers [(mod5e/action
                                             {:name "Malleable Illusions"
                                              :page 118
                                              :summary "change the nature of an illusion you cast"})]}
                             10 {:modifiers [(mod5e/reaction
                                              {:name "Illusory Self"
                                               :page 118
                                               :frequency units5e/rests-1
                                               :summary "attacker hits an illusion of you instead of you"})]}
                             14 {:modifiers [(mod5e/bonus-action
                                              {:name "Illusory Reality"
                                               :page 119
                                               :summary "make an illusory object real for 1 minute"})]}}
                    :traits [(spell-school-savant "illusion" 118)
                             {:name "Improved Minor Illusion"
                              :page 118
                              :summary "can create a sound and an image with the same casting of minor illusion"}]}
                 #_{:name "School of Necromancy"
                    :levels {10 {:modifiers [(mod5e/damage-resistance :necrotic)]}
                             14 {:modifiers [(mod5e/action
                                              {:name "Command Undead"
                                               :page 119
                                               :summary (str "bring undead under your control unless it succeeds on a DC " (?spell-save-dc ::char5e/int) " CHA save")})]}}
                    :traits [(spell-school-savant "necromancy" 118)
                             {:name "Grim Harvest"
                              :level 2
                              :page 118
                              :frequency units5e/turns-1
                              :summary "when you kill a creature with a spell, you regain HPs equal to 2X the spell level or 3X the spell level for necromancy spells"}
                             {:name "Undead Thralls"
                              :page 119
                              :level 6
                              :summary (str "target one additional corpse or pile of bones for animate dead; whenever you create an undead it's HP max is increased by your wizard level amount and it adds your prof bonus to weapon damage rolls")}
                             {:name "Inured to Undeath"
                              :level 10
                              :summary "resistant to necrotic damage; your HP max cannot be reduced"}]}
                 #_{:name "School of Transmutation"
                    :modifiers [(mod5e/spells-known 4 :polymorph ::char5e/int "Wizard")]
                    :levels {14 {:modifiers [(mod5e/action
                                              {:name "Master Transmuter"
                                               :page 119
                                               :summary "expend your transmuter's stone for 1 effect: 1) convert a non-magical object into another non-magical object, 2) remove curses, poisons, diseases and damage from a creature 3) raise dead 4) reduce a willing creature's apparent age by 3d10"})]}}
                    :traits [(spell-school-savant "transmutation" 119)
                             {:name "Minor Alchemy"
                              :level 2
                              :page 119
                              :duration units5e/hours-1
                              :summary "transform an object of one substance to another substance"}
                             {:name "Transmuter's Stone"
                              :level 6
                              :page 119
                              :summary "create a stone with 1 benefit: 1) darkvision 60 ft. 2) +10 speed 3) prof in CON saves 4) resistance to acid, fire, cold, lightning, or thunder damage"}
                             {:name "Shapechanger"
                              :level 10
                              :page 119
                              :frequency units5e/long-rests-1
                              :summary "cast polymorph without a spell slot to turn into a CR 1 or less beast"}]}]}))

(def melee-weapons-xform
  (comp
   (filter
    ::weapon5e/melee?)
   (map
    (fn [weapon]
      (t/option-cfg
       {:name (or (:name weapon) (::weapon5e/name weapon))})))))

(defn pact-weapon-option [title weapons]
  (t/option-cfg
   {:name title
    :selections [(t/selection-cfg
                  {:name "Pact Weapon"
                   :tags #{:class}
                   :options (sequence
                             melee-weapons-xform
                             weapons)})]}))

(defn pact-boon-options [spell-lists spells-map]
  [(t/option-cfg
    {:name "Pact of the Chain"
     :modifiers [(mod5e/spells-known 1 :find-familiar ::char5e/cha "Warlock")
                 (mod5e/trait-cfg
                  {:name opt5e/pact-of-the-chain-name
                   :page 107
                   :summary "Can cast find familiar as a ritual, use your attack action to give your familiar an attack as a reaction"})]})
   (t/option-cfg
    {:name "Pact of the Blade"
     :modifiers [(mod5e/trait-cfg
                  {:name opt5e/pact-of-the-blade-name
                   :page 107
                   :summary "summon a magical weapon"})]})
   (t/option-cfg
    {:name "Pact of the Tome"
     :selections [(t/selection-cfg
                   {:name "Book of Shadows Cantrips"
                    :tags #{:spells}
                    :min 3
                    :max 3
                    :options (opt5e/spell-options spells-map
                                                  (into
                                                   #{}
                                                   (mapcat
                                                    (fn [[cls-kw spells-by-level]]
                                                      (spells-by-level 0))
                                                    spell-lists))
                                                  ::char5e/cha
                                                  "Warlock"
                                                  false
                                                  "uses Book of Shadows")})]
     :modifiers [(mod5e/trait-cfg
                  {:name opt5e/pact-of-the-tome-name
                   :page 108
                   :summary "you have a spellbook with 3 extra cantrips"})]})])


(defn eldritch-invocation-options [plugin-invocations spell-lists spells-map]
  (concat
   (map
    (fn [{:keys [name description]}]
      (t/option-cfg
       {:name name
        :modifiers [(mod5e/trait-cfg
                     {:name (str "Eldritch Invocation: " name)
                      :description description})]}))
    plugin-invocations)
   [(t/option-cfg
     {:name "Agonizing Blast"
      :modifiers [(mod5e/dependent-trait
                   {:name "Eldritch Invocation: Agonizing Blast"
                    :page 110
                    :summary (str "add " (?ability-bonuses ::char5e/cha) " to eldritch blast spell damage")})]
      :prereqs [opt5e/has-eldritch-blast-prereq]})
    (t/option-cfg
     {:name "Armor of Shadows"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Armor of Shadows"
                    :page 110
                    :summary "cast mage armor on yourself at will"})
                  (mod5e/spells-known 1 :mage-armor ::char5e/cha "Warlock" 0 "at will")]})
    (t/option-cfg
     {:name "Ascendant Step"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Ascendant Step"
                    :page 110
                    :summary "cast levitate on yourself at will"})
                  (mod5e/spells-known 2 :levitate ::char5e/cha "Warlock" 0 "at will")]
      :prereqs [(opt5e/total-levels-option-prereq 9 :warlock)]})
    (t/option-cfg
     {:name "Beast Speech"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Beast Speech"
                    :page 110
                    :summary "can cast speak with animals at will"})
                  (mod5e/spells-known 1 :speak-with-animals ::char5e/cha "Warlock" 0 "at will")]})
    (t/option-cfg
     {:name "Beguiling Influence"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Beguiling Influence"
                    :page 110
                    :summary "proficiency in deception and persuasion"})
                  (mod5e/skill-proficiency :deception)
                  (mod5e/skill-proficiency :persuasion)]})
    (t/option-cfg
     {:name "Bewitching Whispers"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Bewitching Whispers"
                    :page 110
                    :frequency units5e/long-rests-1
                    :summary "cast compulsion once using warlock spell slot"})
                  (mod5e/spells-known 4 :compulsion ::char5e/cha "Warlock" 0 "once per long rest")]})
    (t/option-cfg
     {:name "Book of Ancient Secrets"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Book of Ancient Secrets"
                    :page 110
                    :summary "inscribe and cast rituals"})]
      :selections [(t/selection-cfg
                    {:name "Book of Ancient Secrets Rituals"
                     :tags #{:spells}
                     :multiselect? true
                     :options (opt5e/spell-options
                               spells-map
                               (map
                                (fn [s] (or (:key s)
                                            (common/name-to-kw (:name s))))
                                (filter
                                 (fn [s] (and (= 1 (:level s)) (opt5e/ritual-spell? s)))
                                 spells5e/spells))
                               ::char5e/cha
                               "Warlock"
                               false
                               "Book of Ancient Secrets Ritual")
                     :min 2
                     :max 2})]
      :prereqs [opt5e/pact-of-the-tome-prereq]})
    (t/option-cfg
     {:name "Chains of Carceri"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Chains of Carceri"
                    :page 110
                    :frequency units5e/long-rests-1
                    :summary "cast hold monster at will on celestials, fiends, or elementals"})
                  (mod5e/spells-known 5 :hold-monster ::char5e/cha "Warlock" 0 "at will")]
      :prereqs [opt5e/pact-of-the-chain-prereq
                (opt5e/total-levels-option-prereq 15 :warlock)]})
    (t/option-cfg
     {:name "Devil's Sight"
      :modifiers [(mod5e/darkvision 120 1)
                  (mod5e/trait-cfg
                   {:name "Eldritch Invocation: Devil's Sight"
                    :page 110
                    :range units5e/ft-120
                    :summary "see normally in magical and nonmagical darkness"})]})
    (t/option-cfg
     {:name "Dreadful Word"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Dreadful Word"
                    :page 110
                    :summary "use warlock spell slot to cast confusion"
                    :frequency units5e/long-rests-1})
                  (mod5e/spells-known 4 :confusion ::char5e/cha "Warlock" 0 "once per long rest")]
      :prereqs [(opt5e/total-levels-option-prereq 7 :warlock)]})
    (t/option-cfg
     {:name "Eldritch Sight"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Eldritch Sight"
                    :page 110
                    :summary "cast detect magic at will"})
                  (mod5e/spells-known 1 :detect-magic ::char5e/cha "Warlock" 0 "at will")]})
    (t/option-cfg
     {:name "Eldritch Spear"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Eldritch Spear"
                    :page 111
                    :summary "eldrich blast with range 300 ft."})]
      :prereqs [opt5e/has-eldritch-blast-prereq]})
    (t/option-cfg
     {:name "Eyes of the Rune Keeper"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Eyes of the Rune Keeper"
                    :page 111
                    :summary "read any writing."})]})
    (t/option-cfg
     {:name "Fiendish Vigor"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Fiendish Vigor"
                    :page 111
                    :summary "cast false life at will"})
                  (mod5e/spells-known 1 :false-life ::char5e/cha "Warlock" 0 "at will")]})
    (t/option-cfg
     {:name "Gaze of Two Minds"
      :modifiers [(mod5e/trait "Eldritch Invocation: Gaze of Two Minds"
                               "You can use your action to touch a willing humanoid and perceive through its senses until the end of your next turn. As long as the creature is on the same plane of existence as you, you can use your action on subsequent turns to maintain this connection, extending the duration until the end of your next turn. While perceiving through the other creature’s senses, you benefit from any special senses possessed by that creature, and you are blinded and deafened to your own surroundings.")]})
    (t/option-cfg
     {:name "Lifedrinker"
      :modifiers [(mod5e/dependent-trait
                   {:name "Eldritch Invocation: Lifedrinker"
                    :page 111
                    :summary (str "extra " (max 1 (?ability-bonuses ::char5e/cha)) " necrotic damage with your pact weapon")})]
      :prereqs [(opt5e/total-levels-option-prereq 12 :warlock)
                opt5e/pact-of-the-blade-prereq]})
    (t/option-cfg
     {:name "Mask of Many Faces"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Mask of Many Faces"
                    :page 111
                    :summary "cast disguise self at will"})
                  (mod5e/spells-known 1 :disguise-self ::char5e/cha "Warlock" 0 "at will")]})
    (t/option-cfg
     {:name "Master of Myriad Forms"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Master of Myriad Forms"
                    :page 111
                    :summary "cast alter self at will"})
                  (mod5e/spells-known 2 :alter-self ::char5e/cha "Warlock" 0 "at will")]
      :prereqs [(opt5e/total-levels-option-prereq 15 :warlock)]})
    (t/option-cfg
     {:name "Minions of Chaos"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Minions of Chaos"
                    :page 111
                    :frequency units5e/long-rests-1
                    :summary "cast conjure elemental using warlock spell slot
long rest."})
                  (mod5e/spells-known 5 :conjure-elemental ::char5e/cha "Warlock" 0 "once per rest")]
      :prereqs [(opt5e/total-levels-option-prereq 9 :warlock)]})
    (t/option-cfg
     {:name "Mire the Mind"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Mire the Mind"
                    :page 111
                    :frequency units5e/long-rests-1
                    :summary "cast slow using warlock spell slot"})
                  (mod5e/spells-known 3 :slow ::char5e/cha "Warlock" 0 "at will")]
      :prereqs [(opt5e/total-levels-option-prereq 5 :warlock)]})
    (t/option-cfg
     {:name "Misty Visions"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Misty Visions"
                    :page 111
                    :summary "cast silent image at will"})
                  (mod5e/spells-known 1 :silent-image ::char5e/cha "Warlock" 0 "at will")]})
    (t/option-cfg
     {:name "One with Shadows"
      :modifiers [(mod5e/action
                   {:name "Eldritch Invocation: One with Shadows"
                    :page 111
                    :summary "in dim light or darkness, become invisible"})]
      :prereqs [(opt5e/total-levels-option-prereq 5 :warlock)]})
    (t/option-cfg
     {:name "Otherworldly Leap"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Otherworldly Leap"
                    :page 111
                    :summary "cast jump on yourself at will"})
                  (mod5e/spells-known 1 :jump ::char5e/cha "Warlock" 0 "at will")]
      :prereqs [(opt5e/total-levels-option-prereq 9 :warlock)]})
    (t/option-cfg
     {:name "Repelling Blast"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Repelling Blast"
                    :page 111
                    :summary "push the creature 10 ft when you cast eldritch blast"})]
      :prereqs [opt5e/has-eldritch-blast-prereq]})
    (t/option-cfg
     {:name "Sculptor of Flesh"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Sculptor of Flesh"
                    :page 111
                    :frequency units5e/long-rests-1
                    :summary "cast polymorph using a warlock spell slot"})
                  (mod5e/spells-known 4 :polymorph ::char5e/cha "Warlock" 0 "once per long rest")]
      :prereqs [(opt5e/total-levels-option-prereq 7 :warlock)]})
    (t/option-cfg
     {:name "Sign of Ill Omen"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Sign of Ill Omen"
                    :page 111
                    :frequency units5e/long-rests-1
                    :summary "cast bestow curse using warlock spell slot"})
                  (mod5e/spells-known 3 :bestow-curse ::char5e/cha "Warlock" 0 "once per long rest")]
      :prereqs [(opt5e/total-levels-option-prereq 5 :warlock)]})
    (t/option-cfg
     {:name "Thief of Five Fates"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Thief of Five Fates"
                    :page 111
                    :frequency units5e/long-rests-1
                    :summary "cast bane warlock spell slot"})
                  (mod5e/spells-known 1 :bane ::char5e/cha "Warlock" 0 "once per long rest")]})
    (t/option-cfg
     {:name "Thirsting Blade"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Thirsting Blade"
                    :page 111
                    :summary "when using Attack action, attack with pact blade twice"})]
      :prereqs [(opt5e/total-levels-option-prereq 5 :warlock)
                opt5e/pact-of-the-blade-prereq]})
    (t/option-cfg
     {:name "Visions of Distant Realms"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Visions of Distant Realms"
                    :page 111
                    :summary "cast arcane eye at will"})
                  (mod5e/spells-known 4 :arcane-eye ::char5e/cha "Warlock" 0 "at will")]
      :prereqs [(opt5e/total-levels-option-prereq 15 :warlock)]})
    (t/option-cfg
     {:name "Voice of the Chain Master"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Voice of the Chain Master"
                    :page 111
                    :summary "communicate telepathically with, perceive through, and speak through your familiar"})]
      :prereqs [opt5e/pact-of-the-chain-prereq]})
    (t/option-cfg
     {:name "Whispers of the Grave"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Whispers of the Grave"
                    :page 111
                    :summary "cast speak with dead at will"})
                  (mod5e/spells-known 3 :speak-with-dead ::char5e/cha "Warlock" 0 "at will")]
      :prereqs [(opt5e/total-levels-option-prereq 9 :warlock)]})
    (t/option-cfg
     {:name "Witch Sight"
      :modifiers [(mod5e/trait-cfg
                   {:name "Eldritch Invocation: Witch Sight"
                    :range units5e/ft-30
                    :page 111
                    :summary "see the true form of a creature"})]
      :prereqs [(opt5e/total-levels-option-prereq 15 :warlock)]})]))


(def warlock-spells-known
  {1 2
   2 1
   3 1
   4 1
   5 1
   6 1
   7 1
   8 1
   9 1
   11 1
   13 1
   15 1
   17 1
   19 1})

(defn eldritch-invocation-selection [plugin-invocations spell-lists spells-map & [num]]
  (opt5e/eldritch-invocation-selection
   {:options (eldritch-invocation-options plugin-invocations spell-lists spells-map)
    :min (or num 1)
    :max (or num 1)}))

(defn mystic-arcanum-selection [spells-map spell-level]
  (t/selection-cfg
   {:name (str "Mystic Arcanum: Spell Level " spell-level)
    :tags #{:spells}
    :options (opt5e/spell-options
              spells-map
              (get-in sl5e/spell-lists [:warlock spell-level])
              ::char5e/cha
              "Warlock"
              false
              "uses Mystic Arcanum")}))

(defn warlock-option [spell-lists spells-map plugin-subclasses-map language-map weapon-map invocations]
  (opt5e/class-option
   spell-lists
   spells-map
   plugin-subclasses-map
   language-map
   weapon-map
   {:name "Warlock"
    :key :warlock
    :spellcasting {:cantrips-known {1 2 4 1 10 1}
                   :spells-known warlock-spells-known
                   :slot-schedule t-base/warlock-spell-slot-schedule
                   :known-mode :schedule
                   :pact-magic? true
                   :ability ::char5e/cha}
    :multiclass-prereqs [(opt5e/ability-prereq ::char5e/cha 13)]
    :spellcaster true
    :hit-die 8
    :ability-increase-levels [4 8 12 16 19]
    :profs {:armor {:light false}
            :weapon {:simple false}
            :save {::char5e/wis true ::char5e/cha true}
            :skill-options {:choose 2 :options {:arcana true :deception true :history true :intimidation true :investigation true :nature true :religion true}}}
    :modifiers [(mod/modifier ?pact-magic? true)]
    :selections [(opt5e/new-starting-equipment-selection
                  :warlock
                  {:name "Weapon"
                   :options [(t/option-cfg
                              {:name "Light Crossbow & 20 Bolts"
                               :modifiers [(mod5e/weapon :crossbow-light 1)
                                           (mod5e/equipment :crossbow-bolt 20)]})
                             (t/option-cfg
                              {:name "Simple Weapon"
                               :selections [(opt5e/new-starting-equipment-selection
                                             :warlock
                                             {:name "Simple Weapon"
                                              :options (opt5e/simple-weapon-options 1 (vals weapon-map))
                                              :min 1
                                              :max 1})]})]})
                 (opt5e/simple-weapon-selection 1 :warlock weapon-map)]
    :equipment-choices [{:name "Equipment Pack"
                         :options {:scholars-pack 1
                                   :dungeoneers-pack 1}}
                        {:name "Spellcasting Equipment"
                         :options {:component-pouch 1
                                   :arcane-focus 1}}]
    :weapons {:dagger 2}
    :armor {:leather 1}
    :levels {2 {:selections [(eldritch-invocation-selection invocations spell-lists spells-map 2)]}
             3 {:selections [(t/selection-cfg
                              {:name "Pact Boon"
                               :tags #{:class}
                               :options (pact-boon-options spell-lists spells-map)})]}
             5 {:selections [(eldritch-invocation-selection invocations spell-lists spells-map)]}
             7 {:selections [(eldritch-invocation-selection invocations spell-lists spells-map)]}
             9 {:selections [(eldritch-invocation-selection invocations spell-lists spells-map)]}
             11 {:selections [(mystic-arcanum-selection spells-map 6)]
                 :modifiers [(mod5e/dependent-trait
                              {:name "Mystic Arcanum"
                               :level 11
                               :page 108
                               :summary "You gain a 6th level spell you can cast without expending a slot, more at higher levels"
                               :frequency (units5e/long-rests
                                           (mod5e/level-val
                                            (?class-level :warlock)
                                            {13 2
                                             15 3
                                             17 4
                                             :default 1}))})]}
             12 {:selections [(eldritch-invocation-selection invocations spell-lists spells-map)]}
             13 {:selections [(mystic-arcanum-selection spells-map 7)]}
             15 {:selections [(eldritch-invocation-selection invocations spell-lists spells-map)
                              (mystic-arcanum-selection spells-map 8)]}
             17 {:selections [(mystic-arcanum-selection spells-map 9)]}
             18 {:selections [(eldritch-invocation-selection invocations spell-lists spells-map)]}}
    :traits [{:name "Eldrich Master"
              :level 20
              :page 108
              :summary "Regain all Pact Magic spell slots"
              :frequency units5e/long-rests-1}]
    :subclass-level 1
    :subclass-title "Otherworldly Patron"
    :subclasses [{:name "The Fiend"
                  :traits [{:name "Dark One's Own Luck"
                            :level 6
                            :page 109
                            :summary "add d10 to an ability check or save roll"
                            :frequency units5e/rests-1}
                           {:name "Fiendish Resilience"
                            :level 10
                            :page 109
                            :summary "resistance to a chosen damage type"}
                           {:name "Hurl Through Hell"
                            :level 14
                            :page 109
                            :summary "deal 10d10 psychic damage when you hit with an attack"
                            :frequency units5e/rests-1}]
                  :levels {1 {:modifiers [(mod5e/dependent-trait
                                           {:name "Dark One's Blessing"
                                            :page 109
                                            :summary (str "gain " (+ (?class-level :warlock)
                                                                     (?ability-bonuses ::char5e/cha)) " temp HPs when you reduce a hostile creature to 0 HPs")})]
                              :selections [(opt5e/warlock-subclass-spell-selection spell-lists spells-map [:burning-hands :command])]}
                           3 {:selections [(opt5e/warlock-subclass-spell-selection spell-lists spells-map [:blindness-deafness :scorching-ray])]}
                           5 {:selections [(opt5e/warlock-subclass-spell-selection spell-lists spells-map [:fireball :stinking-cloud])]}
                           7 {:selections [(opt5e/warlock-subclass-spell-selection spell-lists spells-map [:fire-shield :wall-of-fire])]}
                           9 {:selections [(opt5e/warlock-subclass-spell-selection spell-lists spells-map [:flame-strike :hallow])]}}}
                 #_{:name "The Archfey"
                    :modifiers [(mod5e/action
                                 {:name "Fey Presence"
                                  :page 109
                                  :summary (str "charm or frighten creatures in a 10 ft cube from you unless the succeed on a DC " (?spell-save-dc ::char5e/cha) " WIS save.")
                                  :duration units5e/turns-1
                                  :frequency units5e/rests-1})]
                    :levels {1 {:selections [(opt5e/warlock-subclass-spell-selection [:faerie-fire :sleep])]}
                             3 {:selections [(opt5e/warlock-subclass-spell-selection [:calm-emotions :phantasmal-force])]}
                             5 {:selections [(opt5e/warlock-subclass-spell-selection [:blink :plant-growth])]}
                             6 {:modifiers [(mod5e/reaction
                                             {:name "Misty Escape"
                                              :page 109
                                              :frequency units5e/rests-1
                                              :duration units5e/rounds-1
                                              :summary "when you take damage, turn invisible and teleport up to 60 ft."})]}
                             7 {:selections [(opt5e/warlock-subclass-spell-selection [:dominate-beast :greater-invisibility])]}
                             9 {:selections [(opt5e/warlock-subclass-spell-selection [:dominate-person :seeming])]}
                             10 {:modifiers [(mod5e/condition-immunity :charmed)
                                             (mod5e/reaction
                                              {:name "Beguiling Defenses"
                                               :page 109
                                               :duration units5e/minutes-1
                                               :summary (str "when a creature attempts to charm you, you can turn it back on them with a spell save DC " (?spell-save-dc ::char5e/cha) " WIS save")})]}
                             14 {:modifiers [(mod5e/action
                                              {:name "Dark Delerium"
                                               :page 109
                                               :summary (str "charm or frighten a creature within 60 ft., spell save DC " (?spell-save-dc ::char5e/cha) "WIS save")
                                               :frequency units5e/rests-1})]}}}
                 #_{:name "The Great Old One"
                    :levels {1 {:selections [(opt5e/warlock-subclass-spell-selection [:dissonant-whispers :tashas-hideous-laughter])]}
                             3 {:selections [(opt5e/warlock-subclass-spell-selection [:detect-thoughts :phantasmal-force])]}
                             5 {:selections [(opt5e/warlock-subclass-spell-selection [:clairvoyance :sending])]}
                             6 {:modifiers [(mod5e/reaction
                                             {:name "Entropic Ward"
                                              :page 110
                                              :frequency units5e/rests-1
                                              :summary "impose disadvantage on an attack roll against you, if it misses, gain advantage on your next attack roll against the attacker"})]}
                             7 {:selections [(opt5e/warlock-subclass-spell-selection [:dominate-beast :evards-black-tentacles])]}
                             9 {:selections [(opt5e/warlock-subclass-spell-selection [:dominate-person :telekinesis])]}
                             10 {:modifiers [(mod5e/damage-resistance :psychic)]}}
                    :traits [{:name "Awakened Mind"
                              :level 1
                              :page 110
                              :summary "speak telepathically to a creature"
                              :range units5e/ft-30}
                             {:name "Thought Shield"
                              :level 10
                              :page 110
                              :summary "your thoughts can't be read; resistance to psychic damage; when a creature deals psychic damage to you it takes the same amount"}
                             {:name "Create Thrall"
                              :level 14
                              :page 110
                              :summary "charm incapacitated creature, it becomes charmed by you, you can communicate with it telepathically"}]}]}))
