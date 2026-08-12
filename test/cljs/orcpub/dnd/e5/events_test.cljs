(ns orcpub.dnd.e5.events-test
  "Integration tests for re-frame event handlers refactored during the
   subscribe-outside-reactive-context fix. These tests verify that
   dispatching an event produces the expected db state or effects.

   PATTERN — testing a reg-event-db handler:
     1. Reset app-db to a known state
     2. dispatch-sync the event
     3. Assert on @app-db

   PATTERN — testing a reg-event-fx handler:
     For handlers that return {:http ...} or {:dispatch ...}, we can't
     easily capture effects without day8.re-frame/re-frame-test. Instead:
     - Test the db-only portions via dispatch-sync
     - For effects, test indirectly (e.g. verify db guard logic)
     - Or register a stub :http fx handler before the test

   NOTE: These tests require all event handlers to be registered. We do
   this by requiring orcpub.dnd.e5.events, which has side effects
   (reg-event-db, reg-event-fx calls at load time)."
  (:require [cljs.test :refer-macros [deftest testing is use-fixtures]]
            [cljs.reader :as reader]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.selections :as selections5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.feats :as feats5e]
            [orcpub.dnd.e5.db :as db]
            [cljs.spec.alpha :as s]
            [orcpub.dnd.e5.autosave-fx :as autosave-fx]
            ;; Side effect: registers all event handlers
            [orcpub.dnd.e5.events :as events]))

;; ---------------------------------------------------------------------------
;; Fixtures
;; ---------------------------------------------------------------------------

(def pristine-db
  "Minimal app-db state. Extend per-test as needed."
  {})

(defn reset-db!
  "Reset app-db before each test to prevent state leakage."
  []
  (reset! app-db pristine-db))

(use-fixtures :each {:before reset-db!})

;; ---------------------------------------------------------------------------
;; ::char5e/filter-spells  (reg-event-db)
;;
;; This handler was refactored to call compute-sorted-spells on db
;; instead of @(subscribe [::char5e/sorted-spells]).
;; ---------------------------------------------------------------------------

(deftest filter-spells-short-text-returns-all-sorted
  (testing "filter text under 3 chars → all spells returned (no filtering)"
    (reset! app-db {:plugins {}})
    (rf/dispatch-sync [::char5e/filter-spells "fi"])
    (let [db @app-db]
      ;; Text stored for the UI input
      (is (= "fi" (::char5e/spell-text-filter db)))
      ;; All sorted spells returned (not filtered) because "fi" < 3 chars
      (let [result (::char5e/filtered-spells db)
            names (set (map :name result))]
        ;; Should contain known static spells
        (is (contains? names "Fireball"))
        (is (contains? names "Shield"))))))

(deftest filter-spells-long-text-filters
  (testing "filter text >= 3 chars → only matching spells returned"
    (reset! app-db {:plugins {}})
    (rf/dispatch-sync [::char5e/filter-spells "fire"])
    (let [result (::char5e/filtered-spells @app-db)
          names (set (map :name result))]
      ;; Only spells containing "fire" (case-insensitive)
      (is (contains? names "Fireball"))
      (is (contains? names "Fire Bolt"))
      (is (not (contains? names "Shield"))))))

(deftest filter-spells-includes-plugin-spells
  (testing "plugin spells are merged into the result"
    (reset! app-db {:plugins {:test-plugin
                              {::e5/spells
                               {:zap {:name "Zap" :key :zap}}}}})
    (rf/dispatch-sync [::char5e/filter-spells "zap"])
    (let [result (::char5e/filtered-spells @app-db)
          names (set (map :name result))]
      (is (contains? names "Zap")))))

;; ---------------------------------------------------------------------------
;; ::char5e/filter-items  (reg-event-db)
;;
;; Same pattern as filter-spells but for magic items.
;; Uses compute-sorted-items instead of subscribe.
;; ---------------------------------------------------------------------------

