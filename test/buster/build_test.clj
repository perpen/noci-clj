(ns buster.build-test
  (:require [midje.sweet :refer :all]
            [ring.mock.request :as mock]
            [buster.testing :as testing]
            [buster.build :as build]
            [buster.utils :as utils]
            [buster.job :as j]))

(facts "Production build"
       (fact "happy"
             (testing/exercise job {:user ..user..
                                    :dir ..dir..
                                    :builder "lein"
                                    :git-url ..url..
                                    :branch ..branch..
                                    :commit ..commit..
                                    :log []}
                               (build/start job))
             =>  {:actions #{:stop}
                  :exit-status 0
                  :builder "lein"
                  :git-url ..url..
                  :branch ..branch..
                  :commit ..commit..
                  :dir ..dir..
                  :log* ["Building ..url.. branch ..branch.."
                         "Fetching from ..url.."
                         "Running command: 'git' 'clone' '..url..' '..dir..'"
                         "  from directory ..dir.."
                         "Exit status 0"
                         "Running command: 'lein' 'uberjar'"
                         "  from directory ..dir.."
                         "Exit status 0"
                         "Running command: 'lein' 'install'"
                         "  from directory ..dir.."
                         "Exit status 0"]
                  :rag :green
                  :status-message "Build successful"
                  :user ..user..}
             (provided
              (#'j/run-helper anything ["git" "clone" ..url.. ..dir..]) => 0
              (#'j/run-helper anything ["lein" "uberjar"]) => 0
              (#'j/run-helper anything ["lein" "install"]) => 0))

       (fact "git clone error"
             (testing/exercise-exception job {:user ..user..
                                              :dir ..dir..
                                              :builder "lein"
                                              :git-url ..url..
                                              :branch ..branch..
                                              :commit ..commit..
                                              :log []}
                                         (build/start job))
             =>  {:actions #{:stop}
                  :exit-status 666
                  :builder "lein"
                  :git-url ..url..
                  :branch ..branch..
                  :commit ..commit..
                  :dir ..dir..
                  :log* ["Building ..url.. branch ..branch.."
                         "Fetching from ..url.."
                         "Running command: 'git' 'clone' '..url..' '..dir..'"
                         "  from directory ..dir.."
                         "Exit status 666"]
                  :rag :amber
                  :status-message "Building"
                  :user ..user..}
             (provided
              (#'j/run-helper anything ["git" "clone" ..url.. ..dir..]) => 666))

       (fact "lein uberjar error"
             (testing/exercise-exception job {:user ..user..
                                              :dir ..dir..
                                              :builder "lein"
                                              :git-url ..url..
                                              :branch ..branch..
                                              :commit ..commit..
                                              :log []}
                                         (build/start job))
             =>  {:actions #{:stop}
                  :exit-status 666
                  :builder "lein"
                  :git-url ..url..
                  :branch ..branch..
                  :commit ..commit..
                  :dir ..dir..
                  :log* ["Building ..url.. branch ..branch.."
                         "Fetching from ..url.."
                         "Running command: 'git' 'clone' '..url..' '..dir..'"
                         "  from directory ..dir.."
                         "Exit status 0"
                         "Running command: 'lein' 'uberjar'"
                         "  from directory ..dir.."
                         "Exit status 666"]
                  :rag :amber
                  :status-message "Building"
                  :user ..user..}
             (provided
              (#'j/run-helper anything ["git" "clone" ..url.. ..dir..]) => 0
              (#'j/run-helper anything ["lein" "uberjar"]) => 666)))
