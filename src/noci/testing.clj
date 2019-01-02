(ns noci.testing
  (:require [noci.job :as j]))

(defn simplify-logs [job]
  (await job) ; b/c we will use :log
  (let [simplified (-> job
                       (send assoc :log* (map :message (:log @job)))
                       (send dissoc :log))]
    (await simplified) ; b/c we will deref
    @simplified))

(defmacro exercise
  "Initialises a job from the provided `data` map, and binds it to `job-var`
 before running the `body`.
 Returns the job map resulting from the execution, with the :log entries
 replaced with a :log* sequence consisting of a simplified version of the log,
 with only the messages and not the user/time/etc."
  [job-var data & body]
  `(let [~job-var (agent ~data)]
     ~@body
     (simplify-logs ~job-var)))

(defmacro exercise-exception
  "Same as `exercise`, but here the body is expected to throw an exception."
  [job-var data & body]
  `(let [~job-var (agent ~data)]
     (try
       ~@body
       (assert false)
       (catch Throwable t#
         (simplify-logs ~job-var)))))
