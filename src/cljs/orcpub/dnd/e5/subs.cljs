(ns orcpub.dnd.e5.subs
  (:require [re-frame.core :refer [reg-sub reg-sub-raw subscribe dispatch reg-event-db]]
            [orcpub.entity :as entity]
            [orcpub.entity.strict :as se]
            [orcpub.template :as t]
            [orcpub.common :as common]
            [orcpub.modifiers :as mod]
            [orcpub.registration :as registration]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.common :as common5e]
            [orcpub.dnd.e5.db :refer [tab-path]]
            [orcpub.dnd.e5.event-utils :as event-utils :refer [url-for-route auth-headers
                                                                    show-generic-error mod-cfg
                                                                    default-mod-set
                                                                    handle-api-response]]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.char-decision-tree :as char-dec5e]
            [orcpub.dnd.e5.char-filter :as char-filter]
            [orcpub.dnd.e5.character.equipment :as char-equip5e]
            [orcpub.dnd.e5.party :as party5e]
            [orcpub.dnd.e5.folder :as folder5e]
            [orcpub.dnd.e5.monsters :as monsters5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.armor :as armor5e]
            [orcpub.dnd.e5.weapons :as weapon5e]
            [orcpub.dnd.e5.magic-items :as mi5e]
            [orcpub.dnd.e5.content-reconciliation :as content-recon]
            [orcpub.route-map :as routes]
            [clojure.string :as s]
            [reagent.ratom :as ra]
            [cljs.core.async :refer [<!]]
            [orcpub.dnd.e5.http-safe :as http]
            [orcpub.dnd.e5.spell-subs])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; =============================================================================
;; Version: 1.03 - Add export warning modal subscription
;; =============================================================================

(reg-sub
 :db
 (fn [db _]
   db))

(reg-sub
 :registration-form
 (fn [db [_]]
   (get db :registration-form)))

(reg-sub
 :builder-field-errors
 (fn [db _]
   (get db :builder-field-errors #{})))

(reg-sub
 :username-taken?
 (fn [db [_]]
   (get db :username-taken?)))

(reg-sub
 :email-taken?
 (fn [db [_]]
   (get db :email-taken?)))

(reg-sub
 :srd-message-closed?
 (fn [db [_]]
   (get db :srd-message-closed? false)))

(reg-sub
 :registration-validation
 :<- [:registration-form]
 :<- [:email-taken?]
 :<- [:username-taken?]
 (fn [args [_]]
   (apply registration/validate-registration args)))

(reg-sub
 :temp-email
 (fn [db [_]]
   (get db :temp-email)))

(reg-sub
 :locked
 (fn [db [_ path]]
   (get-in db [:locked-components path])))

(reg-sub
 :has-homebrew?
 :<- [:character]
 (fn [character _]
   (some
    (fn [[k v]]
      v)
    (get character ::entity/homebrew-paths))))

(reg-sub
 :homebrew?
 :<- [:character]
 (fn [character [_ path]]
   (get-in character
           [::entity/homebrew-paths path])))

(reg-sub
 :locked-components
 (fn [db []]
   (get db :locked-components)))

(reg-sub
 :loading
 (fn [db _]
   (get db :loading)))

(reg-sub
 :active-tabs
 (fn [db _]
   (get-in db tab-path)))

(reg-sub
 :character
 (fn [db _]
   (:character db)))

(reg-sub
 :entity-values
 :<- [:character]
 (fn [character _]
   (get-in character [::entity/values])))

(reg-sub
 :entity-value
 :<- [:entity-values]
 (fn [entity-values [_ kw]]
   (get entity-values kw)))

(reg-sub
 :entity-options
 :<- [:character]
 (fn [character _]
   (get-in character [::entity/options])))

(reg-sub
 ::char5e/ability-scores-option-value
 :<- [:entity-option :ability-scores]
 (fn [option _]
   (get option ::entity/value)))

(reg-sub
 ::char5e/ability-scores-option-key
 :<- [:entity-option :ability-scores]
 (fn [option _]
   (get option ::entity/key)))

(reg-sub
 :entity-option
 :<- [:entity-options]
 (fn [entity-options [_ kw]]
   (get entity-options kw)))

(reg-sub
 :custom-race-name
 :<- [:entity-options]
 (fn [options _]
   (get-in options [:race ::entity/value])))

(reg-sub
 :custom-subrace-name
 :<- [:entity-options]
 (fn [options _]
   (get-in options [:race
                    ::entity/options
                    :subrace
                    ::entity/value])))

(reg-sub
 :custom-subclass-name
 :<- [:character]
 :<- [:built-template]
 (fn [[character built-template] [_ path]]
   (get-in character
           (entity/get-option-value-path built-template
                                         character
                                         path))))

(reg-sub
 :custom-feat-name
 :<- [:character]
 :<- [:built-template]
 (fn [[character built-template] [_ path]]
   (get-in character
           (entity/get-option-value-path built-template
                                         character
                                         path))))

(reg-sub
 :custom-background-name
 :<- [:entity-options]
 (fn [options _]
   (get-in options [:background ::entity/value])))

(reg-sub
 :option-paths
 :<- [:character]
 (fn [character _]
   (entity/make-path-map character)))

(defn selected-plugin-options [character]
  (into #{}
        (comp (map ::entity/key)
              (remove nil?))
        (get-in character [::entity/options :optional-content])))

(reg-sub
 :selected-plugin-options
 :<- [:character]
 (fn [character _]
   (selected-plugin-options character)))

(reg-sub
 :available-selections
 :<- [:character]
 :<- [:built-character]
 :<- [:built-template]
 (fn [[character built-character built-template]]
   (entity/available-selections character built-character built-template)))

(reg-sub
 :template
 (fn [db _]
   (:template db)))

(reg-sub
 :plugins
 (fn [db _]
   (:plugins db)))

(reg-sub
 :page
 (fn [db _]
   (:page db)))

(reg-sub
 :route
 (fn [db _]
   (:route db)))

(reg-sub
 :previous-route
 (fn [db _]
   (-> db :route-history peek)))

(reg-sub
 :user-data
 (fn [db _]
   (:user-data db)))

(reg-sub
 :username
 (fn [db _]
   (-> db :user-data :user-data :username)))

(reg-sub
 :email
 (fn [db _]
   (-> db :user-data :user-data :email)))

(reg-sub
 :pending-email
 (fn [db _]
   (-> db :user-data :user-data :pending-email)))

(reg-sub
 :send-updates?
 (fn [db _]
   (boolean (-> db :user-data :user-data :send-updates?))))

(reg-sub
 :email-change-sent?
 (fn [db _]
   (:email-change-sent? db)))

(reg-sub
 :email-change-error
 (fn [db _]
   (:email-change-error db)))

(defn built-template [template selected-plugin-options]
  template
  #_(let [selected-plugins (map
                          :selections
                          (filter
                           (fn [{:keys [key]}]
                             (selected-plugin-options key))
                           t5e/plugins))]
    (if (seq selected-plugins)
      (update template
              ::t/selections
              (fn [s]
                (apply
                 entity/merge-multiple-selections
                 s
                 selected-plugins)))
      template)))

