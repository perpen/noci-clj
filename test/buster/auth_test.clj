(ns buster.auth-test
  (:require [midje.sweet :refer :all]
            [ring.mock.request :as mock]
            [buster.utils :as utils]
            [buster.auth :refer :all]))

(facts "about auth"
       (clear-tokens)

       (fact "failed authentication"
             (create-token ..username.. ..password..) => nil
             (provided
              (#'buster.auth/ldap-authenticated? ..username.. ..password..)
              => false))

       (fact "successful authentication"
             (create-token ..username.. ..password..) => ..token..
             (provided
              (#'buster.auth/ldap-authenticated? ..username.. ..password..)
              => true
              (#'buster.auth/ldap-user-details ..username..) => {:groups ..groups..}
              (utils/now) => ..time..
              (utils/generate-random-string) => ..token..))

       (fact "retrieve token info"
             (get-user-for-token ..token..) => {:groups ..groups..
                                                :href "http://localhost:3000/auth/..token.."
                                                :time ..time..})

       (fact "retrieve unknown token info"
             (get-user-for-token ..token-unknown..) => nil)

       (clear-tokens)

       (fact "clearing tokens"
             (get-user-for-token ..token..) => nil))
