(ns noci.config-test
  (:require [midje.sweet :refer :all]
            [noci.config :refer :all]))

(facts "about config"
       (fact "happy"
             (config :tmp-base-dir) => "/var/tmp/noci")

       (fact "sad"
             (config :blah) => (throws Exception "no config for key ':blah'")))
