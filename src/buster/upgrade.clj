(ns buster.upgrade
  (:require [buster.job :as j]
            [buster.tags :as tags]
            [buster.nexus :as nexus]
            [buster.utils :as utils]))

(defn- make-nexus-spec [version]
  {:repo "Tools"
   :group "com.barbapapa.gbm.tooling"
   :name "buster"
   :version version
   :packaging "jar"})

(defn- check [version env ctx]
  (let [nexus-spec (make-nexus-spec version)
        path (nexus/download nexus-spec)
        checksum (utils/checksum path)]
    1))

(defn- prepare-exit
  [job version]
  (let [env (utils/current-env)
        requirements {:repo "Tools"
                      :group "com.barbapapa.gbm.tooling"
                      :artifact "buster"
                      :tags-by-authority
                      (case env
                        :dev {}
                        :uat {:buster-prd #{"built"}}
                        :prd {:buster-prd #{"built" "integration-tested"}
                              :tooling-team #{"looks-good-in-uat"}
                              :fck #{"approved"}})}

        requirements {:repo "Tools"
                      :group "com.barbapapa.gbm.tooling"
                      :artifact "buster"
                      :tags-by-authority
                      (case env
                        :dev {}
                        :uat {:buster-prd #{"built"}}
                        :prd {:buster-prd #{"built" "integration-tested"}
                              :tooling-team #{"looks-good-in-uat"}
                              :fck #{"approved"}})}
        nexus-spec {:repo "Tools"
                    :group "com.barbapapa.gbm.tooling"
                    :name "buster"
                    :version version
                    :packaging "jar"}
        live-path "/tmp/dummy.jar"
        new-path (tags/download-and-check nexus-spec requirements)]
    (j/check-for-interrupt job)
    (utils/cp new-path live-path)
    (utils/exit 0)))

(defn start
  [job]
  (let [job-map @job
        {user :user
         version :version} job-map]
    (-> job
        (j/assign {:status-message "Upgrading", :rag :amber, :actions #{:stop}})
        (j/log (str "Upgrading buster to " version) user)
        (prepare-exit version))))

(defn stop
  [job action params]
  (if (j/get-process job)
    (let [{user :user
           comment :comment} params]
      (-> job
          (j/log (str "Stopping process - comment: " comment)
                 :user user)
          (j/interrupt)))))
