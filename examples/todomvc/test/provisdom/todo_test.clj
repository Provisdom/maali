(ns provisdom.todo-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [<! <!! >! mult chan go tap untap put!]]
            [provisdom.maali.rules :refer [defsession]]
            [provisdom.todo.specs :as specs]
            [provisdom.todo.rules :as todo]
            [clojure.pprint :refer [pprint]]))

(defn todo-response
  [{::todo/keys [new-todo-request] :as result} todo]
  (let [{::specs/keys [response-fn]} new-todo-request]
    (response-fn #::specs{:Request new-todo-request :Todo todo})))

(def td1 (todo/new-todo "Hi"))
(def td2 (todo/new-todo "there"))
(def td3 (todo/new-todo "Hi!"))
(def td4 (todo/new-todo "FOO!"))

(def session todo/session)

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

(defonce r (mult todo/response-ch))
(def query-ch (chan 10 todo/response->q-results-xf))

(use-fixtures :each
              (fn [f]
                (tap r query-ch)
                (put! query-ch [nil session])
                (f)
                (untap r query-ch)))

(deftest new-todo
  (<!!
    (go
      (todo-response (<! query-ch) td1)
      (todo-response (<! query-ch) td2)
      (let [{::todo/keys [update-title-requests update-done-requests retract-todo-requests]} (<! query-ch)
            expected #{td1 td2}]
        (is (= expected (set (map ::specs/Todo update-title-requests))))
        (is (= expected (set (map ::specs/Todo update-done-requests))))
        (is (= expected (set (map ::specs/Todo retract-todo-requests))))))))