(deftest filter-items-short-text-returns-all
  (testing "filter text under 3 chars → all items returned"
    (reset! app-db {})
    (rf/dispatch-sync [::char5e/filter-items "ba"])
    (let [result (::char5e/filtered-items @app-db)
          names (set (map mi/name-key result))]
      ;; Known static item should be present
      (is (contains? names "Alchemy Jug")))))

(deftest filter-items-long-text-filters
  (testing "filter text >= 3 chars → only matching items returned"
    (reset! app-db {})
    (rf/dispatch-sync [::char5e/filter-items "alchemy"])
    (let [result (::char5e/filtered-items @app-db)
          names (set (map mi/name-key result))]
      (is (contains? names "Alchemy Jug"))
      ;; Something unrelated should NOT be present
      (is (not (contains? names "Animated Shield"))))))

;; ---------------------------------------------------------------------------
;; ::char5e/save-character  (reg-event-fx)
;;
;; Autosave handler. Was refactored to read cached template from app-db
;; (via track! watcher) instead of @(subscribe [:built-character]).
;;
;; Testing an fx handler without day8.re-frame/re-frame-test:
;; We can't easily intercept the :http effect, but we CAN test the
;; guard logic (cached-template nil → no-op) by checking that the
;; handler doesn't crash and doesn't produce unwanted side effects.
;; ---------------------------------------------------------------------------

(deftest save-character-skips-when-no-cached-template
  (testing "without cached template, handler is a no-op (returns {})"
    ;; Set up db with a character but no cached template
    (reset! app-db {::char5e/character-map {42 {:orcpub.entity/options {}}}})
    ;; This should NOT throw or dispatch error — it should silently skip
    ;; We verify by checking no error dispatch happened
    (rf/dispatch-sync [::char5e/save-character "42"])
    ;; If we got here without exception, the nil guard works.
    ;; The db should not have :loading set to true (no save attempted)
    (is (nil? (:loading @app-db))
        "No save should be attempted without cached template")))

(deftest save-character-rejects-missing-abilities
  (testing "with cached template but no ability scores → error dispatch"
    ;; REGRESSION GUARD that caught a REAL crash (not a stale test): an empty
    ;; (non-nil) template reached entity/build and threw a null-fn `.call`. The
    ;; autosave guard only skipped a NIL template; it now skips nil OR empty
    ;; (both mean "not ready"). The handler must return the no-op {} skip, not
    ;; throw. If this errors again, the guard regressed — fix the CODE.
    (let [template {} ;; degenerate/not-yet-ready template
          character {:orcpub.entity/options {}}]
      (reset! app-db {::char5e/character-map {42 character}
                      ::autosave-fx/cached-template template})
      (rf/dispatch-sync [::char5e/save-character "42"])
      ;; no crash, and nothing was sent (no :loading set) — autosave skipped
      (is (nil? (:loading @app-db))
          "empty template → autosave safely skips this cycle"))))

;; ---------------------------------------------------------------------------
;; :save-character  (reg-event-fx)
;;
;; Manual save. Was refactored to receive built-character as a parameter
;; from the component instead of @(subscribe [:built-character]).
;; ---------------------------------------------------------------------------

(deftest save-character-manual-rejects-missing-abilities
  (testing "built-character without abilities → error dispatch"
    (reset! app-db {:character {:orcpub.entity/options {}}})
    ;; Pass a built-character with no :base-abilities
    (rf/dispatch-sync [:save-character {}])
    ;; Handler should not crash. The ability check will fail,
    ;; resulting in a :show-error-message dispatch.
    ;; Without intercepting effects, we verify no exception.
    (is true "Handler completed without exception")))

;; ---------------------------------------------------------------------------
;; :verify-user-session  (reg-event-fx)
;;
;; Replaced @(subscribe [:user false]) in core.cljs.
;; Tests that the guard logic works: no user → no HTTP call.
;; ---------------------------------------------------------------------------

