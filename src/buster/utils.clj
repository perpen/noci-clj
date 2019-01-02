(ns buster.utils
  (:require [digest]
            [buddy.core.nonce :as nonce]
            [buddy.core.codecs :as codecs]
            [clojure.java.io :as io]))

(defn now []
  (java.util.Date.))

(defn now-string []
  (.format (java.text.SimpleDateFormat. "yyyy-MM-dd_HH-mm-ss.SSS") (now)))

(defn current-env []
  :dev)

(defn generate-random-string []
  (let [randomdata (nonce/random-bytes 16)]
    (codecs/bytes->hex randomdata)))

(defn checksum
  [path]
  (digest/sha-256 (io/as-file path)))

(defn cp
  [src tgt])

(defn exit
  [status]
  (println (str "Exiting with status" status))
  (System/exit status))

(defn nexus-upload [nexus-spec path]
  nil)

(defn nexus-download [nexus-spec]
  nil)

(defn fail [type & [msg]]
  (throw (ex-info msg {:type type, :msg msg})))

(defn info [mgs])
