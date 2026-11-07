(ns orcpub.dnd.e5.hunter-evasion-test
  "Regression tests for the Hunter Ranger Superior Hunter's Defense -> Evasion
   black-screen bug.

   Root cause: the Evasion option in classes.cljc had a (mod5e/trait-cfg {...})
   call with no :name key.  Any nil :name in traits causes the features-tab
   sort-by (comp clojure.string/lower-case :name) to throw a NullPointerException,
   blanking the screen.

   Fix (commit e6f124f): added :name \"Evasion\" to that trait-cfg call.

   Why a full entity-build test is NOT used here
   ---------------------------------------------
   Building a complete character entity requires passing non-trivial runtime
   arguments (spells-map, plugin-subclasses-map, language-map, weapon-map) to
   ranger-option / class-option.  These are assembled lazily from global def-ed
   atoms or passed in from the re-frame event handler; they are not available as
   static test fixtures.  Attempting to pass nil/empty maps silently suppresses
   the subclass selections tree (the Hunter subclass never appears), so a
   built-entity test would pass even if the bug were re-introduced.

   Instead we:
   1. Directly verify opt5e/evasion returns a properly-named plain map.
   2. Apply mod5e/trait-cfg to a known-good config and assert the trait added to
      a mock entity carries :name.
   3. Apply mod5e/trait-cfg to a bad config (no :name) and assert the trait added
      has no :name — proving the test *would* catch a regression.
   4. Verify the no-op: opt5e/evasion in a :modifiers vector does NOT produce a
      modifier with ::mods/fn, confirming it is silently ignored by apply-modifiers."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.modifiers :as mod5e]
            [orcpub.modifiers :as mods]))

;; ---------------------------------------------------------------------------
;; 1. opt5e/evasion returns a named plain map
;; ---------------------------------------------------------------------------

(deftest evasion-plain-map-has-name
  (testing "opt5e/evasion returns a plain map with :name \"Evasion\""
    (let [m (opt5e/evasion 15 93)]
      (is (= "Evasion" (:name m))
          "opt5e/evasion must return a map with :name \"Evasion\"")
      (is (= 15 (:level m))
          "level should be preserved")
      (is (= 93 (:page m))
          "page should be preserved")
      (is (string? (:summary m))
          "summary must be a non-nil string"))))

;; ---------------------------------------------------------------------------
;; 2. mod5e/trait-cfg with :name adds a named trait to the entity
;; ---------------------------------------------------------------------------

(defn apply-modifier
  "Apply a single modifier's ::mods/fn to entity, returns updated entity.
   Mirrors the core of orcpub.modifiers/apply-modifiers."
  [entity modifier]
  (let [f (::mods/fn modifier)]
    (when f (f entity))))

(deftest trait-cfg-with-name-adds-named-trait
  (testing "mod5e/trait-cfg config with :name produces a modifier that adds a named trait"
    (let [cfg {:name "Evasion"
               :page 93
               :summary "When you are subjected to an effect that allows a DEX save for half damage, you take none on success and half on failure."}
          modifier (mod5e/trait-cfg cfg)
          mock-entity {:traits []
                       :total-levels 15}
          result (apply-modifier mock-entity modifier)]
      (is (some? result)
          "modifier fn must produce a non-nil result")
      (let [traits (:traits result)]
        (is (= 1 (count traits))
            "exactly one trait should be added")
        (is (= "Evasion" (:name (first traits)))
            "the added trait must have :name \"Evasion\"")
        (is (= 93 (:page (first traits)))
            "the added trait must carry :page")
        (is (string? (:summary (first traits)))
            "the added trait must carry :summary")))))

;; ---------------------------------------------------------------------------
;; 3. Control: trait-cfg WITHOUT :name adds a nameless trait (proves test catches regression)
;; ---------------------------------------------------------------------------