(reg-sub
 :built-template
 :<- [:selected-plugin-options]
 :<- [::char5e/template]
 (fn [[selected-plugin-options template] _]
   (built-template template selected-plugin-options)))

(defn built-character [character built-template]
  (entity/build character built-template))

(def ^:private build-debounce-ms
  "Leading+trailing debounce for entity/build. First change fires
   immediately; rapid changes batch until quiet for this many ms."
  500)

(defn- debounced-build-sub
  "reg-sub-raw handler: wraps entity/build with leading+trailing edge
   debounce. Dropdown changes compute instantly; rapid keystrokes batch."
  [char-sub tmpl-sub]
  (let [timeout-id (atom nil)
        last-run   (atom 0)
        result     (ra/atom (built-character @char-sub @tmpl-sub))
        wk         (gensym "build-")
        do-build   (fn []
                     (reset! last-run (.now js/Date))
                     (reset! result (built-character @char-sub @tmpl-sub)))
        on-change  (fn [_ _ _ _]
                     (when-let [tid @timeout-id] (js/clearTimeout tid))
                     (if (>= (- (.now js/Date) @last-run) build-debounce-ms)
                       (do-build)
                       (reset! timeout-id
                               (js/setTimeout do-build build-debounce-ms))))]
    (add-watch char-sub wk on-change)
    (add-watch tmpl-sub wk on-change)
    (ra/make-reaction
     (fn [] @result)
     :on-dispose (fn []
                   (remove-watch char-sub wk)
                   (remove-watch tmpl-sub wk)
                   (when-let [tid @timeout-id]
                     (js/clearTimeout tid))))))

(reg-sub-raw
 :built-character
 (fn [_ _]
   (debounced-build-sub
    (subscribe [:character])
    (subscribe [:built-template]))))

(reg-sub
 :expanded-characters
 (fn [db _]
   (:expanded-characters db)))

(reg-sub
 :expanded-monsters
 (fn [db _]
   (:expanded-monsters db)))

(reg-sub
 :monster-expanded?
 (fn [db [_ name]]
   (get-in db [:expanded-monsters name])))

(reg-sub
 :expanded-spells
 (fn [db _]
   (:expanded-spells db)))

(reg-sub
 :spell-expanded?
 (fn [db [_ name]]
   (get-in db [:expanded-spells name])))

(reg-sub
 :expanded-items
 (fn [db _]
   (:expanded-items db)))

(reg-sub
 :item-expanded?
 (fn [db [_ name]]
   (get-in db [:expanded-items name])))

;; API-backed subscriptions — use handle-api-response for consistent
;; status handling with sensible 401/500 defaults and catch-all logging.
(reg-sub-raw
  ::char5e/characters
  (fn [app-db [_ login-optional?]]
    (when (:token (:user-data @app-db))
      (go (dispatch [:set-loading true])
          (let [response (<! (http/get (url-for-route routes/dnd-e5-char-summary-list-route)
                                       {:headers (auth-headers @app-db)}))]
            (dispatch [:set-loading false])
            (handle-api-response response
              #(dispatch [::char5e/set-characters (:body response)])
              :on-401 #(when-not login-optional? (dispatch [:route-to-login]))
              :context "fetch characters"))))
    (ra/make-reaction
     (fn [] (get @app-db ::char5e/characters [])))))

(reg-sub-raw
  ::party5e/parties
  (fn [app-db [_ login-optional?]]
    (when (:token (:user-data @app-db))
      (go (dispatch [:set-loading true])
          (let [response (<! (http/get (url-for-route routes/dnd-e5-char-parties-route)
                                       {:headers (auth-headers @app-db)}))]
            (dispatch [:set-loading false])
            (handle-api-response response
              #(dispatch [::party5e/set-parties (:body response)])
              :on-401 #(when-not login-optional? (dispatch [:route-to-login]))
              :context "fetch parties"))))
    (ra/make-reaction
     (fn [] (get @app-db ::char5e/parties [])))))

(reg-sub-raw
  :user
  (fn [app-db [_ required?]]
    (when (:token (:user-data @app-db)) ;; guard: skip HTTP when not logged in
     (go (let [hdrs (auth-headers @app-db)
              response (<! (http/get (url-for-route routes/user-route) {:headers hdrs}))]
          (handle-api-response response
            (fn [])
            :on-401 #(do (dispatch [:set-user-data (dissoc (:user-data @app-db) :user-data :token)])
                         (when required? (dispatch [:route-to-login])))
            :on-500 #(when required? (dispatch (show-generic-error)))
            :context "fetch user"))))
    (ra/make-reaction
     (fn [] (get @app-db :user [])))))

(reg-sub
 :following-users
 :<- [:user]
 (fn [user _]
   (set (:following user))))

(reg-sub
 ::char5e/character-map
 (fn [db _]
   (::char5e/character-map db)))

(reg-sub
 ::party5e/party-map
 (fn [[_ login-optional?]]
   (subscribe [::party5e/parties login-optional?]))
 (fn [parties _]
   (common/map-by-id parties)))

