(ns orcpub.dnd.e5.content-specs-test
  "B4 drift guard: prove the invariant `save ⊆ load` for every homebrew content
   type — anything a SAVE spec accepts, the LOOSE LOAD floor must also accept.

   Why it matters: save and load validate independently. If the load floor ever
   grows stricter than a save spec (someone 'tightens validation on import'), then
   content that saved fine would be QUARANTINED on the next boot — a data-loss-
   adjacent surprise. This test goes red the moment that subset relation breaks,
   so the loose-load backward-compat guarantee can't silently erode.

   It also pins the registry itself: every content type with a save handler is in
   `content-specs/save-specs` exactly once, and each entry names a real spec."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.spec.alpha :as spec]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.content-specs :as cs]
            ;; loaded for their side-effecting spec/defs (referenced via cs/save-specs)
            [orcpub.dnd.e5.classes]
            [orcpub.dnd.e5.races]
            [orcpub.dnd.e5.feats]
            [orcpub.dnd.e5.backgrounds]
            [orcpub.dnd.e5.languages]
            [orcpub.dnd.e5.monsters]
            [orcpub.dnd.e5.encounters]
            [orcpub.dnd.e5.selections]
            [orcpub.dnd.e5.spells]
            [orcpub.common :as common]))

;; A valid, save-passing item per content type (name/key filled in below). The
;; extra fields are the type-specific requirements (monster hit-points, spell
;; level/school/spell-lists, subrace :race, subclass :class, …). Keyed by the
;; content-type keyword so we can pair each base with its save spec from the
;; registry — no second copy of the type→spec list.
(def valid-bases
  {::e5/spells      {:option-pack "Pack" :school "evocation" :level 1
                     :spell-lists {:wizard true}}
   ::e5/monsters    {:option-pack "Pack" :hit-points {:die 8 :die-count 1}}
   ::e5/encounters  {:option-pack "Pack"}
   ::e5/backgrounds {:option-pack "Pack"}
   ::e5/languages   {:option-pack "Pack"}
   ::e5/invocations {:option-pack "Pack"}
   ::e5/boons       {:option-pack "Pack"}
   ::e5/selections  {:option-pack "Pack"}
   ::e5/feats       {:option-pack "Pack"}
   ::e5/races       {:option-pack "Pack"}
   ::e5/subraces    {:option-pack "Pack" :race :elf}
   ::e5/subclasses  {:option-pack "Pack" :class :wizard}
   ::e5/classes     {:option-pack "Pack"}})

(defn- named [base]
  (assoc base :name "Valid Name" :key (common/name-to-kw "Valid Name")))

;; --- registry integrity ----------------------------------------------------

(deftest registry-covers-every-base-with-a-real-spec
  (testing "every content type has a base sample and a registered spec, 1:1"
    (is (= (set (keys cs/save-specs)) (set (keys valid-bases)))
        "save-specs and the test's valid-bases must describe the same content types")
    (doseq [[ct spec-kw] cs/save-specs]
      (is (some? (spec/get-spec spec-kw))
          (str ct " → " spec-kw " is not a registered spec")))))

(deftest bases-are-save-valid-and-load-valid
  (testing "each canonical base passes BOTH its strict save spec and the loose
            load floor — the subset relation, proven on a concrete sample"
    (doseq [[ct spec-kw] cs/save-specs]
      (let [item (named (valid-bases ct))]
        (is (spec/valid? spec-kw item)
            (str ct " base should be save-valid. explain: "
                 (spec/explain-str spec-kw item)))
        (is (spec/valid? cs/load-item-spec item)
            (str ct " base should also satisfy the load floor "
                 cs/load-item-spec))))))

;; --- generative save ⊆ load -------------------------------------------------

;; :option-pack takes mostly strings (save-valid) but sometimes a non-string, so
;; the property exercises the case where save and load MUST agree to reject.
(def gen-option-pack
  (gen/frequency [[6 gen/string-alphanumeric]
                  [1 gen/small-integer]
                  [1 (gen/return nil)]
                  [1 (gen/return :not-a-string)]]))

;; Unrelated extra keys — noise that must not affect the subset relation. Base
;; wins on any collision (merge order below), so type-required fields stay intact.
(def gen-extra
  (gen/map gen/keyword gen/small-integer {:max-elements 4}))

(defn- save-subset-of-load?
  "For one content type: over many generated perturbations of its valid base,
   assert that anything the save spec accepts, the load floor also accepts."
  [spec-kw base]
  (let [prop (prop/for-all [op gen-option-pack
                            extra gen-extra]
               (let [item (merge extra (named base) {:option-pack op})]
                 (or (not (spec/valid? spec-kw item))          ; not save-valid → nothing to prove
                     (spec/valid? cs/load-item-spec item))))]  ; save-valid ⇒ must be load-valid
    (tc/quick-check 300 prop)))

(deftest generative-save-implies-load
  (testing "save ⊆ load holds under generated perturbation for every content type"
    (doseq [[ct spec-kw] cs/save-specs]
      (let [result (save-subset-of-load? spec-kw (valid-bases ct))]
        (is (:pass? result)
            (str ct ": found an item that passes SAVE but fails LOAD (drift!): "
                 (pr-str (:fail result))))))))
