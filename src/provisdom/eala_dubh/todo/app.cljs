(ns provisdom.eala-dubh.todo.app
  (:require [provisdom.eala-dubh.rules :refer-macros [defsession] :as rules]
            [provisdom.eala-dubh.todo.rules :as todo]
            [provisdom.eala-dubh.listeners :as listeners]
            [provisdom.eala-dubh.pprint]
            [provisdom.eala-dubh.todo.commands :as commands]
            [provisdom.eala-dubh.todo.view :as view]
            [clojure.core.async :as async]
            [cljs.pprint :refer [pprint]]))


(enable-console-print!)

(set! (.-onerror js/window) #(do
                               (println "FAARK!!!!!!!!!!!!!!!!!")
                               (when-let [explanation (-> % ex-data :explanation)] (pprint explanation))
                               (pprint %)))

(defn reload
  []
  #_(session/register s session-key)
  #_(session/reload-session :foo))

(defsession session [provisdom.eala-dubh.todo.rules/rules provisdom.eala-dubh.todo.rules/queries]
  {:fact-type-fn rules/spec-type})

(defn init []
  (let [init-cmds [[:init session]
                   [:insert-many :todos [(todo/new-todo "Rename Cloact to Reagent")
                                         (todo/new-todo "Add undo demo")
                                         (todo/new-todo "Make all rendering async")
                                         (todo/new-todo "Allow any arguments to component functions")]]]
        xf (comp commands/update-state-xf
                 listeners/query-bindings-xf
                 commands/query-result-xf)
        debug-xf (comp commands/debug-update-state-xf
                       listeners/query-bindings-xf
                       commands/query-result-xf)
        view-ch (async/chan 1 xf)]
    (async/pipe view/intent-ch view-ch)
    (view/run)
    (async/go-loop [commands (async/<! view-ch)]
                   (when commands
                     (view/update-view commands)
                     (recur (async/<! view-ch))))
    (async/onto-chan view/intent-ch init-cmds false)))
