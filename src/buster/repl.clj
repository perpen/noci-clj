(ns buster.repl
  (:require [spyscope.core :refer :all]
            [clojure.tools.trace :as trace]
            [buster.handler :refer [run-reload-server]]
            [buster.auth :as auth]
            [buster.handler :as handler]
            [buster.job :as j]
            [buster.trigger :as t]
            [buster.utils :as utils]
            [buster.build :as build]))

#_(defn lint []
    (eastwood.lint/eastwood {:source-paths ["src"]
                             :test-paths ["test"]
                             :add-linters [:unused-locals :unused-private-vars]}))
