(ns orcpub.dnd.e5
  (:require #?(:cljs [cljs.spec.alpha :as spec])
            #?(:clj [clojure.spec.alpha :as spec])
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.languages :as languages]
            [orcpub.common :as common]))

(spec/def ::spells (spec/map-of common/keyword-starts-with-letter?
                                ::spells/homebrew-spell))

(spec/def ::content-keyword (fn [v] (or (= v :disabled?)
                                        (and (qualified-keyword? v)
                                             (common/keyword-starts-with-letter? v)
                                             (= (namespace v) "orcpub.dnd.e5")))))

(spec/def ::option-pack string?)

(spec/def ::homebrew-item (spec/keys :req-un [::option-pack]))

(spec/def ::homebrew-items (spec/map-of common/keyword-starts-with-letter?
                                        ::homebrew-item))

(spec/def ::plugin (spec/map-of ::content-keyword
                                (spec/or :items ::homebrew-items
                                         :bool boolean?)))

(spec/def ::plugins (spec/map-of string? ::plugin))


(defn salvage-plugins
  "Partition a loaded multi-plugin map into `{:kept … :rejected …}` so one corrupt
   source can't make the loader discard the whole homebrew library.

   `valid-plugin?` is injected (db.cljs passes `#(spec/valid? ::plugin %)`) to keep
   this pure and JVM-testable without spec machinery. A non-map input yields two
   empty maps; the caller preserves the raw string in that case."
  [valid-plugin? plugins]
  (if (map? plugins)
    (reduce-kv (fn [acc plugin-name plugin]
                 (update acc
                         (if (valid-plugin? plugin) :kept :rejected)
                         assoc plugin-name plugin))
               {:kept {} :rejected {}}
               plugins)
    {:kept {} :rejected {}}))


(defn salvage-plugin-items
  "Per-ENTRY salvage of ONE source. Walks each content group and splits its items
   by `valid-item?` (a fn of [content-type item-key item]) — valid items go to
   :kept, invalid to :rejected. Non-content entries (e.g. `:disabled?`, or a
   content group that is a boolean) stay with :kept. Returns {:kept <plugin>
   :rejected <plugin>}; a content-type key is absent on a side that has nothing.

   This is what lets ONE bad entry be siloed WITHOUT quarantining its whole source:
   the source keeps its valid items, only the broken ones are set aside for repair.
   `valid-item?` is injected (content-specs supplies the load-floor version) so this
   stays pure/JVM-testable. Non-map input yields two empty maps."
  [valid-item? plugin]
  (if (map? plugin)
    (reduce-kv
     (fn [acc k v]
       (if (and (qualified-keyword? k) (map? v))
         (reduce-kv
          (fn [a ik iv]
            (assoc-in a [(if (valid-item? k ik iv) :kept :rejected) k ik] iv))
          acc
          v)
         ;; non-content-group entry (or boolean content group) — keep as-is
         (assoc-in acc [:kept k] v)))
     {:kept {} :rejected {}}
     plugin)
    {:kept {} :rejected {}}))

(defn salvage-library-items
  "Per-ENTRY salvage across a whole library `{source-name plugin}`. Returns
   {:kept {name plugin} :rejected {name plugin}}: each source's valid items in
   :kept, its invalid items (a partial plugin) in :rejected. A source is absent
   from a side when it has nothing there. Non-map input yields two empty maps."
  [valid-item? plugins]
  (if (map? plugins)
    (reduce-kv
     (fn [acc name plugin]
       (let [{:keys [kept rejected]} (salvage-plugin-items valid-item? plugin)]
         (cond-> acc
           (seq kept)     (assoc-in [:kept name] kept)
           (seq rejected) (assoc-in [:rejected name] rejected))))
     {:kept {} :rejected {}}
     plugins)
    {:kept {} :rejected {}}))

(defn reconcile-rejected
  "Maintain the name-keyed quarantine map (`plugins:rejected`) across loads: merge
   this load's rejected sources into the already-quarantined ones (latest-wins per
   name, so nothing accumulates), then drop any whose name reappears in `kept` — a
   repaired source clears itself. Returns the cleaned `{name → bad-source}` map
   (caller removes the storage key when empty).

   Pure/dependency-free for JVM tests. Non-map `old-rejected` is treated as empty."
  [old-rejected new-rejected kept]
  (let [old (if (map? old-rejected) old-rejected {})
        incoming (if (map? new-rejected) new-rejected {})
        merged (merge old incoming)]
    (apply dissoc merged (keys kept))))