(reg-sub-raw
  ::folder5e/folders
  (fn [app-db _]
    (when (:token (:user-data @app-db))
      (go (dispatch [:set-loading true])
          (let [response (<! (http/get (url-for-route routes/dnd-e5-char-folders-route)
                                       {:headers (auth-headers @app-db)}))]
            (dispatch [:set-loading false])
            (handle-api-response response
              #(dispatch [::folder5e/set-folders (:body response)])
              :context "fetch folders"))))
    (ra/make-reaction
     (fn [] (get @app-db ::folder5e/folders [])))))

(reg-sub
 ::folder5e/folder-map
 :<- [::folder5e/folders]
 (fn [folders _]
   (common/map-by-id folders)))

(reg-sub
 ::folder5e/expanded
 (fn [db _]
   (get db ::folder5e/expanded {})))

(reg-sub
 ::folder5e/renaming
 (fn [db _]
   (get db ::folder5e/renaming {})))

(reg-sub
 ::folder5e/character-folder-map
 :<- [::folder5e/folders]
 (fn [folders _]
   (reduce (fn [m folder]
             (reduce (fn [m char]
                       (assoc m (:db/id char) (:db/id folder)))
                     m
                     (::folder5e/character-ids folder)))
           {}
           folders)))

;; Used by combat_tracker (views.cljs) and as input signal for ::char5e/summary
(reg-sub
 ::char5e/summary-map
 (fn [[_ login-optional?]]
   (subscribe [::char5e/characters login-optional?]))
 (fn [characters _]
   (common/map-by :db/id characters)))

;; Used by combat_tracker (views.cljs)
(reg-sub
 ::char5e/summary
 :<- [::char5e/summary-map]
 (fn [character-map [_ id]]
   (get character-map id)))


(reg-sub-raw
  ::char5e/character
  (fn [app-db [_ id :as args]]
    (let [int-id (when id (js/parseInt id))]
      (when (some? int-id)
        (go (dispatch [:set-loading true])
            (let [response (<! (http/get (url-for-route
                                           routes/dnd-e5-char-route
                                           :id int-id)))]
              (dispatch [:set-loading false])
              (handle-api-response response
                #(let [body (:body response)]
                   (if (http/decode-failed? body)
                     ;; Response was unreadable even after self-heal: don't feed
                     ;; the marker to from-strict (that silently builds a blank
                     ;; default character). Flag the load as failed so the
                     ;; character page renders an in-place recovery panel
                     ;; (delete / go to list) instead of a blank sheet.
                     (dispatch [::char5e/set-character-load-error int-id body])
                     (do (dispatch [::char5e/set-character-load-error int-id nil])
                         (dispatch [::char5e/set-character int-id (char5e/from-strict body)]))))
                :context (str "fetch character " int-id)))))
      (ra/make-reaction
       (fn []
         (if int-id
           (get-in @app-db [::char5e/character-map int-id] {})
           (get @app-db :character)))))))

;; Records that a character's server response could not be decoded even after
;; self-heal; the character page reads it to show an in-place recovery panel
;; (with a copyable diagnostic report) instead of a blank sheet. `marker` is the
;; http-safe decode-error map (carries the raw body + reader error); nil clears
;; it on a subsequent successful load.
(reg-event-db
 ::char5e/set-character-load-error
 (fn [db [_ id marker]]
   (if marker
     (assoc-in db [::char5e/character-load-errors id]
               {:error (get marker http/error-key)
                :raw   (get marker http/raw-key)})
     (update db ::char5e/character-load-errors dissoc id))))

(reg-sub
 ::char5e/character-load-error?
 (fn [db [_ id]]
   (contains? (::char5e/character-load-errors db) (when id (js/parseInt id)))))

(reg-sub
 ::char5e/character-load-error-info
 (fn [db [_ id]]
   (get-in db [::char5e/character-load-errors (when id (js/parseInt id))])))

(reg-sub
 ::char5e/character-report-status
 (fn [db [_ id]]
   (get-in db [:character-report-status (when id (js/parseInt id))])))

(reg-sub
 ::char5e/character-changed?
 (fn [[_ id]]
   [(subscribe [::char5e/character id])
    (subscribe [:character])])
 (fn [[saved-character character] _]
   (and (:db/id saved-character)
        (not= character saved-character))))

(reg-sub
 ::char5e/has-selected?
 (fn [db _]
   (->> db ::char5e/selected seq)))

(reg-sub
 ::char5e/selected?
 (fn [db [_ id]]
   (get-in db [::char5e/selected id])))

(reg-sub
 ::char5e/selected
 (fn [db _]
   (get db ::char5e/selected)))

(reg-sub
 ::char5e/selected-plugin-options
 (fn [[_ id] _]
   (subscribe [::char5e/character id]))
 (fn [character _ _]
   (selected-plugin-options character)))

;; ::char5e/template is registered in equipment_subs.cljs (derives from template-selections).
;; Used by autosave_fx.cljs and as input signal for ::char5e/built-template.

(reg-sub
 ::char5e/built-template
 (fn [[_ id] _]
   [(subscribe [::char5e/selected-plugin-options id])
    (subscribe [::char5e/template])])
 (fn [[selected-plugin-options template] _]
   (built-template template selected-plugin-options)))

(reg-sub-raw
 ::char5e/built-character
 (fn [_ [_ id]]
   (debounced-build-sub
    (subscribe [::char5e/character id])
    (subscribe [::char5e/built-template id]))))

(reg-sub
 :message-shown?
 (fn [db _]
   (:message-shown? db)))

(reg-sub
 :login-message-shown?
 (fn [db _]
   (:login-message-shown? db)))

(reg-sub
 :message
 (fn [db _]
   (:message db)))

(reg-sub
 :confirmation-shown?
 (fn [db _]
   (:confirmation-shown? db)))

(reg-sub
 :confirmation-cfg
 (fn [db _]
   (:confirmation-cfg db)))

(reg-sub
 :login-message
 (fn [db _]
   (:login-message db)))

(reg-sub
 :message-type
 (fn [db _]
   (:message-type db)))

(reg-sub
 :device-type
 (fn [db _]
   (:device-type db)))

(reg-sub
 :mobile?
 :<- [:device-type]
 (fn [device-type _]
   (= :mobile device-type)))

