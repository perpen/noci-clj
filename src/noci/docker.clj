(ns noci.docker
  (:require [clojure.string :as string]
            [noci.job :as j]))

(defn start [job]
  (let [{:keys [timeout-mn volumes args image]} @job
        timeout-mn (or timeout-mn 30)
        volume-options (map #(list "-v" (str "/mnt/" % ":/mnt/" %))
                            volumes)
        docker-cmd (flatten ["docker" "run"
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
  [job action params user]
  (if (j/get-process job)
    (let [{comment :comment} params]
      (-> job
          (j/log (str "Stopping process - comment: " comment))
          (j/interrupt)))))
