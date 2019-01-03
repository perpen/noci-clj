(ns noci.scripted
  (:require [noci.job :as j]
            [noci.tags :as tags]
            [noci.nexus :as nexus]
            [noci.utils :as utils]))

"
To action the job, we just write the payload to its fifo.

From a python script:
push({status-message: 'doing something', rag: 'blue'})

Consider simple shell scripts, no json.
"

(comment

  (defn script-listener
    [job]
    (loop [msg (read-fifo (:from-script @job))]
      (validate msg) ; ensure not overwriting anything important
      (job/assign msg)))

  (defn start
    [job]
    (let [job-map @job
          {:keys [user script]} job-map
          to-script (create-fifo)
          from-script (create-fifo)
          json-params (save-json job-map)]
      (-> job
          (j/assign {:to-script to-script, :from-script from-script, :actions #{:notify}})
          (j/run [script json-params to-script from-script])
          (j/log "Completed"))))

  ; A generic method to use for all actions
  ; Should provide an "abort" function too, for stopping script if stuck.
  (defn generic-action
    [job params]
    (when (j/get-process job)
      (let [extended-params (assoc params
                                   :user user)
            to-script (:to-script @job)]
        (fifo-write to-script extended-params)))))