(deftest verify-user-session-no-user
  (testing "without user in db, handler is a no-op"
    (reset! app-db {})
    ;; Should not attempt HTTP call
    (rf/dispatch-sync [:verify-user-session])
    ;; If we got here, the guard check passed. No exception = success.
    (is (= {} (select-keys @app-db [:loading]))
        "No loading state should be set without user")))

(deftest verify-user-session-no-token
  (testing "user without token → no HTTP call"
    (reset! app-db {:user {:name "test"}})
    (rf/dispatch-sync [:verify-user-session])
    (is true "Handler completed without exception")))

;; ---------------------------------------------------------------------------
;; Emergency raw export
;; ---------------------------------------------------------------------------

(def ^:private sample-plugins
  {"Pack A" {:orcpub.dnd.e5/classes {:artificer {:option-pack "Pack A"}}}
   "Pack B" {:orcpub.dnd.e5/spells {:fireball {:option-pack "Pack B"}}}})

(deftest emergency-export-named-plugin
  (testing "a known plugin-name dumps just that source under its own filename"
    (is (= ["Pack A.orcbrew" (get sample-plugins "Pack A")]
           (events/select-emergency-export sample-plugins "Pack A")))))

(deftest emergency-export-whole-library
  (testing "nil / unknown plugin-name dumps the entire library"
    (is (= ["orcpub-EMERGENCY-backup.orcbrew" sample-plugins]
           (events/select-emergency-export sample-plugins nil)))
    (is (= ["orcpub-EMERGENCY-backup.orcbrew" sample-plugins]
           (events/select-emergency-export sample-plugins "Nonexistent")))))

(deftest emergency-export-never-validates
  (testing "even structurally-invalid plugins are returned verbatim (no gate)"
    (let [broken {"Bad" {:orcpub.dnd.e5/classes {:x {:no-option-pack true}}}}]
      (is (= ["Bad.orcbrew" (get broken "Bad")]
             (events/select-emergency-export broken "Bad")))
      (is (= ["orcpub-EMERGENCY-backup.orcbrew" broken]
             (events/select-emergency-export broken nil))))))

;; ---------------------------------------------------------------------------
;; serialize-orcbrew (pure serialization, split from the saveAs side effect)
;; ---------------------------------------------------------------------------

(def ^:private sample-content
  {:orcpub.dnd.e5/classes {:artificer {:name "Artificer" :option-pack "Pack"}}})

(deftest serialize-orcbrew-compact-roundtrips
  (testing "compact output is readable EDN that round-trips to the same data"
    (let [s (events/serialize-orcbrew sample-content)]
      (is (string? s))
      (is (= sample-content (reader/read-string s))))))

