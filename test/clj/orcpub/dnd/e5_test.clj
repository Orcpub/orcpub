(ns orcpub.dnd.e5-test
  ;; explicit :refer to avoid namespace pollution from :refer :all
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.spec.alpha :as spec]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [orcpub.common :as common]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.races :as races5e]
            [orcpub.dnd.e5.feats :as feats5e]
            [orcpub.dnd.e5.backgrounds :as backgrounds5e]
            [orcpub.dnd.e5.languages :as languages5e]
            [orcpub.dnd.e5.monsters :as monsters5e]
            [orcpub.dnd.e5.encounters :as encounters5e]
            [orcpub.dnd.e5.selections :as selections5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.content-specs :as content-specs]))

(def plugins-1 {"Source A" {:orcpub.dnd.e5/backgrounds {:bg1 {:x 1
                                                         :option-pack "Source A"}
                                                   :bg2 {:x 1
                                                         :option-pack "Source A"}}}
                "Source B" {:orcpub.dnd.e5/classes {:c1 {:x 1
                                                     :option-pack "Source B"}
                                                :c2 {:x 1
                                                     :option-pack "Source B"}}}})

(deftest test-specs
  (is (spec/valid? ::e5/plugins plugins-1))
  (is (not (spec/valid? ::e5/plugin plugins-1)))
  (is (spec/valid? ::e5/plugin (plugins-1 "Source A")))
  (is (not (spec/valid? ::e5/plugins (plugins-1 "Source A")))))

