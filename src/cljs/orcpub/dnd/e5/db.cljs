(ns orcpub.dnd.e5.db
  (:require [orcpub.route-map :as route-map]
            [orcpub.user-agent :as user-agent]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.backgrounds :as bg5e]
            [orcpub.dnd.e5.languages :as langs5e]
            [orcpub.dnd.e5.feats :as feats5e]
            [orcpub.dnd.e5.races :as race5e]
            [orcpub.dnd.e5.classes :as class5e]
            [orcpub.dnd.e5.magic-items :as mi5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.monsters :as monsters5e]
            [orcpub.dnd.e5.encounters :as encounters5e]
            [orcpub.dnd.e5.combat :as combat5e]
            [orcpub.dnd.e5.equipment :as equip5e]
            [orcpub.dnd.e5.selections :as selections5e]
            [re-frame.core :as re-frame]
            [orcpub.entity :as entity]
            [orcpub.entity.strict :as se]
            [cljs.spec.alpha :as spec]
            [cljs.reader :as reader]
            [bidi.bidi :as bidi]
            [cljs-http.client :as http]
            [cljs.pprint :refer [pprint]]))

(def local-storage-character-key "character")
(def local-storage-user-key "user")
(def local-storage-magic-item-key "magic-item")
(def local-storage-spell-key "spell")
(def local-storage-monster-key "monster")
(def local-storage-encounter-key "encounter")
(def local-storage-combat-key "combat")
(def local-storage-background-key "background")
(def local-storage-language-key "language")
(def local-storage-metamagic-key "metamagic")
(def local-storage-invocation-key "invocation")
(def local-storage-boon-key "boon")
(def local-storage-selection-key "selection")
(def local-storage-feat-key "feat")
(def local-storage-race-key "race")
(def local-storage-subrace-key "subrace")
(def local-storage-subclass-key "subclass")
(def local-storage-class-key "class")
(def local-storage-plugins-key "plugins")

(def default-route route-map/dnd-e5-char-builder-route)

(defn parse-route []
  (let [route (if js/window.location
                (bidi/match-route route-map/routes js/window.location.pathname))]
    (if route
      route
      default-route)))

(def default-character (char5e/set-class t5e/character :barbarian 0 (class5e/barbarian-option nil nil nil nil nil)))

(def default-spell {:level 0
                    :school "abjuration"
                    :spell-lists {:bard true
                                  :cleric true
                                  :druid true
                                  :paladin true
                                  :ranger true
                                  :sorcerer true
                                  :warlock true
                                  :wizard true}})

(def default-monster {:size :large
                      :type :aberration
                      :alignment "neutral"
                      :armor-class 10
                      :str 10
                      :dex 10
                      :con 10
                      :int 10
                      :wis 10
                      :cha 10})

(def default-encounter {:creatures []})

(def default-combat {:parties []
                     :encounters []
                     :characters []
                     :monsters []})

(def default-background {:traits []})

(def default-language {})

(def default-metamagic {})

(def default-invocation {})

(def default-boon {})

(def default-selection {:options []})