(reg-sub
 :warning-hidden
 (fn [db _]
   (:warning-hidden db)))

(def character-subs
  {::char5e/base-swimming-speed char5e/base-swimming-speed
   ::char5e/base-flying-speed char5e/base-flying-speed
   ::char5e/base-land-speed char5e/base-land-speed
   ::char5e/speed-with-armor char5e/land-speed-with-armor
   ::char5e/unarmored-speed-bonus char5e/unarmored-speed-bonus
   ::char5e/max-hit-points char5e/max-hit-points
   ::char5e/current-hit-points char5e/current-hit-points
   ::char5e/hit-point-level-bonus char5e/hit-point-level-bonus
   ::char5e/class-hit-point-level-bonus char5e/class-hit-point-level-bonus
   ::char5e/initiative char5e/initiative
   ::char5e/passive-perception char5e/passive-perception
   ::char5e/character-name char5e/character-name
   ::char5e/proficiency-bonus char5e/proficiency-bonus
   ::char5e/save-bonuses char5e/save-bonuses
   ::char5e/saving-throws char5e/saving-throws
   ::char5e/race char5e/race
   ::char5e/subrace char5e/subrace
   ::char5e/age char5e/age
   ::char5e/sex char5e/sex
   ::char5e/height char5e/height
   ::char5e/weight char5e/weight
   ::char5e/hair char5e/hair
   ::char5e/eyes char5e/eyes
   ::char5e/skin char5e/skin
   ::char5e/alignment char5e/alignment
   ::char5e/background char5e/background
   ::char5e/classes char5e/classes
   ::char5e/levels char5e/levels
   ::char5e/darkvision char5e/darkvision
   ::char5e/skill-profs char5e/skill-proficiencies
   ::char5e/skill-bonuses char5e/skill-bonuses
   ::char5e/skill-expertise char5e/skill-expertise
   ::char5e/tool-profs char5e/tool-proficiencies
   ::char5e/tool-expertise char5e/tool-expertise
   ::char5e/tool-bonus-fn char5e/tool-bonus-fn
   ::char5e/weapon-profs char5e/weapon-proficiencies
   ::char5e/armor-profs char5e/armor-proficiencies
   ::char5e/resistances char5e/damage-resistances
   ::char5e/damage-vulnerabilities char5e/damage-vulnerabilities
   ::char5e/damage-immunities char5e/damage-immunities
   ::char5e/immunities char5e/immunities
   ::char5e/condition-immunities char5e/condition-immunities
   ::char5e/languages char5e/languages
   ::char5e/abilities char5e/ability-values
   ::char5e/race-ability-increases char5e/race-ability-increases
   ::char5e/subrace-ability-increases char5e/subrace-ability-increases
   ::char5e/ability-increases char5e/ability-increases
   ::char5e/ability-bonuses char5e/ability-bonuses
   ::char5e/armor-class char5e/base-armor-class
   ::char5e/armor-class-with-armor char5e/armor-class-with-armor
   ::char5e/armor char5e/normal-armor-inventory
   ::char5e/magic-armor char5e/magic-armor-inventory
   ::char5e/all-armor-inventory char5e/all-armor-inventory
   ::char5e/spells-known char5e/spells-known
   ::char5e/spells-known-modes char5e/spells-known-modes
   ::char5e/spell-slots char5e/spell-slots
   ::char5e/pact-magic? char5e/pact-magic?
   ::char5e/prepares-spells char5e/prepares-spells
   ::char5e/prepare-spell-count-fn char5e/prepare-spell-count-fn
   ::char5e/spell-modifiers char5e/spell-modifiers
   ::char5e/spell-slot-factors char5e/spell-slot-factors
   ::char5e/total-spellcaster-levels char5e/total-spellcaster-levels
   ::char5e/weapons char5e/normal-weapons-inventory
   ::char5e/magic-weapons char5e/magic-weapons-inventory
   ::char5e/equipment char5e/normal-equipment-inventory
   ::char5e/custom-equipment char5e/custom-equipment
   ::char5e/treasure char5e/treasure
   ::char5e/custom-treasure char5e/custom-treasure
   ::char5e/magic-items char5e/magical-equipment-inventory
   ::char5e/traits char5e/traits
   ::char5e/attacks char5e/attacks
   ::char5e/bonus-actions char5e/bonus-actions
   ::char5e/reactions char5e/reactions
   ::char5e/actions char5e/actions
   ::char5e/image-url char5e/image-url
   ::char5e/image-url-failed char5e/image-url-failed
   ::char5e/faction-image-url char5e/faction-image-url
   ::char5e/faction-image-url-failed char5e/faction-image-url-failed
   ::char5e/personality-trait-1 char5e/personality-trait-1
   ::char5e/personality-trait-2 char5e/personality-trait-2
   ::char5e/xps char5e/xps
   ::char5e/ideals char5e/ideals
   ::char5e/bonds char5e/bonds
   ::char5e/flaws char5e/flaws
   ::char5e/description char5e/description
   ::char5e/notes char5e/notes
   ::char5e/critical-hit-values char5e/critical-hit-values
   ::char5e/crit-values-str char5e/crit-values-str
   ::char5e/number-of-attacks char5e/number-of-attacks
   ::char5e/has-weapon-prof char5e/has-weapon-prof
   ::char5e/weapon-attack-modifier-fn char5e/weapon-attack-modifier-fn
   ::char5e/weapon-damage-modifier-fn char5e/weapon-damage-modifier-fn
   ::char5e/best-weapon-attack-modifier-fn char5e/best-weapon-attack-modifier-fn
   ::char5e/best-weapon-damage-modifier-fn char5e/best-weapon-damage-modifier-fn
   ::char5e/total-levels char5e/total-levels
   ::char5e/class-level-fn char5e/class-level-fn
   ::char5e/dual-wield-weapon-fn char5e/dual-wield-weapon-fn
   ::char5e/option-sources char5e/option-sources
   ::char5e/public? char5e/public?
   ::char5e/used-resources char5e/used-resources
   ::char5e/al-illegal-reasons char5e/al-illegal-reasons
   ::char5e/feats char5e/feats
   ::char5e/features-used char5e/features-used
   ::char5e/main-hand-weapon char5e/main-hand-weapon
   ::char5e/off-hand-weapon char5e/off-hand-weapon
   ::char5e/worn-armor char5e/worn-armor
   ::char5e/wielded-shield char5e/wielded-shield
   ::char5e/attuned-magic-items char5e/attuned-magic-items})

