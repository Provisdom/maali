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
(def td3 (todo/new-todo "Foo!"))
(def td4 (todo/new-todo "BAR!"))

(defn pr-res
  ([result] (pr-res result nil))
  ([result note]
   (println (or (str note "*******************") "RESULT********************"))
   (pprint result)))

(defn todo-response
  [{::todo/keys [new-todo-request] :as result} todo]
  #_(pr-res result)
  (let [{::specs/keys [response-fn]} new-todo-request]
    (response-fn #::specs{:Request new-todo-request :Todo todo})))

;;; Use tap instead of pipeline to connect to the response channel. This allows the connection to be
;;; undone when working in the REPL. pipeline should be fine for production use.
(defonce r (async/mult todo/response-ch))

(let [query-ch (async/chan 10 todo/response->q-results-xf)]
  (async/tap r query-ch)
  (async/put! query-ch [nil todo/session])
  (async/go
    (todo-response (<! query-ch) td1)
    (todo-response (<! query-ch) td2)
    (let [result (<! query-ch)
          _ (pr-res result "TODOS")
          request (some #(if (= "Hi" (-> % ::specs/Todo ::specs/title)) % nil) (::todo/update-title-requests result))
          {::specs/keys [response-fn]} request]
      (response-fn #::specs{:Request request ::specs/title "FOO!"}))
    (let [result (<! query-ch)
          _ (pr-res result "RENAMED Hi->FOO!")
          request (some #(if (= "there" (-> % ::specs/Todo ::specs/title)) % nil) (::todo/update-done-requests result))
          {::specs/keys [response-fn]} request]
      (response-fn #::specs{:Request request ::specs/done true}))
    (let [result (<! query-ch)
          _ (pr-res result "MARK there DONE")
          request (::todo/retract-complete-request result)
          {::specs/keys [response-fn]} request]
      (response-fn #::specs{:Request request}))
    (println "||||||||||||||||||||||||||||||||||||||||||||")
    (pr-res (<! query-ch) "RETRACT DONE")
    #_(async/close! query-ch)
    (async/untap r query-ch)))

(let [query-ch (async/chan 10 todo/response->q-results-xf)]
  (async/tap r query-ch)
  (async/put! query-ch [nil todo/session])
  (async/go
    (todo-response (<! query-ch) td1)
    (todo-response (<! query-ch) td2)
    (let [result (<! query-ch)
          _ (pr-res result "TODOS")
          request (some #(if (= "Hi" (-> % ::specs/Todo ::specs/title)) % nil) (::todo/retract-todo-requests result))
          {::specs/keys [response-fn]} request]
      (response-fn #::specs{:Request request}))
    (println "||||||||||||||||||||||||||||||||||||||||||||")
    (pr-res (<! query-ch) "RETRACT Hi")
    #_(async/close! query-ch)
    (async/untap r query-ch)))

(let [query-ch (async/chan 10 todo/response->q-results-xf)]
  (async/tap r query-ch)
  (async/put! query-ch [nil todo/session])
  (async/go
    (todo-response (<! query-ch) td1)
    (todo-response (<! query-ch) td2)
    (todo-response (<! query-ch) (assoc td3 ::specs/done true))
    (let [result (<! query-ch)
          _ (pr-res result "TODOS")
          request (::todo/complete-all-request result)
          {::specs/keys [response-fn]} request]
      (response-fn #::specs{:Request request}))
    (println "||||||||||||||||||||||||||||||||||||||||||||")
    (pr-res (<! query-ch) "COMPLETE ALL")
    #_(async/close! query-ch)
    (async/untap r query-ch)))

(let [query-ch (async/chan 10 todo/response->q-results-xf)]
  (async/tap r query-ch)
  (async/put! query-ch [nil todo/session])
  (async/go
    (todo-response (<! query-ch) td1)
    (todo-response (<! query-ch) td2)
    (todo-response (<! query-ch) (assoc td3 ::specs/done true))
    (let [result (<! query-ch)
          _ (pr-res result "VISIBILITY :all")
          request (::todo/visibility-request result)
          {::specs/keys [response-fn]} request]
      (response-fn #::specs{:Request request ::specs/visibility :active}))
    (let [result (<! query-ch)
          _ (pr-res result "VISIBILITY :active")
          request (::todo/visibility-request result)
          {::specs/keys [response-fn]} request]
      (response-fn #::specs{:Request request ::specs/visibility :completed}))
    (println "||||||||||||||||||||||||||||||||||||||||||||")
    (pr-res (<! query-ch) "VISIBILITY :completed")
    #_(async/close! query-ch)
    (async/untap r query-ch)))