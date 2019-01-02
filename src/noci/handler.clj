(ns noci.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [ring.middleware.defaults :as middleware]
            [ring.middleware.json :as middleware-json]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.response :as response]
            [spyscope.core :refer :all]
            [noci.job :as j]
            [noci.trigger :as t]
            [noci.secrets :as secrets]
            [noci.utils :as utils]
            [noci.auth :as auth])
  (:gen-class))

(defn- make-response
  [status data]
  (-> (response/response data)
      (response/status status)))

(defn- response-unauthorised [msg]
  (make-response 401 msg))

(defn- response-conflict [msg]
  (make-response 409 msg))

(defn- response-invalid [msg]
  (make-response 400 msg))

(defn- response-created [data]
  (make-response 201 data))

(defn- response-deleted [data]
  (make-response 202 data))

(defn- response-system-error [msg]
  (make-response 500 msg))

(defn- interpolate
  "In the template map, replaces placeholder values %i or %s with the
  corresponding values in the params.
  Eg. (interpolate {:x 1, :y \"%i\"} {:y 2}) => {:x 1, :y 2}"
  [template params]
  (letfn [(format [val fmt]
            (case fmt
              "%i" (Integer. val)
              "%s" val
              "%a" (if (empty? val)
                     []
                     (string/split val #" *, *"))
              val))
          (get-val [var]
                   (or (get params var)
                       (utils/fail :invalid (str "missing trigger param: " (name var)))))]

    (into {}
          (map (fn [[k v]]
                 [k (cond
                      (some #{v} ["%i" "%s" "%a"]) (format (get-val k) v)
                      (map? v) (interpolate v params)
                      true v)])
               template))))

(defn- cleanup-job-map
  "Removes things we don't want returned to clients, and trims
  the log according to `log-start-index` param."
  [job-map & {:keys [log-start-index] :or {log-start-index -10}}]
  (if job-map
    (let [log-start-index (or log-start-index -10)
          log (:log job-map)
          trimmed-log (if (or (zero? log-start-index)
                              (pos? log-start-index))
                        (drop log-start-index log)
                        (take-last (- log-start-index) log))]
      (-> job-map
          (dissoc :process)
          (assoc :log (or trimmed-log []))))))

(defn- lookup-job-function
  "For <type> and phase ('start or <action>) returns function noci.<type>/<action>"
  [job-type phase]
  (let [ns-name (symbol (str "noci." job-type))
        fun-name (symbol phase)]
    (try
      (ns-resolve ns-name fun-name)
      (catch Exception e
        nil))))

(defn- call-start [params]
  (if-let [job-type (:type params)]
    (if-let [start-function (lookup-job-function job-type 'start)]
      (let [job-seed (assoc params
                          ; hide token FIXME
                            :params (dissoc params :user))
            job (j/create job-seed job-type)
            job-seed @job]
        (future
          (j/fg* job true (start-function job)))
        (cleanup-job-map job-seed)))))

(defroutes api-routes
  (context "/api" []
    (GET "/" []
      "return README.md")

    ;;;;;;;;;;;;;;;;;;;;;;;;;;; triggers

    ;; Create trigger
    (POST "/triggers/:trigger-name" [trigger-name :as {params :body}]
      (if (t/get-trigger trigger-name)
        (response-conflict (str "trigger name already in use: " trigger-name))
        (let [trigger (-> params
                          (assoc :creator (-> params :user :username))
                          (dissoc :user))]
          (response-created (t/create trigger-name trigger)))))

    ;; Trigger info
    (GET "/triggers/:trigger-name" [trigger-name]
      (if-let [trigger (t/get-trigger trigger-name)]
        (response/response trigger)
        (response/not-found (str "no trigger with name: " trigger-name))))

    ;; Delete trigger
    (DELETE "/triggers/:trigger-name" [trigger-name]
      (if-let [trigger (t/delete trigger-name)]
        (response-deleted trigger)
        (response/not-found (str "no trigger with name: " trigger-name))))

    ;; List triggers
    (GET "/triggers" []
      (response/response (t/get-all)))

    ;; Trigger a job
    (POST "/triggers/:trigger-name/job" [trigger-name & params]
      (if-let [trigger (t/get-trigger trigger-name)]
        (let [params (interpolate trigger params)
              job-map (call-start params)]
          (response/response job-map))
        (response/not-found (str "no trigger with name: " trigger-name))))

    ;;;;;;;;;;;;;;;;;;;;;;;;;;; jobs

    ;; Start a job
    (POST "/jobs" {params :body}
      (try
        (if-let [job-map (call-start params)]
          (response/response job-map))
        (response-invalid (str "unknown job type: " (:type params)))))

    ;; Job info
    (GET "/jobs/:job-id" [job-id start]
      (if-let [job (j/get-job job-id)]
        (response/response (cleanup-job-map @job
                                            :log-start-index
                                            (if start (Integer. start))))
        (response/not-found (str "no job with id '" job-id "'"))))

    ;; Action against job
    (PUT "/jobs/:job-id/actions/:action" [job-id action :as {params :body}]
      (let [job (j/get-job job-id)]
        (cond
          (not job)
          (response/not-found (str "no job with id '" job-id "'"))

          (not (j/valid-action? job (keyword action)))
          (response-invalid (str "invalid action: " action))

          true
          (let [job-type (:type @job)
                action-function (lookup-job-function job-type (symbol action))
                actioned-job (j/fg* job false (action-function job action params))]
            (response/response (cleanup-job-map @actioned-job))))))

    ;; List jobs
    (GET "/jobs" [limit]
      (let [limit (if limit (Integer. limit) 10)
            jobs (take-last limit (j/get-all-maps))]
        (response/response (map cleanup-job-map jobs))))

    ;;;;;;;;;;;;;;;;;;;;;;;;;;; auth

    ;; Login
    (POST "/auth" {params :body}
      (let [username (:username params)
            password (:password params)]
        (response/response {:token (auth/create-token username password)})))

    ;; Auth token info
    (GET "/auth/:token" [token]
      (if-let [info (auth/get-user-for-token token)]
        (response/response info)
        (response/not-found (str "unknown token '" token "'"))))

    ;; Invalidate token
    (DELETE "/auth/:token" [token]
      (do (auth/invalidate-token token)
          (response-deleted "deleted")))

    ;;;;;;;;;;;;;;;;;;;;;;;;;;; unseal

    ;; Submit decryption key
    (POST "/unseal" {params :body}
      (let [secret (:secret params)]
        (if (secrets/unseal secret)
          (response-created {:status true})
          (response-created {:status false}))))

    ;; Returns true iff we have the key
    (GET "/unseal" []
      (response/response {:status (secrets/unsealed?)}))

    (route/not-found "no route")))

(defn- auth-middleware
  "For any other request than login or trigger, handle the auth-token header"
  [handler]
  (fn [request]
    (let [unchecked? (and (or (= (:uri request) "/api/auth")
                              (re-matches #"^/api/triggers/[^/]+/job$" (:uri request)))
                          (= (:request-method request) :post))]
      (if unchecked?
        ;; Pass through
        (handler request)
        ;; Lookup user from token
        (let [token (get-in request [:headers "auth-token"])
              user (if token (auth/get-user-for-token token))]
          (if user
            (handler (assoc-in request [:body :user] user))
            (response-unauthorised "missing or invalid auth token")))))))

(defn- wrap-exceptions
  [handler]
  (fn [request]
    (try
      (let [resp (handler request)]
        (flush) ; for spyscope output
        resp)
      (catch Exception e
        (println "CAUGHT by handler/wrap-exceptions" e)
        (response-system-error (.getMessage e))))))

(def app
  (routes
   (-> api-routes
       wrap-exceptions
       middleware-json/wrap-json-response
       auth-middleware
       (wrap-cors :access-control-allow-origin [#".*"]
                  :access-control-allow-methods [:get :put :post :delete])
       (middleware-json/wrap-json-body {:keywords? true})
       (middleware/wrap-defaults middleware/api-defaults))))

(def app-with-reload
  (wrap-reload #'app))

(defn run-reload-server []
  (ring.adapter.jetty/run-jetty #'app-with-reload {:port 3000 :join? false}))

(defn -main [& args]
  (let [port (Integer/valueOf (or (System/getenv "port") "3000"))]
    (println "Starting on port" port)
    (run-jetty app {:port port})))
