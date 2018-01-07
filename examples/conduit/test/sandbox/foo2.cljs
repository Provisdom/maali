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
            [provisdom.maali.pprint :refer-macros [pprint]]))

(def conduit-user-key "jwtToken")
#_(.setItem js/localStorage conduit-user-key "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpZCI6MTk2MzMsInVzZXJuYW1lIjoic3BhcmtvZnJlYXNvbiIsImV4cCI6MTUyMDI4NTcyOX0.nJ-ER1GW2rful2y-tQqaBg0KR5zCUcaOnRGuVGdoGI4")
(def token (.getItem js/localStorage conduit-user-key))

(defsession session [provisdom.conduit.rules/rules provisdom.conduit.rules/queries]
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
  [session init-filter]
  (let [command-log (log-xf (fn [command]
                              (println "COMMAND" command)
                              #_(s/explain ::commands/command command)))
        query-log (log-xf (fn [query-result]
                            (println "QUERY" query-result)))
        effect-log (log-xf (fn [effects]
                             (println "EFFECTS" effects)
                             (when effects
                               (s/explain ::effects/effects effects))))
        xf (comp command-log
                 commands/update-state-xf
                 listeners/query-bindings-xf
                 query-log
                 commands/query-result-xf
                 effect-log)
        debug-xf (comp command-log
                       commands/debug-update-state-xf
                       listeners/query-bindings-xf
                       commands/query-result-xf)
        command-ch (async/chan 1 xf)
        effect-ch (async/chan 1)
        cmds [[:init session]
              [:upsert nil init-filter]
              [:page :home]
              #_[:upsert init-filter {::specs/feed false}]]]
    (async/pipe command-ch effect-ch)
    (async/go-loop [effects (async/<! effect-ch)]
      (when effects
        (effects/handle-effects command-ch effects)
        (recur (async/<! effect-ch))))
    (async/onto-chan command-ch cmds false)
    (js/setTimeout #(async/onto-chan command-ch [[:page {::specs/slug "asdf"}]] false) 10000)
    #_(async/close! command-ch)))

(if token
  (effects/http-effect
    {:method          :get
     :uri             (conduit/endpoint "user")             ;; evaluates to "api/articles/"
     :headers         (conduit/auth-header token)           ;; get and pass user token obtained during login
     :response-format (ajax/json-response-format {:keywords? true}) ;; json response and all keys to keywords
     :on-success      (fn [result]
                        (let [s (-> session
                                    (rules/insert ::specs/User (:user result))
                                    (rules/insert ::specs/Token {::specs/token (-> result :user :token)})
                                    (rules/fire-rules))]
                          (start s {::specs/feed true})))
     :on-failure      #(println %)})
  (start (-> session
             (rules/insert ::specs/Token {::specs/token nil})
             (rules/insert ::specs/Feed {::specs/feed :global})
             (rules/fire-rules))
         {::specs/feed false}))

#_(effects/get-articles {} nil)