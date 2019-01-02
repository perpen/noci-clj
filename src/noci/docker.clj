(ns noci.docker
  (:require [clojure.string :as string]
            [noci.job :as j]))

(defn start [job]
  (let [{user :user
         timeout-mn :timeout-mn
         volumes :volumes
         args :args
         image :image} @job
        timeout-mn (or timeout-mn 10)
        volume-options (map #(list "-v" (str "/mnt/" % ":/mnt/" %))
                            volumes)
        docker-cmd (flatten ["timeout" (str timeout-mn "m")
                             "docker" "run"
                             volume-options
                             image
                             (or args [])])]
    (-> job
        (j/assign {:status-message "Running"
                   :rag :amber
                   :actions #{:stop}})
        (j/run docker-cmd)
        (j/assign {:status-message "Ran successfully", :rag :green}))))

(defn stop
  [job action params]
  (if (j/get-process job)
    (let [{user :user
           comment :comment} params]
      (-> job
          (j/log (str "Stopping process - comment: " comment)
                 :user user)
          (j/interrupt)))))
