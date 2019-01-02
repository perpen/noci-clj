(ns noci.config)

(defn config [key]
  (let [data {:tmp-base-dir "/var/tmp/noci"}
        value (get data key)]
    (or value (throw (Exception. (str "no config for key '" key "'"))))))
