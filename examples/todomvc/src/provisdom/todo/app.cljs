(ns provisdom.todo.app
  (:require [provisdom.todo.specs :as specs]
            [provisdom.todo.rules :as todo]
            [provisdom.todo.view :as view]
            [provisdom.maali.rules :as rules]
            [cljs.core.async :refer [<! >!] :as async]
            [cljs.pprint :refer [pprint]]))

#_(st/instrument)

(set! (.-onerror js/window) #(do
                               (println "I wish this actually caught all exceptions.")
                               (when-let [explanation (-> % ex-data :explanation)] (pprint explanation))
                               (pprint %)))

(defn reload
  [])

;;; Handy function to help initialize the list of todos
(defn todo-response
  [{::todo/keys [new-todo-request] :as result} todo]
  (let [{::specs/keys [response-fn]} new-todo-request]
    (response-fn #::specs{:Request new-todo-request :Todo todo})))

(def todos [(todo/new-todo "Rename Cloact to Reagent")
            (todo/new-todo "Add undo demo")
            (todo/new-todo "Make all rendering async")
            (todo/new-todo "Allow any arguments to component functions")])

(def session (apply rules/insert todo/session ::specs/Todo todos))

(defn init []
  ;;; Initialize the view
  (view/run)

  (let [query-ch (async/chan 10 todo/response->q-results-xf)]
    ;;; Initialize with the session.
    (async/put! query-ch [nil session])

    ;;; Connect the response channel to the processing pipeline.
    (async/pipe todo/response-ch query-ch)

    (async/go-loop []
      (when-some [result (<! query-ch)]
        (view/update-view result)
        (recur)))))
