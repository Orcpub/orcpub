(ns orcpub.entity
  (:require #?(:clj [clojure.spec.alpha :as spec])
            #?(:cljs [cljs.spec.alpha :as spec])
            [orcpub.common :as common]
            [orcpub.modifiers :as mods]
            [orcpub.entity-spec :as es]
            [orcpub.template :as t]
            [orcpub.entity.strict :as strict]
            [clojure.string :as s]
            [clojure.set :refer [difference union intersection]]))

(spec/def ::key ::strict/key)
(spec/def ::option (spec/keys :opt [::key ::options]))
(spec/def ::option-vec (spec/* ::option))
(spec/def ::options (spec/map-of keyword? (spec/or :single ::option
                                                   :multiple ::option-vec)))
(spec/def ::values (spec/or :map (spec/map-of keyword? any?)
                            :nil nil?))
(spec/def ::raw-entity (spec/keys :opt [::options
                                        ::values]))

(spec/def ::flat-option (spec/keys :req [::t/path]
                                   :opt [::value]))
(spec/def ::flat-options (spec/+ ::flat-option))

(declare to-strict-option)

(defn children [n]
  (cond
    (map? n) (filter
              (fn [v]
                (or (map? v)
                    (sequential? v)))
              (vals n))
    (sequential? n) n
    :else nil))

(defn db-ids [entity & [branch-fn]]
  (disj
   (set
    (map
     :db/id
     (tree-seq
      (or branch-fn children)
      children
      entity))) nil))

(defn remove-orphan-ids-aux [remove-ids? entity & [ids]]
  (cond
    (map? entity)
    (into {}
          (map
           (fn [[k v]]
             [k (remove-orphan-ids-aux (or remove-ids?
                                           (get ids (:db/id entity))
                                           (-> entity :db/id nil?))
                                       v
                                       ids)])
           (if (or remove-ids?
                   (get ids (:db/id entity)))
             (dissoc entity :db/id)
             entity)))

    (sequential? entity)
    (mapv #(remove-orphan-ids-aux remove-ids? % ids) entity)

    :else
    entity))

(defn remove-specific-ids [entity ids]
  (remove-orphan-ids-aux false entity ids))

(defn remove-orphan-ids [entity]
  (remove-orphan-ids-aux false entity))

(defn remove-ids [entity]
  (remove-orphan-ids-aux true entity))

(defn selection-options [{:keys [::t/options]}]
  options)

(defn to-strict-selections [options path homebrew-paths]
  (mapv
   (fn [[k v]]
     (let [id (-> v meta :db/id)
           new-path (conj path k)]
       (cond-> {::strict/key k}
         id (assoc :db/id id)
         (get homebrew-paths new-path) (assoc ::strict/homebrew? true)
         (sequential? v) (assoc ::strict/options (map #(to-strict-option % new-path homebrew-paths) v))
         (map? v) (assoc ::strict/option (to-strict-option v new-path homebrew-paths)))))
   options))

(defn to-strict-option [{:keys [:db/id ::key ::value ::options] :as option} path homebrew-paths]
  (cond-> {}
    id (assoc :db/id id)
    key (assoc ::strict/key key)
    options (assoc ::strict/selections (to-strict-selections options (conj path key) homebrew-paths))
    (int? value) (assoc ::strict/int-value value)
    (string? value) (assoc ::strict/string-value value)
    (map? value) (assoc ::strict/map-value value)))

(defn remove-empty-fields [raw-character]
  (into {}
        (comp
         (map
          (fn [[k v]]
            [k (cond
                 (sequential? v)
                 (vec
                  (sequence
                   (comp
                    (map #(if (map? %)
                            (remove-empty-fields %)
                            %))
                    (remove #(or (nil? %)
                                 (and (coll? %)
                                      (empty? %)))))
                   v))
                 
                 (map? v) (remove-empty-fields v)
                 :else v)]))
         (remove
          (fn [[k v]]
            (and (coll? v)
                 (empty? v)))))
        raw-character))

(defn to-strict-homebrew-paths [homebrew-paths]
  (reduce
   (fn [ps [path set?]]
     (if set?
       (conj ps {::strict/homebrew-path path})
       ps))
   []
   homebrew-paths))

(defn to-strict [{:keys [:db/id ::options ::values ::homebrew-paths ::owner]}]
  (cond-> {::strict/selections (to-strict-selections options [] homebrew-paths)}
    values (assoc ::strict/values (into {} (remove (comp nil? val)) values))
    id (assoc :db/id id)
    owner (assoc ::strict/owner owner)              ;; round-trip ::strict/owner via ::owner
    true remove-empty-fields))

(spec/fdef to-strict
           :args (spec/cat :entity ::raw-entity)
           :ret ::strict/entity)

(declare from-strict-selections)

(defn from-strict-option [{:keys [:db/id
                                  ::strict/key
                                  ::strict/selections
                                  ::strict/int-value
                                  ::strict/string-value
                                  ::strict/map-value]
                           :as option}]
  (let [value (or int-value map-value string-value)]
    (cond-> {}
      id (assoc :db/id id)
      key (assoc ::key key)
      selections (assoc ::options (from-strict-selections selections))
      value (assoc ::value value))))

(defn from-strict-options [options]
  (mapv from-strict-option options))

;; array-map preserves insertion order at any size. The previous reduce/assoc
;; approach promoted to PersistentHashMap beyond 8 keys, scrambling selection
;; order during strict round-trips.
(defn from-strict-selections [selections]
  (apply array-map
         (mapcat
          (fn [{:keys [:db/id ::strict/key ::strict/option ::strict/options]}]
            [key (with-meta
                   (if option
                     (from-strict-option option)
                     (from-strict-options options))
                   {:db/id id})])
          selections)))

(defn from-strict-homebrew-paths [homebrew-paths]
  (reduce
   (fn [ps {:keys [homebrew-path]}]
     (assoc ps homebrew-path true))
   {}
   homebrew-paths))

(declare selections-homebrew-paths)

(defn option-homebrew-paths [{:keys [::strict/key ::strict/selections]} path]
  (selections-homebrew-paths selections (conj path key)))

(defn options-homebrew-paths [options path]
  (reduce
   (fn [paths option]
     (merge
      paths
      (option-homebrew-paths option path)))
   {}
   options))

(defn selections-homebrew-paths [selections path]
  (reduce
   (fn [paths {:keys [::strict/key ::strict/homebrew? ::strict/option ::strict/options]}]
     (let [new-path (conj path key)]
       (merge
        paths
        (when homebrew? {new-path true})
        (option-homebrew-paths option new-path)
        (options-homebrew-paths options new-path))))
   {}
   selections))

(defn from-strict [{:keys [:db/id ::strict/selections ::strict/values ::strict/owner] :as strict-entity}]
  (let [homebrew-paths (selections-homebrew-paths selections [])]
    (cond-> {::options (from-strict-selections selections)}
      (seq homebrew-paths) (assoc ::homebrew-paths homebrew-paths) 
      id (assoc :db/id id)
      values (assoc ::values values)
      owner (assoc ::owner owner))))

(spec/fdef from-strict
           :args (spec/cat :entity ::strict/entity)
           :ret ::raw-entity)

;;============== topo sort ===============

(defn without
  "Returns set s with x removed."
  [s x] (difference s #{x}))

(defn take-1
  "Returns the pair [element, s'] where s' is set s with element removed."
  [s] {:pre [(seq s)]}
  (let [item (first s)]
    [item (without s item)]))

(defn no-incoming
  "Returns the set of nodes in graph g for which there are no incoming
  edges, where g is a map of nodes to sets of nodes."
  [g]
  (let [nodes (set (keys g))
        have-incoming (apply union (vals g))]
    (difference nodes have-incoming)))

(defn normalize
  "Returns g with empty outgoing edges added for nodes with incoming
  edges only.  Example: {:a #{:b}} => {:a #{:b}, :b #{}}"
  [g]
  (let [have-incoming (apply union (vals g))]
    (reduce #(if (get % %2) % (assoc % %2 #{})) g have-incoming)))

(defn kahn-sort
  "Proposes a topological sort for directed graph g using Kahn's
   algorithm, where g is a map of nodes to sets of nodes. If g is
   cyclic, returns nil."
  ([g]
     (kahn-sort (normalize g) [] (no-incoming g)))
  ([g l s]
     (if (empty? s)
       (when (every? empty? (vals g)) l)
       (let [[n s'] (take-1 s)
             m (g n)
             g' (reduce #(update-in % [n] without %2) g m)]
         (recur g' (conj l n) (union s' (intersection (no-incoming g') m)))))))

;;==========================================



(declare build-options-paths)

(defn build-option-paths [path option]
  (let [new-path (conj path (::key option))
        child-options (::options option)
        option-value (::value option)
        result (cond-> {::t/path new-path
                        ::t/key option
                        ::t/modifiers option}
                 option-value (assoc ::value option-value))]
    (if (seq child-options)
      (conj (build-options-paths new-path child-options)
            result)
      result)))

(defn build-options-entry-value-paths [path value]
  (if (sequential? value)
    (map (partial build-option-paths path) value)
    [(build-option-paths path value)]))

(defn build-options-entry-paths [path [option-key value]]
  (let [new-path (conj path option-key)]
    (build-options-entry-value-paths new-path value)))

(defn build-options-paths [path options]
  (map (partial build-options-entry-paths path) options))

(defn flatten-options [options]
  (flatten (build-options-paths [] options)))

(declare get-template-selection-path)

(defn get-template-option-path [selection [f & r] current-path]
  (let [[option option-i]
        (first (keep-indexed
                (fn [i s]
                  (when (= (::t/key s) f)
                    [s i]))
                (selection-options selection)))
        next-path (vec (concat current-path [::t/options option-i]))]
    (if (seq r)
      (get-template-selection-path option r next-path)
      next-path)))

(defn get-template-selection-path [template [f & r] current-path]
  (let [[selection selection-i]
        (first (keep-indexed
                (fn [i s]
                  (when (= (::t/key s) f)
                    [s i]))
                (::t/selections template)))
        next-path (vec (concat current-path [::t/selections selection-i]))]
    (if (seq r)
      (get-template-option-path selection r next-path)
      next-path)))

(defn get-lazy [a k]
  (if (and (seq? a)
           (int? k))
    (first (drop k a))
    (get a k)))

(defn get-in-lazy [m ks]
  (reduce get-lazy m ks))

(defn collect-plugins [flat-options plugin-map]
  (mapcat
   (fn [{path ::t/path
         option-value ::value
         :as option}]
     (::t/plugins (get-in plugin-map path)))
   flat-options))

(defn modifier-functions [modifiers]
  (map
   (fn [{:keys [::mods/value ::mods/fn ::mods/deferred-fn]}]
     (if (and deferred-fn value)
       (deferred-fn value)
       fn))
   modifiers))

(defn index-of-option [selection option-key]
  (first
   (keep-indexed
    (fn [i v]
      (when (= option-key (::key v))
        i))
    selection)))

(defn template-item-with-key [items item-key]
  (first
   (keep-indexed
    (fn [i s]
      (when (= (::t/key s) item-key)
        [i s]))
    items)))

(defn entity-item-with-key [items item-key]
  (first
   (keep-indexed
    (fn [i s]
      (when (and item-key
               (= (::key s) item-key))
        [i s]))
    items)))

(defn get-entity-path
  ([template entity option-path]
   (get-entity-path template entity [] option-path))
  ([template entity current-path [selection-k option-k & ks :as option-path]]
   (if selection-k
     (let [[selection-i selection] (template-item-with-key (::t/selections template) selection-k)
           {:keys [::t/min ::t/max ::t/multiselect?]} selection
           options (selection-options selection)
           [option-i option] (template-item-with-key options option-k)
           selection-path (vec (concat current-path [::options selection-k]))
           entity-items (get-in entity selection-path)
           [entity-i _] (entity-item-with-key entity-items option-k)
           path-i (when (and (or option-k entity-i)
                           (or (nil? max)
                               (> max 1)
                               multiselect?))
                    (if (nat-int? option-k)
                      option-k
                      entity-i))
           full-path (if path-i (conj selection-path path-i) selection-path)]
       (get-entity-path
        option
        entity
        full-path
        ks))
     (vec current-path))))

(defn meta-path [entity-path entity]
  (let [paths (reductions
               conj
               []
               entity-path)]
    (map #(meta (get-in entity %)) paths)))

#_[:orcpub.entity/options :class 0 :orcpub.entity/options :eldritch-invocations :orcpub.entity/options :book-of-ancient-secrets-rituals]

(defn update-option [template entity path update-fn]
  (let [entity-path (get-entity-path template entity path)
        updated (update-in entity entity-path update-fn)]
    updated))

(defn order-modifiers [modifiers order]
  (let [order-map (zipmap order (range (count order)))]
    (sort-by (comp order-map ::mods/key) modifiers)))

(defn combine-ref-selections [selections]
  (let [first-selection (first selections)]
    (when first-selection
      (assoc
       first-selection
       ::t/min (apply + (map ::t/min selections))
       ::t/max (when (every? ::t/max selections) (apply + (map ::t/max selections)))
       ::t/options (into
                    (sorted-set-by
                     #(compare (::t/key %) (::t/key %2)))
                    (apply concat (map selection-options selections)))))))

(defn combine-selections [selections]
  (let [by-ref (group-by ::t/ref selections)
        non-ref-selections (get by-ref nil)
        combined-ref-selections (map
                                 (fn [[_ ref-selections]]
                                   (combine-ref-selections ref-selections))
                                 (dissoc by-ref nil))]
    (sort-by (fn [s] [(or (::t/order s) 1000) (::t/name s)])
             (concat non-ref-selections combined-ref-selections))))

(defn add-child-paths [path ref children]
  (map
   (fn [child]
     (assoc child
            ::path
            (vec
             (conj (or ref path)
                   (::t/key child)))))
   children))

(defn get-all-selections-aux-2 [template selected-option-paths]
  (loop [[current & r] [template]
         used-ref-option-paths #{}
         accum-selections []]
    (if current
      (let [{:keys [::t/selections ::path ::t/ref]} current
            options (selection-options current)
            selection? options
            children (or selections options)
            children-with-paths (add-child-paths path ref children)
            active-children (filter
                             (fn [{:keys [::path]}]
                               (or (= current template)
                                   (not selection?)
                                   (and (get-in selected-option-paths path)
                                        (not (used-ref-option-paths path)))))
                             children-with-paths)
            active-children-paths (map ::path active-children)]
        (recur (concat active-children r)
               (if ref
                 (union used-ref-option-paths (set active-children-paths))
                 used-ref-option-paths)
               (if selection?
                 (conj accum-selections current)
                 accum-selections)))
      accum-selections)))

(defn remove-disqualified-selections
  "Remove selections whose prereq-fn fails against built-char.
   Callers must provide a real built entity, never nil."
  [selections built-char]
  (remove #(or (nil? %)
               (let [prereq-fn (::t/prereq-fn %)]
                 (and prereq-fn (not (prereq-fn built-char)))))
          selections))

(defn get-all-selections-2 [obj selected-option-paths built-char]
  (remove-disqualified-selections
   (get-all-selections-aux-2 obj selected-option-paths)
   built-char))

(defn make-path-map-aux [character]
  (let [flat-options (flatten-options (::options character))]
    (reduce
     (fn [m v]
       (update-in m (::t/path v) (fn [c] (or c {}))))
     {}
     flat-options)))

(def memoized-make-path-map-aux (memoize make-path-map-aux))

(defn make-path-map [character]
  (make-path-map-aux character))

(defn available-selections [raw-entity built-entity template]
  (let [path-map (make-path-map raw-entity)
        all-selections (get-all-selections-2 template path-map built-entity)]
    all-selections))

(defn tagged-selections [available-selections tags]
  (filter
   #(seq (intersection tags (::t/tags %)))
   available-selections))

(defn make-ref-selection-map [raw-entity template]
  (let [path-map (make-path-map raw-entity)
        all-selections (remove nil? (flatten (get-all-selections-aux-2 template path-map)))
        by-ref (group-by ::t/ref all-selections)]
    (reduce-kv
     (fn [m ref selections]
       (if ref
         (assoc m (if (sequential? ref) ref [ref]) (combine-ref-selections selections))
         m))
     {}
     by-ref)))

(defn get-modifiers [template ref-selection-map path]
  (let [selection-path (butlast path)
        option-key (last path)
        ref-selection (ref-selection-map selection-path)
        option (if ref-selection
                 (first
                  (filter
                   (fn [{:keys [::t/key] :as option}]
                     (= option-key key))
                   (vec (selection-options ref-selection))))
                 (let [template-path (get-template-selection-path template path [])]
                   (get-in-lazy template template-path)))]
    (::t/modifiers option)))

(defn collect-modifiers [raw-entity flat-options template]
  (let [ref-selection-map (make-ref-selection-map raw-entity template)]
    (mapcat
     (fn [{path ::t/path
           option-value ::value
           :as option}]
       (let [modifiers (get-modifiers template ref-selection-map path)]
         (flatten
          (map
           (fn [{:keys [::mods/name ::mods/value ::mods/fn ::mods/deferred-fn ::mods/default-value] :as mod}]
             (if deferred-fn
               (deferred-fn (or option-value default-value))
               mod))
           (flatten modifiers)))))
     flat-options)))

(defn make-template-option-map [selections]
  (reduce
   (fn [m {:keys [::path ::t/ref] :as selection}]
     (merge m
            (reduce
             (fn [m2 {:keys [::t/key] :as option}]
               (let [option-path (conj (vec (or ref path)) key)]
                 (assoc m2 option-path option)))
             {}
             (selection-options selection))))
   {}
   selections))

;; [:class :warlock :eldritch-invocations :book-of-ancient-secrets :book-of-ancient-secrets-rituals :identify]

(defn prn-js [& args]
  #?(:cljs (apply js/console.log (map #(clj->js %) args))))

(defn collect-modifiers-2 [raw-entity flat-options template]
  (let [selections (get-all-selections-aux-2 template (make-path-map raw-entity))
        template-option-map (make-template-option-map selections)]
    (mapcat
     (fn [{path ::t/path
           option-value ::value
           :as option}]
       (let [template-option (template-option-map path)
             modifiers (::t/modifiers template-option)]
         (flatten
          (map
           (fn [{:keys [::mods/name ::mods/value ::mods/fn ::mods/deferred-fn ::mods/default-value] :as mod}]
             (if deferred-fn
               (deferred-fn (or option-value default-value))
               mod))
           (flatten modifiers)))))
     flat-options)))

(defn apply-options [raw-entity template]
  (let [options (flatten-options (::options raw-entity))
        modifiers (sort-by ::mods/order (collect-modifiers-2 raw-entity options template))
        deps (reduce
              (fn [m {:keys [::mods/key ::mods/deps]}]
                (if (seq deps)
                  (update m key union deps)
                  m))
              {}
              modifiers)
        base (merge (::t/base template)
                    (::values raw-entity))
        base-deps (::es/deps base)
        all-deps (merge-with union deps base-deps)
        mod-order (rseq (kahn-sort all-deps))
        ordered-mods (order-modifiers modifiers mod-order)]
    (mods/apply-modifiers base ordered-mods)))

(defn build-aux [raw-entity template]
  (apply-options raw-entity template))

(def memoized-build-aux (memoize build-aux))

(defn build [raw-entity template]
  (build-aux raw-entity template))

(def memoized-make-modifier-map (memoize t/make-modifier-map))

(declare merge-selections)

(defn merge-options [options other-options]
  (when (or options other-options)
    (let [opt-map (zipmap (map ::t/key options) options)
          other-opt-map (zipmap (map ::t/key other-options) other-options)
          merged (merge-with
                  (fn [o1 o2]
                    (assoc
                     o1
                     ::t/selections (merge-selections (::t/selections o1) (::t/selections o2))
                     ::t/modifiers (vec (concat (::t/modifiers o1) (::t/modifiers o2)))))
                  opt-map
                  other-opt-map)]
      (vec
       (concat
        (map
         (fn [{key ::t/key}]
           (merged key))
         options)
        (vals (apply dissoc merged (map ::t/key options))))))))

(defn merge-selections [selections other-selections]
  (when (or selections other-selections)
    (let [sel-map (zipmap (map ::t/key selections) selections)
          other-sel-map (zipmap (map ::t/key other-selections) other-selections)
          merged (merge-with
                  (fn [s1 s2]
                    (assoc
                     s1
                     ::t/options
                     (merge-options
                      (selection-options s1)
                      (selection-options s2))))
                  sel-map
                  other-sel-map)]
      (vec
       (concat
        (map
         (fn [{key ::t/key}]
           (merged key))
         selections)
        (vals (apply dissoc merged (map ::t/key selections))))))))

(defn merge-multiple-selections [& selections]
  (reduce
   merge-selections
   selections))

(defn build-template-aux [plugins template]
  (reduce
   (fn [templ {:keys [::t/path ::t/selections ::t/modifiers] :as plugin}]
     (let [template-path (get-template-selection-path templ path [])]
       (update-in
        templ
        template-path
        #(assoc
          %
          ::t/selections (merge-selections (::t/selections %) selections)
          ::t/modifiers (concat (::t/modifiers %) modifiers)))))
   template
   plugins))

(def memoized-build-template-aux (memoize build-template-aux))

(defn build-template [raw-entity template]
  (let [plugin-map (t/make-modifier-map template)
        options (flatten-options (::options raw-entity))
        plugins (collect-plugins options plugin-map)]
    (build-template-aux plugins template)))

(spec/fdef
 build
 :args (spec/cat :raw-entity ::raw-entity :modifier-map ::t/template)
 :ret any?)

;; (Removed the dead `entity/name-to-kw` — it had zero callers and was a second,
;; looser copy of `common/name-to-kw` that didn't strip apostrophes. Use
;; `orcpub.common/name-to-kw`, which is the single guarded derivation.)

(defn get-option-value-path [template entity path]
  (conj (get-entity-path template entity path) ::value))

(defn actual-path [{:keys [::t/ref ::path] :as selection}]
  (vec (if ref (if (sequential? ref) ref [ref]) path)))

(defn get-option [built-template entity path]
  (get-in entity (get-entity-path built-template entity path)))

(defn option-selected? [require-value? option]
  (and (or (::value option)
           (not require-value?))
       (::key option)))

(defn count-remaining [built-template character {:keys [::t/min ::t/max ::t/require-value?] :as selection}]
  (let [actual-path (actual-path selection)
        homebrew? (get-in character [::homebrew-paths actual-path])
        selected-options (get-option built-template character actual-path)
        selected-count (cond
                         (sequential? selected-options)
                         (count (if require-value?
                                  (filter (partial option-selected? require-value?) selected-options)
                                  selected-options))
                         
                         (map? selected-options)
                         (if (option-selected? require-value? selected-options)
                           1
                           0)
                         
                         :else 0)]
    (cond homebrew? 0

          (< selected-count min)
          (- min selected-count)

          (and max (> selected-count max))
          (- max selected-count)

          :else
          0)))

(defn meets-prereqs?
  "Check if option's prereqs are satisfied by built-char.
   Callers must provide a real built entity (from entity/build or
   subscribe [:built-character]), never nil."
  [option & [built-char]]
  (every?
   (fn [{:keys [::t/prereq-fn]}]
     (if prereq-fn
       (prereq-fn built-char)
       true))
   (::t/prereqs option)))
