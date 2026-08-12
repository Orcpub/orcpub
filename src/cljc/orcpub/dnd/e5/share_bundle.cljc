(ns orcpub.dnd.e5.share-bundle
  "Compute the complete homebrew (\"plugin\") content a single character depends
   on, so it can be bundled into a shareable file (the \"embed the content in
   the shared link\" feature).

   Pure data functions — no re-frame, no DOM — so the core unit-tests under
   `lein test`. See docs/kb/share-bundle-dependency-extraction.md (agents/develop)
   for the full data-model map this implements.

   Strategy (validated by the dependency-surface spike):
     1. DIRECT   — sweep every ::entity/key the character selected
        (entity/flatten-options) and keep those that exist in the plugins map.
     2. CLOSURE  — follow the shallow (<=2 hop) reference edges homebrew defs
        carry (subclass->class, subrace->race, granted spells, race->language
        by NAME, class->selection) to a fixpoint.
     3. REVERSE  — a homebrew class's spell list is stored ON the spells
        (:spell-lists {class-key true}), not on the class, so pull every
        homebrew spell that names an included class.

   Emits a :plugins-shaped map grouped by original source, ready to serialize
   like an .orcbrew export.

   Scope limit: custom magic items / weapons / armor are NOT plugins (they live
   in the server-side ::mi5e/custom-items store, referenced by DB id), so they
   are not — and cannot be — part of this closure. A character using one shows
   it as missing on the recipient side. See the KB."
  (:require [clojure.string :as str]
            [orcpub.entity :as entity]
            [orcpub.template :as t]
            [orcpub.common :as common]))

;; Content-type keywords are written as literals (:orcpub.dnd.e5/...) rather
;; than via the ::e5 alias so this namespace stays clj-loadable for `lein test`
;; without pulling the cljs-flavored orcpub.dnd.e5 namespace.
(def ^:private e5-ns "orcpub.dnd.e5")

(def spells      :orcpub.dnd.e5/spells)
(def classes     :orcpub.dnd.e5/classes)
(def subclasses  :orcpub.dnd.e5/subclasses)
(def races       :orcpub.dnd.e5/races)
(def subraces    :orcpub.dnd.e5/subraces)
(def languages   :orcpub.dnd.e5/languages)
(def selections  :orcpub.dnd.e5/selections)

(def content-types
  "The 13 homebrew content types (content_specs/save-specs). A shared bundle may
   contain ONLY these — anything else is rejected by the structural whitelist."
  #{:orcpub.dnd.e5/spells      :orcpub.dnd.e5/monsters    :orcpub.dnd.e5/encounters
    :orcpub.dnd.e5/backgrounds :orcpub.dnd.e5/languages   :orcpub.dnd.e5/invocations
    :orcpub.dnd.e5/boons       :orcpub.dnd.e5/selections  :orcpub.dnd.e5/feats
    :orcpub.dnd.e5/races       :orcpub.dnd.e5/subraces    :orcpub.dnd.e5/subclasses
    :orcpub.dnd.e5/classes})

;; ── Plugin index ────────────────────────────────────────────────────────────

(defn plugin-index
  "Flatten {source {::e5/type {key def}}} into
   {::e5/type {key {:def def :source source}}}, last-source-wins (matching the
   content-map subs). Only keys whose namespace is orcpub.dnd.e5 are treated as
   content, so :disabled? and other bookkeeping keys are ignored."
  [plugins]
  (reduce
   (fn [idx [source pdata]]
     (if-not (map? pdata)
       idx
       (reduce
        (fn [idx [ctype cmap]]
          (if (and (keyword? ctype) (= e5-ns (namespace ctype)) (map? cmap))
            (reduce (fn [idx [k d]]
                      (assoc-in idx [ctype k] {:def d :source source}))
                    idx cmap)
            idx))
        idx pdata)))
   {} plugins))

(defn- type-of
  "The content-type of key k in the index, or nil if k is not homebrew."
  [idx k]
  (some (fn [[ctype cmap]] (when (contains? cmap k) ctype)) idx))

;; ── Pass 1: direct references ────────────────────────────────────────────────

