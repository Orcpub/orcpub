(ns orcpub.dnd.e5.orcbrew-validation-test
  (:require [cljs.test :refer-macros [deftest testing is]]
            [cljs.reader :refer [read-string]]
            [orcpub.dnd.e5.orcbrew-validation :as orcbrew-val]
            [orcpub.common :as common]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.content-specs :as content-specs]
            [cljs.spec.alpha :as spec]))

;; ============================================================================
;; Test Data
;; ============================================================================

(def valid-plugin-edn
  "{:orcpub.dnd.e5/spells
    {:fireball {:option-pack \"My Homebrew\"
                :name \"Fireball\"
                :level 3
                :school \"evocation\"}
     :lightning-bolt {:option-pack \"My Homebrew\"
                      :name \"Lightning Bolt\"
                      :level 3
                      :school \"evocation\"}}}")

(def invalid-plugin-edn-parse-error
  "{:orcpub.dnd.e5/spells
    {:fireball {:option-pack \"My Homebrew\"")  ; Missing closing braces

(def plugin-with-missing-option-pack
  "{:orcpub.dnd.e5/spells
    {:fireball {:name \"Fireball\"
                :level 3}}}")  ; Missing :option-pack

(def plugin-with-empty-option-pack
  "{:orcpub.dnd.e5/spells
    {:fireball {:option-pack \"\"
                :name \"Fireball\"
                :level 3}}}")


(def plugin-with-disabled-nil
  "{:disabled? nil
    :orcpub.dnd.e5/spells
    {:fireball {:option-pack \"My Homebrew\"
                :name \"Fireball\"
                :level 3}}}")

(def multi-plugin-edn
  "{\"Plugin 1\" {:orcpub.dnd.e5/spells
                   {:fireball {:option-pack \"Plugin 1\"
                               :name \"Fireball\"}}}
    \"Plugin 2\" {:orcpub.dnd.e5/races
                   {:elf {:option-pack \"Plugin 2\"
                          :name \"Elf\"}}}}")

(def plugin-with-mixed-validity
  "{:orcpub.dnd.e5/spells
    {:valid-spell {:option-pack \"Test\"
                   :name \"Valid Spell\"}
     :invalid-spell {:name \"No Option Pack\"}
     :another-valid {:option-pack \"Test\"
                     :name \"Another Valid\"}}}")

;; ============================================================================
;; Parse Tests
;; ============================================================================

(deftest test-parse-edn-success
  (testing "Parsing valid EDN"
    (let [result (orcbrew-val/parse-edn valid-plugin-edn)]
      (is (:success result))
      (is (map? (:data result)))
      (is (contains? (:data result) :orcpub.dnd.e5/spells)))))

(deftest test-parse-edn-failure
  (testing "Parsing invalid EDN"
    (let [result (orcbrew-val/parse-edn invalid-plugin-edn-parse-error)]
      (is (not (:success result)))
      (is (:error result))
      (is (string? (:hint result))))))

(deftest test-parse-edn-empty-string
  (testing "Parsing empty string"
    (let [result (orcbrew-val/parse-edn "")]
      (is (not (:success result))))))

;; ============================================================================
;; Validation Tests
;; ============================================================================

(deftest test-validate-item-valid
  (testing "Validating a valid item"
    (let [item {:option-pack "Test Pack" :name "Test Item"}
          result (orcbrew-val/validate-item :test-item item)]
      (is (:valid result)))))

(deftest test-validate-item-missing-option-pack
  (testing "Validating item without option-pack"
    (let [item {:name "Test Item"}
          result (orcbrew-val/validate-item :test-item item)]
      (is (not (:valid result)))
      (is (:errors result)))))

(deftest test-validate-content-group
  (testing "Validating a content group with mixed items"
    (let [items {:valid1 {:option-pack "Test" :name "Valid 1"}
                 :invalid1 {:name "No Pack"}
                 :valid2 {:option-pack "Test" :name "Valid 2"}}
          result (orcbrew-val/validate-content-group :orcpub.dnd.e5/spells items)]
      (is (= 2 (:valid-count result)))
      (is (= 1 (:invalid-count result)))
      (is (= 1 (count (:invalid-items result)))))))

(deftest test-validate-plugin-progressive
  (testing "Progressive validation of plugin"
    (let [plugin (read-string plugin-with-mixed-validity)
          result (orcbrew-val/validate-plugin-progressive plugin)]
      (is (not (:valid result)))  ; Has invalid items
      (is (= 2 (:valid-items-count result)))
      (is (= 1 (:invalid-items-count result))))))

;; ============================================================================
;; Pre-Export Validation Tests
;; ============================================================================

(deftest test-validate-before-export-valid
  (testing "Pre-export validation of valid plugin"
    (let [plugin (read-string valid-plugin-edn)
          result (orcbrew-val/validate-before-export plugin)]
      (is (:valid result))
      (is (or (nil? (:warnings result))
             (empty? (:warnings result)))))))

(deftest test-validate-before-export-empty-option-pack
  (testing "Pre-export validation detects empty option-pack"
    (let [plugin (read-string plugin-with-empty-option-pack)
          result (orcbrew-val/validate-before-export plugin)]
      (is (seq (:warnings result)))
      (is (some #(re-find #"option-pack" %) (:warnings result))))))

(deftest test-validate-before-export-nil-values
  (testing "Pre-export validation detects nil values"
    (let [plugin {:orcpub.dnd.e5/spells {:test {:option-pack nil}}
                  :some-key nil}
          result (orcbrew-val/validate-before-export plugin)]
      (is (seq (:warnings result))))))

;; ============================================================================
;; Import Strategy Tests
;; ============================================================================

(deftest test-import-all-or-nothing-valid
  (testing "All-or-nothing import with valid data"
    (let [plugin (read-string valid-plugin-edn)
          result (orcbrew-val/import-all-or-nothing plugin)]
      (is (:success result))
      (is (= :single-plugin (:strategy result)))
      (is (= plugin (:data result))))))

(deftest test-import-all-or-nothing-invalid
  (testing "All-or-nothing import with invalid data"
    (let [plugin (read-string plugin-with-missing-option-pack)
          result (orcbrew-val/import-all-or-nothing plugin)]
      (is (not (:success result)))
      (is (:errors result)))))

(deftest test-import-progressive-with-errors
  (testing "Progressive import recovers valid items"
    (let [plugin (read-string plugin-with-mixed-validity)
          result (orcbrew-val/import-progressive plugin)]
      (is (:success result))
      (is (:had-errors result))
      (is (= 2 (:imported-count result)))
      (is (= 1 (:skipped-count result)))
      (is (= 1 (count (:skipped-items result))))
      ;; Verify cleaned plugin only has valid items
      (let [cleaned-spells (get-in result [:data :orcpub.dnd.e5/spells])]
        (is (= 2 (count cleaned-spells)))
        (is (contains? cleaned-spells :valid-spell))
        (is (contains? cleaned-spells :another-valid))
        (is (not (contains? cleaned-spells :invalid-spell)))))))

(deftest test-import-progressive-all-valid
  (testing "Progressive import with all valid items"
    (let [plugin (read-string valid-plugin-edn)
          result (orcbrew-val/import-progressive plugin)]
      (is (:success result))
      (is (not (:had-errors result)))
      (is (= 2 (:imported-count result)))
      (is (= 0 (:skipped-count result))))))

;; ============================================================================
;; Auto-Cleaning Tests
;; ============================================================================

(deftest test-validate-import-with-auto-clean
  (testing "Auto-clean fixes disabled? nil"
    (let [result (orcbrew-val/validate-import plugin-with-disabled-nil
                                             {:strategy :progressive
                                              :auto-clean true})]
      (is (:success result))
      ;; After cleaning, disabled? should be false instead of nil
      (let [disabled (get-in result [:data :disabled?])]
        (is (= false disabled))))))

(deftest test-validate-import-empty-option-pack-auto-clean
  (testing "Auto-clean fixes empty option-pack"
    (let [result (orcbrew-val/validate-import plugin-with-empty-option-pack
                                             {:strategy :progressive
                                              :auto-clean true})]
      ;; Should succeed after cleaning
      (is (:success result)))))

;; ============================================================================
;; Complete Workflow Tests
;; ============================================================================

(deftest test-full-import-workflow-valid
  (testing "Complete import workflow with valid file"
    (let [result (orcbrew-val/validate-import valid-plugin-edn
                                             {:strategy :progressive
                                              :auto-clean true})]
      (is (:success result))
      (is (not (:had-errors result)))
      (is (map? (:data result))))))

(deftest test-full-import-workflow-parse-error
  (testing "Complete import workflow with parse error"
    (let [result (orcbrew-val/validate-import invalid-plugin-edn-parse-error
                                             {:strategy :progressive
                                              :auto-clean true})]
      (is (not (:success result)))
      (is (:parse-error result))
      (is (:error result))
      (is (:hint result)))))

(deftest test-full-import-workflow-progressive
  (testing "Complete import workflow with progressive strategy"
    (let [result (orcbrew-val/validate-import plugin-with-mixed-validity
                                             {:strategy :progressive
                                              :auto-clean true})]
      (is (:success result))
      (is (:had-errors result))
      ;; Should have imported 2 valid items and skipped 1 invalid
      (is (= 2 (:imported-count result)))
      (is (= 1 (:skipped-count result))))))

(deftest test-full-import-workflow-strict
  (testing "Complete import workflow with strict strategy"
    (let [result (orcbrew-val/validate-import plugin-with-mixed-validity
                                             {:strategy :strict
                                              :auto-clean true})]
      ;; Strict mode should fail because not all items are valid
      (is (not (:success result)))
      (is (:errors result)))))

;; ============================================================================
;; Multi-Plugin Tests
;; ============================================================================

(deftest test-import-multi-plugin
  (testing "Importing multi-plugin file"
    (let [result (orcbrew-val/validate-import multi-plugin-edn
                                             {:strategy :strict
                                              :auto-clean true})]
      (is (:success result))
      (is (= :multi-plugin (:strategy result)))
      (is (map? (:data result)))
      (is (contains? (:data result) "Plugin 1"))
      (is (contains? (:data result) "Plugin 2")))))

;; ============================================================================
;; Error Message Formatting Tests
;; ============================================================================

(deftest test-format-import-result-success
  (testing "Formatting successful import result"
    (let [result {:success true :imported-count 5}
          message (orcbrew-val/format-import-result result)]
      (is (string? message))
      (is (re-find #"✅" message))
      (is (re-find #"successful" message)))))

(deftest test-format-import-result-with-warnings
  (testing "Formatting import result with warnings"
    (let [result {:success true
                  :had-errors true
                  :imported-count 2
                  :skipped-count 1}
          message (orcbrew-val/format-import-result result)]
      (is (string? message))
      (is (re-find #"⚠️" message))
      (is (re-find #"warning" message)))))

(deftest test-format-import-result-parse-error
  (testing "Formatting parse error result"
    (let [result {:success false
                  :parse-error true
                  :error "Unexpected token"
                  :line 5
                  :hint "Check brackets"}
          message (orcbrew-val/format-import-result result)]
      (is (string? message))
      (is (re-find #"⚠️" message))
      (is (re-find #"Could not read" message))
      (is (re-find #"Line: 5" message)))))

(deftest test-format-import-result-validation-error
  (testing "Formatting validation error result"
    (let [result {:success false
                  :errors ["Error 1" "Error 2"]}
          message (orcbrew-val/format-import-result result)]
      (is (string? message))
      (is (re-find #"⚠️" message))
      (is (re-find #"Invalid" message)))))
;; ============================================================================
;; Data-Level Cleaning Tests
;; ============================================================================

(def plugin-with-preserved-nil
  "{:orcpub.dnd.e5/classes
    {:wizard {:option-pack \"Test\"
              :name \"Wizard\"
              :spellcasting {:spell-list-kw nil}}}}")

(def plugin-with-removed-nil
  "{:orcpub.dnd.e5/monsters
    {:monster {:option-pack \"Test\"
               :name \"Test Monster\"
               :saving-throws {:str nil, :dex 5, :con nil}}}}")

(def plugin-with-ability-nils
  "{:orcpub.dnd.e5/monsters
    {:monster {:option-pack \"Test\"
               :name \"Test Monster\"
               :abilities {:str nil, :dex nil, :con 10}}}}")

(def plugin-with-trailing-comma
  "{:orcpub.dnd.e5/spells
    {:fireball {:option-pack \"Test\"
                :name \"Fireball\",}}}")

(def multi-plugin-with-empty-key
  "{\"\" {:orcpub.dnd.e5/races {:elf {:option-pack \"\" :name \"Elf\"}}}
    \"Existing Pack\" {:orcpub.dnd.e5/spells {:fireball {:option-pack \"Existing Pack\" :name \"Fireball\"}}}}")

(deftest test-data-clean-preserves-semantic-nil
  (testing "Data cleaning preserves nil for semantic fields like spell-list-kw"
    (let [result (orcbrew-val/validate-import plugin-with-preserved-nil
                                             {:strategy :progressive
                                              :auto-clean true})]
      (is (:success result))
      ;; spell-list-kw nil should be PRESERVED (it means custom spell list)
      (let [wizard (get-in result [:data :orcpub.dnd.e5/classes :wizard])]
        (is (= "Wizard" (:name wizard)))
        (is (contains? (:spellcasting wizard) :spell-list-kw))
        (is (nil? (get-in wizard [:spellcasting :spell-list-kw])))))))

(deftest test-data-clean-removes-numeric-nil
  (testing "Data cleaning removes nil for numeric fields like ability scores"
    (let [result (orcbrew-val/validate-import plugin-with-removed-nil
                                             {:strategy :progressive
                                              :auto-clean true})]
      (is (:success result))
      ;; :str nil and :con nil should be REMOVED (accidental leftovers)
      (let [saving-throws (get-in result [:data :orcpub.dnd.e5/monsters :monster :saving-throws])]
        (is (not (contains? saving-throws :str)))
        (is (not (contains? saving-throws :con)))
        (is (= 5 (:dex saving-throws)))))))

(deftest test-data-clean-ability-nils
  (testing "Data cleaning removes nil ability scores"
    (let [result (orcbrew-val/validate-import plugin-with-ability-nils
                                             {:strategy :progressive
                                              :auto-clean true})]
      (is (:success result))
      (let [abilities (get-in result [:data :orcpub.dnd.e5/monsters :monster :abilities])]
        (is (not (contains? abilities :str)))
        (is (not (contains? abilities :dex)))
        (is (= 10 (:con abilities)))))))

(deftest test-string-clean-trailing-comma
  (testing "String cleaning removes trailing commas before closing braces"
    (let [result (orcbrew-val/validate-import plugin-with-trailing-comma
                                             {:strategy :progressive
                                              :auto-clean true})]
      (is (:success result))
      (is (= "Fireball" (get-in result [:data :orcpub.dnd.e5/spells :fireball :name]))))))

(deftest test-data-clean-renames-empty-plugin-key
  (testing "Data cleaning renames empty string plugin key"
    (let [result (orcbrew-val/validate-import multi-plugin-with-empty-key
                                             {:strategy :progressive
                                              :auto-clean true})]
      (is (:success result))
      ;; Empty key should be renamed to "Unnamed Content"
      (let [data (:data result)]
        (is (not (contains? data "")))
        (is (contains? data "Unnamed Content"))
        (is (contains? data "Existing Pack"))))))

(deftest test-data-clean-fixes-empty-option-pack
  (testing "Data cleaning fixes empty option-pack strings"
    (let [result (orcbrew-val/validate-import multi-plugin-with-empty-key
                                             {:strategy :progressive
                                              :auto-clean true})]
      (is (:success result))
      ;; Empty option-pack is replaced with the built-in "Default Option Source".
      (let [elf (get-in result [:data "Unnamed Content" :orcpub.dnd.e5/races :elf])]
        (is (= "Default Option Source" (:option-pack elf)))))))

;; ============================================================================
;; Duplicate Key Detection Tests
;; ============================================================================

(def multi-plugin-with-internal-duplicate
  "{\"Source A\" {:orcpub.dnd.e5/classes {:artificer {:option-pack \"Source A\" :name \"Artificer A\"}}}
    \"Source B\" {:orcpub.dnd.e5/classes {:artificer {:option-pack \"Source B\" :name \"Artificer B\"}}}}")

(def plugin-external-conflict
  "{:orcpub.dnd.e5/classes {:wizard {:option-pack \"New Source\" :name \"My Wizard\"}}}")

(def existing-plugins
  {"PHB" {:orcpub.dnd.e5/classes {:wizard {:option-pack "PHB" :name "Wizard"}}}})

(deftest test-detect-internal-duplicate-keys
  (testing "Detecting duplicate keys within a multi-plugin import"
    (let [data (read-string multi-plugin-with-internal-duplicate)
          conflicts (orcbrew-val/detect-duplicate-keys data nil "Test")]
      (is (= 1 (count (:internal-conflicts conflicts))))
      (is (= :artificer (-> conflicts :internal-conflicts first :key)))
      (is (= 2 (count (-> conflicts :internal-conflicts first :sources)))))))

(deftest test-detect-external-duplicate-keys
  (testing "Detecting duplicate keys against existing plugins"
    (let [data (read-string plugin-external-conflict)
          conflicts (orcbrew-val/detect-duplicate-keys data existing-plugins "New Source")]
      (is (= 0 (count (:internal-conflicts conflicts))))
      (is (= 1 (count (:external-conflicts conflicts))))
      (is (= :wizard (-> conflicts :external-conflicts first :key))))))

(deftest test-no-false-positive-duplicates
  (testing "No false positives when keys don't conflict"
    (let [data {:orcpub.dnd.e5/classes {:sorcerer {:option-pack "Test" :name "Sorcerer"}}}
          conflicts (orcbrew-val/detect-duplicate-keys data existing-plugins "Test")]
      (is (empty? (:internal-conflicts conflicts)))
      (is (empty? (:external-conflicts conflicts))))))

;; ============================================================================
;; Key Renaming Tests
;; ============================================================================

(deftest test-generate-new-key
  (testing "Generating new key with source suffix"
    (is (= :artificer-kibbles-tasty
           (orcbrew-val/generate-new-key :artificer "Kibbles' Tasty")))
    (is (= :wizard-my-homebrew
           (orcbrew-val/generate-new-key :wizard "My Homebrew")))
    (is (= :monk-test-123
           (orcbrew-val/generate-new-key :monk "Test 123")))))

(deftest test-rename-key-in-plugin
  (testing "Renaming a key in a plugin"
    (let [plugin {:orcpub.dnd.e5/classes
                  {:artificer {:option-pack "Test" :name "Artificer"}}}
          result (orcbrew-val/rename-key-in-plugin
                  plugin
                  :orcpub.dnd.e5/classes
                  :artificer
                  :artificer-test)]
      (is (not (contains? (get result :orcpub.dnd.e5/classes) :artificer)))
      (is (contains? (get result :orcpub.dnd.e5/classes) :artificer-test))
      (is (= "Artificer" (get-in result [:orcpub.dnd.e5/classes :artificer-test :name]))))))

(deftest test-rename-missing-key-is-noop-not-nil-clobber
  (testing "REGRESSION: renaming a key that isn't present must NOT fabricate a
            `new-key -> nil` entry. A key that is BOTH an internal and an external
            conflict generates two renames for it; the second hits an already-moved
            key and used to assoc nil, producing a non-map item that fails ::plugin
            and quarantined the whole source."
    (let [plugin {:orcpub.dnd.e5/subclasses
                  {:artillerist {:option-pack "P" :name "Artillerist"}}}
          ;; :alchemist was already moved away (or never here) — rename is redundant
          result (orcbrew-val/rename-key-in-plugin
                  plugin :orcpub.dnd.e5/subclasses :alchemist :alchemist-ua)]
      (is (= plugin result) "no-op: plugin unchanged")
      (is (not (contains? (get result :orcpub.dnd.e5/subclasses) :alchemist-ua))
          "no fabricated key")
      (is (not (some nil? (vals (get result :orcpub.dnd.e5/subclasses))))
          "no nil item values")))

  (testing "REGRESSION (batch): a duplicate rename of the same key resolves once
            and leaves the item a map — never nil."
    (let [data {"UA - Revisited"
                {:orcpub.dnd.e5/subclasses
                 {:alchemist {:option-pack "UA - Revisited" :name "Alchemist"}}}}
          ;; same (source, from) twice — the overlap an internal+external conflict makes
          renames [{:source "UA - Revisited" :content-type :orcpub.dnd.e5/subclasses
                    :from :alchemist :to :alchemist-ua-revisited}
                   {:source "UA - Revisited" :content-type :orcpub.dnd.e5/subclasses
                    :from :alchemist :to :alchemist-ua-revisited}]
          result (orcbrew-val/apply-key-renames data renames)
          items (get-in result ["UA - Revisited" :orcpub.dnd.e5/subclasses])]
      (is (map? (:alchemist-ua-revisited items)) "item stays a map, not nil")
      (is (not (some nil? (vals items))) "no nil item values in the group"))))

(deftest test-rename-key-updates-subclass-references
  (testing "Renaming a class key updates subclass references"
    (let [plugin {:orcpub.dnd.e5/classes
                  {:artificer {:option-pack "Test" :name "Artificer"}}
                  :orcpub.dnd.e5/subclasses
                  {:alchemist {:option-pack "Test" :name "Alchemist" :class :artificer}
                   :armorer {:option-pack "Test" :name "Armorer" :class :artificer}
                   :other-subclass {:option-pack "Other" :name "Other" :class :wizard}}}
          result (orcbrew-val/rename-key-in-plugin
                  plugin
                  :orcpub.dnd.e5/classes
                  :artificer
                  :artificer-kibbles)]
      ;; Class should be renamed
      (is (contains? (get result :orcpub.dnd.e5/classes) :artificer-kibbles))
      (is (not (contains? (get result :orcpub.dnd.e5/classes) :artificer)))
      ;; Subclasses should have updated :class references
      (is (= :artificer-kibbles (get-in result [:orcpub.dnd.e5/subclasses :alchemist :class])))
      (is (= :artificer-kibbles (get-in result [:orcpub.dnd.e5/subclasses :armorer :class])))
      ;; Other subclass should be unchanged
      (is (= :wizard (get-in result [:orcpub.dnd.e5/subclasses :other-subclass :class]))))))

(deftest test-rename-key-updates-subrace-references
  (testing "Renaming a race key updates subrace references"
    (let [plugin {:orcpub.dnd.e5/races
                  {:elf {:option-pack "Test" :name "Elf"}}
                  :orcpub.dnd.e5/subraces
                  {:high-elf {:option-pack "Test" :name "High Elf" :race :elf}
                   :wood-elf {:option-pack "Test" :name "Wood Elf" :race :elf}
                   :hill-dwarf {:option-pack "Test" :name "Hill Dwarf" :race :dwarf}}}
          result (orcbrew-val/rename-key-in-plugin
                  plugin
                  :orcpub.dnd.e5/races
                  :elf
                  :elf-homebrew)]
      ;; Race should be renamed
      (is (contains? (get result :orcpub.dnd.e5/races) :elf-homebrew))
      (is (not (contains? (get result :orcpub.dnd.e5/races) :elf)))
      ;; Subraces should have updated :race references
      (is (= :elf-homebrew (get-in result [:orcpub.dnd.e5/subraces :high-elf :race])))
      (is (= :elf-homebrew (get-in result [:orcpub.dnd.e5/subraces :wood-elf :race])))
      ;; Other subrace should be unchanged
      (is (= :dwarf (get-in result [:orcpub.dnd.e5/subraces :hill-dwarf :race]))))))

;; REGRESSION GUARD for import key-rename + cross-reference rewrite. It was
;; failing on BROKEN SCAFFOLDING, not a code bug: `apply-key-renames` (and its
;; only real caller, events.cljs `:apply-conflict-resolutions`) read each rename
;; as {:source :content-type :from :to}; the test was passing :old-key/:new-key,
;; so from/to were nil and nothing was renamed. Keys corrected to :from/:to here
;; — now the guard actually exercises the rename + subclass `:class` ref rewrite.
;; If this fails again, fix the CODE; the input shape mirrors the live caller.
(deftest test-apply-key-renames-batch
  (testing "Applying batch of key renames"
    (let [data {"Source A" {:orcpub.dnd.e5/classes
                            {:artificer {:option-pack "Source A" :name "Artificer A"}}
                            :orcpub.dnd.e5/subclasses
                            {:alchemist {:option-pack "Source A" :name "Alchemist" :class :artificer}}}
                "Source B" {:orcpub.dnd.e5/classes
                            {:artificer {:option-pack "Source B" :name "Artificer B"}}}}
          renames [{:source "Source A"
                    :content-type :orcpub.dnd.e5/classes
                    :from :artificer
                    :to :artificer-source-a}]
          result (orcbrew-val/apply-key-renames data renames)]
      ;; Source A's artificer should be renamed
      (is (contains? (get-in result ["Source A" :orcpub.dnd.e5/classes]) :artificer-source-a))
      (is (not (contains? (get-in result ["Source A" :orcpub.dnd.e5/classes]) :artificer)))
      ;; Source A's subclass should have updated reference
      (is (= :artificer-source-a (get-in result ["Source A" :orcpub.dnd.e5/subclasses :alchemist :class])))
      ;; Source B's artificer should be unchanged
      (is (contains? (get-in result ["Source B" :orcpub.dnd.e5/classes]) :artificer)))))

;; ============================================================================
;; Option Auto-Fill Tests
;; ============================================================================

(def selection-with-empty-options
  {:name "My Selection"
   :key :my-selection
   :option-pack "Test Pack"
   :options [{} {:name ""} {:name "Valid Option"}]})

(def selection-no-options
  {:name "No Options Selection"
   :key :no-options
   :option-pack "Test Pack"})

(deftest test-fill-missing-option-fields
  (testing "Empty option gets placeholder name with index"
    (let [[filled changes] (orcbrew-val/fill-missing-option-fields 0 {})]
      (is (= "Option 1" (:name filled)))
      (is (= [:name] changes))))
  (testing "Option with blank name gets filled"
    (let [[filled changes] (orcbrew-val/fill-missing-option-fields 2 {:name ""})]
      (is (= "Option 3" (:name filled)))
      (is (= [:name] changes))))
  (testing "Option with valid name is unchanged"
    (let [[filled changes] (orcbrew-val/fill-missing-option-fields 0 {:name "Fireball"})]
      (is (= "Fireball" (:name filled)))
      (is (empty? changes))))
  (testing "Option with description but no name gets filled"
    (let [[filled changes] (orcbrew-val/fill-missing-option-fields 4 {:description "A cool option"})]
      (is (= "Option 5" (:name filled)))
      (is (= "A cool option" (:description filled)))
      (is (= [:name] changes)))))

(deftest test-fill-options-in-item
  (testing "Item with empty options gets filled"
    (let [[filled count] (orcbrew-val/fill-options-in-item selection-with-empty-options)]
      (is (= "Option 1" (get-in filled [:options 0 :name])))
      (is (= "Option 2" (get-in filled [:options 1 :name])))
      (is (= "Valid Option" (get-in filled [:options 2 :name])))
      (is (= 2 count))))
  (testing "Item without options is unchanged"
    (let [[filled count] (orcbrew-val/fill-options-in-item selection-no-options)]
      (is (nil? (:options filled)))
      (is (= 0 count))))
  (testing "Item with all valid options has zero changes"
    (let [[filled count] (orcbrew-val/fill-options-in-item
                          {:options [{:name "A"} {:name "B"}]})]
      (is (= "A" (get-in filled [:options 0 :name])))
      (is (= "B" (get-in filled [:options 1 :name])))
      (is (= 0 count)))))

(deftest test-fill-all-missing-fields-includes-options
  (testing "fill-all-missing-fields processes options"
    (let [item {:options [{} {:name "Good"}]}
          result (orcbrew-val/fill-all-missing-fields item :orcpub.dnd.e5/selections)]
      (is (= "Option 1" (get-in result [:item :options 0 :name])))
      (is (= "Good" (get-in result [:item :options 1 :name])))
      (is (= 1 (get-in result [:changes :options-fixed])))))
  (testing "fill-all-missing-fields handles item with no options"
    (let [result (orcbrew-val/fill-all-missing-fields {:name "Test"} :orcpub.dnd.e5/selections)]
      (is (= 0 (get-in result [:changes :options-fixed])))))
  (testing "fill-all-missing-fields processes both traits and options"
    (let [item {:traits [{:name "Good Trait"} {}]
                :options [{} {:name "Good Option"}]}
          result (orcbrew-val/fill-all-missing-fields item :orcpub.dnd.e5/races)]
      (is (= "Missing Trait Name" (get-in result [:item :traits 1 :name])))
      (is (= "Option 1" (get-in result [:item :options 0 :name])))
      (is (= 1 (get-in result [:changes :traits-fixed])))
      (is (= 1 (get-in result [:changes :options-fixed]))))))

;; ============================================================================
;; Levenshtein Distance Tests
;; ============================================================================

(deftest test-levenshtein-distance-basics
  (testing "Known edit distances"
    (is (= 0 (orcbrew-val/levenshtein-distance :abc :abc)))
    (is (= 3 (orcbrew-val/levenshtein-distance :kitten :sitting)))
    (is (= 3 (orcbrew-val/levenshtein-distance :saturday :sunday))))
  (testing "Empty string edge cases"
    (is (= 3 (orcbrew-val/levenshtein-distance :abc (keyword ""))))
    (is (= 0 (orcbrew-val/levenshtein-distance (keyword "") (keyword ""))))))

(deftest test-levenshtein-early-return
  (testing "Length diff > 10 returns len-diff (skips matrix computation)"
    ;; :ab (2 chars) vs :abcdefghijklmno (15 chars) — diff is 13
    (is (= 13 (orcbrew-val/levenshtein-distance :ab :abcdefghijklmno))))
  (testing "Length diff <= 10 still computes full matrix"
    ;; :abc (3 chars) vs :abcdefghijk (11 chars) — diff is 8, should compute
    (let [dist (orcbrew-val/levenshtein-distance :abc :abcdefghijk)]
      (is (= 8 dist)))))

;; ============================================================================
;; Format Spec Problem — falsy value handling
;; ============================================================================

(deftest test-format-spec-problem-val-display
  (testing "nil val suppressed from output (no 'Got:' line)"
    (let [result (orcbrew-val/format-spec-problem {:path [] :pred 'string? :val nil :via [] :in []})]
      (is (not (re-find #"Got:" result)))))
  (testing "false val shown (some? distinguishes false from nil)"
    (let [result (orcbrew-val/format-spec-problem {:path [] :pred 'string? :val false :via [] :in []})]
      (is (re-find #"Got: false" result))))
  (testing "Long values truncated at 50 chars"
    (let [long-str (apply str (repeat 60 "x"))
          result (orcbrew-val/format-spec-problem {:path [] :pred 'string? :val long-str :via [] :in []})]
      (is (re-find #"\.\.\." result)))))

;; ============================================================================
;; Human-readable error reporting (Dev Console)
;; ============================================================================

(deftest test-humanize-pred-cljs-namespaced-predicates
  (testing "cljs.core/* predicates are humanized, not dumped raw"
    ;; The bug: code matched 'clojure.core/fn but cljs emits 'cljs.core/*,
    ;; so every predicate fell through to a raw form dump.
    (is (= "must be true or false"
           (orcbrew-val/humanize-pred 'cljs.core/boolean? [])))
    (is (= "must be a map of values"
           (orcbrew-val/humanize-pred 'cljs.core/map? [])))
    (is (= "must be a text string"
           (orcbrew-val/humanize-pred 'cljs.core/string? []))))
  (testing "no raw compiler form leaks through"
    (let [pred '(cljs.core/fn [v] (cljs.core/or (cljs.core/= v :disabled?)))
          msg (orcbrew-val/humanize-pred pred [:orcpub.dnd.e5/content-keyword])]
      (is (not (re-find #"cljs.core" msg)))
      (is (re-find #"content-type key" msg)))))

(deftest test-humanize-pred-missing-required-field
  (testing "(contains? % :option-pack) becomes a clear missing-field message"
    (let [pred '(cljs.core/fn [%] (cljs.core/contains? % :option-pack))
          msg (orcbrew-val/humanize-pred pred [:orcpub.dnd.e5/homebrew-item])]
      (is (= "is missing the required field :option-pack" msg)))))

(deftest test-missing-required-key
  ;; spec/keys reports a missing :req-un field as a nested
  ;; (fn [%] (contains? % :k)) form. Extracting :k must work regardless of the
  ;; cljs.core vs clojure.core namespace, and only for an actual contains? form.
  (testing "extracts the missing key from a cljs.core contains? form"
    (is (= :option-pack
           (orcbrew-val/missing-required-key
            '(cljs.core/fn [%] (cljs.core/contains? % :option-pack))))))
  (testing "works for the clojure.core namespace too (JVM-emitted specs)"
    (is (= :name
           (orcbrew-val/missing-required-key
            '(clojure.core/fn [%] (clojure.core/contains? % :name))))))
  (testing "a bare value predicate (no contains?) yields nil"
    (is (nil? (orcbrew-val/missing-required-key 'cljs.core/string?)))
    (is (nil? (orcbrew-val/missing-required-key
               '(cljs.core/fn [%] (cljs.core/pos-int? %)))))))

(deftest test-humanize-pred-via-domain-spec
  (testing "leaf spec name from :via drives the message for anonymous fns"
    ;; When the predicate is an anonymous fn (no concrete name), the most
    ;; specific spec from :via produces a clear, domain-aware message.
    (is (re-find #"source/pack"
                 (orcbrew-val/humanize-pred '(cljs.core/fn [v] true)
                                           [:orcpub.dnd.e5/option-pack])))))

(deftest test-describe-location-map-entry-selectors
  (testing "top level when path is empty"
    (is (= "the top level" (orcbrew-val/describe-location []))))
  (testing "trailing 0 (map-entry key selector) reads as 'the key'"
    (is (= "the key \"Sample Source Book\""
           (orcbrew-val/describe-location ["Sample Source Book" 0]))))
  (testing "trailing 1 (value selector) is dropped, breadcrumb ends at the key"
    (is (= "\"Sample Source Book\" > :orcpub.dnd.e5/subclasses"
           (orcbrew-val/describe-location ["Sample Source Book" 1 :orcpub.dnd.e5/subclasses 1])))))

(deftest test-describe-value-surfaces-item-identity
  (testing "maps surface :name instead of chopped EDN"
    (is (re-find #"Fireball"
                 (orcbrew-val/describe-value {:name "Fireball" :level 3}))))
  (testing "maps without :name show their keys"
    (is (re-find #":war-magic"
                 (orcbrew-val/describe-value {:war-magic {:class :wizard}}))))
  (testing "scalars are still shown directly"
    (is (= "false" (orcbrew-val/describe-value false)))))

(deftest test-format-validation-errors-real-spec
  (testing "end-to-end: a mis-shaped plugin produces readable, non-munged output"
    (let [bad-plugin {"Sample Source Book" {:orcpub.dnd.e5/subclasses
                                           {:war-magic {:class :wizard}}}}
          explain (spec/explain-data ::e5/plugin bad-plugin)
          msg (orcbrew-val/format-validation-errors explain)]
      (is (string? msg))
      (is (re-find #"Validation errors found" msg))
      ;; The headline regression: raw compiler forms must not appear.
      (is (not (re-find #"cljs.core/fn" msg)))
      (is (not (re-find #"clojure.core" msg)))
      ;; And the string plugin key is flagged as the wrong shape.
      (is (re-find #"content-type key" msg)))))

(deftest test-format-validation-errors-caps-output
  (testing "huge problem lists are capped with a remainder note"
    (let [explain {:cljs.spec.alpha/problems
                   (vec (for [i (range 100)]
                          {:pred 'cljs.core/string? :val i :via [] :in [i]}))}
          msg (orcbrew-val/format-validation-errors explain 10)]
      (is (re-find #"and 90 more" msg)))))

;; ============================================================================
;; Normalize Text & count-non-ascii (I13)
;; ============================================================================

(deftest test-normalize-text-in-data-seq-input
  (testing "seq input returns vector (not lazy seq) with normalized strings"
    (let [input (list "h\u00e9llo" "w\u00f6rld")
          result (orcbrew-val/normalize-text-in-data input)]
      (is (vector? result))
      (is (= 2 (count result))))))

(deftest test-normalize-text-common-unicode
  (testing "Smart quotes become straight quotes"
    (is (= "\"Hello\" and 'World'" (orcbrew-val/normalize-text "\u201cHello\u201d and \u2018World\u2019"))))
  (testing "Em-dash and en-dash become hyphens"
    (is (= "foo--bar" (orcbrew-val/normalize-text "foo\u2014bar")))
    (is (= "1-5" (orcbrew-val/normalize-text "1\u20135"))))
  (testing "Ellipsis becomes three dots"
    (is (= "Wait..." (orcbrew-val/normalize-text "Wait\u2026"))))
  (testing "Non-breaking space becomes regular space"
    (is (= "10 ft" (orcbrew-val/normalize-text "10\u00A0ft"))))
  (testing "Zero-width space removed entirely"
    (is (= "nobreak" (orcbrew-val/normalize-text "no\u200Bbreak"))))
  (testing "Plain ASCII string unchanged"
    (is (= "normal text" (orcbrew-val/normalize-text "normal text"))))
  (testing "Non-string input passed through"
    (is (= 42 (orcbrew-val/normalize-text 42)))
    (is (= nil (orcbrew-val/normalize-text nil)))))

;; REGRESSION GUARD — not a stale expectation. count-non-ascii must actually
;; detect non-ASCII. It long returned nil for ALL input in cljs because
;; `(int one-char-string)` is 0 (cljs has no char type), so `(> (int %) 127)`
;; never fired. This guard correctly FAILED until the impl switched to
;; `.charCodeAt`. Do NOT relax it to match the old (broken) behavior.
(deftest test-count-non-ascii
  (testing "All-ASCII string returns nil"
    (is (nil? (orcbrew-val/count-non-ascii "hello world"))))
  (testing "String with non-ASCII returns count and char set"
    (let [result (orcbrew-val/count-non-ascii "caf\u00e9")]
      (is (= 1 (:count result)))
      (is (contains? (:chars result) \u00e9))))
  (testing "Multiple non-ASCII chars counted"
    (let [result (orcbrew-val/count-non-ascii "\u201cHello\u201d")]
      (is (= 2 (:count result)))))
  (testing "Non-string input returns nil"
    (is (nil? (orcbrew-val/count-non-ascii nil)))
    (is (nil? (orcbrew-val/count-non-ascii 42)))))

(deftest test-normalize-text-in-data-recursive
  ;; STALE EXPECTATION corrected. normalize-text normalizes typographic
  ;; punctuation (smart quotes/apostrophes/dashes \u2014 the curated `unicode-to-ascii`
  ;; map) but DELIBERATELY does NOT strip accented letters: accents carry meaning,
  ;; and stripping them silently is data loss. The old `"Caf\u00e9" -> "Cafe"` assertion encoded
  ;; the opposite, abandoned behavior. Accents are surfaced via count-non-ascii
  ;; (warn, don't strip), not normalized away here.
  (testing "Normalizes typographic punctuation but PRESERVES accented letters"
    (let [input {:name "Caf\u00e9"
                 :traits [{:name "Smart\u2019s"
                            :description "Uses \u201cmagic\u201d"}]
                 :level 3}
          result (orcbrew-val/normalize-text-in-data input)]
      (is (= "Caf\u00e9" (:name result)) "accented letter preserved (not stripped)")
      (is (= "Smart's" (get-in result [:traits 0 :name])) "smart apostrophe -> ASCII")
      (is (= "Uses \"magic\"" (get-in result [:traits 0 :description])) "smart quotes -> ASCII")
      (is (= 3 (:level result))))))

;; ============================================================================
;; Nil Cleaning Edge Cases (I4)
;; ============================================================================

(deftest test-clean-nil-in-map-nil-key
  (testing "Map entries with nil keys are removed"
    (let [input {nil nil :name "Test"}
          result (orcbrew-val/clean-nil-in-map-with-log input)]
      (is (not (contains? (:data result) nil)))
      (is (= "Test" (get-in result [:data :name])))
      (is (seq (:changes result)))
      (is (= :removed-nil-key (-> result :changes first :type))))))

(deftest test-clean-nil-preserves-semantic-nils
  (testing "spell-list-kw nil is preserved (means custom spell list)"
    (let [input {:spell-list-kw nil :name "Wizard"}
          result (orcbrew-val/clean-nil-in-map-with-log input)]
      (is (contains? (:data result) :spell-list-kw))
      (is (nil? (get-in result [:data :spell-list-kw])))
      (is (some #(= :preserved-nil (:type %)) (:changes result))))))

(deftest test-clean-nil-removes-numeric-nils
  (testing "Ability score nils are removed (accidental leftover data)"
    (let [input {:str nil :dex 14 :con nil :name "Fighter"}
          result (orcbrew-val/clean-nil-in-map-with-log input)]
      (is (not (contains? (:data result) :str)))
      (is (not (contains? (:data result) :con)))
      (is (= 14 (get-in result [:data :dex])))
      (is (= "Fighter" (get-in result [:data :name]))))))

(deftest test-clean-nil-replaces-with-defaults
  (testing "Known nil fields get replaced with sensible defaults"
    (let [input {:option-pack nil :name "Test Spell"}
          result (orcbrew-val/clean-nil-in-map-with-log input)]
      (is (= "Default Option Source" (get-in result [:data :option-pack])))
      (is (some #(= :replaced-nil (:type %)) (:changes result))))))

(deftest test-validate-import-mixed-nil-scenarios
  (testing "Full pipeline handles plugin with all nil categories"
    (let [plugin-edn (str "{:orcpub.dnd.e5/classes"
                          " {:wizard {:option-pack nil"
                          "           :name \"Wizard\""
                          "           :spellcasting {:spell-list-kw nil}"
                          "           :abilities {:str nil :int 16}}}}")
          result (orcbrew-val/validate-import plugin-edn
                                             {:strategy :progressive
                                              :auto-clean true})]
      (is (:success result))
      (let [wizard (get-in result [:data :orcpub.dnd.e5/classes :wizard])]
        ;; option-pack nil → "Default Option Source" (replaced)
        (is (= "Default Option Source" (:option-pack wizard)))
        ;; spell-list-kw nil → preserved (semantic)
        (is (contains? (:spellcasting wizard) :spell-list-kw))
        (is (nil? (get-in wizard [:spellcasting :spell-list-kw])))
        ;; :str nil → removed, :int 16 → kept
        (is (not (contains? (:abilities wizard) :str)))
        (is (= 16 (get-in wizard [:abilities :int])))))))

;; ============================================================================
;; Selection Option Deduplication Tests
;; ============================================================================

(deftest test-dedup-options-identical-content
  (testing "True duplicates (same name + same content) are collapsed to first"
    (let [options [{:name "Alchemical Homunculus" :description "A tiny construct"}
                   {:name "Alchemical Homunculus" :description "A tiny construct"}]
          [deduped changes] (orcbrew-val/dedup-options-in-selection options)]
      (is (= 1 (count deduped)))
      (is (= "Alchemical Homunculus" (:name (first deduped))))
      (is (= 1 (count changes)))
      (is (= :dedup-identical (:type (first changes)))))))

(deftest test-dedup-options-different-content-renamed
  (testing "Same name but different content gets numbered"
    (let [options [{:name "Bonus" :description "Extra attack"}
                   {:name "Bonus" :description "Extra damage"}]
          [deduped changes] (orcbrew-val/dedup-options-in-selection options)]
      (is (= 2 (count deduped)))
      (is (= "Bonus" (:name (first deduped))))
      (is (= "Bonus 2" (:name (second deduped))))
      (is (= 1 (count changes)))
      (is (= :dedup-renamed (:type (first changes)))))))

(deftest test-dedup-options-no-duplicates
  (testing "Unique options pass through unchanged"
    (let [options [{:name "Option A" :description "Desc A"}
                   {:name "Option B" :description "Desc B"}]
          [deduped changes] (orcbrew-val/dedup-options-in-selection options)]
      (is (= 2 (count deduped)))
      (is (empty? changes)))))

(deftest test-dedup-options-empty-or-short
  (testing "Empty and single-element option lists pass through"
    (let [[d1 c1] (orcbrew-val/dedup-options-in-selection [])
          [d2 c2] (orcbrew-val/dedup-options-in-selection [{:name "Solo"}])]
      (is (= 0 (count d1)))
      (is (empty? c1))
      (is (= 1 (count d2)))
      (is (empty? c2)))))

(deftest test-dedup-options-case-insensitive
  (testing "Names differing only by case are treated as duplicates"
    (let [options [{:name "Bonus Attack" :description "Same"}
                   {:name "bonus attack" :description "Same"}]
          [deduped changes] (orcbrew-val/dedup-options-in-selection options)]
      (is (= 1 (count deduped)))
      (is (= 1 (count changes))))))

(deftest test-dedup-options-in-item-nested
  (testing "Dedup works on selections nested within an item"
    (let [item {:name "My Selection"
                :selections {:companion-choice
                             {:name "Beast Companion"
                              :options [{:name "Homunculus" :description "A tiny construct"}
                                        {:name "Homunculus" :description "A tiny construct"}
                                        {:name "Defender" :description "A medium construct"}]}}}
          [updated changes] (orcbrew-val/dedup-options-in-item item)]
      (is (= 2 (count (get-in updated [:selections :companion-choice :options]))))
      (is (= 1 (count changes))))))

;; REGRESSION GUARD that caught a REAL bug (not stale): the full import pipeline
;; must dedup duplicate options on a top-level homebrew Selection. dedup only
;; walked options nested under an item's :selections map, so an actual
;; :orcpub.dnd.e5/selections item's own :options were never deduped. Fixed in
;; dedup-options-in-item (handles both shapes). If this fails again, the dedup
;; pipeline regressed — fix the CODE.
(deftest test-dedup-options-in-import-full-pipeline
  (testing "Full import pipeline deduplicates selection options"
    (let [plugin-edn (str "{:orcpub.dnd.e5/selections"
                          " {:test-sel {:option-pack \"Test\""
                          "             :name \"Test Selection\""
                          "             :options [{:name \"Alpha\" :description \"A\"}"
                          "                       {:name \"Alpha\" :description \"A\"}"
                          "                       {:name \"Beta\" :description \"B\"}]}}}")
          result (orcbrew-val/validate-import plugin-edn
                                             {:strategy :progressive
                                              :auto-clean true})]
      (is (:success result))
      ;; Should have deduped Alpha (identical content)
      (let [options (get-in result [:data :orcpub.dnd.e5/selections :test-sel :options])]
        (is (= 2 (count options)))
        (is (= #{"Alpha" "Beta"} (set (map :name options)))))
      ;; Should have a dedup change logged
      (is (some #(= :dedup-selection-options (:type %)) (:changes result))))))

;; ============================================================================
;; Tests for format-export-validation-for-log
;; ============================================================================
;; Regression test for "Plugin X has errors: M" bug. In advanced-compilation
;; builds, passing cljs data structures directly to js/console.error renders
;; the munged class name (e.g. "M"). The formatter must return a plain string.

(deftest test-format-export-validation-for-log-missing-required-fields
  (testing "Missing-required-fields branch produces a readable multi-line string"
    (let [validation {:valid false
                      :has-missing-required-fields true
                      :missing-fields-issues
                      [{:content-type :orcpub.dnd.e5/spells
                        :invalid-items [{:key :bad-spell
                                         :name "Bad Spell"
                                         :missing-fields [:level :school]
                                         :traits-missing-names 0}]}]
                      :warnings []
                      :errors ["Some items are missing required fields (names, etc.)"]}
          result (orcbrew-val/format-export-validation-for-log validation)]
      (is (string? result))
      (is (re-find #"Missing required fields" result))
      (is (re-find #"spells" result))
      (is (re-find #"Bad Spell" result))
      (is (re-find #":level" result))
      (is (re-find #":school" result)))))

(deftest test-format-export-validation-for-log-string-errors
  (testing "String :errors (from format-validation-errors) passes through"
    (let [validation {:valid false
                      :errors "Validation errors found:\n  • at root: bad"}
          result (orcbrew-val/format-export-validation-for-log validation)]
      (is (string? result))
      (is (= "Validation errors found:\n  • at root: bad" result)))))

(deftest test-format-export-validation-for-log-vector-errors
  (testing "Vector :errors get joined into a single string"
    (let [validation {:valid false
                      :errors ["first error" "second error"]}
          result (orcbrew-val/format-export-validation-for-log validation)]
      (is (string? result))
      (is (re-find #"first error" result))
      (is (re-find #"second error" result)))))

(deftest test-format-export-validation-for-log-nil-errors
  (testing "Nil :errors does not throw and returns a placeholder string"
    (let [result (orcbrew-val/format-export-validation-for-log
                  {:valid false :errors nil})]
      (is (string? result))
      (is (not (re-find #"null" result))))))

;; ============================================================================
;; Tests for validate-item-for-export (enriched with trait indices)
;; ============================================================================

(deftest test-validate-item-for-export-valid-spell
  (testing "Spell with all required fields passes"
    (let [result (orcbrew-val/validate-item-for-export
                  {:name "Fireball" :level 3 :school "evocation"}
                  :orcpub.dnd.e5/spells)]
      (is (:valid result)))))

(deftest test-validate-item-for-export-missing-spell-fields
  (testing "Spell missing level and school is flagged with both fields"
    (let [result (orcbrew-val/validate-item-for-export
                  {:name "Fireball"}
                  :orcpub.dnd.e5/spells)]
      (is (not (:valid result)))
      (is (= #{:level :school} (set (:missing-fields result)))))))

(deftest test-validate-item-for-export-traits-with-indices
  (testing "Traits missing :name return their indices, not just a count"
    (let [result (orcbrew-val/validate-item-for-export
                  {:name "Fighter"
                   :traits [{:name "Action Surge"}
                            {:description "no name"}
                            {:name "Extra Attack"}
                            {:description "also no name"}]}
                  :orcpub.dnd.e5/classes)]
      (is (not (:valid result)))
      (is (= 2 (:traits-missing-names result)))
      (is (= [{:index 1 :current-name nil}
              {:index 3 :current-name nil}]
             (:traits-needing-names result))))))

(deftest test-validate-item-for-export-no-traits-issue
  (testing "Item with all traits named returns valid"
    (let [result (orcbrew-val/validate-item-for-export
                  {:name "Wizard"
                   :traits [{:name "Arcane Recovery"} {:name "Spellcasting"}]}
                  :orcpub.dnd.e5/classes)]
      (is (:valid result)))))

;; ============================================================================
;; Tests for classify-plugins-for-export
;; ============================================================================

(def valid-plugin
  {:orcpub.dnd.e5/spells
   {:fireball {:option-pack "Test" :name "Fireball" :level 3 :school "evocation"}}})

(def plugin-missing-name
  {:orcpub.dnd.e5/spells
   {:no-name {:option-pack "Test" :level 3 :school "evocation"}}})

(deftest test-classify-clean-plugins
  (testing "All valid plugins go into :clean"
    (let [result (orcbrew-val/classify-plugins-for-export
                  {"Good Plugin" valid-plugin})]
      (is (= 1 (count (:clean result))))
      (is (empty? (:fillable result)))
      (is (empty? (:blockers result))))))

(deftest test-classify-fillable-plugins
  (testing "Plugin with missing required fields goes into :fillable"
    (let [result (orcbrew-val/classify-plugins-for-export
                  {"Missing Name" plugin-missing-name})]
      (is (= 1 (count (:fillable result))))
      (is (empty? (:clean result)))
      (is (empty? (:blockers result)))
      (is (= "Missing Name" (:name (first (:fillable result))))))))

(deftest test-classify-mixed-plugins
  (testing "Mix of clean and fillable sorts correctly"
    (let [result (orcbrew-val/classify-plugins-for-export
                  {"Good" valid-plugin
                   "Bad" plugin-missing-name})]
      (is (= 1 (count (:clean result))))
      (is (= 1 (count (:fillable result))))
      (is (empty? (:blockers result))))))

;; ============================================================================
;; Tests for apply-user-edits-to-plugin
;; ============================================================================

(deftest test-apply-user-edits-basic
  (testing "User-entered name gets written into the plugin"
    (let [plugin {:orcpub.dnd.e5/spells
                  {:my-spell {:option-pack "Test" :level 3 :school "evocation"}}}
          edits {["Test Pack" :orcpub.dnd.e5/spells :my-spell :name] "Meteor Swarm"}
          result (orcbrew-val/apply-user-edits-to-plugin plugin "Test Pack" edits)]
      (is (= "Meteor Swarm"
             (get-in result [:orcpub.dnd.e5/spells :my-spell :name]))))))

(deftest test-apply-user-edits-wrong-plugin-name-ignored
  (testing "Edits for a different plugin name are not applied"
    (let [plugin {:orcpub.dnd.e5/spells
                  {:my-spell {:option-pack "Test"}}}
          edits {["Other Pack" :orcpub.dnd.e5/spells :my-spell :name] "Nope"}
          result (orcbrew-val/apply-user-edits-to-plugin plugin "Test Pack" edits)]
      (is (not= "Nope"
                (get-in result [:orcpub.dnd.e5/spells :my-spell :name]))))))

(deftest test-apply-user-edits-nan-rejected
  (testing "NaN values are not written into the plugin"
    (let [plugin {:orcpub.dnd.e5/spells
                  {:my-spell {:option-pack "Test" :name "X" :school "evocation"}}}
          edits {["P" :orcpub.dnd.e5/spells :my-spell :level] js/NaN}
          result (orcbrew-val/apply-user-edits-to-plugin plugin "P" edits)]
      ;; NaN rejected, fill-missing fills :level with dummy (0)
      (is (= 0 (get-in result [:orcpub.dnd.e5/spells :my-spell :level]))))))

(deftest test-apply-user-edits-blank-string-rejected
  (testing "Blank strings are not written into the plugin"
    (let [plugin {:orcpub.dnd.e5/spells
                  {:my-spell {:option-pack "Test" :level 3 :school "evocation"}}}
          edits {["P" :orcpub.dnd.e5/spells :my-spell :name] "   "}
          result (orcbrew-val/apply-user-edits-to-plugin plugin "P" edits)]
      ;; Blank rejected, fill-missing fills :name with dummy
      (is (= "Missing Spell Name"
             (get-in result [:orcpub.dnd.e5/spells :my-spell :name]))))))

(deftest test-apply-user-edits-nil-rejected
  (testing "Nil values are not written into the plugin"
    (let [plugin {:orcpub.dnd.e5/spells
                  {:my-spell {:option-pack "Test" :level 3 :school "evocation"}}}
          edits {["P" :orcpub.dnd.e5/spells :my-spell :name] nil}
          result (orcbrew-val/apply-user-edits-to-plugin plugin "P" edits)]
      (is (= "Missing Spell Name"
             (get-in result [:orcpub.dnd.e5/spells :my-spell :name]))))))

(deftest test-apply-user-edits-trait-name
  (testing "Trait name edits apply to the correct index"
    (let [plugin {:orcpub.dnd.e5/classes
                  {:fighter {:option-pack "Test" :name "Fighter"
                             :traits [{:name "Action Surge"}
                                      {:description "unnamed"}
                                      {:name "Extra Attack"}]}}}
          edits {["P" :orcpub.dnd.e5/classes :fighter :trait 1 :name] "Second Wind"}
          result (orcbrew-val/apply-user-edits-to-plugin plugin "P" edits)]
      (is (= "Second Wind"
             (get-in result [:orcpub.dnd.e5/classes :fighter :traits 1 :name]))))))
;; ============================================================================
;; strip-export-blanks — don't export meaningless nils/falses/empties.
;; ============================================================================

(deftest strip-export-blanks-drops-meaningless
  (testing "drops false / nil / empty-collection map values"
    (is (= {:a true}
           (orcbrew-val/strip-export-blanks {:a true :b false :c nil :d [] :e {}}))))
  (testing "keeps real values (true, numbers, strings, non-empty colls)"
    (is (= {:a true :n 3 :s "x" :v [1 2]}
           (orcbrew-val/strip-export-blanks {:a true :n 3 :s "x" :v [1 2]}))))
  (testing "recurses and drops a map that becomes empty after cleaning"
    (is (= {:keep true}
           (orcbrew-val/strip-export-blanks {:keep true :components {:somatic false}})))))

(deftest strip-export-blanks-keeps-meaningful-blanks
  (testing "keeps nil for the keep-nil keys (nil is a real answer there)"
    (is (= {:spell-list-kw nil :ability nil :class-key nil}
           (orcbrew-val/strip-export-blanks {:spell-list-kw nil :ability nil :class-key nil}))))
  (testing "keeps [prof-kw first-class?] pairs intact (vector elements not dropped)"
    ;; the multiclass 'first-class-only' rule lives in pairs, NOT {k false} maps
    (is (= {:armor-profs [[:heavy false] [:medium true]]}
           (orcbrew-val/strip-export-blanks {:armor-profs [[:heavy false] [:medium true]]})))))

(deftest strip-export-blanks-roundtrip-safe
  (testing "the toggle false-cruft is removed, real proficiencies kept, still valid"
    (let [plugin (read-string
                  (str "{:orcpub.dnd.e5/feats"
                       " {:lucky {:option-pack \"P\" :name \"Lucky\" :key :lucky"
                       "          :disabled? false"            ; meaningless -> drop
                       "          :props {:skill-prof {:athletics true"   ; keep
                       "                               :stealth false"    ; cruft -> drop
                       "                               :arcana false}}}}}"))   ; cruft -> drop
          stripped (orcbrew-val/strip-export-blanks plugin)
          skills (get-in stripped [:orcpub.dnd.e5/feats :lucky :props :skill-prof])]
      ;; real proficiency kept, false-cruft gone
      (is (= {:athletics true} skills) "kept the real prof, dropped the false ones")
      ;; meaningless :disabled? false removed
      (is (not (contains? (get-in stripped [:orcpub.dnd.e5/feats :lucky]) :disabled?))
          "dropped :disabled? false")
      ;; required real values untouched
      (is (= "Lucky" (get-in stripped [:orcpub.dnd.e5/feats :lucky :name])))
      (is (= :lucky (get-in stripped [:orcpub.dnd.e5/feats :lucky :key])))
      ;; still a valid plugin after stripping
      (is (spec/valid? :orcpub.dnd.e5/plugin stripped)
          (str "stripped plugin must stay spec-valid: "
               (spec/explain-str :orcpub.dnd.e5/plugin stripped))))))

;; ---------------------------------------------------------------------------
;; Real-content cruft shapes (from the orcbrew catalog survey — 146 files).
;; Mirrors test/fixtures/cruft-shapes.orcbrew. The two real-world patterns are
;; false-cruft :spell-lists (every off-class stored as false — e.g. Faiths of the
;; Forgotten Realms :searing-song) and a {nil nil} stray entry (UA Sidekicks, the
;; UA Artificer). Proves the whole loop: LOADS without quarantine -> strip cleans
;; -> both items STILL pass their per-type save spec (nothing meaningful lost).
;; ---------------------------------------------------------------------------

(def cruft-shapes-edn
  (str "{:orcpub.dnd.e5/spells"
       " {:test-cantrip {:name \"Test Cantrip\" :key :test-cantrip"
       "                 :option-pack \"Cruft Shapes\" :level 0 :school \"evocation\""
       "                 :spell-lists {:wizard true :cleric true"
       "                               :bard false :druid false :paladin false"
       "                               :ranger false :sorcerer false :warlock false}"
       "                 :ritual false :material false}}"
       " :orcpub.dnd.e5/classes"
       " {:test-sidekick {nil nil"
       "                  :name \"Test Sidekick\" :key :test-sidekick"
       "                  :option-pack \"Cruft Shapes\" :hit-die 8"
       "                  :profs {:skill {:athletics true :stealth false :arcana false}}}}}"))

(defn- deep-has? [pred x]
  (cond (map? x) (or (some pred (keys x)) (some pred (vals x))
                     (some #(deep-has? pred %) (vals x)))
        (coll? x) (some #(deep-has? pred %) x)
        :else (pred x)))

(deftest real-cruft-shapes-load-strip-stay-valid
  (testing "real false-cruft + {nil nil} shapes: load clean, strip cleans, stay save-valid"
    (let [plugin  (read-string cruft-shapes-edn)
          spell-spec (content-specs/save-spec-for :orcpub.dnd.e5/spells)
          class-spec (content-specs/save-spec-for :orcpub.dnd.e5/classes)]
      ;; 1. LOADS without false quarantine (the loose floor keeps it)
      (is (content-specs/valid-for-load? plugin) "real cruft shapes must load, not quarantine")
      ;; 2. both items are save-valid even WITH the cruft (cruft is in non-req fields)
      (is (spec/valid? spell-spec (get-in plugin [:orcpub.dnd.e5/spells :test-cantrip])))
      (is (spec/valid? class-spec (get-in plugin [:orcpub.dnd.e5/classes :test-sidekick])))
      ;; 3. STRIP removes the cruft
      (let [stripped (orcbrew-val/strip-export-blanks plugin)
            spell    (get-in stripped [:orcpub.dnd.e5/spells :test-cantrip])
            klass    (get-in stripped [:orcpub.dnd.e5/classes :test-sidekick])]
        (is (= {:wizard true :cleric true} (:spell-lists spell))
            "false-cruft classes dropped, real trues kept")
        (is (not (contains? spell :ritual)) "dropped :ritual false")
        (is (not (contains? klass nil)) "dropped the {nil nil} stray entry")
        (is (= {:athletics true} (get-in klass [:profs :skill])) "dropped nested false skills")
        (is (not (deep-has? nil? stripped)) "no nil key/value survives anywhere")
        (is (not (deep-has? false? stripped)) "no false value survives anywhere")
        ;; 4. after stripping, both items STILL pass their save spec (nothing lost)
        (is (spec/valid? spell-spec spell)
            (str "stripped spell must stay valid: " (spec/explain-str spell-spec spell)))
        (is (spec/valid? class-spec klass)
            (str "stripped class must stay valid: " (spec/explain-str class-spec klass)))
        ;; and the whole plugin re-loads clean
        (is (content-specs/valid-for-load? stripped) "stripped plugin still load-valid")))))

(deftest sanitize-item-names-coerces-invalid-names-and-rekeys
  (testing "an invalid/blank name is replaced with a valid placeholder and re-keyed
            so save-anyway can never persist a broken key"
    ;; the reported bug: "1@-asdml;" doesn't start with a letter
    (let [out (orcbrew-val/sanitize-item-names {:name "1@-asdml;"} "Race")]
      (is (common/starts-with-letter? (:name out)) "name now starts with a letter")
      (is (common/keyword-starts-with-letter? (:key out)) "key is valid (starts with a letter)")
      (is (= "Unnamed Race" (:name out))))
    (let [out (orcbrew-val/sanitize-item-names {:name "   "} "Spell")]
      (is (= "Unnamed Spell" (:name out)) "blank/whitespace name -> placeholder"))
    ;; a valid name is left intact (trimmed) and re-keyed consistently
    (let [out (orcbrew-val/sanitize-item-names {:name "  Aarakocra  "} "Race")]
      (is (= "Aarakocra" (:name out)))
      (is (= :aarakocra (:key out))))
    ;; nested option/trait names are coerced too
    (let [out (orcbrew-val/sanitize-item-names
               {:name "Fighter" :options [{:name "9 Lives"} {:name "Valid Option"}]}
               "Class")]
      (is (common/starts-with-letter? (get-in out [:options 0 :name])) "invalid nested name coerced")
      (is (= "Valid Option" (get-in out [:options 1 :name])) "valid nested name kept"))))
