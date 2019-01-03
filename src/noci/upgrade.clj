(ns noci.upgrade
  (:require [noci.job :as j]
            [noci.tags :as tags]
            [noci.nexus :as nexus]
            [noci.utils :as utils]))

(defn- make-nexus-spec [version]
  {:repo "Tools"
   :group "com.barbapapa.gbm.tooling"
   :name "noci"
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
                      :artifact "noci"
                      :tags-by-authority
                      (case env
                        :dev {}
                        :uat {:noci-prd #{"built"}}
                        :prd {:noci-prd #{"built" "integration-tested"}
                              :tooling-team #{"looks-good-in-uat"}
                              :fck #{"approved"}})}

        requirements {:repo "Tools"
                      :group "com.barbapapa.gbm.tooling"
                      :artifact "noci"
                      :tags-by-authority
                      (case env
                        :dev {}
                        :uat {:noci-prd #{"built"}}
                        :prd {:noci-prd #{"built" "integration-tested"}
                              :tooling-team #{"looks-good-in-uat"}
                              :fck #{"approved"}})}
        nexus-spec {:repo "Tools"
                    :group "com.barbapapa.gbm.tooling"
                    :name "noci"
                    :version version
                    :packaging "jar"}
        live-path "/tmp/dummy.jar"
        new-path (tags/download-and-check nexus-spec requirements)]
    (j/check-for-interrupt job)
    (utils/cp new-path live-path)
    (utils/exit 0)))

(defn start
  [job]
  (let [{:keys [user version]} @job]
    (-> job
        (j/assign {:status-message "Upgrading", :rag :amber, :actions #{:stop}})
        (j/log (str "Upgrading noci to " version) user)
        (prepare-exit version))))

(defn stop
  [job action params]
  (if (j/get-process job)
    (let [{:keys [user comment]} params]
      (-> job
          (j/log (str "Stopping process - comment: " comment))
          (j/interrupt)))))
