(ns orcpub.dnd.e5.template
  (:require [clojure.string :as s]
            [clojure.set :as sets]
            [orcpub.entity :as entity]
            [orcpub.entity-spec :as es]
            [orcpub.template :as t]
            [orcpub.dice :as dice]
            [orcpub.modifiers :as mod]
            [orcpub.common :as common]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.modifiers :as mod5e]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.units :as units5e]
            [orcpub.dnd.e5.weapons :as weapon5e]
            [orcpub.dnd.e5.equipment :as equip5e]
            [orcpub.dnd.e5.character.equipment :as char-equip5e]
            [orcpub.dnd.e5.armor :as armor5e]
            [orcpub.dnd.e5.spell-lists :as sl]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.skills :as skill5e]
            #_[orcpub.dnd.e5.display :as disp5e]
            [orcpub.dnd.e5.template-base :as t-base]
            #_[orcpub.dnd.e5.templates.scag :as scag]
            #_[orcpub.dnd.e5.templates.ua-base :as ua]
            [re-frame.core :refer [subscribe dispatch]]))

(def character
  {::entity/options {:ability-scores {::entity/key :standard-scores
                                      ::entity/value (char5e/abilities 15 14 13 12 10 8)}
                     :class [{::entity/key :barbarian
                              ::entity/options {:levels [{::entity/key :level-1}]}}]}})

(defn set-ability! [app-state ability-key ability-value]
  (swap! app-state
         assoc-in
         [:character ::entity/options :ability-scores ::entity/value ability-key]
         ability-value))

(defn swap-abilities [app-state i other-i k v]
  (fn []
    (swap! app-state
           update-in
           [:character ::entity/options :ability-scores ::entity/value]
           (fn [a]
             (let [a-vec (vec a)
                   other-index (mod other-i (count a-vec))
                   [other-k other-v] (a-vec other-index)]
               (assoc a k other-v other-k v))))))

(def ability-icons
  "Icons / names are provided by https://game-icons.net/"
  {::char5e/str "strong"
   ::char5e/con "rod-of-asclepius"
   ::char5e/dex "body-balance"
   ::char5e/int "read"
   ::char5e/wis "meditation"
   ::char5e/cha "aura"})

;; REVIEW: use generic function for all icons instead?
(defn ability-icon
  "Takes [k size theme] to generate css class name based on the size and theme.
   `k` is the icon name/key in image set,
   `size` sets both h and w,
   `theme` sets light/dark color and opacity.
   Only used for `ability-icons` -- see body of function below."
  [k size theme]
  (let [light-theme? (= "light-theme" theme)]
    [:img {:class-name (str "h-" size " w-" size (if light-theme? " opacity-7"))
           :src (str (if light-theme? "/image/black/" "/image/") (ability-icons k) ".svg")}]))

(defn ability-modifier [v]
  [:div.f-6-12.f-w-n.h-24
   [:div.t-a-c.f-s-10.opacity-5
    "mod"]
   [:div.m-t--1
    (opt5e/ability-bonus-str v)]])

(defn ability-component [k v i app-state controls]
  [:div.m-t-10.t-a-c
   (ability-icon k 24 @(subscribe [:theme]))
   [:div.uppercase (name k)]
   [:div.f-s-18.f-w-b v]
   (ability-modifier v)
   controls])

(defn abilities-standard [app-state]
  [:div.flex.justify-cont-s-b
    (let [abilities (or (opt5e/get-raw-abilities app-state) (char5e/abilities 15 14 13 12 10 8))
          abilities-vec (vec abilities)]
      (doall
       (map-indexed
        (fn [i [k v]]
          ^{:key k}
          [ability-component k v i app-state
           [:div.f-s-16
            [:i.fa.fa-chevron-circle-left.orange
             {:on-click (swap-abilities app-state i (dec i) k v)}]
            [:i.fa.fa-chevron-circle-right.orange.m-l-5
             {:on-click (swap-abilities app-state i (inc i) k v)}]]])
        abilities-vec)))])

(defn abilities-roller [app-state reroll-fn]
  [:div
   (abilities-standard app-state)
   [:button.form-button.m-t-5
    {:on-click (fn [e]
                 (reroll-fn)
                 (.stopPropagation e))}
    "Re-Roll"]])

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


(defn point-buy-abilities [app-state]
  (let [abilities (or (opt5e/get-raw-abilities app-state)
                      (char5e/abilities 8 8 8 8 8 8))
        abilities-vec (vec (map (fn [[a v]] [a (-> v (min 15) (max 8))]) abilities))
        points-used (apply + (map (comp score-costs second) abilities-vec))
        points-remaining (- point-buy-points points-used)]
    [:div
     [:div.m-t-5
      [:span.f-w-n "Points Remaining: "]
      [:span.f-w-b points-remaining]]
     [:div.flex.justify-cont-s-b
      (doall
       (map-indexed
        (fn [i [k v]]
          (let [increase-disabled? (or (>= v 15) (<= points-remaining 0))
                decrease-disabled? (or (<= v 8) (>= points-remaining point-buy-points))]
            ^{:key k}
            [ability-component k v i app-state
             [:div.f-s-16
              [:i.fa.fa-minus-circle.orange
               {:class-name (if decrease-disabled? "opacity-5 cursor-disabled")
                :on-click (fn [_] (if (not decrease-disabled?) (set-ability! app-state k (dec v))))}]
              [:i.fa.fa-plus-circle.orange.m-l-5
               {:class-name (if increase-disabled? "opacity-5 cursor-disabled")
                :on-click (fn [_] (if (not increase-disabled?) (set-ability! app-state k (inc v))))}]]]))
        abilities-vec))]]))

(declare template-selections)

#_(def aasimar-option-cfg
  {:name "Aasimar"
   :abilities {::char5e/cha 2}
   :size :medium
   :speed 30
   :darkvision 60
   :source :vgm
   :languages ["Common" "Celestial"]
   :modifiers [(mod5e/damage-resistance :necrotic)
               (mod5e/damage-resistance :radiant)
               (mod5e/spells-known 0 :light ::char5e/cha "Aasimar")]
   :traits [{:name "Healing Hands"
             :page 105
             :summary "Heal a creature a number of hit points equal to your level"}]
   :subraces [{:name "Protector Aasimar"
               :abilities {::char5e/wis 1}
               :modifiers [(mod5e/dependent-trait
                            {:name "Radiant Soul"
                             :level 3
                             :page 105
                             :source :vgm
                             :summary (str "For 1 minute, sprout wings (30 ft. flying speed) and deal "
                                           ?total-levels
                                           " extra radiant damage.") })]}
              {:name "Scourge Aasimar"
               :abilities {::char5e/con 1}
               :modifiers [(mod5e/dependent-trait
                            {:name "Radiant Consumption"
                             :level 3
                             :page 105
                             :source :vgm
                             :summary (let [level ?total-levels]
                                        (str "For 1 minute, deal "
                                             (common/round-up (/ level 2))
                                             " radiant damage to each creature within 10 ft. and deal an additional " level " radiant damage to one target you deal damage to with a spell or attack"))})]}
              {:name "Fallen Aasimar"
               :abilities {::char5e/str 1}
               :modifiers [(mod5e/dependent-trait
                            {:name "Necrotic Shroud"
                             :level 3
                             :page 105
                             :source :vgm
                             :summary (str "For 1 minute, creatures within 10 ft. must succeed on a DC " (?spell-save-dc ::char5e/cha) " cha save or be frightened of you. During that time also deal an additional " ?total-levels " necrotic damage to one target you deal damage to with a spell or attack.")})]}]})

#_(defn powerful-build [page]
  {:name "Powerful Build"
   :page page
   :source :vgm
   :summary "Count as Large for purposes of determining weight you can carry, push, drag, or lift."})