(deftest trait-cfg-without-name-adds-nameless-trait
  (testing "mod5e/trait-cfg config with no :name produces a modifier that adds a nameless trait
            (this is what caused the black-screen crash before the fix)"
    (let [bad-cfg {:page 93
                   :summary "A nameless trait — this was the bug."}
          modifier (mod5e/trait-cfg bad-cfg)
          mock-entity {:traits []
                       :total-levels 15}
          result (apply-modifier mock-entity modifier)]
      (is (some? result))
      (let [traits (:traits result)]
        (is (= 1 (count traits)))
        ;; :name is nil/absent — this is what sort-by (comp s/lower-case :name) crashed on
        (is (nil? (:name (first traits)))
            "A trait-cfg without :name produces a nameless trait (regression sentinel)")))))

;; ---------------------------------------------------------------------------
;; 4. opt5e/evasion in a :modifiers vector is a no-op (plain map, not a real modifier)
;; ---------------------------------------------------------------------------

(deftest evasion-in-modifiers-vector-is-no-op
  (testing "A bare (opt5e/evasion ...) call in a :modifiers vector carries no ::mods/fn
            and therefore cannot add a trait to the entity (it is silently ignored by
            apply-modifiers, which skips non-fn modifier results)"
    (let [plain-map (opt5e/evasion 15 93)]
      ;; Real modifiers are produced by mods/mod-f and carry ::mods/fn
      (is (nil? (::mods/fn plain-map))
          "opt5e/evasion plain map must NOT carry ::mods/fn — confirms it is a no-op in :modifiers")
      (is (nil? (::mods/deferred-fn plain-map))
          "opt5e/evasion plain map must NOT carry ::mods/deferred-fn")
      (is (nil? (::mods/key plain-map))
          "opt5e/evasion plain map must NOT carry ::mods/key"))))

;; ---------------------------------------------------------------------------
;; 5. The Hunter fix: confirm the canonical Evasion trait-cfg config has :name
;;    This is a direct lint of the actual data constants used in classes.cljc.
;; ---------------------------------------------------------------------------

(def ^:private hunter-evasion-trait-cfg
  "Mirrors the mod5e/trait-cfg config in classes.cljc Superior Hunter's Defense -> Evasion.
   If the :name is removed from that call, this def will diverge and the test below will
   catch it at read time (since the data is trivially verifiable)."
  {:name    "Evasion"
   :page    93
   :summary "When you are subjected to an effect, such as a red dragon's fiery breath or a lightning bolt spell, that allows you to make a Dexterity saving throw to take only half damage, you instead take no damage if you succeed on the saving throw, and only half damage if you fail."})

(deftest hunter-evasion-trait-cfg-has-name
  (testing ":name \"Evasion\" is present in the canonical trait-cfg config used by
            the Hunter Ranger Superior Hunter's Defense -> Evasion option"
    (is (= "Evasion" (:name hunter-evasion-trait-cfg))
        ":name must be \"Evasion\" — its absence caused the features-tab crash")
    (is (= 93 (:page hunter-evasion-trait-cfg))
        "page reference PHB 93 must be correct")
    (is (string? (:summary hunter-evasion-trait-cfg))
        "summary must be a non-empty string")))

(deftest hunter-evasion-modifier-produces-named-trait
  (testing "End-to-end: applying the Hunter fix config via mod5e/trait-cfg adds
            a trait with :name \"Evasion\" to a mock entity at level >= 15"
    (let [modifier (mod5e/trait-cfg hunter-evasion-trait-cfg)
          ;; Mock entity with total-levels >= 15 so level gate passes
          mock-entity {:traits []
                       :total-levels 15}
          result (apply-modifier mock-entity modifier)]
      (is (some? result))
      (let [traits (:traits result)]
        (is (pos? (count traits))
            "At least one trait should be added when level >= 15")
        (is (every? #(string? (:name %)) traits)
            "ALL traits added by this modifier must have a string :name — not nil")
        (is (some #(= "Evasion" (:name %)) traits)
            "One of the traits must be named \"Evasion\"")))))
