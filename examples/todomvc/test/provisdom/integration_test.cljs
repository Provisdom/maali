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
  [query-results]
  (rand-nth (filter #(isa? (rules/spec-type %) ::specs/Request) (apply concat (map #(if (seq? %) % [%]) (vals query-results))))))

(defn abuse
  [iterations delay-ms]
  (let [view-ch (async/chan 1)]
    (add-watch view/view-state :view
               (fn [_ _ _ query-results]
                 (async/put! view-ch query-results)))
    (async/go-loop [i 0]
      (enable-console-print!)
      (when (< i iterations)
        (when-some [result (<! view-ch)]
          (let [request (select-request result)
                response-fn (::specs/response-fn request)
                response (gen-response request)]
            (response-fn response))
          (<! (async/timeout delay-ms))
          (recur (inc i))))
      (remove-watch view/view-state :view)
      (println "DONE!"))))


(defn abuse-async
  [iterations delay-ms max-responses]
  (let [view-ch (async/chan 1)]
    (add-watch view/view-state :view
               (fn [_ _ _ query-results]
                 (async/put! view-ch query-results)))
    (async/go-loop [i 0]
      (enable-console-print!)
      (when (< i iterations)
        (let [[result port] (async/alts! [view-ch (async/timeout delay-ms)])
              n (if (< (rand) (/ 2 (dec max-responses))) (rand-int max-responses) 0)
              requests (if (= port view-ch) (repeatedly n #(select-request result)) [(select-request @view/view-state)])]
          (doseq [request requests]
            (let [response-fn (::specs/response-fn request)
                  response (gen-response request)]
              (response-fn response)))
          (<! (async/timeout delay-ms))
          (recur (+ i (inc n)))))
      (remove-watch view/view-state :view)
      (println "DONE!"))))