#_(def firbolg-option-cfg
  {:name "Firbolg"
   :abilities {::char5e/wis 2 ::char5e/str 1}
   :size :medium
   :speed 30
   :source :vgm
   :languages ["Common" "Elvish" "Giant"]
   :modifiers [(mod5e/spells-known 1 :detect-magic ::char5e/wis "Firbolg")
               (mod5e/spells-known 1 :disguise-self ::char5e/wis "Firbolg" 1 "only to seem 3 ft. shorter")
               (mod5e/bonus-action
                {:name "Hidden Step"
                 :duration units5e/rounds-1
                 :frequency units5e/rests-1
                 :page 107
                 :source :vgm
                 :summary "Turn invisible"})]
   :traits [(powerful-build 107)
            {:name "Speech of Beast and Leaf"
             :page 107
             :source :vgm
             :summary "Beast and plants can understand you and you have advantage on Charisma checks to influence them."}]})

#_(defn goliath-option-cfg [source page]
  {:name (str "Goliath (" (s/upper-case (name source)) ")")
   :key (if (= :vgm source) :goliath :goliath-ee)
   :abilities {::char5e/str 2 ::char5e/con 1}
   :size :medium
   :speed 30
   :languages ["Common" "Giant"]
   :source source
   :modifiers [(mod5e/skill-proficiency :athletics)
               (mod5e/reaction
                {:name "Stone's Endurance"
                 :frequency units5e/rests-1
                 :page page
                 :source source
                 :summary (str "Reduce damage taken by 1d12 + " (::char5e/con ?ability-bonuses))})]
   
   :traits [{:name "Mountain Born"
             :page page
             :summary "Adapted to high altitude and cold climates."}
            (powerful-build page)]})

#_(def kenku-option-cfg
  {:name "Kenku"
   :abilities {::char5e/dex 2 ::char5e/wis 1}
   :size :medium
   :speed 30
   :source :vgm
   :languages ["Common" "Auran"]
   :modifiers [(mod5e/dependent-trait
                {:name "Mimicry"
                 :page 111
                 :source :vgm
                 :summary (str "Mimic sounds you've heard, creatures disbelieve it with an insight check opposed to your deception check (1d20" (common/mod-str (:deception ?skill-bonuses)) ").")})]
   :selections [(opt5e/skill-selection [:acrobatics :deception :stealth :sleight-of-hand] 2)]
   :profs {:skill-options {:choose 2 :options {:acrobatics true :deception true :stealth true :sleight-of-hand true}}}
   :traits [{:name "Expert Forgery"
             :page 111
             :summary "Advantage on checks to duplicate existing objects"}]})

#_(def lizardfolk-option-cfg
  {:name "Lizardfolk"
   :abilities {::char5e/con 2 ::char5e/wis 1}
   :size :medium
   :speed 30
   :source :vgm
   :languages ["Common" "Draconic"]
   :selections [(opt5e/skill-selection [:animal-handling :nature :perception :stealth :survival] 2)]
   :modifiers [(mod5e/swimming-speed 30)
               (mod/modifier ?natural-ac-bonus 3)
               (mod/modifier ?armor-class-with-armor
                             (fn [armor & [shield]]
                               (max (+ ?base-armor-class
                                       (if shield (?shield-ac-bonus shield) 0))
                                    (?armor-class-with-armor armor shield))))
               (mod5e/bonus-action
                {:name "Hungry Jaws"
                 :page 113
                 :source :vgm
                 :frequency units5e/rests-1
                 :summary (str "Special attack with your bite. If you hit, you gain "
                               (max 1 (::char5e/con ?ability-bonuses))
                               " temp. hit points")})
               (mod5e/attack
                {:name "Bite"
                 :page 113
                 :source :vgm
                 :attack-type :melee
                 :damage-type :piercing
                 :damage-die 6
                 :damage-die-count 1
                 :damage-modifier (::char5e/str ?ability-bonuses)})]
   :profs {:skill-options {:choose 2 :options {:animal-handling true :nature true :stealth true :perception true :survival true}}}
   :traits [{:name "Cunning Artisan"
             :page 113
             :summary "Craft certain weapons and armor from creature remains."}
            {:name "Hold Breath"
             :page 113
             :summary "Up to 15 min."}]})

#_(def tabaxi-option-cfg
  {:name "Tabaxi"
   :abilities {::char5e/dex 2 ::char5e/cha 1}
   :size :medium
   :speed 30
   :darkvision 60
   :source :vgm
   :languages ["Common"]
   :modifiers [(mod5e/climbing-speed 20)
               (mod5e/skill-proficiency :perception)
               (mod5e/skill-proficiency :stealth)
               (mod5e/attack
                {:name "Cat's Claws"
                 :page 115
                 :source :vgm
                 :attack-type :melee
                 :damage-type :slashing
                 :damage-die 4
                 :damage-die-count 1
                 :damage-modifier (::char5e/str ?ability-bonuses)})]
   :language-options {:choose 1 :options {:any true}}
   :traits [{:name "Feline Agility"
             :page 115
             :summary "Double speed when moving on your turn in combat."}]})

#_(def triton-option-cfg
  {:name "Triton"
   :abilities {::char5e/str 1 ::char5e/con 1 ::char5e/cha 1}
   :size :medium
   :speed 30
   :source :vgm
   :languages ["Common" "Primordial"]
   :modifiers [(mod5e/swimming-speed 30)
               (mod5e/spells-known 1 :fog-cloud ::char5e/cha "Triton")
               (mod5e/spells-known 2 :gust-of-wind ::char5e/cha "Triton" 3)
               (mod5e/spells-known 3 :wall-of-water ::char5e/cha "Triton" 5)
               (mod5e/damage-resistance :cold)]
   :traits [{:name "Amphibious"
             :page 118
             :summary "Breath water and air"}
            {:name "Emissary of the Sea"
             :page 118
             :summary "Water-breathing beasts can understand your words."}
            {:name "Guardians of the Depths"
             :page 118
             :summary "No negative effects from deep, underwater environment."}]})

#_(def bugbear-option-cfg
  {:name "Bugbear"
   :abilities {::char5e/str 2 ::char5e/dex 1}
   :size "Medium"
   :speed 30
   :darkvision 60
   :source :vgm
   :modifiers [(mod5e/skill-proficiency :stealth)]
   :languages ["Common" "Goblin"]
   :traits [{:name "Long Limbed"
             :page 119
             :summary "5 ft. additional reach to melee attacks on your turn"}
            (powerful-build 119)
            {:name "Surprise Attack"
             :page 119
             :summary "Extra 2d6 damage when hitting a surprised creature."}]})

#_(def goblin-option-cfg
  {:name "Goblin"
   :abilities {::char5e/dex 2 ::char5e/con 1}
   :size "Medium"
   :speed 30
   :darkvision 60
   :source :vgm
   :languages ["Common" "Goblin"]
   :modifiers [(mod5e/dependent-trait
                {:name "Fury of the Small"
                 :page 119
                 :summary (str "Extra " ?total-levels " damage to a larger creature (use once per long or short rest)")})
               (mod5e/bonus-action
                {:name "Nimble Escape"
                 :page 119
                 :summary "Disengage or Hide action as a bonus action."})]})

#_(def hobgoblin-option-cfg
  {:name "Hobgoblin"
   :abilities {::char5e/con 2 ::char5e/int 1}
   :size "Medium"
   :speed 30
   :darkvision 60
   :source :vgm
   :languages ["Common" "Goblin"]
   :selections [(t/selection-cfg
                 {:name "Martial Weapon Proficiencies"
                  :tags #{:weapon-profs :profs}
                  :options (opt5e/weapon-proficiency-options (weapon5e/martial-weapons weapon5e/weapons))
                  :min 2
                  :max 2})]
   :modifiers [(mod5e/light-armor-proficiency)]
   :traits [{:name "Saving Face"
             :page 119
             :summary "Add a bonus to a missed roll for each ally you can see, up to 5."}]})