(doseq [[sub-key char-fn] character-subs]
  (reg-sub
   sub-key
   (fn [[_ id override-built-char]]
     (if id
       (subscribe [::char5e/built-character id])
       (if override-built-char
         (atom override-built-char)
         (subscribe [:built-character]))))
   (fn [built-char _]
     (char-fn built-char))))

(defn armor-calculations [ac-fn armor shields]
  (for [armor-item (conj armor nil)
         shield (conj shields nil)]
     {:ac (ac-fn armor-item shield)
      :armor armor-item
      :shield shield}))

(reg-sub
 ::char5e/best-armor-combo
 (fn [[_ id]]
   [(subscribe [::char5e/armor-class-with-armor id])
    (subscribe [::char5e/non-shield-armor-details id])
    (subscribe [::char5e/shields-details id])])
 (fn [[ac-fn armor shields]]
   (apply max-key :ac (armor-calculations ac-fn armor shields))))

(reg-sub
 ::char5e/current-armor-class
 (fn [[_ id]]
   [(subscribe [::char5e/armor-class-with-armor id])
    (subscribe [::char5e/worn-armor id])
    (subscribe [::char5e/wielded-shield id])
    (subscribe [::mi5e/all-armor-map])
    (subscribe [::char5e/best-armor-combo id])])
 (fn [[ac-fn armor-kw shield-kw all-armor-map best-armor-combo]]
   ;; for some reason the map has nil as a key
   (if (and (nil? armor-kw)
            (nil? shield-kw))
     (:ac best-armor-combo)
     (let [armor (when armor-kw
                   (all-armor-map armor-kw))
           shield (when shield-kw
                    (all-armor-map shield-kw))]
       (ac-fn armor shield)))))

(reg-sub
 ::char5e/all-armor
 (fn [[_ id]]
   [(subscribe [::char5e/magic-armor id])
    (subscribe [::char5e/armor id])])
 (fn [[magic-armor armor] _]
   (merge magic-armor armor)))

(reg-sub
 ::char5e/non-shield-armor
 (fn [[_ id]]
   [(subscribe [::char5e/all-armor id])
    (subscribe [::mi5e/all-armor-map])])
 (fn [[all-armor all-armor-map]]
   (filter
    (fn [[key]]
      (let [item (all-armor-map key)]
        (not= :shield (:type item))))
    all-armor)))

(reg-sub
 ::char5e/non-shield-armor-details
 (fn [[_ id]]
   [(subscribe [::char5e/non-shield-armor id])
    (subscribe [::mi5e/all-armor-map])])
 (fn [[armor armor-map]]
   (map
    (fn [[key]]
      (armor-map key))
    armor)))

(reg-sub
 ::char5e/shields-details
 (fn [[_ id]]
   [(subscribe [::char5e/shields id])
    (subscribe [::mi5e/all-armor-map])])
 (fn [[armor armor-map]]
   (map
    (fn [[key]]
      (armor-map key))
    armor)))

(reg-sub
 ::char5e/shields
 (fn [[_ id]]
   [(subscribe [::char5e/all-armor id])
    (subscribe [::mi5e/all-armor-map])])
 (fn [[all-armor all-armor-map]]
   (filter
    (fn [[key]]
      (let [item (all-armor-map key)]
        (= :shield (:type item))))
    all-armor)))

(reg-sub
 ::char5e/carried-armor
 (fn [[_ id]]
   (subscribe [::char5e/non-shield-armor id]))
 (fn [armor]
   (filter
    (comp ::char-equip5e/equipped? val)
    armor)))

(reg-sub
 ::char5e/carried-shields
 (fn [[_ id]]
   (subscribe [::char5e/shields id]))
 (fn [armor]
   (filter
    (comp ::char-equip5e/equipped? val)
    armor)))

(reg-sub
 ::char5e/all-weapons
 (fn [[_ id]]
   [(subscribe [::char5e/magic-weapons id])
    (subscribe [::char5e/weapons id])])
 (fn [[magic-weapons weapons] _]
   (merge magic-weapons weapons)))

(reg-sub
 ::char5e/carried-weapons
 (fn [[_ id]]
   (subscribe [::char5e/all-weapons id]))
 (fn [weapons]
   (filter
    (comp ::char-equip5e/equipped? val)
    weapons)))

(reg-sub
 :search-text
 (fn [db _]
   (:search-text db)))

(reg-sub
 :search-results
 (fn [db _]
   (:search-results db)))

(reg-sub
 :search-text?
 :<- [:search-text]
 (fn [search-text _]
   (not (s/blank? search-text))))

(reg-sub
 :orcacle-clicked?
 (fn [db _]
   (:orcacle-clicked? db)))

(reg-sub
 :orcacle-open?
 :<- [:search-text?]
 :<- [:orcacle-clicked?]
 (fn [[search-text? orcacle-clicked?]]
   (or search-text? orcacle-clicked?)))

(reg-sub
 ::char5e/selected-display-tab
 (fn [db _]
   (::char5e/selected-display-tab db)))

(reg-sub
 ::char5e/builder-tab
 (fn [db _]
   (::char5e/builder-tab db)))

(reg-sub
 ::char5e/monster-types
 (fn [_ _]
   (set (map :type monsters5e/monsters))))

(reg-sub
 ::char5e/monster-subtypes
 (fn [_ _]
   (set (mapcat :subtypes monsters5e/monsters))))

(reg-sub
 ::char5e/monster-sizes
 (fn [_ _]
   (set (map :size monsters5e/monsters))))

(reg-sub
 ::char5e/spell-text-filter
 (fn [db _]
   (::char5e/spell-text-filter db)))

(reg-sub
 ::char5e/item-text-filter
 (fn [db _]
   (::char5e/item-text-filter db)))

(reg-sub
 ::char5e/monster-text-filter
 (fn [db _]
   (::char5e/monster-text-filter db)))

