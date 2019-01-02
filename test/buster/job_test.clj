(ns buster.job-test
  (:require [midje.sweet :refer :all]
            [ring.mock.request :as mock]
            [buster.utils :as utils]
            [buster.job :refer :all]))

(facts "about jobs"
       #_(fact "log"
               (let [x @(log (agent {:x 1 :log []}) ..msg.. ..user..)]
                 (Thread/sleep 1000)
                 x)
               => {:x 1
                   :log [{:user ..user..
                          :time ..time..
                          :message ..msg..}]}
               (provided
                (utils/now) => ..time..)))