#_(def kobold-option-cfg
  {:name "Kobold"
   :abilities {::char5e/dex 2 ::char5e/str -2}
   :size "Medium"
   :speed 30
   :darkvision 60
   :source :vgm
   :languages ["Common" "Draconic"]
   :traits [{:name "Grovel, Cower, and Beg"
             :page 119
             :summary "Cower to give allies advantage on attacks against enemies within 10 ft."}
            {:name "Pack Tactics"
             :page 119
             :summary "Advantage on attacks if an ally is within 5 ft. of the target."}
            (sunlight-sensitivity 119)]})

#_(def orc-option-cfg
  {:name "Orc"
   :abilities {::char5e/str 2 ::char5e/con 1 ::char5e/int -2}
   :size "Medium"
   :speed 30
   :darkvision 60
   :source :vgm
   :modifiers [(mod5e/skill-proficiency :intimidation)
               (mod5e/bonus-action
                {:name "Aggressive"
                 :page 120
                 :summary (str "Move up to " ?speed " feet toward and enemy you can see or hear")})]
   :languages ["Common" "Orc"]
   :traits [(powerful-build 120)]})

#_(def yuan-ti-option-cfg
  {:name "Yuan-Ti Pureblood"
   :abilities {::char5e/cha 2 ::char5e/int 1}
   :size "Medium"
   :speed 30
   :darkvision 60
   :source :vgm
   :modifiers [(mod5e/spells-known 0 :poison-spray ::char5e/cha "Yuan-Ti")
               (mod5e/spells-known 1 :animal-friendship ::char5e/cha "Yuan-Ti" 1 "unlimited uses, can only target snakes")
               (mod5e/spells-known 2 :suggestion ::char5e/cha "Yuan-Ti" 3 "one use per long rest")
               (mod5e/damage-immunity :poison)
               (mod5e/condition-immunity :poisoned)
               (mod5e/saving-throw-advantage [:magic])]
   :languages ["Common" "Abyssal" "Draconic"]})

#_(defn al-illegal-flying-mod [name]
  (mod5e/al-illegal (str name " gains flying speed a 1st level, which is not legal")))

#_(def aarakocra-option-cfg
  {:name "Aarakocra"
   :abilities {::char5e/dex 2 ::char5e/wis 1}
   :size :medium
   :speed 25
   :languages ["Common" "Aarakocra" "Auran"]
   :source :ee
   :modifiers [(mod5e/flying-speed-override 50)
               (al-illegal-flying-mod "Aarakocra")
               (mod5e/attack
                {:name "Talons"
                 :source :ee
                 :page 3
                 :damage-die 4
                 :damage-die-count 1
                 :damage-type :slashing
                 :damage-modifier (?ability-bonuses ::char5e/str)})]})

#_(def ee-gnome-option-cfg
  (opt5e/deep-gnome-option-cfg :deep-gnome-ee :ee 7))

#_(def genasi-option-cfg
  {:name "Genasi"
   :source :ee
   :abilities {::char5e/con 2}
   :size "Medium"
   :speed 30
   :languages ["Common" "Primordial"]
   :subraces [{:name "Air Genasi"
               :abilities {::char5e/dex 1}
               :modifiers [(mod5e/spells-known 2 :levitate ::char5e/con "Air Genasi" nil "once/long rest")]
               :traits [{:name "Unending Breath"
                         :page 9
                         :source :ee
                         :summary "hold breath indefinitely"}
                        {:name "Mingle with the Wind"
                         :page 9
                         :source :ee
                         :frequency units5e/long-rests-1
                         :summary "cast levitate without material components"}]}
              {:name "Earth Genasi"
               :abilities {::char5e/str 1}
               :modifiers [(mod5e/spells-known 2 :pass-without-trace ::char5e/con "Earth Genasi" nil "once/long rest")]
               :traits [{:name "Earth Walk"
                         :page 9
                         :source :ee
                         :summary "walk on difficult earth or stone terrain without extra movement"}
                        {:name "Merge with Stone"
                         :page 9
                         :source :ee
                         :frequency units5e/long-rests-1
                         :summary "cast pass without trace without material components"}]}
              {:name "Fire Genasi"
               :abilities {::char5e/int 1}
               :modifiers [(mod5e/darkvision 60)
                           (mod5e/damage-resistance :fire)
                           (mod5e/spells-known 0 :produce-flame ::char5e/con "Fire Genasi")
                           (mod5e/spells-known 1 :burning-hands ::char5e/con "Fire Genasi" 3 "once/long rest")]
               :traits [{:name "Fire Resistance"
                         :page 10
                         :source :ee
                         :summary "resistance to fire damage"}
                        {:name "Reach to the Blaze"
                         :page 10
                         :source :ee
                         :frequency units5e/long-rests-1
                         :summary "have 'produce flame' cantrip; at level 3 can cast burning hands"}]}
              {:name "Water Genasi"
               :abilities {::char5e/wis 1}
               :modifiers [(mod5e/damage-resistance :acid)
                           (mod5e/swimming-speed 30)
                           (mod5e/spells-known 0 :shape-water ::char5e/con "Water Genasi")
                           (mod5e/spells-known 1 :create-or-destroy-water ::char5e/con "Water Genasi" 3 "once/long rest")]
               :traits [{:name "Acid Resistance"
                         :page 10
                         :source :ee
                         :summary "resistant to acid damage"}
                        {:name "Amphibious"
                         :page 10
                         :source :ee
                         :summary "breathe in air and water"}
                        {:name "Swim"
                         :page 10
                         :source :ee
                         :summary "swimming speed of 30 ft."}
                        {:name "Call to the Wave"
                         :page 10
                         :source :ee
                         :frequency units5e/long-rests-1
                         :summary "have 'shape water' cantrip; at level 3 can cast create or destroy water as 2nd level"}]}]})



#_(defn criminal-background [nm]
  {:name nm
   :help "You have a history of criminal activity."
   :traits [{:name "Criminal Contact"
             :page 129
             :summary "You have a contact into a network of criminals"}]
   :profs {:skill {:deception true, :stealth true}
           :tool {:thieves-tools true}
           :tool-options {:gaming-set 1}}
   :equipment {:crowbar 1
               :clothes-common 1
               :pouch 1}
   :treasure {:gp 15}})

#_(def ships-passage-trait-cfg
  {:name "Ship's Passage"
   :page 139
   :summary "You are able to secure free passage on a sailing ship"})

#_(def charlatan-bg
  {:name "Charlatan"
   :help "You have a history of being able to work people to your advantage."
   :traits [{:name "False Identity"
             :page 128
             :summary "you have a false identity; you can forge documents"}]
   :profs {:skill {:deception true :sleight-of-hand true}
           :tool {:disguise-kit true :forgery-kit true}}
   :equipment {:clothes-fine 1
               :disguise-kit 1
               :pouch 1}
   :treasure {:gp 15}})

#_(def entertainer-bg
  {:name "Entertainer"
   :help "You have a history of entertaining people."
   :traits [{:name "By Popular Demand"
             :page 130
             :summary "you are able to find a place to perform, in which you will recieve free food and lodging"}]
   :profs {:skill {:acrobatics true :performance true}
           :tool {:disguise-kit true}
           :tool-options {:musical-instrument 1}}
   :equipment-choices [musical-instrument-choice-cfg]
   :equipment {:costume 1
               :pouch 1}
   :treasure {:gp 15}})

#_(def gladiator-bg
  {:name "Gladiator"
   :help "You have a history of gladiatorial entertainment."
   :traits [{:name "By Popular Demand"
             :page 130
             :summary "you are able to find a place to perform, in which you will recieve free food and lodging"}]
   :profs {:skill {:acrobatics true :performance true}
           :tool {:disguise-kit true}
           :tool-options {:musical-instrument 1}}
   :selections [(opt5e/new-starting-equipment-selection
                 nil
                 {:name "Gladiator Weapon"
                  :options (opt5e/weapon-options weapon5e/weapons)})]
   :equipment {:costume 1
               :pouch 1}
   :treasure {:gp 15}})

