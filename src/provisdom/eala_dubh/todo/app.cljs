(ns provisdom.eala-dubh.todo.app
  (:require [provisdom.eala-dubh.dom :as dom]
            [clara.rules :refer [insert insert-all retract fire-rules query insert! retract!]]
            [clara.rules.accumulators :as acc]
            [provisdom.eala-dubh.rules :refer-macros [deffacttype defrules defsession] :as rules]
            [provisdom.eala-dubh.todo.rules :as todo]
            [provisdom.eala-dubh.tracing]
            [provisdom.eala-dubh.listeners]
            [cljs.pprint :refer [pprint]]
            [provisdom.eala-dubh.pprint]
            [provisdom.eala-dubh.todo.commands :as commands]
            [provisdom.eala-dubh.todo.view :as view]
            [provisdom.eala-dubh.todo.intents :as intents]
            [clojure.core.async :as async]))


#_(enable-console-print!)

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
                   [:update :visibility :all]
                   [:insert-many :todos [(todo/new-todo "Rename Cloact to Reagent")
                                         (todo/new-todo "Add undo demo")
                                         (todo/new-todo "Make all rendering async")
                                         (todo/new-todo "Allow any arguments to component functions")]]]
        xf (comp commands/update-state-xf
                 commands/query-bindings-xf
                 commands/query-result-xf)
        view-ch (async/chan 1 xf)]
    (async/pipe intents/intent-ch view-ch)
    (view/run)
    (async/go-loop [commands (async/<! view-ch)]
                   (when commands
                     (view/update-view commands)
                     (recur (async/<! view-ch))))
    (async/onto-chan intents/intent-ch init-cmds false))
  (println "AWWWWDUNN!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"))
