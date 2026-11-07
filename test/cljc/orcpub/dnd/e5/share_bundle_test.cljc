(ns orcpub.dnd.e5.share-bundle-test
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.share-bundle :as sb]))

;; A homebrew library that exercises every extraction edge:
;;   - a homebrew race that grants a homebrew language BY NAME
;;   - a homebrew subclass whose parent class is also homebrew (subclass->class)
;;   - a homebrew subrace whose parent race is also homebrew (subrace->race)
;;   - a homebrew spell that belongs to the homebrew class's spell list
;;     (the REVERSE :spell-lists edge)
;;   - an unrelated homebrew spell that must NOT be pulled
(def plugins
  {"My Source"
   {:orcpub.dnd.e5/classes    {:my-class {:name "My Class"}}
    :orcpub.dnd.e5/subclasses {:my-sub   {:name "My Sub" :class :my-class}}
    :orcpub.dnd.e5/races      {:my-race  {:name "My Race" :languages #{"My Lang"}}}
    :orcpub.dnd.e5/subraces   {:my-subrace {:name "My Subrace" :race :my-race}}
    :orcpub.dnd.e5/languages  {:my-lang  {:name "My Lang"}}
    :orcpub.dnd.e5/spells     {:my-spell    {:name "My Spell"  :spell-lists {:my-class true}}
                               :other-spell {:name "Other Spell" :spell-lists {:wizard true}}}
    :disabled? false}})

;; Character selects the homebrew subrace (=> pulls its parent race + that race's
;; granted language) and a base Fighter with the homebrew subclass (=> pulls the
;; subclass's parent homebrew class => reverse-pulls the class's spell list).
(def character
  {::entity/options
   {:race {::entity/key :my-race
           ::entity/options {:subrace {::entity/key :my-subrace}}}
    :class [{::entity/key :fighter          ;; base class, not homebrew
             ::entity/options {:martial-archetype {::entity/key :my-sub}}}]}})

(deftest selected-keys-walks-the-whole-tree
  (testing "flatten sweep collects keys at every depth, not just the top level"
    (let [ks (sb/selected-keys character)]
      (is (contains? ks :my-race))
      (is (contains? ks :my-subrace))
      (is (contains? ks :fighter))
      (is (contains? ks :my-sub)))))

(deftest extract-bundle-pulls-the-full-closure
  (let [bundle (sb/extract-bundle character plugins)
        c (get bundle "My Source")]
    (testing "direct homebrew selections are included"
      (is (contains? (:orcpub.dnd.e5/races c) :my-race))
      (is (contains? (:orcpub.dnd.e5/subraces c) :my-subrace))
      (is (contains? (:orcpub.dnd.e5/subclasses c) :my-sub)))
    (testing "subclass->class edge pulls the parent homebrew class"
      (is (contains? (:orcpub.dnd.e5/classes c) :my-class)))
    (testing "race->language-by-NAME edge resolves via name-to-kw"
      (is (contains? (:orcpub.dnd.e5/languages c) :my-lang)))
    (testing "reverse :spell-lists edge pulls the class's spells"
      (is (contains? (:orcpub.dnd.e5/spells c) :my-spell)))
    (testing "unrelated homebrew spell is NOT pulled"
      (is (not (contains? (:orcpub.dnd.e5/spells c) :other-spell))))
    (testing "the base (non-homebrew) fighter is not emitted as content"
      (is (not (contains? (:orcpub.dnd.e5/classes c) :fighter))))))

(deftest empty-and-vanilla-characters-yield-empty-bundles
  (testing "a character with no homebrew selections produces nothing to bundle"
    (is (empty? (sb/extract-bundle
                 {::entity/options {:race {::entity/key :elf}}}
                 plugins))))
  (testing "no plugins => empty bundle regardless of selections"
    (is (empty? (sb/extract-bundle character {})))))

;; ── Structural whitelist (security) ──────────────────────────────────────────

(deftest whitelist-rejects-hostile-shapes-keeps-valid
  (testing "a well-formed bundle passes through intact, nothing dropped"
    (let [good {"Src" {:orcpub.dnd.e5/spells {:my-spell {:name "My Spell" :level 1}}}}
          {:keys [bundle dropped]} (sb/whitelist-bundle good)]
      (is (= good bundle))
      (is (zero? dropped))))
  (testing "unknown content types are dropped (e.g. an attempt to smuggle db shape)"
    (let [{:keys [bundle dropped]}
          (sb/whitelist-bundle {"Src" {:re-frame/db {:x 1}
                                        :orcpub.dnd.e5/spells {:ok {:name "Ok"}}}})]
      (is (= {"Src" {:orcpub.dnd.e5/spells {:ok {:name "Ok"}}}} bundle))
      (is (pos? dropped))))
  (testing "non-keyword / non-letter-leading item keys and non-map defs are dropped"
    (let [{:keys [bundle dropped]}
          (sb/whitelist-bundle {"Src" {:orcpub.dnd.e5/spells
                                        {"stringkey" {:name "bad"}   ;; not a keyword
                                         :-danger {:name "bad"}      ;; doesn't start with letter
                                         :good {:name "good"}        ;; valid
                                         :also-bad "not-a-map"}}})]   ;; def not a map
      (is (= {"Src" {:orcpub.dnd.e5/spells {:good {:name "good"}}}} bundle))
      (is (= 3 dropped))))
  (testing "non-string source and non-map input fail closed"
    (is (= {} (:bundle (sb/whitelist-bundle {:not-a-string {:orcpub.dnd.e5/spells {:s {}}}}))))
    (is (= {} (:bundle (sb/whitelist-bundle "not even a map"))))
    (is (= {} (:bundle (sb/whitelist-bundle [1 2 3]))))))

(deftest used-custom-items-keeps-equipped-raw-items
  ;; Matching uses the app's OWN expand (injected) to get each item's real key(s)
  ;; — never a hand-rolled name-to-kw — and keeps a raw item if the character
  ;; selected ANY of its expanded (subtype) variants.
  (let [char {::entity/options {:class [{::entity/key :fighter
                                         ::entity/options {:magic-weapons [{::entity/key :flametongue-sword}]}}]}}
        raw [{:orcpub.dnd.e5.magic-items/name "Flametongue"}
             {:orcpub.dnd.e5.magic-items/name "Unused Ring"}]
        expand-one (fn [it]
                     (if (= "Flametongue" (:orcpub.dnd.e5.magic-items/name it))
                       [{:key :flametongue-sword} {:key :flametongue-axe}]
                       [{:key :unused-ring}]))]
    (is (= [{:orcpub.dnd.e5.magic-items/name "Flametongue"}]
           (sb/used-custom-items char raw expand-one)))))

(deftest whitelist-shared-handles-container-and-legacy
  (testing "container: plugins whitelisted, well-formed raw items kept, bad ones dropped"
    (let [r (sb/whitelist-shared
             {:plugins {"S" {:orcpub.dnd.e5/spells {:ok {:name "Ok"}}}}
              :custom-items [{:orcpub.dnd.e5.magic-items/name "Sword of X"} ;; good
                             {:orcpub.dnd.e5.magic-items/name "-bad"}       ;; name not letter-leading
                             {:name "wrong name key"}                        ;; missing ::mi5e/name
                             "not a map"]})]                                 ;; not a map
      (is (= {"S" {:orcpub.dnd.e5/spells {:ok {:name "Ok"}}}} (:plugins r)))
      (is (= 1 (count (:custom-items r))))
      (is (= "Sword of X" (:orcpub.dnd.e5.magic-items/name (first (:custom-items r)))))
      (is (= 3 (:dropped r)))))
  (testing "legacy bare plugins map still decodes (no :custom-items)"
    (let [r (sb/whitelist-shared {"S" {:orcpub.dnd.e5/spells {:ok {:name "Ok"}}}})]
      (is (= {"S" {:orcpub.dnd.e5/spells {:ok {:name "Ok"}}}} (:plugins r)))
      (is (empty? (:custom-items r))))))

(deftest collisions-flags-differing-shared-keys
  (let [shared {"Shared" {:orcpub.dnd.e5/spells {:fireball  {:name "Fireball" :level 9}
                                                 :new-spell {:name "New Spell"}}}}
        lib    {"Mine"   {:orcpub.dnd.e5/spells {:fireball  {:name "Fireball" :level 3}}}}]
    (testing "a key in both sides with a DIFFERENT def is a collision"
      (let [colls (sb/collisions shared lib)]
        (is (= 1 (count colls)))
        (is (= :fireball (:key (first colls))))
        (is (= "Fireball" (:name (first colls))))))
    (testing "identical defs are NOT collisions"
      (is (empty? (sb/collisions {"S" {:orcpub.dnd.e5/spells {:fireball {:name "Fireball" :level 3}}}}
                                 lib))))
    (testing "keys only on one side are NOT collisions"
      (is (empty? (sb/collisions {"S" {:orcpub.dnd.e5/spells {:only-shared {:name "X"}}}}
                                 lib))))))
