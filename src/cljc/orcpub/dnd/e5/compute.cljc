(ns ^{:doc "Pure computation helpers that replicate subscription chains for use
     in event handlers. Avoids @(subscribe ...) outside reactive context.
     Extracted from events.cljs so they can be tested as .cljc (JVM + CLJS)."}
  orcpub.dnd.e5.compute
  (:require [orcpub.common :as common]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.magic-items :as mi]
            [clojure.string :as s]))

(defn compute-plugin-vals
  "Filters out disabled plugins and disabled entries within plugins.
   Replicates ::e5/plugin-vals subscription."
  [plugins]
  (map
   (fn [p]
     (into
      {}
      (map
       (fn [[type-k type-m]]
         [type-k
          (if (coll? type-m)
            (into
             {}
             (remove
              (fn [[_k {:keys [disabled?]}]]
                disabled?)
              type-m))
            type-m)])
       p)))
   (filter (comp not :disabled?)
           (vals plugins))))

(defn compute-sorted-spells
  "Computes sorted spells from db. Replicates the chain:
   ::e5/plugins -> ::e5/plugin-vals -> ::spells5e/plugin-spells ->
   ::spells5e/spells -> ::char5e/sorted-spells."
  [db]
  (let [plugins (get db :plugins)
        plugin-vals (compute-plugin-vals plugins)
        plugin-spells (map
                       (fn [spell]
                         (assoc spell :edit-event [::spells/edit-spell spell]))
                       (mapcat (comp vals ::e5/spells) plugin-vals))
        all-spells (into
                    (sorted-set-by (fn [x y] (compare (:key x) (:key y))))
                    (concat
                     (reverse plugin-spells)
                     spells/spells))]
    (common/aloof-sort-by :name all-spells)))

(def ^:private sorted-static-items
  "Pre-sorted static magic items. Mirrors equipment_subs/sorted-items delay."
  (delay (sort-by mi/name-key mi/magic-items)))

(defn compute-sorted-items
  "Computes sorted items from db. Replicates the chain:
   ::mi5e/custom-items -> ::mi5e/expanded-custom-items -> ::char5e/sorted-items."
  [db]
  (let [custom-items (get db ::mi/custom-items [])
        expanded (mi/expand-magic-items custom-items)]
    (concat expanded @sorted-static-items)))

(defn filter-by-name-xform
  "Returns a transducer that filters items by name matching filter-text."
  [filter-text name-key]
  (let [pattern (re-pattern (str ".*" (s/lower-case filter-text) ".*"))]
    (filter
     (fn [x]
       ;; common/lower-case: an item with no name folds to "" (no match), not a crash
       (re-matches pattern (common/lower-case (name-key x)))))))

(defn filter-spells
  "Filters and sorts spells whose :name matches filter-text."
  [filter-text sorted-spells]
  (sort-by
   :name
   (sequence (filter-by-name-xform filter-text :name) sorted-spells)))

(defn filter-items
  "Filters and sorts items whose ::mi/name matches filter-text."
  [filter-text sorted-items]
  (sort-by
   mi/name-key
   (sequence (filter-by-name-xform filter-text mi/name-key) sorted-items)))
