(ns noci.trigger
  (:require [noci.utils :as utils]
            [clojure.string :as string]
            [clojure.java.io :as io]))

(def triggers (atom {}))

(defn get-all [] @triggers)

(defn clear-all []
  (reset! triggers {}))

(defn get-trigger [name]
  (get @triggers name))

(defn create [name trigger]
  (swap! triggers assoc name trigger)
  trigger)

(defn delete [name]
  (let [trigger (get-trigger name)]
    (swap! triggers dissoc name)
    trigger))
