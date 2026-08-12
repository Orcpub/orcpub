(ns orcpub.email-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as s]
            [orcpub.email :as email]))

(defn- subject [report] (:subject (email/character-report-message report)))
(defn- body [report] (get-in (email/character-report-message report) [:body 0 :content]))

(deftest character-report-message-shape
  (testing "composes subject with the char-id, body with error + raw, cc = reporter"
    (let [msg (email/character-report-message
               {:char-id "123" :user-email "p@x.com" :error "boom" :raw "{:k :v}"})]
      (is (s/includes? (:subject msg) "123"))
      (is (= "p@x.com" (:cc msg)))
      (is (s/includes? (get-in msg [:body 0 :content]) "boom"))
      (is (s/includes? (get-in msg [:body 0 :content]) "{:k :v}"))))
  (testing "cc is omitted when the reporter email is blank"
    (is (not (contains? (email/character-report-message
                         {:char-id "1" :user-email "" :error "e" :raw "{}"})
                        :cc)))))

(deftest character-report-message-blocks-header-injection
  (testing "CR/LF and control chars in client-supplied char-id/user-email are
            stripped from headers, so no extra email header can be injected"
    (let [report {:char-id "9\nBcc: evil@x.com"
                  :user-email "v@x.com\r\nSubject: hijacked"
                  :error "x" :raw "{}"}
          msg (email/character-report-message report)]
      (is (nil? (re-find #"\p{Cntrl}" (:subject msg))) "no control chars in subject")
      (is (nil? (re-find #"\p{Cntrl}" (str (:cc msg)))) "no control chars in cc")
      ;; the injected text survives as inert inline text, not a new header line
      (is (s/includes? (:subject msg) "Bcc: evil@x.com")))))

(deftest character-report-message-caps-raw
  (testing "an oversize raw blob is truncated so the body can't be unbounded"
    (let [huge (apply str (repeat 120000 "x"))
          content (body {:char-id "1" :user-email "a@b.com" :error "e" :raw huge})]
      (is (< (count content) 110000) "body is capped well under the raw size")
      (is (s/includes? content "[truncated")))))
