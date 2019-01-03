(ns noci.auth
  (:require [noci.utils :as utils]
            [buddy.sign.jwt :as jwt]
            [clj-time.core :as time]))

(def token-ttl-seconds (* 60 60))

(defn- ldap-authenticated?
  [username password]
  true)

(defn- ldap-user-details
  [username]
  {:username username
   :display-name "Joe"
   :groups #{"a" "b"}})

(def jwt-secret "FIXME")

(defn create-token
  [username password]
  (if (ldap-authenticated? username password)
    (let [user-details (ldap-user-details username)
          claim (assoc user-details
                       :exp (time/plus (time/now) (time/seconds token-ttl-seconds)))
          token (jwt/sign claim jwt-secret)]
      token)))

(defn get-user-for-token [& _] nil)