#_(def folk-hero-bg
  {:name "Folk Hero"
   :help "You are regarded as a hero by the people of your home village."
   :traits [{:name "Rustic Hospitality"
             :page 131
             :summary "find a place to rest, hide, or recuperate among commoners"}]
   :profs {:skill {:animal-handling true :survival true}
           :tool {:land-vehicles true}
           :tool-options {:artisans-tool 1}}
   :equipment-choices [opt5e/artisans-tools-choice-cfg]
   :equipment {:shovel 1
               :pot-iron 1
               :clothes-common 1
               :pouch 1}
   :treasure {:gp 10}})

#_(def guild-artisan-bg
  {:name "Guild Artisan"
   :help "You are an artisan and a member of a guild in a particular field."
   :traits [{:name "Guild Membership"
             :page 133
             :summary "fellow guild members will provide you with food and lodging; you have powerful political connections through your guild"}]
   :profs {:skill {:insight true :persuasion true}
           :tool-options {:artisans-tool 1}
           :language-options {:choose 1 :options {:any true}}}
   :equipment-choices [opt5e/artisans-tools-choice-cfg]
   :equipment {:clothes-traveler-s 1
               :pouch 1}
   :treasure {:gp 15}})

#_(def guild-merchant-bg
  {:name "Guild Merchant"
   :help "You are member of a guild of merchants"
   :traits [{:name "Guild Membership"
             :page 133
             :summary "fellow guild members will provide you with food and lodging; you have powerful political connections through your guild"}]
   :profs {:skill {:insight true :persuasion true}
           :language-options {:choose 1 :options {:any true}}}
   :selections [(t/selection-cfg
                 {:name "Proficiency: Navigator's Tools or Language"
                  :tags #{:profs}
                  :options [(t/option-cfg
                             {:name "Navigator's Tools"
                              :modifiers [(mod5e/tool-proficiency :navigators-tools)]})
                            (t/option-cfg
                             {:name "Language"
                              :selections [(opt5e/language-selection opt5e/languages 1)]})]})]
   :equipment {:clothes-traveler-s 1
               :pouch 1
               :mule 1
               :cart 1}
   :treasure {:gp 15}})

#_(def hermit-bg
  {:name "Hermit"
   :help "You have lived a secluded life."
   :traits [{:name "Discovery"
             :page 134
             :summary "You have made a powerful and unique discovery"}]
   :profs {:skill {:medicine true :religion true}
           :tool {:herbalism-kit true}
           :language-options {:choose 1 :options {:any true}}}
   :equipment {:case-map-or-scroll 1
               :clothes-common 1
               :herbalism-kit 1}
   :custom-equipment {"Winter Blanket" 1
                      "Notes from studies/prayers" 1}
   :treasure {:gp 5}})


#_(def noble-bg
  {:name "Noble"
   :help "You are of noble birth."
   :traits []
   :profs {:skill {:history true :persuasion true}
           :tool-options {:gaming-set 1}
           :language-options {:choose 1 :options {:any true}}}
   :selections [(t/selection-cfg
                 {:name "Noble Feature"
                  :tags #{:background}
                  :options [(t/option-cfg
                             {:name "Position of Privilege"
                              :modifiers [(mod5e/trait-cfg
                                           {:name "Position of Privilege"
                                            :page 135
                                            :summary "you are welcome in high society and common folk try to accomodate you"})]})
                            (t/option-cfg
                             {:name "Retainers"
                              :modifiers [(mod5e/trait-cfg
                                           {:name "Retainers"
                                            :page 136
                                            :summary "You have 3 commoner retainers"})]})]})]
   :equipment {:clothes-fine 1
               :signet-ring 1
               :purse 1}
   :custom-equipment {"Scroll of Pedigree" 1}
   :treasure {:gp 25}})

#_(def knight-bg
  {:name "Knight"
   :help "You are a knight."
   :traits [{:name "Retainers"
             :page 136
             :summary "You have 2 commoner retainers and 1 noble squire"}]
   :profs {:skill {:history true :persuasion true}
           :tool-options {:gaming-set 1}
           :language-options {:choose 1 :options {:any true}}}
   :equipment {:clothes-fine 1
               :signet-ring 1
               :purse 1}
   :custom-equipment {"Scroll of Pedigree" 1
                      "Emblem of Chivalry" 1}
   :treasure {:gp 25}})

#_(def outlander-bg
  {:name "Outlander"
   :help "You were raised in the wilds."
   :traits [{:name "Wanderer"
             :page 136
             :summary "Your memory of maps, geography, settlements, and terrain is excellent. You can find fresh food and water for you and 5 other people."}]
   :profs {:skill {:athletics true :survival true}
           :tool-options {:musical-instrument 1}
           :language-options {:choose 1 :options {:any true}}}
   :equipment {:staff 1
               :clothes-traveler-s 1
               :pouch 1
               :hunting-trap 1}
   :custom-equipment {"Trophy from Animal You Killed" 1}
   :treasure {:gp 10}})

#_(def sage-bg
  {:name "Sage"
   :help "You spent your life studying lore."
   :traits [{:name "Researcher"
             :page 139
             :summary "If you don't know a piece of info you often know where to find it"}]
   :profs {:skill {:arcana true :history true}
           :language-options {:choose 2 :options {:any true}}}
   :equipment {:ink 1
               :clothes-common 1
               :pouch 1
               :knife-small 1}
   :custom-equipment {"Quill" 1
                      "Letter with question from dead colleague" 1}
   :treasure {:gp 10}})

#_(def sailor-bg
  {:name "Sailor"
   :help "You were a member of a crew for a seagoing vessel."
   :traits [ships-passage-trait-cfg]
   :profs {:skill {:athletics true :perception true}
           :tool {:navigators-tools true :water-vehicles true}}
   :weapons {:club 1}
   :equipment {:rope-silk 1
               :clothes-common 1
               :pouch 1}
   :custom-equipment {"Belaying Pin" 1
                      "Lucky Charm" 1}
   :treasure {:gp 10}})


#_(def pirate-bg
  {:name "Pirate"
   :help "You were a member of a crew for a seagoing vessel."
   :profs {:skill {:athletics true :perception true}
           :tool {:navigators-tools true :water-vehicles true}}
   :weapons {:club 1}
   :equipment {:rope-silk 1
               :clothes-common 1
               :pouch 1}
   :selections [(t/selection-cfg
                 {:name "Feature"
                  :tags #{:background}
                  :options [(t/option-cfg
                             {:name "Ship's Passage"
                              :modifiers [(mod5e/trait-cfg
                                           ships-passage-trait-cfg)]})
                            (t/option-cfg
                             {:name "Bad Reputation"
                              :modifiers [(mod5e/trait-cfg
                                           {:name "Bad Reputation"
                                            :page 139
                                            :summary "People in a civilized settlement are afraid of you and will let you get away with minor crimes"})]})]})]
   :custom-equipment {"Belaying Pin" 1
                      "Lucky Charm" 1}
   :treasure {:gp 10}})

#_(def soldier-bg
  {:name "Soldier"
   :help "You have spent your living by the sword."
   :traits [{:name "Military Rank"
             :page 140
             :summary "Where recognized, your previous rank provides influence among military"}]
   :profs {:skill {:athletics true :intimidation true}
           :tool {:land-vehicles true}
           :tool-options {:gaming-set 1}}
   :equipment {:clothes-common 1
               :pouch 1}
   :equipment-choices [{:name "Dice or Cards"
                        :options {:dice-set 1
                                  :playing-card-set 1}}]
   :custom-equipment {"Insignia of Rank" 1
                      "Trophy from Fallen Enemy" 1}
   :treasure {:gp 10}})

#_(def urchin-bg
  {:name "Urchin"
   :help "You were a poor orphan living on the streets."
   :traits [{:name "City Streets"
             :page 141
             :summary "You can travel twice your normal speed between city locations"}]
   :profs {:skill {:sleight-of-hand true :stealth true}
           :tool {:disguise-kit true :thieves-tools true}}
   :equipment {:knife-small 1
               :clothes-common 1
               :pouch 1}
   :custom-equipment {"Map of city you grew up in" 1
                      "Pet mouse" 1
                      "Token to remember your parents" 1}
   :treasure {:gp 10}})

