(ns noci.main
  (:require [noci.trigger :as t]
            [noci.handler :as handler]
            [ring.adapter.jetty :refer [run-jetty]]
            [clojure.string :as string]
            [clojure.java.io :as io])
  (:gen-class))

(defn load-seeds
  [seeds-path]
  (println "Seeding from" seeds-path)
  (let [seeds (-> seeds-path slurp read-string)]
    (doseq [[trigger-name params] (:triggers seeds)]
      (t/create trigger-name params))))

(defn usage []
  (println "Usage: java -jar noci.jar <port> <locked (true|false)> <seeds>")
  (System/exit 2))

(defn -main [& args]
  (if (< (count args) 2)
    (usage))
  (let [[port locked? [seeds-path]] args
        port (Integer. port)
        locked? (= locked? "true")]
    (if seeds-path
      (load-seeds seeds-path))
    (println "Starting on port" port "in" (if locked? "locked" "open") "mode")
    (reset! handler/locked? locked?)
    (run-jetty handler/app {:port port :join? false})))
