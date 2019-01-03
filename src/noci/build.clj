(ns noci.build
  (:require [noci.utils :as utils]
            [noci.job :as j]
            [clojure.data.json :as json]))

(defn- sign
  [job]
  (let [path (str "target/noci-0.1.0-SNAPSHOT-standalone.jar")
        checksum "xxx" ;(utils/checksum (str (j/get-dir job) "/" path))
        tag-payload (json/write-str (assoc (:params job)
                                           :checksum checksum))
        tag-file "tag"]
    (spit tag-file tag-payload)
    (j/run job ["stags" "sign" "authorities" tag-file "jenkins-prd"])
    (j/log job (slurp tag-file))
    job))

(defn- lein
  [job]
  (-> job
      (j/run ["lein" "uberjar"])
      #_(sign)
      (j/run ["lein" "install"])))

(defn- script
  [job]
  (letfn ((extract-nexus-spec []
            {:path "target/blah.jar"}))
    (j/run job ["./build.sh"])))

(defn start [job]
  (let [{:keys [builder git-url branch commit]} @job
        builder-func (or (and builder (ns-resolve 'noci.build (symbol builder)))
                         (utils/fail :fatal (str "invalid 'build' param for build: " builder)))]
    (-> job
        (j/log (str "Building " git-url " branch " branch))
        (j/assign {:status-message "Building", :rag :amber, :actions #{:stop}})
        (j/git-clone git-url :branch branch :commit commit)
        (builder-func)
        (j/assign {:status-message "Build successful", :rag :green}))))

(defn stop
  [job action params user]
  (if (j/get-process job)
    (let [{comment :comment} params]
      (-> job
          (j/log (str "Stopping process - comment: " comment))
          (j/interrupt)))))
