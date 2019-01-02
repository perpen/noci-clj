(defproject buster "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [compojure "1.5.1"]
                 [metosin/compojure-api "1.1.11"]
                 [ring/ring-core "1.6.2"]
                 [ring/ring-devel "1.6.2"]
                 [ring/ring-jetty-adapter "1.6.2"]
                 [ring/ring-defaults "0.3.1"]
                 [ring "1.6.2"]
                 [ring/ring-json "0.4.0"]
                 [ring-cors "0.1.11"]
                 [buddy "2.0.0"]
                 [digest "1.4.8"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/core.async "0.3.443"]
                 [me.raynes/conch "0.8.0"]
                 [me.raynes/fs "1.4.6"]
                 [spyscope "0.1.5"]
                 [org.clojure/tools.trace "0.7.9"]]
  :plugins [[lein-ring "0.9.7"]
            [lein-cljfmt "0.6.2"]]

  :ring {:handler buster.handler/app
         :reload-paths ["src"]}

  :profiles
  {:uberjar {:aot :all
             :main buster.handler}

   :repl {:dependencies [[midje "1.8.3"]]}
   :dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.2"]
                        [jonase/eastwood "0.2.8" :exclusions [org.clojure/clojure]]]}}

  :repl-options {:init-ns buster.repl
                 :init (do (require '[midje.repl :refer [autotest]])
                           (autotest))})
