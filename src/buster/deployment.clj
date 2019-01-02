(ns buster.deployment
  (:require [buster.tags :as tags]
            [buster.utils :as utils]
            [buster.job :as j]
            [buster.fck :as fck]))

(def ansible-deployer-commit "FIXME-ansible-deployer-commit")

(def ansible-deployer-git-url "ssh://git@stash.hk.barbapapa:8203/fxt/ansible-deployer.git")

(defn- deploy
  "Invokes ansible-deployer, returns exit status."
  [job]
  (comment
    (let [{app :app, env :env, version :version} @job]
      (j/git-clone job ansible-deployer-git-url :commit ansible-deployer-commit)
      (j/log job "Creating vault_p")
      (spit (str (get-dir job) "/vault_p") (get-secret app))
      (j/run job ["./trigger.sh" "deploy" app version env])
      (j/get-exit-status job))))

(defn- await-fck-approval
  [job cr-number]
  (loop []
    (let [cr (fck/get-cr cr-number)
          status (:status cr)
          desc (:description cr)]
      (cond (some #{status} #{:rejected :withdrawn})
            (utils/fail :fatal (str "CR was disapproved: " status))
            (= status :approved) (j/log "CR was approved")
            true (do (j/check-for-interrupt job)
                     (Thread/sleep 30000)
                     (recur))))))

(defn- create-cr-await-approval
  [job env cr-number intro]
  (when (= env :prd)
    (if (nil? cr-number)
      (let [cr-number (fck/create-cr intro)]
        (j/log job (str "Created " cr-number))
        (j/assign job {:cr-number cr-number})))
    (await-fck-approval cr-number)))

(defn start
  [job]
  (let [{user :user
         env :env
         app :app
         version :version
         cr :cr} @job
        intro (str "Deploying bundle-" app " " version " into " env)
        env (keyword env)]
    (-> job
        (j/log intro :user user)
        (j/assign {:rag :amber, :actions #{:stop}})
        (create-cr-await-approval env cr intro)
        (j/check-for-interrupt)
        (j/extra
         (let [success (deploy job)]
           (if success
             (j/assign job {:status-message "Deployment successful", :rag :green})
             (j/assign job {:status-message "Deployment failed", :rag :red}))
           (when (= env :prd)
             (j/log job "Reflecting deployment status on CR")
             (fck/transition job cr (if success
                                      :successful-no-impact
                                      :failure-no-impact))))))))

(defn stop
  [job action params]
  (if (j/get-process job)
    (let [{user :user
           comment :comment} params]
      (-> job
          (j/log (str "Stopping process - comment: " comment)
                 :user user)
          (j/interrupt)))))
