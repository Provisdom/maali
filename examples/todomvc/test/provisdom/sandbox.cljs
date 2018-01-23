(ns provisdom.sandbox
  (:require [clojure.core.async :as async :refer [<! >!]]
            [provisdom.todo.rules :as todo]
            [provisdom.todo.specs :as specs]
            [provisdom.todo.app :as app]
            [provisdom.maali.listeners :as listeners]
            [provisdom.maali.pprint :refer-macros [pprint]]))

(enable-console-print!)

#_(pprint provisdom.todo.rules/queries)

(def td1 (todo/new-todo "Hi"))
(def td2 (todo/new-todo "there"))
(def td3 (todo/new-todo "Hi!"))
(def td4 (todo/new-todo "FOO!"))

(defn pr-res
  [result]
  (println "RESULT********************")
  (pprint result)
  (println "**************************"))

(defn todo-response
  [{::todo/keys [new-todo-request] :as result} todo]
  #_(pr-res result)
  (let [{::specs/keys [response-fn]} new-todo-request]
    (response-fn #::specs{:Request new-todo-request :Todo todo})))

(defonce r (async/mult todo/response-ch))

(let [process (comp todo/handle-response-xf listeners/query-bindings-xf todo/query-xf)
      query-ch (async/chan 10 process)]
  (async/tap r query-ch)
  (async/put! query-ch [nil todo/session])
  (async/go
    (todo-response (<! query-ch) td1)
    (todo-response (<! query-ch) td2)
    (let [result (<! query-ch)
          request (some #(if (= "Hi" (-> % ::specs/Todo ::specs/title)) % nil) (::todo/update-title-requests result))
          {::specs/keys [response-fn]} request]
      (response-fn #::specs{:Request request ::specs/title "FOO!"}))
    (let [result (<! query-ch)
          request (some #(if (= "there" (-> % ::specs/Todo ::specs/title)) % nil) (::todo/update-done-requests result))
          {::specs/keys [response-fn]} request]
      (response-fn #::specs{:Request request ::specs/done true}))
    (let [result (<! query-ch)
          request (::todo/retract-complete-request result)
          {::specs/keys [response-fn]} request]
      (response-fn #::specs{:Request request}))
    (println "||||||||||||||||||||||||||||||||||||||||||||")
    (pr-res (<! query-ch))
    #_(async/close! query-ch)
    (async/untap r query-ch)))

(let [process (comp todo/handle-response-xf listeners/query-bindings-xf todo/query-xf)
      query-ch (async/chan 10 process)]
  (async/tap r query-ch)
  (async/put! query-ch [nil todo/session])
  (async/go
    (todo-response (<! query-ch) td1)
    (todo-response (<! query-ch) td2)
    (let [result (<! query-ch)
          request (some #(if (= "Hi" (-> % ::specs/Todo ::specs/title)) % nil) (::todo/retract-todo-requests result))
          _ (println request)
          {::specs/keys [response-fn]} request]
      (response-fn #::specs{:Request request}))
    (println "||||||||||||||||||||||||||||||||||||||||||||")
    (pr-res (<! query-ch))
    #_(async/close! query-ch)
    (async/untap r query-ch)))

(let [process (comp todo/handle-response-xf listeners/query-bindings-xf todo/query-xf)
      query-ch (async/chan 10 process)]
  (async/tap r query-ch)
  (async/put! query-ch [nil todo/session])
  (async/go
    (todo-response (<! query-ch) td1)
    (todo-response (<! query-ch) td2)
    (todo-response (<! query-ch) (assoc td3 ::specs/done true))
    (let [result (<! query-ch)
          request (::todo/complete-all-request result)
          _ (println request)
          {::specs/keys [response-fn]} request]
      (response-fn #::specs{:Request request}))
    (println "||||||||||||||||||||||||||||||||||||||||||||")
    (pr-res (<! query-ch))
    #_(async/close! query-ch)
    (async/untap r query-ch)))

(let [process (comp todo/handle-response-xf listeners/query-bindings-xf todo/query-xf)
      query-ch (async/chan 10 process)]
  (async/tap r query-ch)
  (async/put! query-ch [nil todo/session])
  (async/go
    (todo-response (<! query-ch) td1)
    (todo-response (<! query-ch) td2)
    (todo-response (<! query-ch) (assoc td3 ::specs/done true))
    (let [result (<! query-ch)
          _ (pr-res result)
          request (::todo/visibility-request result)
          {::specs/keys [response-fn]} request]
      (response-fn #::specs{:Request request ::specs/visibility :active}))
    (let [result (<! query-ch)
          _ (pr-res result)
          request (::todo/visibility-request result)
          {::specs/keys [response-fn]} request]
      (response-fn #::specs{:Request request ::specs/visibility :completed}))
    (println "||||||||||||||||||||||||||||||||||||||||||||")
    (pr-res (<! query-ch))
    #_(async/close! query-ch)
    (async/untap r query-ch)))