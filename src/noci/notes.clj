(ns noci.notes
  (:require [noci.job :as j]))

(defn deployed-version
  [app env]
  "<some commit hash>")

(defn release-notes
  [src tgt]
  (comment
    (let [bb-diff-url "xx"
          tickets (collect-tickets src tgt)
          attachments (map pdf-export tickets)]
      1)))
