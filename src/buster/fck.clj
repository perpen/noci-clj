(ns buster.fck
  (:require [buster.utils :as utils]))

(defn get-cr [cr-number & {:keys [env]}])

(defn create-cr [desc spec & {:keys [env]}])

(defn transition [cr transition-name & {:keys [env]}])

(defn cr-approved? [cr]
  false)

(defn in-time-window? [cr]
  false)
