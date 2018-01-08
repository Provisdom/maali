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
        command-ch (async/chan 1)
        xf (comp #_command-log
                 commands/update-state-xf
                 listeners/query-bindings-xf
                 #_query-log
                 commands/query-result-xf
                 #_effect-log
                 (map (partial effects/handle-effects command-ch)))
        debug-xf (comp command-log
                       commands/debug-update-state-xf
                       listeners/query-bindings-xf
                       commands/query-result-xf
                       (map (partial effects/handle-effects command-ch)))
        command->effects-ch (async/chan 1 xf)]
    (async/go-loop [command [[:init session]]]
      (loop [command command]
        (when (seq command)
          (async/>! command->effects-ch command)
          (recur (async/<! command->effects-ch))))
      (recur (async/<! command-ch))
      #_(when effects
        (effects/handle-effects command-ch effects)
        (recur (async/<! effect-ch))))
    (async/go
      (async/>! command-ch [[:init session]])
      (async/>! command-ch [[:upsert nil init-filter]
                            [:page :home]])
      (async/<! (async/timeout 1000))
      (async/>! command-ch [[:page {::specs/slug "asdf"}]])
      (async/<! (async/timeout 1000))
      (async/>! command-ch [[:page {::specs/username "asdf"}]])
      (async/<! (async/timeout 1000))
      #_(async/>! command-ch [[:page :home]]))
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