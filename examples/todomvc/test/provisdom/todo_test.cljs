(ns provisdom.todo-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests use-fixtures async]]
            [clojure.core.async :as async :refer [<! >!]]
            [provisdom.todo.specs :as specs]
            [provisdom.maali.rules :refer-macros [defrules defqueries defsession] :as rules]
            [provisdom.todo.rules :as todo]
            [provisdom.maali.listeners :as listeners]
            [provisdom.maali.pprint :refer-macros [pprint]]
            [net.cgrand.xforms :as xforms]))

(defsession session [provisdom.todo.rules/rules provisdom.todo.rules/queries]
  {:fact-type-fn rules/spec-type})

(def td1 (todo/new-todo "Hi"))
(def td2 (todo/new-todo "there"))
(def td3 (todo/new-todo "Hi!"))
(def td4 (todo/new-todo "FOO!"))

(def handle-response (listeners/session-reducer-with-query-listener todo/handle-response))

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
(def handle-response-xf (comp (xforms/reductions handle-response nil) (drop 1)))
(def process (comp handle-response-xf listeners/query-bindings-xf todo/query-xf))
(def query-ch (async/chan 10 process))
(def s (-> session
           (listeners/with-listener listeners/query-listener)
           (rules/insert ::specs/Anchor {::specs/time (specs/now)})
           #_(rules/fire-rules)))

(use-fixtures :each
              :before
              (fn []
                (async/tap r query-ch)
                (async/put! query-ch [nil s]))

              :after
              (fn []
                (async/untap r query-ch)))

(deftest new-todo
  (async done
    (async/go
      (todo-response (<! query-ch) td1)
      (todo-response (<! query-ch) td2)
      (let [{::rules/keys [update-title-requests update-done-requests retract-todo-requests]} (<! query-ch)
            expected #{td1 td2}]
        (println "FOO" (= expected (set (map ::specs/Todo update-title-requests))))
        (is (= expected (set (map ::specs/Todo update-title-requests)))))
      (done))))