(defn selected-keys
  "Every ::entity/key selected anywhere in the character's option tree
   (all depths, all content types), regardless of whether it is homebrew."
  [character]
  (into #{}
        (comp (map #(get-in % [::t/key ::entity/key]))
              (remove nil?))
        (entity/flatten-options (::entity/options character))))

(defn- direct-refs
  "Seed set {ctype #{keys}} — selected keys that are actually homebrew."
  [idx character]
  (reduce (fn [acc k]
            (if-let [ct (type-of idx k)]
              (update acc ct (fnil conj #{}) k)
              acc))
          {} (selected-keys character)))

;; ── Transitive edges ─────────────────────────────────────────────────────────

(defn- spell-key-refs
  "Spell keys a race/subrace/class/subclass def grants, from every known shape."
  [d]
  (concat
   ;; race/subrace granted spells: :spells [{:value {:key k}} ...]
   (keep #(get-in % [:value :key]) (:spells d))
   ;; level-modifiers of type :spell
   (keep (fn [m] (when (= :spell (:type m)) (get-in m [:value :key])))
         (:level-modifiers d))
   ;; class spell grants: {level {slot spell-key}}
   (mapcat (fn [gk] (when-let [m (get d gk)] (mapcat vals (vals m))))
           [:paladin-spells :cleric-spells :warlock-spells])))

(defn- language-name-refs
  "Homebrew language keys a def grants by NAME (races/subraces :languages is a
   set of name strings). The one fuzzy edge — resolved via name-to-kw; names
   that do not resolve to a loaded homebrew language are simply not added
   (surface these upstream rather than dropping silently)."
  [idx d]
  (let [lang-idx (get idx languages)]
    (keep (fn [nm]
            (let [k (common/name-to-kw (str nm))]
              (when (contains? lang-idx k) k)))
          (:languages d))))

(defn- prop-language-refs
  "Language keys referenced via a :props :language map (feats and friends)."
  [d]
  (keys (get-in d [:props :language])))

(defn- selection-refs
  "Selection keys a class references via :level-selections :type."
  [d]
  (keep :type (:level-selections d)))

(defn- outgoing-refs
  "All [ctype key] a def points at. Callers filter these against the index."
  [idx ctype d]
  (cond-> []
    (and (= ctype subclasses) (:class d)) (conj [classes (:class d)])
    (and (= ctype subraces) (:race d))    (conj [races (:race d)])
    true (into (for [k (spell-key-refs d)] [spells k]))
    true (into (for [k (language-name-refs idx d)] [languages k]))
    true (into (for [k (prop-language-refs d)] [languages k]))
    true (into (for [k (selection-refs d)] [selections k]))))

(defn- add-ref [acc ctype k] (update acc ctype (fnil conj #{}) k))
(defn- has-ref? [acc ctype k] (contains? (get acc ctype) k))

(defn- closure
  "Fixpoint over outgoing-refs — add every homebrew def transitively referenced."
  [idx seed]
  (loop [acc seed]
    (let [nxt (reduce
               (fn [a [ctype ks]]
                 (reduce
                  (fn [a k]
                    (let [d (get-in idx [ctype k :def])]
                      (reduce (fn [a [rct rk]]
                                (if (and (get-in idx [rct rk]) (not (has-ref? a rct rk)))
                                  (add-ref a rct rk)
                                  a))
                              a (outgoing-refs idx ctype d))))
                  a ks))
               acc acc)]
      (if (= nxt acc) acc (recur nxt)))))

(defn- add-reverse-spell-lists
  "A homebrew class's spell list is declared ON the spells (:spell-lists
   {class-key true}), so pull every homebrew spell that names an included class."
  [idx acc]
  (let [cls (get acc classes)]
    (if (empty? cls)
      acc
      (reduce (fn [a [spell-key {d :def}]]
                (if (some #(get-in d [:spell-lists %]) cls)
                  (add-ref a spells spell-key)
                  a))
              acc (get idx spells)))))

;; ── Emit ─────────────────────────────────────────────────────────────────────

(defn- emit-bundle
  "Rebuild a plugins-shaped {source {ctype {key def}}} map from the resolved set."
  [idx refset]
  (reduce
   (fn [acc [ctype ks]]
     (reduce (fn [acc k]
               (if-let [{d :def src :source} (get-in idx [ctype k])]
                 (assoc-in acc [src ctype k] d)
                 acc))
             acc ks))
   {} refset))

;; ── Public entry point ───────────────────────────────────────────────────────

(defn extract-bundle
  "Given a character entity and the full :plugins map, return the plugins-shaped
   sub-map of exactly the homebrew content this character depends on."
  [character plugins]
  (let [idx (plugin-index plugins)]
    (->> (direct-refs idx character)
         (closure idx)
         (add-reverse-spell-lists idx)
         (emit-bundle idx))))

;; The share link always carries the character's FULL content — descriptions and
;; all. A feat/trait IS its description, so a "trimmed to fit" link is useless;
;; the codec (share-url) ships the whole payload or, only when it's too big for
;; any link, falls back to a downloadable file. No lossy middle tier.

(defn bundle->edn
  "Serialize a bundle to an EDN string for encoding into a URL."
  [bundle]
  (pr-str bundle))

;; ── Structural whitelist (security layer 5) ──────────────────────────────────
;; The fail-closed shape gate for an UNTRUSTED shared bundle. It keeps only the
;; parts that match the exact {source {content-type {item-key def}}} shape with a
;; known content type and a letter-leading keyword key; everything else is
;; dropped. This is a SHAPE gate only — item CONTENT is validated and sanitized
;; afterward by the same .orcbrew import path a file upload goes through
;; (salvage-library-items + per-type spec + sanitize-item-names).

(defn- flatten-by-type
  "Collapse {source {content-type {key def}}} into {content-type {key def}},
   merging across sources (later sources win, matching the content-lookup subs)."
  [plugins]
  (reduce
   (fn [acc [_src pdata]]
     (if (map? pdata)
       (reduce (fn [acc [ct cmap]]
                 (if (and (contains? content-types ct) (map? cmap))
                   (update acc ct merge cmap)
                   acc))
               acc pdata)
       acc))
   {} plugins))

(defn collisions
  "Keys present in BOTH the shared content and the recipient's library with a
   DIFFERENT definition. These are the entries where the shared (view-scoped)
   version wins on the shared sheet while the recipient's own copy stays
   untouched — the thing worth telling the recipient about. Returns a seq of
   {:content-type ct :key k :name display-name}."
  [shared-plugins library-plugins]
  (let [shared (flatten-by-type shared-plugins)
        lib    (flatten-by-type library-plugins)]
    (for [[ct smap] shared
          [k sdef]  smap
          :let [ldef (get-in lib [ct k])]
          :when (and (some? ldef) (not= ldef sdef))]
      {:content-type ct :key k :name (or (:name sdef) (name k))})))

(defn whitelist-bundle
  "Drop every part of an untrusted bundle that doesn't match the expected shape.
   Returns {:bundle kept-plugins-shaped-map :dropped count-of-rejected-parts}.
   Fails closed: a non-map input, a non-string source, an unknown content type,
   a non-keyword or non-letter-leading item key, or a non-map def are all removed
   rather than trusted."
  [data]
  (if-not (map? data)
    {:bundle {} :dropped 1}
    (reduce-kv
     (fn [acc source pdata]
       (if-not (and (string? source) (map? pdata))
         (update acc :dropped inc)
         (reduce-kv
          (fn [acc ctype cmap]
            (if-not (and (contains? content-types ctype) (map? cmap))
              (update acc :dropped inc)
              (reduce-kv
               (fn [acc k d]
                 (if (and (keyword? k)
                          (common/keyword-starts-with-letter? k)
                          (map? d))
                   (update-in acc [:bundle source ctype] (fnil assoc {}) k d)
                   (update acc :dropped inc)))
               acc cmap)))
          acc pdata)))
     {:bundle {} :dropped 0}
     data)))

;; Custom magic items are carried as their RAW server form (as in
;; ::mi5e/custom-items), so the recipient runs the identical expand pipeline and
;; the raw list drops straight into every seam that reads custom-items. The raw
;; name lives under this namespaced key (mi5e/name).
(def ^:private raw-item-name-key :orcpub.dnd.e5.magic-items/name)

(defn used-custom-items
  "Raw custom items this character equips. `expand-one` maps a single raw item to
   its expanded, keyed variant(s) (magic-items/expand-magic-items on a 1-item vec)
   — injected so this stays pure/cljc-testable. A weapon/armor item expands to
   several keyed variants (one per subtype); the raw item is kept if the character
   selected ANY of them. Returns a vector; empty when none."
  [character raw-items expand-one]
  (let [used (selected-keys character)]
    (filterv (fn [raw] (some #(contains? used (:key %)) (expand-one raw))) raw-items)))

(defn whitelist-shared
  "Fail-closed structural gate for an UNTRUSTED shared payload. Accepts either the
   container {:plugins {...} :custom-items [...]} or (legacy) a bare plugins map.
   Returns {:plugins <whitelisted> :custom-items <vec of well-formed raw items>
   :dropped n}. A custom item is kept only if it's a map with a letter-leading
   name; its remaining fields are data the builder interprets defensively
   (safe-read already forbids code; decode caps size)."
  [data]
  (let [container? (and (map? data)
                        (or (contains? data :plugins) (contains? data :custom-items)))
        plugins-in (if container? (:plugins data) data)
        items-in   (when container? (:custom-items data))
        {pb :bundle pd :dropped} (whitelist-bundle plugins-in)
        items (if (sequential? items-in)
                (filterv (fn [it]
                           (and (map? it)
                                (let [nm (get it raw-item-name-key)]
                                  (and (string? nm)
                                       (common/starts-with-letter? (str/trim nm))))))
                         items-in)
                [])
        idropped (if (sequential? items-in) (- (count items-in) (count items)) 0)]
    {:plugins pb :custom-items items :dropped (+ pd idropped)}))
