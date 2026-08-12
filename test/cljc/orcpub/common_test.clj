;; ============================================================================
;; NAMESPACE COLLISION — READ THIS BEFORE "DISCOVERING" IT AGAIN
;; ----------------------------------------------------------------------------
;; This file AND `common_test.cljc` (same dir) both declare `orcpub.common-test`.
;; Clojure resolves `.clj` before `.cljc`, so under `lein test` (clj/cljc) ONLY
;; THIS FILE loads — the .cljc's ~14 deftests (aloof-sort-by, name-to-kw guard,
;; sanitize-edn-colons, cljs.reader round-trips) are SHADOWED and DO NOT RUN
;; here. That is why `lein test orcpub.common-test` reports just 1 test.
;;
;; The .cljc suite runs under the CLJS test runner (`test_runner.cljs` requires
;; `orcpub.common-test`; in cljs there is no .clj to shadow it) via `lein fig:test`.
;; So both suites DO get exercised — just by different runners. This is expected,
;; not a bug. To make `lein test` cover the .cljc too, fold these into the .cljc
;; and delete this file. Until then: don't re-file this as a mystery.
;; ============================================================================
(ns orcpub.common-test
  (:require [clojure.test :refer [is deftest]]
            [clojure.spec.test.alpha :as stest]
            [orcpub.common :as common]))

(deftest test-add-namespaces-to-keys
  (stest/instrument `common/add-namespaces-to-keys)
  (is (= {:db/id 88
          :a.b.c/x 1
          :a.b.c/y "sdlk"}
         (common/add-namespaces-to-keys "a.b.c" {:db/id 88
                                                 :x 1
                                                 :y "sdlk"})))
  (stest/instrument `common/add-namespaces-to-keys))
