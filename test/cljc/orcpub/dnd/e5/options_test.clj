(ns orcpub.dnd.e5.options-test
  (:require [clojure.test :refer [is deftest testing]]
            [clojure.spec.alpha :as spec]
            [orcpub.dnd.e5.options :as opt]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.template :as t]
            [orcpub.entity :as entity]))

(deftest test-total-slots
  (is (= {1 2} (opt/total-slots 3 3)))
  (is (= {1 4
          2 3
          3 3
          4 1}
         (opt/total-slots 20 3))))

;; -- feat-prereqs --

(deftest feat-prereqs-ability-prereq
  (testing "ability key produces ability prereq with min 13"
    (let [result (opt/feat-prereqs [::char5e/str] nil {})]
      (is (= 1 (count result)))
      (is (= "Requires STR 13 or higher" (::t/label (first result))))
      (is (fn? (::t/prereq-fn (first result)))))))

(deftest feat-prereqs-spellcasting-prereq
  (testing ":spellcasting produces can-cast-spell prereq"
    (let [result (opt/feat-prereqs [:spellcasting] nil {})]
      (is (= 1 (count result)))
      (is (= "Requires the ability to cast at least one spell."
             (::t/label (first result)))))))

(deftest feat-prereqs-armor-prereq
  (testing "non-ability non-spellcasting key produces armor prereq"
    (let [result (opt/feat-prereqs [:heavy] nil {})]
      (is (= 1 (count result)))
      (is (re-find #"(?i)heavy" (::t/label (first result)))))))

(deftest feat-prereqs-race-prereq-from-map
  (testing "race prereq resolves names from race-map parameter"
    (let [race-map {:elf {:name "Elf"} :dwarf {:name "Dwarf"}}
          path-prereqs {:race {:elf true :dwarf false}}
          result (opt/feat-prereqs [] path-prereqs race-map)
          labels (map ::t/label result)]
      ;; Only :elf has truthy value, :dwarf is false
      (is (= 1 (count result)))
      (is (some #(re-find #"Elf" %) labels)))))

(deftest feat-prereqs-no-race-prereq-when-empty
  (testing "no race prereqs when path-prereqs has no :race key"
    (let [result (opt/feat-prereqs [] {} {:elf {:name "Elf"}})]
      (is (empty? result))))
  (testing "no race prereqs when race map values are all false"
    (let [result (opt/feat-prereqs [] {:race {:elf false}} {:elf {:name "Elf"}})]
      (is (empty? result)))))

(deftest feat-prereqs-mixed-ability-and-race
  (testing "both ability and race prereqs combine"
    (let [race-map {:human {:name "Human"}}
          path-prereqs {:race {:human true}}
          result (opt/feat-prereqs [::char5e/str] path-prereqs race-map)]
      ;; 1 ability + 1 race
      (is (= 2 (count result))))))

(deftest missing-spell-keys-flags-undefined-refs
  (testing "returns spell-list keys with no definition in spells-map (dangling
            imported references), empty when all resolve"
    (let [spells-map {:fireball {:name "Fireball"} :magic-missile {:name "Magic Missile"}}]
      ;; :guiding-hand referenced but not defined -> flagged
      (is (= #{:guiding-hand}
             (opt/missing-spell-keys {0 #{:magic-missile}
                                      1 #{:fireball :guiding-hand}}
                                     spells-map)))
      ;; everything defined -> empty
      (is (= #{}
             (opt/missing-spell-keys {1 #{:fireball :magic-missile}} spells-map)))
      ;; multiple missing across levels
      (is (= #{:guiding-hand :sudden-awakening}
             (opt/missing-spell-keys {0 #{:guiding-hand} 1 #{:fireball :sudden-awakening}}
                                     spells-map))))))

(deftest spell-key-alias-targets-all-exist
  (testing "every spell-key-aliases target resolves to a real spell in spell-map
            (guards against typos and future SRD data drift)"
    (doseq [[old-key target] spells/spell-key-aliases]
      (is (contains? spells/spell-map target)
          (str old-key " aliases to " target " which is missing from spell-map")))))

(deftest resolve-spell-key-is-non-destructive
  (testing "resolve-spell-key only remaps a known rename whose target is loaded"
    (let [spells-map {:secret-chest {:name "Secret Chest"}
                      :leomunds-secret-chest {:name "Homebrew Secret Chest"}
                      :fireball {:name "Fireball"}}]
      ;; loaded homebrew under the old key wins — never overridden
      (is (= :leomunds-secret-chest
             (spells/resolve-spell-key spells-map :leomunds-secret-chest)))
      ;; a loaded, non-aliased key is returned as-is
      (is (= :fireball (spells/resolve-spell-key spells-map :fireball)))
      ;; a genuinely unknown key is left alone (stays flagged), not guessed
      (is (= :made-up-spell (spells/resolve-spell-key spells-map :made-up-spell))))
    (let [spells-map {:secret-chest {:name "Secret Chest"} :floating-disk {:name "Floating Disk"}}]
      ;; dangling old-name ref with the target loaded -> remapped to canonical
      (is (= :secret-chest (spells/resolve-spell-key spells-map :leomunds-secret-chest)))
      (is (= :floating-disk (spells/resolve-spell-key spells-map :tensers-floating-disk)))
      ;; a known alias whose target is NOT loaded stays as-is (still flagged)
      (is (= :bigbys-hand (spells/resolve-spell-key spells-map :bigbys-hand))))))
