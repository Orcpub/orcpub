(ns orcpub.template
  (:require [clojure.spec.alpha :as spec]
            [clojure.spec.test.alpha :as stest]
            [orcpub.modifiers :as modifiers]
            [orcpub.common :as common]))

(spec/def ::name string?)
(spec/def ::key keyword?)
(spec/def ::min (spec/nilable (spec/int-in 0 100)))
(spec/def ::max (spec/nilable (spec/int-in 1 100)))
(spec/def ::attribute (spec/keys :req [::name ::key]))
(spec/def ::attributes (spec/+ ::attribute))
(spec/def ::derived-value (spec/or :func (spec/fspec :args (spec/cat :entity map?))
                                   :keyword keyword?))
(spec/def ::derived-attribute (spec/keys :req [::name ::key ::derived-value]))
(spec/def ::derived-attributes (spec/+ ::derived-attribute))
(spec/def ::modifiers (spec/+ ::modifiers/modifier))
(spec/def ::option (spec/keys :req [::name ::key]
                              :opt [::modifiers ::selections]))
(spec/def ::options (spec/+ ::option))
(spec/def ::selection (spec/keys :req [::name ::key ::options]
                                 :opt [::min ::max]))
(spec/def ::selections (spec/* ::selection))
(spec/def ::template (spec/keys :opt [::attributes ::derived-attributes ::selections]))

(spec/def ::modifier-map-value (spec/or :modifiers ::modifiers
                                        :modifier-map ::modifier-map))
(spec/def ::modifier-map-entry (spec/tuple keyword? ::modifier-map-value))
(spec/def ::modifier-map (spec/map-of keyword? (spec/or :modifier-map-value ::modifier-map-value
                                                        :min ::min
                                                        :max ::max)))

(spec/def ::tags set?)

(spec/def ::selection-cfg (spec/keys :req-un [::tags ::options]))

(defn selection-cfg [{:keys [name key source page order options help min sequential? multiselect? ui-fn new-item-fn prereq-fn tags ref icon different? require-value? show-if-zero? adder-key-fn] :as cfg}]
  (let [max (if (find cfg :max) (:max cfg) 1)]
    {::name name
     ::key (or key (common/name-to-kw name))
     ::source (or source :phb)
     ::page page
     ::order order
     ::options options
     ::help help
     ::min (or min 1)
     ::max (or max (when (not multiselect?) (or min 1)))
     ::sequential? (boolean sequential?)
     ::multiselect? (or multiselect? (and (some? max) (> max 1)))
     ::ui-fn ui-fn
     ::tags tags
     ::ref ref
     ::icon icon
     ::new-item-fn new-item-fn
     ::adder-key-fn adder-key-fn
     ::prereq-fn prereq-fn
     ::different? different?
     ::show-if-zero? show-if-zero?
     ::require-value? require-value?}))


(spec/fdef
 selection-cfg
 :args (spec/cat :cfg ::selection-cfg)
 :ret ::selection)

#_(stest/instrument `selection-cfg)

(defn option-prereq [explanation func & [hide-if-fail?]]
  {::label explanation
   ::prereq-fn func
   ::hide-if-fail? hide-if-fail?})

(defn option-cfg [{:keys [:db/id name key help selections modifiers associated-options prereqs order ui-fn icon select-fn edit-event plugin-source] :as cfg}]
  {::id id
   ::name name
   ::key (or key (common/name-to-kw name))
   ::help help
   ::order order
   ::selections selections
   ::modifiers modifiers
   ::associated-options associated-options
   ::prereqs prereqs
   ::ui-fn ui-fn
   ::select-fn select-fn
   ::icon icon
   ::edit-event edit-event
   ::plugin-source plugin-source})

(declare make-modifier-map-from-selections)
(declare make-plugin-map-from-selections)

(defn make-modifier-map-entry-from-option [option]
  [(::key option)
   (let [modifiers option
         selections (::selections option)]
     (if selections
       (merge (make-modifier-map-from-selections selections) modifiers)
       modifiers))])

(spec/fdef
 make-modifier-map-entry-from-option
 :args ::option
 :ret ::modifier-map-entry)

#_(defn make-modifier-map-entry-from-selection [{:keys [ref key min max options]}]
  (let [path (or ref key)]
    (assoc-in {} ))
  [(or ref key)
   (into (select-keys selection [::min ::max])
         (map make-modifier-map-entry-from-option (::options selection)))])

#_(spec/fdef
 make-modifier-map-entry-from-selection
 :args ::selection
 :ret ::modifier-map-entry)

(defn selections-or-options [item]
  (or (::selections item)
      (::options item)))

(defn get-ref-selections [template]
  (filter
   ::ref
   (tree-seq selections-or-options
             selections-or-options
             template)))

(defn make-modifier-map-from-selections [selections]
  (reduce
   (fn [m {:keys [::ref ::key ::min ::max ::options] :as selection}]
     (let [path (if ref (if (sequential? ref) ref [ref]) [key])]
       (assoc-in m
                 path
                 (into (select-keys selection [::min ::max])
                       (map make-modifier-map-entry-from-option options)))))
   {}
   selections)
  #_(into {} (map make-modifier-map-entry-from-selection selections)))

(spec/fdef
 make-modifier-map-entry-from-selections
 :args ::selections
 :ret ::modifier-map)

(defn make-modifier-map [template]
  (let [ref-selections (get-ref-selections template)]
    (make-modifier-map-from-selections (concat (::selections template) ref-selections))))

(spec/fdef
 make-modifier-map
 :args ::template
 :ret ::modifier-map)

(spec/fdef make-modifier-map
           :args (spec/cat :template ::template)
           :ret ::modifier-map)
