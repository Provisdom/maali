(ns sandbox.foo2
  (:require [provisdom.conduit.specs :as specs]
            [provisdom.maali.rules :refer-macros [defrules defqueries defsession] :as rules]
            [provisdom.conduit.rules :as conduit]
            [provisdom.maali.listeners :as listeners]
            [provisdom.maali.pprint :refer-macros [pprint]]
            [provisdom.conduit.commands :as commands]
            [provisdom.conduit.effects :as effects]
            [provisdom.conduit.view :as view]
            [clara.tools.inspect :as inspect]
            [cljs.reader]
            [cljs.core.async :as async]
            [ajax.core :as ajax]
            [clojure.spec.alpha :as s]
            [provisdom.maali.pprint :refer-macros [pprint]]
            [clara.tools.inspect :as inspect]))

(def conduit-user-key "jwtToken")
#_(.setItem js/localStorage conduit-user-key "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpZCI6MTk2MzMsInVzZXJuYW1lIjoic3BhcmtvZnJlYXNvbiIsImV4cCI6MTUyMDI4NTcyOX0.nJ-ER1GW2rful2y-tQqaBg0KR5zCUcaOnRGuVGdoGI4")
(def token (.getItem js/localStorage conduit-user-key))

(defsession session [provisdom.conduit.rules/http-handling-rules
                     provisdom.conduit.rules/home-page-rules
                     provisdom.conduit.rules/article-page-rules
                     provisdom.conduit.rules/article-edit-rules
                     provisdom.conduit.rules/comment-edit-rules
                     provisdom.conduit.rules/profile-page-rules
                     provisdom.conduit.rules/view-update-rules
                     provisdom.conduit.rules/queries]
            {:fact-type-fn rules/spec-type})

(defn log-xf
  [log-fn]
  (fn [xf]
    (fn
      ([] (xf))
      ([result] (xf result))
      ([result input]
       (log-fn input)
       (xf result input)))))

(defn start
  [session]
  (let [command-ch (async/chan 100)
        session (-> session
                    (rules/insert ::specs/AppData {::specs/command-ch command-ch})
                    (rules/fire-rules))]
    (async/go-loop [session session
                    command (async/<! command-ch)]
      (when command
        (.log js/console "COMMODE" (pr-str command))
        (let [session (commands/update-state session command)]
          #_(inspect/explain-activations session)
          (recur session (async/<! command-ch)))))
    (async/go
      (async/>! command-ch [:init session])
      #_(async/>! command-ch [:page :home])
      #_(async/<! (async/timeout 1000))
      #_(async/>! command-ch [:page {::specs/slug "asdf"}])
      #_(async/<! (async/timeout 1000))
      #_(async/>! command-ch [:new-comment "I'm new here"])
      #_(async/>! command-ch [:delete-comment 9397])
      #_(async/>! command-ch [:page {::specs/username "asdf"}])
      #_(async/<! (async/timeout 5000))
      (async/>! command-ch [:page {::specs/username "sss1"}])
      #_(async/>! command-ch [:page :home])
      #_(async/<! (async/timeout 5000))
      #_(async/close! command-ch)
      #_(println "*******************************************"))))

(if token
  (effects/http-effect nil
    {:method          :get
     :uri             (conduit/endpoint "user")             ;; evaluates to "api/articles/"
     :headers         (conduit/auth-header token)           ;; get and pass user token obtained during login
     :response-format (ajax/json-response-format {:keywords? true}) ;; json response and all keys to keywords
     :on-success      (fn [result]
                        (let [s (-> session
                                    (rules/insert ::specs/User (:user result))
                                    (rules/insert ::specs/Token {::specs/token (-> result :user :token)})
                                    (rules/insert ::specs/Filter {::specs/feed true})
                                    (rules/fire-rules))]
                          (start s)))
     :on-failure      #(println %)})
  (start (-> session
             (rules/insert ::specs/Token {::specs/token nil})
             (rules/insert ::specs/Filter {::specs/feed false})
             (rules/fire-rules))))
