(ns noci.job
  (:require [noci.utils :as utils]
            [clojure.string :as string]
            [me.raynes.conch.low-level :as conch]
            [me.raynes.fs :as fs]
            [clojure.core.async :as async]
            [clojure.java.io :as io]))

(defonce jobs (atom {}))

(defonce job-seq (atom 0))

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

(defn get-dir
  [job]
  (await job)
  (:dir @job))

(defn log
  [job msg & {:keys [style-hint]}]
  (let [log-entry {:time (utils/now)
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
    (conch/destroy process)
    (conch/done process))
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
  [job command-line & {:keys [dir timeout-s]}]
  (let [p (apply conch/proc (concat command-line [:dir dir]))
        timeout-s (or timeout-s (* 60 60))]
    (set-process job p)
    (let [out-rdr (io/reader (:out p))
          err-rdr (io/reader (:err p))]
      (future
        (try
          (doseq [line (line-seq out-rdr)]
            (log job (str "> " line) :style-hint "stdout"))
          (catch Exception e
            nil)))
      (future
        (try
          (doseq [line (line-seq err-rdr)]
            (log job (str "> " line) :style-hint "stderr"))
          (catch Exception e
            nil))))
    (let [code (conch/exit-code p (* timeout-s 1000))]
      (conch/done p)
      (set-process job nil)
      code)))

(defn run
  "Runs the command synchronously.
  For as long as it is running, the process object is available via
  `get-process`.
  If `timeout-s` is specified, the process is stopped after this duration
  and the returned value is :timeout.
  On completion the exit status is available via `set-exit-status`.
  The process can be stopped by calling `interrupt`."
  [job command-line & {:keys [dir timeout-s]}]
  (let [dir (or dir (get-dir job))]
    (log job (str "Running command: '" (string/join "' '" command-line) "'"))
    (log job (str "  from directory " dir))
    (if timeout-s
      (log job (str "  with timeout " timeout-s "s")))
    (let [args (flatten [job command-line
                         :dir dir
                         (if timeout-s [:timeout-s timeout-s] [])])
          status (run-helper job command-line :dir dir)]
      (set-exit-status job status)
      (check-for-interrupt job)
      (log job (if (= status :timeout)
                 "Process timed out"
                 (str "Exit status " status)))
      (if (or (= status :timeout) (not (zero? status)))
        (utils/fail :fatal))
      job)))

(def tmp-dir-root "/var/tmp/noci/")

(defn- delete-tmp-dir
  "Before deleting, checks :dir wasn't changed to eg /"
  [job]
  (let [dir (:dir @job)]
    (if (re-matches (re-pattern (str tmp-dir-root "[^/]+")) dir)
      (fs/delete-dir dir)
      (utils/fail :achtung (str "refusing to cleanup job dir: " dir)))))

(defn create [seed hint user]
  (let [job-key (make-uid hint)
        job-num (swap! job-seq inc)
        dir (str "/var/tmp/noci/" (utils/generate-random-string))
        initial-data (assoc seed
                            :created (utils/now)
                            :key job-key
                            :num (str job-num)
                            :rag :amber
                            :status-message "Starting"
                            :actions #{}
                            :dir dir
                            :log []
                            :params seed)
        agent-error-handler (fn [a e]
                              (println "Error against agent " a ": " e))
        job (agent initial-data
                   :error-handler agent-error-handler)]
    (fs/mkdirs dir)
    (swap! jobs assoc job-key job)
    (if user
      (log job (str "Job started by " (:display-name user) " (" (:username user) ")")))
    (log job (str "Starting from seed: " seed))
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
  "Eases the injection of arbitrary code into a (-> job ...) thread.
   Evals the body, and always returns the job at the end."
  [job-var & body]
  `(let [job# ~job-var]
     (await job#)
     ~@body
     job#))

(defmacro job->
  "If `die?` then the job will die if an exception is thrown.
   Meant to be called only when starting a job, or invoking an action."
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

(defn git-clone
  [job git-url & {:keys [commit branch dir]}]
  (let [dir (or dir (get-dir job))]
    (log job (str "Fetching from " git-url))
    (run job ["git" "clone" git-url dir])))
