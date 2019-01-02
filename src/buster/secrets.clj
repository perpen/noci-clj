(ns buster.secrets
  ^{:doc "For now just for storing bundle vault passwords."}
  (:require [me.raynes.conch :as sh]
            [clojure.string :as string]
            [me.raynes.fs :as fs]
            [buster.utils :as utils]))

(def resource-path "vault-passwords.yml")

(def secrets (atom nil))

(defn unsealed? []
  (not (nil? @secrets)))

(defn get-secret [name]
  (if (unsealed?)
    (get @secrets name)
    (utils/fail :fatal "sealed")))

(defn- decrypt-file [secret]
  (let [path "/tmp/buster-dec"]
    (spit path secret)
    (try
      (sh/with-programs [ansible-vault]
        (ansible-vault "view" "--vault-password" path resource-path))
      (catch Exception _
        nil)
      (finally
        (fs/delete path)))))

(defn- parse [text]
  (let [lines (string/split text #"\n+")]
    (into {}
          (map vec
               (map #(map string/trim (string/split % #" *: *"))
                    lines)))))

(defn unseal [secret]
  (let [text (decrypt-file secret)]
    (if text
      (reset! secrets (parse text)))))