(reg-sub
 ::char5e/sorted-spells
 :<- [::spells5e/spells]
 (fn [spells _]
   (common/aloof-sort-by :name spells)))

(reg-sub
 ::char5e/filtered-spells
 :<- [:db]
 :<- [::char5e/sorted-spells]
 (fn [[db sorted-spells] _]
   (or (::char5e/filtered-spells db)
       sorted-spells)))

(reg-sub
 ::char5e/filtered-items
 :<- [:db]
 :<- [::char5e/sorted-items]
 (fn [[db sorted-items] _]
   (or (::char5e/filtered-items db)
       sorted-items)))

(reg-sub
  ::char5e/monster-sort-criteria
  (fn [db _]
    (get db ::char5e/monster-sort-criteria "name")))

(reg-sub
  ::char5e/monster-sort-direction
  (fn [db _]
    (get db ::char5e/monster-sort-direction "asc")))

(reg-sub
  ::char5e/monster-filters
  (fn [db _]
    (get db ::char5e/monster-filter-hidden?)))

(reg-sub
 ::char5e/monster-filter-hidden?
 :<- [::char5e/monster-filters]
 (fn [monster-filters [_ filter value]]
   (get-in monster-filters [filter value] false)))

(reg-sub
 ::char5e/spell-prepared?
 (fn [[_ id] _]
   (subscribe [::char5e/character id]))
 (fn [character [_ id class spell-key]]
   (get-in character
           [::entity/values
            ::char5e/prepared-spells-by-class
            class
            spell-key])))

(reg-sub
 ::char5e/spell-slot-used?
 (fn [[_ id] _]
   (subscribe [::char5e/character id]))
 (fn [character [_ id level i]]
   (get-in character
           [::entity/values
            ::spells5e/slots-used
            (common5e/slot-level-key level)
            i])))

(reg-sub
 ::char5e/all-spell-slots-used
 (fn [[_ id] _]
   (subscribe [::char5e/character id]))
 (fn [character [_ id level i]]
   (get-in character
           [::entity/values
            ::spells5e/slots-used])))

(defn level-slots-used [slots-used level]
  (count
   (get slots-used (common5e/slot-level-key level))))

(reg-sub
 ::char5e/spell-slots-used
 (fn [[_ id] _]
   (subscribe [::char5e/all-spell-slots-used id]))
 (fn [slots-used [_ id level i]]
   (level-slots-used slots-used level)))

(reg-sub
 ::char5e/slot-levels-available
 (fn [[_ id] _]
   [(subscribe [::char5e/all-spell-slots-used id])
    (subscribe [::char5e/spell-slots id])])
 (fn [[slots-used spell-slots] _]
   (map
    first
    (filter
     (fn [[lvl slots]]
       (pos? (- slots (level-slots-used slots-used lvl))))
     spell-slots))))

(reg-sub
 ::char5e/spell-slots-remaining
 (fn [[_ id level] _]
   [(subscribe [::char5e/spell-slots-used id level])
    (subscribe [::char5e/spell-slots id])])
 (fn [[slots-used spell-slots] [_ _ level]]
   (- (or (spell-slots level) 0)
      slots-used)))

(reg-sub
 ::char5e/prepared-spells-by-class
 (fn [[_ id] _]
   (subscribe [::char5e/character id]))
 (fn [character _]
   (get-in character
           [::entity/values
            ::char5e/prepared-spells-by-class])))

(reg-sub
 ::char5e/feature-used?
 (fn [[_ id units nm]]
   (subscribe [::char5e/features-used id]))
 (fn [features-used [_ id units nm]]
   (get-in features-used [units nm])))