#_(def backgrounds [acolyte-bg
                  #_charlatan-bg
                  #_(criminal-background "Criminal")
                  #_(criminal-background "Spy")
                  #_entertainer-bg
                  #_gladiator-bg
                  #_folk-hero-bg
                  #_guild-artisan-bg
                  #_guild-merchant-bg
                  #_hermit-bg
                  #_noble-bg
                  #_knight-bg
                  #_outlander-bg
                  #_sage-bg
                  #_sailor-bg
                  #_pirate-bg
                  #_soldier-bg
                  #_urchin-bg])

#_(def volos-guide-to-monsters-selections
  [(t/selection-cfg
    {:name "Race"
     :tags #{:race}
     :options (map
               (fn [race]
                 (opt5e/race-option (assoc race :source :vgm)))
               [aasimar-option-cfg
                firbolg-option-cfg
                (goliath-option-cfg :vgm 109)
                kenku-option-cfg
                lizardfolk-option-cfg
                tabaxi-option-cfg
                triton-option-cfg
                bugbear-option-cfg
                goblin-option-cfg
                hobgoblin-option-cfg
                kobold-option-cfg
                orc-option-cfg
                yuan-ti-option-cfg])})])

#_(def cos-backgrounds
  (map
   (partial opt5e/add-sources :cos)
   [{:name "Haunted One"
     :help "You have been subjected to an unimaginable horror"
     :profs {:skill-options {:choose 2 :options {:arcana true :investigation true :religion true :survival true}}
             :language-options {:choose 2 :options {:abyssal true :celestial true :deep-speech true :draconic true :infernal true :primordial true :sylvan true :undercommon true}}}
     :equipment {:monster-hunters-pack 1}
     :traits [{:name "Heart of Darkness"
               :page 209
               :source :cos
               :summary "Commoners do their utmost to help you, even fighting along side you"}]}]))

#_(def dmg-classes
  [{:name "Cleric"
    :plugin? true
    :subclass-level 1
    :subclass-title "Divine Domain"
    :source :dmg
    :subclasses [{:name "Death Domain"
                  :modifiers [(mod5e/al-illegal "Death Domain is not allowed")
                              (mod5e/weapon-proficiency :martial)
                              (opt5e/cleric-spell 1 :false-life 1)
                              (opt5e/cleric-spell 1 :ray-of-sickness 1)
                              (opt5e/cleric-spell 2 :blindness-deafness 3)
                              (opt5e/cleric-spell 2 :ray-of-enfeeblement 3)
                              (opt5e/cleric-spell 3 :animate-dead 5)
                              (opt5e/cleric-spell 3 :vampiric-touch 5)
                              (opt5e/cleric-spell 4 :blight 7)
                              (opt5e/cleric-spell 4 :death-ward 7)
                              (opt5e/cleric-spell 5 :antilife-shell 9)
                              (opt5e/cleric-spell 5 :cloudkill 9)
                              (mod5e/trait-cfg
                               {:name "Reaper"
                                :page 96
                                :source :dmg
                                :summary "Your necromancy cantrips that only target 1 creature can instead target two within 5 ft of each other"})]
                  :selections [(opt5e/spell-selection {:title "Necromancy Cantrip"
                                                       :spellcasting-ability ::char5e/wis
                                                       :class-name "Cleric"
                                                       :num 1
                                                       :spell-keys (map :key (filter #(and (= "necromancy" (:school %))
                                                                                           (= 0 (:level %))) spells/spells))
                                                       :exclude-ref? true})]
                  :levels {2 {:modifiers [(mod5e/action
                                           {:name "Channel Divinity: Touch of Death"
                                            :page 97
                                            :source :dmg
                                            :summary (str "when you hit with melee weapon attack, deal an extra " (+ 5 (* 2 (?class-level :cleric))) " necrotic damage")})]}
                           6 {:modifiers [(mod5e/trait-cfg
                                           {:name "Inescapable Destruction"
                                            :page 97
                                            :source :dmg
                                            :summary "Your cleric spells and Channel Divinity ignore necrotic damage resistance"})]}
                           8 {:modifiers [(opt5e/divine-strike "necrotic" 97 :dmg)]}}
                  :traits [{:level 17
                            :name "Improved Reaper"
                            :summary "If you cast a necromancy spell that targets only 1 creature, you can instead target two within 5 ft. of each other"}]}]}
   (opt5e/subclass-plugin
    opt5e/paladin-base-cfg
    :dmg
    [{:name "Oathbreaker"
      :modifiers [(mod5e/al-illegal "Oathbreaker is not allowed")
                  (opt5e/paladin-spell 1 :hellish-rebuke 3)
                  (opt5e/paladin-spell 1 :inflict-wounds 3)
                  (opt5e/paladin-spell 2 :crown-of-madness 5)
                  (opt5e/paladin-spell 2 :darkness 5)
                  (opt5e/paladin-spell 3 :animate-dead 9)
                  (opt5e/paladin-spell 3 :bestow-curse 9)
                  (opt5e/paladin-spell 4 :blight 13)
                  (opt5e/paladin-spell 4 :confusion 13)
                  (opt5e/paladin-spell 5 :contagion 17)
                  (opt5e/paladin-spell 5 :dominate-person 17)
                  (mod5e/action
                   {:name "Channel Divinity: Control Undead"
                    :source :dmg
                    :page 97
                    :summary (str "Control an undead creature of CR " (?class-level :paladin) " or less if it fails a DC " (?spell-save-dc ::char5e/cha) " WIS save")})
                  (mod5e/action
                   {:name "Channel Divinity: Dreadful Aspect"
                    :page 97
                    :source :dmg
                    :summary (str "creatures of your choice within 30 ft. must succeed on a DC " (?spell-save-dc ::char5e/cha) " WIS save or be frightened of you")})]
      :levels {7 {:modifiers [(mod5e/dependent-trait
                               {:name "Aura of Hate"
                                :page 97
                                :source :dmg
                                :summary (str "you and friends within " (if (>= (?class-level :paladin) 18) 30 10) " ft. gain a " (common/bonus-str (max 1 (?ability-bonuses ::char5e/cha))) " bonus to melee weapon attack damage")})]}
               15 {:modifiers [(mod5e/damage-resistance :bludgeoning "nonmagical weapons")
                               (mod5e/damage-resistance :piercing "nonmagical weapons")
                               (mod5e/damage-resistance :slashing "nonmagical weapons")]}
               20 {:modifiers [(mod5e/action
                                {:name "Dread Lord"
                                 :page 97
                                 :source :dmg
                                 :frequency units5e/long-rests-1
                                 :duration units5e/minutes-1
                                 :range units5e/ft-30
                                 :summary (str "create an aura that: reduces bright light to dim; frightened enemies within aura take 4d10 psychic damage; creatures that rely on sight have disadvantage on attack rolls agains you and allies within aura; as a bonus action make a melee spell attack on a creature within aura that deals 3d10 " (common/mod-str (?ability-bonuses ::char5e/cha)) "necrotic damage")})]}}}]
    true)])

#_(def elemental-evil-selections
  [(opt5e/feat-selection-2
    {:options [(opt5e/svirfneblin-magic-feat :ee 7)]})
   (opt5e/race-selection
    {:options (map
               opt5e/race-option
               [aarakocra-option-cfg
                ee-gnome-option-cfg
                genasi-option-cfg
                (goliath-option-cfg :ee 11)])})])

#_(def keen-senses-option
  (t/option-cfg
   {:name "Keen Senses"
    :modifiers [(mod5e/skill-proficiency :perception)]}))

#_(defn elf-parentage-option [name trait-options]
  (t/option-cfg
   {:name name
    :selections [(t/selection-cfg
                  {:name "Elf Trait"
                   :tags #{:race}
                   :order 3
                   :options trait-options})]}))

