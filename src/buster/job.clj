(ns buster.job
  (:require [buster.utils :as utils]
            [clojure.string :as string]
            [me.raynes.conch.low-level :as conch]
            [me.raynes.fs :as fs]
            [clojure.core.async :as async]
            [clojure.java.io :as io]))

(def jobs (atom {}))

(defn- make-uid
  [hint]
  (let [timestamp (utils/now-string)]
    (str hint "-" timestamp)))

(defn get-all-maps
  "Returns seq of job maps, sorted by creation time."
  []
  (sort-by :created (map deref (vals @jobs))))

(defn clear-all []
  (reset! jobs {}))

(defn get-job [job-key]
  (get @jobs job-key))

(defn get-action-function
  [job]
  (:action-function @job))

(defn get-env
  [job]
  (:env @job))

(defn get-dir
  [job]
  (await job)
  (:dir @job))

(defn log
  [job msg & {:keys [user style-hint]}]
  (let [username (if (map? user)
                   (:username user)
                   #_(utils/fail :fatal "username in use?!")
                   user)
        log-entry {:time (utils/now)
                   :user username
                   :message msg
                   :style-hint style-hint}]
    (send job update-in [:log] conj log-entry)))

(defn assign [job values]
  #_(log job (str "Assigning: " values))
  (send job merge values))

(defn valid-action?
  [job action]
  (some #{action} (:actions @job)))

(defn- set-process
  [job process]
  (send job assoc :process process))

(defn get-process
  [job]
  (:process @job))

(defn- clear-process
  [job]
  (send job dissoc :process))

(defn set-exit-status
  [job exit-status]
  (send job assoc :exit-status exit-status))

(defn get-exit-status
  [job]
  (await job)
  (:exit-status @job))

(defn interrupt [job]
  (when-let [process (get-process job)]
    (conch/destroy process))
  (assign job {:interrupt true, :rag :red, :status-message "Interrupted"})
  job)

(defn check-for-interrupt [job]
  (await job)
  (if (:interrupt @job)
    (utils/fail :interrupt nil))
  job)

(defn- run-helper
  "Runs the command synchronously.
  Until the process exits, the process object is available via `get-process`.
  Returns the exit status.
  The process can be stopped by calling `interrupt`."
  [job command-line & {:keys [raise dir] :or {raise true}}]
  (let [p (apply conch/proc (concat command-line [:dir dir]))]
    (set-process job p)
    (let [out-rdr (io/reader (:out p))
          err-rdr (io/reader (:err p))
          out-fut (future (doseq [line (line-seq out-rdr)]
                            (log job line :style-hint "stdout")))
          err-fut (future (doseq [line (line-seq err-rdr)]
                            (log job line :style-hint "stderr")))]
        ; Wait for both to close
      [@out-fut, @err-fut])
    (conch/done p)
    (conch/exit-code p)))

(defn run
  "Runs the command synchronously.
  For as long as it is running, the process object is available via
  `get-process`.
  On completion the exit status is available via `set-exit-status`.
  The process can be stopped by calling `interrupt`."
  [job command-line & {:keys [raise dir] :or {raise true}}]
  (let [dir (or dir (get-dir job))]
    (log job (str "Running command: '" (string/join "' '" command-line) "'"))
    (log job (str "  from directory " dir))
    (let [status (run-helper job command-line)]
      (set-exit-status job status)
      (log job (str "Exit status " status))
      (check-for-interrupt job)
      (if (and raise (not (zero? status)))
        (utils/fail :fatal)))
    job))

(def tmp-dir-root "/var/tmp/buster/")

(defn- delete-tmp-dir
  "Before deleting, checks :dir wasn't changed to eg /"
  [job]
  (let [dir (:dir @job)]
    (if (re-matches (re-pattern (str tmp-dir-root "[^/]+")) dir)
      (fs/delete-dir dir)
      (utils/fail :achtung (str "refusing to cleanup job dir: " dir)))))

(defn create [initial-data hint]
  (let [job-key (make-uid hint)
        dir (str "/var/tmp/buster/" (utils/generate-random-string))
        initial-data (assoc initial-data
                            :created (utils/now)
                            :key job-key
                            :rag :green
                            :status-message "Starting"
                            :actions #{}
                            :dir dir
                            :log [])
        agent-error-handler (fn [a e]
                              (println "Error against agent " a ": " e))
        job (agent initial-data
                   :error-handler agent-error-handler)]
    (fs/mkdirs dir)
    (swap! jobs assoc job-key job)
    job))

(defn mark-as-dead
  [job]
  (await job)
  (-> job
      (log (str "End status: " (:status-message @job)))
      (send merge {:dead true, :actions #{}})
      (delete-tmp-dir))
  job)

(defn log-exception
  [job e & [cause?]]
  (if-not cause?
    (log job (str "Caught exception: " e)))
  (doseq [line (.getStackTrace e)]
    (log job (str "  " line)))
  (when-let [cause (.getCause e)]
    (log job "Cause:")
    (log-exception job cause true)))

(defmacro extra
  [job-var & body]
  `(let [job# ~job-var]
     (await job#)
     ~@body
     job#))

(defmacro fg*
  [job-var die? & body]
  `(try
     ~@body
     ~job-var
     (catch Throwable t#
       ;; Log exception details against job
       (if-let [data# (ex-data t#)]
         ; Our own exception
         (let [type# (:type data#)
               msg# (:msg data#)]
           (if (= type# :interrupt)
             (log ~job-var "Interrupted")
             (-> ~job-var
                 (log (str "Fatal" (if msg# (str " - " msg#))))
                 (assign {:interrupt true, :rag :red, :status-message "Error"}))))
         ; Not exception of ours, log the full details
         (do
           (assign ~job-var {:status-message "Error", :rag :red})
           (log-exception ~job-var t#)))
       (await ~job-var)
       ~job-var)
     (finally
       (if ~die?
         (mark-as-dead ~job-var)
         ~job-var))))

(defmacro fg->
  [job-var die? & body]
  `(try
     (-> ~job-var
         ~@body)
     (catch Throwable t#
       ;; Log exception details against job
       (if-let [data# (ex-data t#)]
         ; Our own exception
         (let [type# (:type data#)
               msg# (:msg data#)]
           (if (= type# :interrupt)
             (log ~job-var "Interrupted")
             (-> ~job-var
                 (log (str "Fatal" (if msg# (str " - " msg#))))
                 (assign {:interrupt true, :rag :red, :status-message "Error"}))))
         ; Not exception of ours, log the full details
         (do
           (assign ~job-var {:status-message "Error", :rag :red})
           (log-exception ~job-var t#)))
       (await ~job-var)
       ~job-var)
     (finally
       (if ~die?
         (mark-as-dead ~job-var)
         ~job-var))))

(defmacro bg->
  [job-var & body]
  `(future
     (fg-> ~job-var true ~@body)))

(defmacro fg-action
  "Executes the body. Catches throwables and logs the stack trace to the job.
  Returns the job."
  [job-var & body]
  `(fg* ~job-var false ~@body))

(defmacro fg
  "Executes the body. Catches throwables and logs the stack trace to the job.
  Finally, marks the job as dead.
  Returns the job."
  [job-var & body]
  `(fg* ~job-var true ~@body))

(defmacro bg
  [job-var & body]
  `(future
     (fg ~job-var ~@body)))

(defn git-clone
  [job git-url & {:keys [commit branch dir]}]
  (let [dir (or dir (get-dir job))]
    (log job (str "Fetching from " git-url))
    (run job ["git" "clone" git-url dir])))
