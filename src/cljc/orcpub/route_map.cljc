(ns orcpub.route-map
  (:require [bidi.bidi :as bidi]))

(def default-route :default)
(def dnd-e5-char-builder-route :char-builder-5e)
(def dnd-e5-newb-char-builder-route :newb-char-builder-5e)
(def dnd-e5-char-list-route :char-list-5e)
(def dnd-e5-char-route :char-5e)
(def dnd-e5-char-page-route :char-5e-page)
(def dnd-e5-char-list-page-route :char-list-5e-page)
(def dnd-e5-char-summary-list-route :char-summary-list-5e-page)
(def dnd-e5-char-parties-route :char-parties-5e)
(def dnd-e5-char-party-route :char-party-5e)
(def dnd-e5-char-party-name-route :char-party-name-5e)
(def dnd-e5-char-party-characters-route :char-party-characters-5e)
(def dnd-e5-char-party-character-route :char-party-character-5e)
(def dnd-e5-char-parties-page-route :char-parties-5e-page)
(def dnd-e5-orcacle-page-route :orcacle-page)

(def dnd-e5-char-page-routes #{default-route
                               dnd-e5-char-builder-route
                               dnd-e5-newb-char-builder-route
                               dnd-e5-char-list-page-route
                               dnd-e5-char-page-route
                               dnd-e5-char-parties-page-route})

(def dnd-e5-monster-list-page-route :monster-list-5e-page)
(def dnd-e5-monster-page-route :monster-5e-page)
(def dnd-e5-monster-builder-page-route :monster-builder-5e-page)

(def dnd-e5-monster-page-routes #{dnd-e5-monster-list-page-route dnd-e5-monster-page-route dnd-e5-monster-builder-page-route})

(def dnd-e5-spell-list-page-route :spell-list-5e-page)
(def dnd-e5-spell-page-route :spell-5e-page)
(def dnd-e5-spell-builder-page-route :spell-builder-5e-page)
(def dnd-e5-background-builder-page-route :background-builder-5e-page)
(def dnd-e5-encounter-builder-page-route :encounter-builder-5e-page)
(def dnd-e5-combat-tracker-page-route :combat-tracker-5e-page)
(def dnd-e5-race-builder-page-route :race-builder-5e-page)
(def dnd-e5-subrace-builder-page-route :subrace-builder-5e-page)
(def dnd-e5-subclass-builder-page-route :subclass-builder-5e-page)
(def dnd-e5-class-builder-page-route :class-builder-5e-page)
(def dnd-e5-language-builder-page-route :language-builder-5e-page)
(def dnd-e5-metamagic-builder-page-route :metamagic-builder-5e-page)
(def dnd-e5-invocation-builder-page-route :invocation-builder-5e-page)
(def dnd-e5-boon-builder-page-route :boon-builder-5e-page)
(def dnd-e5-feat-builder-page-route :feat-builder-5e-page)
(def dnd-e5-selection-builder-page-route :selection-builder-5e-page)

(def dnd-e5-spell-page-routes #{dnd-e5-spell-list-page-route
                                dnd-e5-spell-page-route
                                dnd-e5-spell-builder-page-route})

(def dnd-e5-items-route :items-5e)
(def dnd-e5-item-summaries-route :item-summaries-5e)
(def dnd-e5-item-route :item-5e)
(def dnd-e5-item-list-page-route :item-list-5e-page)
(def dnd-e5-item-page-route :item-5e-page)
(def dnd-e5-item-builder-page-route :item-builder-5e-page)

(def dnd-e5-item-page-routes #{dnd-e5-item-list-page-route
                               dnd-e5-item-builder-page-route
                               dnd-e5-item-page-route})

(def dnd-e5-my-content-route :my-content-5e-page)

(def dnd-e5-my-content-routes #{dnd-e5-my-content-route
                                dnd-e5-feat-builder-page-route
                                dnd-e5-background-builder-page-route
                                dnd-e5-race-builder-page-route
                                dnd-e5-subrace-builder-page-route
                                dnd-e5-subclass-builder-page-route
                                dnd-e5-class-builder-page-route
                                dnd-e5-language-builder-page-route
                                dnd-e5-metamagic-builder-page-route
                                dnd-e5-invocation-builder-page-route
                                dnd-e5-boon-builder-page-route
                                dnd-e5-selection-builder-page-route})

(def dnd-e5-my-encounters-route :my-content-5e-page)

(def dnd-e5-my-encounters-routes #{dnd-e5-combat-tracker-page-route
                                   dnd-e5-encounter-builder-page-route }  )




