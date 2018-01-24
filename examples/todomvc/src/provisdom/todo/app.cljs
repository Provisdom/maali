(ns provisdom.todo.app
  (:require [provisdom.todo.specs :as specs]
            [provisdom.maali.rules :refer-macros [defsession] :as rules]
            [provisdom.todo.rules :as todo]
            [provisdom.maali.listeners :as listeners]
            [provisdom.maali.pprint]
            [provisdom.todo.commands :as commands]
            [provisdom.todo.view :as view]
            [cljs.core.async :refer [<! >!] :as async]
            [net.cgrand.xforms :as xforms]
            [cljs.pprint :refer [pprint]]))


(enable-console-print!)

#_(st/instrument)

(set! (.-onerror js/window) #(do
                               (println "FAARK!!!!!!!!!!!!!!!!!")
                               (when-let [explanation (-> % ex-data :explanation)] (pprint explanation))
                               (pprint %)))

(defn reload
  []
  #_(session/register s session-key)
  #_(session/reload-session :foo))

(defsession session [provisdom.todo.rules/rules provisdom.todo.rules/queries]
  {:fact-type-fn rules/spec-type})

(defn todo-response
  [{::todo/keys [new-todo-request] :as result} todo]
  #_(pr-res result)
  (let [{::specs/keys [response-fn]} new-todo-request]
    (response-fn #::specs{:Request new-todo-request :Todo todo})))

(def todos [(todo/new-todo "Rename Cloact to Reagent")
            (todo/new-todo "Add undo demo")
            (todo/new-todo "Make all rendering async")
            (todo/new-todo "Allow any arguments to component functions")])

(defonce r (async/mult todo/response-ch))

(defn init []
  (view/run)
  (let [query-ch (async/chan 10 todo/response->q-results-xf)]
    (async/put! query-ch [nil todo/session])
    (async/tap r query-ch)
    (async/go
      (loop [result (<! query-ch)
             [todo & todos] todos]
        (view/update-view result)
        (when (and result todo)
          (todo-response result todo)
          (recur (<! query-ch) todos)))

      (loop [result (<! query-ch)]
        (when result
          (view/update-view result)
          (recur (<! query-ch)))))))
