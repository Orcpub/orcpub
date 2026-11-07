;; NOTE: `common_test.clj` in this dir shares this namespace and SHADOWS this
;; file under `lein test` (Clojure loads .clj before .cljc). These deftests run
;; under the CLJS runner (`lein fig:test`), not `lein test`. See the header
;; comment in common_test.clj before treating that as a bug.
(ns orcpub.common-test
  (:require [clojure.test :refer [deftest testing is]]
            #?(:cljs [cljs.reader])
            [orcpub.common :as common]))

;; ---------------------------------------------------------------------------
;; aloof-sort-by
;; ---------------------------------------------------------------------------

(deftest aloof-sort-by-normal-string-names
  (testing "case-insensitive alphabetical order for plain string :name keys"
    (let [items [{:name "Zebra"} {:name "apple"} {:name "Mango"}]
          result (common/aloof-sort-by :name items)]
      (is (= ["apple" "Mango" "Zebra"]
             (mapv :name result)))))

  (testing "already-sorted collection is unchanged"
    (let [items [{:name "Alpha"} {:name "Beta"} {:name "Gamma"}]
          result (common/aloof-sort-by :name items)]
      (is (= ["Alpha" "Beta" "Gamma"]
             (mapv :name result)))))

  (testing "single-element collection is returned unchanged"
    (let [items [{:name "Solo"}]
          result (common/aloof-sort-by :name items)]
      (is (= [{:name "Solo"}] (vec result)))))

  (testing "empty collection yields empty sequence"
    (is (empty? (common/aloof-sort-by :name [])))))

