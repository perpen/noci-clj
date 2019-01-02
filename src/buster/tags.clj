(ns buster.tags
  (:require [buster.utils :as utils]
            [buster.fck :as fck]
            [buster.nexus :as nexus]
            [clojure.string :as string]
            [clojure.data.json :as json]
            [clojure.set :as s]))

(def sample-sig
  {:nexus-spec {}
   :time "time signed"
   :tags ["built"]})

; Should be retrieved from some safe source
(def system-keys
  {:buster-prd "askdjf"
   :buster-uat "sjdfjef"
   :fck "sldjfkj"})

(defn env-for-authority
  "eg :fck-prd -> :prd"
  [authority]
  nil)

(defn- fck-verify
  "authority=fck-uat|fck-prd"
  [nexus-spec authority required-tags {cr-number :cr-number}]
  (let [env (env-for-authority authority)
        cr (fck/get-cr cr-number :env env)
        cr-artifact-spec (:artifact-spec cr)
        cr-artifact-checksum (:artifact-checksum cr)
        cr-tags (:tags cr)
        missing-tags (s/difference required-tags cr-tags)]
    (and (fck/cr-approved? cr)
         (= nexus-spec cr-artifact-spec)
         (empty? missing-tags)
         (fck/in-time-window?))))

(defn- openssl-check [data signature authority-key]
  true)

(defn- nexus-verify-single-tag
  [nexus-spec authority tag]
  (let [sig-spec (assoc nexus-spec :packaging (name authority "." tag ".sig"))
        sig-path (nexus/download sig-spec)]
    (if (nil? sig-path)
      (do
        (utils/info (str "Tag not found: " sig-spec))
        false)
      (let [[data signature] (map string/trim
                                  (string/split (slurp sig-path) 'nexus-sig-separator))
            signed? (openssl-check data signature 'authority-key)
            sig (json/read-str data)
            signed-nexus-spec (:nexus-spec sig)]
        (= signed-nexus-spec nexus-spec)))))

(defn- nexus-verify
  [nexus-spec authority required-tags _]
  (let [sig-spec (assoc nexus-spec :packaging (name authority ".tags"))
        sig-path (nexus/download sig-spec)]
    1))

(def verifiers
  {:buster-prd nexus-verify
   :buster-uat nexus-verify
   :fck fck-verify})

(def nexus-base-url "https://efx-nexus.hk.barbapapa/nexus")

; In nexus:
; /Tools/gbm-tooling/buster/1.0/buster-1.0.jar
; /Tools/gbm-tooling/buster/1.0/buster-1.0.jar.buster-prd.built.sig
; /Tools/gbm-tooling/buster/1.0/buster-1.0.jar.buster-prd.integration-tested.sig

(defn tag [authority spec _])

(defn- get-json
  [url]
  "downloads and parse json")

(defn get-authority-tags
  "Retrieve set of tags attached by given authority.
  Throws exception if the tags were provided to an artifact with
  a different checksum."
  [artifact-url authority artifact-checksum]
  (let [url (str artifact-url "." authority ".tags")
        data (get-json url)
        checksum (:checksum data)
        tags (-> data :tags set)]
    (if (not= checksum artifact-checksum)
      (throw (Exception. (str "invalid tags, expected checksum "
                              artifact-checksum ", was " checksum)))
      tags)))

(defn- get-missing-tags
  [artifact-url requirements checksum]
  (reduce (fn [acc [authority required-tags]]
            (let [tags (get-authority-tags artifact-url authority checksum)
                  missing-tags (clojure.set/difference required-tags tags)]
              (if (empty? missing-tags)
                acc
                (assoc acc authority missing-tags))))
          {}
          requirements))

(defn download-and-check
  "Check artifact has required tags, downloads it.
  Returns path of local copy."
  [artifact-spec requirements]
  (let [path (utils/nexus-download artifact-spec)
        checksum (utils/checksum path)
        missing-tags-by-authority (get-missing-tags artifact-spec requirements checksum)]
    (if (empty? missing-tags-by-authority)
      path
      (throw (Exception. (str "missing tags: " missing-tags-by-authority))))))
