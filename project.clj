; Allow http connection, as org.apache.pdfbox/pdfbox has http dependnecies
(require 'cemerick.pomegranate.aether)
(cemerick.pomegranate.aether/register-wagon-factory!
 "http" #(org.apache.maven.wagon.providers.http.HttpWagon.))

(defproject orcpub "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main orcpub.server

  :min-lein-version "2.7.1"

  :repositories [["apache" "http://repository.apache.org/snapshots/"]
                 ["my.datomic.com" {:url "https://my.datomic.com/repo"
                                    :username [:gpg :env]
                                    :password [:gpg :env]}]]
  :mirrors {"apache" {:url "https://repository.apache.org/snapshots/"}}

  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/test.check "0.9.0"]
                 [org.clojure/clojurescript "1.10.439"]
                 [org.clojure/core.async "0.4.490"]
                 [cljsjs/react "16.6.0-0"]
                 [cljsjs/react-dom "16.6.0-0"]
                 [cljsjs/filesaverjs "1.3.3-0"]
                 [com.cognitect/transit-cljs "0.8.256"]
                 [cljs-http "0.1.45"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]
                 [clj-time "0.15.0"]
                 [clj-http "3.9.1"]
                 [com.yetanalytics/ring-etag-middleware "0.1.1"]
                 [org.clojure/test.check "0.9.0"]

                 [org.clojure/core.match "0.3.0-alpha5"]
                 [re-frame "0.10.9"]
                 [reagent "0.7.0"]
                 [garden "1.3.2"]
                 [org.apache.pdfbox/pdfbox "2.1.0-20170324.170253-831"]
                 [io.pedestal/pedestal.service "0.5.1"]
                 [io.pedestal/pedestal.route "0.5.1"]
                 [io.pedestal/pedestal.jetty "0.5.1"]
                 [org.clojure/data.json "0.2.6"]
                 [org.slf4j/slf4j-simple "1.7.21"]
                 [buddy/buddy-auth "1.4.1"]
                 [buddy/buddy-hashers "1.2.0"]
                 [reloaded.repl "0.2.3"]
                 [bidi "2.0.17"]

                 [com.stuartsierra/component "0.3.2"]
                 [com.google.guava/guava "21.0"]

                 [com.fasterxml.jackson.core/jackson-databind "2.11.1"]

                 [hiccup "1.0.5"]
                 [com.draines/postal "2.0.2"]
                 [environ "1.1.0"]

                 [pdfkit-clj "0.1.7"]
                 [vvvvalvalval/datomock "0.2.0"]
                 [com.datomic/datomic-free "0.9.5697"]
                 [funcool/cuerdas "2.2.0"]
                 [camel-snake-kebab "0.4.0"]
                 [org.webjars/font-awesome "5.13.1"]]

  :plugins [[lein-figwheel "0.5.19"]
            [lein-cljsbuild "1.1.7" :exclusions [[org.clojure/clojure]]]
            [lein-garden "0.3.0"]
            [lein-environ "1.1.0"]
            [lein-cljfmt "0.6.8"]
            [lein-kibit "0.1.8"]
            #_[lein-resource "16.9.1"]]

  :source-paths ["src/clj" "src/cljc" "src/cljs"]

  :test-paths ["test/clj" "test/cljc" "test/cljs"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :resource-paths ["resources" "resources/.ebextensions/"]

  :uberjar-name "orcpub.jar"

  :garden {:builds [{;; Optional name of the build:
                     :id "screen"
                     ;; Source paths where the stylesheet source code is
                     :source-paths ["src/clj" "src/cljc"]
                     ;; The var containing your stylesheet:
                     :stylesheet orcpub.styles.core/app
                     ;; Compiler flags passed to `garden.core/css`:
                     :compiler {;; Where to save the file:
                                :output-to "resources/public/css/compiled/styles.css"
                                ;; Compress the output?
                                :pretty-print? false}}]}

  :prep-tasks [["garden" "once"]]

  :cljsbuild {:builds
              {:dev
               {:source-paths ["web/cljs" "src/cljc" "src/cljs"]

                ;; the presence of a :figwheel configuration here
                ;; will cause figwheel to inject the figwheel client
                ;; into your build
                :figwheel     {:on-jsload "orcpub.core/on-js-reload"
                               ;; :open-urls will pop open your application
                               ;; in the default browser once Figwheel has
                               ;; started and complied your application.
                               ;; Comment this out once it no longer serves you.
                               :open-urls ["http://localhost:8890"]}

                :compiler     {:main                 orcpub.core
                               :asset-path           "/js/compiled/out"
                               :output-to            "resources/public/js/compiled/orcpub.js"
                               :output-dir           "resources/public/js/compiled/out"
                               :source-map-timestamp true}}}}

  :figwheel {;; :http-server-root "public" ;; default and assumes "resources"
             ;; :server-port 3449 ;; default
             ;; :server-ip "127.0.0.1"

             :css-dirs ["resources/public/css"] ;; watch and update CSS

             ;; Start an nREPL server into the running figwheel process
             ;; :nrepl-port 7888

             ;; Server Ring Handler (optional)
             ;; if you want to embed a ring handler into the figwheel http-kit
             ;; server, this is for simple ring servers, if this

             ;; doesn't work for you just run your own server :) (see lein-ring)

             ;; :ring-handler hello_world.server/handler

             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"

             ;; if you are using emacsclient you can just use
             ;; :open-file-command "emacsclient"

             ;; if you want to disable the REPL
             ;; :repl false

             ;; to configure a different figwheel logfile path
             ;; :server-logfile "tmp/logs/figwheel-logfile.log"
             }

  :repl-options {;; If nREPL takes too long to load it may timeout,
             ;; increase this to wait longer before timing out.
             ;; Defaults to 30000 (30 seconds)
                 :timeout 300000 ; 5 mins to wait
                 }

  ;; setting up nREPL for Figwheel and ClojureScript dev
  ;; Please see:
  ;; https://github.com/bhauman/lein-figwheel/wiki/Using-the-Figwheel-REPL-within-NRepl

  :uberjar-inclusions [#"^\.ebextensions"]
  :jar-inclusions [#"^\.ebextensions"]

  :aliases {"figwheel-native" ["with-profile" "native-dev" "run" "-m" "user" "--figwheel"]
            ;;"figwheel-web" ["figwheel"]
            "externs" ["do" "clean"
                       ["run" "-m" "externs"]]
            "rebuild-modules" ["run" "-m" "user" "--rebuild-modules"]
            "lint" ["with-profile" "lint" "run" "-m" "clj-kondo.main" "--lint" "src"]
            "prod-build" ^{:doc "Recompile code with prod profile."}
            ["externs"
             ["with-profile" "prod" "cljsbuild" "once" "main"]]}
  :profiles {:dev          {:dependencies [[binaryage/devtools "0.9.10"]
                                           [figwheel-sidecar "0.5.19"]
                                           [cider/piggieback "0.4.0"]
                                           [org.clojure/test.check "0.9.0"]
                                           [day8.re-frame/re-frame-10x "0.3.7"]]
                            ;; need to add dev source path here to get user.clj loaded
                            :source-paths ["web/cljs" "src/clj" "src/cljc" "src/cljs" "dev"]
                            :cljsbuild    {:builds {:dev {:compiler {:closure-defines {"re_frame.trace.trace_enabled_QMARK_" true}
                                                                     ;; To console.log CLJS data-structures make sure you enable devtools in Chrome
                                                                     ;; https://github.com/binaryage/cljs-devtools
                                                                     :preloads        [devtools.preload day8.re-frame-10x.preload]}}}}
                            ;; for CIDER
                            ;; :plugins [[cider/cider-nrepl "0.12.0"]]
                            :repl-options {:init-ns          user
                                           :nrepl-middleware [cider.piggieback/wrap-cljs-repl]}}
             :native-dev   {:dependencies [[figwheel-sidecar "0.5.19"]
                                           [com.cemerick/piggieback "0.2.1"]
                                           [org.clojure/test.check "0.9.0"]]
                            :source-paths ["src/cljs" "native/cljs" "src/cljc" "env/dev"]
                            :cljsbuild    {:builds [{:id           "main"
                                                     :source-paths ["src/cljs" "native/cljs" "src/cljc" "env/dev"]
                                                     :figwheel     true
                                                     :compiler     {:output-to     "target/not-used.js"
                                                                    :main          "env.main"
                                                                    :output-dir    "target"
                                                                    :optimizations :none}}]}
                            :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}
             :prod         {:cljsbuild    {:builds [{:id           "main"
                                                     :source-paths ["src/cljs" "native/cljs" "src/cljc" "env/prod"]
                                                     :compiler     {:output-to          "main.js"
                                                                    :main               "env.main"
                                                                    :output-dir         "target"
                                                                    :static-fns         true
                                                                    :externs            ["js/externs.js"]
                                                                    :parallel-build     true
                                                                    :optimize-constants true
                                                                    :optimizations      :advanced}}]}
                            :dependencies [[com.datomic/datomic-free "0.9.5697"]]}
             :uberjar      {:prep-tasks  ["clean" "compile" ["cljsbuild" "once" "prod"]]
                            :env         {:production true}
                            :aot         :all
                            :omit-source true
                            :cljsbuild   {:builds
                                          {:prod
                                           {:source-paths ["web/cljs" "src/cljc" "src/cljs"]
                                            :compiler     {:main          orcpub.core
                                                           :asset-path    "/js/compiled/out"
                                                           :output-to     "resources/public/js/compiled/orcpub.js"
                                                           ;;:output-dir "resources/public/js/compiled/out"
                                                           :optimizations :advanced
                                                           :pretty-print  false}}}}}
             :lint         {:dependencies [[clj-kondo "RELEASE"]]}
             ;; Use like: lein with-profile +start-server repl
             :start-server {:repl-options {:init-ns user
                                           :init    (start-server)}}})