(deftest aloof-sort-by-nil-name-does-not-throw
  (testing "item with nil :name sorts before items with non-empty names"
    ;; nil -> (str nil) -> \"\" -> (s/lower-case \"\") -> \"\"
    ;; \"\" sorts before any non-empty lowercase string
    (let [items [{:name "Fireball"} {:name nil} {:name "Acid Splash"}]
          result (common/aloof-sort-by :name items)
          names  (mapv :name result)]
      (is (= nil (first names))
          "nil :name should sort first (as empty string)")
      (is (= #{"Acid Splash" "Fireball"} (set (rest names))))))

  (testing "multiple nil :name items do not throw"
    (let [items [{:name nil} {:name "Shield"} {:name nil}]
          result (common/aloof-sort-by :name items)]
      (is (= 3 (count result)))
      (is (= "Shield" (:name (last result)))))))

(deftest aloof-sort-by-blank-whitespace-name
  (testing "blank string sorts before non-blank strings"
    (let [items [{:name "Bless"} {:name ""} {:name "Aid"}]
          result (common/aloof-sort-by :name items)
          names  (mapv :name result)]
      (is (= "" (first names)))
      (is (= ["Aid" "Bless"] (vec (rest names))))))

  (testing "whitespace-only name sorts near the front (before letters)"
    (let [items [{:name "Cure Wounds"} {:name "   "} {:name "Aid"}]
          result (common/aloof-sort-by :name items)
          names  (mapv :name result)]
      ;; \"   \" lower-cased is still \"   \" which precedes \"aid\"
      (is (= "   " (first names))))))

(deftest aloof-sort-by-mixed-nil-and-non-nil
  (testing "mixed nil and non-nil :name values sort correctly without throwing"
    (let [items [{:name "Fireball"}
                 {:name nil}
                 {:name "acid splash"}
                 {:name nil}
                 {:name "Bless"}]
          result (common/aloof-sort-by :name items)
          names  (mapv :name result)]
      ;; Two nils sort as \"\" at the front, then alphabetical
      (is (= [nil nil "acid splash" "Bless" "Fireball"] names)))))

(deftest aloof-sort-by-title-sorter
  (testing ":title sorter works identically to :name sorter"
    (let [items [{:title "Sword"} {:title "axe"} {:title "Bow"}]
          result (common/aloof-sort-by :title items)]
      (is (= ["axe" "Bow" "Sword"]
             (mapv :title result)))))

  (testing "nil :title does not throw"
    (let [items [{:title "Rapier"} {:title nil}]
          result (common/aloof-sort-by :title items)]
      (is (= 2 (count result)))
      (is (= nil (:title (first result)))))))

(deftest aloof-sort-by-non-string-sorter-values
  (testing "numeric sorter value is coerced via str without throwing"
    ;; str on a number produces its decimal representation, e.g. (str 42) -> \"42\"
    (let [items [{:level 3} {:level 1} {:level 2}]
          result (common/aloof-sort-by :level items)]
      ;; Lexicographic string sort: \"1\" < \"2\" < \"3\"
      (is (= [1 2 3] (mapv :level result)))))

  (testing "keyword sorter value is coerced via str without throwing"
    ;; (str :fireball) -> \":fireball\"; all sort as their keyword printed forms
    (let [items [{:key :shield} {:key :fireball} {:key :bless}]
          result (common/aloof-sort-by :key items)]
      (is (= 3 (count result)))
      ;; Just verify no exception and count is preserved; ordering is deterministic
      ;; but depends on keyword print form \":bless\" < \":fireball\" < \":shield\"
      (is (= :bless (:key (first result)))))))

(deftest aloof-sort-by-missing-key
  (testing "item missing the sort key entirely (returns nil from sorter) does not throw"
    (let [items [{:name "Fireball"} {:other "data"} {:name "Acid Splash"}]
          result (common/aloof-sort-by :name items)]
      (is (= 3 (count result)))
      ;; The item with no :name has nil->\"\" so sorts first
      (is (nil? (:name (first result)))))))

(deftest toggle-flag-flips-booleans-but-never-collapses-collections
  (testing "behaves like `not` for flag values"
    (is (= true (common/toggle-flag false)))
    (is (= true (common/toggle-flag nil)))
    (is (= false (common/toggle-flag true))))
  (testing "leaves a map/collection UNTOUCHED (the B6 guard — `not` would nuke it to false)"
    (is (= {:athletics true} (common/toggle-flag {:athletics true})))
    (is (= [] (common/toggle-flag [])))
    (is (= {} (common/toggle-flag {})))))

(deftest toggle-in-toggles-heals-and-protects
  (testing "toggles a leaf flag like toggle-flag"
    (is (= {:a true} (common/toggle-in {} [:a])))
    (is (= {:a false} (common/toggle-in {:a true} [:a])))
    (is (= {:p {:k true}} (common/toggle-in {} [:p :k]))))
  (testing "leaf that holds a MAP is preserved (no collapse)"
    (is (= {:p {:skills {:athletics true}}}
           (common/toggle-in {:p {:skills {:athletics true}}} [:p :skills]))))
  (testing "SELF-HEAL: a stray false/boolean INTERMEDIATE becomes a fresh map"
    (is (= {:p {:skills {:stealth true}}}
           (common/toggle-in {:p {:skills false}} [:p :skills :stealth])))
    (is (= {:p {:skills {:stealth true}}}
           (common/toggle-in {:p {:skills true}} [:p :skills :stealth])))))

;; ---------------------------------------------------------------------------
;; name-to-kw — the empty-keyword guard (single choke point)
;;
;; A name that reduces to "" must NEVER become the empty keyword `:`, whose
;; printed form is a bare colon and is unreadable — a `:` key in stored data
;; makes read-string throw "A single colon is not a valid keyword." and the
;; whole load crashes. `(keyword "")` is written constructed, never as a
;; literal `:`, precisely because a literal `:` would break the reader on this
;; very test file.
;; ---------------------------------------------------------------------------

(def ^:private empty-kw (keyword ""))   ; the bad token, never written literally

(deftest name-to-kw-never-returns-empty-keyword
  (testing "names that strip to empty never become the empty keyword"
    ;; "" and apostrophe-only names both reduce to "" in the pipeline; the
    ;; inline guard substitutes a placeholder instead of (keyword "").
    (is (not= empty-kw (common/name-to-kw "")))
    (is (not= empty-kw (common/name-to-kw "'")))
    (is (not= empty-kw (common/name-to-kw "''")))
    (is (keyword? (common/name-to-kw ""))))
  (testing "distinct empties get distinct placeholders (hashed off the original)"
    ;; guarding off the ORIGINAL name keeps "" / "'" / "''" from colliding
    (is (= 3 (count (distinct [(common/name-to-kw "")
                               (common/name-to-kw "'")
                               (common/name-to-kw "''")])))))
  (testing "existing (non-blank) keys are unchanged — no data migration risk"
    (is (= :fireball (common/name-to-kw "Fireball")))
    (is (= :bobs-item (common/name-to-kw "Bob's Item")))
    (is (nil? (common/name-to-kw nil)))))

#?(:cljs
   (deftest name-to-kw-output-survives-the-cljs-reader
     ;; The real crash is cljs.reader on a bare `:`. Prove the guarded output
     ;; round-trips, and that a bare empty keyword would NOT (the reader is the
     ;; gate our fix routes around).
     (testing "guarded blank name round-trips through the cljs reader"
       (let [kw (common/name-to-kw "")]
         (is (= kw (cljs.reader/read-string (pr-str kw))))))
     (testing "control: the bare empty keyword is genuinely unreadable"
       (is (thrown? js/Error (cljs.reader/read-string (pr-str empty-kw)))))))

;; ---------------------------------------------------------------------------
;; sanitize-edn-colons — self-heal for already-corrupt stored EDN
;; ---------------------------------------------------------------------------

(deftest sanitize-edn-colons-repairs-bare-colons
  (testing "bare-colon tokens become unique placeholders; result is readable"
    (let [{:keys [text count]} (common/sanitize-edn-colons "{: {:a 1}}")]
      (is (= 1 count))
      (is (clojure.string/includes? text ":unnamed-1")))
    (let [{:keys [text count]} (common/sanitize-edn-colons "{:k :}")]
      (is (= 1 count))
      (is (clojure.string/includes? text ":unnamed-1")))
    (let [{:keys [text count]} (common/sanitize-edn-colons "{: 1 : 2}")]
      (is (= 2 count) "two bad tokens get distinct placeholders (no map-key collision)")
      (is (clojure.string/includes? text ":unnamed-1"))
      (is (clojure.string/includes? text ":unnamed-2"))))
  (testing "already-clean input is untouched (count 0)"
    (is (= 0 (:count (common/sanitize-edn-colons "{:fighter 1 :orcpub.dnd.e5/x 2}"))))
    (is (= 0 (:count (common/sanitize-edn-colons "#:orcpub.dnd.e5{:c {:artificer {:key :artificer}}}")))))
  (testing "colons INSIDE strings are preserved (not mistaken for bad tokens)"
    (let [in (str "{:desc " (pr-str "Choose one: fire") "}")
          {:keys [text count]} (common/sanitize-edn-colons in)]
      (is (= 0 count))
      (is (clojure.string/includes? text "Choose one: fire"))))
  (testing "non-string input is returned unchanged"
    (is (= {:text nil :count 0} (common/sanitize-edn-colons nil)))))

#?(:clj
   (deftest sanitize-edn-colons-parses-in-clj-reader
     ;; Prove repaired output is actually READABLE and the original was not.
     (testing "a bare-colon blob is unreadable but its sanitized form parses"
       (let [bad "{:orcpub.entity.strict/key :, :name \"x\"}"]
         (is (thrown? Exception (read-string bad)))
         (is (map? (read-string (:text (common/sanitize-edn-colons bad)))))))))

#?(:cljs
   (deftest sanitize-edn-colons-parses-in-cljs-reader
     (testing "sanitized output round-trips through the cljs reader"
       (let [bad "{:orcpub.entity.strict/key :, :name \"x\"}"]
         (is (thrown? js/Error (cljs.reader/read-string bad)))
         (is (map? (cljs.reader/read-string (:text (common/sanitize-edn-colons bad)))))))))

;; ---------------------------------------------------------------------------
;; Generative (fuzz) coverage — clj-only (keeps test.check off the cljs build).
;; These assert the *invariants* over adversarial input, not hand-picked cases.
;; ---------------------------------------------------------------------------

#?(:clj
   (do
     (require '[clojure.test.check.clojure-test :refer [defspec]])
     (require '[clojure.test.check.generators :as gen])
     (require '[clojure.test.check.properties :as prop])
     (require '[clojure.edn :as edn])

     (def ^:private wild-char
       (gen/frequency [[6 gen/char-alphanumeric]
                       [3 (gen/elements [\space \tab \: \" \\ \, \{ \} \[ \] \( \) \; (char 39) \/ \. \- \_])]
                       [1 gen/char]]))
     (def ^:private wild-str (gen/fmap (partial apply str) (gen/vector wild-char 0 40)))

     ;; name-to-kw is THE guarded char-cleaning derivation (the single choke
     ;; point). For arbitrary input it yields nil (non-string guard) or a
     ;; READABLE, non-empty keyword — never the bare `:`.
     (defspec name-to-kw-always-safe 4000
       (prop/for-all [s wild-str]
         (let [k (common/name-to-kw s)]
           (or (nil? k)
               (and (keyword? k) (not= empty-kw k) (= k (edn/read-string (pr-str k))))))))

     ;; sanitize never touches VALID edn (round-trip of arbitrary data stays identical).
     (def ^:private wild-edn
       (gen/one-of [gen/small-integer gen/boolean wild-str gen/keyword
                    (gen/map gen/keyword wild-str) (gen/vector wild-str 0 5)]))
     (defspec sanitize-leaves-valid-edn-untouched 2000
       (prop/for-all [d wild-edn]
         (let [es (pr-str d) {:keys [text count]} (common/sanitize-edn-colons es)]
           (and (= 0 count) (= text es)))))

     ;; sanitize is idempotent on arbitrary raw strings.
     (defspec sanitize-idempotent 2000
       (prop/for-all [s wild-str]
         (let [{t1 :text} (common/sanitize-edn-colons s)
               {t2 :text} (common/sanitize-edn-colons t1)]
           (= t1 t2))))))

;; ---------------------------------------------------------------------------
;; Number-word translation + name repair (for keyword-trap recovery)
;; ---------------------------------------------------------------------------

(deftest cardinal->words-covers-the-range
  (testing "cardinals compose correctly, and decline outside 0..999"
    (is (= "zero" (common/cardinal->words 0)))
    (is (= "nine" (common/cardinal->words 9)))
    (is (= "nineteen" (common/cardinal->words 19)))
    (is (= "twenty" (common/cardinal->words 20)))
    (is (= "forty-seven" (common/cardinal->words 47)))
    (is (= "one hundred" (common/cardinal->words 100)))
    (is (= "one hundred one" (common/cardinal->words 101)))
    (is (= "three hundred" (common/cardinal->words 300)))
    (is (= "nine hundred ninety-nine" (common/cardinal->words 999)))
    (is (nil? (common/cardinal->words 1000)) "above the range declines")
    (is (nil? (common/cardinal->words -1)))))

(deftest ordinal->words-ordinalizes-the-final-atom
  (testing "irregular stems + composed ordinals"
    (is (= "first" (common/ordinal->words 1)))
    (is (= "second" (common/ordinal->words 2)))
    (is (= "third" (common/ordinal->words 3)))
    (is (= "fifth" (common/ordinal->words 5)))
    (is (= "ninth" (common/ordinal->words 9)))
    (is (= "thirteenth" (common/ordinal->words 13)))
    (is (= "twentieth" (common/ordinal->words 20)))
    (is (= "twenty-first" (common/ordinal->words 21)))
    (is (= "one hundredth" (common/ordinal->words 100)))
    (is (nil? (common/ordinal->words 1000)))))

(deftest lead-number->words-cardinal-and-ordinal
  (testing "translates a leading number, preserving the rest of the name"
    (is (= "Nine Lives" (common/lead-number->words "9 Lives")))
    (is (= "Twenty Sided" (common/lead-number->words "20 Sided")))
    (is (= "Forty-seven Ronin" (common/lead-number->words "47 Ronin")))
    (is (= "One Hundred Hands" (common/lead-number->words "100 Hands")))
    (is (= "One Hundred One Damnations" (common/lead-number->words "101 Damnations")))
    (is (= "Second Wind" (common/lead-number->words "2nd Wind")))
    (is (= "First Strike" (common/lead-number->words "1st Strike")))
    (is (= "Thirteenth Warrior" (common/lead-number->words "13th Warrior")))
    (is (= "Twenty-first Century" (common/lead-number->words "21st Century")))
    ;; leading whitespace is tolerated (left-trimmed to find the number); the
    ;; caller (repair-name-lead) is what fully trims — see its test below.
    (is (= "Nine Lives" (common/lead-number->words "  9 Lives")))))

(deftest lead-number->words-declines-non-name-numbers
  (testing "bails (nil) on out-of-range, dice/version tokens, and non-numeric leads"
    (is (nil? (common/lead-number->words "2020 Vision")) "above cap = a year, not a word")
    (is (nil? (common/lead-number->words "3d6 Damage")) "dice notation, not a number word")
    (is (nil? (common/lead-number->words "5e Feat")) "glued to a letter")
    (is (nil? (common/lead-number->words "Bob")) "already letter-leading")
    (is (nil? (common/lead-number->words "@@@Bob")) "symbol-leading, not numeric")))

(deftest repair-name-lead-chain
  (testing "least-destructive: number->word, else strip, else nil (caller -> placeholder)"
    (is (= "Bob" (common/repair-name-lead "Bob")) "already valid, unchanged")
    (is (= "Nine Lives" (common/repair-name-lead "9 Lives")) "number -> word")
    (is (= "Second Wind" (common/repair-name-lead "2nd Wind")) "ordinal -> word")
    (is (= "Three Hundred" (common/repair-name-lead "300")))
    (is (= "Bob" (common/repair-name-lead "@@@Bob")) "strip leading symbols")
    (is (nil? (common/repair-name-lead "@@@")) "nothing usable -> nil")
    (is (nil? (common/repair-name-lead "2020")) "out-of-range number, no letters -> nil")
    (is (= "Nine Lives" (common/repair-name-lead "  9 Lives  ")) "trims"))
  (testing "every non-nil repair derives a valid letter-leading key"
    (doseq [nm ["9 Lives" "2nd Wind" "@@@Bob" "300" "13th Warrior" "Bob"]]
      (let [r (common/repair-name-lead nm)]
        (is (common/keyword-starts-with-letter? (common/name-to-kw r))
            (str nm " -> " (pr-str r) " must key-validate"))))))
