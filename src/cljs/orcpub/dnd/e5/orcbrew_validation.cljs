(ns orcpub.dnd.e5.orcbrew-validation
  "Comprehensive validation for orcbrew file import/export.

  Provides detailed error messages and progressive validation to help users
  identify and fix issues with their orcbrew files."
  (:require [cljs.spec.alpha :as spec]
            [cljs.reader :as reader]
            [clojure.string :as str]
            [orcpub.dnd.e5 :as e5]
            [orcpub.common :as common]))

;; Forward declarations for functions used before definition
(declare is-multi-plugin?)

;; ============================================================================
;; Text Normalization - Ensure clean ASCII for reliable PDF/export
;; ============================================================================

(def unicode-to-ascii
  "Map of common problematic Unicode characters to ASCII equivalents.
   These often sneak in from copy/paste from Word, Google Docs, etc."
  {;; Quotation marks
   \u2018 "'"    ; left single quote → straight apostrophe
   \u2019 "'"    ; right single quote → straight apostrophe
   \u201A "'"    ; single low-9 quote
   \u201B "'"    ; single high-reversed-9 quote
   \u201C "\""   ; left double quote → straight double quote
   \u201D "\""   ; right double quote → straight double quote
   \u201E "\""   ; double low-9 quote
   \u201F "\""   ; double high-reversed-9 quote
   \u2032 "'"    ; prime (feet)
   \u2033 "\""   ; double prime (inches)

   ;; Dashes and hyphens
   \u2010 "-"    ; hyphen
   \u2011 "-"    ; non-breaking hyphen
   \u2012 "-"    ; figure dash
   \u2013 "-"    ; en-dash → hyphen
   \u2014 "--"   ; em-dash → double hyphen
   \u2015 "--"   ; horizontal bar

   ;; Spaces
   \u00A0 " "    ; non-breaking space → regular space
   \u2002 " "    ; en space
   \u2003 " "    ; em space
   \u2009 " "    ; thin space
   \u200A " "    ; hair space
   \u200B ""     ; zero-width space → remove
   \u202F " "    ; narrow no-break space
   \u205F " "    ; medium mathematical space

   ;; Other common replacements
   \u2026 "..."  ; ellipsis → three dots
   \u2022 "*"    ; bullet → asterisk
   \u2027 "-"    ; hyphenation point
   \u00B7 "*"    ; middle dot → asterisk
   \u2212 "-"    ; minus sign → hyphen
   \u00D7 "x"    ; multiplication sign
   \u00F7 "/"    ; division sign
   \u2044 "/"    ; fraction slash
   \u00AE "(R)"  ; registered trademark
   \u00A9 "(c)"  ; copyright
   \u2122 "(TM)" ; trademark
   })

(defn normalize-text
  "Normalizes a string by replacing problematic Unicode with ASCII equivalents.
   This ensures clean text for PDF generation and file export.
   Returns the string unchanged if no replacements needed."
  [s]
  (if (string? s)
    (reduce-kv
     (fn [text char replacement]
       (str/replace text (str char) replacement))
     s
     unicode-to-ascii)
    s))

