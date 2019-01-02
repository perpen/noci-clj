(ns noci.build
  (:require [noci.utils :as utils]
            [noci.job :as j]))

(defn- lein
  [job]
  (-> job
      (j/run ["lein" "uberjar"])
      (j/run ["lein" "install"])))

(defn- script
  [job]
  (letfn ((extract-nexus-spec []
            {:path "target/blah.jar"}))
    (j/run job ["./build.sh"])))

(defn start [job]
  (let [{user :user
         builder :builder
         git-url :git-url
         branch :branch
         commit :commit} @job
        builder-func (or (and builder (ns-resolve 'noci.build (symbol builder)))
                         (utils/fail :fatal (str "invalid 'build' param for build: " builder)))]
    (-> job
        (j/log (str "Building " git-url " branch " branch) :user user)
        (j/assign {:status-message "Building", :rag :amber, :actions #{:stop}})
        (j/git-clone git-url :branch branch :commit commit)
        (builder-func)
        (j/assign {:status-message "Build successful", :rag :green}))))

(defn stop
  [job action params]
  (if (j/get-process job)
    (let [{user :user
           comment :comment} params]
      (-> job
          (j/log (str "Stopping process - comment: " comment)
                 :user user)
          (j/interrupt)))))
