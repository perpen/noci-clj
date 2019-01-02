(ns buster.auth
  (:require [buster.utils :as utils]))

(def ttl-in-seconds (* 30 60 1000))

(defonce tokens (atom {}))

(defn clear-tokens []
  (reset! tokens {}))

(defn get-user-for-token
  [token]
  (get @tokens token))

(defn valid?
  [token]
  (if-let [{time :time} (get-user-for-token token)]
    ;; FIXME check age
    true))

(defn- make-href
  [resource id]
  (str "http://localhost:3000/" resource "/" id))

(defn- ldap-authenticated?
  [username password]
  true)

(defn- ldap-user-details
  [username]
  {:username username
   :groups #{"a" "b"}})

(defn create-token
  [username password]
  (if (ldap-authenticated? username password)
    (let [user-details (ldap-user-details username)
          token (utils/generate-random-string)
          token-details (assoc user-details
                               :href (make-href "auth" token)
                               :time (utils/now))]
      (swap! tokens assoc token token-details)
      token)))

(defn invalidate-token
  [token]
  (swap! tokens dissoc token))
