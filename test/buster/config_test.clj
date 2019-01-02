(ns buster.config-test
  (:require [midje.sweet :refer :all]
            [buster.config :refer :all]))

(facts "about config"
       (fact "happy"
             (config :tmp-base-dir) => "/var/tmp/buster")

       (fact "sad"
             (config :blah) => (throws Exception "no config for key ':blah'")))
