(ns orcpub.dnd.e5.events
  (:require [orcpub.entity :as entity]
            [orcpub.entity.strict :as se]
            [orcpub.template :as t]
            [orcpub.common :as common]
            [orcpub.dice :as dice]
            [orcpub.modifiers :as mod]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.content-specs :as content-specs]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.common :as common5e]
            [orcpub.dnd.e5.orcbrew-validation :as orcbrew-val]
            [orcpub.dnd.e5.share-bundle :as share-bundle]
            [orcpub.dnd.e5.share-url :as share-url]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.char-decision-tree :as char-dec5e]
            [orcpub.dnd.e5.backgrounds :as bg5e]
            [orcpub.dnd.e5.languages :as langs5e]
            [orcpub.dnd.e5.selections :as selections5e]
            [orcpub.dnd.e5.feats :as feats5e]
            [orcpub.dnd.e5.races :as race5e]
            [orcpub.dnd.e5.classes :as class5e]
            [orcpub.dnd.e5.units :as units5e]
            [orcpub.dnd.e5.party :as party5e]
            [orcpub.dnd.e5.folder :as folder5e]
            [orcpub.dnd.e5.character.random :as char-rand5e]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.monsters :as monsters]
            [orcpub.dnd.e5.encounters :as encounters]
            [orcpub.dnd.e5.combat :as combat]
            [orcpub.dnd.e5.weapons :as weapons]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.event-handlers :as event-handlers]
            [orcpub.dnd.e5.character.equipment :as char-equip5e]
            [orcpub.dnd.e5.content-reconciliation :as content-recon]
            [orcpub.dnd.e5.db :refer [default-value
                                      character->local-store
                                      user->local-store
                                      magic-item->local-store
                                      spell->local-store
                                      monster->local-store
                                      encounter->local-store
                                      combat->local-store
                                      background->local-store
                                      language->local-store
                                      invocation->local-store
                                      boon->local-store
                                      selection->local-store
                                      feat->local-store
                                      race->local-store
                                      subrace->local-store
                                      subclass->local-store
                                      class->local-store
                                      plugins->local-store
                                      get-rejected-plugins
                                      set-rejected-plugins
                                      default-character
                                      default-spell
                                      default-monster
                                      default-encounter
                                      default-combat
                                      default-background
                                      default-language
                                      default-invocation
                                      default-boon
                                      default-selection
                                      default-feat
                                      default-race
                                      default-subrace
                                      default-class
                                      default-subclass]]
            [orcpub.dnd.e5.autosave-fx :as autosave-fx]
            [orcpub.dnd.e5.event-utils :as event-utils]
            [orcpub.dnd.e5.compute :as compute]
            [re-frame.core :refer [reg-event-db reg-event-fx reg-fx inject-cofx path
                                   after dispatch ->interceptor]]
            [cljs.spec.alpha :as spec]
            [orcpub.dnd.e5.http-safe :as http]
            [cljs.core.async :refer [<! timeout]]
            [cljs-time.core :as time]
            [cljs.reader :as reader]
            [clojure.string :as s]
            [bidi.bidi :as bidi]
            [orcpub.route-map :as routes]
            [orcpub.errors :as errors]
            [orcpub.fork.integrations :as integrations]
            [orcpub.fork.branding :as branding]
            [clojure.set :as sets]
            [cljsjs.filesaverjs]
            [clojure.pprint :as pprint])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; =============================================================================
;; Version: 1.06 - Add export warning modal events, required field validation
;; =============================================================================

;; Forward declaration — defined below :update-value-field (line ~1226).
;; Used in :save-character to auto-generate names for unnamed characters.
(declare generate-random-name)

(defn check-and-throw
  "throw an exception if db doesn't match the spec"
  [a-spec db]
  (when-not (spec/valid? a-spec db)
    (throw (ex-info (str "spec check failed: " (spec/explain-str a-spec db)) {}))))

(def check-spec-interceptor (after (partial check-and-throw ::entity/raw-entity)))

(def ->local-store (after character->local-store))

(def db-char->local-store (after (fn [db] (character->local-store (:character db)))))

(def user->local-store-interceptor (after (fn [db] (user->local-store (:user-data db)))))

(def magic-item->local-store-interceptor (after magic-item->local-store))

(def spell->local-store-interceptor (after spell->local-store))

(def monster->local-store-interceptor (after monster->local-store))

(def encounter->local-store-interceptor (after encounter->local-store))

(def combat->local-store-interceptor (after combat->local-store))

(def background->local-store-interceptor (after background->local-store))

(def language->local-store-interceptor (after language->local-store))

(def invocation->local-store-interceptor (after invocation->local-store))

(def boon->local-store-interceptor (after boon->local-store))

(def selection->local-store-interceptor (after selection->local-store))

(def feat->local-store-interceptor (after feat->local-store))

(def race->local-store-interceptor (after race->local-store))

(def subrace->local-store-interceptor (after subrace->local-store))

(def subclass->local-store-interceptor (after subclass->local-store))

(def class->local-store-interceptor (after class->local-store))

(def plugins->local-store-interceptor (after plugins->local-store))

(def set-changed (->interceptor
                  :id :set-changed
                  :before (fn [context]
                            (assoc-in context [:coeffects :db :character :changed] true))))

(def character-interceptors [check-spec-interceptor
                             set-changed
                             (path :character)
                             ->local-store])


(def item-interceptors [(path ::mi/builder-item)
                        magic-item->local-store-interceptor])

(def spell-interceptors [(path ::spells/builder-item)
                         spell->local-store-interceptor])

(def monster-interceptors [(path ::monsters/builder-item)
                           monster->local-store-interceptor])

(def encounter-interceptors [(path ::encounters/builder-item)
                             encounter->local-store-interceptor])

(def combat-interceptors [(path ::combat/tracker-item)
                          combat->local-store-interceptor])

(def background-interceptors [(path ::bg5e/builder-item)
                              background->local-store-interceptor])

(def language-interceptors [(path ::langs5e/builder-item)
                            language->local-store-interceptor])

(def invocation-interceptors [(path ::class5e/invocation-builder-item)
                              invocation->local-store-interceptor])

(def boon-interceptors [(path ::class5e/boon-builder-item)
                        boon->local-store-interceptor])

(def selection-interceptors [(path ::selections5e/builder-item)
                             selection->local-store-interceptor])

(def feat-interceptors [(path ::feats5e/builder-item)
                        feat->local-store-interceptor])

(def race-interceptors [(path ::race5e/builder-item)
                        race->local-store-interceptor])

(def subrace-interceptors [(path ::race5e/subrace-builder-item)
                           subrace->local-store-interceptor])

(def class-interceptors [(path ::class5e/builder-item)
                         class->local-store-interceptor])

(def subclass-interceptors [(path ::class5e/subclass-builder-item)
                            subclass->local-store-interceptor])

(def plugins-interceptors [(path :plugins)
                           plugins->local-store-interceptor])


;; -- Event Handlers --------------------------------------------------

;; Delegated to event-utils to break circular dep with subs files.
(def backend-url event-utils/backend-url)

(reg-event-fx
 :initialize-db
 [(inject-cofx :local-store-character)
  (inject-cofx :local-store-user)
  (inject-cofx :local-store-magic-item)
  ;; Restore every homebrew builder's in-progress item (one cofx, driven by
  ;; db/builder-wip-stores) so WIP survives a refresh in ALL builders, not just class.
  (inject-cofx :local-store-builder-items)
  (inject-cofx ::e5/plugins)
  ;; AFTER ::e5/plugins — that cofx reconciles/writes plugins:rejected, and
  ;; this reads the result into app-db for the reactive repair panel.
  (inject-cofx ::e5/rejected-plugins)
  (inject-cofx ::combat/tracker-item)
  check-spec-interceptor]
 (fn [{:keys [db
              local-store-character
              local-store-user
              local-store-magic-item
              local-store-builder-items
              ::e5/plugins
              ::e5/rejected-plugins
              ::combat/tracker-item]} _]
   {:db (if (seq db)
          db
          (cond-> default-value
            plugins (assoc :plugins plugins)
            (seq rejected-plugins) (assoc :quarantined-plugins rejected-plugins)
            local-store-character (assoc :character local-store-character)
            local-store-user (update :user-data merge local-store-user)
            local-store-magic-item (assoc ::mi/builder-item local-store-magic-item)
            ;; Restore in-progress builder WIP (all builders) across refresh.
            (seq local-store-builder-items) (merge local-store-builder-items)
            tracker-item (assoc ::combat/tracker-item tracker-item)))}))

(defn reset-character [_ _]
  (char5e/set-class t5e/character :barbarian 0 (class5e/barbarian-option [] {} {} {} {})))

(reg-event-db
 :reset-character
 character-interceptors
 reset-character)

(reg-event-fx
 ::char5e/clone-character
 (fn [{:keys [db]} _]
   {:dispatch [:set-character (-> :character
                                  db
                                  char5e/to-strict
                                  entity/remove-ids
                                  char5e/from-strict
                                  (update-in
                                   [::entity/values ::char5e/character-name]
                                   (fn [nm]
                                     (str nm " (clone)"))))]}))

(defn random-sequential-selection [built-template character {:keys [::t/min ::t/options ::entity/path] :as selection}]
  (let [num (inc (rand-int (count options)))
        actual-path (entity/actual-path selection)]
    (entity/update-option
     built-template
     character
     actual-path
     (fn [_]
       (mapv
        (fn [{:keys [::t/key]}]
          {::entity/key key})
        (take num options))))))