(def default-feat {:ability-increases #{}
                   :prereqs #{}})

(def default-race {:size :medium
                   :speed 30
                   :languages #{}
                   :traits []})

(def default-subrace {:race :dwarf
                      :traits []})

(def default-subclass {:class :barbarian
                       :traits []
                       :level-modifiers []})

(def default-class {:hit-die 6
                    :ability-increase-levels [4 8 12 16 19]
                    :traits []
                    :level-modifiers []})

(def default-value
  {:builder {:character {:tab #{:build :options}}}
   :character default-character
   :template t5e/template
   :plugins {"Default Option Source" {}}
   :locked-components #{}
   :route (parse-route)
   :route-history (list default-route)
   :return-route default-route
   :registration-form {:send-updates? false}
   :device-type (user-agent/device-type)
   ::spells5e/builder-item default-spell
   ::monsters5e/builder-item default-monster
   ::encounters5e/builder-item default-encounter
   ::combat5e/tracker-item default-combat
   ::bg5e/builder-item default-background
   ::langs5e/builder-item default-language
   ::class5e/metamagic-builder-item default-metamagic
   ::class5e/invocation-builder-item default-invocation
   ::class5e/boon-builder-item default-boon
   ::selections5e/builder-item default-selection
   ::feats5e/builder-item default-feat
   ::race5e/builder-item default-race
   ::race5e/subrace-builder-item default-subrace
   ::class5e/builder-item default-class
   ::class5e/subclass-builder-item default-subclass
   ::char5e/newb-char-data {:answers {}
                            :tags #{}}})

(defn set-item [key value]
  (try
    (.setItem js/window.localStorage key value)
    (catch js/Object e (prn "FAILED SETTING LOCALSTORAGE ITEM"))))

(defn character->local-store [character]
  (if js/window.localStorage
    (set-item local-storage-character-key
              (str (assoc (char5e/to-strict character)
                          :changed
                          (:changed character))))))

(defn user->local-store [user-data]
  (if js/window.localStorage
    (set-item local-storage-user-key (str user-data))))

(defn magic-item->local-store [magic-item]
  (if js/window.localStorage
    (set-item local-storage-magic-item-key (str magic-item))))

(defn spell->local-store [spell]
  (if js/window.localStorage
    (set-item local-storage-spell-key (str spell))))

(defn monster->local-store [monster]
  (if js/window.localStorage
    (set-item local-storage-monster-key (str monster))))

(defn encounter->local-store [encounter]
  (if js/window.localStorage
    (set-item local-storage-encounter-key (str encounter))))

(defn combat->local-store [combat]
  (if js/window.localStorage
    (set-item local-storage-combat-key (str combat))))

(defn background->local-store [background]
  (if js/window.localStorage
    (set-item local-storage-background-key (str background))))

(defn language->local-store [language]
  (if js/window.localStorage
    (set-item local-storage-language-key (str language))))

(defn invocation->local-store [invocation]
  (if js/window.localStorage
    (set-item local-storage-invocation-key (str invocation))))

(defn metamagic->local-store [metamagic]
  (if js/window.localStorage
    (set-item local-storage-metamagic-key (str metamagic))))

(defn boon->local-store [boon]
  (if js/window.localStorage
    (set-item local-storage-boon-key (str boon))))

(defn selection->local-store [selection]
  (if js/window.localStorage
    (set-item local-storage-selection-key (str selection))))

(defn feat->local-store [feat]
  (if js/window.localStorage
    (set-item local-storage-feat-key (str feat))))

(defn race->local-store [race]
  (if js/window.localStorage
    (set-item local-storage-race-key (str race))))

(defn subrace->local-store [subrace]
  (if js/window.localStorage
    (set-item local-storage-subrace-key (str subrace))))

(defn subclass->local-store [subclass]
  (if js/window.localStorage
    (set-item local-storage-subclass-key (str subclass))))

(defn class->local-store [class]
  (if js/window.localStorage
    (set-item local-storage-class-key (str class))))

(defn plugins->local-store [plugins]
  (if js/window.localStorage
    (set-item local-storage-plugins-key (str plugins))))

(def tab-path [:builder :character :tab])

(defn get-local-storage-item [local-storage-key]
  (if-let [stored-str (if js/window.localStorage
                        (.getItem js/window.localStorage local-storage-key))]
    (try (reader/read-string stored-str)
         (catch js/Object e (do (prn "E" e)
                                (js/console.warn "UNREADABLE ITEM FOUND, REMOVING.." local-storage-key stored-str)
                                (.removeItem js/window.localStorage local-storage-key))))))

(defn reg-local-store-cofx [key local-storage-key item-spec & [item-fn]]
  (re-frame/reg-cofx
   key
   (fn [cofx _]
     (assoc cofx
            key
            (if-let [stored-item (get-local-storage-item local-storage-key)]
              (if (spec/valid? item-spec stored-item)
                (if item-fn
                  (item-fn stored-item)
                  stored-item)
                (do
                  (js/console.warn "INVALID ITEM FOUND, IGNORING")
                  (pprint (spec/explain-data item-spec stored-item)))))))))

(reg-local-store-cofx
 :local-store-character
 local-storage-character-key
 ::se/entity
 (fn [char]
   (assoc
    (char5e/from-strict char)
    :changed
    (:changed char))))

(spec/def ::username string?)
(spec/def ::email string?)
(spec/def ::token string?)
(spec/def ::theme string?)
(spec/def ::user-data (spec/keys :req-un [::username ::email]))
(spec/def ::user (spec/keys :opt-un [::user-data ::token ::theme]))

(reg-local-store-cofx
 :local-store-user
 local-storage-user-key
 ::user)

(reg-local-store-cofx
 :local-store-magic-item
 local-storage-magic-item-key
 ::mi5e/internal-magic-item)

(def musical-instrument-choice-cfg
  {:name "Musical Instrument"
   :options (zipmap (map :key equip5e/musical-instruments) (repeat 1))})

(reg-local-store-cofx
 ::e5/plugins
 local-storage-plugins-key
 ::e5/plugins)

(reg-local-store-cofx
 ::combat5e/tracker-item
 local-storage-combat-key
 ::combat5e/combat)

