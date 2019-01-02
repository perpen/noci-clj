(ns noci.handler-test
  (:require [midje.sweet :refer :all]
            [clojure.data.json :as json]
            [ring.mock.request :as mock]
            [ring.util.response :as response]
            [noci.auth :as auth]
            [noci.job :as job]
            [noci.handler :refer :all]))

(facts "about app"
       (let [mock-user {:username "joe"}
             mock-job-function (fn [a b] (agent {:x 1}))]

         (fact "main route"
               (-> (mock/request :get "/api")
                   (mock/header  "auth-token" ..token..)
                   app
                   (dissoc :headers))
               => {:body "return README.md"
                   :status 200}
               (provided
                (auth/get-user-for-token "..token..") => mock-user))

         (fact "no auth token"
               (-> (mock/request :get "/api")
                   app
                   (dissoc :headers))
               => {:body "missing or invalid auth token"
                   :status 401})

         (fact "invalid auth token"
               (-> (mock/request :get "/api")
                   (mock/header  "auth-token" ..token..)
                   app
                   (dissoc :headers))
               => {:body "missing or invalid auth token"
                   :status 401}
               (provided
                (auth/get-user-for-token "..token..") => nil))

         #_(fact "creating job"
                 (-> (mock/request :post "/api/jobs")
                     (mock/json-body {:type "valid"})
                     (mock/header  "auth-token" ..token..)
                     app
                     (dissoc :headers))
                 => {:x 1}
                 (provided
                  (auth/get-user-for-token "..token..") => mock-user
                  (function-for-job-type "valid") => mock-job-function))

         (fact "creating job with invalid function"
               (-> (mock/request :post "/api/jobs")
                   (mock/json-body {:type "hmm"})
                   (mock/header  "auth-token" ..token..)
                   app
                   (dissoc :headers))
               => {:body "unknown job type: hmm"
                   :status 400}
               (provided
                (auth/get-user-for-token "..token..") => mock-user))

         (fact "get unknown job"
               (-> (mock/request :get "/api/jobs/banana")
                   (mock/header  "auth-token" ..token..)
                   app
                   (dissoc :headers))
               => {:body "no job with id 'banana'"
                   :status 404}
               (provided
                (auth/get-user-for-token "..token..") => mock-user))

         (fact "get job"
               (-> (mock/request :get "/api/jobs/known-id"
                                 #_{:log-start-index 2})
                   (mock/header  "auth-token" ..token..)
                   app
                   (dissoc :headers))
               => {:body (json/write-str {:x 1 :log (range 10 20)})
                   :status 200}
               (provided
                (job/get-job "known-id") => (agent {:x 1
                                                    :process ..process..
                                                    :log (range 20)})
                (auth/get-user-for-token "..token..") => mock-user))

         (fact "negative log-start-index"
               (#'noci.handler/cleanup-job-map {:x 1 :log [1 2 3 4]}
                                                 :log-start-index -2)
               => {:x 1 :log [3 4]})

         (fact "large negative log-start-index"
               (#'noci.handler/cleanup-job-map {:x 1 :log [1 2 3 4]}
                                                 :log-start-index -10)
               => {:x 1 :log [1 2 3 4]})

         (fact "zero log-start-index"
               (#'noci.handler/cleanup-job-map {:x 1 :log [1 2 3 4]}
                                                 :log-start-index 0)
               => {:x 1 :log [1 2 3 4]})

         (fact "positive log-start-index"
               (#'noci.handler/cleanup-job-map {:x 1 :log [1 2 3 4]}
                                                 :log-start-index 2)
               => {:x 1 :log [3 4]})

         (fact "large positive log-start-index"
               (#'noci.handler/cleanup-job-map {:x 1 :log [1 2 3 4]}
                                                 :log-start-index 10)
               => {:x 1 :log []})

         (fact "invalid route"
               (-> (mock/request :get "/api/invalid")
                   (mock/header  "auth-token" ..token..)
                   app
                   (dissoc :headers))
               => {:body "no route"
                   :status 404}
               (provided
                (auth/get-user-for-token "..token..") => mock-user))))

(facts "about merging params into job template"
       (fact "integer"
             (#'noci.handler/interpolate {:x 1 :y "%i"} {:y "2"})
             => {:x 1 :y 2})
       (fact "string"
             (#'noci.handler/interpolate {:x 1 :msg "%s"} {:msg "hi"})
             => {:x 1 :msg "hi"})
       (fact "array"
             (#'noci.handler/interpolate {:x 1 :things "%a"} {:things "a,b"})
             => {:x 1 :things ["a", "b"]})
       (fact "empty array"
             (#'noci.handler/interpolate {:x 1 :things "%a"} {:things ""})
             => {:x 1 :things []})
       (fact "deep values"
             (#'noci.handler/interpolate {:x {:y "%i", :z {:msg "%s"}}}
                                           {:y "2", :msg "hi"})
             => {:x {:y 2, :z {:msg "hi"}}})
       (fact "invalid format left unchanged"
             (#'noci.handler/interpolate {:x "%x"} {:x 1})
             => {:x "%x"}))
