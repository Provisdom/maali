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

(defsession session [provisdom.conduit.rules/http-handling-rules
                     provisdom.conduit.rules/home-page-rules
                     provisdom.conduit.rules/article-page-rules
                     provisdom.conduit.rules/article-edit-rules
                     provisdom.conduit.rules/comment-edit-rules
                     provisdom.conduit.rules/profile-page-rules
                     provisdom.conduit.rules/user-rules
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
  (let [token (.getItem js/localStorage conduit/token-key)
        command-ch (async/chan 100)
        session (-> session
                    (rules/insert ::specs/AppData {::specs/command-ch command-ch})
                    (rules/fire-rules))]
    (async/go-loop [session session
                    command (async/<! command-ch)]
      (when command
        (println "COMMAND" (pr-str command))
        (let [session (commands/update-state session command)]
          #_(inspect/explain-activations session)
          (recur session (async/<! command-ch)))))
    (async/go
      (async/>! command-ch [:init session])
      (async/>! command-ch [:set-token token])
      (async/<! (async/timeout 2000))
      (async/>! command-ch [:page :login])
      (async/>! command-ch [:login {::specs/email "dave.d.dixon@gmail.com" ::specs/password "lovepump"}])
      (async/<! (async/timeout 2000))
      (async/>! command-ch [:update-user {::specs/email "dave.d.dixon@gmail.com"
                                          ::specs/bio "Fnorb"
                                          ::specs/username "sparkofreason"
                                          ::specs/image nil}])

      (async/<! (async/timeout 2000))
      #_(async/>! command-ch [:logout])
      #_(async/>! command-ch [:page :home])
      #_(async/<! (async/timeout 1000))
      #_(async/>! command-ch [:page {::specs/slug "asdf"}])
      #_(async/<! (async/timeout 1000))
      #_(async/>! command-ch [:new-comment "I'm new here"])
      #_(async/>! command-ch [:delete-comment 9397])
      #_(async/>! command-ch [:page {::specs/username "sparkofreason" ::specs/profile-filter {::specs/username "sparkofreason"
                                                                                            ::specs/limit 10
                                                                                            ::specs/offset 0}}])
      #_(async/<! (async/timeout 1000))
      #_(async/>! command-ch [:page {::specs/username "sparkofreason" ::specs/profile-filter {::specs/favorited-user "sparkofreason"
                                                                                            ::specs/limit 10
                                                                                            ::specs/offset 0}}])
      #_(async/<! (async/timeout 1000))
      #_(async/>! command-ch [:page {::specs/username "sss1" ::specs/profile-view :my-articles}])
      #_(async/>! command-ch [:page {:new true :title "" :description "" :body "" :tagList []}])
      #_(async/>! command-ch [:new-article {:new         true
                                            :title       "It's new foo"
                                            :description "When your bar is old"
                                            :body        "# Mark it down"
                                            :tagList     ["foo"]}])
      #_(async/>! command-ch [:page {:description    "When your bar is old"
                                   :slug           "it-s-new-foo-dfsy04"
                                   :updatedAt      "2018-01-09T20:50:00.700Z"
                                   :createdAt      "2018-01-09T20:50:00.700Z"
                                   :title          "It's new foo"
                                   :author         {:username "sparkofreason", :bio nil, :image "https://static.productionready.io/images/smiley-cyrus.jpg", :following false}
                                   :favoritesCount 0
                                   :body           "# Mark it down"
                                   :favorited      false
                                   :tagList        ["foo"]}])
      #_(async/>! command-ch [:update-article {:description    "When your bar is old"
                                             :slug           "it-s-new-foo-dfsy04"
                                             :updatedAt      "2018-01-09T20:50:00.700Z"
                                             :createdAt      "2018-01-09T20:50:00.700Z"
                                             :title          "Not so new foo"
                                             :author         {:username "sparkofreason", :bio nil, :image "https://static.productionready.io/images/smiley-cyrus.jpg", :following false}
                                             :favoritesCount 0
                                             :body           "# Mark it down\n ## Foo me up, Scotty!"
                                             :favorited      false
                                             :tagList        ["foo" "bar"]}])
      #_(async/>! command-ch [:page {::specs/slug "it-s-new-foo-z7kdmw"}])
      #_(async/<! (async/timeout 1000))
      #_(async/>! command-ch [:delete-article {:slug "it-s-new-foo-z7kdmw"}])
      #_(async/>! command-ch [:page :home])
      #_(async/<! (async/timeout 5000))
      #_(async/close! command-ch)
      #_(println "*******************************************"))))

(start session)
#_(if token
  (effects/http-effect nil
                       {:method          :get
                        :uri             (conduit/endpoint "user") ;; evaluates to "api/articles/"
                        :headers         (conduit/auth-header token) ;; get and pass user token obtained during login
                        :response-format (ajax/json-response-format {:keywords? true}) ;; json response and all keys to keywords
                        :on-success      (fn [result]
                                           (let [s (-> session
                                                       (rules/insert ::specs/User (:user result))
                                                       (rules/insert ::specs/Token {::specs/token (-> result :user :token)
                                                                                    ::specs/token-key conduit-user-key})
                                                       (rules/fire-rules))]
                                             (start s)))
                        :on-failure      #(println %)})
  (start (-> session
             (rules/insert ::specs/Token {::specs/token nil
                                          ::specs/token-key conduit-user-key})
             (rules/fire-rules))))
