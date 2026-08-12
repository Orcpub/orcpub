(ns orcpub.dnd.e5.db
  (:require [orcpub.route-map :as route-map]
            [orcpub.user-agent :as user-agent]
            [orcpub.common :as common]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.content-specs :as content-specs]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.backgrounds :as bg5e]
            [orcpub.dnd.e5.languages :as langs5e]
            [orcpub.dnd.e5.feats :as feats5e]
            [orcpub.dnd.e5.races :as race5e]
            [orcpub.dnd.e5.classes :as class5e]
            [orcpub.dnd.e5.magic-items :as mi5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.monsters :as monsters5e]
            [orcpub.dnd.e5.encounters :as encounters5e]
            [orcpub.dnd.e5.combat :as combat5e]
            [orcpub.dnd.e5.equipment :as equip5e]
            [orcpub.dnd.e5.selections :as selections5e]
            [re-frame.core :as re-frame]
            [orcpub.entity :as entity]
            [orcpub.entity.strict :as se]
            [cljs.spec.alpha :as spec]
            [cljs.reader :as reader]
            [bidi.bidi :as bidi]
            [cljs-http.client :as http]
            [orcpub.dnd.e5.orcbrew-validation :as orcbrew-val]))

;; =============================================================================
;; Version: 1.01 - Add conflict-resolution state for duplicate key handling
;; =============================================================================

(def local-storage-character-key "character")
(def local-storage-user-key "user")
(def local-storage-magic-item-key "magic-item")
(def local-storage-spell-key "spell")
(def local-storage-monster-key "monster")
(def local-storage-encounter-key "encounter")
(def local-storage-combat-key "combat")
(def local-storage-background-key "background")
(def local-storage-language-key "language")
(def local-storage-invocation-key "invocation")
(def local-storage-boon-key "boon")
(def local-storage-selection-key "selection")
(def local-storage-feat-key "feat")
(def local-storage-race-key "race")
(def local-storage-subrace-key "subrace")
(def local-storage-subclass-key "subclass")
(def local-storage-class-key "class")
(def local-storage-plugins-key "plugins")
;; Resilient-loader companion to `plugins`: sources that failed validation on load
;; are preserved for repair here instead of being silently discarded.
(def local-storage-plugins-rejected-key "plugins:rejected")

(def default-route route-map/dnd-e5-char-builder-route)

(defn parse-route []
  (let [route (when js/window.location
                (bidi/match-route route-map/routes js/window.location.pathname))]
    (or route
        default-route)))

(def default-character (char5e/set-class t5e/character :barbarian 0 (class5e/barbarian-option nil nil nil nil nil)))

(def default-spell {:level 0
                    :school "abjuration"
                    :spell-lists {:bard true
                                  :cleric true
                                  :druid true
                                  :paladin true
                                  :ranger true
                                  :sorcerer true
                                  :warlock true
                                  :wizard true}})

(def default-monster {:size :large
                      :type :aberration
                      :alignment "neutral"
                      :armor-class 10
                      :str 10
                      :dex 10
                      :con 10
                      :int 10
                      :wis 10
                      :cha 10})

(def default-encounter {:creatures []})

(def default-combat {:parties []
                     :encounters []
                     :characters []
                     :monsters []})

(def default-background {:traits []})

(def default-language {})

(def default-invocation {})

(def default-boon {})

(def default-selection {:options []})