(def valid-plugin? #(spec/valid? ::e5/plugin %))

(deftest test-salvage-plugins-all-valid
  ;; Every source valid -> all kept, nothing rejected.
  (let [{:keys [kept rejected]} (e5/salvage-plugins valid-plugin? plugins-1)]
    (is (= plugins-1 kept))
    (is (= {} rejected))))

(deftest test-salvage-plugins-mixed
  ;; One bad source (item missing required :option-pack) must NOT take the whole
  ;; library down: good sources are kept, only the bad one is quarantined.
  (let [bad-plugin {:orcpub.dnd.e5/classes {:c1 {:x 1}}} ; no :option-pack -> invalid
        mixed (assoc plugins-1 "BAD" bad-plugin)
        {:keys [kept rejected]} (e5/salvage-plugins valid-plugin? mixed)]
    (is (= plugins-1 kept) "valid sources survive")
    (is (= {"BAD" bad-plugin} rejected) "invalid source is quarantined, not lost")
    ;; Sanity: the bad plugin really is spec-invalid and the good ones really valid.
    (is (not (valid-plugin? bad-plugin)))
    (is (every? valid-plugin? (vals kept)))))

(deftest test-salvage-plugins-all-invalid
  (let [bad {"A" {:orcpub.dnd.e5/classes {:c1 {:x 1}}}
             "B" {:orcpub.dnd.e5/spells {:s1 {:x 1}}}}
        {:keys [kept rejected]} (e5/salvage-plugins valid-plugin? bad)]
    (is (= {} kept))
    (is (= bad rejected) "all sources preserved for repair, none discarded")))

(deftest test-salvage-plugins-empty-and-non-map
  ;; Empty library is a clean no-op; a non-map (corrupt parse) yields empties so
  ;; the caller can fall back to preserving the raw string.
  (is (= {:kept {} :rejected {}} (e5/salvage-plugins valid-plugin? {})))
  (is (= {:kept {} :rejected {}} (e5/salvage-plugins valid-plugin? "not-a-map")))
  (is (= {:kept {} :rejected {}} (e5/salvage-plugins valid-plugin? nil))))

(deftest test-salvage-plugins-no-data-loss
  ;; Invariant: kept ∪ rejected == input (every source is accounted for).
  (let [mixed (assoc plugins-1
                     "BAD" {:orcpub.dnd.e5/classes {:c1 {:x 1}}})
        {:keys [kept rejected]} (e5/salvage-plugins valid-plugin? mixed)]
    (is (= mixed (merge kept rejected)))
    (is (= (set (keys mixed)) (into (set (keys kept)) (keys rejected))))))

(deftest test-resilient-load-vs-old-all-or-nothing
  ;; REGRESSION PROOF for the all-or-nothing loader (docs §3A/§6.2). This test
  ;; fails if the loader reverts to the old behavior, because it asserts the
  ;; exact contrast: old logic dropped the whole library, new logic keeps the
  ;; valid sources.
  (let [bad {:orcpub.dnd.e5/classes {:c1 {:x 1}}}        ; missing :option-pack
        mixed (assoc plugins-1 "BAD" bad)
        ;; The OLD loader, verbatim: (if (valid? ::plugins all) all nil)
        old-result (if (spec/valid? ::e5/plugins mixed) mixed nil)
        ;; The NEW loader keeps the valid subset (what db.cljs returns).
        {:keys [kept]} (e5/salvage-plugins valid-plugin? mixed)]
    (is (nil? old-result)
        "PROOF OF BUG: one invalid source makes the whole blob spec-invalid, so the old all-or-nothing loader returned nil — dropping EVERYTHING")
    (is (and (seq kept) (= plugins-1 kept))
        "FIX: the resilient loader still loads the valid sources where the old one loaded nothing")))

(deftest test-reconcile-rejected-accumulates
  ;; A source quarantined on an earlier load survives a later load even
  ;; if it's no longer in localStorage["plugins"] (e.g. dropped by a save). The
  ;; new load's rejected set is MERGED with the already-quarantined map.
  (let [old {"A" {:bad 1}}
        incoming {"B" {:bad 2}}
        kept {"Good" {:ok 1}}]
    (is (= {"A" {:bad 1} "B" {:bad 2}}
           (e5/reconcile-rejected old incoming kept))
        "both quarantined sources retained")))

(deftest test-reconcile-rejected-latest-wins
  ;; A re-broken source updates its record rather than duplicating.
  (is (= {"A" {:bad 2}}
         (e5/reconcile-rejected {"A" {:bad 1}} {"A" {:bad 2}} {}))))

(deftest test-reconcile-rejected-drops-repaired
  ;; The key behavior: a source that is now present-and-valid (in `kept`) is
  ;; dropped from quarantine, so a repaired source doesn't linger as a ghost.
  (let [old {"A" {:bad 1} "B" {:bad 2}}
        kept {"A" {:fixed 1}}]                 ; A repaired, now valid+kept
    (is (= {"B" {:bad 2}}
           (e5/reconcile-rejected old {} kept))
        "repaired A cleared; still-broken B remains")))

(deftest test-reconcile-rejected-empty-and-nonmap
  ;; Empty result (everything repaired) is returned so the caller can clear the
  ;; storage key; a non-map legacy/corrupt `old` is treated as empty, not a throw.
  (is (= {} (e5/reconcile-rejected {"A" {:bad 1}} {} {"A" {:ok 1}})))
  (is (= {} (e5/reconcile-rejected nil {} {})))
  (is (= {"B" {:bad 2}} (e5/reconcile-rejected "corrupt-legacy-string" {"B" {:bad 2}} {}))
      "a non-map old-rejected (legacy raw write) is ignored, not fatal"))

(deftest test-rekey-content-group-fixes-trap
  ;; An item under an invalid key (the keyword trap) is moved to the key
  ;; derived from its now-corrected name, and its :key field is synced.
  (let [items {:9-lives {:name "Nine Lives" :key :9-lives :option-pack "P"}}
        result (e5/rekey-content-group items)]
    (is (= [:nine-lives] (keys result)) "moved to the name-derived key")
    (is (= :nine-lives (:key (result :nine-lives))) ":key field synced")
    (is (= "Nine Lives" (:name (result :nine-lives))) "content otherwise intact")))

(deftest test-rekey-content-group-leaves-valid-keys
  ;; A valid key is left untouched even if it doesn't match the name, so existing
  ;; references aren't disturbed.
  (let [items {:fireball {:name "Fireball Plus" :key :fireball :option-pack "P"}}]
    (is (= items (e5/rekey-content-group items)))))

(deftest test-rekey-content-group-collision-suffixes
  ;; Two trapped items deriving the same key must both survive (numeric suffix).
  (let [items {:1a {:name "Echo" :option-pack "P"}
               :2a {:name "Echo" :option-pack "P"}}
        result (e5/rekey-content-group items)]
    (is (= 2 (count result)) "no item dropped on key collision")
    (is (= #{:echo :echo-2} (set (keys result))))))

(deftest test-rekey-content-group-collision-with-valid-sibling
  ;; A trapped item whose corrected name derives onto an ALREADY-VALID sibling's
  ;; key must get a suffix — not overwrite (and drop) the valid sibling, even when
  ;; the valid one would be assoc'd later. Guards the reserved-keys seeding.
  (let [items {:9-echo {:name "Echo" :option-pack "P"}    ; trapped, derives :echo
               :echo   {:name "Echo" :option-pack "P"}}   ; already valid, key :echo
        result (e5/rekey-content-group items)]
    (is (= 2 (count result)) "valid sibling not dropped by the re-key")
    (is (contains? result :echo) "the already-valid :echo survives untouched")
    (is (= #{:echo :echo-2} (set (keys result))) "trapped item got a suffix")))

(deftest test-rekey-content-group-no-name-keeps-key
  ;; Without a usable name we can't derive a key; keep the original (validation
  ;; will still flag it) rather than throwing.
  (let [items {:9-x {:option-pack "P"}}]
    (is (= items (e5/rekey-content-group items)))))

(deftest test-rekey-plugin-passes-through-non-content
  (let [plugin {:orcpub.dnd.e5/classes {:9-lives {:name "Nine Lives" :option-pack "P"}}
                :disabled? true}
        result (e5/rekey-plugin plugin)]
    (is (= true (:disabled? result)) "non-content entry untouched")
    (is (= [:nine-lives] (keys (:orcpub.dnd.e5/classes result))) "content group re-keyed")))

(deftest test-export-str-bug-invariant
  ;; REGRESSION GUARD for the original banner bug (docs §2). The export path
  ;; validates its argument as ::e5/plugin, so it must receive the MAP, not
  ;; (str map). Wrapping in str yields a value that fails map? at the root —
  ;; exactly the reported "Failed validation: cljs.core/map?" error. This pins
  ;; why a `(str ...)` at the call site is a bug.
  (let [plugin (plugins-1 "Source A")]
    (is (spec/valid? ::e5/plugin plugin)
        "the raw map is a valid plugin (what export must receive)")
    (is (not (spec/valid? ::e5/plugin (str plugin)))
        "PROOF OF BUG: (str plugin) — what the banner used to dispatch — fails ::e5/plugin validation")))

(deftest test-merge-all-plugins
  (let [plugins-2 {"Source A" {:orcpub.dnd.e5/backgrounds {:bg2 {:x 2
                                                            :option-pack "Source A"}
                                                      :bg3 {:x 2
                                                            :option-pack "Source A"}}}
                   "Source C" {:orcpub.dnd.e5/classes {:c1 {:x 2
                                                       :option-pack "Source C"}
                                                  :c2 {:x 2
                                                       :option-pack "Source C"}}}}
        expected-result {"Source A" {:orcpub.dnd.e5/backgrounds {:bg1 {:x 1
                                                                  :option-pack "Source A"}
                                                            :bg2 {:x 2
                                                                  :option-pack "Source A"}
                                                            :bg3 {:x 2
                                                                  :option-pack "Source A"}}}
                         "Source B" {:orcpub.dnd.e5/classes {:c1 {:x 1
                                                              :option-pack "Source B"}
                                                         :c2 {:x 1
                                                              :option-pack "Source B"}}}
                         "Source C" {:orcpub.dnd.e5/classes {:c1 {:x 2
                                                             :option-pack "Source C"}
                                                        :c2 {:x 2
                                                             :option-pack "Source C"}}}}]
    (is (= expected-result (e5/merge-all-plugins plugins-1 plugins-2)))
    (is (= plugins-1 (e5/merge-all-plugins plugins-1 plugins-1)))))

;; ---- keyword-derivation audit (folded from keyword-audit-test) ----
;; A user can type a name that LOOKS fine ("5th Edition Sorcerer", "9 Lives") but
;; common/name-to-kw derives an INVALID keyword from it (it doesn't sanitise a
;; leading non-letter), which later fails validation with an error pointing
;; vaguely at "Name". This audit enumerates EVERY homebrew content type with a
;; name->keyword field against a battery of tricky names and asserts:
;;   1. COVERAGE       — every such spec rejects a name deriving an invalid keyword.
;;   2. DIAGNOSABILITY — the explanation's :in path names WHICH element failed,
;;      which events.cljs/spec-field-problems turns into a human message.
;; It also pins name-to-kw's actual behaviour on each tricky input.

;; Names a real user might type that derive a keyword NOT starting with a letter.
(def tricky-names
  ["5th Edition"   ; leading digit — the classic trap
   "9 Lives"       ; leading digit, with a space
   "3.5e Throwback"
   "-Foo"          ; leading dash
   ".Bar"          ; leading dot (\W -> -)
   "  Baz"         ; leading whitespace (\W -> -)
   "++Plus"        ; leading symbols
   "9"             ; a bare digit
   "42"
   "#Hashtag"
   "Über"          ; leading accented letter — not in [a-zA-Z]
   "Éclair"
   "梦"])           ; leading non-latin letter

;; A genuinely-fine name that must keep validating, so rejection of the bad ones
;; is meaningful rather than blanket.
(def good-name "Valid Name")

;; Each entry: a valid base item (sans name/key) + the spec it must satisfy.
;; with-name derives :key via common/name-to-kw the same way the builders do, so
;; the test exercises the real pipeline: user types name -> key derived -> spec runs.
(defn- with-name [base ns-str nm]
  (assoc base :name nm :key (common/name-to-kw nm ns-str)))

(def content-types
  [{:label "class"      :spec ::classes5e/homebrew-class
    :ns "orcpub.dnd.e5.classes" :base {:option-pack "Pack"}}
   {:label "subclass"   :spec ::classes5e/homebrew-subclass
    :ns "orcpub.dnd.e5.classes" :base {:option-pack "Pack" :class :wizard}}
   {:label "invocation" :spec ::classes5e/homebrew-invocation
    :ns "orcpub.dnd.e5.classes" :base {:option-pack "Pack"}}
   {:label "boon"       :spec ::classes5e/homebrew-boon
    :ns "orcpub.dnd.e5.classes" :base {:option-pack "Pack"}}
   {:label "race"       :spec ::races5e/homebrew-race
    :ns "orcpub.dnd.e5.races" :base {:option-pack "Pack"}}
   {:label "subrace"    :spec ::races5e/homebrew-subrace
    :ns "orcpub.dnd.e5.races" :base {:option-pack "Pack" :race :elf}}
   {:label "feat"       :spec ::feats5e/homebrew-feat
    :ns "orcpub.dnd.e5.feats" :base {:option-pack "Pack"}}
   {:label "background" :spec ::backgrounds5e/homebrew-background
    :ns "orcpub.dnd.e5.backgrounds" :base {:option-pack "Pack"}}
   {:label "language"   :spec ::languages5e/homebrew-language
    :ns "orcpub.dnd.e5.languages" :base {:option-pack "Pack"}}
   {:label "monster"    :spec ::monsters5e/homebrew-monster
    :ns "orcpub.dnd.e5.monsters" :base {:option-pack "Pack"
                                        :hit-points {:die 8 :die-count 1}}}
   {:label "encounter"  :spec ::encounters5e/encounter
    :ns "orcpub.dnd.e5.encounters" :base {:option-pack "Pack"}}
   {:label "selection"  :spec ::selections5e/homebrew-selection
    :ns "orcpub.dnd.e5.selections" :base {:option-pack "Pack"}}
   {:label "spell"      :spec ::spells5e/homebrew-spell
    :ns "orcpub.dnd.e5.spells" :base {:option-pack "Pack" :school "evocation"
                                      :level 1 :spell-lists {:wizard true}}}])

(deftest audit-specs-match-the-registry
  (testing "the specs exercised here are exactly the save specs in the shared
            content-specs registry — so this audit table and the registry can't
            drift apart"
    (is (= (set (map :spec content-types))
           (set (vals content-specs/save-specs)))
        "keyword-audit content-types and content-specs/save-specs must name the
         same set of specs")))

(deftest good-name-validates-everywhere
  (testing "a clean name validates for every content type (so rejection of the
            tricky names below is meaningful, not blanket)"
    (doseq [{:keys [label spec ns base]} content-types]
      (let [item (with-name base ns good-name)]
        (is (spec/valid? spec item)
            (str label " should accept a clean name. explain: "
                 (spec/explain-str spec item)))))))

(deftest tricky-names-are-rejected-everywhere
  (testing "every name->keyword content type rejects a name that derives an
            invalid keyword (no silent acceptance)"
    (doseq [{:keys [label spec ns base]} content-types
            nm tricky-names]
      (let [item (with-name base ns nm)]
        (is (not (spec/valid? spec item))
            (str label " accepted tricky name " (pr-str nm)
                 " -> key " (pr-str (:key item))
                 " (SILENT BAD-DATA: would later break name-to-kw consumers)"))))))

(deftest rejection-is-diagnosable
  (testing "the spec explanation points at :name or :key (so the banner can say
            'Name must start with a letter', not just 'invalid')"
    (doseq [{:keys [spec ns base label]} content-types
            nm ["5th Edition" "9 Lives" "-Foo"]]
      (let [item (with-name base ns nm)
            problems (::spec/problems (spec/explain-data spec item))
            in-fields (mapcat #(filter keyword? (:in %)) problems)]
        (is (some #{:name :key} in-fields)
            (str label " for name " (pr-str nm)
                 " did not surface :name/:key in any :in path; banner would be "
                 "generic. problems: " (pr-str problems)))))))

(deftest nested-selection-option-name-is-validated-and-located
  (testing "a selection with a bad OPTION name is rejected, AND the :in path
            carries the option index so the banner can say which option failed"
    (let [item (-> (with-name {:option-pack "Pack"}
                              "orcpub.dnd.e5.selections" good-name)
                   (assoc :options [{:name "Good Option"}
                                    {:name "9 Lives"}]))   ; second option is bad
          problems (::spec/problems
                    (spec/explain-data ::selections5e/homebrew-selection item))]
      (is (seq problems)
          "a bad nested option name must invalidate the whole selection")
      (testing ":in localises the failure to options[1]"
        (let [paths (map :in problems)]
          (is (some (fn [in] (and (some #{:options} in)
                                  (some #{1} in)
                                  (some #{:name} in)))
                    paths)
              (str ":in path should pinpoint options 1 :name. paths: "
                   (pr-str paths))))))))

(deftest name-to-kw-does-not-sanitise-leading-non-letter
  (testing "documents WHY the above fails: name-to-kw keeps a leading non-letter,
            so keyword-starts-with-letter? rejects it"
    (doseq [nm tricky-names]
      (let [kw (common/name-to-kw nm)]
        (is (not (common/keyword-starts-with-letter? kw))
            (str (pr-str nm) " -> " (pr-str kw) " unexpectedly starts with a letter")))))
  (testing "and a clean name does derive a letter-leading keyword"
    (is (common/keyword-starts-with-letter? (common/name-to-kw good-name)))))

(deftest name-to-kw-derivations-pinned
  (testing "exact derived keywords, so the failure mode per input is explicit"
    (is (= :5th-edition (common/name-to-kw "5th Edition")))
    (is (= :9-lives (common/name-to-kw "9 Lives")))
    (is (= :-foo (common/name-to-kw "-Foo")))
    (is (= :-bar (common/name-to-kw ".Bar")))
    (is (= :-baz (common/name-to-kw "  Baz")))
    (is (= :-plus (common/name-to-kw "++Plus")))
    ;; accented/non-latin leading letters collapse to a leading dash (ASCII \w),
    ;; so the name ALSO fails starts-with-letter?
    (is (false? (boolean (common/starts-with-letter? "Über"))))
    (is (false? (boolean (common/starts-with-letter? "Éclair"))))))

;; ---- resilient-loader real-content guard (folded from plugin-load-test) ----
;; The loader quarantines a source only if it fails ::e5/plugin. The danger is
;; FALSE quarantine — a source real users rely on getting dropped on load. These
;; push actual .orcbrew fixtures (test/fixtures/) through the same salvage
;; decision the loader uses: valid content kept in full, only broken content
;; isolated. Fixtures are asserted to EXIST so a missing one fails loudly.
;;
;; test-pak.orcbrew is a large (~760KB) multi-source pak whose content has been
;; replaced with public-domain filler — no copyrighted names. It's the everyday-
;; content guard: every valid source must be kept, none falsely quarantined.

;; The exact predicate the resilient loader injects into salvage-plugins — one
;; shared definition of "loadable", not a copy — so this real-content guard proves
;; the loader's actual accept/quarantine decision, not a look-alike.
(def load-valid-plugin? content-specs/valid-for-load?)

(def bom (char 0xFEFF))

(defn- strip-bom [s]
  (if (and s (pos? (count s)) (= bom (first s))) (subs s 1) s))

(defn read-orcbrew
  "Parse an .orcbrew file to data, or nil if it can't be parsed (mirrors the
   loader's get-local-storage-item, which returns nil on unreadable input)."
  [path]
  (try
    (edn/read-string (strip-bom (slurp path)))
    (catch Exception _ nil)))

(defn as-multi
  "Normalize parsed orcbrew to the loader's {source-name plugin} shape (same rule
   as orcbrew-validation/is-multi-plugin?)."
  [data]
  (if (and (map? data) (seq data) (every? string? (keys data)))
    data
    {"Imported" data}))

(def fixtures "test/fixtures")
(defn fix [name] (str fixtures "/" name))

(deftest test-pak-no-false-quarantine
  (testing "large multi-source pak (public-domain filler): every valid source is kept, none quarantined"
    (is (.exists (io/file (fix "test-pak.orcbrew"))) "fixture must be present")
    (let [data (read-orcbrew (fix "test-pak.orcbrew"))
          multi (as-multi data)
          {:keys [kept rejected]} (e5/salvage-plugins load-valid-plugin? multi)]
      (println "\n[test-pak] sources:" (count multi)
               "| kept:" (count kept) "| rejected:" (count rejected)
               (when (seq rejected) (str "-> " (vec (keys rejected)))))
      (is (map? data) "parses as EDN")
      (is (> (count multi) 1) "is a real multi-source pak")
      (is (empty? rejected)
          (str "real pak sources must NOT be falsely quarantined; rejected: "
               (vec (keys rejected))))
      (is (= multi kept) "the loaded library is identical to the input (no loss)"))))

(deftest valid-homebrew-kept
  (testing "real valid homebrew (sourced-classes, duplicate-external) loads fully"
    (doseq [f ["sourced-classes.orcbrew"]]
      (is (.exists (io/file (fix f))) (str f " fixture must be present"))
      (let [{:keys [kept rejected]} (e5/salvage-plugins load-valid-plugin?
                                                        (as-multi (read-orcbrew (fix f))))]
        (is (empty? rejected) (str f " should be fully kept"))
        (is (seq kept) (str f " should load content"))))
    (doseq [f ["test/duplicate-external-a.orcbrew"
               "test/duplicate-external-b.orcbrew"]]
      (when (.exists (io/file f))
        (let [{:keys [rejected]} (e5/salvage-plugins load-valid-plugin?
                                                     (as-multi (read-orcbrew f)))]
          (is (empty? rejected) (str f " should be fully kept")))))))

(deftest broken-spec-quarantined-not-catastrophic
  (testing "a genuinely spec-broken source IS quarantined"
    (is (.exists (io/file (fix "broken-spec.orcbrew"))) "fixture must be present")
    (let [{:keys [rejected]} (e5/salvage-plugins load-valid-plugin?
                                                 (as-multi (read-orcbrew (fix "broken-spec.orcbrew"))))]
      (is (seq rejected) "the broken source must be quarantined for repair"))))

(deftest unparseable-handled
  (testing "unparseable content yields nil (loader preserves raw, loads nothing) — no throw"
    (is (.exists (io/file (fix "broken-parse.orcbrew"))) "fixture must be present")
    (is (nil? (read-orcbrew (fix "broken-parse.orcbrew"))))))

(deftest loader-edge-inputs-never-throw
  (testing "empty / nil / non-map inputs are handled, never throw"
    (is (= {:kept {} :rejected {}} (e5/salvage-plugins load-valid-plugin? {})))
    (is (= {:kept {} :rejected {}} (e5/salvage-plugins load-valid-plugin? nil)))
    (is (= {:kept {} :rejected {}} (e5/salvage-plugins load-valid-plugin? "corrupt")))
    (is (= {:kept {} :rejected {}} (e5/salvage-plugins load-valid-plugin? 42)))))

;; ---------------------------------------------------------------------------
;; Per-ENTRY salvage — one bad entry must NOT quarantine its whole source
;; ---------------------------------------------------------------------------

(deftest test-salvage-library-items-silos-bad-entries
  (testing "a bad entry is siloed WITHOUT taking down its source: the source keeps
            its valid items (kept), only the broken item is rejected"
    (let [lib {"Src" {:orcpub.dnd.e5/feats
                      {:good {:option-pack "Src" :name "Good"}
                       :bad  {:name "Bad"}}}}   ; missing :option-pack
          {:keys [kept rejected]} (e5/salvage-library-items
                                   content-specs/valid-item-for-load? lib)]
      (is (= {:good {:option-pack "Src" :name "Good"}}
             (get-in kept ["Src" :orcpub.dnd.e5/feats]))
          "good item stays in the live source")
      (is (= {:bad {:name "Bad"}}
             (get-in rejected ["Src" :orcpub.dnd.e5/feats]))
          "only the bad item is set aside, under the same source/content-type")
      (is (spec/valid? ::e5/plugin (get kept "Src"))
          "the kept source is now load-valid"))))

(deftest test-salvage-library-items-all-valid
  (testing "all-valid library: everything kept, nothing rejected"
    (let [{:keys [kept rejected]} (e5/salvage-library-items
                                   content-specs/valid-item-for-load? plugins-1)]
      (is (= plugins-1 kept))
      (is (= {} rejected)))))

(deftest test-salvage-plugin-items-keeps-non-content-entries
  (testing ":disabled? and other non-content-group entries stay with kept"
    (let [{:keys [kept rejected]}
          (e5/salvage-plugin-items content-specs/valid-item-for-load?
                                   {:disabled? true
                                    :orcpub.dnd.e5/feats {:g {:option-pack "P"}}})]
      (is (true? (:disabled? kept)))
      (is (= {} rejected)))))

(deftest test-salvage-library-items-edge-inputs
  (testing "nil / non-map never throw"
    (is (= {:kept {} :rejected {}}
           (e5/salvage-library-items content-specs/valid-item-for-load? nil)))
    (is (= {:kept {} :rejected {}}
           (e5/salvage-library-items content-specs/valid-item-for-load? "x")))))

(deftest test-reconcile-rejected-items
  (testing "merges old + new set-aside entries, prunes any now live in kept"
    (let [old {"S" {:orcpub.dnd.e5/feats {:a {:name "A"}}}}
          new {"S" {:orcpub.dnd.e5/feats {:b {:name "B"}}}}
          kept {"S" {:orcpub.dnd.e5/feats {:a {:option-pack "S" :name "A"}}}}] ; :a now live
      (is (= {"S" {:orcpub.dnd.e5/feats {:b {:name "B"}}}}
             (e5/reconcile-rejected-items old new kept))
          ":a pruned (now live), :b stays set aside")))
  (testing "a source whose entries are all pruned disappears"
    (is (= {} (e5/reconcile-rejected-items
               {"S" {:orcpub.dnd.e5/feats {:a {:name "A"}}}}
               {}
               {"S" {:orcpub.dnd.e5/feats {:a {:option-pack "S" :name "A"}}}}))))
  (testing "nil inputs never throw"
    (is (= {} (e5/reconcile-rejected-items nil nil nil)))))
