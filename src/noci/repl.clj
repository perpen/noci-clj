(ns noci.repl
  (:require [spyscope.core :refer :all]
            [clojure.tools.trace :as trace]
            [noci.handler :refer [run-reload-server]]
            [noci.auth :as auth]
            [noci.main :as main]
            [noci.handler :as handler]
            [noci.job :as j]
            [noci.trigger :as t]
            [noci.utils :as utils]
            [noci.build :as build]))

#_(defn lint []
    (eastwood.lint/eastwood {:source-paths ["src"]
                             :test-paths ["test"]
                             :add-linters [:unused-locals :unused-private-vars]}))