(reg-sub
 ::char5e/feature-used-count
 (fn [[_ id units nm]]
   (subscribe [::char5e/features-used id]))
 (fn [features-used [_ id units nm]]
   (->> (get-in features-used [units])
        (filter #(s/starts-with? % nm))
        first)))

(reg-sub
 :theme
 (fn [db _]
   (get-in db [:user-data :theme])))

(reg-sub
 ::show-class-source-suffix
 (fn [db _]
   (boolean (get-in db [:user-data :show-class-source-suffix]))))

(reg-sub
 ::mi5e/builder-item
 (fn [db _]
   (::mi5e/builder-item db)))

(reg-sub
 ::mi5e/item-types
 (fn [db _]
   [:wondrous-item :weapon ::mi5e/armor :ring :wand :rod :scroll :potion :other]))

(reg-sub
 ::mi5e/rarities
 (fn [db _]
   [:common :uncommon :rare :very-rare :legendary :varies]))

(doseq [toggle-mod [:damage-resistance :damage-vulnerability :damage-immunity :condition-immunity]]
  (reg-sub
   (keyword "orcpub.dnd.e5.magic-items" (str "has-" (name toggle-mod) "?"))
   :<- [::mi5e/builder-item]
   (fn [item [_ type]]
     (get-in item [::mi5e/internal-modifiers
                   toggle-mod
                   type]))))

(reg-sub
 ::mi5e/item-ability-bonus
 :<- [::mi5e/builder-item]
 (fn [item [_ type ability]]
   (let [mod-cfg (mod-cfg (if (= type :becomes-at-least)
                                   :ability-override
                                   :ability)
                                 ability)
         modifiers (default-mod-set (::mi5e/internal-modifiers item))
         modifier (get modifiers mod-cfg)
         args (::mod/args modifier)]
     (second args))))

(reg-sub
 ::mi5e/item-ability-mod-type
 :<- [::mi5e/builder-item]
 (fn [item [_ type ability]]
   (let [mod-cfg (mod-cfg (if (= type :becomes-at-least)
                                   :ability-override
                                   :ability)
                                 ability)
         modifiers (default-mod-set (::mi5e/internal-modifiers item))
         modifier (get modifiers mod-cfg)
         args (::mod/args modifier)]
     (second args))))

(reg-sub
 ::mi5e/item-damage-die
 :<- [::mi5e/builder-item]
 (fn [item _]
   (get item ::weapon5e/damage-die)))

(reg-sub
 ::mi5e/item-damage-die-count
 :<- [::mi5e/builder-item]
 (fn [item _]
   (get item ::weapon5e/damage-die-count)))

(reg-sub
 ::mi5e/item-versatile-damage-die
 :<- [::mi5e/builder-item]
 (fn [item _]
   (get-in item [::weapon5e/versatile ::weapon5e/damage-die])))

(reg-sub
 ::mi5e/item-versatile-damage-die-count
 :<- [::mi5e/builder-item]
 (fn [item _]
   (get-in item [::weapon5e/versatile ::weapon5e/damage-die-count])))

(reg-sub
 ::mi5e/item-range-min
 :<- [::mi5e/builder-item]
 (fn [item _]
   (get-in item [::weapon5e/range ::weapon5e/min])))

(reg-sub
 ::mi5e/item-range-max
 :<- [::mi5e/builder-item]
 (fn [item _]
   (get-in item [::weapon5e/range ::weapon5e/max])))

(reg-sub
 ::mi5e/item-weapon-type
 :<- [::mi5e/builder-item]
 (fn [item _]
   (get item ::weapon5e/type)))

(reg-sub
 ::mi5e/item-melee-ranged
 :<- [::mi5e/builder-item]
 (fn [item _]
   (cond
     (get item ::weapon5e/melee?) :melee
     (get item ::weapon5e/ranged?) :ranged
     :else nil)))

(reg-sub
 ::mi5e/item-damage-type
 :<- [::mi5e/builder-item]
 (fn [item _]
   (get item ::weapon5e/damage-type)))

(reg-sub
 ::mi5e/item-finesse?
 :<- [::mi5e/builder-item]
 (fn [item _]
   (get item ::weapon5e/finesse?)))

(reg-sub
 ::mi5e/item-reach?
 :<- [::mi5e/builder-item]
 (fn [item _]
   (get item ::weapon5e/reach?)))

(reg-sub
 ::mi5e/item-ammunition?
 :<- [::mi5e/builder-item]
 (fn [item _]
   (get item ::weapon5e/ammunition?)))

(reg-sub
 ::mi5e/item-special?
 :<- [::mi5e/builder-item]
 (fn [item _]
   (get item ::weapon5e/special?)))

(reg-sub
 ::mi5e/item-loading?
 :<- [::mi5e/builder-item]
 (fn [item _]
   (get item ::weapon5e/loading?)))

(reg-sub
 ::mi5e/item-versatile?
 :<- [::mi5e/builder-item]
 (fn [item _]
   (get item ::weapon5e/versatile)))

(reg-sub
 ::mi5e/item-heavy?
 :<- [::mi5e/builder-item]
 (fn [item _]
   (get item ::weapon5e/heavy?)))

(reg-sub
 ::mi5e/item-light?
 :<- [::mi5e/builder-item]
 (fn [item _]
   (get item ::weapon5e/light?)))

(reg-sub
 ::mi5e/item-two-handed?
 :<- [::mi5e/builder-item]
 (fn [item _]
   (get item ::weapon5e/two-handed?)))

(reg-sub
 ::mi5e/item-thrown?
 :<- [::mi5e/builder-item]
 (fn [item _]
   (get item ::weapon5e/thrown?)))

(reg-sub
 ::mi5e/ability-mod-type
 :<- [::mi5e/builder-item]
 (fn [item [_ ability-kw]]
   (get-in item
           [::mi5e/internal-modifiers
            :ability
            ability-kw
            :type])))

(reg-sub
 ::mi5e/ability-mod-value
 :<- [::mi5e/builder-item]
 (fn [item [_ ability-kw]]
   (get-in item
           [::mi5e/internal-modifiers
            :ability
            ability-kw
            :value])))

(reg-sub
 ::mi5e/speed-mod-type
 :<- [::mi5e/builder-item]
 (fn [item [_ speed-type-kw]]
   (get-in item
           [::mi5e/internal-modifiers
            speed-type-kw
            :type])))

(reg-sub
 ::mi5e/speed-mod-value
 :<- [::mi5e/builder-item]
 (fn [item [_ speed-type-kw]]
   (get-in item
           [::mi5e/internal-modifiers
            speed-type-kw
            :value])))

(reg-sub
 ::mi5e/save-mod-value
 :<- [::mi5e/builder-item]
 (fn [item [_ ability-kw]]
   (get-in item
           [::mi5e/internal-modifiers
            :save
            ability-kw
            :value])))

(reg-sub
 ::mi5e/has-subtype?
 :<- [::mi5e/builder-item]
 (fn [item [_ type]]
   (get (::mi5e/subtypes item) type)))

(reg-sub
 ::char5e/options-shown?
 (fn [db _]
   (get db ::char5e/options-shown?)))

(reg-sub
 ::char5e/options-component
 (fn [db _]
   (get db ::char5e/options-component)))

(reg-sub
 ::char5e/print-spell-cards?
 (fn [db _]
   (-> db ::char5e/exclude-spell-cards-print? not)))

(reg-sub
 ::char5e/print-spell-card-dc-mod?
 (fn [db _]
   (-> db ::char5e/exclude-spell-cards-by-dc-mod? not)))

(reg-sub
 ::char5e/print-card-back-logo?
 (fn [db _]
   ;; Positive flag, default off — card backs stay text-only unless opted in.
   (boolean (::char5e/print-card-back-logo? db))))

(reg-sub
 ::char5e/card-back-logo-faded?
 (fn [db _]
   ;; Card-back logo treatment: default (false) = solid black; true = faded
   ;; brand-orange watermark (color printers). Only meaningful when the logo is
   ;; on and printer-friendly B&W mode is off.
   (boolean (::char5e/card-back-logo-faded? db))))

(reg-sub
 ::char5e/print-bw?
 (fn [db _]
   ;; Printer-friendly monochrome: solid-black spell-card icons + forced
   ;; solid-black card-back logo (no color anywhere). Default off.
   (boolean (::char5e/print-bw? db))))

(reg-sub
 ::char5e/bw-faded?
 (fn [db _]
   ;; B&W icon style: default (false) = solid black with white-halo labels;
   ;; true = faded grayscale icons. Only meaningful when print-bw? is on.
   (boolean (::char5e/bw-faded? db))))


(reg-sub
 ::char5e/print-character-sheet?
 (fn [db _]
   (-> db ::char5e/exclude-character-sheet-print? not)))

(reg-sub
 ::char5e/print-prepared-spells?
 (fn [db _]
   (get db ::char5e/print-prepared-spells?)))

(reg-sub
 ::char5e/print-large-abilities?
 (fn [db _]
   (get db ::char5e/print-large-abilities?)))

(reg-sub
 ::char5e/print-character-sheet-style?
 (fn [db [_ id]]
   (get db ::char5e/print-character-sheet-style?)))

(reg-sub
 ::char5e/delete-confirmation-shown?
 (fn [db [_ id]]
   (get-in db [::char5e/delete-confirmation-shown? id])))

(reg-sub
 ::char5e/delete-plugin-confirmation-shown?
 (fn [db _]
   (get-in db [::char5e/delete-plugin-confirmation-shown?])))

;item delete confirmation
(reg-sub
 ::mi5e/delete-confirmation-shown?
 (fn [db [_ id]]
   (get-in db [::mi5e/delete-confirmation-shown? id])))

(reg-sub
 ::char5e/newb-char-data
 (fn [db _]
   (get db ::char5e/newb-char-data)))

(reg-sub
 ::char5e/current-question
 (fn [db _]
   (get db ::char5e/current-question (char-dec5e/next-question {}))))

(reg-sub
 ::char5e/has-question-history?
 (fn [db _]
   (seq (get-in db [::char5e/question-history :newb-char-data]))))

;; ============================================================================
;; Import Log Subscriptions
;; ============================================================================

(reg-sub
 :import-log
 (fn [db _]
   (:import-log db)))

(reg-sub
 :import-log-shown?
 (fn [db _]
   (get-in db [:import-log :panel-shown?])))

(reg-sub
 :import-log-has-content?
 (fn [db _]
   (let [log (:import-log db)]
     (or (seq (:changes log))
         (seq (:errors log))
         (seq (:skipped-items log))))))

;; ============================================================================
;; Conflict Resolution Subscriptions
;; ============================================================================

(reg-sub
 :conflict-resolution
 (fn [db _]
   (:conflict-resolution db)))

(reg-sub
 :conflict-resolution-active?
 (fn [db _]
   (get-in db [:conflict-resolution :active?])))

(reg-sub
 :conflict-resolution-conflicts
 (fn [db _]
   (get-in db [:conflict-resolution :conflicts])))

(reg-sub
 :conflict-resolution-decisions
 (fn [db _]
   (get-in db [:conflict-resolution :decisions])))

;; ============================================================================
;; Export Warning Modal Subscriptions
;; ============================================================================

(reg-sub
 :export-warning
 (fn [db _]
   (:export-warning db)))

(reg-sub
 :export-warning-edits
 (fn [db _]
   (get-in db [:export-warning :edits])))

(reg-sub
 :export-warning-show-as-is?
 (fn [db _]
   (get-in db [:export-warning :show-export-as-is?])))

;; ============================================================================
;; Missing Content Detection Subscriptions
;; ============================================================================

(reg-sub
 ::char5e/available-content
 (fn [_]
   ;; Subscribe to all content types we need to check against.
   ;; Uses plugin-* subs (not the full subs) because SRD content is
   ;; hardcoded and handled by builtin-* sets in content_reconciliation.
   [(subscribe [:orcpub.dnd.e5.classes/plugin-classes])
    (subscribe [:orcpub.dnd.e5.classes/plugin-subclasses])
    (subscribe [:orcpub.dnd.e5.races/plugin-races])
    (subscribe [:orcpub.dnd.e5.races/plugin-subraces])
    (subscribe [:orcpub.dnd.e5.backgrounds/plugin-backgrounds])
    (subscribe [:orcpub.dnd.e5.feats/plugin-feats])])
 (fn [[classes subclasses races subraces backgrounds feats]]
   {:classes classes
    :subclasses subclasses
    :races races
    :subraces subraces
    :backgrounds backgrounds
    :feats feats}))

(reg-sub
 ::char5e/missing-content-report
 (fn [_]
   [(subscribe [:character])
    (subscribe [::char5e/available-content])])
 (fn [[character available-content]]
   (when character
     (content-recon/generate-missing-content-report character available-content))))

(reg-sub
 ::char5e/has-missing-content?
 :<- [::char5e/missing-content-report]
 (fn [report]
   (:has-missing? report)))

;; ---- Character List Filter Subscriptions -----------------------------------

(reg-sub
 ::char5e/char-name-filter
 (fn [db _]
   (get db ::char5e/char-name-filter "")))

(reg-sub
 ::char5e/char-level-filters
 (fn [db _]
   (get db ::char5e/char-level-filters #{})))

(reg-sub
 ::char5e/char-class-filters
 (fn [db _]
   (get db ::char5e/char-class-filters #{})))

(reg-sub
 ::char5e/char-has-portrait?
 (fn [db _]
   (get db ::char5e/char-has-portrait?)))

(reg-sub
 ::char5e/char-has-faction-pic?
 (fn [db _]
   (get db ::char5e/char-has-faction-pic?)))

(reg-sub
 ::char5e/char-classes-available
 :<- [::char5e/characters]
 (fn [characters _]
   (->> characters
        (mapcat ::char5e/classes)
        (map ::char5e/class-name)
        (remove nil?)
        distinct
        sort
        vec)))

(reg-sub
 ::char5e/char-levels-available
 :<- [::char5e/characters]
 (fn [characters _]
   (->> characters
        (mapcat ::char5e/classes)
        (map ::char5e/level)
        (remove nil?)
        distinct
        sort
        vec)))

(reg-sub
 ::char5e/filtered-characters
 :<- [::char5e/characters]
 :<- [::char5e/char-name-filter]
 :<- [::char5e/char-level-filters]
 :<- [::char5e/char-class-filters]
 :<- [::char5e/char-has-portrait?]
 :<- [::char5e/char-has-faction-pic?]
 (fn [[characters name-filter level-filters class-filters has-portrait? has-faction-pic?] _]
   (char-filter/filter-characters characters name-filter level-filters class-filters has-portrait? has-faction-pic?)))