(defn random-selection [built-template character {:keys [::t/key ::t/min ::t/options ::t/multiselect? ::entity/path] :as selection}]
  (let [built-char (entity/build character built-template)
        new-options (take (entity/count-remaining built-template character selection)
                          (shuffle (filter
                                    (fn [o]
                                      (and (entity/meets-prereqs? o built-char)
                                           (not (#{:none :custom} (::t/key o)))))
                                    options)))]
    (reduce
     (fn [new-character {:keys [::t/key]}]
       (let [new-option {::entity/key key}]
         (entity/update-option
          built-template
          new-character
          (conj (entity/actual-path selection) key)
          (fn [options] (if multiselect? (conj (or options []) new-option) new-option)))))
     character
     (if (and (= :class key) (empty? new-options))
       [{::t/key :fighter}]
       new-options))))

(defn random-hit-points-option [levels class-kw]
  {::entity/key :roll
   ::entity/value (dice/die-roll (-> levels class-kw :hit-die))})

(def selection-randomizers
  {:ability-scores (fn [s _]
                     (fn [_] {::entity/key :standard-roll
                              ::entity/value (char5e/standard-ability-rolls)}))
   :hit-points (fn [{[_ class-kw] ::entity/path} built-char]
                 (fn [_]
                   (random-hit-points-option (char5e/levels built-char) class-kw)))})

#_ ;; unreferenced — random-character loop hardcodes 10
  (def max-iterations 100)

(defn keep-options [built-template entity option-paths]
  (reduce
   (fn [new-entity option-path]
     (entity/update-option
      built-template
      new-entity
      option-path
      (fn [_] (entity/get-option built-template entity option-path))))
   {}
   option-paths))


(defn random-character [current-character built-template locked-components]
  (reduce
   (fn [character i]
     (if (< i 10)
       (let [built-char (entity/build character built-template)
             available-selections (entity/available-selections character built-char built-template)
             combined-selections (entity/combine-selections available-selections)
             pending-selections (filter
                                 (fn [{:keys [::entity/path ::t/ref] :as selection}]
                                   (let [remaining (entity/count-remaining built-template character selection)]
                                     (and (pos? remaining)
                                          (not (locked-components path)))))
                                 combined-selections)]
         (if (empty? pending-selections)
           (reduced character)
           (reduce
            (fn [new-character {:keys [::t/key ::t/sequential?] :as selection}]
              (let [selection-randomizer (selection-randomizers key)]
                (if selection-randomizer
                  (let [random-value (selection-randomizer selection)]
                    (entity/update-option
                     built-template
                     new-character
                     (entity/actual-path selection)
                     (selection-randomizer selection built-char)))
                  (if sequential?
                    (random-sequential-selection built-template new-character selection)
                    (random-selection built-template new-character selection)))))
            character
            pending-selections)))
       (reduced character)))
   (let [starting-character (keep-options built-template current-character (conj (vec locked-components) [:optional-content]))]
     starting-character)
   (range)))

(reg-event-fx
 :set-random-character
 (fn [{:keys [db]} [_ character built-template locked-components]]
   {:dispatch [:set-character (random-character character built-template locked-components)]}))

(reg-event-fx
 :random-character
 (fn [_ [_ character built-template locked-components]]
   {:dispatch [:set-random-character character built-template locked-components]}))

#_ ;; unreferenced — character path is constructed inline
  (def dnd-5e-characters-path [:dnd :e5 :characters])

(reg-event-fx
 :character-save-success
 (fn [{:keys [db]} [_ response]]
   (let [strict-character (:body response)
         character (char5e/from-strict strict-character)
         id (:db/id character)]
     {:dispatch-n [[:show-message "Your character has been saved."]
                   [:set-character character]
                   [::char5e/set-character id character]]})))

(defn descriptive-character-label
  "Build a descriptive label like 'High Elf Ranger 3' from character properties.
   Used as the summary name when the user hasn't set a character name."
  [race subrace classes levels]
  (let [race-part (or subrace race)
        class-parts (when (seq classes)
                      (s/join "/"
                              (map (fn [cls]
                                     (let [{:keys [class-name class-level]} (levels cls)]
                                       (str class-name " " class-level)))
                                   classes)))]
    (cond
      (and race-part class-parts) (str race-part " " class-parts)
      race-part race-part
      class-parts class-parts
      :else "Adventurer")))

(defn make-summary [built-char]
  (let [classes (char5e/classes built-char)
        levels (char5e/levels built-char)
        race (char5e/race built-char)
        subrace (char5e/subrace built-char)
        character-name (char5e/character-name built-char)
        image-url (char5e/image-url built-char)
        faction-image-url (char5e/faction-image-url built-char)
        age (char5e/age built-char)
        sex (char5e/sex built-char)
        height (char5e/height built-char)
        weight (char5e/weight built-char)
        hair (char5e/hair built-char)
        eyes (char5e/eyes built-char)
        skin (char5e/skin built-char)
        ;; When user hasn't set a name, auto-generate a descriptive label
        ;; for the summary (what lists/parties display).
        display-name (if (s/blank? character-name)
                       (descriptive-character-label race subrace classes levels)
                       character-name)]
    (cond-> {::char5e/character-name display-name}
      image-url (assoc ::char5e/image-url image-url)
      faction-image-url (assoc ::char5e/faction-image-url faction-image-url)
      race (assoc ::char5e/race-name race)
      subrace (assoc ::char5e/subrace-name subrace)
      age (assoc ::char5e/age age)
      sex (assoc ::char5e/sex sex)
      height (assoc ::char5e/height height)
      weight (assoc ::char5e/weight weight)
      hair (assoc ::char5e/hair hair)
      eyes (assoc ::char5e/eyes eyes)
      skin (assoc ::char5e/skin skin)
      ;alignment (assoc ::char5e/alignment alignment) ;This is not available? 
      ;background (assoc ::char5e/background background) ;This is not available? 
      (seq classes) (assoc ::char5e/classes (map
                                             (fn [cls-nm]
                                               (let [{:keys [class-name subclass-name class-level]}
                                                     (levels cls-nm)]
                                                 (cond-> {}
                                                   class-name (assoc ::char5e/class-name class-name)
                                                   subclass-name (assoc ::char5e/subclass-name subclass-name)
                                                   class-level (assoc ::char5e/level class-level))))
                                             classes)))))

(def authorization-headers event-utils/auth-headers)
(def url-for-route event-utils/url-for-route)

;; Autosave handler — dispatched from autosave_fx.cljs throttle timer.
;; Posts character + summary to server.
(reg-event-fx
 ::char5e/save-character
 (fn [{:keys [db]} [_ id]]
   (let [character (get-in db [::char5e/character-map (js/parseInt id)] {})
         ;; Template is cached in app-db by autosave_fx's track! watcher.
         ;; Since built-template is a no-op (plugin merging commented out),
         ;; we use the cached template directly with entity/build.
         cached-template (get db ::autosave-fx/cached-template)]
     ;; Skip when the template isn't ready — nil OR empty {}. An empty template
     ;; crashes entity/build (null fn `.call`); `seq` covers both cases.
     ;; (Guarded by events-test save-character-rejects-missing-abilities.)
     (if-not (seq cached-template)
       {} ;; template not cached yet — skip this cycle, next autosave will retry
       (let [{:keys [:db/id] :as strict} (char5e/to-strict character)
             built-character (entity/build character cached-template)
             summary (make-summary built-character)]
         (if (every?
              (fn [ability-kw]
                (nat-int? (get-in built-character [:base-abilities ability-kw])))
              char5e/ability-keys)
           {:dispatch [:set-loading true]
            :http {:method :post
                   :headers (authorization-headers db)
                   :url (url-for-route routes/dnd-e5-char-list-route)
                   :transit-params (assoc strict :orcpub.entity.strict/summary summary)
                   :on-success [:character-save-success]}}
           {:dispatch [:show-error-message "You must provide values for all ability scores"]}))))))

;; Manual save — dispatched from character builder UI with built-char in scope.
;; If the user hasn't set a name, generates a random one and persists it.
(reg-event-fx
 :save-character
 (fn [{:keys [db]} [_ built-character]]
   (let [character-name (char5e/character-name built-character)
         needs-name? (s/blank? character-name)
         ;; Generate a random name for unnamed characters on manual save
         rand-name (when needs-name? (generate-random-name built-character))
         ;; Update entity in db so the name persists across future edits
         db' (if needs-name?
               (assoc-in db [:character ::entity/values ::char5e/character-name] rand-name)
               db)
         {:keys [:db/id] :as strict} (char5e/to-strict (:character db'))
         summary (cond-> (make-summary built-character)
                   ;; Override summary name with the generated name
                   ;; (make-summary produced a descriptive label since entity was blank)
                   needs-name? (assoc ::char5e/character-name rand-name))]
     (if (every?
          (fn [ability-kw]
            (nat-int? (get-in built-character [:base-abilities ability-kw])))
          char5e/ability-keys)
       {:db db'
        :dispatch [:set-loading true]
        :http {:method :post
               :headers (authorization-headers db')
               :url (url-for-route routes/dnd-e5-char-list-route)
               :transit-params (assoc strict :orcpub.entity.strict/summary summary)
               :on-success [:character-save-success]}}
       {:dispatch [:show-error-message "You must provide values for all ability scores"]}))))

(reg-event-fx
 :item-save-success
 (fn [{:keys [db]} [_ response]]
   (let [strict-item (:body response)
         item (mi/to-internal-item strict-item)
         item-id (:db/id strict-item)
         existing-items (::mi/custom-items db)
         updated-items (if (some #(= item-id (:db/id %)) existing-items)
                         (mapv #(if (= item-id (:db/id %)) strict-item %) existing-items)
                         (conj (vec existing-items) strict-item))]
     {:db (assoc db ::mi/custom-items updated-items)
      :dispatch-n [[:show-message "Your item has been saved."]
                   [::mi/set-item item]]})))

(reg-event-fx
 ::mi/save-item
 (fn [{:keys [db]} _]
   (let [item (::mi/builder-item db)
         ;; The type dropdown shows its first option ("Wondrous Item") even when
         ;; the item has no type set (a controlled <select> whose value doesn't
         ;; match any option), so what the user sees IS a default. ::type is
         ;; optional in the spec, so a never-set type would otherwise save blank
         ;; and render as e.g. ", very rare". Persist the shown default instead.
         t (::mi/type item)
         item (cond-> item
                (not (and (keyword? t) (not (s/blank? (name t)))))
                (assoc ::mi/type :wondrous-item))
         strict-item (mi/from-internal-item item)]
     {:dispatch [:set-loading true]
      :http {:method :post
             :headers (authorization-headers db)
             :url (url-for-route routes/dnd-e5-items-route)
             :transit-params strict-item
             :on-success [:item-save-success]}})))

(def ^:private homebrew-field-labels
  "User-facing labels for the spec keys that homebrew builders can leave unfilled.
   :key is derived from :name, so report it as Name."
  {:name "Name"
   :key "Name"
   :option-pack "Option Source Name"
   :level "Level"
   :school "School"
   :spell-lists "Class Spell Lists"
   :hit-points "Hit Points"
   :die "Hit Die"
   :die-count "Hit Dice Count"})

(defn- field-label [field-key]
  (or (homebrew-field-labels field-key)
      (s/capitalize (s/replace (name field-key) #"-" " "))))

(def ^:private builder-invalid-reason-rules
  "Why a present-but-rejected value fails. Matched against the printed predicate
   form so it is robust to how the form is wrapped (and to advanced-compilation
   shapes). Lets the banner explain a value that IS filled but isn't acceptable
   (e.g. a name that starts with a digit) instead of telling the user to 'fill
   in' a field they already filled."
  [[#"starts-with-letter" "must start with a letter"]])

(defn- builder-invalid-reason [pred]
  (let [s (pr-str pred)]
    (or (some (fn [[re msg]] (when (re-find re s) msg)) builder-invalid-reason-rules)
        "is not valid")))

(defn- singularize
  "Crude singular for a humanized collection name: \"Options\" -> \"Option\"."
  [s]
  (if (s/ends-with? s "s") (subs s 0 (dec (count s))) s))

(defn- problem-location
  "Turn a spec problem's `:in` path into a 1-based human location like \"Option 2\"
   when it lives inside an indexed collection (e.g. a selection's :options), so the
   banner can name WHICH nested element failed. nil for a top-level field."
  [in]
  (->> (partition 2 1 in)
       (keep (fn [[a b]]
               (when (and (keyword? a) (int? b))
                 (str (singularize (field-label a)) " " (inc b)))))
       (s/join " ")
       (#(when (seq %) %))))

(defn spec-field-problems
  "Classify each failing required field from a spec explanation against the
   original (pre-fill) item. Returns one entry per field:
     {:field k :status :missing}                       — absent or blank
     {:field k :status :invalid :reason \"...\"}         — present but rejected
   A missing :req-un key is a nested `(fn [%] (contains? % :k))` (found via
   tree-seq); an invalid value is reported with the field at the end of :in.
   :key maps to :name, since :key is derived from the name.

   A problem inside an indexed collection also carries :location (\"Option 2\") so
   the message names the nested element instead of a bare \"Name\"."
  [explanation item]
  (when-let [problems (::spec/problems explanation)]
    (->> problems
         (keep (fn [{:keys [pred in]}]
                 (let [missing-key (orcbrew-val/missing-required-key pred)
                       field (let [f (or missing-key (last (filter keyword? in)))]
                               (when f (if (= f :key) :name f)))
                       location (problem-location in)]
                   (when field
                     ;; resolve the actual offending value via :in so a nested
                     ;; option's blank-check looks at the option, not the parent.
                     (let [container (if missing-key
                                       (get-in item (vec in))
                                       (get-in item (vec (butlast in))))
                           v (if (and (not missing-key) (map? container))
                               (get container field)
                               (get item field))
                           blank? (or (nil? v)
                                      (and (string? v) (s/blank? v))
                                      ;; a checkbox-group map with nothing checked
                                      ;; (e.g. spell-lists) reads as "not chosen"
                                      (and (map? v) (seq v)
                                           (every? boolean? (vals v))
                                           (not (some true? (vals v)))))]
                       (cond-> (if (or missing-key blank?)
                                 {:field field :status :missing}
                                 {:field field :status :invalid
                                  :reason (builder-invalid-reason pred)})
                         location (assoc :location location)))))))
         ;; one entry per (location, field); prefer an :invalid report over a
         ;; :missing one for the same target.
         (reduce (fn [acc {:keys [field location status] :as p}]
                   (let [k [location field]]
                     (if (or (not (contains? acc k)) (= status :invalid))
                       (assoc acc k p)
                       acc)))
                 {})
         vals
         vec)))

(defn- and-join
  "Interpose hiccup items with ', ' / ' and ' so a list reads naturally and two
   things are clearly two things."
  [items]
  (case (count items)
    0 []
    1 (vec items)
    2 [(nth items 0) " and " (nth items 1)]
    (vec (concat (interpose ", " (butlast items)) [", and " (last items)]))))

(defn builder-error-hiccup
  "A clear, multi-line save-validation message: the empty fields on one line
   (bold, 'and'-joined) and each invalid field with its reason on its own line,
   so even a hurried reader sees the distinct problems. When `save-anyway-event`
   is given, also offers a remediating escape hatch so imperfect work isn't trapped."
  [type-name problems & [save-anyway-event]]
  (let [located? :location
        ;; bold field label, prefixed with its nested location when known
        ;; ("Option 2 Name") so the reader knows which element to fix.
        labelled (fn [{:keys [field location]}]
                   (let [lbl (field-label field)]
                     [:span.f-w-b (if location (str location " " lbl) lbl)]))
        ;; top-level missing fields batch onto one "Please fill in ..." line;
        ;; located ones each get their own line so the location is unambiguous.
        missing      (filter #(= :missing (:status %)) problems)
        flat-missing (remove located? missing)
        located-missing (filter located? missing)
        invalid      (filter #(= :invalid (:status %)) problems)
        missing-line (when (seq flat-missing)
                       (into [:div.m-t-5 "Please fill in "]
                             (conj (and-join (mapv labelled flat-missing)) ".")))
        located-missing-lines (for [p located-missing]
                                [:div.m-t-5 "Please fill in " (labelled p) "."])
        invalid-lines (for [p invalid]
                        [:div.m-t-5 (labelled p) " " (:reason p) "."])]
    (into [:div [:span.f-w-b (str type-name ":")]]
          (cond-> []
            missing-line (conj missing-line)
            true (into located-missing-lines)
            true (into invalid-lines)
            save-anyway-event
            (conj [:div.m-t-10
                   [:span.pointer.underline.f-w-b
                    {:on-click #(dispatch [save-anyway-event])}
                    "Save anyway with placeholders"]])))))

(def ^:private builder-error-ttl
  "How long the homebrew save-validation banner stays up (ms). Long enough to
   read and act on, since the user needs to go fix the flagged fields."
  45000)

(reg-event-db
 :set-builder-field-errors
 ;; Map of required field key -> :missing|:invalid for the last save attempt;
 ;; builder fields read this to flag themselves (amber for missing, red for
 ;; invalid). Cleared ({}) on a successful save.
 (fn [db [_ field->status]]
   (assoc db :builder-field-errors (or field->status {}))))

(reg-event-db
 :clear-builder-field-error
 ;; Editing a flagged field removes its cue immediately, for missing and invalid
 ;; alike (we can't re-run the spec predicate from the field component).
 (fn [db [_ field]]
   (update db :builder-field-errors dissoc field)))

(defn builder-field-error-fx
  "Effects for a failed homebrew save: flag the offending fields and show a
   targeted, long-lived banner. Falls back to the static message when no
   specific field can be identified."
  [type-name explanation item fallback-message & [save-anyway-event]]
  (let [problems (spec-field-problems explanation item)]
    (if (seq problems)
      {:dispatch-n [[:set-builder-field-errors (into {} (map (juxt :field :status) problems))]
                    [:show-error-message
                     (builder-error-hiccup type-name problems save-anyway-event)
                     builder-error-ttl]]}
      {:dispatch-n [[:set-builder-field-errors {}]
                    [:show-error-message fallback-message builder-error-ttl]]})))

(defn reg-save-homebrew [type-name
                         event-key
                         item-key
                         plugin-key
                         error-message]
  (let [;; Save spec is derived from the content type via the shared registry, not
        ;; passed per-call, so save and load can't name different specs and drift.
        spec-key (content-specs/save-spec-for plugin-key)
        ;; Companion "save anyway" event, offered from the failure banner.
        anyway-event-key (keyword (namespace event-key)
                                  (str (name event-key) "-anyway"))]
    (reg-event-fx
     event-key
     (fn [{:keys [db]} _]
       (let [{:keys [name option-pack] :as item} (item-key db)
             key (common/name-to-kw name)
             ;; Validate the user's ACTUAL input (normalized), NOT a placeholder-
             ;; filled copy: a blank or invalid required field must block and prompt,
             ;; never silently save under a placeholder. Placeholder-filling +
             ;; name-sanitizing is the explicit "save anyway" path only.
             normalized-item (orcbrew-val/normalize-text-in-data item)
             item-with-key (assoc normalized-item :key key)
             plugins (:plugins db)
             explanation (spec/explain-data spec-key item-with-key)]
         (if (nil? explanation)
           (let [new-plugins (assoc-in plugins
                                       [option-pack plugin-key key]
                                       item-with-key)]
             {:dispatch-n [[::e5/set-plugins new-plugins]
                           [:set-builder-field-errors {}]
                           [:show-warning-message
                            [:div [:span.f-w-b.f-s-18.red "IMPORTANT!: "]
                             [:span.text-shadow
                              (str type-name " saved to your browser which could be lost if you clear your browser history or your browser storage fill up, you MUST export and save the content source by clicking ")]
                             [:span.pointer.underline.black
                              {:on-click #(dispatch [::e5/export-plugin option-pack (new-plugins option-pack)])}
                              "here"]]
                            60000]]})
           (builder-field-error-fx type-name explanation item error-message anyway-event-key)))))

    ;; Save-anyway: placeholder-fill the blocking fields (option source, name,
    ;; key) and land the flagged item in My Content. Reuses fill-all-missing-fields;
    ;; adds only a placeholder option source.
    (reg-event-fx
     anyway-event-key
     (fn [{:keys [db]} _]
       (let [{:keys [name option-pack] :as item} (item-key db)
             normalized-item (orcbrew-val/normalize-text-in-data item)
             {filled-item :item} (orcbrew-val/fill-all-missing-fields normalized-item plugin-key)
             ;; Coerce invalid/blank names (top-level + nested) to valid placeholders
             ;; and re-derive the key, so an invalid name like "1@-asdml;" can NEVER
             ;; be persisted with a broken key. This is the sanitized output the
             ;; "save anyway with placeholders" button is supposed to produce.
             sanitized (orcbrew-val/sanitize-item-names filled-item type-name)
             src (if (s/blank? option-pack) "Unsorted Homebrew" option-pack)
             item-with-key (assoc sanitized :option-pack src)
             new-plugins (assoc-in (:plugins db) [src plugin-key (:key item-with-key)] item-with-key)]
         {:dispatch-n [[::e5/set-plugins new-plugins]
                       [:set-builder-field-errors {}]
                       [:show-warning-message
                        (str type-name " saved to My Content under \"" src
                             "\" with placeholders for missing fields. Review it "
                             "and re-export before sharing.")]]})))))

(reg-save-homebrew
 "Spell"
 ::spells/save-spell
 ::spells/builder-item
 ::e5/spells
 "You must specify 'Name', 'Option Source Name', and at select at least one class in 'Class Spell Lists'")

(reg-save-homebrew
 "Monster"
 ::monsters/save-monster
 ::monsters/builder-item
 ::e5/monsters
 "You must specify 'Name', 'Option Source Name', 'Hit Points Die Count', and 'Hit Points Die'")

(reg-save-homebrew
 "Encounter"
 ::encounters/save-encounter
 ::encounters/builder-item
 ::e5/encounters
 "You must specify 'Name', 'Option Source Name'")

(reg-save-homebrew
 "Background"
 ::bg5e/save-background
 ::bg5e/builder-item
 ::e5/backgrounds
 "You must specify 'Name', 'Option Source Name'")

(reg-save-homebrew
 "Language"
 ::langs5e/save-language
 ::langs5e/builder-item
 ::e5/languages
 "You must specify 'Name', 'Option Source Name'")

(reg-save-homebrew
 "Invocation"
 ::class5e/save-invocation
 ::class5e/invocation-builder-item
 ::e5/invocations
 "You must specify 'Name', 'Option Source Name'")

(reg-save-homebrew
 "Boon"
 ::class5e/save-boon
 ::class5e/boon-builder-item
 ::e5/boons
 "You must specify 'Name', 'Option Source Name'")

;; Selection save handler — standalone instead of reg-save-homebrew to add
;; duplicate option name validation. Mirrors reg-save-homebrew logic plus
;; checks for empty names and duplicate option names within :options.
(reg-event-fx
 ::selections5e/save-selection
 (fn [{:keys [db]} _]
   (let [{:keys [name option-pack] :as item} (::selections5e/builder-item db)
         key (common/name-to-kw name)
         normalized-item (orcbrew-val/normalize-text-in-data item)
         {filled-item :item} (orcbrew-val/fill-all-missing-fields normalized-item ::e5/selections)
         item-with-key (assoc filled-item :key key)
         plugins (:plugins db)
         explanation (spec/explain-data (content-specs/save-spec-for ::e5/selections) item-with-key)
         ;; Check for empty option names
         option-names (map :name (:options item))
         empty-names? (some s/blank? option-names)
         ;; Check for duplicate option names (case-insensitive via key derivation)
         option-keys (map #(when-not (s/blank? %) (common/name-to-kw %))
                          option-names)
         key-freqs (frequencies (remove nil? option-keys))
         dupe-keys (set (map first (filter #(> (val %) 1) key-freqs)))
         dupe-names (when (seq dupe-keys)
                      (->> option-names
                           (filter #(and (not (s/blank? %))
                                         (contains? dupe-keys (common/name-to-kw %))))
                           distinct
                           sort))]
     (cond
       ;; Reject empty option names
       empty-names?
       {:dispatch [:show-error-message
                   "Cannot save: all options must have names"
                   builder-error-ttl]}
       ;; Reject duplicate option names
       (seq dupe-names)
       {:dispatch [:show-error-message
                   (str "Cannot save: duplicate option names: "
                        (s/join ", " dupe-names)
                        ". Each option must have a unique name.")
                   builder-error-ttl]}
       ;; Spec validation — offer "Save anyway" (empty/duplicate option names
       ;; were already ruled out above, so the only thing left to remediate is the
       ;; missing name/option-source that fill-all-missing-fields can placeholder).
       (some? explanation)
       (builder-field-error-fx "Selection" explanation item
                               "You must specify 'Name', 'Option Source Name'"
                               ::selections5e/save-selection-anyway)
       ;; All good — save
       :else
       (let [new-plugins (assoc-in plugins
                                   [option-pack ::e5/selections key]
                                   item-with-key)]
         {:dispatch-n [[::e5/set-plugins new-plugins]
                       [:set-builder-field-errors {}]
                       [:show-warning-message
                        [:div [:span.f-w-b.f-s-18.red "IMPORTANT!: "]
                         [:span.text-shadow
                          "Selection saved to your browser which could be lost if you clear your browser history or your browser storage fill up, you MUST export and save the content source by clicking "]
                         [:span.pointer.underline.black
                          {:on-click #(dispatch [::e5/export-plugin option-pack (new-plugins option-pack)])}
                          "here"]]
                        60000]]})))))

;; Selection is a standalone handler (for its option-name checks), so it doesn't
;; get reg-save-homebrew's auto-generated -anyway event and needs its own:
;; placeholder-fill the missing fields and land the flagged selection in My Content.
(reg-event-fx
 ::selections5e/save-selection-anyway
 (fn [{:keys [db]} _]
   (let [{:keys [option-pack] :as item} (::selections5e/builder-item db)
         normalized-item (orcbrew-val/normalize-text-in-data item)
         {filled-item :item} (orcbrew-val/fill-all-missing-fields normalized-item ::e5/selections)
         src (if (s/blank? option-pack) "Unsorted Homebrew" option-pack)
         key (common/name-to-kw (:name filled-item))
         item-with-key (assoc filled-item :key key :option-pack src)
         new-plugins (assoc-in (:plugins db) [src ::e5/selections key] item-with-key)]
     {:dispatch-n [[::e5/set-plugins new-plugins]
                   [:set-builder-field-errors {}]
                   [:show-warning-message
                    (str "Selection saved to My Content under \"" src
                         "\" with placeholders for missing fields. Review it "
                         "and re-export before sharing.")]]})))

(reg-save-homebrew
 "Feat"
 ::feats5e/save-feat
 ::feats5e/builder-item
 ::e5/feats
 "You must specify 'Name', 'Option Source Name'")

(reg-save-homebrew
 "Race"
 ::race5e/save-race
 ::race5e/builder-item
 ::e5/races
 "You must specify 'Name', 'Option Source Name'")

(reg-save-homebrew
 "Subrace"
 ::race5e/save-subrace
 ::race5e/subrace-builder-item
 ::e5/subraces
 "You must specify 'Name', 'Option Source Name', and 'Race'")

(reg-save-homebrew
 "Subclass"
 ::class5e/save-subclass
 ::class5e/subclass-builder-item
 ::e5/subclasses
 "You must specify 'Name', 'Option Source Name', and 'Class'")

(reg-save-homebrew
 "Class"
 ::class5e/save-class
 ::class5e/builder-item
 ::e5/classes
 "You must specify 'Name', 'Option Source Name'")

(defn reg-delete-homebrew [event-key plugin-key]
  (reg-event-fx
   event-key
   (fn [{:keys [db]} [_ {:keys [key option-pack]}]]
     {:dispatch [::e5/set-plugins (update-in (:plugins db) [option-pack plugin-key] dissoc key)]})))

(reg-delete-homebrew
 ::spells/delete-spell
 ::e5/spells)

(reg-delete-homebrew
 ::monsters/delete-monster
 ::e5/monsters)

(reg-delete-homebrew
 ::encounters/delete-encounter
 ::e5/encounters)

(reg-delete-homebrew
 ::bg5e/delete-background
 ::e5/backgrounds)

(reg-delete-homebrew
 ::langs5e/delete-language
 ::e5/languages)

(reg-delete-homebrew
 ::class5e/delete-invocation
 ::e5/invocations)

(reg-delete-homebrew
 ::class5e/delete-boon
 ::e5/boons)

(reg-delete-homebrew
 ::selections5e/delete-selection
 ::e5/selections)

(reg-delete-homebrew
 ::feats5e/delete-feat
 ::e5/feats)

(reg-delete-homebrew
 ::race5e/delete-race
 ::e5/races)

(reg-delete-homebrew
 ::race5e/delete-subrace
 ::e5/subraces)

(reg-delete-homebrew
 ::class5e/delete-subclass
 ::e5/subclasses)

(reg-delete-homebrew
 ::class5e/delete-class
 ::e5/classes)

(reg-event-fx
 ::party5e/make-party-success
 (fn []
   {:dispatch [:show-message [:div
                              "Your party has been created. View it on the "
                              [:span.underline.pointer.orange
                               {:on-click #(dispatch [:route routes/dnd-e5-char-parties-page-route])}
                               "Parties Page"]]]}))

(reg-event-fx
 ::party5e/make-party
 (fn [{:keys [db]} [_ character-ids]]
   {:dispatch [:set-loading true]
    :http {:method :post
           :headers (authorization-headers db)
           :url (url-for-route routes/dnd-e5-char-parties-route)
           :transit-params {::party5e/name "A New Party"
                            ::party5e/character-ids character-ids}
           :on-success [::party5e/make-party-success]}}))

(reg-event-fx
 ::party5e/make-empty-party-success
 (fn [{:keys [db]} [_ response]]
   (let [new-party (:body response)
         parties (conj (vec (::char5e/parties db)) new-party)]
     {:db (assoc db
                 ::char5e/parties parties
                 ::char5e/parties-map (common/map-by-id parties))})))

(reg-event-fx
 ::party5e/make-empty-party
 (fn [{:keys [db]} [_]]
   {:dispatch [:set-loading true]
    :http {:method :post
           :headers (authorization-headers db)
           :url (url-for-route routes/dnd-e5-char-parties-route)
           :transit-params {::party5e/name "A New Party"}
           :on-success [::party5e/make-empty-party-success]}}))

(reg-event-fx
 ::party5e/rename-party
 (fn [{:keys [db]} [_ id new-name]]
   {:db (update
         db
         ::char5e/parties
         (fn [parties]
           (map
            (fn [party]
              (if (= id (:db/id party))
                (assoc party ::party5e/name new-name)
                party))
            parties)))
    :http {:method :put
           :headers (authorization-headers db)
           :url (url-for-route routes/dnd-e5-char-party-name-route :id id)
           :transit-params new-name}}))

(reg-event-fx
 ::party5e/delete-party
 (fn [{:keys [db]} [_ id new-name]]
   {:db (update
         db
         ::char5e/parties
         (fn [parties]
           (remove
            (fn [party]
              (= id (:db/id party)))
            parties)))
    :http {:method :delete
           :headers (authorization-headers db)
           :url (url-for-route routes/dnd-e5-char-party-route :id id)}}))

(reg-event-fx
 ::mi/delete-custom-item-success
 (fn [_ _]
   {:dispatch [:route routes/dnd-e5-item-list-page-route]}))

(reg-event-fx
 ::mi/delete-custom-item
 (fn [{:keys [db]} [_ id]]
   {:db (update
         db
         ::mi/custom-items
         (fn [custom-items]
           (remove
            (fn [item]
              (= id (:db/id item)))
            custom-items)))
    :dispatch [::mi/delete-custom-item-success]
    :http {:method :delete
           :headers (authorization-headers db)
           :url (url-for-route routes/dnd-e5-item-route :id id)}}))

(reg-event-fx
 ::party5e/remove-character
 (fn [{:keys [db]} [_ id character-id]]
   {:db (update
         db
         ::char5e/parties
         (fn [parties]
           (map
            (fn [party]
              (if (= id (:db/id party))
                (update
                 party
                 ::party5e/character-ids
                 (fn [character-ids]
                   (remove
                    (fn [{:keys [:db/id]}]
                      (= character-id id))
                    character-ids)))
                party))
            parties)))
    :http {:method :delete
           :headers (authorization-headers db)
           :url (url-for-route routes/dnd-e5-char-party-character-route :id id :character-id character-id)}}))

(reg-event-fx
 ::party5e/add-character-remote-success
 (fn [_ [_ show-confirmation?]]
   (when show-confirmation?
     {:dispatch [:show-message [:div
                                "Character has been added to the party. View it on the "
                                [:span.underline.pointer.orange
                                 {:on-click #(dispatch [:route routes/dnd-e5-char-parties-page-route])}
                                 "Parties Page"]]]})))

(reg-event-fx
 ::party5e/add-character-remote
 (fn [{:keys [db]} [_ id character-id show-confirmation?]]
   {:http {:method :post
           :headers (authorization-headers db)
           :transit-params character-id
           :url (url-for-route routes/dnd-e5-char-party-characters-route :id id)
           :on-success [::party5e/add-character-remote-success show-confirmation?]}}))

(reg-event-fx
 ::party5e/add-character
 (fn [{:keys [db]} [_ id character-id show-confirmation?]]
   {:db (update
         db
         ::char5e/parties
         (fn [parties]
           (map
            (fn [party]
              (if (= id (:db/id party))
                (update
                 party
                 ::party5e/character-ids
                 conj
                 (get-in db [::char5e/summary-map character-id]))
                party))
            parties)))
    :dispatch [::party5e/add-character-remote id character-id show-confirmation?]}))

;; ---- Folder Events -------------------------------------------------------

(reg-event-db
 ::folder5e/set-folders
 (fn [db [_ folders]]
   (assoc db ::folder5e/folders folders)))

(reg-event-fx
 ::folder5e/on-folder-failure
 ;; Re-fetches folders from server to reconcile optimistic UI on HTTP failure.
 (fn [{:keys [db]} [_ _response]]
   {:http {:method :get
           :headers (authorization-headers db)
           :url (url-for-route routes/dnd-e5-char-folders-route)
           :on-success [::folder5e/set-folders-from-response]}
    :dispatch (event-utils/show-generic-error)}))

(reg-event-db
 ::folder5e/set-folders-from-response
 (fn [db [_ response]]
   (assoc db ::folder5e/folders (:body response))))

(reg-event-fx
 ::folder5e/create-folder
 (fn [{:keys [db]} [_]]
   {:http {:method :post
           :headers (authorization-headers db)
           :transit-params {::folder5e/name "New Folder"}
           :url (url-for-route routes/dnd-e5-char-folders-route)
           :on-success [::folder5e/create-folder-success]
           :on-failure [::folder5e/on-folder-failure]}}))

(reg-event-fx
 ::folder5e/create-folder-success
 (fn [{:keys [db]} [_ response]]
   (let [folder (:body response)]
     {:db (update db ::folder5e/folders conj folder)
      :dispatch [::folder5e/toggle-renaming (:db/id folder)]})))

(reg-event-fx
 ::folder5e/rename-folder
 (fn [{:keys [db]} [_ id new-name]]
   (let [trimmed (clojure.string/trim (str new-name))]
     (when-not (clojure.string/blank? trimmed)
       {:db (update db ::folder5e/folders
                    (fn [folders]
                      (map (fn [f]
                             (if (= id (:db/id f))
                               (assoc f ::folder5e/name trimmed)
                               f))
                           folders)))
        :http {:method :put
               :headers (authorization-headers db)
               :transit-params trimmed
               :url (url-for-route routes/dnd-e5-char-folder-name-route :id id)
               :on-failure [::folder5e/on-folder-failure]}}))))

(reg-event-fx
 ::folder5e/delete-folder
 (fn [{:keys [db]} [_ id]]
   {:db (update db ::folder5e/folders
                (fn [folders]
                  (remove #(= id (:db/id %)) folders)))
    :http {:method :delete
           :headers (authorization-headers db)
           :url (url-for-route routes/dnd-e5-char-folder-route :id id)
           :on-failure [::folder5e/on-folder-failure]}}))

(reg-event-fx
 ::folder5e/add-character
 (fn [{:keys [db]} [_ folder-id character-id]]
   {:db (update db ::folder5e/folders
                (fn [folders]
                  (map (fn [f]
                         (if (= folder-id (:db/id f))
                           (update f ::folder5e/character-ids
                                   conj
                                   (get-in db [::char5e/summary-map character-id]))
                           ;; remove from any other folder (at-most-one constraint)
                           (update f ::folder5e/character-ids
                                   (fn [chars]
                                     (remove #(= character-id (:db/id %)) chars)))))
                       folders)))
    :http {:method :post
           :headers (authorization-headers db)
           :transit-params character-id
           :url (url-for-route routes/dnd-e5-char-folder-characters-route :id folder-id)
           :on-failure [::folder5e/on-folder-failure]}}))

(reg-event-fx
 ::folder5e/remove-character
 (fn [{:keys [db]} [_ folder-id character-id]]
   {:db (update db ::folder5e/folders
                (fn [folders]
                  (map (fn [f]
                         (if (= folder-id (:db/id f))
                           (update f ::folder5e/character-ids
                                   (fn [chars]
                                     (remove #(= character-id (:db/id %)) chars)))
                           f))
                       folders)))
    :http {:method :delete
           :headers (authorization-headers db)
           :url (url-for-route routes/dnd-e5-char-folder-character-route
                               :id folder-id
                               :character-id character-id)
           :on-failure [::folder5e/on-folder-failure]}}))

;; When expanding a folder, collapse its characters so they start closed
(reg-event-db
 ::folder5e/toggle-expanded
 (fn [db [_ folder-id char-ids]]
   (let [opening? (not (get-in db [::folder5e/expanded folder-id]))]
     (cond-> (update-in db [::folder5e/expanded folder-id] not)
       (and opening? (seq char-ids))
       (update :expanded-characters
               (fn [ec] (apply dissoc (or ec {}) char-ids)))))))

(reg-event-db
 ::folder5e/toggle-renaming
 (fn [db [_ folder-id]]
   (update-in db [::folder5e/renaming folder-id] not)))

;; ---- End Folder Events ----------------------------------------------------

;; ---- Character Filter Events -----------------------------------------------

(reg-event-db
 ::char5e/set-char-name-filter
 (fn [db [_ v]]
   (assoc db ::char5e/char-name-filter v)))

(reg-event-db
 ::char5e/toggle-char-level-filter
 (fn [db [_ level]]
   (let [filters (or (get db ::char5e/char-level-filters) #{})]
     (assoc db ::char5e/char-level-filters
            ((if (filters level) disj conj) filters level)))))

(reg-event-db
 ::char5e/toggle-char-class-filter
 (fn [db [_ cls]]
   (let [filters (or (get db ::char5e/char-class-filters) #{})]
     (assoc db ::char5e/char-class-filters
            ((if (filters cls) disj conj) filters cls)))))

(reg-event-db
 ::char5e/toggle-char-has-portrait
 (fn [db _]
   (update db ::char5e/char-has-portrait?
           #(case % nil true, true false, false nil))))

(reg-event-db
 ::char5e/toggle-char-has-faction-pic
 (fn [db _]
   (update db ::char5e/char-has-faction-pic?
           #(case % nil true, true false, false nil))))

(reg-event-db
 ::char5e/clear-char-filters
 (fn [db _]
   (dissoc db
           ::char5e/char-name-filter
           ::char5e/char-level-filters
           ::char5e/char-class-filters
           ::char5e/char-has-portrait?
           ::char5e/char-has-faction-pic?)))

;; ---- End Character Filter Events -------------------------------------------

(reg-event-fx
 :follow-user-success
 (fn []))

(reg-event-fx
 :follow-user
 (fn [{:keys [db]} [_ username]]
   (let [path (routes/path-for routes/follow-user-route :user username)]
     {:dispatch [:set-user (update (:user db) :following conj username)]
      :http {:method :post
             :headers (authorization-headers db)
             :url (backend-url path)
             :on-success [:follow-user-success]}})))

(reg-event-fx
 :unfollow-user-success
 (fn []))

(reg-event-fx
 :delete-account
 (fn [{:keys [db]} _]
   (let [path (routes/path-for routes/user-route)]
     {:dispatch-n [[:logout]
                   [:new-character]
                   [:route routes/dnd-e5-char-builder-route]]
      :http {:method :delete
             :headers (authorization-headers db)
             :url (backend-url path)}})))

(reg-event-fx
 :change-email
 (fn [{:keys [db]} [_ new-email]]
   {:db (dissoc db :email-change-sent? :email-change-error)
    :http {:method :put
           :headers (authorization-headers db)
           :url (backend-url (routes/path-for routes/user-email-route))
           :transit-params {:new-email new-email}
           :on-success [:change-email-success]
           :on-failure [:change-email-failure]}}))

(reg-event-db
 :change-email-success
 (fn [db [_ response]]
   (-> db
       (assoc :email-change-sent? true)
       ;; Use server-canonical (lowercased/trimmed) email for display
       (assoc-in [:user-data :user-data :pending-email]
                 (-> response :body :pending-email)))))

(reg-event-db
 :change-email-failure
 (fn [db [_ response]]
   (let [body (:body response)
         error (:error body)]
     (assoc db :email-change-error
            (case error
              :email-taken "That email address is already in use by another account."
              :invalid-email "Please enter a valid email address."
              :same-as-current "That is already your current email address."
              :too-many-requests
              (let [secs (:retry-after-secs body)]
                (if (and secs (pos? secs))
                  (if (<= secs 60)
                    ;; 0–1 min zone: email is in transit, show short countdown
                    (str "Your email is on its way. You can resend in " secs " second" (when (> secs 1) "s") ".")
                    ;; 1–5 min zone for a different email: show minutes
                    (let [mins (.ceil js/Math (/ secs 60))]
                      (str "Please wait " mins " minute" (when (> mins 1) "s") " before requesting another change.")))
                  "Please wait a few minutes before requesting another email change."))
              :email-send-failed "Verification email could not be sent. Please try again later."
              "There was an error updating your email. Please try again.")))))

(reg-event-db
 :change-email-clear
 (fn [db _]
   (dissoc db :email-change-sent? :email-change-error)))

;; ─── Email Preferences ─────────────────────────────────────────────

(reg-event-fx
 :toggle-send-updates
 (fn [{:keys [db]} [_ new-value]]
   {:http {:method :put
           :headers (authorization-headers db)
           :url (backend-url (routes/path-for routes/user-route))
           :transit-params {:send-updates? (boolean new-value)}
           :on-success [:toggle-send-updates-success new-value]
           :on-failure [:toggle-send-updates-failure]}}))

(reg-event-db
 :toggle-send-updates-success
 (fn [db [_ new-value]]
   (assoc-in db [:user-data :user-data :send-updates?] (boolean new-value))))

(reg-event-db
 :toggle-send-updates-failure
 (fn [db _]
   db))

(reg-event-fx
 :unfollow-user
 (fn [{:keys [db]} [_ username]]
   (let [path (routes/path-for routes/follow-user-route :user username)]
     {:dispatch-n [[:set-user (update (:user db) :following #(remove (partial = username) %))]
                   [::char5e/remove-user-characters username]]
      :http {:method :delete
             :headers (authorization-headers db)
             :url (backend-url path)
             :on-success [:unfollow-user-success]}})))

(defn- loaded-class-keys
  "Set of class keys currently known to the system — SRD built-ins plus
   enabled plugin classes from db :plugins. Same source the class dropdown
   consumes via ::classes5e/classes."
  [db]
  (into class5e/base-class-keys
        (for [[_ plugin-data] (:plugins db)
              :when (and (map? plugin-data) (not (:disabled? plugin-data)))
              [class-key class-data] (::e5/classes plugin-data)
              :when (and (map? class-data) (not (:disabled? class-data)))]
          class-key)))

(defn set-character [db [_ character]]
  ;; db :plugins are already hydrated here — ::e5/plugins is a sync cofx at
  ;; :initialize-db, so the reconciler can trust loaded-class-keys.
  (let [{:keys [character]}
        (content-recon/reconcile-spell-selection-keys
         character
         (loaded-class-keys db))]
    (assoc db :character character :loading false)))

(reg-event-db
 :toggle-character-expanded
 (fn [db [_ character-id]]
   (update-in db [:expanded-characters character-id] not)))

(reg-event-db
 :toggle-monster-expanded
 (fn [db [_ monster-name]]
   (update-in db [:expanded-monsters monster-name] not)))

(reg-event-db
 :toggle-spell-expanded
 (fn [db [_ spell-name]]
   (update-in db [:expanded-spells spell-name] not)))

(reg-event-db
 :toggle-item-expanded
 (fn [db [_ item-name]]
   (update-in db [:expanded-items item-name] not)))

(reg-event-db
 :set-character
 [db-char->local-store]
 set-character)

(def character-values-path
  [::entity/values])

(defn character-value-path [prop-name]
  (conj character-values-path prop-name))

(defn update-value-field [character [_ prop-name value]]
  (assoc-in character (character-value-path prop-name) value))

(reg-event-db
 :update-value-field
 character-interceptors
 update-value-field)

(defn generate-random-name
  "Generate a random name from the built character's race/subrace/sex.
   Falls back to a random human name for unsupported or custom races."
  [built-char]
  (let [race-kw (common/name-to-kw (char5e/race built-char) "orcpub.dnd.e5.character.random")
        subrace-kw (common/name-to-kw (char5e/subrace built-char) "orcpub.dnd.e5.character.random")
        sex-kw (common/name-to-kw (char5e/sex built-char) "orcpub.dnd.e5.character.random")]
    (:name (char-rand5e/random-name-result
            {:race race-kw
             :subrace (when (= ::char-rand5e/human race-kw) subrace-kw)
             :sex sex-kw}))))

;; Generate a random name based on character's race/subrace/sex.
;; built-char passed from component (description-fields).
(reg-event-fx
 ::char5e/set-random-name
 (fn [_ [_ built-char]]
   {:dispatch [:update-value-field ::char5e/character-name
               (generate-random-name built-char)]}))
(reg-event-db
 :select-option
 character-interceptors
 event-handlers/select-option)

(defn add-class [character [_ first-unselected]]
  (update-in
   character
   [::entity/options :class]
   conj
   {::entity/key first-unselected ::entity/options {:levels [{::entity/key :level-1}]}}))

(reg-event-db
 :add-class
 character-interceptors
 add-class)

(reg-event-db
 :set-image-url
 character-interceptors
 (fn [character [_ image-url]]
   (update character
           ::entity/values
           assoc
           ::char5e/image-url
           image-url
           ::char5e/image-url-failed
           nil)))

#_ ;; never dispatched from UI
  (reg-event-db
   :toggle-public
   character-interceptors
   (fn [character _]
     (update character
             ::entity/values
             update
             ::char5e/share?
             not)))

(reg-event-db
 :set-faction-image-url
 character-interceptors
 (fn [character [_ faction-image-url]]
   (update character
           ::entity/values
           assoc
           ::char5e/faction-image-url
           faction-image-url
           ::char5e/faction-image-url-failed
           nil)))

(reg-event-db
 :add-background-starting-equipment
 character-interceptors
 event-handlers/add-background-starting-equipment)

(reg-event-db
 :set-class
 character-interceptors
 event-handlers/set-class)

(reg-event-db
 :set-class-level
 character-interceptors
 event-handlers/set-class-level)

(defn delete-class [character [_ class-key i options-map]]
  (let [updated (update-in
                 character
                 [::entity/options :class]
                 (fn [classes] (vec (remove #(= class-key (::entity/key %)) classes))))
        new-first-class-key (get-in updated [::entity/options :class 0 ::entity/key])
        new-first-class-option (when new-first-class-key (options-map new-first-class-key))]
    (if (and (zero? i)
             new-first-class-option)
      (char5e/set-class updated new-first-class-key 0 new-first-class-option)
      updated)))

(reg-event-db
 :delete-class
 character-interceptors
 delete-class)

(reg-event-db
 :add-inventory-item
 character-interceptors
 (fn [character [_ selection-key item-key]]
   (event-handlers/add-inventory-item character selection-key item-key)))

(defn toggle-inventory-item-equipped [character [_ selection-key item-index]]
  (common/toggle-in
   character
   [::entity/options selection-key item-index ::entity/value ::char-equip5e/equipped?]))

(reg-event-db
 :toggle-inventory-item-equipped
 character-interceptors
 toggle-inventory-item-equipped)

(defn toggle-custom-inventory-item-equipped [character [_ custom-equipment-key item-index]]
  (common/toggle-in
   character
   [::entity/values custom-equipment-key item-index ::char-equip5e/equipped?]))

(reg-event-db
 :toggle-custom-inventory-item-equipped
 character-interceptors
 toggle-custom-inventory-item-equipped)

(defn change-inventory-item-quantity [character [_ selection-key item-index quantity]]
  (update-in
   character
   [::entity/options selection-key item-index ::entity/value]
   (fn [item-cfg]
     ;; the select keys here is to keep :equipped while wiping out the starting-equipment indicators
     (assoc (select-keys item-cfg [::char-equip5e/equipped?]) ::char-equip5e/quantity quantity))))

(reg-event-db
 :change-inventory-item-quantity
 character-interceptors
 change-inventory-item-quantity)

(defn change-custom-inventory-item-quantity [character [_ custom-equipment-key item-index quantity]]
  (update-in
   character
   [::entity/values custom-equipment-key item-index]
   (fn [item-cfg]
     ;; the select keys here is to keep :equipped and :name while wiping out the starting-equipment indicators
     (assoc
      (select-keys item-cfg [::char-equip5e/name ::char-equip5e/equipped?])
      ::char-equip5e/quantity
      quantity))))

(reg-event-db
 :change-custom-inventory-item-quantity
 character-interceptors
 change-custom-inventory-item-quantity)

(reg-event-db
 :remove-inventory-item
 character-interceptors
 event-handlers/remove-inventory-item)

(defn remove-custom-inventory-item [character [_ custom-equipment-key name]]
  (update-in
   character
   [::entity/values custom-equipment-key]
   (fn [items]
     (vec (remove #(= name (::char-equip5e/name %)) items)))))

(reg-event-db
 :remove-custom-inventory-item
 character-interceptors
 remove-custom-inventory-item)

(defn set-abilities [character [_ abilities]]
  (assoc-in character [::entity/options :ability-scores ::entity/value] abilities))

(reg-event-db
 :set-abilities
 character-interceptors
 set-abilities)

(defn swap-ability-values [character [_ i other-i k v]]
  (update-in
   character
   [::entity/options :ability-scores ::entity/value]
   (fn [a]
     (let [a-vec (vec (map (fn [k] [k (k a)]) char5e/ability-keys))
           other-index (mod other-i (count a-vec))
           [other-k other-v] (a-vec other-index)]
       (assoc a k other-v other-k v)))))

(reg-event-db
 :swap-ability-values
 character-interceptors
 swap-ability-values)

(defn decrease-ability-value [character [_ full-path k]]
  (update-in
   character
   full-path
   (fn [incs]
     (common/remove-first
      (fn [{inc-key ::entity/key}]
        (= inc-key k))
      incs))))

(reg-event-db
 :decrease-ability-value
 character-interceptors
 decrease-ability-value)

(defn increase-ability-value [character [_ full-path k]]
  (update-in
   character
   full-path
   conj
   {::entity/key k}))

(reg-event-db
 :increase-ability-value
 character-interceptors
 increase-ability-value)

(defn set-ability-score [character [_ ability-kw v]]
  (assoc-in character [::entity/options :ability-scores ::entity/value ability-kw] v))

(reg-event-db
 :set-ability-score
 character-interceptors
 set-ability-score)

(defn set-ability-score-variant [character [_ variant-key]]
  (assoc-in character [::entity/options :ability-scores ::entity/key] variant-key))

(reg-event-db
 :set-ability-score-variant
 character-interceptors
 set-ability-score-variant)

(defn select-skill [character [_ path selected? skill-key]]
  (update-in
   character
   path
   (fn [skills]
     (if selected?
       (vec (remove (fn [s] (= skill-key (::entity/key s))) skills))
       (vec (conj skills {::entity/key skill-key}))))))

(reg-event-db
 :select-skill
 character-interceptors
 select-skill)

(defn set-total-hps [character [_ full-path first-selection selection average-value remainder]]
  (assoc-in
   character
   full-path
   {::entity/key :manual-entry
    ::entity/value (if (= first-selection selection)
                     (+ average-value remainder)
                     average-value)}))

(reg-event-db
 :set-total-hps
 character-interceptors
 set-total-hps)

(defn randomize-hit-points [character [_ built-template path levels class-kw]]
  (assoc-in
   character
   (entity/get-entity-path built-template character path)
   (random-hit-points-option levels class-kw)))

(reg-event-db
 :randomize-hit-points
 character-interceptors
 randomize-hit-points)

(defn set-hit-points-to-average [character [_ built-template path levels class-kw]]
  (assoc-in
   character
   (entity/get-entity-path built-template character path)
   {::entity/key :average
    ::entity/value (dice/die-mean-round-up (-> levels class-kw :hit-die))}))

(reg-event-db
 :set-hit-points-to-average
 character-interceptors
 set-hit-points-to-average)

#_:clj-kondo/ignore
(defn set-level-hit-points [character [_ built-template character level-value value]]
  (assoc-in
   character
   (entity/get-entity-path built-template character (:path level-value))
   {::entity/key :manual-entry
    ::entity/value (when (not (js/isNaN value)) value)}))

(reg-event-db
 :set-level-hit-points
 character-interceptors
 set-level-hit-points)

(defn set-page [db [_ page-index]]
  (assoc db :page page-index))

(reg-event-db
 :set-page
 set-page)

(defn make-url [protocol hostname path & [port]]
  (str protocol "://" hostname (when port (str ":" port)) path))

(defn- shared-content-payload
  "The homebrew-share payload carried in the URL fragment (#c=...), or nil.
   Only meaningful on a character-page URL someone was given a share link to."
  []
  (let [h (or (some-> js/window .-location .-hash) "")]
    (when (s/starts-with? h "#c=")
      (not-empty (subs h 3)))))

(reg-event-fx
 :route
 (fn [{:keys [db]} [_ {:keys [handler route-params] :as new-route} {:keys [no-return? skip-path? event secure?] :as options}]]
   (integrations/track-page-view! new-route)
   (let [{:keys [route route-history]} db
         seq-params (seq route-params)
         flat-params (flatten seq-params)
         path (apply routes/path-for (or handler new-route) flat-params)
         ;; Homebrew embedded in a share link loads only on the character page,
         ;; and only into the ephemeral :shared-plugins overlay (never the library).
         char-page? (= (or handler new-route) routes/dnd-e5-char-page-route)
         shared-payload (when char-page? (shared-content-payload))]
     (when (and js/window.location
                secure?
                (not= "localhost" js/window.location.hostname))
       (set! js/window.location.href (make-url "https"
                                               js/window.location.hostname
                                               path
                                               js/window.location.port)))
     (cond-> {:db (assoc db :route new-route)
              :dispatch-n [[:hide-message]
                           [:close-orcacle]]}
       (not no-return?) (assoc-in [:db :return-route] new-route)
       (not skip-path?) (assoc :path path)
       ;; Leaving/entering a plain character view clears any prior shared overlay
       ;; so view-once content (homebrew + custom items) never lingers across characters.
       char-page? (update :db assoc :shared-plugins nil :shared-custom-items nil)
       shared-payload (update :dispatch-n conj [::e5/load-shared-content shared-payload])
       event (update :dispatch-n conj event)))))

(reg-event-db
 :set-user-data
 [user->local-store-interceptor]
 (fn [db [_ user-data]]
   (update db :user-data merge user-data)))

(reg-event-db
 :clear-login
 [user->local-store-interceptor]
 (fn [db [_ user-data]]
   (update db :user-data dissoc :user-data :token)))

;; Startup auth check — validates stored token on app load (core.cljs).
;; Clears stale sessions before reg-sub-raw subs fire HTTP with expired tokens.
(reg-event-fx
 :verify-user-session
 (fn [{:keys [db]} _]
   (if (:token (:user-data db))
     (do (go (let [response (<! (http/get (url-for-route routes/user-route)
                                          {:headers (authorization-headers db)}))]
               (case (:status response)
                 200 nil
                 401 (do (dispatch [:clear-login])
                         (dispatch [:set-loading false]))
                 nil)))
         {})
     {})))

(reg-event-db
 :set-user
 (fn [db [_ user-data]]
   (assoc db :user user-data)))

#_ ;; never dispatched from UI
  (defn set-active-tabs [db [_ active-tabs]]
    (assoc-in db tab-path active-tabs))

#_ ;; never dispatched from UI
  (reg-event-db
   :set-active-tabs
   set-active-tabs)

(defn set-loading
  "Loading is a counter, not a boolean. true increments, false decrements.
   Overlay shows when > 0. Multiple parallel HTTP calls no longer fight."
  [db [_ v]]
  (let [current (or (:loading db) 0)]
    (assoc db :loading
           (if v
             (inc current)
             (max 0 (dec current))))))

(reg-event-db
 :set-loading
 set-loading)

(reg-event-db
 :toggle-locked
 (fn [db [_ path]]
   (update db :locked-components (fn [comps]
                                   (if (comps path)
                                     (disj comps path)
                                     (conj comps path))))))

(reg-event-db
 :toggle-homebrew
 character-interceptors
 (fn [character [_ path]]
   (update-in character
              [::entity/homebrew-paths path]
              not)))

(reg-event-db
 :failed-loading-image
 character-interceptors
 (fn [character [_ image-url]]
   (update character
           ::entity/values
           assoc
           ::char5e/image-url-failed
           image-url)))

(reg-event-db
 :failed-loading-faction-image
 character-interceptors
 (fn [character [_ faction-image-url]]
   (update character
           ::entity/values
           assoc
           ::char5e/faction-image-url-failed
           faction-image-url)))

(reg-event-db
 :loaded-image
 character-interceptors
 (fn [character []]
   (update character
           ::entity/values
           dissoc
           ::char5e/image-url-failed)))

(reg-event-db
 :loaded-faction-image
 character-interceptors
 (fn [character []]
   (update character
           ::entity/values
           dissoc
           ::char5e/faction-image-url-failed)))

(reg-event-db
 :set-custom-race
 character-interceptors
 (fn [character [_ name]]
   (assoc-in character
             [::entity/options
              :race
              ::entity/value]
             name)))

(reg-event-db
 :set-custom-subrace
 character-interceptors
 (fn [character [_ name]]
   (assoc-in character
             [::entity/options
              :race
              ::entity/options
              :subrace
              ::entity/value]
             name)))

;; Set homebrew subclass name. built-template passed from custom-option-builder.
(reg-event-db
 :set-custom-subclass
 character-interceptors
 (fn [character [_ path built-template name]]
   (let [entity-path (entity/get-option-value-path
                      built-template
                      character
                      path)]
     (assoc-in character
               entity-path
               name))))

;; Set homebrew feat name. built-template passed from custom-option-builder.
(reg-event-db
 :set-custom-feat-name
 character-interceptors
 (fn [character [_ path built-template name]]
   (let [entity-path (entity/get-option-value-path
                      built-template
                      character
                      path)]
     (assoc-in character
               entity-path
               name))))

(reg-event-db
 :set-custom-background
 character-interceptors
 (fn [character [_ name]]
   (assoc-in character
             [::entity/options
              :background
              ::entity/value]
             name)))

(defn cookies []
  (let [cookie js/document.cookie]
    (into {}
          (map #(s/split % "="))
          (s/split cookie "; "))))

(def show-generic-error event-utils/show-generic-error)

(reg-fx
 :http
 (fn [{:keys [on-success on-failure on-unauthorized auth-token] :as cfg}]
   (let [final-cfg (if auth-token
                     (assoc-in cfg [:headers "Authorization"] (str "Token " auth-token))
                     cfg)]
     (go (let [response (<! (http/request final-cfg))]
           (dispatch [:set-loading false])
           (if (<= 200 (:status response) 299)
             (when on-success (dispatch (conj on-success response)))
             (if (= 401 (:status response))
               (if on-unauthorized
                 (dispatch (conj on-unauthorized response))
                 (dispatch [:route-to-login]))
               (if on-failure
                 (dispatch (conj on-failure response))
                 (dispatch (show-generic-error))))))))))

(reg-fx
 :path
 (fn [path]
   (.pushState js/window.history {} nil path)))

(def login-url (backend-url "/login"))

(reg-event-fx
 :login-success
 [user->local-store-interceptor]
 (fn [{:keys [db]} [_ backtrack? response]]
   {:db (update db :user-data merge (-> response :body))
    :dispatch [:route (or
                       (:return-route db)
                       routes/dnd-e5-char-builder-route)]}))

(defn show-old-account-message []
  [:show-login-message [:div  "There is no account for the email or username, please double-check it. Usernames and passwords are case sensitive, email addresses are not. You can also try to " [:a {:href (routes/path-for routes/register-page-route)} "register"] "."]])

(defn dispatch-login-failure [message]
  {:dispatch-n [[:clear-login]
                [:show-login-message message]]})

(reg-event-fx
 :login-failure
 (fn [{:keys [db]} [_ response]]
   (let [error-code (-> response :body :error)]
     (cond
       (= error-code errors/username-required) (dispatch-login-failure "Username is required.")
       (= error-code errors/too-many-attempts) (dispatch-login-failure "You have made too many login attempts, you account is locked for 15 minutes. Please do not try to login again until 15 minutes have passed.")
       (= error-code errors/password-required) (dispatch-login-failure "Password is required.")
       (= error-code errors/bad-credentials) (dispatch-login-failure "Password is incorrect.")
       (= error-code errors/no-account) {:dispatch-n [[:clear-login]
                                                      (show-old-account-message)]}
       (= error-code errors/unverified) {:db (assoc db :temp-email (-> response :body :email))
                                         :dispatch [:route routes/verify-sent-route]}
       (= error-code errors/unverified-expired) {:dispatch [:route routes/verify-failed-route]}
       :else (dispatch-login-failure
              (if (seq branding/support-email)
                [:div "An error occurred. If the problem persists please email "
                 [:a {:href (str "mailto:" branding/support-email) :target :blank} branding/support-email]]
                [:div "An error occurred. Please try again later."]))))))

(reg-event-fx
 :logout
 (fn [cofx [_ response]]
   {:dispatch-n [[:clear-login]]}))

(def login-routes
  #{routes/login-page-route
    routes/register-page-route
    routes/verify-sent-route
    routes/reset-password-page-route
    routes/verify-failed-route
    routes/verify-success-route
    routes/send-password-reset-page-route
    routes/password-reset-success-route
    routes/password-reset-expired-route
    routes/password-reset-used-route
    routes/unsubscribe-success-route})

(reg-event-fx
 :login
 (fn [{:keys [db]} [_ params backtrack?]]
   {:http {:method :post
           :url login-url
           :json-params params
           :on-success [:login-success backtrack?]
           :on-unauthorized [:login-failure]}}))

(reg-event-db
 :register-success
 (fn [db [_ backtrack? response]]
   (-> db
       (update :user-data merge (:body response))
       (assoc :route :verify-sent))))

(reg-event-fx
 :register-failure
 (fn [cofx [_ response]]
   {:dispatch [:clear-login]}))

#_ ;; dead stub — real impl is orcpub.registration/validate-registration
  (defn validate-registration [])


(reg-event-db
 :email-taken
 (fn [db [_ response]]
   (assoc db :email-taken? (-> response :body (= "true")))))

(reg-event-db
 :username-taken
 (fn [db [_ response]]
   (assoc db :username-taken? (-> response :body (= "true")))))

#_ ;; never dispatched — registration form uses :register-first-and-last-name
  (reg-event-db
   :registration-first-and-last-name
   (fn [db [_ first-and-last-name]]
     (assoc-in db [:registration-form :first-and-last-name] first-and-last-name)))

(reg-event-fx
 :registration-email
 (fn [{:keys [db]} [_ email]]
   {:db (assoc-in db [:registration-form :email] email)
    :dispatch [:check-email email]}))

(reg-event-fx
 :registration-verify-email
 (fn [{:keys [db]} [_ email]]
   {:db (assoc-in db [:registration-form :verify-email] email)}))

(reg-event-fx
 :registration-username
 (fn [{:keys [db]} [_ username]]
   {:db (assoc-in db [:registration-form :username] username)
    :dispatch [:check-username username]}))

(reg-event-db
 :registration-password
 (fn [db [_ password]]
   (assoc-in db [:registration-form :password] password)))

(reg-event-db
 :registration-send-updates?
 (fn [db [_ send-updates?]]
   (assoc-in db [:registration-form :send-updates?] send-updates?)))

#_ ;; never dispatched from UI
  (reg-event-db
   :register-first-and-last-name
   (fn [db [_ first-and-last-name]]
     (assoc-in db [:registration-form :first-and-last-name] first-and-last-name)))

(reg-event-fx
 :check-email
 (fn [{:keys [db]} [_ email]]
   {:http {:method :get
           :url (backend-url (bidi/path-for routes/routes routes/check-email-route))
           :query-params {:email email}
           :on-success [:email-taken]}}))

(reg-event-fx
 :check-username
 (fn [{:keys [db]} [_ username]]
   {:http {:method :get
           :url (backend-url (bidi/path-for routes/routes routes/check-username-route))
           :query-params {:username username}
           :on-success [:username-taken]}}))

(reg-event-fx
 :register
 (fn [{:keys [db]} [_ params backtrack?]]
   (let [registration-form (:registration-form db)]
     {:db (assoc db :temp-email (:email registration-form))
      :http {:method :post
             :url (backend-url (bidi/path-for routes/routes routes/register-route))
             :json-params registration-form
             :on-success [:register-success backtrack?]
             :on-failure [:register-failure]}})))

(reg-event-db
 :re-verify-success
 (fn [db []]
   (assoc db :route routes/verify-sent-route)))

(reg-event-fx
 :re-verify
 (fn [{:keys [db]} [_ params]]
   {:db (assoc db :temp-email (:email params))
    :http {:method :get
           :url (backend-url (bidi/path-for routes/routes routes/re-verify-route))
           :query-params params
           :on-success [:re-verify-success]}}))

(reg-event-db
 :send-password-reset-success
 (fn [db []]
   (assoc db :route routes/password-reset-sent-route)))

(reg-event-fx
 :send-password-reset-failure
 (fn [_ [_ response]]
   (let [error (-> response :body :error (= :no-account))]
     (if error
       (dispatch (show-old-account-message))
       (show-generic-error)))))

(reg-event-fx
 :send-password-reset
 (fn [{:keys [db]} [_ params]]
   {:db (assoc db :temp-email (:email params))
    :http {:method :get
           :url (backend-url (bidi/path-for routes/routes routes/send-password-reset-route))
           :query-params params
           :on-success [:send-password-reset-success]
           :on-failure [:send-password-reset-failure]}}))

;; never dispatched — character loading uses :load-user-data flow
#_(reg-event-db
   :load-characters-success
   (fn [db [_ response]]
     (assoc-in db [:dnd :e5 :characters] (:body response))))

(defn get-auth-token [db]
  (-> db :user-data :token))

#_ ;; never dispatched — character loading uses :load-user-data flow
  (reg-event-fx
   :load-characters
   (fn [{:keys [db]} [_ params]]
     {:http {:method :get
             :auth-token (get-auth-token db)
             :url (backend-url (routes/path-for routes/dnd-e5-char-list-route))
             :on-success [:load-characters-success]}}))

(reg-event-db
 :password-reset-success
 (fn [db []]
   (assoc db :route routes/password-reset-success-route)))

(reg-event-fx
 :password-reset-failure
 (fn [_ _]
   (dispatch-login-failure "There was an error resetting your password.")))

(reg-event-fx
 :password-reset
 (fn [{:keys [db]} [_ params]]
   (let [c (cookies)
         token (c "token")]
     {:db (assoc db :temp-email (:email params))
      :http {:method :post
             :auth-token token
             :url (backend-url (bidi/path-for routes/routes routes/reset-password-route))
             :json-params params
             :on-success [:password-reset-success]
             :on-unauthorized [:password-reset-failure]
             :on-failure [:password-reset-failure]}})))

(reg-event-db
 ::char5e/set-characters
 (fn [db [_ characters]]
   (assoc db
          ::char5e/characters characters
          ::char5e/summary-map (common/map-by-id characters))))

(reg-event-db
 ::mi/set-custom-items
 (fn [db [_ items]]
   (assoc db ::mi/custom-items items)))

(reg-event-db
 ::party5e/set-parties
 (fn [db [_ parties]]
   (assoc db
          ::char5e/parties parties
          ::char5e/parties-map (common/map-by-id parties))))

(reg-event-db
 ::char5e/remove-user-characters
 (fn [db [_ user]]
   (update db ::char5e/characters (fn [characters]
                                    (remove
                                     (fn [{:keys [:orcpub.entity.strict/owner]}]
                                       (= owner user))
                                     characters)))))

(reg-event-db
 ::char5e/set-character
 (fn [db [_ id character]]
   (let [int-id (js/parseInt id)
         updated (assoc-in db
                           [::char5e/character-map int-id]
                           character)]
     (if (and (= int-id (get-in db [:character :db/id]))
              (not (get-in db [:character :changed])))
       (assoc updated :character character)
       updated))))

(reg-event-fx
 :edit-character
 (fn [{:keys [db]} [_ character]]
   {:dispatch-n [[:set-character character]
                 [:route routes/dnd-e5-char-builder-route]]}))

(reg-event-fx
 ::mi/edit-custom-item
 (fn [{:keys [db]} [_ item]]
   {:dispatch-n [[::mi/set-item (mi/to-internal-item item)]
                 [:route routes/dnd-e5-item-builder-page-route]]}))

(defn reg-edit-homebrew [event set-event route]
  (reg-event-fx
   event
   (fn [{:keys [db]} [_ item]]
     {:dispatch-n [[set-event item]
                   [:route route]]})))

(reg-edit-homebrew
 ::spells/edit-spell
 ::spells/set-spell
 routes/dnd-e5-spell-builder-page-route)

(reg-edit-homebrew
 ::monsters/edit-monster
 ::monsters/set-monster
 routes/dnd-e5-monster-builder-page-route)

(reg-edit-homebrew
 ::encounters/edit-encounter
 ::encounters/set-encounter
 routes/dnd-e5-encounter-builder-page-route)

(reg-edit-homebrew
 ::bg5e/edit-background
 ::bg5e/set-background
 routes/dnd-e5-background-builder-page-route)

(reg-edit-homebrew
 ::langs5e/edit-language
 ::langs5e/set-language
 routes/dnd-e5-language-builder-page-route)

(reg-edit-homebrew
 ::class5e/edit-invocation
 ::class5e/set-invocation
 routes/dnd-e5-invocation-builder-page-route)

(reg-edit-homebrew
 ::class5e/edit-boon
 ::class5e/set-boon
 routes/dnd-e5-boon-builder-page-route)

(reg-edit-homebrew
 ::selections5e/edit-selection
 ::selections5e/set-selection
 routes/dnd-e5-selection-builder-page-route)

(reg-edit-homebrew
 ::feats5e/edit-feat
 ::feats5e/set-feat
 routes/dnd-e5-feat-builder-page-route)

(reg-edit-homebrew
 ::race5e/edit-race
 ::race5e/set-race
 routes/dnd-e5-race-builder-page-route)

(reg-edit-homebrew
 ::race5e/edit-subrace
 ::race5e/set-subrace
 routes/dnd-e5-subrace-builder-page-route)

(reg-edit-homebrew
 ::class5e/edit-subclass
 ::class5e/set-subclass
 routes/dnd-e5-subclass-builder-page-route)

(reg-edit-homebrew
 ::class5e/edit-class
 ::class5e/set-class
 routes/dnd-e5-class-builder-page-route)

(reg-event-fx
 :delete-character-success
 (fn [_ _]
   {:dispatch [:show-message "Character successfully deleted"]}))


(reg-event-fx
 :delete-character
 (fn [{:keys [db]} [_ id]]
   {:db (update db
                ::char5e/characters
                (fn [chars]
                  (remove #(-> % :db/id (= id)) chars)))
    :http {:method :delete
           :auth-token (get-auth-token db)
           :url (backend-url (routes/path-for routes/dnd-e5-char-route :id id))
           :on-success [:delete-character-success]}}))

(reg-event-fx
 :report-character-problem
 ;; User clicked "email support" on the character-load recovery panel. POST the
 ;; diagnostic (auth'd); the server gates on email config + rate-limits and
 ;; returns {:sent? .. :reason ..}, which we reflect in the button.
 (fn [{:keys [db]} [_ char-id error raw]]
   {:db (assoc-in db [:character-report-status char-id] :sending)
    :http {:method :post
           :auth-token (get-auth-token db)
           :url (backend-url (routes/path-for routes/dnd-e5-char-report-route))
           :transit-params {:char-id char-id :error error :raw raw}
           :on-success [:report-character-result char-id]
           :on-failure [:report-character-result char-id]}}))

(reg-event-db
 :report-character-result
 (fn [db [_ char-id response]]
   (assoc-in db [:character-report-status char-id]
             (if (get-in response [:body :sent?]) :sent :failed))))

(reg-event-fx
 :new-character
 (fn [{:keys [db]} _]
   {:db (assoc db :character default-character)
    :dispatch [:route routes/dnd-e5-char-builder-route]}))

(reg-event-db
 :hide-message
 (fn [db _]
   (assoc db :message-shown? false)))

(reg-event-db
 :hide-login-message
 (fn [db _]
   (assoc db :login-message-shown? false)))

(reg-event-db
 :show-message
 (fn [db [_ message ttl]]
   (go (<! (timeout (or ttl 5000)))
       (dispatch [:hide-message]))
   (assoc db
          :message-shown? true
          :message message
          :message-type :success)))

(reg-event-db
 :show-message-2
; Display msg with out auto closing the msg.
 (fn [db [_ message]]
   (prn message)
   (assoc db
          :message-shown? true
          :message message
          :message-type :success)))

(reg-event-db
 :show-warning-message
 (fn [db [_ message ttl]]
   (go (<! (timeout (or ttl 5000)))
       (dispatch [:hide-message]))
   (assoc db
          :message-shown? true
          :message message
          :message-type :warning)))

(reg-event-db
 :show-error-message
 ;; Errors are NOT fleeting: they persist until the user dismisses them
 ;; (the message banner is click/✕-to-close via `hide-message`). An error the
 ;; user may need to act on must not disappear on a timer. A `ttl` may still be
 ;; passed to opt a specific error back into auto-dismiss, but the default is
 ;; sticky-until-dismissed.
 (fn [db [_ message ttl]]
   (when ttl
     (go (<! (timeout ttl))
         (dispatch [:hide-message])))
   (assoc db
          :message-shown? true
          :message message
          :message-type :error)))

(reg-event-db
 :show-login-message
 (fn [db [_ message]]
   (go (<! (timeout 15000))
       (dispatch [:hide-login-message]))
   (assoc db
          :login-message-shown? true
          :login-message message)))

#_ ;; never dispatched from UI
  (reg-event-db
   :hide-warning
   (fn [db _]
     (assoc db :warning-hidden true)))

(reg-event-db
 :hide-confirmation
 (fn [db _]
   (assoc db :confirmation-shown? false)))

(reg-event-fx
 :confirm
 (fn [_ [_ event]]
   {:dispatch-n [[:hide-confirmation]
                 event]}))

(reg-event-db
 :show-confirmation
 (fn [db [_ cfg]]
   (assoc db
          :confirmation-shown? true
          :confirmation-cfg cfg)))

(defn name-result [search-text]
  (let [[sex race subrace :as result] (event-handlers/parse-name-query search-text)]
    (when result
      {:type :name
       :result (char-rand5e/random-name-result
                {:race race
                 :subrace subrace
                 :sex sex})})))

#_ ;; unreferenced
  (defn remove-subtypes [subtypes hidden-subtypes]
    (let [result (sets/difference subtypes hidden-subtypes)]
      result))

#_ ;; orphaned re-export alias — callers use compute/compute-plugin-vals directly
  (def compute-plugin-vals compute/compute-plugin-vals)
(def compute-sorted-spells compute/compute-sorted-spells)
(def compute-sorted-items compute/compute-sorted-items)
(def filter-by-name-xform compute/filter-by-name-xform)
(def filter-spells compute/filter-spells)
(def filter-items compute/filter-items)

(defn search-results [text]
  (let [search-text (s/lower-case text)
        dice-result (dice/dice-roll-text search-text)
        kw (when search-text (common/name-to-kw search-text))
        name-result (name-result search-text)
        top-result (cond
                     dice-result {:type :dice-roll
                                  :result dice-result}
                     (spells/spell-map kw) {:type :spell
                                            :result (spells/spell-map kw)}
                     (monsters/monster-map kw) {:type :monster
                                                :result (monsters/monster-map kw)}
                     (mi/magic-item-map kw) {:type :magic-item
                                             :result (mi/magic-item-map kw)}
                     (= "tavern name" search-text) {:type :tavern-name
                                                    :result (char-rand5e/random-tavern-name)}
                     name-result name-result
                     :else nil)
        filter-xform (filter-by-name-xform search-text :name)
        top-spells (when (>= (count text) 3)
                     (sequence
                      filter-xform
                      spells/spells))
        top-monsters (when (>= (count text) 3)
                       (sequence
                        filter-xform
                        monsters/monsters))]
    (cond-> {}
      top-result (assoc :top-result top-result)
      (seq top-spells) (update :results conj {:type :spell
                                              :results top-spells})
      (seq top-monsters) (update :results conj {:type :monster
                                                :results top-monsters}))))


(reg-event-db
 :set-search-text
 (fn [db [_ search-text]]
   (cond-> db
     true (assoc :search-text search-text
                 :search-results (search-results search-text))
     (s/blank? search-text) (assoc :orcacle-clicked? false))))

(reg-event-db
 :close-orcacle
 (fn [db _]
   (-> db
       (assoc :orcacle-clicked? false)
       (dissoc :search-text))))

#_ ;; never dispatched from UI (note: "orcacle" typo)
  (reg-event-fx
   :open-orcacle-over-character-builder
   (fn []
     {:dispatch-n [[:route routes/dnd-e5-char-builder-route]
                   [:open-orcacle]]}))

(reg-event-db
 :open-orcacle
 (fn [db _]
   (-> db
       (assoc :orcacle-clicked? true)
       (dissoc :search-text))))

(reg-event-db
 ::char5e/set-selected-display-tab
 (fn [db [_ tab]]
   (assoc db ::char5e/selected-display-tab tab)))

(reg-event-db
 ::char5e/set-builder-tab
 (fn [db [_ tab]]
   (assoc db ::char5e/builder-tab tab)))

(reg-event-db
 ::char5e/sort-monsters
 (fn [db [_ sort-criteria sort-direction]]
   (assoc db ::char5e/monster-sort-criteria sort-criteria
          ::char5e/monster-sort-direction sort-direction)))

(reg-event-db
 ::char5e/filter-monsters
 (fn [db [_ filter-text]]
   (assoc db ::char5e/monster-text-filter filter-text)))

;; Filter spell list by name. Computes sorted spells from db directly
;; (avoids subscribe outside reactive context).
(reg-event-db
 ::char5e/filter-spells
 (fn [db [_ filter-text]]
   (let [sorted (compute-sorted-spells db)]
     (assoc db
            ::char5e/spell-text-filter filter-text
            ::char5e/filtered-spells (if (>= (count filter-text) 3)
                                       (filter-spells filter-text sorted)
                                       sorted)))))

;; Filter magic item list by name. Computes sorted items from db directly.
(reg-event-db
 ::char5e/filter-items
 (fn [db [_ filter-text]]
   (let [sorted (compute-sorted-items db)]
     (assoc db
            ::char5e/item-text-filter filter-text
            ::char5e/filtered-items (if (>= (count filter-text) 3)
                                      (filter-items filter-text sorted)
                                      sorted)))))

(reg-event-db
 ::char5e/toggle-selected
 (fn [db [_ id]]
   (update db
           ::char5e/selected
           (fn [s]
             (if (get s id)
               (disj s id)
               (conj (or s #{}) id))))))

(reg-event-db
 ::char5e/toggle-monster-filter-hidden
 (fn [db [_ filter value]]
   (update-in db [::char5e/monster-filter-hidden? filter value] not)))

(defn toggle-set [key set]
  (if (get set key)
    (disj set key)
    (conj (or set #{}) key)))

(defn toggle-character-spell-prepared [class spell-key character]
  (update-in
   character
   [::entity/values
    ::char5e/prepared-spells-by-class
    class]
   (partial toggle-set spell-key)))

(defn toggle-spell-slot-used [level i character]
  (update-in
   character
   [::entity/values
    ::spells/slots-used
    (common5e/slot-level-key level)]
   (partial toggle-set i)))

(defn update-character-fx [db id update-fn]
  (if id
    {:db (update-in
          db
          [::char5e/character-map (js/parseInt id)]
          update-fn)
     ::char5e/save-character-throttled id}
    {:dispatch [:set-character (update-fn (:character db))]}))

(reg-event-fx
 ::char5e/toggle-spell-prepared
 (fn [{:keys [db]} [_ id class spell-key]]
   (let [update-fn (partial toggle-character-spell-prepared class spell-key)]
     (update-character-fx db id update-fn))))

(defn use-spell-slot [lvl character]
  (update-in
   character
   [::entity/values
    ::spells/slots-used
    (common5e/slot-level-key lvl)]
   (fn [level-slots-used]
     (let [first-empty-slot (some
                             (fn [v]
                               (when (not (get level-slots-used v))
                                 v))
                             (range))]
       (conj (or level-slots-used #{})
             first-empty-slot)))))

(reg-event-fx
 ::char5e/use-spell-slot
 (fn [{:keys [db]} [_ id lvl]]
   (let [update-fn (partial use-spell-slot lvl)]
     (update-character-fx db id update-fn))))

(reg-event-fx
 ::char5e/toggle-spell-slot-used
 (fn [{:keys [db]} [_ id level i]]
   (let [update-fn (partial toggle-spell-slot-used level i)]
     (update-character-fx db id update-fn))))

(defn set-current-hit-points [character current-hit-points]
  (assoc-in
   character
   [::entity/values
    ::char5e/current-hit-points]
   current-hit-points))

(defn set-current-xps [character xps]
  (assoc-in
   character
   [::entity/values
    ::char5e/xps]
   (when (not (js/isNaN xps))
     xps)))

(defn set-notes [character notes]
  (assoc-in
   character
   [::entity/values
    ::char5e/notes]
   notes))

(defn add-level [character]
  (let [path [::entity/options
              :class
              0
              ::entity/options
              :levels]
        levels (get-in character path)
        updated (if levels
                  (update-in
                   character
                   path
                   (fn [levels]
                     (conj
                      levels
                      (event-handlers/empty-level (count levels)))))
                  character)
        updated-levels (get-in updated path)]
    updated))

(reg-event-fx
 ::char5e/set-current-hit-points
 (fn [{:keys [db]} [_ id current-hit-points]]
   (update-character-fx db id #(set-current-hit-points % current-hit-points))))

(reg-event-fx
 ::char5e/set-current-xps
 (fn [{:keys [db]} [_ id current-xps]]
   (update-character-fx db id #(set-current-xps % current-xps))))

(reg-event-fx
 ::char5e/add-level
 (fn [{:keys [db]} [_ id]]
   (update-character-fx db id add-level)))

;; Level up a character by id — adds a level and opens the builder.
(reg-event-fx
 ::char5e/level-up
 (fn [{:keys [db]} [_ character-id]]
   {:dispatch-n [[::char5e/add-level character-id]
                 [:set-character (get-in db [::char5e/character-map (js/parseInt character-id)] {})]
                 [:route routes/dnd-e5-char-builder-route]]}))

(reg-event-fx
 ::char5e/set-notes
 (fn [{:keys [db]} [_ id notes]]
   (update-character-fx db id #(set-notes % notes))))

(defn toggle-feature-used [character units nm]
  (-> character
      (update-in
       [::entity/values
        ::char5e/features-used
        units]
       (partial toggle-set nm))
      (dissoc
       [::entity/values
        ::char5e/features-used
        :db/id])))

(reg-event-fx
 ::char5e/toggle-feature-used
 (fn [{:keys [db]} [_ id units nm]]
   (update-character-fx db id #(toggle-feature-used % units nm))))

(defn clear-period [db id update-fn & units]
  (update-character-fx db id #(cond-> %
                                true (update-in
                                      [::entity/values ::char5e/features-used]
                                      (fn [features-used]
                                        (apply dissoc features-used units)))
                                update-fn update-fn)))

(reg-event-fx
 ::char5e/finish-long-rest
 (fn [{:keys [db]} [_ id]]
   (clear-period db
                 id
                 (fn [character]
                   (update
                    character
                    ::entity/values
                    dissoc
                    ::spells/slots-used
                    character
                    ::char5e/current-hit-points ::char5e/max-hit-points))
                 ::units5e/long-rest
                 ::units5e/rest)))

(reg-event-fx
 ::char5e/finish-short-rest-warlock
 (fn [{:keys [db]} [_ id]]
   (clear-period db
                 id
                 (fn [character]
                   (update
                    character
                    ::entity/values
                    dissoc
                    ::spells/slots-used))
                 ::units5e/rest)))

(reg-event-fx
 ::char5e/finish-short-rest
 (fn [{:keys [db]} [_ id]]
   (clear-period db id nil ::units5e/short-rest ::units5e/rest)))

(reg-event-fx
 ::char5e/new-round
 (fn [{:keys [db]} [_ id]]
   (clear-period db id nil ::units5e/round)))

(reg-event-fx
 ::char5e/new-turn
 (fn [{:keys [db]} [_ id]]
   (clear-period db id nil ::units5e/turn)))

(defn vec-conj [v item]
  (conj (or v []) item))

(reg-event-db
 ::char5e/new-custom-item
 (fn [db [_ items-key]]
   (update-in
    db
    [:character
     ::entity/values
     items-key]
    vec-conj
    {::char-equip5e/name "New Custom Item"
     ::char-equip5e/quantity 1
     ::char-equip5e/equipped? true})))

(reg-event-db
 ::char5e/set-custom-item-name
 (fn [db [_ items-key i value]]
   (assoc-in
    db
    [:character
     ::entity/values
     items-key
     i
     ::char-equip5e/name]
    value)))

(reg-event-db
 :toggle-theme
 [user->local-store-interceptor]
 (fn [db _]
   (update-in db [:user-data :theme]
              (fn [theme]
                (if (= theme "light-theme")
                  "dark-theme"
                  "light-theme")))))

(reg-event-db
 ::toggle-class-source-suffix
 [user->local-store-interceptor]
 (fn [db _]
   (update-in db [:user-data :show-class-source-suffix] not)))

#_ ;; never dispatched from UI
  (reg-event-db
   ::mi/set-builder-item
   [magic-item->local-store-interceptor]
   (fn [db [_ magic-item]]
     (assoc db ::mi/builder-item magic-item)))

(reg-event-db
 ::mi/toggle-attunement
 item-interceptors
 (fn [item _]
   (if (::mi/attunement item)
     (dissoc item ::mi/attunement)
     (assoc item ::mi/attunement #{:any}))))

(defn set-any-attunement [attunement]
  (if (empty? attunement)
    (conj attunement :any)
    (disj attunement :any)))

(reg-event-db
 ::mi/toggle-attunement-value
 item-interceptors
 (fn [item [_ value]]
   (update item
           ::mi/attunement
           #(->> %
                 (toggle-set value)
                 set-any-attunement))))

(reg-event-db
 ::mi/add-remote-item
 (fn [db [_ item]]
   (assoc-in db [::mi/remote-items (:db/id item)] item)))

(reg-event-db
 ::mi/set-item-name
 item-interceptors
 (fn [item [_ item-name]]
   (assoc item ::mi/name item-name)))

(reg-event-db
 ::spells/set-spell-prop
 spell-interceptors
 (fn [spell [_ prop-key prop-value]]
   (assoc spell prop-key prop-value)))

(reg-event-db
 ::spells/toggle-spell-prop
 spell-interceptors
 (fn [spell [_ prop-key]]
   (update spell prop-key not)))

(reg-event-db
 ::monsters/set-monster-prop
 monster-interceptors
 (fn [monster [_ prop-key prop-value]]
   (assoc monster prop-key prop-value)))

(reg-event-db
 ::encounters/set-encounter-prop
 encounter-interceptors
 (fn [encounter [_ prop-key prop-value]]
   (assoc encounter prop-key prop-value)))

(reg-event-db
 ::monsters/set-monster-path-prop
 monster-interceptors
 (fn [monster [_ prop-path prop-value]]
   (assoc-in monster prop-path prop-value)))

(reg-event-db
 ::combat/set-monster-hit-points
 combat-interceptors
 (fn [combat [_ {:keys [num monster]} individual-index value]]
   (let [{:keys [die die-count modifier]} (:hit-points monster)]
     (assoc-in combat
               [:monster-data (:key monster) individual-index :hit-points]
               value))))

(reg-event-db
 ::combat/delete-monster-condition
 combat-interceptors
 (fn [combat [_ monster-key individual-index condition-index]]
   (update-in combat
              [:monster-data monster-key individual-index :conditions]
              common/remove-at-index
              condition-index)))

(reg-event-db
 ::combat/set-monster-condition-type
 combat-interceptors
 (fn [combat [_ monster-key individual-index condition-index type]]
   (update-in combat
              [:monster-data monster-key individual-index :conditions]
              (fn [conditions]
                (let [conditions (vec conditions)]
                  (assoc-in conditions [condition-index :type] type))))))

(reg-event-db
 ::combat/set-monster-condition-duration
 combat-interceptors
 (fn [combat [_ monster-key individual-index condition-index duration-type hours]]
   (update-in combat
              [:monster-data monster-key individual-index :conditions]
              (fn [conditions]
                (let [conditions (vec conditions)]
                  (assoc-in conditions
                            [condition-index :duration duration-type]
                            hours))))))

(reg-event-db
 ::combat/randomize-monster-hit-points
 combat-interceptors
 (fn [combat [_ {:keys [num monster]} monster-map]]
   (let [{:keys [die die-count modifier]} (:hit-points monster)]
     (update-in combat
                [:monster-data (:key monster)]
                (fn [monster-data]
                  (reduce
                   (fn [m x]
                     (assoc-in m
                               [x :hit-points]
                               (dice/dice-roll {:num die-count
                                                :sides die
                                                :modifier modifier})))
                   monster-data
                   (range num)))))))

(reg-event-db
 ::combat/set-combat-prop
 combat-interceptors
 (fn [combat [_ prop-key prop-value]]
   (assoc combat prop-key prop-value)))

(defn nil-or-zero? [v]
  (or (nil? v) (zero? v)))

(defn zero-duration? [{{:keys [hours minutes rounds]} :duration}]
  (and (nil-or-zero? hours)
       (nil-or-zero? minutes)
       (nil-or-zero? rounds)))

(defn decrement-duration [condition]
  (update
   condition
   :duration
   (fn [{:keys [hours minutes rounds] :as duration}]
     (when (not (zero-duration? condition))
       (let [total-rounds (+ rounds
                             (* common/rounds-per-minute minutes)
                             (* common/rounds-per-hour hours))
             next-total-rounds (dec total-rounds)
             next-hours (int (/ next-total-rounds common/rounds-per-hour))
             remaining (rem next-total-rounds common/rounds-per-hour)
             next-minutes (int (/ remaining common/rounds-per-minute))
             next-rounds (rem remaining common/rounds-per-minute)]
         {:hours next-hours
          :minutes next-minutes
          :rounds next-rounds})))))

(defn update-individual-monster [data monster-index individual-data]
  (let [current-conditions (:conditions individual-data)
        decremented-conditions (map decrement-duration current-conditions)
        {new-conditions false removed-conditions true}
        (group-by zero-duration? decremented-conditions)]
    (assoc
     data
     monster-index
     (assoc
      individual-data
      :conditions
      new-conditions
      :removed-conditions
      removed-conditions))))

(defn update-monster-data-item [monster-data monster-kw data]
  (assoc
   monster-data
   monster-kw
   (reduce-kv
    update-individual-monster
    data
    data)))

(defn update-monster-data [monster-data]
  (reduce-kv
   update-monster-data-item
   monster-data
   monster-data))

(defn update-conditions [combat]
  (update combat
          :monster-data
          update-monster-data))

(reg-event-fx
 ::combat/next-initiative
 (fn [{:keys [db]} [_ monster-map]]
   (let [combat (::combat/tracker-item db)
         initiatives (->> combat
                          :initiative
                          vals
                          (mapcat vals)
                          (sort >))
         current-initiative (:current-initiative combat)
         next-initiative (if current-initiative
                           (or (first (drop-while #(>= % current-initiative) initiatives))
                               (first initiatives))
                           (second initiatives))
         round (get combat :round 1)
         next-round? (and current-initiative
                          (> next-initiative current-initiative))
         updated (cond-> combat
                   true (assoc :current-initiative next-initiative)
                   next-round? (assoc :round (inc round))
                   next-round? update-conditions)
         removed-conditions (when next-round?
                              (filter
                               (comp seq :removed-conditions)
                               (flatten
                                (map
                                 (fn [[monster-kw individuals]]
                                   (map
                                    (fn [[individual-index {:keys [removed-conditions]}]]
                                      {:type :monster
                                       :index individual-index
                                       :name (get-in monster-map [monster-kw :name])
                                       :removed-conditions (map :type removed-conditions)})
                                    individuals))
                                 (:monster-data updated)))))]
     {:dispatch-n (cond-> [[::combat/set-combat updated]]
                    (seq removed-conditions)
                    (conj [:show-message
                           [:div.m-t-5.f-w-b.f-s-18
                            (doall
                             (map-indexed
                              (fn [i {:keys [name index removed-conditions]}]
                                ^{:key i}
                                [:div.m-b-5 (str name " #" (inc index) " is no longer " (common/list-print (map common/kw-to-name removed-conditions) "or") ".")])
                              removed-conditions))]]))})))

(reg-event-db
 ::encounters/set-encounter-path-prop
 encounter-interceptors
 (fn [encounter [_ prop-path prop-value]]
   (assoc-in encounter prop-path prop-value)))

(reg-event-db
 ::combat/delete-party
 combat-interceptors
 (fn [combat [_ index]]
   (update combat :parties common/remove-at-index index)))

(reg-event-db
 ::combat/delete-encounter
 combat-interceptors
 (fn [combat [_ index]]
   (update combat :encounters common/remove-at-index index)))

(reg-event-db
 ::combat/delete-character
 combat-interceptors
 (fn [combat [_ index]]
   (update combat :characters common/remove-at-index index)))

(reg-event-db
 ::combat/delete-monster
 combat-interceptors
 (fn [combat [_ index]]
   (update combat :monsters common/remove-at-index index)))

(reg-event-db
 ::combat/set-combat-path-prop
 combat-interceptors
 (fn [combat [_ path-prop prop-value]]
   ;; Guard: ensure combat is a valid map with vector collections.
   ;; If the path interceptor extracts nil (key missing from db), bare
   ;; assoc-in creates maps for integer keys instead of vectors,
   ;; corrupting localStorage and failing the spec on next load.
   (assoc-in (or combat default-combat) path-prop prop-value)))

(reg-event-db
 ::encounters/delete-creature
 encounter-interceptors
 (fn [encounter [_ index]]
   (update encounter :creatures common/remove-at-index index)))

(reg-event-db
 ::class5e/set-class-path-prop
 class-interceptors
 (fn [class [_ prop-path prop-value prop-path-2 prop-value-2]]
   ;; Only apply second assoc-in if prop-path-2 is provided
   ;; (prevents {nil nil} corruption when called with 2 args)
   (cond-> class
     true (assoc-in prop-path prop-value)
     prop-path-2 (assoc-in prop-path-2 prop-value-2))))

(reg-event-db
 ::selections5e/set-selection-path-prop
 selection-interceptors
 (fn [selection [_ prop-path prop-value]]
   (assoc-in selection prop-path prop-value)))

(reg-event-db
 ::selections5e/delete-option
 selection-interceptors
 (fn [selection [_ index]]
   (update selection :options common/remove-at-index index)))

;; Append a new option to the selection with a unique default name ("Option N").
;; Starts from count+1, increments if that name already exists (e.g., after deletions).
(reg-event-db
 ::selections5e/add-option
 selection-interceptors
 (fn [selection]
   (let [existing (set (map :name (:options selection)))
         idx (inc (count (:options selection)))
         idx (if (contains? existing (str "Option " idx))
               (loop [n idx] (if (contains? existing (str "Option " n)) (recur (inc n)) n))
               idx)]
     (update selection :options conj {:name (str "Option " idx)}))))

(reg-event-db
 ::class5e/set-subclass-path-prop
 subclass-interceptors
 (fn [subclass [_ prop-path prop-value]]
   (assoc-in subclass prop-path prop-value)))

(reg-event-db
 ::race5e/set-race-path-prop
 race-interceptors
 (fn [race [_ prop-path prop-value]]
   (assoc-in race prop-path prop-value)))

(reg-event-db
 ::race5e/set-subrace-path-prop
 subrace-interceptors
 (fn [subrace [_ prop-path prop-value]]
   (assoc-in subrace prop-path prop-value)))

(reg-event-db
 ::bg5e/set-background-prop
 background-interceptors
 (fn [background [_ prop-key prop-value]]
   (assoc background prop-key prop-value)))

(reg-event-db
 ::langs5e/set-language-prop
 language-interceptors
 (fn [language [_ prop-key prop-value]]
   (assoc language prop-key prop-value)))

(reg-event-db
 ::class5e/set-invocation-prop
 invocation-interceptors
 (fn [invocation [_ prop-key prop-value]]
   (assoc invocation prop-key prop-value)))

(reg-event-db
 ::class5e/set-boon-prop
 boon-interceptors
 (fn [boon [_ prop-key prop-value]]
   (assoc boon prop-key prop-value)))

(reg-event-db
 ::selections5e/set-selection-prop
 selection-interceptors
 (fn [selection [_ prop-key prop-value]]
   (assoc selection prop-key prop-value)))

(reg-event-db
 ::race5e/set-race-prop
 race-interceptors
 (fn [race [_ prop-key prop-value]]
   (assoc race prop-key prop-value)))

(reg-event-db
 ::race5e/set-subrace-prop
 subrace-interceptors
 (fn [subrace [_ prop-key prop-value]]
   (assoc subrace prop-key prop-value)))

(reg-event-db
 ::class5e/set-subclass-prop
 subclass-interceptors
 (fn [subclass [_ prop-key prop-value]]
   (assoc subclass prop-key prop-value)))

(reg-event-db
 ::class5e/toggle-save-prof
 class-interceptors
 (fn [class [_ key]]
   (update-in class
              [:profs :save]
              (fn [saves]
                (if (key saves)
                  (dissoc saves key)
                  (assoc saves key true))))))

(reg-event-db
 ::class5e/toggle-ability-increase-level
 class-interceptors
 (fn [class [_ level]]
   (update class
           :ability-increase-levels
           (fn [levels]
             (let [levels-set (into (sorted-set) levels)]
               (vec
                (if (levels-set level)
                  (disj levels-set level)
                  (conj levels-set level))))))))

(reg-event-db
 ::class5e/set-class-prop
 class-interceptors
 (fn [class [_ prop-key prop-value]]
   (assoc class prop-key prop-value)))

(reg-event-db
 ::class5e/toggle-class-spell-list
 class-interceptors
 (fn [class [_ level spell-kw]]
   (update-in class
              [:spellcasting :spell-list level]
              (fn [spells]
                (let [spells (or spells #{})]
                  (if (spells spell-kw)
                    (disj spells spell-kw)
                    (conj spells spell-kw)))))))

(reg-event-db
 ::class5e/toggle-subclass-spellcasting
 subclass-interceptors
 (fn [subclass]
   (if (:spellcasting subclass)
     (dissoc subclass :spellcasting)
     (assoc subclass :spellcasting {:level-factor 3}))))

(reg-event-db
 ::class5e/set-class-spell
 subclass-interceptors
 (fn [subclass [_ class-spells-key level index spell-kw]]
   (assoc-in subclass [class-spells-key level index] spell-kw)))

#_ ;; never dispatched from UI
  (reg-event-db
   ::class5e/set-spell-list
   subclass-interceptors
   (fn [subclass [_ class-kw]]
     (assoc-in subclass [:spellcasting :spell-list] class-kw)))

(reg-event-db
 ::feats5e/set-feat-prop
 feat-interceptors
 (fn [feat [_ prop-key prop-value]]
   (assoc feat prop-key prop-value)))

#_ ;; never dispatched from UI
  (reg-event-db
   ::bg5e/set-feature-prop
   background-interceptors
   (fn [background [_ prop-key prop-value]]
     (assoc-in background [:traits 0 prop-key] prop-value)))

(reg-event-db
 ::feats5e/toggle-feat-prop
 feat-interceptors
 (fn [feat [_ key]]
   ;; toggle-in, not update-in/not: [:props key] may hold a MAP (a sibling skill/
   ;; save grid) that bare `not` would collapse to false and destroy.
   (common/toggle-in feat [:props key])))

#_ ;; never dispatched from UI — feat builder uses toggle-feat-prop instead
  (reg-event-db
   ::feats5e/toggle-feat-selection
   feat-interceptors
   (fn [feat [_ key]]
     (update-in feat [:selections key] not)))

(reg-event-db
 ::feats5e/toggle-feat-value-prop
 feat-interceptors
 (fn [feat [_ key num]]
   (update feat :props (fn [m]
                         (if (= (get m key) num)
                           (dissoc m key)
                           (assoc m key num))))))

(reg-event-db
 ::race5e/toggle-race-prop
 race-interceptors
 (fn [race [_ key]]
   (common/toggle-in race [:props key])))

(reg-event-db
 ::race5e/toggle-subrace-value-prop
 subrace-interceptors
 (fn [subrace [_ key num]]
   (update subrace :props (fn [m]
                            (if (= (get m key) num)
                              (dissoc m key)
                              (assoc m key num))))))

#_ ;; never dispatched — class/subclass builder UI not wired for value-prop toggles
  (reg-event-db
   ::class5e/toggle-subclass-value-prop
   subclass-interceptors
   (fn [subclass [_ key num]]
     (update subclass :props (fn [m]
                               (if (= (get m key) num)
                                 (dissoc m key)
                                 (assoc m key num))))))

#_ ;; never dispatched — class builder UI not wired for value-prop toggles
  (reg-event-db
   ::class5e/toggle-class-value-prop
   class-interceptors
   (fn [class [_ key num]]
     (update class :props (fn [m]
                            (if (= (get m key) num)
                              (dissoc m key)
                              (assoc m key num))))))

(reg-event-db
 ::feats5e/toggle-feat-map-prop
 feat-interceptors
 (fn [feat [_ key value]]
   (common/toggle-in feat [:props key value])))

(reg-event-db
 ::race5e/toggle-subrace-map-prop
 subrace-interceptors
 (fn [subrace [_ key value]]
   (common/toggle-in subrace [:props key value])))

(reg-event-db
 ::monsters/toggle-monster-map-prop
 monster-interceptors
 (fn [monster [_ key value]]
   (common/toggle-in monster [:props key value])))

(reg-event-db
 ::class5e/toggle-class-path-prop
 class-interceptors
 (fn [class [_ prop-path prop-value]]
   ;; toggle-in guards against prop-path landing on a map + self-heals a stray
   ;; false intermediate (corruption).
   (common/toggle-in class prop-path)))

#_ ;; never dispatched — class builder UI not wired for prof toggles
  (reg-event-db
   ::class5e/toggle-class-prof
   class-interceptors
   (fn [class [_ prop-path]]
     (let [v (get-in class prop-path)]
       ;; for classes, the value for a prof signals whether
       ;; it only applies to the first class a character takes
       (if (= v false)
         (common/dissoc-in class prop-path)
         (assoc-in class prop-path false)))))

(reg-event-db
 ::class5e/toggle-subclass-path-prop
 subclass-interceptors
 (fn [subclass [_ prop-path prop-value]]
   (common/toggle-in subclass prop-path)))

(reg-event-db
 ::race5e/toggle-race-path-prop
 race-interceptors
 (fn [race [_ prop-path prop-value]]
   (common/toggle-in race prop-path)))

(reg-event-db
 ::race5e/toggle-subrace-path-prop
 subrace-interceptors
 (fn [subrace [_ prop-path prop-value]]
   (common/toggle-in subrace prop-path)))

(reg-event-db
 ::race5e/toggle-race-map-prop
 race-interceptors
 (fn [race [_ key value]]
   (common/toggle-in race [:props key value])))

#_ ;; never dispatched — class builder UI not wired for subclass map-prop toggles
  (reg-event-db
   ::class5e/toggle-subclass-map-prop
   subclass-interceptors
   (fn [subclass [_ key value]]
     (update-in subclass [:props key value] not)))

#_ ;; never dispatched — class builder UI not wired for class map-prop toggles
  (reg-event-db
   ::class5e/toggle-class-map-prop
   class-interceptors
   (fn [class [_ key value]]
     (update-in class [:props key value] not)))

#_ ;; never dispatched — background builder UI not wired for map-prop toggles
  (reg-event-db
   ::bg5e/toggle-background-map-prop
   background-interceptors
   (fn [background [_ key value]]
     (update-in background [:props key value] not)))



(reg-event-db
 ::feats5e/toggle-feat-ability-increase
 feat-interceptors
 (fn [feat [_ ability-key]]
   (update feat :ability-increases (fn [s]
                                     (if (s ability-key)
                                       (disj s ability-key)
                                       (conj s ability-key))))))

(reg-event-db
 ::feats5e/toggle-ability-prereq
 feat-interceptors
 (fn [feat [_ ability-key]]
   (update feat :prereqs (fn [s]
                           (if (s ability-key)
                             (disj s ability-key)
                             (conj s ability-key))))))

(reg-event-db
 ::feats5e/toggle-path-prereq
 feat-interceptors
 (fn [feat [_ path]]
   (common/toggle-in feat (cons :path-prereqs path))))

(reg-event-db
 ::feats5e/toggle-spellcasting-prereq
 feat-interceptors
 (fn [feat]
   (update feat :prereqs (fn [s]
                           (if (s :spellcasting)
                             (disj s :spellcasting)
                             (conj s :spellcasting))))))


(reg-event-db
 ::bg5e/set-background-gold
 background-interceptors
 (fn [background [_ amount]]
   (assoc-in background [:treasure :gp] (js/parseInt amount))))

(reg-event-db
 ::race5e/set-race-speed
 race-interceptors
 (fn [race [_ v]]
   (assoc race :speed (js/parseInt v))))

(reg-event-db
 ::race5e/set-race-value-prop
 race-interceptors
 (fn [race [_ k v]]
   (assoc-in race [:props k] v)))

(reg-event-db
 ::race5e/set-subrace-speed
 subrace-interceptors
 (fn [subrace [_ v]]
   (assoc subrace :speed (js/parseInt v))))

(reg-event-db
 ::race5e/set-race-ability-increase
 race-interceptors
 (fn [race [_ ability-kw bonus]]
   (assoc-in race [:abilities ability-kw] (js/parseInt bonus))))

(reg-event-db
 ::race5e/set-subrace-ability-increase
 subrace-interceptors
 (fn [subrace [_ ability-kw bonus]]
   (assoc-in subrace [:abilities ability-kw] (js/parseInt bonus))))

(reg-event-db
 ::spells/set-spell-level
 spell-interceptors
 (fn [spell [_ level]]
   (assoc spell :level (js/parseInt level))))

(reg-event-db
 ::spells/toggle-component
 spell-interceptors
 (fn [spell [_ component]]
   (common/toggle-in spell [:components component])))

(reg-event-db
 ::bg5e/toggle-skill-prof
 background-interceptors
 (fn [background [_ key]]
   (if (get-in background [:profs :skill key])
     (update-in background [:profs :skill] dissoc key)
     (assoc-in background [:profs :skill key] true))))

(reg-event-db
 ::race5e/toggle-language
 race-interceptors
 (fn [race [_ nm]]
   (if (get-in race [:languages nm])
     (update race :languages disj nm)
     (update race :languages conj nm))))

(reg-event-db
 ::bg5e/toggle-tool-prof
 background-interceptors
 (fn [background [_ key]]
   (if (get-in background [:profs :tool key])
     (update-in background [:profs :tool] dissoc key)
     (assoc-in background [:profs :tool key] true))))

(reg-event-db
 ::bg5e/toggle-starting-equipment
 background-interceptors
 (fn [background [_ key]]
   (if (get-in background [:equipment key])
     (update-in background [:equipment] dissoc key)
     (assoc-in background [:equipment key] 1))))

(reg-event-db
 ::bg5e/toggle-starting-equipment-choice
 background-interceptors
 (fn [background [_ equipment equipment-name]]
   (letfn [(find-equipment [{:keys [name]}]
             (= name equipment-name))]
     (if (some
          find-equipment
          (:equipment-choices background))
       (update background :equipment-choices #(remove find-equipment %))
       (update background :equipment-choices conj {:name equipment-name
                                                   :options (zipmap
                                                             (map
                                                              :key
                                                              equipment)
                                                             (repeat 1))})))))

(reg-event-db
 ::bg5e/toggle-choice-tool-prof
 background-interceptors
 (fn [background [_ key num]]
   (if (= num (get-in background [:profs :tool-options key]))
     (update-in background [:profs :tool-options] dissoc key)
     (assoc-in background [:profs :tool-options key] num))))

(reg-event-db
 ::bg5e/toggle-choice-language-prof
 background-interceptors
 (fn [background [_ num]]
   (if (= num (get-in background [:profs :language-options :choose]))
     (update background :profs dissoc  :language-options)
     (assoc-in background [:profs :language-options] {:choose num :options {:any true}}))))

(reg-event-db
 ::spells/toggle-spell-list
 spell-interceptors
 (fn [spell [_ class-key]]
   (common/toggle-in spell [:spell-lists class-key])))

(reg-event-db
 ::spells/set-material-component
 spell-interceptors
 (fn [spell [_ material-component]]
   (assoc-in spell [:components :material-component] material-component)))

;;;; Item Builder

(reg-event-db
 ::mi/set-item-description
 item-interceptors
 (fn [item [_ item-description]]
   (assoc item ::mi/description item-description)))

(reg-event-db
 ::mi/set-item-type
 item-interceptors
 (fn [item [_ item-type-str]]
   (-> item
       (assoc ::mi/type (keyword item-type-str))
       (dissoc ::mi/subtypes))))

(reg-event-db
 ::mi/set-item-weapon-type
 item-interceptors
 (fn [item [_ item-type-str]]
   (assoc item ::weapons/type (keyword item-type-str))))

(reg-event-db
 ::mi/set-item-damage-type
 item-interceptors
 (fn [item [_ item-type-str]]
   (assoc item ::weapons/damage-type (keyword item-type-str))))

(reg-event-db
 ::mi/set-item-melee-ranged
 item-interceptors
 (fn [item [_ item-type-str]]
   (let [kw (keyword item-type-str)]
     (assoc item
            ::weapons/melee? (= kw :melee)
            ::weapons/ranged? (= kw :ranged)))))

(reg-event-db
 ::mi/set-item-range-min
 item-interceptors
 (fn [item [_ v]]
   (assoc-in item [::weapons/range ::weapons/min] v)))

(reg-event-db
 ::mi/set-item-range-max
 item-interceptors
 (fn [item [_ v]]
   (assoc-in item [::weapons/range ::weapons/max] v)))

(reg-event-db
 ::mi/set-item-damage-die-count
 item-interceptors
 (fn [item [_ v]]
   (assoc item ::weapons/damage-die-count v)))

(reg-event-db
 ::mi/set-item-damage-die
 item-interceptors
 (fn [item [_ v]]
   (assoc item ::weapons/damage-die v)))

(reg-event-db
 ::mi/set-item-versatile-damage-die-count
 item-interceptors
 (fn [item [_ v]]
   (assoc-in item [::weapons/versatile ::weapons/damage-die-count] v)))

(reg-event-db
 ::mi/set-item-versatile-damage-die
 item-interceptors
 (fn [item [_ v]]
   (assoc-in item [::weapons/versatile ::weapons/damage-die] v)))

(reg-event-db
 ::mi/toggle-item-finesse?
 item-interceptors
 (fn [item _]
   (update item ::weapons/finesse? not)))

(reg-event-db
 ::mi/toggle-item-reach?
 item-interceptors
 (fn [item _]
   (update item ::weapons/reach? not)))

(reg-event-db
 ::mi/toggle-item-two-handed?
 item-interceptors
 (fn [item _]
   (update item ::weapons/two-handed? not)))

(reg-event-db
 ::mi/toggle-item-heavy?
 item-interceptors
 (fn [item _]
   (update item ::weapons/heavy? not)))

(reg-event-db
 ::mi/toggle-item-light?
 item-interceptors
 (fn [item _]
   (update item ::weapons/light? not)))

(reg-event-db
 ::mi/toggle-item-thrown?
 item-interceptors
 (fn [item _]
   (update item ::weapons/thrown? not)))

(reg-event-db
 ::mi/toggle-item-ammunition?
 item-interceptors
 (fn [item _]
   (update item ::weapons/ammunition? not)))

(reg-event-db
 ::mi/toggle-item-special?
 item-interceptors
 (fn [item _]
   (update item ::weapons/special? not)))

(reg-event-db
 ::mi/toggle-item-loading?
 item-interceptors
 (fn [item _]
   (update item ::weapons/loading? not)))

(reg-event-db
 ::mi/toggle-item-versatile?
 item-interceptors
 (fn [item _]
   (if (::weapons/versatile item)
     (dissoc item ::weapons/versatile)
     (assoc item ::weapons/versatile {}))))

(defn set-value [item kw value]
  (if value
    (assoc item kw value)
    (dissoc item kw)))

(reg-event-db
 ::mi/set-item-rarity
 item-interceptors
 (fn [item [_ item-type-str]]
   (assoc item ::mi/rarity (keyword item-type-str))))

(reg-event-db
 ::mi/set-item-damage-bonus
 item-interceptors
 (fn [item [_ bonus]]
   (set-value item ::mi/magical-damage-bonus bonus)))

(reg-event-db
 ::mi/set-item-attack-bonus
 item-interceptors
 (fn [item [_ bonus]]
   (set-value item ::mi/magical-attack-bonus bonus)))

(reg-event-db
 ::mi/set-item-ac-bonus
 item-interceptors
 (fn [item [_ bonus]]
   (set-value item ::mi/magical-ac-bonus bonus)))

#_ ;; orphaned re-export aliases — all callers use event-utils/ directly now
  (def mod-cfg event-utils/mod-cfg)
#_(def mod-key event-utils/mod-key)
#_(def compare-mod-keys event-utils/compare-mod-keys)
#_(def default-mod-set event-utils/default-mod-set)

(doseq [toggle-mod [:damage-resistance :damage-vulnerability :damage-immunity :condition-immunity]]
  (reg-event-db
   (keyword "orcpub.dnd.e5.magic-items" (str "toggle-" (name toggle-mod)))
   item-interceptors
   (fn [item [_ type]]
     (update-in item
                [::mi/internal-modifiers
                 toggle-mod
                 type]
                not))))

(reg-event-db
 ::mi/set-item
 item-interceptors
 (fn [_ [_ item]]
   item))

(reg-event-db
 ::e5/set-plugins
 plugins-interceptors
 (fn [_ [_ plugins]]
   plugins))

;; Persist-then-report: write the library to localStorage FIRST and only fire the
;; success dispatch if the write actually stuck. A failed write (typically quota)
;; already surfaces ::e5/plugins-save-failed from plugins->local-store, so on
;; failure we simply withhold the "success" message rather than claiming a save
;; that isn't there — the content would otherwise vanish on the next refresh.
;; Used by the import paths so "✅ imported" can't precede a failed save.
(reg-event-fx
 ::e5/store-plugins
 (fn [{:keys [db]} [_ plugins on-success]]
   (let [ok? (plugins->local-store plugins)]
     (cond-> {:db (assoc db :plugins plugins)}
       (and ok? on-success) (assoc :dispatch on-success)))))

;; `plugins->local-store` dispatches this when the localStorage write
;; fails (typically a full quota). The save lives in memory but would vanish on
;; refresh, so surface it loudly and offer the unvalidated full backup
;; (`::e5/emergency-export-raw` with nil = whole library) as an immediate out.
(reg-event-fx
 ::e5/plugins-save-failed
 (fn [_ _]
   {:dispatch [:show-error-message
               [:div
                [:div.f-w-b "Couldn't save to browser storage — it may be full."]
                [:div.m-t-5
                 "Your latest change is in memory but will be lost on refresh."]
                [:div.m-t-10
                 [:span.pointer.underline.f-w-b
                  {:on-click #(dispatch [::e5/emergency-export-raw nil])}
                  "Download a full backup now"]]]
               builder-error-ttl]}))

;; Repair a quarantined source and merge it back into the live library.
;; rekey-plugin re-derives the map key from the fixed name (the keyword-trap case).
;; Atomic and PERSISTED (unlike the export auto-fix, which only rewrote the file):
;; the source lands in :plugins and leaves plugins:rejected together, or — if still
;; invalid — nothing changes and the user is told why.
(reg-event-fx
 ::e5/repair-quarantined-source
 (fn [{:keys [db]} [_ source-name edits]]
   (let [rejected (get-rejected-plugins)
         bad (get rejected source-name)]
     (cond
       (nil? bad)
       {:dispatch [:show-error-message
                   (str "No quarantined source named \"" source-name "\" to repair.")]}

       :else
       ;; Apply the user's edits, dummy-fill remaining gaps, re-key any keyword-trap
       ;; items — then salvage PER ENTRY: valid entries rejoin the live source, the
       ;; still-broken ones stay set aside. (Whole-source all-or-nothing before this
       ;; meant one stubborn entry blocked restoring the rest.)
       (let [fixed (-> (orcbrew-val/apply-user-edits-to-plugin bad source-name (or edits {}))
                       ;; Auto-fix present-but-invalid names (e.g. "@@@") to valid
                       ;; placeholders so 'Fix & Restore' works in one click, not
                       ;; only after the user hand-types a name.
                       (orcbrew-val/coerce-invalid-names)
                       (e5/rekey-plugin))
             {kept-items :kept still-bad :rejected}
             (e5/salvage-plugin-items content-specs/valid-item-for-load? fixed)
             new-rejected (if (seq still-bad)
                            (assoc rejected source-name still-bad)
                            (dissoc rejected source-name))
             live (if (seq kept-items)
                    (e5/merge-all-plugins (:plugins db) {source-name kept-items})
                    (:plugins db))
             count-items (fn [pl] (reduce + 0 (for [[ct items] pl
                                                    :when (and (qualified-keyword? ct) (map? items))]
                                                (count items))))
             n-fixed (count-items kept-items)
             n-left (count-items still-bad)]
         ;; Persist live + quarantine together so they never disagree, and show now.
         (plugins->local-store live)
         (set-rejected-plugins new-rejected)
         {:db (-> db
                  (assoc :plugins live)
                  (assoc :quarantined-plugins new-rejected))
          :dispatch [(if (pos? n-fixed) :show-warning-message :show-error-message)
                     (cond
                       (and (pos? n-fixed) (zero? n-left))
                       (str "Restored " n-fixed " entr" (if (= 1 n-fixed) "y" "ies")
                            " from \"" source-name "\" to My Content.")
                       (pos? n-fixed)
                       (str "Restored " n-fixed "; " n-left " still need a name "
                            "starting with a letter and an option source.")
                       :else
                       (str "Couldn't restore \"" source-name "\" yet — each entry "
                            "needs a name starting with a letter and an option source."))]})))))

;; Permanently discard a quarantined source the user can't (or doesn't want to)
;; repair — e.g. a stale entry from an earlier bad import that no longer
;; corresponds to anything in the library and so never self-clears. Drops it from
;; BOTH the persisted rejected store and the reactive panel. Destructive: this is
;; the only copy, so the panel confirms (and offers raw export) before dispatching.
(reg-event-fx
 ::e5/discard-quarantined-source
 (fn [{:keys [db]} [_ source-name]]
   (set-rejected-plugins (dissoc (get-rejected-plugins) source-name))
   {:db (update db :quarantined-plugins dissoc source-name)
    :dispatch [:show-warning-message
               (str "Discarded quarantined source \"" source-name "\".")]}))

;; ============================================================================
;; Export Validation + File Save
;; ============================================================================

(defn errors->str
  "Normalize a validation result's :errors (which may be a pre-formatted string,
   a seq of strings, or nil) into a single clean string for console output.
   Avoids the munging that happens when a CLJS collection is passed straight to
   js/console.error as a trailing argument."
  [errors]
  (cond
    (nil? errors) ""
    (string? errors) errors
    (sequential? errors) (s/join "\n\n" (map str errors))
    :else (str errors)))

(defn serialize-orcbrew
  "Pure serialize of homebrew content to .orcbrew text — no side effects, so it's
   unit-testable and shared by every export path. pretty-print? is opt-in: pprint
   inflates 3–5MB files to ~10–20MB and can freeze the UI."
  [data & {:keys [pretty-print?]}]
  (if pretty-print?
    (with-out-str (pprint/pprint data))
    (str data)))

(defn- save-orcbrew-blob!
  "Serialize plugin data to a .orcbrew file and trigger download. The only side
   effect; serialization lives in the pure `serialize-orcbrew`."
  [filename data & {:keys [pretty-print?]}]
  (let [content (serialize-orcbrew data :pretty-print? pretty-print?)
        blob (js/Blob.
              (clj->js [content])
              (clj->js {:type "text/plain;charset=utf-8"}))]
    (js/saveAs blob filename)))

(defn reg-export-draft
  "Register a builder-level 'export draft' event: dump the in-progress builder-item
   to a .orcbrew with NO validation, so WIP that won't save/export the normal
   (validated) way can always be rescued. Serialized as a normal single-source
   plugin {source {content-type {key item}}}, so the draft re-imports like any
   orcbrew (import then surfaces/fills whatever still needs fixing)."
  [event-key item-key plugin-key]
  (reg-event-fx
   event-key
   (fn [{:keys [db]} _]
     (let [{:keys [name option-pack] :as item} (item-key db)]
       (if (nil? item)
         {:dispatch [:show-error-message "Nothing in the builder to export yet."]}
         (let [src      (if (s/blank? option-pack) "Draft Export" option-pack)
               item-kw  (if (s/blank? name) :draft-item (common/name-to-kw name))
               plugin   {src {plugin-key {item-kw (assoc item :key item-kw)}}}
               filename (str (if (s/blank? name) "draft" name) "-draft.orcbrew")]
           (save-orcbrew-blob! filename plugin)
           {:dispatch [:show-warning-message
                       (str "Draft exported to '" filename
                            "' (unvalidated WIP). Keep it safe; re-import to continue.")]}))))))

(defn draft-event-for
  "The 'Export draft' event key for a builder's save event (save-event name + a
   \"-draft\" suffix). Derived, not hand-assigned, so builder-page can wire the
   button from the save event it already has."
  [save-event]
  (keyword (namespace save-event) (str (name save-event) "-draft")))

(def builder-drafts
  "Every homebrew builder's Export-draft hatch: save event -> [builder-item sub
   key, content-type it exports under]. The loop below registers a draft event for
   each; views/builder-page derives the same event via draft-event-for."
  {::spells/save-spell           [::spells/builder-item ::e5/spells]
   ::monsters/save-monster       [::monsters/builder-item ::e5/monsters]
   ::encounters/save-encounter   [::encounters/builder-item ::e5/encounters]
   ::bg5e/save-background        [::bg5e/builder-item ::e5/backgrounds]
   ::langs5e/save-language       [::langs5e/builder-item ::e5/languages]
   ::class5e/save-invocation     [::class5e/invocation-builder-item ::e5/invocations]
   ::class5e/save-boon           [::class5e/boon-builder-item ::e5/boons]
   ::selections5e/save-selection [::selections5e/builder-item ::e5/selections]
   ::feats5e/save-feat           [::feats5e/builder-item ::e5/feats]
   ::race5e/save-race            [::race5e/builder-item ::e5/races]
   ::race5e/save-subrace         [::race5e/subrace-builder-item ::e5/subraces]
   ::class5e/save-subclass       [::class5e/subclass-builder-item ::e5/subclasses]
   ::class5e/save-class          [::class5e/builder-item ::e5/classes]})

(doseq [[save-event [item-key content-type]] builder-drafts]
  (reg-export-draft (draft-event-for save-event) item-key content-type))

(defn- log-export-warnings [plugin-name validation]
  (when (seq (:warnings validation))
    (js/console.warn
     (str "Export warnings for \"" plugin-name "\":\n  "
          (s/join "\n  " (map str (:warnings validation)))))))

(defn- validate-and-show-modal-or-export
  "Shared validation logic for both export-plugin and export-plugin-pretty-print.
   Validates the plugin, then either shows the modal (missing fields), exports
   directly (valid), or shows an error (spec failure)."
  [plugin-name plugin {:keys [pretty-print?]}]
  (let [validation (orcbrew-val/validate-before-export plugin)]
    (cond
      (:has-missing-required-fields validation)
      (do
        (js/console.warn
         (str "Export: missing required fields in \"" plugin-name "\":\n"
              (orcbrew-val/format-export-validation-for-log validation)))
        {:dispatch [:show-export-warning-modal
                    {:mode :single
                     :plugins [{:name plugin-name
                                :plugin plugin
                                :issues (:missing-fields-issues validation)}]
                     :warnings (:warnings validation)
                     :pretty-print? pretty-print?}]})

      (:valid validation)
      (do
        (log-export-warnings plugin-name validation)
        ;; Strip meaningless blanks (false/nil/empty) on normal export.
        (save-orcbrew-blob! (str plugin-name ".orcbrew")
                            (orcbrew-val/strip-export-blanks plugin)
                            :pretty-print? pretty-print?)
        (if (seq (:warnings validation))
          {:dispatch [:show-warning-message
                      (str "Plugin '" plugin-name "' exported with warnings. Check console for details.")]}
          {}))

      :else
      ;; Hard spec check failed. Surface a raw, unvalidated escape hatch alongside
      ;; the error so the user can always get their content out.
      (do
        (js/console.error (str "Export validation failed for \"" plugin-name "\":\n"
                               (orcbrew-val/format-export-validation-for-log validation)))
        {:dispatch [:show-error-message
                    [:div
                     [:div (str "Cannot export '" plugin-name
                                "' - contains invalid data. Check console for details.")]
                     [:div.m-t-5
                      [:span.pointer.underline.f-w-b
                       {:on-click #(dispatch [::e5/emergency-export-raw plugin-name])}
                       "Download raw backup instead"]]]]}))))

(reg-event-fx
 ::e5/export-plugin
 (fn [_ [_ name plugin]]
   (validate-and-show-modal-or-export name plugin {})))

(defn select-emergency-export
  "Pick the filename + data for an emergency raw export: just the named source if
   it exists in `plugins`, else the whole library. Pure — unit-testable without
   the DOM/file system."
  [plugins plugin-name]
  (if (and plugin-name (contains? plugins plugin-name))
    [(str plugin-name ".orcbrew") (get plugins plugin-name)]
    ["orcpub-EMERGENCY-backup.orcbrew" plugins]))

(reg-event-fx
 ::e5/emergency-export-raw
 (fn [{:keys [db]} [_ plugin-name]]
   ;; Raw, unvalidated dump. save-orcbrew-blob! serializes via str/pprint, which
   ;; can't fail on bad data — the guaranteed escape hatch when validated export refuses.
   (let [[filename data] (select-emergency-export (:plugins db) plugin-name)]
     (save-orcbrew-blob! filename data)
     {:dispatch [:show-warning-message
                 (str "Raw backup '" filename "' downloaded (unvalidated). "
                      "Keep it safe.")]})))

;; Raw export of a QUARANTINED source (in :quarantined-plugins, not :plugins, so
;; ::e5/emergency-export-raw can't reach it). Unvalidated, so the broken data can
;; always get out to fix externally.
(reg-event-fx
 ::e5/export-quarantined-raw
 (fn [{:keys [db]} [_ source-name]]
   (when-let [data (get-in db [:quarantined-plugins source-name])]
     (save-orcbrew-blob! (str source-name ".orcbrew") data))
   {}))

;; ============================================================================
;; Export Warning Modal Events
;; ============================================================================

(reg-event-db
 :show-export-warning-modal
 (fn [db [_ {:keys [mode plugins warnings pretty-print?]}]]
   (assoc db :export-warning
          {:active? true
           :mode (or mode :single)
           :plugins plugins
           :warnings warnings
           :pretty-print? pretty-print?
           :edits {}
           :show-export-as-is? false})))

(reg-event-db
 :update-export-edit
 (fn [db [_ edit-path value]]
   (assoc-in db [:export-warning :edits edit-path] value)))

(reg-event-db
 :remove-export-edit
 (fn [db [_ edit-path]]
   (update-in db [:export-warning :edits] dissoc edit-path)))

(reg-event-db
 :toggle-export-as-is
 (fn [db _]
   (update-in db [:export-warning :show-export-as-is?] not)))

(defn- build-export-log-entries
  "Build slide-out log entries from the modal's plugin issues."
  [plugins description]
  [{:type :export-missing-fields
    :description description
    :details (vec (mapcat
                   (fn [{:keys [name issues]}]
                     (for [{:keys [content-type invalid-items]} issues
                           item invalid-items]
                       (assoc item :content-type content-type :plugin name)))
                   plugins))}])

(reg-event-fx
 :export-with-auto-fix
 (fn [{:keys [db]} _]
   (let [{:keys [mode plugins edits pretty-print?]} (:export-warning db)
         ;; Auto-fix = user edits + dummy-fill for blanks. The same fixed data goes
         ;; to the file AND back to the library, so My Content matches the export.
         ;; Placeholders like "[Missing Name]" are self-labeling and flagged in the log.
         fixed-plugins (mapv
                        (fn [{:keys [name plugin]}]
                          {:name name
                           :plugin (orcbrew-val/apply-user-edits-to-plugin
                                    plugin name edits)})
                        plugins)
         all-plugins (:plugins db)
         ;; For multi mode, merge the fixed sources back over all plugins.
         final-data (if (= mode :multi)
                      (reduce (fn [acc {:keys [name plugin]}] (assoc acc name plugin))
                              all-plugins fixed-plugins)
                      (:plugin (first fixed-plugins)))
         ;; Library gets the same fixed data as the file (multi: the merged map;
         ;; single: the fixed plugin under its source name).
         new-plugins (if (= mode :multi)
                       final-data
                       (assoc all-plugins (:name (first fixed-plugins)) final-data))
         filename (if (= mode :multi)
                    "all-content.orcbrew"
                    (str (:name (first fixed-plugins)) ".orcbrew"))
         log-entries (build-export-log-entries
                      plugins "Auto-filled missing fields on export")]
     ;; Strip meaningless blanks (false/nil/empty) on normal export.
     (save-orcbrew-blob! filename (orcbrew-val/strip-export-blanks final-data)
                         :pretty-print? pretty-print?)
     (plugins->local-store new-plugins)
     {:db (-> db
              (assoc :plugins new-plugins)
              (assoc :export-warning {:active? false})
              (assoc :import-log {:panel-shown? true
                                  :import-name (if (= mode :multi)
                                                 "Export (all plugins)"
                                                 (str "Export: " (:name (first plugins))))
                                  :changes log-entries
                                  :errors []
                                  :skipped-items []}))
      :dispatch [:show-warning-message
                 (str "Exported and saved to My Content. Placeholders were filled "
                      "in for any fields left blank — review them (see the log) and "
                      "replace before sharing.")]})))

(reg-event-fx
 :export-cancel-with-log
 (fn [{:keys [db]} _]
   (let [{:keys [plugins]} (:export-warning db)
         log-entries (build-export-log-entries
                      plugins "Export cancelled — fix these fields manually")]
     {:db (-> db
              (assoc :export-warning {:active? false})
              (assoc :import-log {:panel-shown? true
                                  :import-name "Export Issues"
                                  :changes log-entries
                                  :errors []
                                  :skipped-items []}))})))

(reg-event-fx
 :export-as-is
 (fn [{:keys [db]} _]
   (let [{:keys [mode plugins pretty-print?]} (:export-warning db)
         all-plugins (:plugins db)
         final-data (if (= mode :multi)
                      (reduce (fn [acc {:keys [name plugin]}]
                                (assoc acc name plugin))
                              all-plugins
                              plugins)
                      (:plugin (first plugins)))
         filename (if (= mode :multi)
                    "all-content.orcbrew"
                    (str (:name (first plugins)) ".orcbrew"))
         log-entries (build-export-log-entries
                      plugins "Exported as-is without fixes")]
     (save-orcbrew-blob! filename final-data :pretty-print? pretty-print?)
     {:db (-> db
              (assoc :export-warning {:active? false})
              (assoc :import-log {:panel-shown? true
                                  :import-name (if (= mode :multi)
                                                 "Export (all plugins)"
                                                 (str "Export: " (:name (first plugins))))
                                  :changes log-entries
                                  :errors []
                                  :skipped-items []}))})))

;; Export all homebrew plugins as .orcbrew file.
(reg-event-fx
 ::e5/export-all-plugins
 (fn [{:keys [db]} [_ {:keys [skip-dup-prompt?]}]]
   ;; skip-dup-prompt? is set when export resumes after conflict resolution, so
   ;; a user who chose "keep both" isn't re-prompted forever for the same key.
   (let [library (:plugins db)
         ;; Run the same checks the importer runs, over the whole library:
         ;; silent cleanups (text/normalize, semantic cleaning, option dedup) and
         ;; cross-source key-conflict detection. This is the shared gate — what
         ;; import would fix, export fixes too, before anything hits the file.
         {corrected :data cleanup-changes :changes key-conflicts :key-conflicts}
         (orcbrew-val/correct-library library)
         library-changed? (not= library corrected)
         dup-conflicts (:internal-conflicts key-conflicts)
         ;; Per-plugin required-field / spec check on the corrected library.
         {:keys [fillable blockers]}
         (orcbrew-val/classify-plugins-for-export corrected)
         ;; Persist silent corrections back so the store matches the file and a
         ;; second export finds nothing left to fix (the checks converge).
         persist (when library-changed? [[::e5/set-plugins corrected]])]

     (when (seq cleanup-changes)
       (js/console.log "Export cleanup:" (clj->js cleanup-changes)))
     (doseq [{:keys [name validation]} (concat fillable blockers)]
       (js/console.warn
        (str "Plugin \"" name "\":\n"
             (orcbrew-val/format-export-validation-for-log validation)))
       (log-export-warnings name validation))

     (cond
       ;; Spec-error blockers — can't safely export
       (seq blockers)
       ;; Surface the actual problems IN the UI (per source, per issue) instead of
       ;; punting the user to the browser console. Uses the same validation data
       ;; the console log would print; long-lived so it can be read and acted on.
       {:dispatch-n
        (vec (concat persist
                     [[:show-error-message
                       (into [:div
                              [:div.f-w-b "Can't export yet — fix these problems first:"]]
                             (for [{:keys [name validation]} blockers]
                               (into [:div.m-t-10 [:span.f-w-b (str "\"" name "\":")]]
                                     (for [err (or (seq (:errors validation)) ["contains invalid data"])]
                                       [:div.m-l-10 (str "• " err)]))))
                       60000]]))}

       ;; Cross-source duplicate keys — resolve them exactly like an import does
       ;; (rename-all / manual), then resume the export via :mode :export.
       (and (seq dup-conflicts) (not skip-dup-prompt?))
       {:dispatch-n (vec (concat persist
                                 [[:start-conflict-resolution
                                   {:import-name "Library"
                                    :import-data corrected
                                    :conflicts {:internal-conflicts dup-conflicts
                                                :external-conflicts []}
                                    :validation-result {:changes cleanup-changes}
                                    :mode :export}]]))}

       ;; Some plugins have missing required fields — show the fill-in modal
       (seq fillable)
       {:dispatch-n (vec (concat persist
                                 [[:show-export-warning-modal
                                   {:mode :multi
                                    :plugins (mapv #(select-keys % [:name :plugin :issues]) fillable)
                                    :warnings (vec (mapcat #(get-in % [:validation :warnings]) fillable))}]]))}

       ;; Everything is clean — write the corrected file (blanks stripped).
       :else
       (do
         (save-orcbrew-blob! "all-content.orcbrew"
                             (orcbrew-val/strip-export-blanks corrected))
         {:dispatch-n (vec (concat persist
                                   (when (seq cleanup-changes)
                                     [[:show-warning-message
                                       (str "✅ Exported all-content.orcbrew\n\n"
                                            "Cleaned " (count cleanup-changes)
                                            " item(s) on the way out.")]])))})))))


(defn clj->json
  [ds]
  (.stringify js/JSON (clj->js ds) nil 2))

(reg-event-fx
 ::e5/save-to-json
 (fn [_ [_ name plugin]]
   (let [blob (js/Blob.
               (clj->js [(clj->json plugin)])
               (clj->js {:type "application/json;charset=utf-8"}))]
     (js/saveAs blob (str name ".json"))
     {})))

(reg-event-fx
 ::e5/export-plugin-pretty-print
 (fn [_ [_ name plugin]]
   (validate-and-show-modal-or-export name plugin {:pretty-print? true})))

;; Export all homebrew plugins as pretty-printed .orcbrew file.
(reg-event-fx
 ::e5/export-all-plugins-pretty-print
 (fn [{:keys [db]} _]
   (let [blob (js/Blob.
               (clj->js [(with-out-str (pprint/pprint (:plugins db)))])
               (clj->js {:type "text/plain;charset=utf-8"}))]
     (js/saveAs blob "all-content.orcbrew")
     {})))

(reg-event-fx
 ::e5/delete-plugin
 (fn [{:keys [db]} [_ name]]
   {:dispatch [::e5/set-plugins (-> db :plugins (dissoc name))]}))

(reg-event-fx
 ::e5/toggle-plugin
 (fn [{:keys [db]} [_ name]]
   {:dispatch [::e5/set-plugins (-> db :plugins (common/toggle-in [name :disabled?]))]}))

(reg-event-fx
 ::e5/toggle-plugin-item
 (fn [{:keys [db]} [_ plugin-name type-key key]]
   {:dispatch [::e5/set-plugins (-> db :plugins (common/toggle-in [plugin-name type-key key :disabled?]))]}))

;; (Removed dead `clean-plugin-errors` — a raw-EDN string-replace hack with zero
;;  callers, superseded by `orcbrew-validation/validate-import` (structured cleaning).)

;; ============================================================================
;; Import Log Events
;; ============================================================================

(reg-event-db
 :set-import-log
 (fn [db [_ {:keys [name changes errors skipped-items]}]]
   (assoc db :import-log
          {:panel-shown? (or (seq changes) (seq errors) (seq skipped-items))
           :changes (or changes [])
           :errors (or errors [])
           :skipped-items (or skipped-items [])
           :import-name name
           :timestamp (js/Date.)})))

(reg-event-db
 :toggle-import-log-panel
 (fn [db _]
   (update-in db [:import-log :panel-shown?] not)))

(reg-event-db
 :close-import-log-panel
 (fn [db _]
   (assoc-in db [:import-log :panel-shown?] false)))

(reg-event-db
 :clear-import-log
 (fn [db _]
   (assoc db :import-log {:panel-shown? false
                          :changes []
                          :errors []
                          :skipped-items []
                          :import-name nil
                          :timestamp nil})))

;; ============================================================================
;; Import Plugin Events
;; ============================================================================

;; ============================================================================
;; Shared-content loading (viewing a character shared with embedded homebrew)
;; ============================================================================
;; A share link may carry the character's homebrew in the URL fragment. On
;; landing we decode + validate it (share-url/decode-shared applies the
;; untrusted-input security gates: input cap, decompression-bomb guard, safe
;; reader, structural whitelist) and load the KEPT items — through the SAME
;; per-item load-floor spec gate a file import uses — into :shared-plugins: an
;; EPHEMERAL overlay that renders for this view only and is never written to the
;; recipient's library.

(reg-fx
 ::decode-shared!
 (fn [payload]
   (-> (share-url/decode-shared payload)
       (.then (fn [result]
                (cond
                  (:error result)
                  (js/console.warn "Shared content not loaded:" (name (:error result)))
                  (or (seq (:plugins result)) (seq (:custom-items result)))
                  (dispatch [::e5/apply-shared-content result])))))))

(reg-event-fx
 ::e5/load-shared-content
 (fn [_ [_ payload]]
   {::decode-shared! payload}))

(defn- count-plugin-items [pl]
  (reduce + 0 (for [[_ p] pl
                    [ct items] p
                    :when (and (qualified-keyword? ct) (map? items))]
                (count items))))

(reg-event-fx
 ::e5/apply-shared-content
 (fn [{:keys [db]} [_ {:keys [plugins custom-items]}]]
   ;; Layer 6. Homebrew: run through the same per-item load-floor spec gate a file
   ;; import uses. Custom items: keep only those that EXPAND cleanly (a malformed
   ;; item would otherwise throw in expand-magic-items and blank the sheet) — this
   ;; both validates untrusted item data and protects every downstream expand site.
   (let [{:keys [kept]} (e5/salvage-library-items content-specs/valid-item-for-load? plugins)
         items (filterv (fn [it]
                          (try (boolean (seq (mi/expand-magic-items [it])))
                               (catch :default _ false)))
                        (or custom-items []))]
     (if (or (seq kept) (seq items))
       ;; Store overlays + a summary the banner reads (counts + any homebrew keys
       ;; that clash with the recipient's own library and differ). No toast — the
       ;; banner is the persistent, actionable surface (view-only vs Keep).
       {:db (assoc db
                   :shared-plugins kept
                   :shared-custom-items items
                   :shared-content-info {:count (count-plugin-items kept)
                                         :item-count (count items)
                                         :collisions (vec (share-bundle/collisions kept (:plugins db)))})}
       {}))))

;; Persist the currently-viewed shared content into the recipient's own library,
;; collapsed under one clearly-labeled source so it can't silently overwrite an
;; existing same-named source. Colliding keys were surfaced by the banner; the
;; user is choosing to keep anyway. Clears the overlay once persisted.
(reg-event-fx
 ::e5/keep-shared-content
 (fn [{:keys [db]} [_ character-name]]
   (let [shared (:shared-plugins db)]
     (if-not (seq shared)
       {}
       (let [source-name (str (if (s/blank? character-name) "Shared" character-name) " (shared)")
             collapsed {source-name (apply merge-with
                                           (fn [a b] (if (and (map? a) (map? b)) (merge a b) b))
                                           (vals shared))}
             live (e5/merge-all-plugins (:plugins db) collapsed)]
         (plugins->local-store live)
         {:db (-> db (assoc :plugins live) (dissoc :shared-plugins :shared-content-info))
          :dispatch [:show-message
                     (str "Saved this character's custom content to your library as \""
                          source-name "\".")]})))))

;; Dismiss the shared-content banner without keeping (content stays view-only for
;; this session; the overlay itself is cleared on the next character route).
(reg-event-db
 ::e5/dismiss-shared-content
 (fn [db _]
   (dissoc db :shared-content-info)))

(defn incoming-sources
  "Normalize freshly-parsed import data to the flat {source-name plugin} shape the
   store expects. A multi-plugin (STRUCTURAL detection — string top-level keys, via
   orcbrew-val/is-multi-plugin?) is returned AS-IS; a single plugin is wrapped under
   `import-name`.

   Structural detection (not spec validity) is load-bearing: a multi-plugin with
   even ONE imperfect sub-source must not be misjudged single and wrapped, which
   double-nests it into a shape ::plugin can never load — quarantining the whole
   pak instead of just the one bad sub-source. Validity is enforced separately and
   per-source downstream by salvage-plugins, so this staying structural is safe."
  [import-name data]
  (if (orcbrew-val/is-multi-plugin? data)
    data
    {import-name data}))

;; Freshly-imported sources are stored the SAME way the boot loader reads them, at
;; PER-ENTRY granularity: each source keeps its valid items (the live library) and
;; only its broken items are set aside into the quarantine store — so one bad entry
;; can't take down its whole source, and the rest imports fine. Both import paths
;; (direct and conflict-resolution) go through this. Uses the loader's OWN item
;; floor (content-specs/valid-item-for-load? via e5/salvage-library-items), so what
;; import keeps and what a refresh keeps agree exactly. Side-effect: persists the
;; reconciled quarantine store and logs each set-aside entry's spec reason.
(defn store-imported-sources
  "Returns {:merged :quarantine :any-rejected? :message}. `merged` is the full live
   library (existing + kept valid items) to persist via ::e5/store-plugins (nil if
   nothing kept); `quarantine` is the reconciled {source partial-plugin} map of
   set-aside entries (also persisted here); message is the notice (nil if none)."
  [existing incoming]
  (let [{:keys [kept rejected]} (e5/salvage-library-items
                                 content-specs/valid-item-for-load? incoming)
        live (e5/merge-all-plugins existing kept)
        ;; Merge set-aside entries into the quarantine, pruning anything now live so
        ;; an item is never both live and quarantined (a fixed entry self-clears).
        quarantine (e5/reconcile-rejected-items (get-rejected-plugins) rejected live)
        n-items (reduce + 0 (for [[_ p] rejected
                                  [ct items] p
                                  :when (and (qualified-keyword? ct) (map? items))]
                              (count items)))]
    (set-rejected-plugins quarantine)
    (doseq [[nm p] rejected]
      ;; Log the EXACT failing paths + predicates (not the giant value) so each
      ;; set-aside entry's reason is actionable: `in` is the path, `pred` the violation.
      (let [problems (:cljs.spec.alpha/problems (spec/explain-data ::e5/plugin p))]
        (js/console.warn
         (str "Set aside " (count problems) " bad entr"
              (if (= 1 (count problems)) "y" "ies") " in source \"" nm "\":\n"
              (s/join "\n"
                      (map (fn [{:keys [in pred]}]
                             (str "  at " (pr-str (vec in)) "  —  " (pr-str pred)))
                           (take 8 problems)))))))
    {:merged (when (seq kept) live)
     :quarantine quarantine
     :any-rejected? (boolean (seq rejected))
     :message (when (seq rejected)
                (str "Imported — but " n-items " entr" (if (= 1 n-items) "y" "ies")
                     " couldn't be loaded and " (if (= 1 n-items) "was" "were")
                     " set aside in “My Content”. The rest imported fine; open it "
                     "there to fix or discard " (if (= 1 n-items) "it." "them.")))}))

(reg-event-fx
 ::e5/import-plugin
 (fn [{:keys [db]} [_ plugin-name plugin-text]]
   ;; Use comprehensive validation with progressive import strategy
   ;; Pass existing plugins for duplicate key detection
   (let [result (orcbrew-val/validate-import plugin-text {:strategy :progressive
                                                         :auto-clean true
                                                         :existing-plugins (:plugins db)
                                                         :import-source-name plugin-name})
         user-message (orcbrew-val/format-import-result result)
         has-conflicts? (or (seq (get-in result [:key-conflicts :internal-conflicts]))
                            (seq (get-in result [:key-conflicts :external-conflicts])))]

     ;; Log a concise summary to the console for debugging. Dumping the whole
     ;; result via clj->js buries the useful bits under the entire plugin data,
     ;; so we surface just the counts and conflict totals here; detailed errors
     ;; are logged (already formatted) in the branches below.
     (js/console.log
      (str "Import \"" plugin-name "\": "
           (cond
             (:parse-error result) "parse error"
             (not (:success result)) "validation failed"
             :else (str "imported " (or (:imported-count result) 0)
                        ", skipped " (or (:skipped-count result) 0)))
           " | changes: " (count (:changes result))
           " | conflicts: " (+ (count (get-in result [:key-conflicts :internal-conflicts]))
                               (count (get-in result [:key-conflicts :external-conflicts])))))

     (cond
       ;; Parse error - cannot recover
       (:parse-error result)
       (do
         (js/console.error
          (str "Parse error: " (:error result)
               (when (:line result) (str " (line " (:line result) ")"))
               (when (:hint result) (str "\n" (:hint result)))))
         {:dispatch-n [[:show-error-message user-message]
                       [:set-import-log {:name plugin-name
                                         :changes (:changes result)
                                         :errors [(:error result)]
                                         :skipped-items []}]]})

       ;; Validation failed completely
       (and (not (:success result)) (:errors result))
       (do
         (js/console.error (str "Validation errors:\n" (errors->str (:errors result))))
         {:dispatch-n [[:show-error-message user-message]
                       [:set-import-log {:name plugin-name
                                         :changes (:changes result)
                                         :errors (:errors result)
                                         :skipped-items []}]]})

       ;; Key conflicts detected - show resolution modal
       (and (:success result) has-conflicts?)
       (do
         (js/console.log "Key conflicts detected, showing resolution modal")
         {:dispatch [:start-conflict-resolution
                     {:import-name plugin-name
                      :import-data (:data result)
                      :conflicts (:key-conflicts result)
                      :validation-result result}]})

       ;; Progressive import succeeded (may have skipped some items)
       (:success result)
       (let [plugin (:data result)
             ;; Normalize to the flat {source-name plugin} shape (never wrapping a
             ;; multi-plugin — see incoming-sources), then store through the shared
             ;; gate so any source that would be quarantined on the next reload (a
             ;; keyword-trap item, or any other ::plugin invalidity) is quarantined
             ;; NOW and surfaced in the repair UI — not after a refresh.
             incoming (incoming-sources plugin-name plugin)
             import-log [:set-import-log {:name plugin-name
                                          :changes (:changes result)
                                          :errors []
                                          :skipped-items (:skipped-items result)
                                          :key-conflicts (:key-conflicts result)
                                          :key-warnings (:key-warnings result)}]
             {:keys [merged quarantine message]}
             (store-imported-sources (:plugins db) incoming)]

         ;; Log skipped items if any
         (when (:had-errors result)
           (js/console.warn
            (str "Skipped " (count (:skipped-items result)) " invalid item(s):\n"
                 (s/join "\n\n"
                         (map (fn [item]
                                (str "  • " (:key item) "\n"
                                     (errors->str (:errors item))))
                              (:skipped-items result))))))

         {:db (assoc db :quarantined-plugins quarantine)
          :dispatch-n (remove nil?
                        [;; Persist the kept (valid) items; the "✅ imported" message
                         ;; is store-plugins' on-success, so it only shows if the write
                         ;; stuck (and only when nothing was set aside).
                         (when merged
                           [::e5/store-plugins merged
                            (when-not message [:show-warning-message user-message])])
                         (when message [:show-warning-message message])
                         import-log])})

       ;; Unknown state
       :else
       {:dispatch [:show-error-message "Unknown import error. Check console for details."]}))))

;; Add a strict import option for users who want all-or-nothing behavior
(reg-event-fx
 ::e5/import-plugin-strict
 (fn [{:keys [db]} [_ plugin-name plugin-text]]
   (let [result (orcbrew-val/validate-import plugin-text {:strategy :strict
                                                         :auto-clean true
                                                         :existing-plugins (:plugins db)
                                                         :import-source-name plugin-name})
         user-message (orcbrew-val/format-import-result result)]

     ;; Concise, pre-formatted console output (mirrors the progressive import
     ;; path) instead of dumping the whole result object.
     (if (:success result)
       (js/console.log
        (str "Strict import \"" plugin-name "\": imported "
             (or (:imported-count result) 0)
             ", skipped " (or (:skipped-count result) 0)))
       (js/console.error
        (str "Strict import \"" plugin-name "\" failed:\n"
             (errors->str (:errors result)))))

     (if (:success result)
       (let [plugin (:data result)]
         {:dispatch-n [[::e5/set-plugins (if (= :multi-plugin (:strategy result))
                                           (e5/merge-all-plugins (:plugins db) plugin)
                                           (assoc (:plugins db) plugin-name plugin))]
                       [:show-warning-message user-message]]})

       {:dispatch [:show-error-message user-message]}))))

;; ============================================================================
;; Conflict Resolution Events
;; ============================================================================

(defn build-conflict-list
  "Build a list of conflicts with unique IDs for UI tracking.
   Combines internal and external conflicts with suggested renames."
  [{:keys [internal-conflicts external-conflicts]} import-name]
  (let [;; Internal conflicts: same key appears in multiple sources within the import
        internal (map-indexed
                  (fn [idx {:keys [key content-type content-type-name sources]}]
                    {:id (str "internal-" idx)
                     :type :internal
                     :key key
                     :content-type content-type
                     :content-type-name content-type-name
                     :sources sources
                     ;; For internal, user picks which source to rename
                     :suggested-renames (mapv (fn [{:keys [source name]}]
                                                {:source source
                                                 :new-key (orcbrew-val/generate-new-key key source)})
                                              sources)})
                  internal-conflicts)

        ;; External conflicts: imported key conflicts with existing key
        external (map-indexed
                  (fn [idx {:keys [key content-type content-type-name
                                   import-source import-name
                                   existing-source existing-name]}]
                    {:id (str "external-" idx)
                     :type :external
                     :key key
                     :content-type content-type
                     :content-type-name content-type-name
                     :import-source import-source
                     :import-name import-name
                     :existing-source existing-source
                     :existing-name existing-name
                     ;; Suggested rename for the import
                     :suggested-new-key (orcbrew-val/generate-new-key key import-source)})
                  external-conflicts)]
    (vec (concat internal external))))

(reg-event-db
 :start-conflict-resolution
 (fn [db [_ {:keys [import-name import-data conflicts validation-result mode]}]]
   (let [conflict-list (build-conflict-list conflicts import-name)]
     (assoc db :conflict-resolution
            {:active? true
             :import-name import-name
             :import-data import-data
             :conflicts conflict-list
             :decisions {}
             :validation-result validation-result
             ;; :import (default) merges the resolved delta into the library;
             ;; :export replaces the library with the resolved version and then
             ;; resumes the export that triggered the resolution.
             :mode (or mode :import)}))))

(reg-event-db
 :set-conflict-decision
 (fn [db [_ conflict-id decision]]
   ;; decision is {:action :rename-import | :skip | :keep-both, :new-key :foo, :source "..."}
   (assoc-in db [:conflict-resolution :decisions conflict-id] decision)))

(reg-event-db
 :rename-all-conflicts
 (fn [db _]
   (let [conflicts (get-in db [:conflict-resolution :conflicts])
         decisions (into {}
                         (map (fn [{:keys [id type suggested-new-key suggested-renames
                                           import-source]}]
                                [id (if (= type :internal)
                                      ;; A key duplicated across N sources within the import
                                      ;; needs N-1 renames in ONE pass: keep the first source's
                                      ;; key and rename the rest to their distinct source-suffixed
                                      ;; keys. Renaming only one (the old behavior) left the others
                                      ;; colliding, so the conflict reappeared on every re-import.
                                      {:action :rename-import
                                       :renames (vec (rest suggested-renames))}
                                      {:action :rename-import
                                       :source import-source
                                       :new-key suggested-new-key})])
                              conflicts))]
     (assoc-in db [:conflict-resolution :decisions] decisions))))

(reg-event-db
 :cancel-conflict-resolution
 (fn [db _]
   (assoc db :conflict-resolution
          {:active? false
           :import-name nil
           :import-data nil
           :conflicts []
           :decisions {}
           :validation-result nil})))

(reg-event-fx
 :apply-conflict-resolutions
 (fn [{:keys [db]} _]
   (let [{:keys [import-name import-data conflicts decisions validation-result mode]}
         (:conflict-resolution db)
         export-mode? (= mode :export)

         ;; Build list of renames from decisions
         renames (reduce
                  (fn [acc {:keys [id type key content-type] :as conflict}]
                    (let [decision (get decisions id)]
                      (cond
                        ;; User chose to rename the import
                        (= :rename-import (:action decision))
                        (if (seq (:renames decision))
                          ;; Internal conflict: one rename per still-colliding source,
                          ;; each to its own distinct key (fully resolved in one pass).
                          (into acc
                                (map (fn [{:keys [source new-key]}]
                                       {:source source
                                        :content-type content-type
                                        :from key
                                        :to new-key})
                                     (:renames decision)))
                          ;; External conflict: rename the single imported item.
                          (conj acc {:source (:source decision)
                                     :content-type content-type
                                     :from key
                                     :to (:new-key decision)}))

                        ;; Skip this item (don't import it)
                        (= :skip (:action decision))
                        acc  ; Will handle removal separately

                        ;; Keep both (no rename - allows override)
                        :else
                        acc)))
                  []
                  conflicts)

         ;; Apply renames to import data
         renamed-data (if (seq renames)
                        (orcbrew-val/apply-key-renames import-data renames)
                        import-data)

         ;; Import mode goes through the SAME store gate as the direct import, so
         ;; a resolved source that still wouldn't survive a reload is quarantined
         ;; and surfaced here — not hidden until the next refresh. Export mode
         ;; replaces the whole library and resumes the export instead.
         ;; incoming-sources keeps a multi-plugin flat (never wrapped/double-nested).
         incoming (incoming-sources import-name renamed-data)
         {:keys [merged quarantine message]}
         (when-not export-mode?
           (store-imported-sources (:plugins db) incoming))
         success-msg (str "✅ Import successful"
                          (when (seq renames)
                            (str "\n\nRenamed " (count renames)
                                 " key(s) to resolve conflicts.")))]

     (js/console.log "Applying conflict resolutions:" (clj->js {:renames renames}))

     {:db (cond-> (assoc db :conflict-resolution
                         {:active? false
                          :import-name nil
                          :import-data nil
                          :conflicts []
                          :decisions {}
                          :validation-result nil
                          :mode :import})
            (some? quarantine) (assoc :quarantined-plugins quarantine))
      :dispatch-n
      (remove
       nil?
       [;; Store the resolved library. Export mode REPLACES the whole library and
        ;; resumes the export; import mode persists the kept (valid) items via
        ;; store-plugins, whose on-success ("✅ Import successful") only fires if
        ;; the write actually stuck and nothing was set aside.
        (if export-mode?
          [::e5/set-plugins renamed-data]
          (when merged
            [::e5/store-plugins merged
             (when-not message [:show-warning-message success-msg])]))

        ;; Export-mode resolution message (import mode reports via store-plugins);
        ;; plus the set-aside notice if any entry was quarantined.
        (when export-mode?
          [:show-warning-message
           (str "✅ Conflicts resolved"
                (when (seq renames)
                  (str "\n\nRenamed " (count renames) " key(s) to resolve conflicts.")))])
        (when message [:show-warning-message message])

        ;; Store import log
        [:set-import-log {:name import-name
                          :changes (concat (:changes validation-result)
                                           (mapv #(assoc % :type :key-renamed) renames))
                          :errors []
                          :skipped-items (:skipped-items validation-result)}]

        ;; Export mode: resume the export now that the library is conflict-free.
        ;; skip-dup-prompt? avoids re-opening this dialog for any "keep both"
        ;; choices the user made (which intentionally leave a shared key).
        (when export-mode? [::e5/export-all-plugins {:skip-dup-prompt? true}])])})))

(reg-event-db
 ::spells/set-spell
 spell-interceptors
 (fn [_ [_ spell]]
   spell))

(reg-event-db
 ::monsters/set-monster
 monster-interceptors
 (fn [_ [_ monster]]
   monster))

(reg-event-db
 ::encounters/set-encounter
 encounter-interceptors
 (fn [_ [_ encounter]]
   encounter))

(reg-event-db
 ::combat/set-combat
 combat-interceptors
 (fn [_ [_ combat]]
   combat))

(reg-event-db
 ::bg5e/set-background
 background-interceptors
 (fn [_ [_ background]]
   background))

(reg-event-db
 ::langs5e/set-language
 language-interceptors
 (fn [_ [_ language]]
   language))

(reg-event-db
 ::class5e/set-invocation
 invocation-interceptors
 (fn [_ [_ invocation]]
   invocation))

(reg-event-db
 ::class5e/set-boon
 boon-interceptors
 (fn [_ [_ boon]]
   boon))

(reg-event-db
 ::selections5e/set-selection
 selection-interceptors
 (fn [_ [_ selection]]
   selection))

(reg-event-db
 ::feats5e/set-feat
 feat-interceptors
 (fn [_ [_ feat]]
   feat))

(reg-event-db
 ::race5e/set-race
 race-interceptors
 (fn [_ [_ race]]
   race))

(reg-event-db
 ::race5e/set-subrace
 subrace-interceptors
 (fn [_ [_ subrace]]
   subrace))

(reg-event-db
 ::class5e/set-subclass
 subclass-interceptors
 (fn [_ [_ subclass]]
   subclass))

(reg-event-db
 ::class5e/set-class
 class-interceptors
 (fn [_ [_ class]]
   class))

(reg-event-fx
 ::mi/reset-item
 (fn [_ _]
   {:dispatch [::mi/set-item
               {::mi/type :wondrous-item
                ::mi/rarity :common}]}))

(reg-event-fx
 ::spells/reset-spell
 (fn [_ _]
   {:dispatch [::spells/set-spell
               default-spell]}))

(reg-event-fx
 ::monsters/reset-monster
 (fn [_ _]
   {:dispatch [::monsters/set-monster
               default-monster]}))

(reg-event-fx
 ::encounters/reset-encounter
 (fn [_ _]
   {:dispatch [::encounters/set-encounter
               default-encounter]}))

(reg-event-fx
 ::combat/reset-combat
 (fn [_ _]
   {:dispatch [::combat/set-combat
               default-combat]}))

(reg-event-fx
 ::bg5e/reset-background
 (fn [_ _]
   {:dispatch [::bg5e/set-background
               default-background]}))

(reg-event-fx
 ::langs5e/reset-language
 (fn [_ _]
   {:dispatch [::langs5e/set-language
               default-language]}))

(reg-event-fx
 ::class5e/reset-invocation
 (fn [_ _]
   {:dispatch [::class5e/set-invocation
               default-invocation]}))

(reg-event-fx
 ::class5e/reset-boon
 (fn [_ _]
   {:dispatch [::class5e/set-boon
               default-boon]}))

(reg-event-fx
 ::selections5e/reset-selection
 (fn [_ _]
   {:dispatch [::selections5e/set-selection
               default-selection]}))

(reg-event-fx
 ::feats5e/reset-feat
 (fn [_ _]
   {:dispatch [::feats5e/set-feat
               default-feat]}))

(reg-event-fx
 ::race5e/reset-race
 (fn [_ _]
   {:dispatch [::race5e/set-race
               default-race]}))

(reg-event-fx
 ::race5e/reset-subrace
 (fn [_ _]
   {:dispatch [::race5e/set-subrace
               default-subrace]}))

(reg-event-fx
 ::class5e/reset-subclass
 (fn [_ _]
   {:dispatch [::class5e/set-subclass
               default-subclass]}))

(reg-event-fx
 ::class5e/reset-class
 (fn [_ _]
   {:dispatch [::class5e/set-class
               default-class]}))

(defn reg-new-homebrew [event set-event default-val route]
  (reg-event-fx
   event
   (fn [_ [_ option-pack option]]
     {:dispatch-n [[set-event (-> default-val
                                  (assoc :option-pack option-pack)
                                  (merge option))]
                   [:route route]]})))

(defn reg-option-selections [option-name option-key interceptors]
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "add-" option-name "-selection"))
   interceptors
   (fn [option]
     (update option :level-selections (fn [t] (if (vector? t) (conj t {}) [{}])))))
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "edit-" option-name "-selection-type"))
   interceptors
   (fn [option [_ index type]]
     (cond-> option
       (nil? (:level-selections option)) (assoc :level-selections [])
       true (assoc-in [:level-selections index :type] type))))
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "edit-" option-name "-selection-level"))
   interceptors
   (fn [option [_ index level]]
     (cond-> option
       (nil? (:level-selections option)) (assoc :level-selections [])
       true (assoc-in [:level-selections index :level] level))))
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "edit-" option-name "-selection-num"))
   interceptors
   (fn [option [_ index num]]
     (cond-> option
       (nil? (:level-selections option)) (assoc :level-selections [])
       true (assoc-in [:level-selections index :num] num))))
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "delete-" option-name "-selection"))
   interceptors
   (fn [option [_ index]]
     (update option :level-selections common/remove-at-index index))))

(reg-option-selections "subclass" ::class5e/subclass-builder-item subclass-interceptors)
(reg-option-selections "class" ::class5e/builder-item class-interceptors)

(defn reg-option-modifiers [option-name option-key interceptors]
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "add-" option-name "-modifier"))
   interceptors
   (fn [option]
     (update option :level-modifiers (fn [t] (if (vector? t) (conj t {}) [{}])))))
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "edit-" option-name "-modifier-type"))
   interceptors
   (fn [option [_ index type]]
     (cond-> option
       (nil? (:level-modifiers option)) (assoc :level-modifiers [])
       true (assoc-in [:level-modifiers index :type] type))))
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "edit-" option-name "-modifier-level"))
   interceptors
   (fn [option [_ index level]]
     (cond-> option
       (nil? (:level-modifiers option)) (assoc :level-modifiers [])
       true (assoc-in [:level-modifiers index :level] level))))
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "edit-" option-name "-modifier-value"))
   interceptors
   (fn [option [_ index value]]
     (cond-> option
       (nil? (:level-modifiers option)) (assoc :level-modifiers [])
       true (assoc-in [:level-modifiers index :value] value))))
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "delete-" option-name "-modifier"))
   interceptors
   (fn [option [_ index]]
     (update option :level-modifiers common/remove-at-index index))))

(reg-option-modifiers "subclass" ::class5e/subclass-builder-item subclass-interceptors)
(reg-option-modifiers "class" ::class5e/builder-item class-interceptors)

(reg-event-db
 ::race5e/set-subrace-spell-level
 subrace-interceptors
 (fn [subrace [_ index level]]
   (cond-> subrace
     (nil? (:spells subrace)) (assoc :spells [])
     true (assoc-in [:spells index :level] level))))

(reg-event-db
 ::race5e/set-subrace-spell-value
 subrace-interceptors
 (fn [subrace [_ index value]]
   (cond-> subrace
     (nil? (:spells subrace)) (assoc :spells [])
     true (assoc-in [:spells index :value] value))))

(reg-event-db
 ::race5e/delete-subrace-spell
 subrace-interceptors
 (fn [subrace [_ index]]
   (update subrace :spells common/remove-at-index index)))

(reg-event-db
 ::race5e/set-race-spell-level
 race-interceptors
 (fn [race [_ index level]]
   (cond-> race
     (nil? (:spells race)) (assoc :spells [])
     true (assoc-in [:spells index :level] level))))

(reg-event-db
 ::race5e/set-race-spell-value
 race-interceptors
 (fn [race [_ index value]]
   (cond-> race
     (nil? (:spells race)) (assoc :spells [])
     true (assoc-in [:spells index :value] value))))

(reg-event-db
 ::race5e/delete-race-spell
 race-interceptors
 (fn [race [_ index]]
   (update race :spells common/remove-at-index index)))

(defn reg-option-traits [option-name option-key interceptors]
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "add-" option-name "-trait"))
   interceptors
   (fn [option]
     (update option :traits (fn [t] (if (vector? t) (conj t {}) [{}])))))
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "edit-" option-name "-trait-name"))
   interceptors
   (fn [option [_ index name]]
     (assoc-in option [:traits index :name] name)))
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "edit-" option-name "-trait-type"))
   interceptors
   (fn [option [_ index type]]
     (assoc-in option [:traits index :type] type)))
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "edit-" option-name "-trait-level"))
   interceptors
   (fn [option [_ index level]]
     (assoc-in option [:traits index :level] level)))
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "edit-" option-name "-trait-description"))
   interceptors
   (fn [option [_ index description]]
     (assoc-in option [:traits index :description] description)))
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "delete-" option-name "-trait"))
   interceptors
   (fn [option [_ index]]
     (update option :traits common/remove-at-index index))))

(reg-option-traits "monster" ::monsters/builder-item monster-interceptors)
(reg-option-traits "subrace" ::race5e/subrace-builder-item subrace-interceptors)
(reg-option-traits "subclass" ::class5e/subclass-builder-item subclass-interceptors)
(reg-option-traits "class" ::class5e/builder-item class-interceptors)
(reg-option-traits "race" ::race5e/builder-item race-interceptors)
(reg-option-traits "background" ::bg5e/builder-item background-interceptors)

(reg-new-homebrew
 ::spells/new-spell
 ::spells/set-spell
 default-spell
 routes/dnd-e5-spell-builder-page-route)

(reg-new-homebrew
 ::monsters/new-monster
 ::monsters/set-monster
 default-monster
 routes/dnd-e5-monster-builder-page-route)

(reg-new-homebrew
 ::encounters/new-encounter
 ::encounters/set-encounter
 default-encounter
 routes/dnd-e5-encounter-builder-page-route)

(reg-new-homebrew
 ::bg5e/new-background
 ::bg5e/set-background
 default-background
 routes/dnd-e5-background-builder-page-route)

(reg-new-homebrew
 ::langs5e/new-language
 ::langs5e/set-language
 default-language
 routes/dnd-e5-language-builder-page-route)

(reg-new-homebrew
 ::class5e/new-invocation
 ::class5e/set-invocation
 default-invocation
 routes/dnd-e5-invocation-builder-page-route)

(reg-new-homebrew
 ::selections5e/new-selection
 ::selections5e/set-selection
 default-selection
 routes/dnd-e5-selection-builder-page-route)

(reg-new-homebrew
 ::class5e/new-boon
 ::class5e/set-boon
 default-boon
 routes/dnd-e5-boon-builder-page-route)

(reg-new-homebrew
 ::feats5e/new-feat
 ::feats5e/set-feat
 default-feat
 routes/dnd-e5-feat-builder-page-route)

(reg-new-homebrew
 ::race5e/new-race
 ::race5e/set-race
 default-race
 routes/dnd-e5-race-builder-page-route)

(reg-new-homebrew
 ::race5e/new-subrace
 ::race5e/set-subrace
 default-subrace
 routes/dnd-e5-subrace-builder-page-route)

(reg-new-homebrew
 ::class5e/new-subclass
 ::class5e/set-subclass
 default-subclass
 routes/dnd-e5-subclass-builder-page-route)

(reg-new-homebrew
 ::class5e/new-class
 ::class5e/set-class
 default-class
 routes/dnd-e5-class-builder-page-route)

(reg-event-fx
 ::mi/new-item
 (fn [_ _]
   {:dispatch-n [[::mi/reset-item]
                 [:route routes/dnd-e5-item-builder-page-route]]}))

(reg-event-db
 ::mi/set-ability-mod-type
 item-interceptors
 (fn [item [_ ability-kw type]]
   (assoc-in item
             [::mi/internal-modifiers
              :ability
              ability-kw
              :type]
             (keyword type))))

(defn set-mod-value [item mods-path mod-key value]
  (if value
    (assoc-in item
              (conj mods-path
                    mod-key
                    :value)
              value)
    (update-in item
               mods-path
               dissoc
               mod-key)))

(reg-event-db
 ::mi/set-ability-mod-value
 item-interceptors
 (fn [item [_ ability-kw value]]
   (set-mod-value item
                  [::mi/internal-modifiers
                   :ability]
                  ability-kw
                  value)))

(reg-event-db
 ::mi/set-speed-mod-type
 item-interceptors
 (fn [item [_ speed-type-kw mod-type]]
   (assoc-in item
             [::mi/internal-modifiers
              speed-type-kw
              :type]
             (keyword mod-type))))

(reg-event-db
 ::mi/set-speed-mod-value
 item-interceptors
 (fn [item [_ speed-type-kw value]]
   (set-mod-value item
                  [::mi/internal-modifiers]
                  speed-type-kw
                  value)))

(reg-event-db
 ::mi/set-save-mod-value
 item-interceptors
 (fn [item [_ ability-kw value]]
   (set-mod-value item
                  [::mi/internal-modifiers
                   :save]
                  ability-kw
                  value)))

(reg-event-db
 ::mi/toggle-subtype
 item-interceptors
 (fn [item [_ type]]
   (mi/apply-subtype-toggle item type)))

(reg-event-fx
 ::char5e/open-character
 (fn [_ [_ character]]
   {:dispatch-n [[:set-character character]
                 [:route routes/dnd-e5-char-builder-route]]}))

(reg-event-fx
 :route-to-login
 (fn [{:keys [db]} _]
   ;; Reset loading counter — multiple parallel 401s can leave the overlay stuck
   {:db (assoc db :loading 0)
    :dispatch [:route routes/login-page-route {:secure? true :no-return? true}]}))

(reg-event-db
 ::char5e/show-options
 (fn [db [_ component]]
   (assoc db
          ::char5e/options-shown? true
          ::char5e/options-component component)))

(reg-event-db
 ::char5e/hide-options
 (fn [db _]
   (assoc db ::char5e/options-shown? false)))

#_ ;; never dispatched — print UI not wired
  (reg-event-db
   ::char5e/toggle-character-sheet-print
   (fn [db _]
     (update db ::char5e/exclude-character-sheet-print? not)))

(reg-event-db
 ::char5e/toggle-spell-cards-print
 (fn [db _]
   (update db ::char5e/exclude-spell-cards-print? not)))

#_ ;; never dispatched — print UI not wired
  (reg-event-db
   ::char5e/toggle-spell-cards-by-level
   (fn [db _]
     (update db ::char5e/exclude-spell-cards-by-level? not)))

(reg-event-db
 ::char5e/toggle-spell-cards-by-dc-mod
 (fn [db _]
   (update db ::char5e/exclude-spell-cards-by-dc-mod? not)))

(reg-event-db
 ::char5e/toggle-print-card-back-logo
 (fn [db _]
   (update db ::char5e/print-card-back-logo? not)))

(reg-event-db
 ::char5e/toggle-card-back-logo-faded
 (fn [db _]
   (update db ::char5e/card-back-logo-faded? not)))

(reg-event-db
 ::char5e/toggle-print-bw
 (fn [db _]
   (update db ::char5e/print-bw? not)))

(reg-event-db
 ::char5e/toggle-bw-faded
 (fn [db _]
   (update db ::char5e/bw-faded? not)))

(reg-event-db
 ::char5e/toggle-large-abilities-print
 (fn [db _]
   (update db ::char5e/print-large-abilities? not)))

(reg-event-db
 ::char5e/set-print-character-sheet-style?
 (fn [db [_ id]]
   (assoc-in db [::char5e/print-character-sheet-style?] id)))

(reg-event-db
 ::char5e/toggle-known-spells-print
 (fn [db _]
   (update db ::char5e/print-prepared-spells? not)))

(reg-event-db
 ::char5e/show-delete-confirmation
 (fn [db [_ id]]
   (assoc-in db [::char5e/delete-confirmation-shown? id] true)))

(reg-event-db
 ::char5e/hide-delete-confirmation
 (fn [db [_ id]]
   (assoc-in db [::char5e/delete-confirmation-shown? id] false)))

(reg-event-db
 ::mi/show-delete-confirmation
 (fn [db [_ id]]
   (assoc-in db [::mi/delete-confirmation-shown? id] true)))

(reg-event-db
 ::mi/hide-delete-confirmation
 (fn [db [_ id]]
   (assoc-in db [::mi/delete-confirmation-shown? id] false)))

(reg-event-db
 ::char5e/show-delete-plugin-confirmation
 (fn [db _]
   (assoc-in db [::char5e/delete-plugin-confirmation-shown?] true)))

(reg-event-db
 ::char5e/hide-delete-plugin-confirmation
 (fn [db _]
   (assoc-in db [::char5e/delete-plugin-confirmation-shown?] false)))

(defn remove-plugin-classes
  "Removes classes from character that aren't base classes.
   If no classes remain, sets to Barbarian. Preserves all other character data."
  [character]
  (let [current-classes (get-in character [::entity/options :class])
        valid-classes (vec (filter #(class5e/base-class-keys (::entity/key %)) current-classes))]
    (if (seq valid-classes)
      ;; Keep only valid base classes
      (assoc-in character [::entity/options :class] valid-classes)
      ;; No valid classes - set to Barbarian
      (char5e/set-class character :barbarian 0 (class5e/barbarian-option [] {} {} {} {})))))

(reg-event-db
 ::char5e/remove-plugin-classes
 character-interceptors
 (fn [character _]
   (remove-plugin-classes character)))

(reg-event-fx
 ::char5e/delete-all-plugins
 (fn [{:keys [db]} _]
   ;; Reset to default empty plugins state.
   ;; DO NOT call remove-plugin-classes - let the character keep its
   ;; references so the Missing Content Warning can properly show what's missing.
   ;; The warning system will help users understand which homebrew they need
   ;; to re-import to restore their character.
   {:dispatch-n [[::e5/set-plugins {"Default Option Source" {}}]
                 [::char5e/hide-delete-plugin-confirmation]]}))

(reg-event-fx
 ::char5e/don-armor
 (fn [{:keys [db]} [_ id armor-kw]]
   (update-character-fx db id #(assoc-in
                                %
                                [::entity/values
                                 ::char5e/worn-armor]
                                armor-kw))))

(reg-event-fx
 ::char5e/wield-shield
 (fn [{:keys [db]} [_ id shield-kw]]
   (update-character-fx db id #(assoc-in
                                %
                                [::entity/values
                                 ::char5e/wielded-shield]
                                shield-kw))))

(reg-event-fx
 ::char5e/wield-main-hand-weapon
 (fn [{:keys [db]} [_ id weapon-kw]]
   (update-character-fx db id #(update
                                %
                                ::entity/values
                                assoc
                                ::char5e/main-hand-weapon
                                weapon-kw
                                ::char5e/off-hand-weapon
                                :none))))

(reg-event-fx
 ::char5e/wield-off-hand-weapon
 (fn [{:keys [db]} [_ id weapon-kw]]
   (update-character-fx db id #(assoc-in
                                %
                                [::entity/values
                                 ::char5e/off-hand-weapon]
                                weapon-kw))))

#_ ;; never dispatched — attunement UI not wired
  (reg-event-fx
   ::char5e/attune-magic-item
   (fn [{:keys [db]} [_ id i weapon-kw]]
     (update-character-fx db id #(update-in
                                  %
                                  [::entity/values
                                   ::char5e/attuned-magic-items]
                                  (fn [items]
                                    (assoc
                                     (or items [:none :none :none])
                                     i
                                     weapon-kw))))))

(reg-event-db
 :close-srd-message
 (fn [db [_]]
   (assoc db :srd-message-closed? true)))

(reg-event-db
 ::char5e/add-answer
 (fn [db [_ question answer]]
   (update db
           ::char5e/newb-char-data
           char-dec5e/add-answer
           question
           answer)))

(reg-event-db
 ::char5e/next-question
 (fn [db _]
   (-> db
       (assoc ::char5e/current-question
              (char-dec5e/next-question (::char5e/newb-char-data db)))
       (update ::char5e/question-history
               (fn [{:keys [questions newb-char-data]}]
                 {:questions (conj questions (get db ::char5e/current-question (char-dec5e/next-question {})))
                  :newb-char-data (conj newb-char-data (::char5e/newb-char-data db))})))))

(reg-event-db
 ::char5e/previous-question
 (fn [db _]
   (let [{:keys [questions newb-char-data] :as hist} (::char5e/question-history db)]
     (assoc
      db
      ::char5e/current-question (peek questions)
      ::char5e/newb-char-data (peek newb-char-data)
      ::char5e/question-history {:questions (pop questions)
                                 :newb-char-data (pop newb-char-data)}))))