(defn count-non-ascii
  "Count non-ASCII chars left after normalization so the importer can WARN about
   content `normalize-text` deliberately keeps (e.g. accented letters — the
   `unicode-to-ascii` map normalizes typographic punctuation but NOT letters).
   Returns {:count N :chars #{...}} or nil if all ASCII. No callers yet; kept
   correct for a future warn-don't-strip wire-up.

   NOTE: cljs has no char type — seq'ing a string yields 1-char STRINGS, and
   `(int \"é\")` is 0, NOT the code point, so `(> (int %) 127)` silently never
   fires. Use `.charCodeAt`."
  [s]
  (when (string? s)
    (let [non-ascii (filter #(> (.charCodeAt % 0) 127) s)]
      (when (seq non-ascii)
        {:count (count non-ascii)
         :chars (set non-ascii)}))))

(defn normalize-text-in-data
  "Recursively walks a data structure and normalizes all strings.
   Useful for cleaning imported or user-created content."
  [data]
  (cond
    (string? data) (normalize-text data)
    (map? data) (into {} (map (fn [[k v]] [k (normalize-text-in-data v)]) data))
    (vector? data) (mapv normalize-text-in-data data)
    (seq? data) (mapv normalize-text-in-data data)
    (set? data) (set (map normalize-text-in-data data))
    :else data))

;; ============================================================================
;; Required Fields - Content-type-specific field requirements
;; ============================================================================

(def required-fields
  "Map of content types to their required fields and dummy values.
   Fields listed here will be auto-filled on import and validated on export.

   Structure: {content-type {:field-name {:dummy <value> :check-fn <optional-predicate>}}}

   :dummy - The placeholder value to use when field is missing
   :check-fn - Optional predicate; if provided, field fails if (check-fn value) is false
               Default check is just (some? value)"
  {:orcpub.dnd.e5/classes
   {:name {:dummy "Missing Name"}}
   ;; :key is auto-derived from :name, not checked here

   :orcpub.dnd.e5/subclasses
   {:name {:dummy "Missing Subclass Name"}
    :class {:dummy nil}} ; parent class ref, checked specially

   :orcpub.dnd.e5/races
   {:name {:dummy "Missing Race Name"}}

   :orcpub.dnd.e5/subraces
   {:name {:dummy "Missing Subrace Name"}
    :race {:dummy nil}} ; parent race ref, checked specially

   :orcpub.dnd.e5/backgrounds
   {:name {:dummy "Missing Background Name"}}

   :orcpub.dnd.e5/feats
   {:name {:dummy "Missing Feat Name"}}

   :orcpub.dnd.e5/spells
   {:name {:dummy "Missing Spell Name"}
    :level {:dummy 0 :check-fn number?}
    :school {:dummy "unknown"}}

   :orcpub.dnd.e5/monsters
   {:name {:dummy "Missing Monster Name"}}

   :orcpub.dnd.e5/invocations
   {:name {:dummy "Missing Invocation Name"}}

   :orcpub.dnd.e5/languages
   {:name {:dummy "Missing Language Name"}}

   :orcpub.dnd.e5/selections
   {:name {:dummy "Missing Selection Name"}}

   :orcpub.dnd.e5/encounters
   {:name {:dummy "Missing Encounter Name"}}})

(def trait-required-fields
  "Required fields for traits (nested within other content types).
   Traits appear in :traits vectors within classes, races, etc."
  {:name {:dummy "Missing Trait Name"}})

(defn field-missing?
  "Check if a required field is missing or invalid.
   Returns true if the field should be filled with dummy data."
  [item field-key field-spec]
  (let [value (get item field-key)
        check-fn (or (:check-fn field-spec) some?)]
    (or (nil? value)
        (and (string? value) (str/blank? value))
        (not (check-fn value)))))

(defn find-missing-fields
  "Find all missing/invalid required fields in an item.
   Returns a vector of {:field :dummy-value} maps for fields that need filling."
  [item content-type]
  (when-let [fields (get required-fields content-type)]
    (reduce-kv
     (fn [missing field-key field-spec]
       (if (and (:dummy field-spec) ; only check fields with dummy values
                (field-missing? item field-key field-spec))
         (conj missing {:field field-key :dummy (:dummy field-spec)})
         missing))
     []
     fields)))

(defn find-missing-trait-fields
  "Find missing required fields in a trait map."
  [trait]
  (reduce-kv
   (fn [missing field-key field-spec]
     (if (field-missing? trait field-key field-spec)
       (conj missing {:field field-key :dummy (:dummy field-spec)})
       missing))
   []
   trait-required-fields))

(defn fill-missing-fields
  "Fill missing required fields in an item with dummy values.
   Returns [updated-item changes] where changes is a vector of field names filled."
  [item content-type]
  (let [missing (find-missing-fields item content-type)]
    (if (seq missing)
      [(reduce (fn [i {:keys [field dummy]}]
                 (assoc i field dummy))
               item
               missing)
       (mapv :field missing)]
      [item []])))

(defn fill-missing-trait-fields
  "Fill missing required fields in a trait with dummy values.
   Returns [updated-trait changes] where changes is a vector of field names filled."
  [trait]
  (let [missing (find-missing-trait-fields trait)]
    (if (seq missing)
      [(reduce (fn [t {:keys [field dummy]}]
                 (assoc t field dummy))
               trait
               missing)
       (mapv :field missing)]
      [trait []])))

(defn fill-traits-in-item
  "Fill missing fields in all traits within an item.
   Returns [updated-item trait-changes] where trait-changes is count of traits fixed."
  [item]
  (if-let [traits (:traits item)]
    (if (vector? traits)
      (let [results (map fill-missing-trait-fields traits)
            updated-traits (mapv first results)
            total-changes (reduce + 0 (map #(count (second %)) results))]
        [(assoc item :traits updated-traits) total-changes])
      [item 0])
    [item 0]))

(def option-required-fields
  "Required fields for options within selections."
  {:name {:dummy "Missing Option Name"}})

(defn fill-missing-option-fields
  "Fill missing required fields in an option with placeholder values.
   Uses 1-based index for placeholder name: [Option 1], [Option 2], etc.
   Returns [updated-option changes] where changes is a vector of field names filled."
  [index option]
  (let [missing (reduce-kv
                 (fn [acc field _field-spec]
                   (if (or (nil? (get option field))
                           (and (string? (get option field))
                                (str/blank? (get option field))))
                     (conj acc {:field field :dummy (str "Option " (inc index))})
                     acc))
                 []
                 option-required-fields)]
    (if (seq missing)
      [(reduce (fn [o {:keys [field dummy]}]
                 (assoc o field dummy))
               option missing)
       (mapv :field missing)]
      [option []])))

(defn fill-options-in-item
  "Fill missing fields in all options within an item (e.g., selections).
   Returns [updated-item options-fixed-count]."
  [item]
  (if-let [options (:options item)]
    (if (vector? options)
      (let [results (map-indexed fill-missing-option-fields options)
            updated-options (mapv first results)
            total-changes (reduce + 0 (map #(count (second %)) results))]
        [(assoc item :options updated-options) total-changes])
      [item 0])
    [item 0]))

(defn fill-all-missing-fields
  "Fill all missing required fields in an item, including nested traits and options.
   Returns {:item updated-item :changes {:fields [...] :traits-fixed N :options-fixed N}}"
  [item content-type]
  (let [[item-with-fields field-changes] (fill-missing-fields item content-type)
        [item-with-traits trait-changes] (fill-traits-in-item item-with-fields)
        [final-item options-changes] (fill-options-in-item item-with-traits)]
    {:item final-item
     :changes {:fields field-changes
               :traits-fixed trait-changes
               :options-fixed options-changes}}))

(defn valid-name?
  "A name that derives a valid key (starts with a letter)."
  [nm]
  (and (string? nm) (common/starts-with-letter? (str/trim nm))))

(defn coerce-name
  "Trimmed name if it's a valid (letter-starting) name, else the fallback."
  [nm fallback]
  (if (valid-name? nm) (str/trim nm) fallback))

(defn sanitize-item-names
  "Coerce the item's :name — and any nested :traits/:options names — to a valid,
   letter-starting name (invalid/blank ones become a placeholder), then re-derive
   the top-level :key from the coerced name. This is what makes 'save anyway'
   safe: it can never persist a name that yields an invalid/structural key (the
   keyword-trap class), the way it used to with e.g. \"1@-asdml;\"."
  [item type-label]
  (let [coerce-nested (fn [coll]
                        (if (vector? coll)
                          (mapv (fn [m] (if (and (map? m) (contains? m :name))
                                          (update m :name coerce-name "Unnamed")
                                          m))
                                coll)
                          coll))
        item* (cond-> (update item :name coerce-name (str "Unnamed " type-label))
                (:traits item)  (update :traits coerce-nested)
                (:options item) (update :options coerce-nested))]
    (assoc item* :key (common/name-to-kw (:name item*)))))

(defn fill-missing-in-content-group
  "Fill missing fields for all items in a content group.
   Returns {:items updated-items :changes [{:key :changes}...]}"
  [content-type items]
  (reduce-kv
   (fn [acc item-key item]
     (let [{:keys [item changes]} (fill-all-missing-fields item content-type)]
       (if (or (seq (:fields changes)) (pos? (:traits-fixed changes)) (pos? (:options-fixed changes)))
         {:items (assoc (:items acc) item-key item)
          :changes (conj (:changes acc) {:key item-key :changes changes})}
         {:items (assoc (:items acc) item-key item)
          :changes (:changes acc)})))
   {:items {} :changes []}
   items))

(defn fill-missing-in-plugin
  "Fill all missing required fields in a plugin.
   Returns {:plugin updated-plugin :all-changes [...]}"
  [plugin]
  (reduce-kv
   (fn [acc content-type content]
     (if (and (qualified-keyword? content-type)
              (= (namespace content-type) "orcpub.dnd.e5")
              (map? content))
       (let [{:keys [items changes]} (fill-missing-in-content-group content-type content)]
         {:plugin (assoc (:plugin acc) content-type items)
          :all-changes (into (:all-changes acc)
                             (map #(assoc % :content-type content-type) changes))})
       {:plugin (assoc (:plugin acc) content-type content)
        :all-changes (:all-changes acc)}))
   {:plugin {} :all-changes []}
   plugin))

(defn fill-missing-in-import
  "Fill missing required fields during import.
   Handles both single-plugin and multi-plugin formats.
   Returns {:data updated-data :changes [change-descriptions]}"
  [data]
  (let [is-multi (is-multi-plugin? data)]
    (if is-multi
      ;; Multi-plugin: fill each plugin separately
      (let [results (reduce-kv
                     (fn [acc plugin-name plugin]
                       (let [{:keys [plugin all-changes]} (fill-missing-in-plugin plugin)]
                         {:data (assoc (:data acc) plugin-name plugin)
                          :all-changes (into (:all-changes acc)
                                             (map #(assoc % :plugin plugin-name) all-changes))}))
                     {:data {} :all-changes []}
                     data)
            change-descriptions (when (seq (:all-changes results))
                                  [{:type :filled-required-fields
                                    :description (str "Filled " (count (:all-changes results))
                                                      " item(s) with missing required fields (names, etc.)")
                                    :details (:all-changes results)}])]
        {:data (:data results)
         :changes (or change-descriptions [])})

      ;; Single plugin
      (let [{:keys [plugin all-changes]} (fill-missing-in-plugin data)
            change-descriptions (when (seq all-changes)
                                  [{:type :filled-required-fields
                                    :description (str "Filled " (count all-changes)
                                                      " item(s) with missing required fields (names, etc.)")
                                    :details all-changes}])]
        {:data plugin
         :changes (or change-descriptions [])}))))

;; ============================================================================
;; Selection Option Deduplication - Remove/rename duplicate options on import
;; ============================================================================

(defn dedup-options-in-selection
  "Dedup options within a single selection by derived key (common/name-to-kw).
   - Identical content (same description, modifiers, etc.): keep first, drop rest
   - Different content but same name: append numbering ('Bonus', 'Bonus 2', etc.)
   Returns [deduped-options changes] where changes is a vector of log entries."
  [options]
  (if (or (not (sequential? options)) (< (count options) 2))
    [options []]
    (let [;; Group by derived key
          grouped (group-by #(when-let [n (:name %)]
                               (when-not (str/blank? n)
                                 (common/name-to-kw n)))
                            options)
          changes (atom [])]
      [(vec
        (mapcat
         (fn [[k opts]]
           (if (or (nil? k) (<= (count opts) 1))
             ;; No duplication or unnamed — pass through
             opts
             ;; Multiple options with the same derived key
             (let [;; Compare content (strip :key and :name for content comparison)
                   content-fn #(dissoc % :key :name)
                   distinct-contents (distinct (map content-fn opts))
                   all-identical? (= 1 (count distinct-contents))]
               (if all-identical?
                 ;; True duplicates — keep first, log the drop
                 (do
                   (swap! changes conj
                          {:type :dedup-identical
                           :description (str "Removed " (dec (count opts))
                                             " duplicate option(s) named '"
                                             (:name (first opts)) "'")})
                   [(first opts)])
                 ;; Same name, different content — rename with numbers
                 (do
                   (swap! changes conj
                          {:type :dedup-renamed
                           :description (str "Renamed " (count opts)
                                             " options with duplicate name '"
                                             (:name (first opts))
                                             "' to unique names")})
                   (map-indexed
                    (fn [i opt]
                      (if (zero? i)
                        opt ;; first keeps original name
                        (let [new-name (str (:name opt) " " (inc i))]
                          (assoc opt
                                 :name new-name
                                 :key (common/name-to-kw new-name)))))
                    opts))))))
         grouped))
       @changes])))

(defn dedup-options-in-item
  "Dedup options for a content item, handling BOTH shapes → [updated-item changes]:
   - a top-level homebrew selection whose `:options` live on the item, and
   - a content item (class/race/…) with a nested `:selections` map, each carrying
     its own `:options`.

   The first branch closed a gap: dedup used to walk only nested `:selections`, so
   duplicate options on an actual homebrew Selection went undeduped on import
   (guarded by test-dedup-options-in-import-full-pipeline)."
  [item]
  (cond
    ;; Top-level selection item — dedup its own :options.
    (sequential? (:options item))
    (let [[deduped changes] (dedup-options-in-selection (:options item))]
      [(assoc item :options deduped) changes])

    ;; Item with a nested :selections map — dedup each selection's options.
    (map? (:selections item))
    (let [result (reduce-kv
                  (fn [acc sel-key sel-data]
                    (if-let [options (:options sel-data)]
                      (let [[deduped changes] (dedup-options-in-selection options)]
                        {:selections (assoc (:selections acc) sel-key
                                            (assoc sel-data :options deduped))
                         :changes (into (:changes acc) changes)})
                      {:selections (assoc (:selections acc) sel-key sel-data)
                       :changes (:changes acc)}))
                  {:selections {} :changes []}
                  (:selections item))]
      [(assoc item :selections (:selections result)) (:changes result)])

    :else [item []]))

(defn dedup-options-in-plugin
  "Dedup options in all selections across all content types in a plugin.
   Returns {:plugin updated-plugin :changes [change-descriptions]}."
  [plugin]
  (reduce-kv
   (fn [acc content-type content]
     (if (and (qualified-keyword? content-type)
              (= (namespace content-type) "orcpub.dnd.e5")
              (map? content))
       (let [result (reduce-kv
                     (fn [inner-acc item-key item]
                       (let [[updated-item changes] (dedup-options-in-item item)]
                         {:items (assoc (:items inner-acc) item-key updated-item)
                          :changes (into (:changes inner-acc)
                                         (map #(assoc % :item-key item-key
                                                        :content-type content-type)
                                              changes))}))
                     {:items {} :changes []}
                     content)]
         {:plugin (assoc (:plugin acc) content-type (:items result))
          :changes (into (:changes acc) (:changes result))})
       {:plugin (assoc (:plugin acc) content-type content)
        :changes (:changes acc)}))
   {:plugin {} :changes []}
   plugin))

(defn dedup-options-in-import
  "Dedup selection options during import. Handles single and multi-plugin formats.
   Returns {:data updated-data :changes [change-descriptions]}."
  [data]
  (let [is-multi (is-multi-plugin? data)]
    (if is-multi
      (let [results (reduce-kv
                     (fn [acc plugin-name plugin]
                       (let [{:keys [plugin changes]} (dedup-options-in-plugin plugin)]
                         {:data (assoc (:data acc) plugin-name plugin)
                          :changes (into (:changes acc)
                                         (map #(assoc % :plugin plugin-name) changes))}))
                     {:data {} :changes []}
                     data)
            change-descriptions (when (seq (:changes results))
                                  [{:type :dedup-selection-options
                                    :description (str "Deduplicated " (count (:changes results))
                                                      " selection option(s) with duplicate names")
                                    :details (:changes results)}])]
        {:data (:data results)
         :changes (or change-descriptions [])})
      (let [{:keys [plugin changes]} (dedup-options-in-plugin data)
            change-descriptions (when (seq changes)
                                  [{:type :dedup-selection-options
                                    :description (str "Deduplicated " (count changes)
                                                      " selection option(s) with duplicate names")
                                    :details changes}])]
        {:data plugin
         :changes (or change-descriptions [])}))))

;; ============================================================================
;; Export Validation - Check for missing required fields before export
;; ============================================================================

(defn validate-item-for-export
  "Check an item for missing required fields (for export validation).
   Returns {:valid true} or {:valid false :missing-fields [...] :traits-missing-names N
            :traits-needing-names [{:index N}...]}"
  [item content-type]
  (let [missing-fields (find-missing-fields item content-type)
        trait-details (when-let [traits (:traits item)]
                        (keep-indexed
                         (fn [idx trait]
                           (when (seq (find-missing-trait-fields trait))
                             {:index idx :current-name (:name trait)}))
                         traits))
        traits-missing (count trait-details)]
    (if (or (seq missing-fields) (pos? traits-missing))
      {:valid false
       :missing-fields (mapv :field missing-fields)
       :traits-missing-names traits-missing
       :traits-needing-names (vec trait-details)}
      {:valid true})))

(defn validate-content-group-for-export
  "Validate all items in a content group for export.
   Returns {:valid true/false :invalid-items [...]}"
  [content-type items]
  (let [results (map (fn [[k v]]
                       (assoc (validate-item-for-export v content-type)
                              :key k :name (:name v)))
                     items)
        invalid (filter #(not (:valid %)) results)]
    {:valid (empty? invalid)
     :invalid-items (vec invalid)}))

(defn validate-plugin-for-export
  "Validate an entire plugin for export.
   Returns {:valid true/false :issues [{:content-type :invalid-items [...]}]}"
  [plugin]
  (let [content-groups (filter
                        (fn [[k _]] (and (qualified-keyword? k)
                                         (= (namespace k) "orcpub.dnd.e5")))
                        plugin)
        validations (map (fn [[k v]]
                           (if (map? v)
                             (assoc (validate-content-group-for-export k v)
                                    :content-type k)
                             {:valid true :content-type k}))
                         content-groups)
        issues (filter #(not (:valid %)) validations)]
    {:valid (empty? issues)
     :issues (vec issues)}))

;; ============================================================================
;; Error Message Formatting
;;
;; Spec problems are notoriously hard to read in the dev console: predicates
;; come through as raw compiler forms like
;;   (cljs.core/fn [v] (cljs.core/or (cljs.core/= v :disabled?) ...))
;; and the `:in` path uses bare integers (0 = a map entry's key, 1 = its
;; value) that mean nothing to a human. The helpers below translate problems
;; into plain English, using the failing spec name (`:via`) and a small table
;; of known predicates, and surface the offending item's :name/:key instead of
;; a value blindly chopped at 50 characters.
;; ============================================================================

(defn- pred-base-name
  "Return the bare (namespace-stripped) name of a predicate symbol so matching
   works regardless of whether it came through as cljs.core/* or clojure.core/*.
   Handles both a plain symbol (`cljs.core/boolean?`) and a call form whose head
   is a symbol (`(cljs.core/map? %)`)."
  [pred]
  (cond
    (symbol? pred) (name pred)
    (and (seq? pred) (symbol? (first pred))) (name (first pred))
    :else nil))

(def ^:private simple-pred-descriptions
  "Plain-English descriptions for common core predicates, keyed by bare name."
  {"boolean?" "must be true or false"
   "string?" "must be a text string"
   "map?" "must be a map of values"
   "vector?" "must be a vector"
   "number?" "must be a number"
   "int?" "must be a whole number"
   "pos-int?" "must be a positive whole number"
   "nat-int?" "must be zero or a positive whole number"
   "keyword?" "must be a keyword"
   "qualified-keyword?" "must be a namespaced keyword (e.g. :orcpub.dnd.e5/spells)"
   "simple-keyword?" "must be an unqualified keyword"
   "set?" "must be a set"
   "coll?" "must be a collection"
   "seq?" "must be a sequence"
   "nil?" "must be nil"
   "ident?" "must be a symbol or keyword"})

(def ^:private spec-descriptions
  "Plain-English descriptions for orcpub domain specs, keyed by the leaf spec
   name from a problem's `:via` (the most specific spec that failed)."
  {"content-keyword" "must be a content-type key like :orcpub.dnd.e5/spells (or :disabled?). Plugin names that are plain strings belong at the multi-plugin top level, not inside a plugin."
   "option-pack" "must be a text string naming the source/pack"
   "homebrew-item" "is missing the required :option-pack field"
   "homebrew-items" "must be a map of content items"
   "homebrew-spell" "is not a valid spell"
   "plugin" "must be a valid plugin"
   "plugins" "must be a valid set of plugins"})

(defn- leaf-spec-name
  "Return the bare name of the most specific spec from a problem's :via, or nil."
  [via]
  (when-let [s (and (seq via) (last via))]
    (when (qualified-keyword? s) (name s))))

(defn missing-required-key
  "If the predicate is a `(contains? % :some-key)` check (how spec/keys reports a
   missing :req-un field), return the missing key. Otherwise nil."
  [pred]
  (when (seq? pred)
    (some (fn [node]
            (when (and (seq? node)
                       (= "contains?" (pred-base-name node)))
              (nth node 2 nil)))
          (tree-seq seq? seq pred))))

(defn humanize-pred
  "Translate a spec predicate (plus its :via context) into a human-readable
   description of what was expected. Falls back to a cleaned predicate name
   rather than dumping the raw compiler form."
  [pred via]
  (let [base-name (pred-base-name pred)
        leaf (leaf-spec-name via)]
    (cond
      ;; spec/keys :req-un failure — the most actionable message
      (missing-required-key pred)
      (str "is missing the required field " (missing-required-key pred))

      ;; A concrete core predicate (boolean?, map?, string?, ...) is the most
      ;; specific thing that failed, so prefer it over the enclosing spec name.
      (simple-pred-descriptions base-name)
      (simple-pred-descriptions base-name)

      ;; Named orcpub domain spec (content-keyword, option-pack, ...)
      (and leaf (spec-descriptions leaf))
      (spec-descriptions leaf)

      ;; spec/keys form surfaced directly
      (= "keys" base-name)
      (str "is missing a required field (expected " (pr-str (rest pred)) ")")

      ;; Last resort: name the check without the noisy compiler form.
      base-name
      (str "failed the " base-name " check")

      :else
      "has an invalid value")))

(defn- truncate-str [s n]
  (if (> (count s) n) (str (subs s 0 (max 0 (- n 3))) "...") s))

(defn- map-entry-selector?
  "In a map-of `:in` path, an entry contributes two segments: 0 for the key and
   1 for the value. These bare ints are navigation noise, not content."
  [seg]
  (and (integer? seg) (or (= seg 0) (= seg 1))))

(defn describe-location
  "Render a spec problem's `:in` path as a readable breadcrumb, dropping the
   map-entry key/value selector ints and quoting string keys."
  [in]
  (if (empty? in)
    "the top level"
    (let [targets-key? (= 0 (last in))
          segs (->> in
                    (remove map-entry-selector?)
                    (map (fn [seg]
                           (cond
                             (string? seg) (str "\"" seg "\"")
                             :else (str seg)))))
          crumb (str/join " > " segs)]
      (cond
        (str/blank? crumb) "the top level"
        targets-key? (str "the key " crumb)
        :else crumb))))

(defn describe-value
  "Describe the offending value. For maps, surface :name/:key (or the keys
   present) so the user can locate the item, instead of chopping raw EDN."
  [val]
  (cond
    (map? val)
    (let [nm (or (:name val) (:key val))
          ks (vec (keys val))]
      (if nm
        (str "the item " (pr-str nm) " (keys: " (truncate-str (pr-str ks) 80) ")")
        (str "a map with " (count ks) " key(s): " (truncate-str (pr-str ks) 80))))

    (vector? val)
    (str "a vector of " (count val) " item(s)")

    :else
    (truncate-str (pr-str val) 50)))

(defn format-spec-problem
  "Converts a single spec problem into a human-readable error message."
  [{:keys [pred val via in]}]
  (str "  • At " (describe-location in) ": " (humanize-pred pred via)
       (when (some? val)
         (str "\n    Got: " (describe-value val)))))

(defn- path-prefix?
  "True if `pre` is a (proper or equal) prefix of `v`."
  [pre v]
  (and (<= (count pre) (count v))
       (= (seq pre) (seq (take (count pre) v)))))

(defn format-validation-errors
  "Formats spec validation errors into user-friendly messages.

   De-duplicates identical lines, drops the redundant boolean? branch that
   spec/or emits alongside a more specific failure deeper in the same path, and
   caps the output so a badly-shaped file can't flood the console."
  ([explain-data] (format-validation-errors explain-data 40))
  ([explain-data max-problems]
   (when explain-data
     (let [problems (:cljs.spec.alpha/problems explain-data)
           ;; `:in` paths of the more specific (non-boolean) failures. When a
           ;; map value fails `(s/or :items ... :bool boolean?)`, spec reports
           ;; both the deep :items failure and a shallow "must be true or false"
           ;; twin at the value's path; the latter is just noise, so drop it
           ;; when a deeper specific failure exists under the same path.
           specific-paths (keep (fn [{:keys [pred in]}]
                                  (when (not= "boolean?" (pred-base-name pred)) in))
                                problems)
           filtered (remove (fn [{:keys [pred in]}]
                              (and (= "boolean?" (pred-base-name pred))
                                   (some #(and (path-prefix? in %)
                                               (> (count %) (count in)))
                                         specific-paths)))
                            problems)
           lines (distinct (map format-spec-problem filtered))
           total (count lines)
           shown (take max-problems lines)
           remaining (- total max-problems)]
       (str "Validation errors found (" total "):\n"
            (str/join "\n" shown)
            (when (pos? remaining)
              (str "\n  ... and " remaining " more (showing first " max-problems ")")))))))

(defn- format-missing-fields-issue
  "Formats one content-type's missing-fields issue into a readable multi-line block."
  [{:keys [content-type invalid-items]}]
  (str "  " (name content-type) " (" (count invalid-items) " item"
       (when (not= 1 (count invalid-items)) "s") "):\n"
       (str/join "\n"
                 (for [{:keys [key name missing-fields traits-missing-names]} invalid-items]
                   (let [label (or name (pr-str key))
                         parts (cond-> []
                                 (seq missing-fields)
                                 (conj (str "missing " (str/join ", " (map pr-str missing-fields))))
                                 (and traits-missing-names (pos? traits-missing-names))
                                 (conj (str traits-missing-names " trait(s) without :name")))]
                     (str "    - " label " [" (str/join "; " parts) "]"))))))

(defn format-export-validation-for-log
  "Converts the result of `validate-before-export` into a plain string suitable
   for console.error. Works around cljs advanced-compilation mangling of
   PersistentVector / PersistentArrayMap class names when they're passed
   directly to js/console.error (which is why errors show up in DevTools as
   cryptic single letters like 'M')."
  [validation]
  (cond
    (:has-missing-required-fields validation)
    (str "Missing required fields:\n"
         (str/join "\n" (map format-missing-fields-issue
                             (:missing-fields-issues validation))))

    (string? (:errors validation))
    (:errors validation)

    (coll? (:errors validation))
    (str/join "\n" (map str (:errors validation)))

    (nil? (:errors validation))
    "(no error details available)"

    :else
    (pr-str (:errors validation))))

;; ============================================================================
;; Parse Error Detection
;; ============================================================================

(defn parse-edn
  "Attempts to parse EDN text with detailed error reporting.

  Returns:
    {:success true :data <parsed-data>} on success
    {:success false :error <error-msg> :line <line-number>} on failure"
  [edn-text]
  (if (str/blank? edn-text)
    {:success false
     :error "Empty input"
     :hint "The file is empty or contains only whitespace"}
    (try
      (let [result (reader/read-string edn-text)]
        {:success true :data result})
    (catch js/Error e
      (let [msg (.-message e)
            line-match (re-find #"line (\d+)" msg)
            line-num (when line-match (js/parseInt (second line-match)))]
        {:success false
         :error msg
         :line line-num
         :hint (cond
                 (str/includes? msg "Unmatched delimiter")
                 "Check for missing or extra brackets/braces/parentheses"

                 (str/includes? msg "EOF")
                 "File appears to be incomplete or corrupted"

                 (str/includes? msg "Invalid token")
                 "File contains invalid characters or syntax"

                 :else
                 "Check the file syntax and ensure it's valid EDN format")})))))

;; ============================================================================
;; Progressive Validation
;; ============================================================================

(defn validate-item
  "Validates a single homebrew item.

  Returns:
    {:valid true} if valid
    {:valid false :errors [...]} if invalid"
  [item-key item]
  (if (spec/valid? ::e5/homebrew-item item)
    {:valid true}
    {:valid false
     :item-key item-key
     :errors (format-validation-errors (spec/explain-data ::e5/homebrew-item item))}))

(defn validate-content-group
  "Validates a group of homebrew content (e.g., all spells, all races).

  Returns map of:
    :valid-count - number of valid items
    :invalid-count - number of invalid items
    :invalid-items - vector of {:key <key> :errors <errors>} for invalid items"
  [content-key items]
  (let [results (map (fn [[k v]] (assoc (validate-item k v) :key k)) items)
        valid (filter :valid results)
        invalid (remove :valid results)]
    {:content-type content-key
     :valid-count (count valid)
     :invalid-count (count invalid)
     :invalid-items (mapv #(select-keys % [:key :errors]) invalid)}))

(defn validate-plugin-progressive
  "Validates a plugin progressively, identifying which specific items are invalid.

  Returns map with:
    :valid - true if entire plugin is valid
    :content-groups - validation results for each content type
    :valid-items-count - total valid items
    :invalid-items-count - total invalid items"
  [plugin]
  (let [content-groups (filter
                        (fn [[k _]] (and (qualified-keyword? k)
                                        (= (namespace k) "orcpub.dnd.e5")))
                        plugin)
        validations (mapv
                     (fn [[k v]]
                       (if (map? v)
                         (validate-content-group k v)
                         {:content-type k :valid-count 1 :invalid-count 0 :invalid-items []}))
                     content-groups)
        total-valid (reduce + 0 (map :valid-count validations))
        total-invalid (reduce + 0 (map :invalid-count validations))]
    {:valid (zero? total-invalid)
     :content-groups validations
     :valid-items-count total-valid
     :invalid-items-count total-invalid}))

;; ============================================================================
;; Pre-Export Validation
;; ============================================================================

(defn validate-before-export
  "Validates plugin data before export to catch bugs early.

  Returns:
    {:valid true :warnings [...]} if exportable (no required field issues)
    {:valid false :errors [...] :missing-fields-issues [...]} if has required field issues"
  [plugin-data]
  (let [required-field-validation (validate-plugin-for-export plugin-data)
        ;; Collect nil-value warnings
        nil-warnings (keep (fn [[k v]] (when (nil? v) (str "Found nil value for key: " k)))
                           plugin-data)
        ;; Collect empty option-pack warnings
        option-pack-warnings (for [[content-key items] plugin-data
                                   :when (and (qualified-keyword? content-key) (map? items))
                                   [item-key item] items
                                   :when (and (map? item)
                                              (or (nil? (:option-pack item))
                                                  (= "" (:option-pack item))))]
                               (str "Item " (name content-key) "/" (name item-key)
                                    " has missing option-pack"))
        warnings (into (vec nil-warnings) option-pack-warnings)]

    ;; Check for required field issues
    (if (not (:valid required-field-validation))
      {:valid false
       :has-missing-required-fields true
       :missing-fields-issues (:issues required-field-validation)
       :warnings warnings
       :errors ["Some items are missing required fields (names, etc.)"]}

      ;; No required field issues - run full spec validation
      (if (spec/valid? ::e5/plugin plugin-data)
        {:valid true
         :warnings warnings}
        {:valid false
         :errors (format-validation-errors (spec/explain-data ::e5/plugin plugin-data))
         :warnings warnings}))))

(defn fill-missing-for-export
  "Fill missing required fields in a plugin for export.
   Returns the updated plugin with all missing fields filled with dummy data."
  [plugin-data]
  (let [{:keys [plugin]} (fill-missing-in-plugin plugin-data)]
    plugin))

(defn classify-plugins-for-export
  "Classify plugins for batch export.
   Returns {:fillable  [{:name :plugin :validation :issues}...]
            :blockers  [{:name :validation}...]
            :clean     [{:name :plugin}...]}"
  [plugins]
  (reduce-kv
   (fn [acc plugin-name plugin]
     (let [v (validate-before-export plugin)]
       (cond
         (:valid v)
         (update acc :clean conj {:name plugin-name :plugin plugin})

         (:has-missing-required-fields v)
         (update acc :fillable conj {:name plugin-name
                                     :plugin plugin
                                     :validation v
                                     :issues (:missing-fields-issues v)})

         :else
         (update acc :blockers conj {:name plugin-name :validation v}))))
   {:fillable [] :blockers [] :clean []}
   plugins))

(defn apply-user-edits-to-plugin
  "Apply the user's export-modal edits to a plugin, then dummy-fill remaining gaps
   into complete/valid content. Used wherever auto-fixed data is needed: the export
   FILE, the write-back to My Content (so library matches file; placeholders are
   self-labeling and flagged), and quarantine repair (re-validates first).

   edits is a map of vector-path → value, e.g.:
     [\"My Pack\" :orcpub.dnd.e5/spells :fireball :level] → 3
     [\"My Pack\" :orcpub.dnd.e5/spells :fireball :trait 0 :name] → \"Fire Aura\"

   Only edits whose first element matches plugin-name are applied; blank/NaN
   values are ignored."
  [plugin plugin-name edits]
  (let [edited-plugin
        (reduce-kv
         (fn [p edit-path value]
           (let [[pname content-type item-key & field-path] edit-path]
             (if (and (= pname plugin-name)
                      (qualified-keyword? content-type)
                      (some? item-key)
                      (seq field-path)
                      (some? value)
                      (not (and (number? value) (js/isNaN value)))
                      (not (and (string? value) (str/blank? value))))
               (if (= :trait (first field-path))
                 ;; Trait edit: field-path is [:trait idx :name]
                 (let [[_ idx field] field-path]
                   (assoc-in p [content-type item-key :traits idx field] value))
                 ;; Direct field edit: field-path is [:level] or [:name] etc.
                 (assoc-in p [content-type item-key (first field-path)] value))
               p)))
         plugin
         edits)]
    (fill-missing-for-export edited-plugin)))

;; ============================================================================
;; Import Strategies
;; ============================================================================

(defn import-all-or-nothing
  "Traditional import: all content must be valid or none is imported."
  [plugin]
  (cond
    (spec/valid? ::e5/plugin plugin)
    {:success true
     :strategy :single-plugin
     :data plugin}

    (spec/valid? ::e5/plugins plugin)
    {:success true
     :strategy :multi-plugin
     :data plugin}

    :else
    {:success false
     :errors [(str "Invalid plugin structure\n\n"
                   (format-validation-errors (spec/explain-data ::e5/plugin plugin))
                   "\n\nIf this is a multi-plugin file:\n"
                   (format-validation-errors (spec/explain-data ::e5/plugins plugin)))]}))

(defn remove-invalid-items
  "Removes invalid items from a content group, keeping only valid ones."
  [content-key items]
  (into {}
        (filter (fn [[k v]]
                  (:valid (validate-item k v)))
                items)))

(defn is-multi-plugin?
  "Check if the data is a multi-plugin structure (string keys at top level)."
  [data]
  (and (map? data)
       (seq data)
       (every? string? (keys data))))

(defn- count-items-in-plugin
  "Count all items in content groups within a single plugin."
  [plugin]
  (reduce
   (fn [total [k v]]
     (if (and (qualified-keyword? k)
              (= (namespace k) "orcpub.dnd.e5")
              (map? v))
       (+ total (count v))
       total))
   0
   plugin))

(defn import-progressive
  "Progressive import: imports valid items and reports invalid ones.

  Returns:
    {:success true
     :data <cleaned-plugin>
     :imported-count <number>
     :skipped-count <number>
     :skipped-items [...]}

  This allows users to recover as much data as possible from corrupted files.
  Handles both single-plugin and multi-plugin structures."
  [plugin]
  (if (map? plugin)
    (if (is-multi-plugin? plugin)
      ;; Multi-plugin: aggregate counts from all inner plugins
      (let [total-items (reduce
                         (fn [total [_plugin-name inner-plugin]]
                           (+ total (count-items-in-plugin inner-plugin)))
                         0
                         plugin)]
        {:success true
         :data plugin
         :imported-count total-items
         :skipped-count 0
         :skipped-items []
         :had-errors false})
      ;; Single-plugin: use existing validation
      (let [validation (validate-plugin-progressive plugin)
            cleaned-plugin (into {}
                                 (map (fn [[k v]]
                                        (if (and (qualified-keyword? k) (map? v))
                                          [k (remove-invalid-items k v)]
                                          [k v]))
                                      plugin))
            invalid-items (mapcat :invalid-items (:content-groups validation))]
        {:success true
         :data cleaned-plugin
         :imported-count (:valid-items-count validation)
         :skipped-count (:invalid-items-count validation)
         :skipped-items invalid-items
         :had-errors (pos? (:invalid-items-count validation))}))
    {:success false
     :errors ["Plugin is not a valid map structure"]}))

;; ============================================================================
;; Data-Level Cleaning (after parse) - With Change Tracking
;; ============================================================================

;; Fields where nil should be replaced with a default value. A source-less item
;; lands in the real built-in "Default Option Source" plugin (db.cljs) rather than
;; a phantom "Unnamed Content" one, so it's manageable content, not an orphan.
(def default-option-source "Default Option Source")

(def nil-replace-defaults
  {:disabled? false
   :option-pack default-option-source})

;; Fields where nil is semantically meaningful and should be preserved
;; NOTE: :spellcasting is NOT preserved because nil means "no spellcasting"
;; which is the same as the key being absent. Preserving it caused issues
;; with classes like Mystic that legitimately have no spellcasting.
(def nil-preserve-fields
  #{:spell-list-kw :ability :class-key})

;; Fields where nil should be removed entirely (inside nested maps)
;; These are typically numeric fields where nil is accidental,
;; or optional fields where nil means "not present"
(def nil-remove-in-maps
  #{:str :dex :con :int :wis :cha  ; ability scores
    :ac :hp :speed                  ; stats
    :level :modifier :die :die-count ; numeric fields
    :spellcasting})                  ; nil means no spellcasting, should be absent

(defn clean-nil-in-map-with-log
  "Removes/replaces nil values for specific keys in a map, tracking changes.
   Also removes entries where the key itself is nil (e.g., {nil nil}).
   Returns {:data <cleaned-map> :changes [...]}"
  ([m] (clean-nil-in-map-with-log m []))
  ([m path]
   (if (map? m)
     (let [changes (atom [])
           cleaned (into {}
                         (keep (fn [[k v]]
                                 (let [current-path (conj path k)]
                                   (cond
                                     ;; Remove entries with nil keys (e.g., {nil nil, :key :foo})
                                     (nil? k)
                                     (do
                                       (swap! changes conj {:type :removed-nil-key
                                                            :path path
                                                            :value v})
                                       nil)

                                     ;; Replace with default if in replace list
                                     (and (nil? v) (contains? nil-replace-defaults k))
                                     (do
                                       (swap! changes conj {:type :replaced-nil
                                                            :path path
                                                            :field k
                                                            :to (get nil-replace-defaults k)})
                                       [k (get nil-replace-defaults k)])

                                     ;; Preserve nil if in preserve list
                                     (and (nil? v) (contains? nil-preserve-fields k))
                                     (do
                                       (swap! changes conj {:type :preserved-nil
                                                            :path path
                                                            :field k})
                                       [k v])

                                     ;; Remove nil if in remove list
                                     (and (nil? v) (contains? nil-remove-in-maps k))
                                     (do
                                       (swap! changes conj {:type :removed-nil
                                                            :path path
                                                            :field k})
                                       nil)

                                     ;; Recurse into nested maps
                                     (map? v)
                                     (let [result (clean-nil-in-map-with-log v current-path)]
                                       (swap! changes into (:changes result))
                                       [k (:data result)])

                                     ;; Recurse into vectors
                                     (vector? v)
                                     (let [results (map-indexed
                                                    (fn [idx item]
                                                      (if (map? item)
                                                        (let [r (clean-nil-in-map-with-log item (conj current-path idx))]
                                                          (swap! changes into (:changes r))
                                                          (:data r))
                                                        item))
                                                    v)]
                                       [k (vec results)])

                                     ;; Keep everything else as-is
                                     :else
                                     [k v])))
                               m))]
       {:data cleaned :changes @changes})
     {:data m :changes []})))

(defn fix-empty-option-pack-with-log
  "Fixes empty string option-pack values in items, tracking changes.
   Returns {:data <cleaned> :changes [...]}"
  ([data] (fix-empty-option-pack-with-log data []))
  ([data path]
   (if (map? data)
     (let [changes (atom [])
           cleaned (into {}
                         (map (fn [[k v]]
                                (let [current-path (conj path k)]
                                  (cond
                                    ;; Fix empty option-pack in homebrew items
                                    (and (= k :option-pack) (= v ""))
                                    (do
                                      (swap! changes conj {:type :fixed-option-pack
                                                           :path path
                                                           :from ""
                                                           :to default-option-source})
                                      [k default-option-source])

                                    ;; Recurse into nested structures
                                    (map? v)
                                    (let [result (fix-empty-option-pack-with-log v current-path)]
                                      (swap! changes into (:changes result))
                                      [k (:data result)])

                                    (vector? v)
                                    (let [results (map-indexed
                                                   (fn [idx item]
                                                     (if (map? item)
                                                       (let [r (fix-empty-option-pack-with-log item (conj current-path idx))]
                                                         (swap! changes into (:changes r))
                                                         (:data r))
                                                       item))
                                                   v)]
                                      [k (vec results)])

                                    :else
                                    [k v])))
                              data))]
       {:data cleaned :changes @changes})
     {:data data :changes []})))

(defn rename-empty-plugin-key-with-log
  "Renames empty string plugin key to a unique name, tracking changes.
   Returns {:data <cleaned> :changes [...]}"
  [data]
  (if (and (map? data) (contains? data ""))
    (let [base-name "Unnamed Content"
          ;; Find a unique name if base-name already exists
          unique-name (if (contains? data base-name)
                        (loop [n 2]
                          (let [candidate (str base-name " " n)]
                            (if (contains? data candidate)
                              (recur (inc n))
                              candidate)))
                        base-name)]
      {:data (-> data
                 (assoc unique-name (get data ""))
                 (dissoc ""))
       :changes [{:type :renamed-plugin-key
                  :from ""
                  :to unique-name}]})
    {:data data :changes []}))

(defn clean-data-with-log
  "Applies all data-level cleaning transformations, tracking all changes.
   Returns {:data <cleaned> :changes [...]}"
  [data]
  (let [step1 (rename-empty-plugin-key-with-log data)
        step2 (fix-empty-option-pack-with-log (:data step1) [])
        step3 (clean-nil-in-map-with-log (:data step2) [])]
    {:data (:data step3)
     :changes (vec (concat (:changes step1)
                           (:changes step2)
                           (:changes step3)))}))

;; Keep original functions for backwards compatibility
(defn clean-nil-in-map [m]
  (:data (clean-nil-in-map-with-log m)))

(defn fix-empty-option-pack [data]
  (:data (fix-empty-option-pack-with-log data)))

(defn rename-empty-plugin-key [data]
  (:data (rename-empty-plugin-key-with-log data)))

(defn clean-data [data]
  (:data (clean-data-with-log data)))

;; ============================================================================
;; Export blank-stripping — don't ship meaningless nils/falses/empties.
;; ============================================================================

(def export-keep-nil-keys
  "Keys where a nil VALUE is meaningful and must survive export (mirrors the
   import-side nil-preserve-fields)."
  #{:spell-list-kw :ability :class-key})

(defn- blank-for-export?
  "A map entry is a meaningless blank to drop on export when its (already-cleaned)
   value is nil (and the key isn't keep-nil), false, or an empty collection."
  [k v]
  (and (not (contains? export-keep-nil-keys k))
       (or (nil? v)
           (false? v)
           (and (coll? v) (empty? v)))))

(defn strip-export-blanks
  "Recursively drop meaningless blank MAP VALUES for export (nil, false, empty
   collection), except keys where nil is meaningful (export-keep-nil-keys).
   Vector/set elements are cleaned but never dropped positionally, so pairs like
   `[prof-kw first-class?]` survive. Only removes blanks, never alters a real value
   (round-trip safe — see export-strip tests). NORMAL exports only; raw/draft stay
   byte-for-byte untouched."
  [data]
  (cond
    (map? data)
    (reduce-kv (fn [m k v]
                 (let [v' (strip-export-blanks v)]
                   (if (blank-for-export? k v')
                     m
                     (assoc m k v'))))
               {}
               data)
    (vector? data) (mapv strip-export-blanks data)
    (set? data) (into #{} (map strip-export-blanks) data)
    (seq? data) (doall (map strip-export-blanks data))
    :else data))

;; ============================================================================
;; Duplicate Key Detection
;; ============================================================================

(def content-type-names
  "Human-readable names for content types."
  {:orcpub.dnd.e5/classes "classes"
   :orcpub.dnd.e5/subclasses "subclasses"
   :orcpub.dnd.e5/races "races"
   :orcpub.dnd.e5/subraces "subraces"
   :orcpub.dnd.e5/backgrounds "backgrounds"
   :orcpub.dnd.e5/feats "feats"
   :orcpub.dnd.e5/spells "spells"
   :orcpub.dnd.e5/monsters "monsters"
   :orcpub.dnd.e5/invocations "invocations"
   :orcpub.dnd.e5/selections "selections"
   :orcpub.dnd.e5/languages "languages"
   :orcpub.dnd.e5/encounters "encounters"})

(def content-type-singular
  "Singular, capitalized label per content type, for placeholder names like
   'Unnamed Race' (sanitize-item-names appends it after 'Unnamed ')."
  {:orcpub.dnd.e5/classes "Class"       :orcpub.dnd.e5/subclasses "Subclass"
   :orcpub.dnd.e5/races "Race"          :orcpub.dnd.e5/subraces "Subrace"
   :orcpub.dnd.e5/backgrounds "Background" :orcpub.dnd.e5/feats "Feat"
   :orcpub.dnd.e5/spells "Spell"        :orcpub.dnd.e5/monsters "Monster"
   :orcpub.dnd.e5/invocations "Invocation" :orcpub.dnd.e5/selections "Selection"
   :orcpub.dnd.e5/languages "Language"  :orcpub.dnd.e5/encounters "Encounter"
   :orcpub.dnd.e5/boons "Boon"})

(defn coerce-invalid-names
  "Coerce any present-but-INVALID item name (and nested trait/option names) to a
   valid letter-leading placeholder, re-keying the item from the fixed name.
   Blanks are already handled by fill-missing-*; this catches the
   present-but-invalid case (e.g. \"@@@\") so the recovery panel's 'Fix & Restore'
   just works in one click instead of forcing the user to hand-type a name."
  [plugin]
  (if-not (map? plugin)
    plugin
    (reduce-kv
     (fn [p ct items]
       (if (and (qualified-keyword? ct) (map? items))
         (assoc p ct
                (reduce-kv
                 (fn [m ik item]
                   (assoc m ik (sanitize-item-names item (get content-type-singular ct "Item"))))
                 {} items))
         (assoc p ct items)))
     {} plugin)))

(defn find-duplicate-keys-in-content
  "Finds duplicate keys within a single content group.
   Returns a vector of {:key :content-type :sources [...]} for each duplicate."
  [content-type items source-name]
  ;; Since items is a map, keys are inherently unique within it.
  ;; But we track them for cross-source comparison.
  (mapv (fn [[k v]]
          {:key k
           :content-type content-type
           :source source-name
           :name (or (:name v) (common/kw-to-name k))})
        items))

(defn collect-all-keys-from-plugin
  "Collects all content keys from a single plugin.
   Returns {:content-type [{:key :source :name} ...]}."
  [plugin source-name]
  (reduce
   (fn [acc [content-type items]]
     (if (and (qualified-keyword? content-type)
              (= (namespace content-type) "orcpub.dnd.e5")
              (map? items))
       (update acc content-type
               (fnil into [])
               (mapv (fn [[k v]]
                       {:key k
                        :source source-name
                        :name (or (:name v) (common/kw-to-name k))})
                     items))
       acc))
   {}
   plugin))

(defn collect-all-keys-from-plugins
  "Collects all content keys from multiple plugins.
   Input: plugins map {source-name plugin-data}
   Returns {:content-type [{:key :source :name} ...]}."
  [plugins]
  (reduce
   (fn [acc [source-name plugin]]
     (let [plugin-keys (collect-all-keys-from-plugin plugin source-name)]
       (merge-with into acc plugin-keys)))
   {}
   plugins))

(defn find-key-conflicts
  "Finds keys that appear in multiple sources.
   Input: key-map from collect-all-keys-from-plugins
   Returns vector of conflicts:
   [{:key :content-type :sources [{:source :name} ...]}]"
  [keys-by-type]
  (reduce-kv
   (fn [conflicts content-type items]
     (let [;; Group by key
           by-key (group-by :key items)
           ;; Find keys with multiple sources
           duplicates (filter (fn [[_ sources]] (> (count sources) 1)) by-key)]
       (into conflicts
             (map (fn [[k sources]]
                    {:key k
                     :content-type content-type
                     :content-type-name (get content-type-names content-type
                                             (name content-type))
                     :sources (mapv #(select-keys % [:source :name]) sources)})
                  duplicates))))
   []
   keys-by-type))

(defn detect-duplicate-keys
  "Detects duplicate keys in imported data and against existing plugins.

   Parameters:
   - import-data: the data being imported (single or multi-plugin)
   - existing-plugins: currently loaded plugins map (optional)
   - import-source-name: name for single-plugin imports

   Returns:
   {:internal-conflicts [...] - duplicates within the import
    :external-conflicts [...] - conflicts with existing plugins}"
  [import-data existing-plugins import-source-name]
  (let [;; Normalize import to multi-plugin format for consistent processing
        import-as-multi (if (is-multi-plugin? import-data)
                          import-data
                          {(or import-source-name "Imported Content") import-data})

        ;; Collect keys from import
        import-keys (collect-all-keys-from-plugins import-as-multi)

        ;; Find internal conflicts (within the import)
        internal-conflicts (find-key-conflicts import-keys)

        ;; Find external conflicts (import vs existing)
        external-conflicts (when existing-plugins
                            (let [existing-keys (collect-all-keys-from-plugins existing-plugins)]
                              (for [[content-type import-items] import-keys
                                    {:keys [key source] item-name :name} import-items
                                    :let [existing-items (get existing-keys content-type)
                                          existing (when existing-items
                                                     (first (filter #(= (:key %) key) existing-items)))]
                                    :when (and existing (not= source (:source existing)))]
                                {:key key
                                 :content-type content-type
                                 :content-type-name (get content-type-names content-type
                                                         (clojure.core/name content-type))
                                 :import-source source
                                 :import-name item-name
                                 :existing-source (:source existing)
                                 :existing-name (:name existing)})))]
    {:internal-conflicts internal-conflicts
     :external-conflicts (or external-conflicts [])}))

(defn format-duplicate-key-warnings
  "Formats duplicate key conflicts into user-friendly warning messages."
  [{:keys [internal-conflicts external-conflicts]}]
  (into
   (mapv (fn [{:keys [key content-type-name sources]}]
           {:type :internal-duplicate
            :severity :warning
            :message (str "Duplicate " content-type-name " key :" (name key)
                         " found in: " (str/join ", " (map :source sources)))})
         internal-conflicts)
   (map (fn [{:keys [key content-type-name import-source import-name
                     existing-source existing-name]}]
          {:type :external-duplicate
           :severity :warning
           :key key
           :content-type-name content-type-name
           :message (str "Key :" (name key) " (" content-type-name ") conflicts: "
                        "\"" import-name "\" from " import-source
                        " vs \"" existing-name "\" from " existing-source)})
        external-conflicts)))

;; ============================================================================
;; Fuzzy Key Matching
;; ============================================================================

(defn levenshtein-distance
  "Calculate the Levenshtein edit distance between two strings.
   Used for fuzzy matching similar key names."
  [s1 s2]
  (let [s1 (name s1)
        s2 (name s2)
        len1 (count s1)
        len2 (count s2)
        len-diff (Math/abs (- len1 len2))]
    (cond
      (zero? len1) len2
      (zero? len2) len1
      ;; Early return: if lengths differ by more than 10, edit distance is at
      ;; least len-diff and the normalized similarity score will be very low.
      ;; Skip the expensive O(n*m) matrix computation.
      (> len-diff 10) len-diff
      :else
      (let [;; Create distance matrix
            matrix (vec (for [i (range (inc len1))]
                          (vec (for [j (range (inc len2))]
                                 (cond
                                   (zero? i) j
                                   (zero? j) i
                                   :else 0)))))]
        ;; Fill in the matrix
        (loop [i 1
               m matrix]
          (if (> i len1)
            (get-in m [len1 len2])
            (recur (inc i)
                   (loop [j 1
                          m2 m]
                     (if (> j len2)
                       m2
                       (let [cost (if (= (nth s1 (dec i)) (nth s2 (dec j))) 0 1)
                             val (min (inc (get-in m2 [(dec i) j]))        ; deletion
                                      (inc (get-in m2 [i (dec j)]))        ; insertion
                                      (+ cost (get-in m2 [(dec i) (dec j)])))] ; substitution
                         (recur (inc j)
                                (assoc-in m2 [i j] val))))))))))))

(defn key-similarity-score
  "Calculate a similarity score between two keys.
   Higher score = more similar. Returns {:score :reason}."
  [missing-key candidate-key]
  (let [missing-str (name missing-key)
        candidate-str (name candidate-key)
        missing-lower (str/lower-case missing-str)
        candidate-lower (str/lower-case candidate-str)]
    (cond
      ;; Exact match (shouldn't happen, but handle it)
      (= missing-key candidate-key)
      {:score 100 :reason :exact}

      ;; Candidate starts with missing key (e.g., :mystic matches :mystic-kibbles-tasty)
      (str/starts-with? candidate-lower missing-lower)
      {:score (- 90 (- (count candidate-str) (count missing-str)))
       :reason :prefix-match}

      ;; Missing key starts with candidate (less likely but possible)
      (str/starts-with? missing-lower candidate-lower)
      {:score (- 80 (- (count missing-str) (count candidate-str)))
       :reason :candidate-prefix}

      ;; Same display name after kw-to-name conversion
      (= (common/kw-to-name missing-key) (common/kw-to-name candidate-key))
      {:score 85 :reason :same-display-name}

      ;; Levenshtein distance - closer names score higher
      :else
      (let [distance (levenshtein-distance missing-str candidate-str)
            max-len (max (count missing-str) (count candidate-str))
            ;; Normalize: 0 distance = score 70, max distance = score 0
            normalized (if (zero? max-len)
                         0
                         (int (* 70 (- 1 (/ distance max-len)))))]
        {:score (max 0 normalized)
         :reason :levenshtein}))))

(defn find-similar-keys
  "Find keys similar to the missing key from available keys.
   Returns sorted vector of {:key :score :reason :source :name} maps.
   Only returns matches with score >= min-score (default 30)."
  ([missing-key available-keys-info]
   (find-similar-keys missing-key available-keys-info 30))
  ([missing-key available-keys-info min-score]
   (let [matches (->> available-keys-info
                      (map (fn [{:keys [key source name] :as info}]
                             (let [{:keys [score reason]} (key-similarity-score missing-key key)]
                               (assoc info :score score :reason reason))))
                      (filter #(>= (:score %) min-score))
                      (sort-by :score >)
                      (take 5))] ; Return top 5 matches
     (vec matches))))

(defn build-key-lookup-index
  "Build an index of all available keys from loaded plugins.
   Returns {:content-type [{:key :source :name} ...]}."
  [plugins]
  (collect-all-keys-from-plugins plugins))

(defn suggest-key-matches
  "Given a missing key and content type, find suggestions from loaded plugins.
   Returns vector of suggestions or empty vector if no good matches."
  [missing-key content-type plugins]
  (let [index (build-key-lookup-index plugins)
        available (get index content-type [])]
    (find-similar-keys missing-key available)))

;; ============================================================================
;; Shared correction pass (import AND export)
;; ============================================================================

(defn correct-library
  "Runs the same data-level cleanups the importer runs, but over an entire
   already-parsed library (multi-plugin map of source-name -> plugin), and
   detects cross-source key conflicts. This is the single gate both boundaries
   share: whatever import would fix, export runs too.

   Returns {:data <corrected library>
            :changes [...]                ; silent fixes applied (for logging)
            :key-conflicts {:internal-conflicts [...] :external-conflicts [...]}}.

   Note: unlike the import pipeline this does NOT auto-fill missing required
   fields — on export those are surfaced to the user through the fill-in dialog
   (classify-plugins-for-export) rather than silently placeholdered."
  [library]
  (let [normalized (normalize-text-in-data library)
        text-normalized? (not= library normalized)
        clean-result (clean-data-with-log normalized)
        dedup-result (dedup-options-in-import (:data clean-result))
        corrected (:data dedup-result)
        changes (vec (concat (when text-normalized?
                               [{:type :text-normalization
                                 :description "Normalized Unicode characters (smart quotes, dashes, etc.) to ASCII"}])
                             (:changes clean-result)
                             (:changes dedup-result)))
        ;; existing-plugins nil -> only within-library (internal) conflicts, which
        ;; is exactly what would collide when this library is re-imported.
        key-conflicts (detect-duplicate-keys corrected nil nil)]
    {:data corrected
     :changes changes
     :key-conflicts key-conflicts}))

;; ============================================================================
;; Main Validation Entry Point
;; ============================================================================

(defn validate-import
  "Main validation function for orcbrew file imports.

  Options:
    :strategy - :strict (all-or-nothing) or :progressive (import valid items)
    :auto-clean - whether to apply automatic cleaning fixes
    :existing-plugins - currently loaded plugins (for duplicate key detection)
    :import-source-name - name to use for single-plugin imports

  Returns detailed validation results with user-friendly error messages.
  Includes :changes key with list of all cleaning operations performed.
  Includes :key-conflicts key with duplicate key warnings."
  [edn-text {:keys [strategy auto-clean existing-plugins import-source-name]
             :or {strategy :progressive auto-clean true}}]

  ;; Step 1: String-level cleaning (syntax fixes only) with tracking
  (let [string-changes (atom [])
        cleaned-text (if auto-clean
                       (let [;; Count disabled? nil fixes
                             disabled-matches (re-seq #"disabled\?\s+nil" edn-text)
                             disabled-count (count disabled-matches)
                             after-disabled (str/replace edn-text #"disabled\?\s+nil" "disabled? false")

                             ;; Count nil nil, fixes (spurious nil key-value pairs like {nil nil, :key :foo})
                             nil-nil-matches (re-seq #"nil\s+nil\s*," after-disabled)
                             nil-nil-count (count nil-nil-matches)
                             after-nil-nil (str/replace after-disabled #"nil\s+nil\s*,\s*" "")

                             ;; Count trailing comma fixes
                             brace-matches (re-seq #",\s*\}" after-nil-nil)
                             bracket-matches (re-seq #",\s*\]" after-nil-nil)
                             comma-count (+ (count brace-matches) (count bracket-matches))
                             after-commas (-> after-nil-nil
                                              (str/replace #",\s*\}" "}")
                                              (str/replace #",\s*\]" "]"))]

                         ;; Record string-level changes
                         (when (pos? disabled-count)
                           (swap! string-changes conj
                                  {:type :string-fix
                                   :description (str "Fixed " disabled-count " 'disabled? nil' → 'disabled? false'")}))
                         (when (pos? nil-nil-count)
                           (swap! string-changes conj
                                  {:type :string-fix
                                   :description (str "Removed " nil-nil-count " spurious 'nil nil,' entries")}))
                         (when (pos? comma-count)
                           (swap! string-changes conj
                                  {:type :string-fix
                                   :description (str "Removed " comma-count " trailing comma(s)")}))
                         after-commas)
                       edn-text)
        ;; Step 2: Parse EDN
        parse-result (parse-edn cleaned-text)]

    (if (:success parse-result)

        ;; Step 2.5: Normalize text (Unicode → ASCII) for reliable PDF/export
        (let [parsed-data (:data parse-result)
              normalized-data (if auto-clean
                                (normalize-text-in-data parsed-data)
                                parsed-data)
              ;; Track if normalization made changes (compare before/after)
              text-normalized? (and auto-clean (not= parsed-data normalized-data))

              ;; Step 3: Data-level cleaning (semantic fixes) with tracking
              clean-result (if auto-clean
                             (clean-data-with-log normalized-data)
                             {:data normalized-data :changes []})

              ;; Step 3.5: Fill missing required fields with dummy data
              fill-result (if auto-clean
                            (fill-missing-in-import (:data clean-result))
                            {:data (:data clean-result) :changes []})

              ;; Step 3.75: Dedup selection options (same-name options within selections)
              dedup-result (if auto-clean
                             (dedup-options-in-import (:data fill-result))
                             {:data (:data fill-result) :changes []})

              all-changes (vec (concat @string-changes
                                       (when text-normalized?
                                         [{:type :text-normalization
                                           :description "Normalized Unicode characters (smart quotes, dashes, etc.) to ASCII"}])
                                       (:changes clean-result)
                                       (:changes fill-result)
                                       (:changes dedup-result)))

              ;; Step 4: Detect duplicate keys (uses deduped data)
              key-conflicts (detect-duplicate-keys (:data dedup-result)
                                                   existing-plugins
                                                   import-source-name)
              key-warnings (format-duplicate-key-warnings key-conflicts)

              ;; Step 5: Validate structure based on strategy (uses deduped data)
              validation-result (if (= strategy :strict)
                                  (import-all-or-nothing (:data dedup-result))
                                  (import-progressive (:data dedup-result)))]

          ;; Add changes and key conflict info to result
          (assoc validation-result
                 :changes all-changes
                 :key-conflicts key-conflicts
                 :key-warnings key-warnings))

        ;; Parse failed - return detailed error
        {:success false
         :parse-error true
         :error (:error parse-result)
         :line (:line parse-result)
         :hint (:hint parse-result)
         :changes @string-changes})))

;; ============================================================================
;; Key Renaming (for conflict resolution)
;; ============================================================================

(def key-reference-map
  "Maps content types to fields that reference other content keys.
   Used to update internal references when renaming keys."
  {:orcpub.dnd.e5/subclasses {:class :orcpub.dnd.e5/classes}    ; :class field references a class key
   :orcpub.dnd.e5/subraces {:race :orcpub.dnd.e5/races}})       ; :race field references a race key

(defn generate-new-key
  "Generate a new key by appending source identifier.
   E.g., :artificer + 'Kibbles Tasty' → :artificer-kibbles-tasty
   Uses common/name-to-kw pattern for consistent slugification."
  [original-key source-name]
  (let [source-slug (name (common/name-to-kw source-name))]
    (keyword (str (name original-key) "-" source-slug))))

(defn update-references-in-item
  "Update references to a renamed key within a single item.
   reference-field: the field in this item that may reference the old key
   old-key: the original key being renamed
   new-key: the new key it's being renamed to"
  [item reference-field old-key new-key]
  (if (= (get item reference-field) old-key)
    (assoc item reference-field new-key)
    item))

(defn update-references-in-content-group
  "Update all references to a renamed key within a content group."
  [items reference-field old-key new-key]
  (into {}
        (map (fn [[k v]]
               [k (update-references-in-item v reference-field old-key new-key)])
             items)))

(defn rename-key-in-plugin
  "Rename a key within a single plugin, updating all internal references.

   Parameters:
   - plugin: the plugin data map
   - content-type: which content type contains the key (e.g., :orcpub.dnd.e5/classes)
   - old-key: the current key to rename
   - new-key: the new key to use

   Returns the updated plugin with:
   1. The item moved to the new key
   2. All internal references updated (e.g., subclasses pointing to renamed class)"
  [plugin content-type old-key new-key]
  (if-let [content-group (get plugin content-type)]
    ;; Only rename when the item actually exists. A redundant rename (e.g. a key
    ;; that is BOTH an internal conflict — same key across import sources — and an
    ;; external one vs existing content generates two renames for it) would
    ;; otherwise hit an already-moved key and `(assoc new-key nil)`, fabricating a
    ;; `key -> nil` entry that fails ::plugin and quarantines the whole source.
    (if-let [item (get content-group old-key)]
      (let [;; Step 1: Rename the key in its content group
            updated-group (-> content-group
                              (dissoc old-key)
                              (assoc new-key item))

            ;; Step 2: Find content types that reference this type
            referencing-types (keep (fn [[ct refs]]
                                      (when (some #(= (val %) content-type) refs)
                                        [ct (key (first (filter #(= (val %) content-type) refs)))]))
                                    key-reference-map)

            ;; Step 3: Update references in those content types
            updated-plugin (reduce
                            (fn [p [ref-content-type ref-field]]
                              (if-let [ref-group (get p ref-content-type)]
                                (assoc p ref-content-type
                                       (update-references-in-content-group
                                        ref-group ref-field old-key new-key))
                                p))
                            (assoc plugin content-type updated-group)
                            referencing-types)]
        updated-plugin)
      ;; old-key already gone — a no-op, not a nil-clobber.
      plugin)
    plugin))

(defn rename-key-in-plugins
  "Rename a key within a multi-plugin structure.

   Parameters:
   - plugins: map of {source-name plugin-data}
   - source-name: which source contains the key to rename
   - content-type: which content type (e.g., :orcpub.dnd.e5/classes)
   - old-key: current key
   - new-key: new key

   Returns updated plugins map."
  [plugins source-name content-type old-key new-key]
  (if-let [plugin (get plugins source-name)]
    (assoc plugins source-name
           (rename-key-in-plugin plugin content-type old-key new-key))
    plugins))

(defn apply-key-renames
  "Apply a batch of key renames to import data.

   Parameters:
   - data: the import data (single or multi-plugin)
   - renames: vector of {:source :content-type :from :to}

   Returns updated data with all renames applied."
  [data renames]
  (let [is-multi (is-multi-plugin? data)]
    (reduce
     (fn [d {:keys [source content-type from to]}]
       (if is-multi
         (rename-key-in-plugins d source content-type from to)
         (rename-key-in-plugin d content-type from to)))
     data
     renames)))

;; ============================================================================
;; User-Friendly Error Messages
;; ============================================================================

(defn format-key-conflict-section
  "Formats key conflicts into a section for display."
  [{:keys [key-conflicts key-warnings]}]
  (when (seq key-warnings)
    (let [internal (filter #(= :internal-duplicate (:type %)) key-warnings)
          external (filter #(= :external-duplicate (:type %)) key-warnings)]
      (str "\n\n⚠️ Key Conflicts Detected:\n"
           (when (seq internal)
             (str "\nWithin this file:\n"
                  (str/join "\n" (map #(str "  • " (:message %)) internal))))
           (when (seq external)
             (str "\nWith existing content:\n"
                  (str/join "\n" (map #(str "  • " (:message %)) external))))
           "\n\nDuplicate keys can cause unexpected behavior. "
           "Consider renaming one of the conflicting items."))))

(defn format-import-result
  "Formats validation result into a user-friendly message."
  [result]
  (let [key-conflict-section (format-key-conflict-section result)]
    (cond
      ;; Parse error
      (:parse-error result)
      (str "⚠️ Could not read file\n\n"
           "Error: " (:error result) "\n"
           (when (:line result)
             (str "Line: " (:line result) "\n"))
           "\n" (:hint result)
           "\n\nThe file may be corrupted or incomplete. "
           "Try exporting a fresh copy if you have the original source.")

      ;; Validation error (strict mode)
      (and (not (:success result)) (:errors result))
      (str "⚠️ Invalid orcbrew file\n\n"
           (str/join "\n\n" (:errors result))
           "\n\nTo recover data from this file, you can:"
           "\n1. Try progressive import (imports valid items, skips invalid ones)"
           "\n2. Check the browser console for detailed validation errors"
           "\n3. Export a fresh copy if you have the original source")

      ;; Progressive import with some items skipped
      (:had-errors result)
      (str "⚠️ Import completed with warnings\n\n"
           "Imported: " (:imported-count result) " valid items\n"
           "Skipped: " (:skipped-count result) " invalid items\n\n"
           "Invalid items were skipped. Check the browser console for details."
           key-conflict-section
           "\n\nTo be safe, export all content now to create a clean backup.")

      ;; Successful import (but may have key conflicts)
      (:success result)
      (str (if (seq (:key-warnings result))
             "⚠️ Import successful with warnings"
             "✅ Import successful")
           "\n\n"
           (when (:imported-count result)
             (str "Imported " (:imported-count result) " items"))
           key-conflict-section
           "\n\nTo be safe, export all content now to create a clean backup.")

      ;; Unknown result
      :else
      "❌ Unknown import result")))
