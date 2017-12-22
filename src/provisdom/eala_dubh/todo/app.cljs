(ns provisdom.eala-dubh.todo.app
  (:require [provisdom.eala-dubh.dom :as dom]
            [clara.rules :refer [insert insert-all retract fire-rules query insert! retract!]]
            [clara.rules.accumulators :as acc]
            [provisdom.eala-dubh.rules :refer-macros [deffacttype defrules defsession] :as rules]
            [provisdom.eala-dubh.todo.rules :as todo]
            [provisdom.eala-dubh.todo.commands]
            [provisdom.eala-dubh.tracing]
            [provisdom.eala-dubh.listeners]
            [cljs.pprint :refer [pprint]]
            [provisdom.eala-dubh.pprint]))


#_(enable-console-print!)

(set! (.-onerror js/window) #(pprint %))

(defn reload
  []
  #_(session/register s session-key)
  #_(session/reload-session :foo))

(defn init []
  (println "AWWWWDUNN!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"))
