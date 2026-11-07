(ns orcpub.test-runner
  (:require [cljs.test :refer-macros [run-tests]]
            ;; .cljc tests (run on both JVM and CLJS)
            [orcpub.common-test]
            [orcpub.dnd.e5.event-utils-test]
            [orcpub.dnd.e5.compute-test]
            [orcpub.dnd.e5.hunter-evasion-test]
            ;; CLJS-only re-frame integration tests (events-test now also holds
            ;; the toggle-corruption stress harness)
            [orcpub.dnd.e5.events-test]
            [orcpub.dnd.e5.subs-test]
            [orcpub.dnd.e5.content-reconciliation-test]
            [orcpub.dnd.e5.views-test]
            ;; storage layer (resilient loader read path)
            [orcpub.dnd.e5.db-test]
            ;; orcbrew import/export validation
            [orcpub.dnd.e5.orcbrew-validation-test]))

(defn -main []
  (run-tests 'orcpub.common-test
             'orcpub.dnd.e5.event-utils-test
             'orcpub.dnd.e5.compute-test
             'orcpub.dnd.e5.hunter-evasion-test
             'orcpub.dnd.e5.events-test
             'orcpub.dnd.e5.subs-test
             'orcpub.dnd.e5.content-reconciliation-test
             'orcpub.dnd.e5.views-test
             'orcpub.dnd.e5.db-test
             'orcpub.dnd.e5.orcbrew-validation-test))

;; Auto-run when figwheel reloads
(defn ^:after-load on-reload []
  (-main))

(-main)