(def default-feat {:ability-increases #{}
                   :prereqs #{}})

(def default-race {:size :medium
                   :speed 30
                   :languages #{}
                   :traits []})

(def default-subrace {:race :dwarf
                      :traits []})

(def default-subclass {:class :barbarian
                       :traits []
                       :level-modifiers []})

(def default-class {:hit-die 6
                    :ability-increase-levels [4 8 12 16 19]
                    :traits []
                    :level-modifiers []})

(def default-value
  {:builder {:character {:tab #{:build :options}}}
   :character default-character
   :template t5e/template
   :plugins {"Default Option Source" {}}
   :locked-components #{}
   :route (parse-route)
   :route-history (list default-route)
   :return-route default-route
   :registration-form {:send-updates? false}
   :device-type (user-agent/device-type)
   :import-log {:panel-shown? false
                :changes []
                :errors []
                :skipped-items []
                :import-name nil
                :timestamp nil}
   ;; Conflict resolution state for import key conflicts
   :conflict-resolution {:active? false
                         :import-name nil
                         :import-data nil         ; The raw parsed data to import
                         :conflicts []            ; List of conflicts to resolve
                         :decisions {}            ; User decisions: {conflict-id {:action :rename-import :new-key ...}}
                         :validation-result nil}  ; Original validation result
   ::spells5e/builder-item default-spell
   ::monsters5e/builder-item default-monster
   ::encounters5e/builder-item default-encounter
   ::combat5e/tracker-item default-combat
   ::bg5e/builder-item default-background
   ::langs5e/builder-item default-language
   ::class5e/invocation-builder-item default-invocation
   ::class5e/boon-builder-item default-boon
   ::selections5e/builder-item default-selection
   ::feats5e/builder-item default-feat
   ::race5e/builder-item default-race
   ::race5e/subrace-builder-item default-subrace
   ::class5e/builder-item default-class
   ::class5e/subclass-builder-item default-subclass
   ::char5e/newb-char-data {:answers {}
                            :tags #{}}})

(defn set-item
  "Write to localStorage. Returns true on success, false if the write failed
   (e.g. a QuotaExceededError when storage is full). Callers that persist user
   content should check this — a silent quota failure used to drop the just-saved
   data on the next refresh with no warning."
  [key value]
  (try
    (.setItem js/window.localStorage key value)
    true
    (catch js/Object e
      (prn "FAILED SETTING LOCALSTORAGE ITEM" key)
      false)))

(defn character->local-store [character]
  (when js/window.localStorage
    (set-item local-storage-character-key
              (str (assoc (char5e/to-strict character)
                          :changed
                          (:changed character))))))

(defn user->local-store [user-data]
  (when js/window.localStorage
    (set-item local-storage-user-key (str user-data))))

(defn magic-item->local-store [magic-item]
  (when js/window.localStorage
    (set-item local-storage-magic-item-key (str magic-item))))

(defn spell->local-store [spell]
  (when js/window.localStorage
    (set-item local-storage-spell-key (str spell))))

(defn monster->local-store [monster]
  (when js/window.localStorage
    (set-item local-storage-monster-key (str monster))))

(defn encounter->local-store [encounter]
  (when js/window.localStorage
    (set-item local-storage-encounter-key (str encounter))))

(defn combat->local-store [combat]
  (when js/window.localStorage
    (set-item local-storage-combat-key (str combat))))

(defn background->local-store [background]
  (when js/window.localStorage
    (set-item local-storage-background-key (str background))))

(defn language->local-store [language]
  (when js/window.localStorage
    (set-item local-storage-language-key (str language))))

(defn invocation->local-store [invocation]
  (when js/window.localStorage
    (set-item local-storage-invocation-key (str invocation))))

(defn boon->local-store [boon]
  (when js/window.localStorage
    (set-item local-storage-boon-key (str boon))))

(defn selection->local-store [selection]
  (when js/window.localStorage
    (set-item local-storage-selection-key (str selection))))

(defn feat->local-store [feat]
  (when js/window.localStorage
    (set-item local-storage-feat-key (str feat))))

(defn race->local-store [race]
  (when js/window.localStorage
    (set-item local-storage-race-key (str race))))

(defn subrace->local-store [subrace]
  (when js/window.localStorage
    (set-item local-storage-subrace-key (str subrace))))

(defn subclass->local-store [subclass]
  (when js/window.localStorage
    (set-item local-storage-subclass-key (str subclass))))

(defn class->local-store [class]
  (when js/window.localStorage
    (set-item local-storage-class-key (str class))))

(defn corrupt-slot-key
  "Companion slot that holds the raw, unparseable contents of `k` for recovery."
  [k]
  (str k ":corrupt"))

(defn plugins->local-store [plugins]
  (when js/window.localStorage
    (let [ok? (set-item local-storage-plugins-key (str plugins))]
      (when-not ok?
        ;; A quota-exceeded write would silently drop the just-saved homebrew on
        ;; the next refresh. Warn and offer a raw backup so in-memory content can
        ;; be rescued. (No reclaim-and-retry: the only reclaimable slots are the
        ;; `:corrupt` ones, which hold the ONLY copy of unreadable content.)
        (re-frame/dispatch [::e5/plugins-save-failed]))
      ok?)))

(def tab-path [:builder :character :tab])

(def ^:private preserve-on-unreadable-keys
  "Storage slots that must NEVER be destroyed on a parse failure — the homebrew
   library and its quarantine companion. A corrupt blob here (e.g. a quota-cut
   write) is moved to a '<key>:corrupt' slot and cleared from the active slot, so
   it survives for recovery instead of being deleted. Other slots (character,
   builder drafts) keep the old remove-on-unreadable behavior."
  #{local-storage-plugins-key
    local-storage-plugins-rejected-key})

(defn- handle-unreadable
  "Fallback for a parse failure that self-heal could not repair: preserve
   homebrew slots to their :corrupt companion (recoverable), remove others."
  [local-storage-key stored-str e]
  (if (contains? preserve-on-unreadable-keys local-storage-key)
    ;; Preserve unparseable homebrew: copy raw bytes to the :corrupt slot,
    ;; then clear the active slot so a poison value can't brick boot. Recoverable.
    (do
      (js/console.warn
       "UNREADABLE homebrew storage; preserved raw copy for recovery in"
       (corrupt-slot-key local-storage-key) "and cleared the active slot."
       local-storage-key)
      (set-item (corrupt-slot-key local-storage-key) stored-str)
      (.removeItem js/window.localStorage local-storage-key)
      nil)
    (do
      (prn "E" e)
      (js/console.warn "UNREADABLE ITEM FOUND, REMOVING.." local-storage-key stored-str)
      (.removeItem js/window.localStorage local-storage-key)
      nil)))

(defn get-local-storage-item [local-storage-key]
  (when-let [stored-str (when js/window.localStorage
                        (.getItem js/window.localStorage local-storage-key))]
    (try (reader/read-string stored-str)
         (catch js/Object e
           ;; SELF-HEAL first: the common corruption is a bare-colon empty
           ;; keyword (":") from a custom element named "" or "'". Repair it in
           ;; place, re-save, and load — instead of quarantining/deleting data.
           (let [{:keys [text count]} (common/sanitize-edn-colons stored-str)]
             (if (pos? count)
               (try
                 (let [healed (reader/read-string text)]
                   (js/console.warn "REPAIRED" count "invalid empty-keyword key(s) in"
                                    local-storage-key "- healed in place and re-saved.")
                   (set-item local-storage-key text)
                   healed)
                 ;; sanitize produced something still unreadable -> normal fallback
                 (catch js/Object _e2
                   (handle-unreadable local-storage-key stored-str e)))
               ;; nothing to heal (some other corruption) -> normal fallback
               (handle-unreadable local-storage-key stored-str e)))))))

(defn reg-local-store-cofx [key local-storage-key item-spec & [item-fn]]
  (re-frame/reg-cofx
   key
   (fn [cofx _]
     (assoc cofx
            key
            (when-let [stored-item (get-local-storage-item local-storage-key)]
              (if (spec/valid? item-spec stored-item)
                (if item-fn
                  (item-fn stored-item)
                  stored-item)
                (do
                  ;; Humanize the spec failure instead of pprinting raw problem
                  ;; forms (which dump cljs.core/* predicate forms to the console).
                  (js/console.warn
                   (str "Invalid stored item, ignoring: " local-storage-key "\n"
                        (orcbrew-val/format-validation-errors
                         (spec/explain-data item-spec stored-item))))
                  nil)))))))

(reg-local-store-cofx
 :local-store-character
 local-storage-character-key
 ::se/entity
 (fn [char]
   (assoc
    (char5e/from-strict char)
    :changed
    (:changed char))))

(spec/def ::username string?)
(spec/def ::email string?)
(spec/def ::token string?)
(spec/def ::theme string?)
(spec/def ::patron string?) ; patron
(spec/def ::patron-tier string?) ; patron-tier
(spec/def ::show-class-source-suffix boolean?)
(spec/def ::user-data (spec/keys :req-un [::username ::email]))
(spec/def ::user (spec/keys :opt-un [::user-data ::token ::theme ::patron ::patron-tier ::show-class-source-suffix]))

(reg-local-store-cofx
 :local-store-user
 local-storage-user-key
 ::user)

(reg-local-store-cofx
 :local-store-magic-item
 local-storage-magic-item-key
 ::mi5e/internal-magic-item)

;; Refresh safety: restore every homebrew builder's in-progress item on boot (the
;; persist side is already wired per-builder via ->local-store interceptors; this
;; table + one cofx drive the restore side from one place). Validated only as
;; `map?`, not the strict per-type spec — the point is to preserve incomplete drafts.
(def builder-wip-stores
  "localStorage key -> the app-db key that builder's in-progress item lives under."
  {local-storage-class-key      ::class5e/builder-item
   local-storage-subclass-key   ::class5e/subclass-builder-item
   local-storage-invocation-key ::class5e/invocation-builder-item
   local-storage-boon-key       ::class5e/boon-builder-item
   local-storage-race-key       ::race5e/builder-item
   local-storage-subrace-key    ::race5e/subrace-builder-item
   local-storage-spell-key      ::spells5e/builder-item
   local-storage-monster-key    ::monsters5e/builder-item
   local-storage-encounter-key  ::encounters5e/builder-item
   local-storage-background-key ::bg5e/builder-item
   local-storage-language-key   ::langs5e/builder-item
   local-storage-selection-key  ::selections5e/builder-item
   local-storage-feat-key       ::feats5e/builder-item})

(re-frame/reg-cofx
 :local-store-builder-items
 (fn [cofx _]
   (assoc cofx :local-store-builder-items
          (reduce-kv
           (fn [acc store-key item-key]
             (let [v (get-local-storage-item store-key)]
               (if (map? v) (assoc acc item-key v) acc)))
           {}
           builder-wip-stores))))

;; dead — duplicate of classes.cljc def, never referenced from .cljs code
#_(def musical-instrument-choice-cfg
  {:name "Musical Instrument"
   :options (zipmap (map :key equip5e/musical-instruments) (repeat 1))})

(defn get-rejected-plugins
  "Read the name-keyed quarantine map (`plugins:rejected`), or {} if absent or
   not a map. Canonical source for quarantined sources."
  []
  (let [r (get-local-storage-item local-storage-plugins-rejected-key)]
    (if (map? r) r {})))

(defn set-rejected-plugins
  "Persist the name-keyed quarantine map. Removes the key entirely when the map
   is empty, so a fully-repaired library leaves no stale quarantine entry."
  [rejected]
  (when js/window.localStorage
    (if (seq rejected)
      (set-item local-storage-plugins-rejected-key (str rejected))
      (.removeItem js/window.localStorage local-storage-plugins-rejected-key))))

;; Resilient plugins loader. The old all-or-nothing version returned nil — dropping
;; the ENTIRE library — if any single source failed the ::e5/plugins spec. Instead,
;; keep the valid sources and quarantine the invalid ones in `plugins:rejected`
;; (preserved for repair). Registered directly, not via reg-local-store-cofx,
;; because the salvage/quarantine behavior is plugins-specific.
(re-frame/reg-cofx
 ::e5/plugins
 (fn [cofx _]
   (assoc cofx
          ::e5/plugins
          (when-let [stored (get-local-storage-item local-storage-plugins-key)]
            (if (not (map? stored))
              ;; Parsed but not a map: preserve raw in the :corrupt slot — NOT
              ;; :rejected, a clean name-keyed map we must not clobber. Load nothing.
              (do
                (set-item (corrupt-slot-key local-storage-plugins-key) (str stored))
                (js/console.warn
                 (str "Stored plugins were not a map; preserved raw copy in '"
                      (corrupt-slot-key local-storage-plugins-key)
                      "'. Loaded no homebrew."))
                nil)

              ;; It's a map: salvage per source — keep the valid sources and
              ;; reconcile the name-keyed quarantine map (see reconcile-rejected).
              (let [{:keys [kept rejected]}
                    ;; PER-ENTRY salvage: keep each source's valid items, set aside
                    ;; only its broken ones — so one bad entry can't drop a whole
                    ;; source. The item floor comes from the shared content-specs
                    ;; registry (save & load agree), not inline, so it can't drift.
                    ;; `stored` normally holds only valid items, so `rejected` is
                    ;; usually empty here — it's the defensive net if the floor tightens.
                    (e5/salvage-library-items content-specs/valid-item-for-load? stored)
                    reconciled (e5/reconcile-rejected-items
                                (get-local-storage-item local-storage-plugins-rejected-key)
                                rejected
                                kept)]
                (if (seq reconciled)
                  (set-item local-storage-plugins-rejected-key (str reconciled))
                  ;; self-clearing: no set-aside entries left → drop the key
                  (when js/window.localStorage
                    (.removeItem js/window.localStorage local-storage-plugins-rejected-key)))
                (when (seq rejected)
                  (js/console.warn
                   (str "Set aside newly-invalid homebrew entries on load (kept the "
                        "rest of each source). Preserved for repair in '"
                        local-storage-plugins-rejected-key "': "
                        (pr-str (vec (keys rejected))))))
                kept))))))

;; Load the name-keyed quarantine map into app-db so the repair UI can
;; render reactively. Injected AFTER ::e5/plugins in :initialize-db, since that
;; cofx is what writes/reconciles plugins:rejected during boot.
(re-frame/reg-cofx
 ::e5/rejected-plugins
 (fn [cofx _]
   (assoc cofx ::e5/rejected-plugins (get-rejected-plugins))))

(reg-local-store-cofx
 ::combat5e/tracker-item
 local-storage-combat-key
 ::combat5e/combat)

