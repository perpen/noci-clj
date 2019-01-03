(ns noci.job-test
  (:require [midje.sweet :refer :all]
            [ring.mock.request :as mock]
            [noci.utils :as utils]
            [noci.job :refer :all]))

(facts "about jobs"
       #_(fact "log"
               (let [x @(log (agent {:x 1 :log []}) ..msg..)]
                 (Thread/sleep 1000)
                 x)
               => {:x 1
                   :log [{:time ..time..
                          :message ..msg..}]}
               (provided
                (utils/now) => ..time..)))