#_(def elf-weapon-training-option
  (t/option-cfg
   {:name "Elf Weapon Training"
    :modifiers elf-weapon-training-mods}))

#_(defn high-elf-parentage-option [name]
  (elf-parentage-option name
                        [keen-senses-option
                         elf-weapon-training-option
                         (t/option-cfg
                          {:name "High Elf Cantrip"
                           :selections [high-elf-cantrip-selection]})]))

#_(def scag-human-option-cfg
  {:name "Human"
   :plugin? true
   :subraces
   [{:name "Arkaiun"}
    {:name "Bedine"}
    {:name "Ffolk"}
    {:name "Gur"}
    {:name "Halruaan"}
    {:name "Imaskari"}
    {:name "Nar"}
    {:name "Shaaran"}
    {:name "Tuigan"}
    {:name "Ulutiun"}]})

#_(def scag-deep-gnome-cfg
  (opt5e/deep-gnome-option-cfg :deep-gnome-scag :scag 115))

#_(def scag-dwarf-option-cfg
  {:name "Dwarf",
   :plugin? true
   :subraces [{:name "Gray Dwarf (Duerger)",
               :abilities {::char5e/str 1}
               :modifiers [(mod5e/darkvision 120)
                           (mod5e/language :undercommon)
                           (mod5e/trait-cfg
                            {:name "Duergar Resilience"
                             :page 104
                             :source :scag
                             :summary "Advantage on saves against being charmed or paralyzed and against illusions."})
                           (mod5e/trait-cfg
                            {:name "Duergar Magic"
                             :page 104
                             :source :scag
                             :summary "At 3rd level, can cast enlarge/reduce (enlarge only); at 5th level can cast invisibility. Each can be cast without material components and once per long rest."})
                           (sunlight-sensitivity 104 :scag)
                           (mod5e/saving-throw-advantage [:charmed])
                           (mod5e/saving-throw-advantage [:paralyzed])
                           (mod5e/saving-throw-advantage [:illusions])
                           (mod5e/spells-known 2 :enlarge-reduce ::char5e/int "Duergar" 3 "enlarge only, once per long rest")
                           (mod5e/spells-known 2 :invisibility ::char5e/int "Duergar" 5 "once per long rest")]}]})

#_(def scag-half-elf-option-cfg
  {:name "Half-Elf"
   :plugin? true
   :selections [(t/selection-cfg
                 {:name "Half-Elf Variant"
                  :tags #{:race}
                  :order 1
                  :options [(t/option-cfg
                             {:name "Standard"
                              :help [:div "This is the standard half-elf presented in the Player's Handbook"]})
                            (t/option-cfg
                             {:name "Sword Coast Variant"
                              :help "The half-elf variants presented in the Sword Coast Adventurer's Guide"
                              :selections [(assoc
                                            (opt5e/skill-selection -2)
                                            ::t/ref
                                            [:race :half-elf :skill-proficiency])
                                           (t/selection-cfg
                                            {:name "Elf Parentage"
                                             :tags #{:race}
                                             :order 2
                                             :options [(elf-parentage-option
                                                        "Wood Elf"
                                                        [keen-senses-option
                                                         elf-weapon-training-option
                                                         (t/option-cfg
                                                          {:name "Fleet of Foot"
                                                           :modifiers [(mod5e/speed 5)]})
                                                         (t/option-cfg
                                                          {:name "Mask of the Wild"
                                                           :modifiers [mask-of-the-wild-mod]})])
                                                       (high-elf-parentage-option "Moon Elf")
                                                       (high-elf-parentage-option "Sun Elf")
                                                       (elf-parentage-option
                                                        "Drow"
                                                        [keen-senses-option
                                                         (t/option-cfg
                                                          {:name "Drow Magic"
                                                           :modifiers drow-magic-mods})])
                                                       (elf-parentage-option
                                                        "Aquatic Elf"
                                                        [keen-senses-option
                                                         (t/option-cfg
                                                          {:name "Swimming Speed"
                                                           :modifiers [(mod5e/swimming-speed 30)]})])]})]})]})]})

#_(defn tiefling-spell-removal-modifier [level spell-key]
  (mod/modifier ?spells-known
                (update
                 ?spells-known
                 level
                 dissoc
                 ["Tiefling" spell-key])))

#_(def scag-tiefling-option-cfg
  {:name "Tiefling"
   :plugin? true
   :selections [(t/selection-cfg
                 {:name "Tiefling Variant"
                  :tags #{:race}
                  :order 1
                  :options [(t/option-cfg
                             {:name "Standard"
                              :help [:div "This is the standard tiefling presented in the Player's Handbook"]})
                            (t/option-cfg
                             {:name "Sword Coast Variant"
                              :help "The tiefling variants presented in the Sword Coast Adventurer's Guide"
                              :selections [(t/selection-cfg
                                            {:name "Ability Scores"
                                             :tags #{:race}
                                             :order 2
                                             :options [(t/option-cfg
                                                        {:name "Standard"
                                                         :help "The 'Ability Score Increase' trait from the Player's Handbook"})
                                                       (t/option-cfg
                                                        {:name "Feral"
                                                         :help "The ability increases from the Sword Coast Adventurer's Guide"
                                                         :modifiers [(mod5e/race-ability ::char5e/dex 2)
                                                                     (mod5e/race-ability ::char5e/cha -2)]})]})
                                           (t/selection-cfg
                                            {:name "Variant Features"
                                             :tags #{:race}
                                             :order 3
                                             :options [(t/option-cfg
                                                        {:name "None"
                                                         :help "Keep the other standard tiefling traits"})
                                                       (t/option-cfg
                                                        {:name "Devil's Tongue"
                                                         :modifiers [(mod5e/spells-known 0 :vicious-mockery ::char5e/cha "Tiefling")
                                                                     (mod5e/spells-known 1 :charm-person ::char5e/cha "Tiefling" 3)
                                                                     (mod5e/spells-known 2 :enthrall ::char5e/cha "Tiefling" 5)
                                                                     (tiefling-spell-removal-modifier 0 :thaumaturgy)
                                                                     (tiefling-spell-removal-modifier 1 :hellish-rebuke)
                                                                     (tiefling-spell-removal-modifier 2 :darkness)]})
                                                       (t/option-cfg
                                                        {:name "Hellfire"
                                                         :modifiers [(mod5e/spells-known 1 :burning-hands ::char5e/cha "Tiefling" 3)
                                                                     (tiefling-spell-removal-modifier 1 :hellish-rebuke)]})
                                                       (t/option-cfg
                                                        {:name "Winged"
                                                         :modifiers [(mod5e/flying-speed-override 30)
                                                                     (al-illegal-flying-mod "Winged Tiefling Variant")
                                                                     (tiefling-spell-removal-modifier 0 :thaumaturgy)
                                                                     (tiefling-spell-removal-modifier 1 :hellish-rebuke)
                                                                     (tiefling-spell-removal-modifier 2 :darkness)]})]})]})]})]})

#_(def scag-halfling-option-cfg
  {:name "Halfling"
   :plugin? true
   :subraces
   [{:name "Ghostwise"
     :abilities {::char5e/wis 1}
     :source :scag
     :traits [{:name "Silent Speech"
               :source :scag
               :page 110
               :range units5e/ft-30
               :summary "Speak telepathically to 1 creature who understands your language"}]}]})

#_(def sword-coast-adventurers-guide-selections
  [(opt5e/feat-selection-2
    {:options [(opt5e/svirfneblin-magic-feat :scag 115)]})
   (opt5e/background-selection
    {:options (map
               opt5e/background-option
               scag/sword-coast-adventurers-guide-backgrounds)})
   (opt5e/race-selection
    {:options (map
               (fn [race] (opt5e/race-option (if (not (:plugin? race))
                                               (assoc race :source :scag)
                                               race)))
               [scag-half-elf-option-cfg
                scag-tiefling-option-cfg
                scag-halfling-option-cfg
                scag-deep-gnome-cfg
                scag-human-option-cfg
                scag-dwarf-option-cfg])})
   (opt5e/class-selection
    {:options (map
               (fn [cfg] (opt5e/class-option (assoc cfg :plugin? true :source :scag)))
               scag/scag-classes)})])

