(ns orcpub.dnd.e5.spell-lists)


#_(def ua-starter-spells-lists
  {:bard
   {1 [:guiding-hand
       :puppet
       :sense-emotion
       :sudden-awakening
       :unearthly-chorus]}
   :cleric
   {0 [:hand-of-radiance
       :toll-the-dead
       :virtue]
    1 [:ceremony
       :guiding-hand]}
   :druid
   {0 [:infestation
       :primal-savagery]
    1 [:guiding-hand
       :snare
       :wild-cunning]}
   :paladin {1 [:ceremony]}
   :ranger {1 [:snare
               :sudden-awakening
               :wild-cunning
               :zephyr-strike]}
   :sorcerer
   {0 [:infestation]
    1 [:chaos-bolt
       :sudden-awakening]}
   :warlock
   {0 [:infestation
       :toll-the-dead]
    1 [:cause-fear
       :healing-elixir
       :puppet
       :sense-emotion]}
   :wizard
   {0 [:infestation
       :toll-the-dead]
    1 [:cause-fear
       :guiding-hand
       :healing-elixir
       :puppet
       :sense-emotion
       :snare
       :sudden-awakening]}})

#_(def ee-spell-lists
  {:bard
   {0
    [:thunderclap]
    1
    [:earth-tremor]
    2
    [:pyrotechnics :skywrite :warding-wind]}
   
   :druid
   {0
    [:create-bonfire
     :control-flames
     :frostbite
     :gust
     :magic-stone
     :mold-earth
     :shape-water
     :thunderclap]
    1
    [:absorb-elements
     #_:beast-bond
     :ice-knife
     :earth-tremor]
    2
    [:dust-devil
     :earthbind
     :skywrite
     :warding-wind]
    3
    [:erupting-earth
     :flame-arrows
     :tidal-wave
     :wall-of-water]
    4
    [:elemental-bane
     :watery-sphere]
    5
    [:control-winds
     :maelstrom
     :transmute-rock]
    6
    [:bones-of-the-earth
     :investiture-of-flame
     :investiture-of-ice
     :investiture-of-stone
     :investiture-of-wind
     :primordial-ward]
    7
    [:whirlwind]}

   :ranger
   {1
    [:absorb-elements #_:beast-bond]
    3
    [:flame-arrows]}

   :sorcerer
   {0
    [:create-bonfire
     :control-flames
     :frostbite
     :gust
     :mold-earth
     :shape-water
     :thunderclap]
    1
    [:catapult
     :ice-knife
     :earth-tremor]
    2
    [:aganazzars-scorcher
     :dust-devil
     :earthbind
     :maximilians-earthen-grasp
     :pyrotechnics
     :snillocs-snowball-swarm
     :warding-wind]
    3
    [:erupting-earth
     :flame-arrows
     :melfs-minute-meteors
     :wall-of-water]
    4
    [:storm-sphere
     :vitriolic-sphere
     :watery-sphere]
    5
    [:control-winds
     :immolation]
    6
    [:investiture-of-flame
     :investiture-of-ice
     :investiture-of-stone
     :investiture-of-wind]
    8
    [#_:abi-dalzims-horrid-wilting]}

   :warlock
   {0
    [:create-bonfire
     :frostbite
     :magic-stone
     :thunderclap]
    2
    [:earthbind]
    4
    [:elemental-bane]
    6
    [:investiture-of-flame
     :investiture-of-ice
     :investiture-of-stone
     :investiture-of-wind]}

   :wizard
   {0
    [:create-bonfire
     :control-flames
     :frostbite
     :gust
     :mold-earth
     :shape-water
     :thunderclap]
    1
    [:absorb-elements
     :catapult
     :ice-knife
     :earth-tremor]
    2
    [:aganazzars-scorcher
     :dust-devil
     :earthbind
     :maximilians-earthen-grasp
     :pyrotechnics
     :skywrite
     :snillocs-snowball-swarm]
    3
    [:erupting-earth
     :flame-arrows
     :melfs-minute-meteors
     :tidal-wave
     :wall-of-sand
     :wall-of-water]
    4
    [:elemental-bane
     :storm-sphere
     :vitriolic-sphere
     :watery-sphere]
    5
    [:control-winds
     :immolation
     :transmute-rock]
    6
    [:investiture-of-flame
     :investiture-of-ice
     :investiture-of-stone
     :investiture-of-wind]
    7
    [:whirlwind]
    8
    [#_:abi-dalzims-horrid-wilting]}})

(def phb-spell-lists
  {#_:artificer
   #_{1
    [:alarm	
     :cure-wounds
     :disguise-self
     :expeditious-retreat
     :false-life
     :jump
     :longstrider
     :sanctuary
     :shield-of-faith]
    2
    [:aid
     :alter-self
     :arcane-lock
     :blur
     :continual-flame
     :darkvision
     :enhance-ability
     :enlarge-reduce
     :invisibility
     :lesser-restoration
     :levitate
     :magic-weapon
     :protection-from-poison
     :rope-trick
     :see-invisibility
     :spider-climb]
    3
    [:blink
     :fly
     :gaseous-form
     :glyph-of-warding
     :haste
     :protection-from-energy
     :revivify
     :water-breathing
     :water-walk]
    4
    [:arcane-eye
     :death-ward
     :fabricate
     :freedom-of-movement
     :leomunds-secret-chest
     :mordenkainens-faithful-hound
     :mordenkainens-private-sanctum
     :otilukes-resilient-sphere
     :stone-shape
     :stoneskin]}
   :bard
   {0
    [#_:blade-ward :dancing-lights #_:friends :light :mage-hand :mending :message :minor-illusion
     :prestidigitation :true-strike :vicious-mockery]
    1
    [#_:animal-friendship :bane :charm-person :comprehend-languages :cure-wounds
     :detect-magic :disguise-self #_:dissonant-whispers :faerie-fire :feather-fall
     :healing-word :heroism :identify :illusory-script
     :longstrider :silent-image :sleep :speak-with-animals :tashas-hideous-laughter :thunderwave
     :unseen-servant
     ],
    2
    [:animal-messenger :blindness-deafness :calm-emotions
     #_:cloud-of-daggers #_:crown-of-madness :detect-thoughts :enhance-ability :enthrall :heat-metal
     :hold-person :invisibility :knock :lesser-restoration
     :locate-animals-or-plants :locate-object :magic-mouth #_:phantasmal-force
     :see-invisibility :shatter :silence :suggestion :zone-of-truth],
    3
    [:bestow-curse :clairvoyance :dispel-magic :fear #_:feign-death :glyph-of-warding
     :hypnotic-pattern :leomunds-tiny-hut :major-image :nondetection :plant-growth :sending
     :speak-with-dead :speak-with-plants :stinking-cloud
     :tongues],
    4
    [#_:compulsion :confusion :dimension-door :freedom-of-movement
     :greater-invisibility :hallucinatory-terrain :locate-creature
     :polymorph],
    5
    [:animate-objects :awaken :dominate-person :dream :geas
     :greater-restoration :hold-monster :legend-lore :mass-cure-wounds
     :mislead :modify-memory :planar-binding :raise-dead :scrying
     :seeming :teleportation-circle],
    6
    [:eyebite :find-the-path :guards-and-wards 
     :mass-suggestion :ottos-irresistible-dance :programmed-illusion :true-seeing],
    7
    [:etherealness :forcecage
     :mirage-arcane :mordenkainens-sword :mordenkainens-magnificent-mansion :project-image
     :regenerate :resurrection :symbol
     :teleport],
    8
    [:dominate-monster :feeblemind :glibness :mind-blank
     :power-word-stun],
    9 [:foresight #_:power-word-heal :power-word-kill :true-polymorph]},
   :cleric
   {0
    [:guidance :light :mending :resistance :sacred-flame :spare-the-dying :thaumaturgy],
    1
    [:bane :bless :command :create-or-destroy-water :cure-wounds
     :detect-evil-and-good :detect-magic :detect-poison-and-disease
     :guiding-bolt :healing-word :inflict-wounds
     :protection-from-evil-and-good :purify-food-and-drink :sanctuary
     :shield-of-faith],
    2
    [:aid :augury :blindness-deafness :calm-emotions :continual-flame
     :enhance-ability :find-traps :gentle-repose :hold-person
     :lesser-restoration :locate-object :prayer-of-healing
     :protection-from-poison :silence :spiritual-weapon :warding-bond
     :zone-of-truth],
    3
    [:animate-dead :beacon-of-hope :bestow-curse :clairvoyance
     :create-food-and-water :daylight :dispel-magic #_:feign-death :glyph-of-warding
     :magic-circle :mass-healing-word :meld-into-stone
     :protection-from-energy :remove-curse :revivify :sending
     :speak-with-dead :spirit-guardians :tongues :water-walk],
    4
    [:banishment :control-water :death-ward :divination
     :freedom-of-movement :guardian-of-faith :locate-creature :stone-shape],
    5
    [:commune :contagion :dispel-evil-and-good :flame-strike :geas
     :greater-restoration :hallow :insect-plague :legend-lore
     :mass-cure-wounds :planar-binding :raise-dead :scrying],
    6
    [:blade-barrier :create-undead :find-the-path :forbiddance :harm
     :heal :heroes-feast :planar-ally :true-seeing :word-of-recall],
    7
    [:conjure-celestial :divine-word :etherealness :fire-storm
     :plane-shift :regenerate :resurrection :symbol],
    8 [:antimagic-field :control-weather :earthquake :holy-aura],
    9 [:astral-projection :gate :mass-heal :true-resurrection]},
   :druid
   {0 [:druidcraft :guidance :mending :poison-spray :produce-flame :resistance :shillelagh #_:thorn-whip],
    1
    [#_:animal-friendship :charm-person :create-or-destroy-water :cure-wounds :detect-magic
     :detect-poison-and-disease :entangle :faerie-fire :fog-cloud #_:goodberry
     :healing-word :jump :longstrider :purify-food-and-drink
     :speak-with-animals :thunderwave
     ],
    2
    [:animal-messenger :barkskin #_:beast-sense :darkvision :enhance-ability
     :find-traps :flame-blade :flaming-sphere :gust-of-wind :heat-metal
     :hold-person :lesser-restoration :locate-animals-or-plants
     :locate-object :moonbeam :pass-without-trace
     :protection-from-poison :spike-growth],
    3
    [:call-lightning :conjure-animals :daylight :dispel-magic #_:feign-death
     :meld-into-stone :plant-growth :protection-from-energy :sleet-storm
     :speak-with-plants :water-breathing :water-walk :wind-wall],
    4
    [:blight :confusion :conjure-minor-elementals
     :conjure-woodland-beings :control-water :dominate-beast
     :freedom-of-movement :giant-insect #_:grasping-vine :hallucinatory-terrain
     :ice-storm :locate-creature :polymorph :stone-shape :stoneskin
     :wall-of-fire],
    5
    [:antilife-shell :awaken :commune-with-nature :conjure-elemental
     :contagion :geas :greater-restoration :insect-plague
     :mass-cure-wounds :planar-binding :reincarnate :scrying
     :tree-stride :wall-of-stone],
    6
    [:conjure-fey :find-the-path :heal :heroes-feast :move-earth
     :sunbeam :transport-via-plants :wall-of-thorns :wind-walk],
    7
    [:fire-storm :mirage-arcane :plane-shift :regenerate
     :reverse-gravity],
    8
    [:animal-shapes :antipathy-sympathy :control-weather :earthquake
     :feeblemind :sunburst :tsunami],
    9 [:foresight :shapechange :storm-of-vengeance :true-resurrection]},
   :paladin
   {1
    [:bless :command :cure-wounds :detect-evil-and-good :detect-magic
     :detect-poison-and-disease :divine-favor :heroism
     :protection-from-evil-and-good :purify-food-and-drink
     :shield-of-faith]
    2
    [:aid :branding-smite :find-steed :lesser-restoration :locate-object :magic-weapon
     :protection-from-poison :zone-of-truth]
    3
    [:create-food-and-water :daylight
     :dispel-magic :magic-circle :remove-curse :revivify]
    4 [:banishment :death-ward :locate-creature]
    5 [:dispel-evil-and-good :geas :raise-dead]}
   :ranger
   {1
    [:alarm #_:animal-friendship :cure-wounds :detect-magic :detect-poison-and-disease
     #_:ensnaring-strike :fog-cloud #_:goodberry #_:hail-of-thorns #_:hunters-mark
     :jump :longstrider :speak-with-animals
     #_:beast-bond],
    2
    [:animal-messenger :barkskin #_:beast-sense #_:cordon-of-arrows :darkvision :find-traps
     :lesser-restoration :locate-animals-or-plants :locate-object
     :pass-without-trace :protection-from-poison :silence
     :spike-growth],
    3
    [:conjure-animals #_:conjure-barrage :daylight #_:lightning-arrow :nondetection :plant-growth
     :protection-from-energy :speak-with-plants :water-breathing
     :water-walk :wind-wall ],
    4
    [:conjure-woodland-beings :freedom-of-movement #_:grasping-vine :locate-creature
     :stoneskin],
    5 [:commune-with-nature #_:conjure-volley #_:swift-quiver :tree-stride]},
   :sorcerer
   {0
    [:acid-splash #_:blade-ward #_:booming-blade :chill-touch :dancing-lights :fire-bolt
     #_:friends #_:green-flame-blade :light :mage-hand
     :mending :message :minor-illusion :poison-spray :prestidigitation :ray-of-frost
     :shocking-grasp #_:sword-burst :true-strike],
    1
    [:burning-hands :charm-person #_:chromatic-orb :color-spray :comprehend-languages
     :detect-magic :disguise-self :expeditious-retreat :false-life
     :feather-fall :fog-cloud :jump :mage-armor :magic-missile #_:ray-of-sickness :shield
     :silent-image :sleep :thunderwave #_:witch-bolt
     ],
    2
    [:alter-self :blindness-deafness :blur #_:cloud-of-daggers :darkness :darkvision
     :detect-thoughts :enhance-ability :enlarge-reduce :gust-of-wind
     :hold-person :invisibility :knock :levitate :mirror-image
     :misty-step #_:phantasmal-force :scorching-ray :see-invisibility :shatter :spider-climb
     :suggestion :web],
    3
    [:blink :clairvoyance #_:counterspell :daylight :dispel-magic :fear :fireball :fly
     :gaseous-form :haste :hypnotic-pattern :lightning-bolt :major-image
     :protection-from-energy :sleet-storm :slow :stinking-cloud :tongues
     :water-breathing :water-walk],
    4
    [:banishment :blight :confusion :dimension-door :dominate-beast
     :greater-invisibility :ice-storm :polymorph :stoneskin
     :wall-of-fire],
    5
    [:animate-objects :cloudkill :cone-of-cold :creation
     :dominate-person :hold-monster :insect-plague :seeming :telekinesis
     :teleportation-circle :wall-of-stone
     ],
    6
    [#_:arcane-gate :chain-lightning :circle-of-death :disintegrate :eyebite
     :globe-of-invulnerability :mass-suggestion :move-earth :sunbeam
     :true-seeing
     ],
    7
    [:delayed-blast-fireball :etherealness :finger-of-death :fire-storm
     :plane-shift :prismatic-spray :reverse-gravity :teleport],
    8
    [:dominate-monster :earthquake :incendiary-cloud :power-word-stun
     :sunburst #_:abi-dalzims-horrid-wilting],
    9 [:gate :meteor-swarm :power-word-kill :time-stop :wish]},
   :warlock
   {0
    [#_:blade-ward :chill-touch :eldritch-blast #_:friends :mage-hand :minor-illusion :prestidigitation
     :true-strike :poison-spray
     #_:booming-blade #_:sword-burst],
    1
    [#_:armor-of-agathys #_:arms-of-hadar :charm-person :comprehend-languages :expeditious-retreat
     :hellish-rebuke #_:hex
     :illusory-script :protection-from-evil-and-good :unseen-servant #_:witch-bolt],
    2
    [#_:cloud-of-daggers #_:crown-of-madness :darkness :enthrall :hold-person :invisibility :mirror-image
     :misty-step :ray-of-enfeeblement :shatter :spider-climb
     :suggestion
     ],
    3
    [#_:counterspell :dispel-magic :fear :fly :gaseous-form #_:hunger-of-hadar :hypnotic-pattern
     :magic-circle :major-image :remove-curse :tongues :vampiric-touch],
    4 [:banishment :blight :dimension-door :hallucinatory-terrain ],
    5 [:contact-other-plane :dream :hold-monster :scrying],
    6
    [#_:arcane-gate :circle-of-death :conjure-fey :create-undead :eyebite
     :flesh-to-stone :mass-suggestion :true-seeing
     ],
    7 [:etherealness :finger-of-death :forcecage :plane-shift],
    8
    [:demiplane :dominate-monster :feeblemind :glibness
     :power-word-stun],
    9 [:astral-projection :foresight :imprisonment :power-word-kill :true-polymorph]},
   :wizard
   {0
    [:acid-splash #_:blade-ward :chill-touch :dancing-lights :fire-bolt #_:friends :light :mage-hand
     :mending :message :minor-illusion :poison-spray :prestidigitation :ray-of-frost
     :shocking-grasp :true-strike
     #_:booming-blade #_:green-flame-blade #_:sword-burst],
    1
    [:alarm :burning-hands :charm-person #_:chromatic-orb :color-spray
     :comprehend-languages :detect-magic :disguise-self
     :expeditious-retreat :false-life :feather-fall :find-familiar
     :fog-cloud :grease :identify :illusory-script
     :jump :longstrider :mage-armor :magic-missile
     :protection-from-evil-and-good #_:ray-of-sickness :shield :silent-image :sleep
     :tashas-hideous-laughter :tensers-floating-disk :thunderwave :unseen-servant #_:witch-bolt
     ],
    2
    [:alter-self :arcane-lock
     :blindness-deafness :blur #_:cloud-of-daggers :continual-flame #_:crown-of-madness :darkness :darkvision
     :detect-thoughts :enlarge-reduce :flaming-sphere :gentle-repose
     :gust-of-wind :hold-person :invisibility :knock :levitate
     :locate-object :magic-mouth :magic-weapon :melfs-acid-arrow :mirror-image :misty-step
     :nystuls-magic-aura #_:phantasmal-force :ray-of-enfeeblement :rope-trick :scorching-ray :see-invisibility
     :shatter :spider-climb :suggestion :web],
    3
    [:animate-dead :bestow-curse :blink :clairvoyance #_:counterspell :dispel-magic
     :fear #_:feign-death :fireball :fly :gaseous-form :glyph-of-warding :haste
     :hypnotic-pattern :leomunds-tiny-hut :lightning-bolt :magic-circle :major-image
     :nondetection :phantom-steed :protection-from-energy :remove-curse
     :sending :sleet-storm :slow :stinking-cloud :tongues
     :vampiric-touch :water-breathing],
    4
    [:arcane-eye :banishment :blight :confusion
     :conjure-minor-elementals :control-water :dimension-door :evards-black-tentacles :fabricate
     :fire-shield :greater-invisibility
     :hallucinatory-terrain :ice-storm :leomunds-secret-chest :locate-creature
     :mordenkainens-faithful-hound :mordenkainens-private-sanctum :otilukes-resilient-sphere
     :phantasmal-killer :polymorph
     :stone-shape :stoneskin :wall-of-fire],
    5
    [:animate-objects :bigbys-hand :cloudkill :cone-of-cold
     :conjure-elemental :contact-other-plane :creation :dominate-person
     :dream :geas :hold-monster :legend-lore :mislead :modify-memory
     :passwall :planar-binding :scrying :seeming :telekinesis
     :teleportation-circle :wall-of-force :wall-of-stone],
    6
    [#_:arcane-gate :chain-lightning :circle-of-death :contingency :create-undead
     :disintegrate :drawmijs-instant-summons :eyebite :flesh-to-stone
     :globe-of-invulnerability :guards-and-wards
     :magic-jar :mass-suggestion :move-earth :otilukes-freezing-sphere :ottos-irresistible-dance
     :programmed-illusion :sunbeam :true-seeing :wall-of-ice
     ],
    7
    [:delayed-blast-fireball :etherealness
     :finger-of-death :forcecage :mirage-arcane :mordenkainens-magnificent-mansion
     :mordenkainens-sword :plane-shift :prismatic-spray :project-image :reverse-gravity
     :sequester :simulacrum :symbol :teleport],
    8
    [:antimagic-field :antipathy-sympathy :clone :control-weather
     :demiplane :dominate-monster :feeblemind :incendiary-cloud :maze
     :mind-blank :power-word-stun :sunburst #_:telepathy],
    9
    [:astral-projection :foresight :gate :imprisonment :meteor-swarm
     :power-word-kill :prismatic-wall :shapechange :time-stop
     :true-polymorph :weird :wish]}})

(defn merge-class-lists [class-list-1 class-list-2]
  (merge-with
   concat
   class-list-1
   class-list-2))

(def spell-lists
  phb-spell-lists
  #_(merge-with
   merge-class-lists
   phb-spell-lists
   ee-spell-lists
   ua-starter-spells-lists))
