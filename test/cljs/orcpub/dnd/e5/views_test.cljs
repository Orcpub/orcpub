(ns orcpub.dnd.e5.views-test
  "Tests for the pure helpers behind the character-display fail-soft logic.

   The UI components (error-boundary, render-guard, feature-render-error,
   character-health-warning) are React/Reagent class components and are not
   meaningfully unit-testable without a DOM + a mounted re-frame app, so they
   are not covered here. The one piece of pure, side-effect-free logic that
   drives the diagnostics — blank-feature-name? — is tested directly."
  (:require [cljs.test :refer-macros [deftest testing is]]
            [orcpub.dnd.e5.views :as views]))

(deftest blank-feature-name?-test
  (testing "nil names are blank"
    (is (true? (boolean (views/blank-feature-name? nil)))))
  (testing "empty string is blank"
    (is (true? (boolean (views/blank-feature-name? "")))))
  (testing "whitespace-only string is blank"
    (is (true? (boolean (views/blank-feature-name? "   "))))
    (is (true? (boolean (views/blank-feature-name? "\t\n")))))
  (testing "a real name is not blank"
    (is (false? (boolean (views/blank-feature-name? "Name")))))
  (testing "a name padded with whitespace is not blank"
    (is (false? (boolean (views/blank-feature-name? "  Rage  "))))))
