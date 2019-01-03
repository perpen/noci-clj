(ns noci.handler-test
  (:require [midje.sweet :refer :all]
            [clojure.data.json :as json]
            [ring.mock.request :as mock]
            [ring.util.response :as response]
            [buddy.sign.jwt :as jwt]
            [noci.auth :as auth]
            [noci.job :as j]
            [noci.utils :as utils]
            [noci.handler :refer :all]))

(facts "about app"
       (let [mock-job-function (fn [a b] (agent {:x 1}))]

         (fact "main route"
               (-> (mock/request :get "/api")
                   (mock/header  "authorization" "Token some-token")
                   app
                   (dissoc :headers))
               => {:body "return README.md"
                   :status 200}
               (provided
                (jwt/unsign "some-token" anything nil) => ..user..))

         (fact "no auth token"
               (-> (mock/request :get "/api")
                   app
                   (dissoc :headers))
               => {:body "Auth token missing, invalid or expired"
                   :status 400})

         (fact "invalid auth token"
               (-> (mock/request :get "/api")
                   (mock/header  "authorization" "Token some-token")
                   app
                   (dissoc :headers))
               => {:body "Auth token missing, invalid or expired"
                   :status 400}
               (provided
                (jwt/unsign "some-token" anything nil) => nil))

         (fact "creating job"
               (-> (mock/request :post "/api/jobs")
                   (mock/json-body {:type "job-type"})
                   (mock/header  "authorization" "Token some-token")
                   app
                   (dissoc :headers :log)
                   :body
                   json/read-str)
               => {"x" 1, "log" []}
               (provided
                (jwt/unsign "some-token" anything nil) => ..user..
                (j/create {:type "job-type"} "job-type" ..user..) => (agent {:x 1})
                (#'noci.handler/lookup-job-function "job-type" 'start) => mock-job-function))

         (fact "creating job with invalid function"
               (-> (mock/request :post "/api/jobs")
                   (mock/json-body {:type "hmm"})
                   (mock/header  "authorization" "Token some-token")
                   app
                   (dissoc :headers))
               => {:body "unknown job type: hmm"
                   :status 400}
               (provided
                (jwt/unsign "some-token" anything nil) => ..user..))

         (fact "get unknown job"
               (-> (mock/request :get "/api/jobs/banana")
                   (mock/header  "authorization" "Token some-token")
                   app
                   (dissoc :headers))
               => {:body "no job with id 'banana'"
                   :status 404}
               (provided
                (jwt/unsign "some-token" anything nil) => ..user..))

         (fact "get job"
               (-> (mock/request :get "/api/jobs/known-id")
                   (mock/header  "authorization" "Token some-token")
                   app
                   (dissoc :headers))
               => {:body (json/write-str {:x 1 :log (range 10 20)})
                   :status 200}
               (provided
                (jwt/unsign "some-token" anything nil) => ..user..
                (j/get-job "known-id") => (agent {:x 1
                                                  :process ..process..
                                                  :log (range 20)})))))

(facts "about making the job response map"
       (fact "negative log-start-index"
             (#'noci.handler/job-response {:x 1 :log [1 2 3 4]}
                                          :log-start-index -2)
             => {:x 1 :log [3 4]})

       (fact "large negative log-start-index"
             (#'noci.handler/job-response {:x 1 :log [1 2 3 4]}
                                          :log-start-index -10)
             => {:x 1 :log [1 2 3 4]})

       (fact "zero log-start-index"
             (#'noci.handler/job-response {:x 1 :log [1 2 3 4]}
                                          :log-start-index 0)
             => {:x 1 :log [1 2 3 4]})

       (fact "positive log-start-index"
             (#'noci.handler/job-response {:x 1 :log [1 2 3 4]}
                                          :log-start-index 2)
             => {:x 1 :log [3 4]})

       (fact "large positive log-start-index"
             (#'noci.handler/job-response {:x 1 :log [1 2 3 4]}
                                          :log-start-index 10)
             => {:x 1 :log []})

       (fact "invalid route"
             (-> (mock/request :get "/api/invalid")
                 (mock/header  "authorization" "Token some-token")
                 app
                 (dissoc :headers))
             => {:body "no route"
                 :status 404}
             (provided
              (jwt/unsign "some-token" anything nil) => ..user..)))

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