(defn- distinct-key
  "Pick `base` if free in `taken` (a map/set of used keys), else append -2, -3, …
   until free — so repair never drops an item when two names derive the same key."
  [taken base]
  (if-not (contains? taken base)
    base
    (loop [n 2]
      (let [candidate (keyword (str (name base) "-" n))]
        (if (contains? taken candidate) (recur (inc n)) candidate)))))

(defn rekey-content-group
  "Re-key only items whose CURRENT key is invalid (the keyword trap — a key not
   starting with a letter, e.g. `:9-lives`): move to the key derived from the
   corrected `:name` and sync `:key`. Already-valid keys are left untouched (don't
   disturb existing references); collisions get a numeric suffix; an item with no
   usable `:name` keeps its original key (validation still flags it).

   Pure so the JVM suite can cover re-key/collision/no-name."
  [items]
  ;; Reserve the already-valid keys. distinct-key is seeded with these plus the
  ;; keys emitted so far, so a re-keyed item can't collide with — and be clobbered
  ;; by — a valid sibling processed later.
  (let [reserved (into #{} (comp (map key)
                                 (filter common/keyword-starts-with-letter?))
                       items)]
    (reduce (fn [acc [k item]]
              (if-let [derived (and (not (common/keyword-starts-with-letter? k))
                                    (string? (:name item))
                                    (common/name-to-kw (:name item)))]
                ;; invalid key + a usable name → move to the name-derived key
                (let [new-key (distinct-key (into reserved (keys acc)) derived)]
                  (assoc acc new-key (assoc item :key new-key)))
                ;; valid key, or no name to derive from → leave the item untouched
                (assoc acc k item)))
            {}
            items)))

(defn rekey-plugin
  "Apply `rekey-content-group` to every content group in a source map
   (`{content-type {item-key item}}`); non-content-group entries (e.g. `:disabled?`)
   pass through. The re-key half of a quarantine repair: after the user fixes a
   trapped item's name, sync its map key so the source can pass `::plugin`."
  [plugin]
  (reduce-kv (fn [acc k v]
               (assoc acc k (if (and (qualified-keyword? k) (map? v))
                              (rekey-content-group v)
                              v)))
             {}
             plugin))


(defn invalid-keyed-items
  "For a source map (`{content-type {item-key item}}`), return a seq of
   `{:content-type :item-key :name}` for items whose KEY is invalid — the
   keyword-trap cases that a rename can repair. Drives the quarantine repair UI."
  [plugin]
  (for [[ct items] plugin
        :when (and (qualified-keyword? ct) (map? items))
        [k item] items
        :when (not (common/keyword-starts-with-letter? k))]
    {:content-type ct :item-key k :name (:name item)}))


(defn merge-plugins [plugin-1 plugin-2]
  (merge-with
   merge
   plugin-1
   plugin-2))

(defn merge-all-plugins [all-plugins-1 all-plugins-2]
  (merge-with
   merge-plugins
   all-plugins-1
   all-plugins-2))

(defn reconcile-rejected-items
  "Per-ENTRY analog of `reconcile-rejected`. Merge this load's rejected entries
   into the already-quarantined ones (latest-wins per item), then drop any item
   that now appears in `kept` (repaired or re-imported valid), and drop content
   groups / sources left empty. Keeps an item from being BOTH live and quarantined,
   and lets a fixed entry self-clear. Returns the cleaned {source partial-plugin}."
  [old-rejected new-rejected kept]
  (let [merged (merge-all-plugins (if (map? old-rejected) old-rejected {})
                                  (if (map? new-rejected) new-rejected {}))]
    (reduce-kv
     (fn [acc src plugin]
       (let [kept-src (get kept src)
             cleaned (reduce-kv
                      (fn [p ct items]
                        (if (and (qualified-keyword? ct) (map? items))
                          (let [kept-items (get kept-src ct)
                                remaining (into {} (remove (fn [[ik _]]
                                                             (contains? kept-items ik))
                                                           items))]
                            (if (seq remaining) (assoc p ct remaining) p))
                          (assoc p ct items)))
                      {}
                      plugin)]
         (if (seq cleaned) (assoc acc src cleaned) acc)))
     {}
     merged)))

