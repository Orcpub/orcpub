(ns orcpub.common
  (:require [clojure.string :as s]
            #?(:clj [clojure.spec.alpha :as spec])
            #?(:cljs [cljs.spec.alpha :as spec])))

(def dot-char "•")

(def ^:private bare-colon-re
  ;; Matches EITHER a full "string literal" OR a bare-colon token (the printed
  ;; form of the empty keyword `:`). Scanning left-to-right, a string literal is
  ;; consumed whole, so a `:` inside a string is never seen as a bare colon.
  ;; The lookahead requires the colon to be followed by a delimiter or end, so
  ;; `:foo`, `::x`, `:ns/foo`, and `#:ns{...}` are all left untouched.
  #"\"(?:[^\"\\]|\\.)*\"|:(?=[\s,{}\[\]()\";]|$)")

(defn sanitize-edn-colons
  "SELF-HEAL: return `edn-str` with every bare-colon token (unreadable empty
   keyword) replaced by a unique placeholder `:unnamed-N`, so an already-corrupt
   EDN blob (a saved character or localStorage plugins carrying a `:` key) can
   be read instead of crashing the load with \"A single colon is not a valid
   keyword.\" String-aware: colons inside \"...\" and all valid keywords are
   preserved. Returns {:text <sanitized> :count <replacements>}; :count 0 means
   the input was already clean (do not rewrite it)."
  [edn-str]
  (if (string? edn-str)
    (let [cnt (atom 0)
          text (s/replace edn-str bare-colon-re
                          (fn [m] (if (= \" (first m))
                                    m
                                    (str ":unnamed-" (swap! cnt inc)))))]
      {:text text :count @cnt})
    {:text edn-str :count 0}))

(defn- name-to-kw-aux [name ns]
  (when (string? name)
    (as-> name $
        (s/lower-case $)
        (s/replace $ #"'" "")
        (s/replace $ #"\W" "-")
        (s/replace $ #"\-+" "-")
        ;; Never emit the empty keyword `:` — a name that reduced to "" (blank,
        ;; or apostrophe-only like "'") would build (keyword "") = a bare `:`,
        ;; an unreadable EDN token that crashes read-string on load. Substitute a
        ;; stable placeholder keyed off the ORIGINAL name so distinct empties
        ;; ("" vs "'" vs "''") stay distinct. Only the empty case is touched, so
        ;; no existing (non-blank) key changes.
        (if (s/blank? $) (str "unnamed-" (hash name)) $)
        (keyword ns $))))

(def memoized-name-to-kw (memoize name-to-kw-aux))

(defn name-to-kw [name & [ns]]
  (memoized-name-to-kw name ns))

(defn kw-to-name [kw & [capitalize?]]
  (when (keyword? kw)
    (as-> kw $
      (name $)
      (s/split $ #"\-")
      (if capitalize? (map s/capitalize $) $)
      (s/join " " $))))

(defn map-by [by values]
  (zipmap (map by values) values))

(defn map-by-key [values]
  (map-by :key values))

(defn map-by-id [values]
  (map-by :db/id values))

;; dead — zero callers (only ref is in #_ discarded views.cljs block)
#_(defmacro ptime [message body]
  `(do (prn ~message)
       (time ~body)))

(defn bonus-str [val]
  (str (when (pos? val) "+") val))

(defn mod-str [val]
  (cond (pos? val) (str "+" val)
        (neg? val) (str "-" (int (Math/abs val)))
        :else (str "+" val)))

(defn map-vals [val-fn m]
  (reduce-kv
   (fn [m2 k v]
     (assoc m2 k (val-fn k v)))
   {}
   m))

(defn list-print [list & [preceding-last]]
  (let [preceding-last (or preceding-last "and")]
    (case (count list)
      0 ""
      1 (str (first list))
      2 (s/join (str " " preceding-last " ") list)
      (str
       (s/join ", " (butlast list))
       ", " preceding-last " "
       (last list)))))

(defn round-up [num]
  (int (Math/ceil (double num))))

(defn warn [message]
  #?(:cljs (js/console.warn message))
  #?(:clj (prn "WARNING: " message)))

(defn safe-name [kw]
  (if (keyword? kw)
    (name kw)
    (warn (str "non-keyword value passed to safe-name: " kw))))

(defn safe-capitalize [s]
  (when (string? s) (s/capitalize s)))

(defn safe-capitalize-kw [kw]
  (some-> kw
          name
          safe-capitalize))

(defn kw-base
  "Extract the base part of a keyword (before first dash).
   E.g., :artificer-kibbles-tasty -> \"artificer\""
  [kw]
  (when (keyword? kw)
    (first (s/split (name kw) #"-"))))

(defn traverse-nested
  "HOF for traversing nested option structures (vector/map/nil pattern).
   Calls (f item path) for each nested item, returns concatenated results."
  [f coll path]
  (mapcat
   (fn [[k v]]
     (cond
       (vector? v)
       (apply concat (map-indexed (fn [idx item] (f item (conj path k idx))) v))
       (map? v)
       (f v (conj path k))
       :else nil))
   coll))

(defn sentensize [desc]
  (when desc
    (str
     (s/upper-case (subs desc 0 1))
     (subs desc 1)
     (when (not (s/ends-with? desc "."))
       "."))))

(def add-keys-xform
  (map
   #(assoc % :key (name-to-kw (:name %)))))

(defn add-keys [vals]
  (into [] add-keys-xform vals))

(defn remove-first [f v]
  (concat
   (take-while (complement f) v)
   (rest (drop-while (complement f) v))))

(defn add-namespaces-to-keys [ns-str item]
  (into {}
        (map
         (fn [x]
           (let [[k v] x]
             [(if (simple-keyword? k)
                (keyword ns-str (name k))
                k)
              v]))
         item)))

(spec/fdef add-namespaces-to-keys
           :args (spec/cat :ns-str string? :item (spec/map-of keyword? any?))
           :ret (spec/map-of qualified-keyword? any?)
           :fn #(and (= (count (-> % :args :item))
                        (count (-> % :ret)))
                     (= (set (-> % :args :item keys))
                        (set (->> % :ret keys (map (fn [k] (keyword (name k)))))))))

(defn ordinal [i]
  (case i
    1 "1st"
    2 "2nd"
    3 "3rd"
    (str i "th")))

(defn starts-with-letter? [nm]
  (re-matches #"^[a-zA-Z].*" nm))

(defn keyword-starts-with-letter? [kw]
  (and (keyword? kw)
       (-> kw name starts-with-letter?)))

;; ── Number-word translation, for repairing keyword-trap names ────────────────
;; A homebrew NAME derives its KEY, and a key must start with a letter
;; (keyword-starts-with-letter?). Names that LEAD with a number ("9 Lives",
;; "2nd Wind") are the most common trap. Instead of discarding the name, we
;; translate the leading number to its word form so the user's intent survives:
;;   "9 Lives"  -> "Nine Lives"      "2nd Wind" -> "Second Wind"
;;   "20 Sided" -> "Twenty Sided"    "13th Warrior" -> "Thirteenth Warrior"
;; This is BOUNDED on purpose (see max-number-word): above the cap a leading
;; number reads as data — a year/stat/code ("2020 Vision") — not a word, so the
;; translator declines and the caller falls back (strip symbols, else placeholder).
;; Depth is cheap to extend; the cap is a quality knob, not an effort limit.

(def ^:const max-number-word
  "Inclusive cap for number->word name repair. 0..this translate to words; a
   larger leading number reads as data, not a name, so translation declines.
   999 covers every realistic name ('100 Hands', '300'); bump to 9999 for
   '1000 Cuts'. It's a constant precisely so moving the ceiling stays one edit."
  999)

(def ^:private cardinal-ones
  ["zero" "one" "two" "three" "four" "five" "six" "seven" "eight" "nine" "ten"
   "eleven" "twelve" "thirteen" "fourteen" "fifteen" "sixteen" "seventeen"
   "eighteen" "nineteen"])

(def ^:private cardinal-tens
  ["" "" "twenty" "thirty" "forty" "fifty" "sixty" "seventy" "eighty" "ninety"])

(defn cardinal->words
  "Cardinal words for 0 <= n <= 999 (121 -> \"one hundred twenty-one\"), else nil."
  [n]
  (cond
    (or (not (integer? n)) (neg? n) (> n 999)) nil
    (< n 20)  (nth cardinal-ones n)
    (< n 100) (let [t (nth cardinal-tens (quot n 10)) o (rem n 10)]
                (if (zero? o) t (str t "-" (nth cardinal-ones o))))
    :else     (let [h (nth cardinal-ones (quot n 100)) r (rem n 100)]
                (if (zero? r) (str h " hundred")
                    (str h " hundred " (cardinal->words r))))))

(def ^:private cardinal->ordinal-word
  ;; The irregular ordinal stems; every other word just takes a "th" suffix
  ;; (fourth, sixth, seventh, tenth, thirteenth, …).
  {"zero" "zeroth"  "one" "first"   "two" "second"  "three" "third"
   "five" "fifth"   "eight" "eighth" "nine" "ninth" "twelve" "twelfth"
   "twenty" "twentieth"  "thirty" "thirtieth"  "forty" "fortieth"
   "fifty" "fiftieth"    "sixty" "sixtieth"    "seventy" "seventieth"
   "eighty" "eightieth"  "ninety" "ninetieth"  "hundred" "hundredth"})

(defn- ordinalize-word [w]
  (or (cardinal->ordinal-word w) (str w "th")))

(defn ordinal->words
  "Ordinal words for 0 <= n <= 999 (21 -> \"twenty-first\", 13 -> \"thirteenth\",
   100 -> \"one hundredth\"), else nil. Only the FINAL atom is ordinalized."
  [n]
  (when-let [c (cardinal->words n)]
    (let [sp   (s/last-index-of c " ")
          head (if sp (subs c 0 (inc sp)) "")
          tail (if sp (subs c (inc sp)) c)
          hy   (s/last-index-of tail "-")]
      (if hy
        (str head (subs tail 0 (inc hy)) (ordinalize-word (subs tail (inc hy))))
        (str head (ordinalize-word tail))))))

(defn- parse-uint [digits]
  #?(:clj  (try (Long/parseLong digits) (catch Exception _ nil))
     :cljs (let [n (js/parseInt digits 10)] (when-not (js/isNaN n) n))))

(defn- title-number-phrase
  "Title-case a number phrase for a name lead: \"twenty-one\" -> \"Twenty-one\",
   \"one hundred\" -> \"One Hundred\" (capitalize the letter after start/space)."
  [phrase]
  (s/replace phrase #"(^|\s)([a-z])"
             (fn [[_ pre ch]] (str pre (s/upper-case ch)))))

(defn lead-number->words
  "If `name` starts with a number — cardinal (\"9 Lives\") or ordinal
   (\"2nd Wind\") — return it with that leading number replaced by its
   Title-Cased word form (\"Nine Lives\", \"Second Wind\"). Returns nil when the
   name doesn't start with a translatable, in-range (<= max-number-word) number,
   or when the digits are glued to a non-ordinal letter (dice/version tokens like
   \"3d6\", \"5e\" are deliberately left alone rather than mangled)."
  [name]
  (when (string? name)
    (let [t (s/triml name)]
      (when-let [[_ digits tail] (re-matches #"(\d+)([\s\S]*)" t)]
        (when (<= (count digits) 4)                 ; length guard before parse
          (when-let [n (parse-uint digits)]
            (when (<= n max-number-word)
              (let [ord (re-find #"(?i)^(st|nd|rd|th)($|\s[\s\S]*|[^a-zA-Z][\s\S]*)" tail)]
                (cond
                  ;; ordinal: "2nd Wind" -> "Second Wind"
                  ord
                  (when-let [w (ordinal->words n)]
                    (str (title-number-phrase w) (nth ord 2)))
                  ;; cardinal: only when the digits are a standalone token
                  ;; (next char is whitespace, end, or a non-alphanumeric)
                  (re-find #"^($|\s|[^a-zA-Z0-9])" tail)
                  (when-let [w (cardinal->words n)]
                    (str (title-number-phrase w) tail))
                  ;; else glued to a letter ("3d6", "5e") — not ours to touch
                  :else nil)))))))))

(defn repair-name-lead
  "Best-effort coerce `name` to a valid, letter-leading name — least-destructive
   first: (1) leading number -> word (preserves intent), else (2) strip leading
   non-letters. Returns the repaired name, or nil when nothing usable remains
   (all-symbol names, or an out-of-range number with no letters after it) — the
   caller then falls back to a placeholder like \"Unnamed <Type>\". Purely a
   SUGGESTION; the caller still checks the derived key for collisions."
  [name]
  (when (string? name)
    (let [t (s/trim name)]
      (cond
        (starts-with-letter? t) t
        (lead-number->words t)  (lead-number->words t)
        :else (let [stripped (s/trim (s/replace t #"^[^a-zA-Z]+" ""))]
                (when (starts-with-letter? stripped) stripped))))))

(defn toggle-flag
  "Flip a boolean flag, but leave a collection untouched instead of collapsing it.
   Use in place of bare `not` for builder toggles whose path could land on a MAP:
   `(not {…})` is `false`, which DESTROYS the map so every child read returns nil
   (the 'true/false/nil from clicking a lot' corruption)."
  [v]
  (if (coll? v) v (not v)))

(defn toggle-in
  "Toggle a boolean flag at path `ks` in `m` (like `update-in` with `not`), with
   two safeguards: the LEAF uses `toggle-flag` so it never collapses a map; a
   non-associative INTERMEDIATE (a stray `false` from the old collapse bug, or an
   absent slot) is healed to a fresh map instead of crashing on `(assoc false …)`,
   so a click on a previously-corrupted spot self-heals."
  [m ks]
  (let [[k & more] ks]
    (if (seq more)
      (let [child (get m k)
            child (if (associative? child) child {})]   ; heal a collapsed node
        (assoc m k (toggle-in child more)))
      (assoc m k (toggle-flag (get m k))))))

(defn remove-at-index [v index]
  (vec
   (keep-indexed
    (fn [i item]
      (when (not= i index)
        item))
    v)))

(def rounds-per-minute 10)
(def minutes-per-hour 60)
;; dead — redefined in views.cljs (also dead there), never referenced from common
#_(def hours-per-day 24)

(def rounds-per-hour (* minutes-per-hour rounds-per-minute))

;; dead — zero callers
#_(defn rounds-to-hours [rounds]
  (int (/ rounds rounds-per-hour)))

;; dead — zero callers
#_(defn rounds-to-minutes [rounds]
  (int (/ (rem rounds rounds-per-hour) rounds-per-minute)))

(def filter-true-xform
  (filter (fn [[k v]] v)))

(defn true-keys [m]
  (keys (sequence filter-true-xform m)))

(defn dissoc-in [m path]
  (update-in m
             (butlast path)
             (fn [x]
               (dissoc x (last path)))))

(defn print-bonus-map [m]
  (s/join ", "
          (map
           (fn [[k v]] (str (safe-capitalize-kw k) " " (bonus-str v)))
           m)))

;; Crash-safe case fold for sort/compare keys: coerces a nil/non-string to "" so
;; core s/lower-case can't crash on it. Never throws — it folds arbitrary keys
;; (e.g. :level), so a non-string isn't a bug here; that judgment is the caller's
;; (see feature-name, where the dev-throw lives).
(defn lower-case [x]
  (s/lower-case (str x)))

;; Case-insensitive `sort-by`, built on the safe fold above so a nil/non-string key
;; sorts as "" rather than throwing.
(defn aloof-sort-by [sorter coll]
  (sort-by (comp lower-case sorter) coll))

;; Display name with an obvious placeholder, not a blank: any unusable name ->
;; "[Unnamed feature]" (shown and sorted by), never a blank or a plausible-looking
;; coercion. Here a string IS expected, so a wrong-typed name is a real bug — dev
;; throws to surface it; prod also shows the placeholder rather than hiding it as
;; e.g. "42". (The generic lower-case fold can't tell, so it never throws.)
(defn feature-name [{:keys [name]}]
  (cond
    (and (string? name) (not (s/blank? name))) name
    (or (nil? name) (string? name)) "[Unnamed feature]"   ; nil or blank string
    :else #?(:cljs (if ^boolean goog/DEBUG
                     (throw (ex-info "feature :name is not a string" {:name name}))
                     "[Unnamed feature]")
             :clj "[Unnamed feature]")))

(defn ->kebab-case [s]
  (-> s
      ;; Insert hyphen before each capital letter, but not at the start.
      (s/replace #"([A-Z])" "-$1")
      .toLowerCase))
