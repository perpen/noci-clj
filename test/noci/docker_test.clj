(ns noci.docker-test
  (:require [midje.sweet :refer :all]
            [ring.mock.request :as mock]
            [noci.docker :as docker]
            [noci.testing :as testing]
            [noci.build :refer :all]
            [noci.utils :as utils]
            [noci.job :as j]))

(facts "about docker"
       (fact "happy no args or volumes"
             (testing/exercise job {:user ..user..
                                    :image ..image..
                                    :dir ..dir..
                                    :log []}
                               (docker/start job))
             =>  {:actions #{:stop}
                  :exit-status 0
                  :image ..image..
                  :dir ..dir..
                  :log* ["Running command: 'docker' 'run' '..image..'"
                         "  from directory ..dir.."
                         "Exit status 0"]
                  :rag :green
                  :status-message "Ran successfully"
                  :user ..user..}
             (provided
              (#'j/run-helper anything ["docker" "run" ..image..]
                              :dir ..dir..) => 0))

       (fact "happy with args and volumes"
             (testing/exercise job {:user ..user..
                                    :image ..image..
                                    :args [..arg-1.. ..arg-2..]
                                    :volumes [..vol-1.. ..vol-2..]
                                    :dir ..dir..
                                    :log []}
                               (docker/start job))
             =>  {:actions #{:stop}
                  :exit-status 0
                  :image ..image..
                  :args [..arg-1.. ..arg-2..]
                  :volumes [..vol-1.. ..vol-2..]
                  :dir ..dir..
                  :log* ["Running command: 'docker' 'run' '-v' '/mnt/..vol-1..:/mnt/..vol-1..' '-v' '/mnt/..vol-2..:/mnt/..vol-2..' '..image..' '..arg-1..' '..arg-2..'"
                         "  from directory ..dir.."
                         "Exit status 0"]
                  :rag :green
                  :status-message "Ran successfully"
                  :user ..user..}
             (provided
              (#'j/run-helper anything ["docker" "run"
                                        "-v" "/mnt/..vol-1..:/mnt/..vol-1.."
                                        "-v" "/mnt/..vol-2..:/mnt/..vol-2.."
                                        ..image.. ..arg-1.. ..arg-2..]
                              :dir ..dir..) => 0))

       (fact "docker command error"
             (testing/exercise-exception job {:user ..user..
                                              :image ..image..
                                              :dir ..dir..
                                              :log []}
                                         (docker/start job))
             =>  {:actions #{:stop}
                  :exit-status 666
                  :image ..image..
                  :dir ..dir..
                  :log* ["Running command: 'docker' 'run' '..image..'"
                         "  from directory ..dir.."
                         "Exit status 666"]
                  :rag :amber
                  :status-message "Running"
                  :user ..user..}
             (provided
              (#'j/run-helper anything ["docker" "run" ..image..]
                              :dir ..dir..) => 666)))
