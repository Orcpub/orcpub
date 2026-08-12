(ns orcpub.dnd.e5.subs-test
  "Tests for API-backed subscriptions (reg-sub-raw).

   These subscriptions gate HTTP calls on the presence of an auth token.
   When no token exists in app-db, the subscription should return an empty
   vector without making any network request (no :set-loading dispatch).

   PATTERN — testing a reg-sub-raw guard:
     1. Reset app-db to a known state (with or without token)
     2. Deref the subscription to trigger it
     3. Assert the return value and check for side effects (:loading)"
  (:require [cljs.test :refer-macros [deftest testing is use-fixtures]]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [cljs-http.client :as http]
            [cljs.core.async :as async]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.party :as party5e]
            [orcpub.dnd.e5.folder :as folder5e]
            ;; Side effect: registers subscriptions
            [orcpub.dnd.e5.subs]))

;; ---------------------------------------------------------------------------
;; Fixtures
;; ---------------------------------------------------------------------------

(defn reset-db!
  "Reset app-db before each test to prevent state leakage."
  []
  (reset! app-db {})
  ;; Clear subscription cache so reg-sub-raw re-evaluates
  (rf/clear-subscription-cache!))

(use-fixtures :each {:before reset-db!})

;; ---------------------------------------------------------------------------
;; ::char5e/characters — token guard
;; ---------------------------------------------------------------------------

(deftest characters-no-token-returns-empty
  (testing "without auth token, subscription returns [] without HTTP call"
    (reset! app-db {})
    (let [result @(rf/subscribe [::char5e/characters])]
      ;; Should return empty vector (default)
      (is (= [] result))
      ;; :set-loading should NOT have been dispatched (go block skipped)
      (is (nil? (:loading @app-db))
          "No loading state should be set without token"))))

(deftest characters-no-token-login-optional
  (testing "login-optional? param doesn't bypass the token guard"
    (reset! app-db {})
    (let [result @(rf/subscribe [::char5e/characters true])]
      (is (= [] result))
      (is (nil? (:loading @app-db))))))

(deftest characters-with-token-empty-is-nil
  (testing "with token but no cached data, returns []"
    (reset! app-db {:user-data {:token "test-token"}})
    ;; The go block will fire and try HTTP (which will fail in test env),
    ;; but the reaction should still return [] since no data is cached yet
    (let [result @(rf/subscribe [::char5e/characters])]
      (is (= [] result)))))

;; ---------------------------------------------------------------------------
;; ::party5e/parties — token guard
;; ---------------------------------------------------------------------------

(deftest parties-no-token-returns-empty
  (testing "without auth token, subscription returns [] without HTTP call"
    (reset! app-db {})
    (let [result @(rf/subscribe [::party5e/parties])]
      (is (= [] result))
      (is (nil? (:loading @app-db))
          "No loading state should be set without token"))))

(deftest parties-no-token-login-optional
  (testing "login-optional? param doesn't bypass the token guard"
    (reset! app-db {})
    (let [result @(rf/subscribe [::party5e/parties true])]
      (is (= [] result))
      (is (nil? (:loading @app-db))))))

(deftest parties-with-token-empty-is-nil
  (testing "with token but no cached data, returns []"
    (reset! app-db {:user-data {:token "test-token"}})
    (let [result @(rf/subscribe [::party5e/parties])]
      (is (= [] result)))))

;; ---------------------------------------------------------------------------
;; :user — token guard
;;
;; Previously checked [:user :token] which was the wrong path.
;; Fixed to check [:user-data :token] (same as auth-headers).
;; ---------------------------------------------------------------------------

(deftest user-no-token-returns-empty
  (testing "without auth token, subscription returns [] without HTTP call"
    (reset! app-db {})
    (let [result @(rf/subscribe [:user])]
      (is (= [] result)))))

(deftest user-stale-user-no-token-still-guarded
  (testing "a stale :user key with no token must NOT trigger an HTTP fetch"
    ;; REGRESSION GUARD, rewritten to assert the right thing. The HTTP fetch is
    ;; gated on [:user-data :token] (canonical, same as auth-headers); a leftover
    ;; [:user] key (no :token) must not slip past it. The OLD assertion
    ;; `(= [] result)` was WRONG: the :user sub passes through `(get db :user [])`,
    ;; so a populated :user returns that map, not [] — it never tested the guard.
    ;; Assert the guard DIRECTLY: http/get is never called.
    (reset! app-db {:user {:name "stale-user"}})
    (let [called? (atom false)]
      (with-redefs [http/get (fn [& _] (reset! called? true) (async/chan))]
        (let [result @(rf/subscribe [:user])]
          (is (false? @called?) "no token → no HTTP, despite the stale :user key")
          (is (= {:name "stale-user"} result) "sub passes the db value through unchanged"))))))

(deftest user-with-token-returns-default
  (testing "with token, subscription fires (returns default until HTTP resolves)"
    (reset! app-db {:user-data {:token "test-token"}})
    (let [result @(rf/subscribe [:user])]
      (is (= [] result)))))

;; ---------------------------------------------------------------------------
;; ::folder5e/folders — token guard
;; ---------------------------------------------------------------------------

(deftest folders-no-token-returns-empty
  (testing "without auth token, subscription returns [] without HTTP call"
    (reset! app-db {})
    (let [result @(rf/subscribe [::folder5e/folders])]
      (is (= [] result))
      (is (nil? (:loading @app-db))
          "No loading state should be set without token"))))

(deftest folders-with-token-returns-default
  (testing "with token but no cached data, returns []"
    (reset! app-db {:user-data {:token "test-token"}})
    (let [result @(rf/subscribe [::folder5e/folders])]
      (is (= [] result)))))