#_(def cos-selections
  [(opt5e/background-selection
    {:options (map
               opt5e/background-option
               cos-backgrounds)})])

#_(def eladrin-elf-option
  {:name "Elf"
   :source :dmg
   :plugin? true
   :subraces
   [{:name "Eladrin Elf"
     :source :dmg
     :abilities {::char5e/int 1}
     :modifiers [elf-weapon-training-mods
                 (mod5e/spells-known 2 :misty-step ::char5e/int "Eladrin Elf" 1 "Once per rest")]}]})

#_(def dmg-selections
  [(opt5e/class-selection
    {:options (map
               (fn [cfg] (opt5e/class-option (assoc cfg :plugin? true :source :dmg)))
               dmg-classes)})
   (opt5e/race-selection
    {:options [(opt5e/race-option eladrin-elf-option)]})])

(defn ability-item [name abbr desc]
  [:li.m-t-5 [:span.f-w-b.m-r-5 (str name " (" abbr ")")] desc])

(defn inventory-help [description page source]
  [:div
   [:div description]
   #_(if page (let [{:keys [abbr url]} (disp5e/get-source source)]
              [:span
               [:span "see"]
               [:a {:href url :target :_blank}
                abbr]
               [:span page]]))])

(defn magic-item-details [{:keys [key :db/id ::mi/name ::mi/description ::mi/summary ::mi/page ::mi/source]}]
  {:key (or key id)
   :name name
   :description (or description summary)
   :page page
   :source source})

(defn magic-item-selection [item-type-name icon options modifier-fn & [converter]]
  (t/selection-cfg
   {:name item-type-name
    :min 0
    :max nil
    :multiselect? true
    :sequential? false
    :icon icon
    :new-item-fn (fn [selection selected-items _ key]
                   {::entity/key key
                    ::entity/value 1})
    :tags #{:equipment}
    :options options}))

(defn inventory-selection [item-type-name icon items modifier-fn & [converter]]
  (t/selection-cfg
   {:name item-type-name
    :min 0
    :max nil
    :multiselect? true
    :sequential? false
    :icon icon
    :new-item-fn (fn [selection selected-items _ key]
                   {::entity/key key
                    ::entity/value 1})
    :tags #{:equipment}
    :options (map
              (fn [item]
                (let [{:keys [name key description page source]} (if converter (converter item) item)]
                  (t/option-cfg
                   {:name name
                    :key key
                    :help (if (or description
                                  page)
                            (inventory-help description page source))
                    :modifiers [(modifier-fn key item)]})))
              items)}))

(defn amazon-frame [link]
  [:iframe {:style {:width "120px" :height "240px"}
            :margin-width 0
            :margin-height 0
            :scrolling :no
            :frame-border 0
            :src link}])

(defn content-list [options]
  [:ul.m-t-5
   (doall
    (map
     (fn [nm]
       ^{:key nm}
       [:li.p-2 (str common/dot-char " " nm)])
     options))])

#_(def scag-amazon-frame
  (amazon-frame "//ws-na.amazon-adsystem.com/widgets/q?ServiceVersion=20070822&OneJS=1&Operation=GetAdHtml&MarketPlace=US&source=ac&ref=tf_til&ad_type=product_link&tracking_id=orcpub-20&marketplace=amazon&region=US&placement=0786965800&asins=0786965800&linkId=f35402a86dd0851190d952228fab36e9&show_border=false&link_opens_in_new_window=true&price_color=ffffff&title_color=f0a100&bg_color=2c3445"))

#_(def volos-amazon-frame
  (amazon-frame "//ws-na.amazon-adsystem.com/widgets/q?ServiceVersion=20070822&OneJS=1&Operation=GetAdHtml&MarketPlace=US&source=ac&ref=tf_til&ad_type=product_link&tracking_id=orcpub-20&marketplace=amazon&region=US&placement=0786966017&asins=0786966017&linkId=8c552e7b980d7d944bd12dec57e002e8&show_border=false&link_opens_in_new_window=true&price_color=ffffff&title_color=f0a100&bg_color=2c3445"))

(def phb-amazon-frame
  (amazon-frame "//ws-na.amazon-adsystem.com/widgets/q?ServiceVersion=20070822&OneJS=1&Operation=GetAdHtml&MarketPlace=US&source=ac&ref=qf_sp_asin_til&ad_type=product_link&tracking_id=orcpub-20&marketplace=amazon&region=US&placement=0786965606&asins=0786965606&linkId=3b5b686390559c31dbc3c20d20f37ec4&show_border=false&link_opens_in_new_window=true&price_color=ffffff&title_color=f0a100&bg_color=2c3445"))

#_(def dmg-amazon-frame
  (amazon-frame "//ws-na.amazon-adsystem.com/widgets/q?ServiceVersion=20070822&OneJS=1&Operation=GetAdHtml&MarketPlace=US&source=ac&ref=tf_til&ad_type=product_link&tracking_id=orcpub-20&marketplace=amazon&region=US&placement=0786965622&asins=0786965622&linkId=01922a9aafc4ea52eb90aed12bbeac04&show_border=false&link_opens_in_new_window=true&price_color=ffffff&title_color=f0a100&bg_color=2c3445"))

#_(def cos-amazon-frame
  (amazon-frame "//ws-na.amazon-adsystem.com/widgets/q?ServiceVersion=20070822&OneJS=1&Operation=GetAdHtml&MarketPlace=US&source=ac&ref=qf_sp_asin_til&ad_type=product_link&tracking_id=orcpub-20&marketplace=amazon&region=US&placement=0786965983&asins=0786965983&linkId=91dfcae14b0c8ecd3795eaf375104ca5&show_border=false&link_opens_in_new_window=true&price_color=ffffff&title_color=f0a100&bg_color=2c3445"))

(defn amazon-frame-help [frame content]
  [:div.flex.m-t-10
   [:div.flex-grow-1.p-r-5
    content]
   frame])

(def srd-url "/SRD-OGL_V5.1.pdf")

(def srd-link
  [:a.orange {:href srd-url :target "_blank"} "the 5e SRD"])


#_(def plugins
  (map
   (fn [{:keys [key] :as plugin}]
     (assoc plugin :url (opt5e/source-url key)))
   (concat
    [{:name "Sword Coast Adventurer's Guide"
      :key :scag
      :race-options? true
      :class-options? true
      :background-options? true
      :selections sword-coast-adventurers-guide-selections
      :help (amazon-frame-help scag-amazon-frame
                               [:span "Incudes too many new, exciting subraces, race variants, subclasses, and backgrounds to list, as well as a ton of other info to help you create in-depth characters in the Sword Coast or elsewhere."])}
     {:name "Volo's Guide to Monsters"
      :key :vgm
      :race-options? true
      :selections volos-guide-to-monsters-selections
      :help (amazon-frame-help volos-amazon-frame
                               [:div
                                "Full of great monster race options, including"
                                (content-list ["Aasimar" "Firbolg" "Goliath" "Kenku" "Lizardfolk" "Tabaxi" "Triton" "Bugbear" "Goblin" "Hobgoblin" "Kobold" "Orc" "Yuan-Ti Pureblood"])])}
     {:name "Elemental Evil Player's Companion"
      :key :ee
      :race-options? true
      :class-options? true
      :spell-options? true
      :selections elemental-evil-selections
      :help [:div "Race and spell options from the " [:a {:href "https://media.wizards.com/2015/downloads/dnd/EE_PlayersCompanion.pdf" :target :_blank} "player's companion to Prince's of the Apocalypse"]]}
     {:name "Dungeon Master's Guide"
      :key :dmg
      :class-options? true
      :race-options? true
      :selections dmg-selections
      :help (amazon-frame-help dmg-amazon-frame
                               [:span "Includes Eladrin Elf and villainous class options, including Cleric: Death Domain and Paladin: Oathbreaker, neither of which are Adventurer's League legal."])}
     {:name "Curse of Strahd"
      :background-options? true
      :key :cos
      :selections cos-selections
      :help (amazon-frame-help cos-amazon-frame
                               [:span "Includes the Haunted One background"])}]
    ua/ua-plugins)))