(def register-route :register)
(def register-page-route :register-page)
(def my-account-page-route :my-account)
(def verify-route :verify)
(def verify-failed-route :verify-failed)
(def verify-success-route :verify-success)
(def re-verify-route :re-verify)
(def verify-sent-route :verify-sent)
(def login-route :login)
(def login-page-route :login-page)
(def follow-user-route :follow-user)
(def character-pdf-route :character-pdf)
(def check-email-route :check-email)
(def check-username-route :check-username)
(def user-route :user)
(def reset-password-page-route :reset-password-page)
(def reset-password-route :reset-password)
(def send-password-reset-route :send-password-reset)
(def send-password-reset-page-route :send-password-reset-page)
(def password-reset-sent-route :password-reset-sent)
(def password-reset-success-route :password-reset-success)
(def password-reset-expired-route :password-reset-expired)
(def password-reset-used-route :password-reset-used)
(def terms-of-use-route :terms-of-use)
(def privacy-policy-route :privacy-policy)
(def community-guidelines-route :community-guidelines)
(def cookies-policy-route :cookies-policy)

(def routes ["/" {"" default-route
                  "verify" verify-route
                  "verification-expired" verify-failed-route
                  "verification-successful" verify-success-route
                  "verification-sent" verify-sent-route
                  "re-verify" re-verify-route
                  "register" register-route
                  "login" login-route
                  "user" user-route
                  
                  "character.pdf" character-pdf-route
                  "check-email" check-email-route
                  "check-username" check-username-route

                  "reset-password" reset-password-route
                  "send-password-reset" send-password-reset-route
                  "password-reset-sent" password-reset-sent-route
                  "password-reset-success" password-reset-success-route
                  "password-reset-expired" password-reset-expired-route
                  "password-reset-used" password-reset-used-route
                  "terms-of-use" terms-of-use-route
                  "privacy-policy" privacy-policy-route
                  "community-guidelines" community-guidelines-route
                  "cookies-policy" cookies-policy-route

                  "following/users" {["/" :user] follow-user-route}
                  
                  "dnd/"
                  {"5e/" {"characters" {"" dnd-e5-char-list-route
                                        ["/" :id] dnd-e5-char-route}
                          "my-content" dnd-e5-my-content-route
                          "items" {"" dnd-e5-items-route
                                   ["/" :id] dnd-e5-item-route}
                          "item-summaries" dnd-e5-item-summaries-route
                          "parties" {"" dnd-e5-char-parties-route
                                     ["/" :id] {"" dnd-e5-char-party-route
                                                "/name" dnd-e5-char-party-name-route
                                                "/characters" {"" dnd-e5-char-party-characters-route
                                                               ["/" :character-id] dnd-e5-char-party-character-route}}}
                          "character-summaries" dnd-e5-char-summary-list-route}}
                  "pages/" {"my-account" my-account-page-route
                            "register-page" register-page-route
                            "login-page" login-page-route
                            "reset-password-page" reset-password-page-route
                            "send-password-reset-page" send-password-reset-page-route
                            "dnd/"
                            {"5e/"
                             {"character-builder" dnd-e5-char-builder-route
                              "newb-character-builder" dnd-e5-newb-char-builder-route
                              "characters" {"" dnd-e5-char-list-page-route
                                            ["/" :id] dnd-e5-char-page-route}
                              "orcacle" dnd-e5-orcacle-page-route
                              "parties" dnd-e5-char-parties-page-route
                              "background-builder" dnd-e5-background-builder-page-route
                              "encounter-builder" dnd-e5-encounter-builder-page-route
                              "combat-tracker" dnd-e5-combat-tracker-page-route
                              "race-builder" dnd-e5-race-builder-page-route
                              "subrace-builder" dnd-e5-subrace-builder-page-route
                              "subclass-builder" dnd-e5-subclass-builder-page-route
                              "class-builder" dnd-e5-class-builder-page-route
                              "language-builder" dnd-e5-language-builder-page-route
                              "metamagic-builder" dnd-e5-metamagic-builder-page-route
                              "invocation-builder" dnd-e5-invocation-builder-page-route
                              "boon-builder" dnd-e5-boon-builder-page-route
                              "feat-builder" dnd-e5-feat-builder-page-route
                              "spell-builder" dnd-e5-spell-builder-page-route
                              "selection-builder" dnd-e5-selection-builder-page-route
                              "monster-builder" dnd-e5-monster-builder-page-route
                              "spells" {"" dnd-e5-spell-list-page-route
                                        ["/" :key] dnd-e5-spell-page-route}
                              "magic-item-builder" dnd-e5-item-builder-page-route
                              "magic-items" {"" dnd-e5-item-list-page-route
                                             ["/" :key] dnd-e5-item-page-route}
                              "monsters" {"" dnd-e5-monster-list-page-route
                                          ["/" :key] dnd-e5-monster-page-route}}}}}])

(defn path-for [& args]
  (apply bidi/path-for routes args))

(defn match-route [path]
  (->> path (bidi/match-route routes)))
