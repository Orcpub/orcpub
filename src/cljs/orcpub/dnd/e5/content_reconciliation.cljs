(ns orcpub.dnd.e5.content-reconciliation
  "Detects missing content references in characters and suggests fixes.

   When a character references homebrew content (classes, races, etc.) that
   isn't currently loaded, this module helps identify what's missing and
   suggests similar content that might be a match.

   Extracts content keys directly from the entity options structure using
   the same get-in patterns the rest of the app uses, rather than walking
   the entire options tree generically."
  (:require [clojure.string :as str]
            [orcpub.entity :as entity]
            [orcpub.common :as common]))

;; ============================================================================
;; Content Type Definitions
;; ============================================================================

(def subclass-selection-keys
  "Subclass selection keys vary by class. Each class names its archetype
   selection differently (e.g. Fighter uses :martial-archetype, Rogue uses
   :roguish-archetype). This set covers all known variants including homebrew."
  #{:martial-archetype :roguish-archetype :sorcerous-origin
    :otherworldly-patron :arcane-tradition :bardic-college
    :divine-domain :druid-circle :monastic-tradition
    :sacred-oath :ranger-archetype :primal-path
    :artificer-specialist :artificer-specialization
    :blood-hunter-order})

(def content-type->field
  "Maps content type keywords to their field names in available-content."
  {:class :classes
   :subclass :subclasses
   :race :races
   :subrace :subraces
   :background :backgrounds
   :feat :feats})

;; ============================================================================
;; Key Extraction — direct get-in on entity options
;; ============================================================================

(defn- extract-race-keys
  "Extract race and subrace keys from a character."
  [options]
  (let [race-opt (get options :race)]
    (cond-> []
      (::entity/key race-opt)
      (conj {:key (::entity/key race-opt) :content-type :race :content-label "Race"})

      (get-in race-opt [::entity/options :subrace ::entity/key])
      (conj {:key (get-in race-opt [::entity/options :subrace ::entity/key])
             :content-type :subrace :content-label "Subrace"}))))

(defn- extract-background-key
  "Extract background key from a character."
  [options]
  (when-let [k (get-in options [:background ::entity/key])]
    [{:key k :content-type :background :content-label "Background"}]))

(defn- extract-class-keys
  "Extract class and subclass keys from a character.
   Each class entry may have a subclass under a class-specific selection key
   (e.g. :martial-archetype for Fighter, :sacred-oath for Paladin)."
  [options]
  (let [classes (get options :class)]
    (when (sequential? classes)
      (mapcat
       (fn [class-opt]
         (let [class-key (::entity/key class-opt)
               class-opts (::entity/options class-opt)
               ;; Find the subclass by checking each known archetype selection key
               subclass-key (some (fn [sel-key]
                                    (get-in class-opts [sel-key ::entity/key]))
                                  subclass-selection-keys)]
           (cond-> []
             class-key
             (conj {:key class-key :content-type :class :content-label "Class"})

             subclass-key
             (conj {:key subclass-key :content-type :subclass :content-label "Subclass"}))))
       classes))))

(defn- extract-feat-keys
  "Extract feat keys from the top-level :feats selection.
   Only extracts direct children — nested sub-selections (ability scores,
   language choices, etc.) under a feat are not content references."
  [options]
  (let [feats (get options :feats)]
    (when (sequential? feats)
      (keep (fn [feat-opt]
              (when-let [k (::entity/key feat-opt)]
                {:key k :content-type :feat :content-label "Feat"}))
            feats))))

