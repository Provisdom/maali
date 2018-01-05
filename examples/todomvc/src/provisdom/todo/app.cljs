(ns provisdom.todo.app
  (:require [provisdom.todo.specs :as specs]
            [provisdom.maali.rules :refer-macros [defsession] :as rules]
            [provisdom.todo.rules :as todo]
            [provisdom.maali.listeners :as listeners]
            [provisdom.maali.pprint]
            [provisdom.todo.commands :as commands]
            [provisdom.todo.view :as view]
            [clojure.spec.test.alpha :as st]
            [cljs.core.async :as async]
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

(def xf (comp commands/update-state-xf
              listeners/query-bindings-xf
              commands/query-result-xf))
(def debug-xf (comp commands/debug-update-state-xf
                    listeners/query-bindings-xf
                    commands/query-result-xf))
(defonce command-ch (async/chan 1 xf))
(defonce view-ch (async/chan 1))

(def init-cmds [[:init session]
                [:update-visibility :all]
                [:insert-many [(specs/new-todo "Rename Cloact to Reagent")
                               (specs/new-todo "Add undo demo")
                               (specs/new-todo "Make all rendering async")
                               (specs/new-todo "Allow any arguments to component functions")]]])

(async/pipe view/intent-ch command-ch)
(async/pipe command-ch view-ch)

(defn init []
  (view/run)
  (async/go-loop [commands (async/<! view-ch)]
    (when commands
      (view/update-view commands)
      (recur (async/<! view-ch))))
  (async/onto-chan view/intent-ch init-cmds false))