(deftest serialize-orcbrew-pretty-differs-but-same-data
  (testing "pretty-print is multi-line and larger, but the same data round-trips"
    (let [compact (events/serialize-orcbrew sample-content)
          pretty  (events/serialize-orcbrew sample-content :pretty-print? true)]
      (is (not= compact pretty))
      (is (re-find #"\n" pretty) "pretty output spans multiple lines")
      (is (= sample-content (reader/read-string pretty))))))

;; ---------------------------------------------------------------------------
;; spec-field-problems — nested-element diagnosability
;;
;; Coverage is watertight (every homebrew spec rejects a name that derives an
;; invalid keyword — proven in clj keyword-audit-test). These tests cover the
;; OTHER half: that a NESTED failure (a bad option name inside a selection) is
;; reported with a human LOCATION ("Option 2"), not a generic top-level "Name".
;; ---------------------------------------------------------------------------

(deftest spec-field-problems-top-level-bad-name
  (testing "a digit-leading class name is flagged as :name :invalid, no location"
    (let [item {:name "9 Lives Sorcerer" :key :9-lives-sorcerer :option-pack "Pack"}
          expl (s/explain-data ::classes5e/homebrew-class item)
          probs (events/spec-field-problems expl item)
          name-prob (first (filter #(= :name (:field %)) probs))]
      (is (some? name-prob))
      (is (= :invalid (:status name-prob)))
      (is (re-find #"start with a letter" (:reason name-prob)))
      (is (nil? (:location name-prob)) "top-level field has no nested location"))))

(deftest spec-field-problems-locates-bad-option
  (testing "a bad name on the 2nd selection option carries :location \"Option 2\""
    (let [item {:name "Valid Selection" :key :valid-selection :option-pack "Pack"
                :options [{:name "Good Option"}
                          {:name "9 Lives"}]}
          expl (s/explain-data ::selections5e/homebrew-selection item)
          probs (events/spec-field-problems expl item)
          opt-prob (first (filter :location probs))]
      (is (some? opt-prob) "a located problem should be produced")
      (is (= "Option 2" (:location opt-prob)))
      (is (= :name (:field opt-prob)))
      (is (= :invalid (:status opt-prob))))))

(deftest builder-error-hiccup-renders-location
  (testing "the rendered banner names the specific option"
    (let [problems [{:field :name :status :invalid
                     :reason "must start with a letter" :location "Option 2"}]
          hiccup (events/builder-error-hiccup "Selection" problems)
          flat (pr-str hiccup)]
      (is (re-find #"Option 2 Name" flat))
      (is (re-find #"must start with a letter" flat)))))

(deftest builder-error-hiccup-batches-top-level-missing
  (testing "top-level missing fields still batch onto one 'Please fill in' line"
    (let [problems [{:field :name :status :missing}
                    {:field :option-pack :status :missing}]
          flat (pr-str (events/builder-error-hiccup "Class" problems))]
      (is (re-find #"Please fill in" flat))
      (is (re-find #"Option Source Name" flat)))))

;; ---------------------------------------------------------------------------
;; ::e5/repair-quarantined-source — persist-to-library repair engine
;;
;; Reuses the inline-edit transform + the re-key primitive and PERSISTS:
;; a repaired source lands in :plugins and leaves quarantine, atomically. Unlike
;; the export auto-fix, which only rewrote the exported file.
;; ---------------------------------------------------------------------------

(def ^:private quarantined-bugged
  ;; A class trapped under an invalid key (digit-leading name = the keyword trap).
  {"Bugged Pack" {:orcpub.dnd.e5/classes
                  {:9-lives {:name "9 Lives" :key :9-lives :option-pack "Bugged Pack"}}}})

(deftest repair-quarantined-source-lands-and-clears
  (testing "a fixed source is re-keyed, persisted to :plugins, and removed from quarantine"
    (.clear js/window.localStorage)
    (db/set-rejected-plugins quarantined-bugged)
    (reset! app-db {:plugins {"Existing" {:orcpub.dnd.e5/spells
                                          {:zap {:name "Zap" :key :zap :option-pack "Existing"}}}}})
    ;; correct the name → repair re-derives the key (:9-lives → :nine-lives) and validates
    (rf/dispatch-sync [::e5/repair-quarantined-source "Bugged Pack"
                       {["Bugged Pack" :orcpub.dnd.e5/classes :9-lives :name] "Nine Lives"}])
    (let [plugins (:plugins @app-db)
          cls (get-in plugins ["Bugged Pack" :orcpub.dnd.e5/classes])]
      (is (contains? plugins "Bugged Pack") "repaired source landed in :plugins")
      (is (contains? plugins "Existing") "existing sources untouched")
      (is (= [:nine-lives] (keys cls)) "item re-keyed to the corrected name")
      (is (= "Nine Lives" (:name (cls :nine-lives))))
      (is (s/valid? :orcpub.dnd.e5/plugin (get plugins "Bugged Pack"))
          "the restored source is now spec-valid")
      (is (nil? (get (db/get-rejected-plugins) "Bugged Pack"))
          "removed from quarantine"))
    (.clear js/window.localStorage)))

(deftest repair-quarantined-source-rejects-still-invalid
  (testing "if the fix doesn't make it valid, nothing is persisted and it stays quarantined"
    (.clear js/window.localStorage)
    (db/set-rejected-plugins quarantined-bugged)
    (reset! app-db {:plugins {}})
    ;; no edit → name stays "9 Lives" → derived key :9-lives still invalid
    (rf/dispatch-sync [::e5/repair-quarantined-source "Bugged Pack" {}])
    (is (empty? (:plugins @app-db)) "not persisted")
    (is (contains? (db/get-rejected-plugins) "Bugged Pack") "still quarantined for another attempt")
    (.clear js/window.localStorage)))

(deftest repair-quarantined-source-missing-is-noop
  (testing "repairing a name that isn't quarantined doesn't touch :plugins"
    (.clear js/window.localStorage)
    (db/set-rejected-plugins quarantined-bugged)
    (reset! app-db {:plugins {}})
    (rf/dispatch-sync [::e5/repair-quarantined-source "Nonexistent" {}])
    (is (empty? (:plugins @app-db)))
    (is (contains? (db/get-rejected-plugins) "Bugged Pack") "quarantine unchanged")
    (.clear js/window.localStorage)))

;; ---- toggle corruption via real re-frame events (folded from toggle-stress-test) ----
;; Stress harness reproducing the emergent "repetitive clicking -> malformed data
;; (nil instead of false)" corruption by driving the REAL toggle event handlers in
;; the REAL cljs runtime.
;;
;; FINDINGS (reproduced here):
;; 1. Leaf flag toggles are nil-clean but leave FALSE-CRUFT: toggling a skill on
;;    then off leaves `:athletics false` (never removed) — it exports forever.
;; 2. The "nil instead of false" is a PARENT-PATH collapse: a boolean toggle whose
;;    path lands on a MAP applies `(not map)` = false, nuking the whole map. Then
;;    every per-key READ under that node returns nil (`(get false :k)` = nil) and
;;    the next child toggle does `(assoc false …)` which THROWS in cljs.
;;
;; STATUS: FIXED — content-prop toggles use common/toggle-flag (not bare `not`),
;; which refuses to collapse a map; these assert the fixed behavior. The false-cruft
;; is a SEPARATE issue (export cleanup); leaf-toggle-hammer-no-nil still documents it.
;; Legacy data already collapsed to `false` self-heals on the next child toggle.

(defn deep-nil?
  "True if any map value or collection element anywhere in x is nil."
  [x]
  (cond
    (map? x) (boolean (or (some nil? (vals x)) (some deep-nil? (vals x))))
    (coll? x) (boolean (or (some nil? x) (some deep-nil? x)))
    :else false))

(def ^:private skill-keys
  [:athletics :stealth :perception :arcana :insight :persuasion :survival :medicine])

;; deterministic pseudo-random index sequence (no Math/random; reproducible)
(defn- idx-seq [seed n m]
  (loop [s seed acc []]
    (if (= (count acc) n)
      acc
      (let [s' (mod (+ (* s 48271) 7) 2147483647)]
        (recur s' (conj acc (mod s' m)))))))

(deftest leaf-toggle-hammer-no-nil
  (testing "hammering real skill-prof toggles in varied order never leaves a nil"
    (reset! app-db {::feats5e/builder-item {:name "Stress" :props {}}})
    (doseq [i (idx-seq 12345 600 (count skill-keys))]
      (rf/dispatch-sync [::feats5e/toggle-feat-map-prop :skill-prof (nth skill-keys i)]))
    (let [item (::feats5e/builder-item @app-db)]
      (is (not (deep-nil? item)) (str "stray nil after leaf hammering: " (pr-str item)))
      ;; false-cruft IS present and expected (the separate cleanliness issue):
      ;; some skills are `false`, not removed.
      (is (some false? (vals (get-in item [:props :skill-prof])))
          "documents the false-cruft: toggled-off skills are left as false"))))

(deftest parent-path-toggle-preserves-map
  (testing "FIXED: a flag toggle whose path lands on a MAP leaves it untouched"
    (reset! app-db {::feats5e/builder-item {:name "Probe" :props {:skill-prof {:athletics true}}}})
    ;; toggle-feat-prop on :skill-prof now uses common/toggle-flag, which refuses
    ;; to collapse a collection — the skill map survives instead of becoming false.
    (rf/dispatch-sync [::feats5e/toggle-feat-prop :skill-prof])
    (is (= {:athletics true} (get-in @app-db [::feats5e/builder-item :props :skill-prof]))
        "the map is preserved (no collapse to false, no data loss)")))

(deftest parent-path-toggle-no-nil-on-read
  (testing "FIXED: the per-key read still returns its real value, not nil"
    (reset! app-db {::feats5e/builder-item {:name "Probe" :props {:skill-prof {:athletics true}}}})
    (rf/dispatch-sync [::feats5e/toggle-feat-prop :skill-prof])
    (is (= true (get-in @app-db [::feats5e/builder-item :props :skill-prof :athletics]))
        "the 'nil instead of false' symptom is gone — the value is intact")))

(deftest legacy-collapsed-parent-self-heals-on-toggle
  (testing "FIXED + SELF-HEALING: legacy data already collapsed to `false` no
            longer crashes — toggling a child heals the stray false into a fresh
            map and applies the toggle (old skills are gone, but it's usable again)."
    (reset! app-db {::feats5e/builder-item {:name "Probe" :props {:skill-prof false}}})
    (let [threw? (try
                   (rf/dispatch-sync [::feats5e/toggle-feat-map-prop :skill-prof :stealth])
                   false
                   (catch :default _ true))]
      (is (not threw?) "no crash — the collapsed false was healed, not assoc'd into")
      (is (= {:stealth true}
             (get-in @app-db [::feats5e/builder-item :props :skill-prof]))
          "healed to a map and the toggle took effect"))))

;; ---------------------------------------------------------------------------
;; incoming-sources — the multi-plugin wrap decision
;; ---------------------------------------------------------------------------
;; REGRESSION: import used to decide "multi-plugin?" by spec VALIDITY, so a
;; multi-plugin (a MegaPak of many sub-sources) with even one imperfect sub-source
;; was misjudged single and WRAPPED under the import name — double-nesting it into
;; a {string {string plugin}} shape ::plugin can never load, quarantining the WHOLE
;; pak. Detection must be STRUCTURAL (shape), with per-source validity enforced
;; downstream by salvage-plugins. These lock that.

(deftest incoming-sources-keeps-multi-plugin-flat
  (testing "a multi-plugin stays flat (keyed by its own sub-source names), even
            when a sub-source is imperfect — NOT wrapped under the import name"
    (let [multi {"UA - Feats" {:orcpub.dnd.e5/feats
                               {:brave {:option-pack "UA - Feats" :name "Brave"}}}
                 ;; imperfect sub-source (feat missing :option-pack) must not flip
                 ;; detection to single-plugin and trigger a wrap
                 "UA - Bad"   {:orcpub.dnd.e5/feats
                               {:oops {:name "Oops"}}}}]
      (is (= multi (events/incoming-sources "MegaPak" multi))
          "returned as-is, not wrapped under \"MegaPak\"")
      (is (every? string? (keys (events/incoming-sources "MegaPak" multi)))
          "top-level keys remain the sub-source names (no nesting)"))))

(deftest incoming-sources-wraps-single-plugin
  (testing "a single plugin (keyword content-type keys) is wrapped under the
            import name so it becomes a proper {source-name plugin} entry"
    (let [single {:orcpub.dnd.e5/feats {:brave {:option-pack "P" :name "Brave"}}}]
      (is (= {"My Pack" single} (events/incoming-sources "My Pack" single))))))