(defn extract-content-keys
  "Extract all content keys from a character's options.
   Returns a seq of {:key :keyword :content-type :type :content-label \"Label\"}."
  [character]
  (let [options (::entity/options character)]
    (concat
     (extract-race-keys options)
     (extract-background-key options)
     (extract-class-keys options)
     (extract-feat-keys options))))

;; ============================================================================
;; Content Availability Checking
;; ============================================================================

(defn- key-similarity
  "Calculate similarity between two keywords (0-1 scale).
   Uses prefix matching and common-base comparison."
  [k1 k2]
  (let [s1 (name k1)
        s2 (name k2)
        exact (if (= s1 s2) 1.0 0.0)
        prefix (if (or (str/starts-with? s1 s2)
                       (str/starts-with? s2 s1))
                 0.7 0.0)
        base-match (if (= (common/kw-base k1) (common/kw-base k2)) 0.8 0.0)]
    (max exact prefix base-match)))

(defn- infer-source-from-key
  "Try to infer the source name from a key's suffix.
   E.g., :artificer-kibbles-tasty → \"Kibbles Tasty\""
  [key]
  (let [parts (str/split (name key) #"-")]
    (when (> (count parts) 1)
      (str/join " " (map str/capitalize (rest parts))))))

(defn find-similar-content
  "Find content similar to a missing key.
   Returns seq of {:key :name :source :similarity} sorted by similarity."
  [missing-key content-type available-content]
  (let [inferred-source (infer-source-from-key missing-key)
        missing-base (common/kw-base missing-key)]
    (->> available-content
         (filter #(and (map? %) (keyword? (:key %))))
         (map (fn [{:keys [key] :as content}]
                (let [similarity (key-similarity missing-key key)
                      content-name (:name content)
                      name-match? (and (string? content-name)
                                       (not (str/blank? content-name))
                                       (= (str/lower-case missing-base)
                                          (common/kw-base (common/name-to-kw content-name))))]
                  (assoc content
                         :similarity (if name-match?
                                       (max similarity 0.6)
                                       similarity)
                         :inferred-source inferred-source))))
         (filter #(> (:similarity %) 0.3))
         (sort-by :similarity >)
         (take 5))))

;; ============================================================================
;; Built-in (SRD) Content — excluded from missing-content warnings
;; ============================================================================

;; Only SRD content belongs here. Non-SRD PHB content (Battle Master,
;; Folk Hero, etc.) comes from plugins and SHOULD be flagged when removed.

(def ^:private builtin-classes
  #{:barbarian :bard :cleric :druid :fighter :monk
    :paladin :ranger :rogue :sorcerer :warlock :wizard})

(def ^:private builtin-races
  #{:dwarf :elf :halfling :human :dragonborn :gnome
    :half-elf :half-orc :tiefling})

;; Built-in subraces: PHB subrace keys auto-generated from their names via
;; common/name-to-kw. Human cultural variants (Calishite etc.) are defined
;; in spell_subs.cljs with only :name, so their keys are derived from the name.
(def ^:private builtin-subraces
  #{;; Dwarf
    :hill-dwarf :mountain-dwarf
    ;; Elf
    :high-elf :wood-elf :drow
    ;; Halfling
    :lightfoot :stout
    ;; Gnome
    :forest-gnome :rock-gnome
    ;; Human cultural variants (spell_subs.cljs human-option-cfg :subraces)
    :calishite :chondathan :damaran :illuskan
    :mulan :rashemi :shou :tethyrian :turami
    ;; Human variant selection options
    :standard-human :variant-human})

;; Only Acolyte is hardcoded (spell_subs.cljs:538).
(def ^:private builtin-backgrounds #{:acolyte})

;; SRD subclasses — one per class, hardcoded in classes.cljc.
(def ^:private builtin-subclasses
  #{:champion :berserker :lore :life :land :open-hand
    :devotion :hunter :thief :draconic :fiend :evocation})

;; Grappler is the only SRD feat (feats5e/feats-plugin, hardcoded).
(def ^:private builtin-feats #{:grappler})

(defn- builtin?
  "True if this key is SRD built-in content that won't appear in plugin subs."
  [k content-type]
  (case content-type
    :class (contains? builtin-classes k)
    :subclass (contains? builtin-subclasses k)
    :race (contains? builtin-races k)
    :subrace (contains? builtin-subraces k)
    :background (contains? builtin-backgrounds k)
    :feat (contains? builtin-feats k)
    false))

;; ============================================================================
;; Missing Content Detection
;; ============================================================================

(defn check-content-availability
  "Check which content keys from a character are missing.

   Parameters:
   - character-keys: seq from extract-content-keys
   - available-content: map of {:classes [...] :races [...] :subclasses [...] ...}

   Returns seq of missing content with suggestions."
  [character-keys available-content]
  (let [available-keys (into {}
                             (map (fn [[ct field]]
                                    [ct (set (map :key (get available-content field)))]))
                             content-type->field)]
    (keep
     (fn [{:keys [key content-type] :as entry}]
       (let [type-keys (get available-keys content-type #{})
             missing? (and (not (contains? type-keys key))
                           (not (builtin? key content-type)))]
         (when missing?
           (let [field (get content-type->field content-type)
                 suggestions (find-similar-content
                              key content-type
                              (get available-content field []))]
             (assoc entry
                    :missing? true
                    :suggestions suggestions
                    :inferred-source (infer-source-from-key key))))))
     character-keys)))

(defn generate-missing-content-report
  "Generate a user-friendly report of missing content.

   Returns:
   {:has-missing? bool
    :missing-count int
    :items [{:key :foo
             :label \"Class\"
             :inferred-source \"Kibbles' Tasty\"
             :suggestions [{:key :bar :name \"Similar\" :similarity 0.8}]}]}"
  [character available-content]
  (let [char-keys (extract-content-keys character)
        missing (check-content-availability char-keys available-content)]
    {:has-missing? (boolean (seq missing))
     :missing-count (count missing)
     :items (vec missing)}))
