(ns noci.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [ring.middleware.defaults :as middleware]
            [ring.middleware.json :as middleware-json]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.logger :as logger]
            [ring.util.response :as response]
            [ring.adapter.jetty :refer [run-jetty]]
            [buddy.auth.backends :as backends]
            [buddy.auth.middleware :refer [wrap-authentication]]
            [buddy.auth.accessrules :as access]
            [spyscope.core :refer :all]
            [noci.job :as j]
            [noci.trigger :as t]
            [noci.secrets :as secrets]
            [noci.utils :as utils]
            [noci.auth :as auth]))

(defn- make-response
  [status data]
  (-> (response/response data)
      (response/status status)))

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

(defn- job-response
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
          (dissoc :process :dir)
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

(defn- call-start [params user]
  (if-let [job-type (:type params)]
    (if-let [start-function (lookup-job-function job-type 'start)]
      (let [job (j/create params job-type user)
            job-seed @job]
        (future
          (j/job-> job true
                   (start-function job)))
        (job-response job-seed)))))

; Details for the authenticated user
(declare ^:dynamic auth-user)

(defroutes api-routes
  (context "/api" []
    (GET "/" []
      "return README.md")

    ;;;;;;;;;;;;;;;;;;;;;;;;;;; triggers

    ;; Create trigger
    (POST "/triggers/:trigger-name" [trigger-name :as {params :body}]
      (if (t/get-trigger trigger-name)
        (response-conflict (str "trigger name already in use: " trigger-name))
        (response-created (t/create trigger-name params))))

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
              job-map (call-start params auth-user)]
          (response/response job-map))
        (response/not-found (str "no trigger with name: " trigger-name))))

    ;;;;;;;;;;;;;;;;;;;;;;;;;;; jobs

    ;; Start a job
    (POST "/jobs" {params :body}
      (try
        (if-let [job-map (call-start params auth-user)]
          (response/response job-map)
          (response-invalid (str "unknown job type: " (:type params))))))

    ;; Job info
    (GET "/jobs/:job-id" [job-id start]
      (if-let [job (j/get-job job-id)]
        (response/response (job-response @job
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
                _ (if auth-user
                    (j/log job (str "Action '" action "' by " (:display-name auth-user) " (" (:username auth-user) ")")))
                actioned-job (j/job-> job false
                                      (action-function job action params auth-user))]
            (response/response (job-response @actioned-job))))))

    ;; List jobs
    (GET "/jobs" [limit]
      (let [limit (if limit (Integer. limit) 10)
            jobs (take-last limit (j/get-all-maps))]
        (response/response (map job-response jobs))))

    ;;;;;;;;;;;;;;;;;;;;;;;;;;; auth

    ;; Login
    (POST "/auth" {params :body}
      (let [username (:username params)
            password (:password params)]
        (response/response {:token (auth/create-token username password)})))

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

(defn- wrap-exceptions
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (println "CAUGHT by handler/wrap-exceptions" e)
        (response-system-error (.getMessage e))))))

(defn- wrap-flush-for-spyscope
  [handler]
  (fn [request]
    (flush)
    (handler request)))

(defn- wrap-logging
  [handler]
  (letfn [(log-fn [{:keys [level throwable message]}]
            (logger/default-log-fn {:level level
                                    :throwable throwable
                                    :message (dissoc message
                                                     :ring.logger/ms
                                                     :ring.logger/type)}))]
    (logger/wrap-log-response
     handler
     {:request-keys [:request-method :uri :params]
      :log-fn log-fn})))

(def locked? (atom false))

(defn- wrap-permissioning [handler]
  (letfn [(anonymous-ok [request] true)

          (locked
           [request]
           (if (:identity request)
             true
             (access/error "Invalid action on locked instance")))

          (authenticated
           [request]
           (if (:identity request)
             true
             (access/error "Auth token missing, invalid or expired")))]

    (let [rules-open [; login
                      {:uris ["/api/auth"]
                       :request-method :post
                       :handler anonymous-ok}
                      ; anything else
                      {:pattern #".*"
                       :handler authenticated}]

          rules-locked [; login
                        {:uris ["/api/auth"]
                         :request-method :post
                         :handler anonymous-ok}
                        ; job start
                        {:uris ["/api/jobs"]
                         :request-method :post
                         :handler locked}
                        ; trigger creation
                        {:pattern #"^/api/trigger/[^/]+$"
                         :request-method :post
                         :handler locked}
                        ; trigger deletion
                        {:pattern #"^/api/trigger/[^/]+$"
                         :request-method :delete
                         :handler locked}
                        ; anything else
                        {:pattern #".*"
                         :handler authenticated}]]

      (fn [request]
        (let [rules (if @locked? rules-locked rules-open)]
          ((access/wrap-access-rules handler {:rules rules}) request))))))

(defn- wrap-auth [handler]
  (letfn [(bind-identity [handler]
            (fn [request]
              (binding [auth-user (:identity request)]
                (handler request))))]
    (let [backend (backends/jws {:secret auth/jwt-secret})]
      (-> handler
          bind-identity
          wrap-permissioning
          (wrap-authentication backend)))))

(def app
  (routes
   (-> api-routes
       wrap-auth
       wrap-exceptions
       wrap-flush-for-spyscope
       wrap-logging
       middleware-json/wrap-json-response
       (middleware-json/wrap-json-body {:keywords? true})
       (wrap-cors :access-control-allow-origin [#".*"]
                  :access-control-allow-methods [:get :put :post :delete])
       (middleware/wrap-defaults middleware/api-defaults))))

(def app-with-reload
  (wrap-reload #'app))

(defn run-reload-server
  "For development, run from repl."
  []
  (ring.adapter.jetty/run-jetty #'app-with-reload {:port 3000 :join? false}))
