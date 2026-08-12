(ns orcpub.dnd.e5.db-test
  "Storage-layer tests for the resilient loader's read path (db.cljs).

   B2.0/F5: `get-local-storage-item` used to DELETE any key whose contents
   wouldn't parse. For the homebrew slots (plugins + its quarantine
   companion) that destroyed data the loader was supposed to preserve — a
   truncated or quota-cut blob was gone before anything could rescue it. These
   tests pin the new behavior: unreadable homebrew is moved to a ':corrupt' slot
   and the active slot cleared (never destroyed), while non-homebrew slots keep
   the old remove-on-unreadable behavior.

   Requires a real localStorage — runs in the headless chromium cljs suite."
  (:require [cljs.test :refer-macros [deftest testing is use-fixtures]]
            [orcpub.dnd.e5.db :as db]))

(defn- clear-storage! []
  (.clear js/window.localStorage))

(use-fixtures :each {:before clear-storage! :after clear-storage!})

(def ^:private unreadable
  ;; Truncated EDN — read-string throws on this (unbalanced/incomplete), exactly
  ;; like a write cut off by a quota error mid-serialization.
  "{\"Pack\" {:orcpub.dnd.e5/classes {:artificer {:name \"Artif")

(deftest unreadable-plugins-preserved-not-destroyed
  (testing "an unreadable plugins blob is moved to :corrupt and the active slot cleared"
    (.setItem js/window.localStorage db/local-storage-plugins-key unreadable)
    (let [result (db/get-local-storage-item db/local-storage-plugins-key)]
      (is (nil? result) "returns nil so the loader simply loads no homebrew")
      (is (nil? (.getItem js/window.localStorage db/local-storage-plugins-key))
          "active slot cleared so a poison value can't brick boot")
      (is (= unreadable
             (.getItem js/window.localStorage
                       (db/corrupt-slot-key db/local-storage-plugins-key)))
          "raw bytes preserved verbatim for recovery — NOT deleted"))))

(deftest unreadable-quarantine-also-preserved
  (testing "the rejected companion is protected the same way as plugins"
    (let [k db/local-storage-plugins-rejected-key]
      (clear-storage!)
      (.setItem js/window.localStorage k unreadable)
      (is (nil? (db/get-local-storage-item k)))
      (is (nil? (.getItem js/window.localStorage k)) "active slot cleared")
      (is (= unreadable (.getItem js/window.localStorage (db/corrupt-slot-key k)))
          "preserved for recovery"))))

(deftest unreadable-non-homebrew-still-removed
  (testing "a non-homebrew slot (character) keeps the old remove-on-unreadable behavior"
    (.setItem js/window.localStorage "character" unreadable)
    (is (nil? (db/get-local-storage-item "character")))
    (is (nil? (.getItem js/window.localStorage "character")) "removed")
    (is (nil? (.getItem js/window.localStorage (db/corrupt-slot-key "character")))
        "no :corrupt copy is made for non-homebrew slots")))

(deftest set-item-returns-success-boolean
  (testing "true on a successful write"
    (is (true? (db/set-item "test-key" "value"))))
  (testing "false when the write throws (e.g. QuotaExceededError)"
    ;; A quota failure must be observable so the caller can warn instead of
    ;; silently dropping the user's content.
    (let [orig (.-setItem js/window.localStorage)]
      (try
        (set! (.-setItem js/window.localStorage)
              (fn [_ _] (throw (js/Error. "QuotaExceededError"))))
        (is (false? (db/set-item "test-key" "value")))
        (finally
          (set! (.-setItem js/window.localStorage) orig))))))

(deftest readable-plugins-untouched
  (testing "a readable plugins blob parses normally and nothing is moved/cleared"
    (let [good "{\"Pack\" {:orcpub.dnd.e5/classes {:artificer {:name \"Artificer\" :key :artificer :option-pack \"Pack\"}}}}"]
      (.setItem js/window.localStorage db/local-storage-plugins-key good)
      (let [result (db/get-local-storage-item db/local-storage-plugins-key)]
        (is (map? result) "parsed back to a map")
        (is (contains? result "Pack"))
        (is (= good (.getItem js/window.localStorage db/local-storage-plugins-key))
            "active slot untouched")
        (is (nil? (.getItem js/window.localStorage
                            (db/corrupt-slot-key db/local-storage-plugins-key)))
            "no :corrupt slot created on a clean read")))))
