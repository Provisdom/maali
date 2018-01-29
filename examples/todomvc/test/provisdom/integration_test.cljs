(ns provisdom.integration-test
  (:require [provisdom.todo.specs :as specs]
            [provisdom.todo.rules :as todo]
            [provisdom.todo.view :as view]
            [provisdom.maali.rules :as rules]
            [cljs.core.async :refer [<! >!] :as async]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sg]
            [cljs.pprint :refer [pprint]]))

(def todos [(todo/new-todo "Rename Cloact to Reagent")
            (todo/new-todo "Add undo demo")
            (todo/new-todo "Make all rendering async")
            (todo/new-todo "Allow any arguments to component functions")])

(def session (apply rules/insert todo/session ::specs/Todo todos))

(defn gen-response
  [request]
  (let [response (sg/generate (s/gen (specs/request->response (rules/spec-type request))))]
    (assoc response ::specs/Request request)))

(defn select-request
  [session]
  (loop [i 0]
    (when (< i 100)
      (let [query (rand-nth (keys todo/request-queries))
            requests (rules/query session query)]
        (if (empty? requests)
          (recur (inc i))
          (:?request (rand-nth requests)))))))

(defn abuse
  [session-ch iterations delay-ms]
  (async/go-loop [i 0]
    (enable-console-print!)
    (when (< i iterations)
      (when-some [session (<! session-ch)]
        (view/run session)
        (let [request (select-request session)
              response-fn (::specs/response-fn request)
              response (gen-response request)]
          (response-fn response))
        (<! (async/timeout delay-ms))
        (recur (inc i))))
    (println "DONE!")))


(defn abuse-async
  [session-ch iterations delay-ms max-responses]
  (async/go-loop [i 0
                  session (<! session-ch)]
    (view/run session)
    (enable-console-print!)
    (when (< i iterations)
      (let [n (if (< (rand) (/ 2 (dec max-responses))) (rand-int max-responses) 0)
            requests (repeatedly n #(select-request session))]
        (let [s (if (= 0 n)
                  session
                  (loop [[request & requests] requests
                         session session]
                    (if request
                      (let [response-fn (::specs/response-fn request)
                            response (gen-response request)]
                        (response-fn response)
                        (recur requests (<! session-ch)))
                      session)))]
          (<! (async/timeout delay-ms))
          (recur (+ i (inc n)) s))))
    (println "DONE!")))