#_(def homebrew-plugin
  (t/option-cfg
   {:name "Homebrew"
    :key :homebrew
    :icon "beer-stein"
    :modifiers [opt5e/homebrew-al-illegal
                (mod/set-mod ?option-sources :homebrew)]
    :selections [opt5e/homebrew-tool-prof-selection
                 opt5e/homebrew-skill-prof-selection
                 (opt5e/ability-increase-selection-2
                  {:min 0})
                 opt5e/homebrew-feat-selection
                 opt5e/homebrew-spell-selection]
    :help "This removes all restrictions and allows you to build your character however you want. Homebrew is not legal in the Adventurer's League."}))

#_(def optional-content-selection
  (t/selection-cfg
   {:name "Optional Content"
    :tags #{:optional-content}
    :help [:span
           "Base options are from the from the "
           srd-link
           " or are OrcPub summaries."] #_(amazon-frame-help phb-amazon-frame
                             [:span
                              "Base options are from the from the "
                              srd-link
                              " or are OrcPub summaries."])
    :options [homebrew-plugin] #_(conj
                   (map
                    #(t/option-cfg
                      (merge-with
                       concat
                       {:modifiers [(mod/set-mod ?option-sources (:key %))]}
                       ;; don't want selections to show up
                       (select-keys % [:name :key :help :icon :modifiers])))
                    plugins)
                   homebrew-plugin)
    :multiselect? true
    :min 0
    :max nil}))


#_(def plugin-map
  {:homebrew {:name "Homebrew"}}
  #_(merge
   {:homebrew {:name "Homebrew"}}
   (into {} (map (juxt :key identity)
                 plugins))))

(defn al-illegal-abilities-mod [reason]
  (mod5e/al-illegal (str reason " The only legal options are 'Point Buy' or 'Standard Scores'")))

(def ability-scores-help
  [:div
   [:p "Ability scores are your major character traits and affect nearly all aspects of play. These scores range from 1 to 20 for player characters and DO NOT include racial or other bonuses."]
   [:ul.m-t-10.m-l-5
    (ability-item "Strength" "STR" "measures your physical power")
    (ability-item "Dexterity" "DEX" "measures your agility and nimbleness")
    (ability-item "Constitution" "CON" "measures your physical health")
    (ability-item "Intelligence" "INT" "measures your memory and reasoning abilities")
    (ability-item "Wisdom" "WIS" "measures your connection to your environment, how observant you are.")
    (ability-item "Charisma" "CHA" "measures how well you interact with others.")]])

(def point-buy-help
  [:div
   [:div "This allows you fine-grained control to customize your scores. You get 27 points to assign to the different scores, each value has a cost determined by the table below:"]
   [:table.m-t-10
    [:thead
     [:tr.f-w-b
      [:th.p-r-5 "Score"]
      [:th.p-r-10 "Cost"]
      [:th.p-r-5 "Score"]
      [:th "Cost"]]]
    [:tbody
     [:tr
      [:td 8]
      [:td 0]
      [:td 12]
      [:td 4]]
     [:tr
      [:td 9]
      [:td 1]
      [:td 13]
      [:td 5]]
     [:tr
      [:td 10]
      [:td 2]
      [:td 14]
      [:td 7]]
     [:tr
      [:td 11]
      [:td 3]
      [:td 15]
      [:td 9]]]]])

(defn custom-race-builder []
  [:div.m-t-10
   [:span "Name"]
   [:input.input
    {:value @(subscribe [:custom-race-name])
     :on-change (fn [e] (dispatch [:set-custom-race (.. e -target -value)]))}]])

(defn template-selections [magic-weapon-options
                           magic-armor-options
                           other-magic-item-options
                           weapon-map
                           custom-and-standard-weapons
                           spell-lists
                           spells-map
                           backgrounds
                           races
                           classes
                           feats
                           language-map]
  [#_optional-content-selection
   (t/selection-cfg
    {:name "Base Ability Scores"
     :key :ability-scores
     :require-value? true
     :order 0
     :tags #{:ability-scores}
     :help ability-scores-help
     :options [{::t/name "Manual Entry"
                ::t/key :manual-entry
                ::t/help "This option allows you to manually type in the value for each ability. Use this if you want to roll dice yourself or if you already have a character with known ability values."
                ::t/modifiers [(mod5e/deferred-abilities)
                               (al-illegal-abilities-mod "You may not choose 'Manual Entry' for abilities.")]}
               {::t/name "Point Buy"
                ::t/key :point-buy
                ::t/help point-buy-help
                ::t/modifiers [(mod5e/deferred-abilities)]}
               {::t/name "Standard Roll"
                ::t/key :standard-roll
                ::t/help "This option rolls the dice for you. You can rearrange the values using the left and right arrow buttons."
                ::t/modifiers [(mod5e/deferred-abilities)
                               (al-illegal-abilities-mod "You may not choose 'Standard Roll' for your abilities.")]}
               {::t/name "Standard Scores"
                ::t/key :standard-scores
                ::t/help "If you aren't feeling lucky, use this option, which gives you a standard set of scores. You can reassign the values using the left and right arrow buttons."
                ::t/modifiers [(mod5e/deferred-abilities)]}]})
   (t/selection-cfg
    {:name "Alignment"
     :tags #{:description :background}
     :options (map
               (fn [alignment]
                 (t/option-cfg
                  {:name alignment
                   :modifiers [(mod5e/alignment alignment)]}))
               opt5e/alignment-titles)})
   (opt5e/race-selection
    {:options (conj
               (map
                (partial opt5e/race-option spell-lists spells-map language-map weapon-map)
                races)
               (opt5e/custom-race-option spell-lists spells-map language-map weapon-map))})
   (opt5e/background-selection
    {:help "Background broadly describes your character origin. It also affords you two skill proficiencies and possibly proficiencies with tools or languages."
     :options (conj
               (map
                (partial opt5e/background-option language-map weapon-map)
                backgrounds)
               (opt5e/custom-background-option language-map))})
   (opt5e/feat-selection-2
    {:options (concat
               (opt5e/feat-options spell-lists spells-map)
               (map
                (partial opt5e/feat-option-from-cfg language-map spells-map spell-lists custom-and-standard-weapons)
                feats))
     :show-if-zero? true
     :min 0
     :max 0})
   (opt5e/class-selection
    {:help [:div
            [:p "Class is your adventuring vocation. It determines many of your special talents, including weapon, armor, skill, saving throw, and tool proficiencies. It also provides starting equipment options. When you gain levels, you gain them in a particular class."]
            [:p.m-t-10 "Select your class using the selector at the top of the 'Class' section. Multiclassing is uncommon, but you may multiclass by clicking the 'Add Levels in Another Class' button at the end of the 'Class' section."]]
     :max nil
     :sequential? false
     :options classes})
   (inventory-selection "Treasure" "cash" equip5e/treasure mod5e/deferred-treasure)
   (inventory-selection "Weapons" "plain-dagger" weapon5e/weapons mod5e/deferred-weapon)
   (magic-item-selection "Magic Weapons" "lightning-bow" magic-weapon-options mod5e/deferred-magic-weapon magic-item-details)
   (inventory-selection "Armor" "breastplate" armor5e/armor mod5e/deferred-armor)
   (magic-item-selection "Magic Armor" "magic-shield" magic-armor-options mod5e/deferred-magic-armor magic-item-details)
   (inventory-selection "Equipment" "backpack" equip5e/equipment mod5e/deferred-equipment)
   (magic-item-selection "Other Magic Items" "orb-wand" other-magic-item-options mod5e/deferred-magic-item magic-item-details)])

(defn template [selections]
  {::t/base t-base/template-base
   ::t/selections selections})
