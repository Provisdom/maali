(ns provisdom.eala-dubh.todo.app
  (:require [provisdom.eala-dubh.dom :as dom]
            [clara.rules :refer [insert insert-all retract fire-rules query insert! retract!]]
            [clara.rules.accumulators :as acc]
            [provisdom.eala-dubh.rules :refer-macros [deffacttype defrules defsession] :as rules]
            #_[provisdom.eala-dubh.todo.rules :as todo]
            [provisdom.eala-dubh.todo.facts :as f]
            [provisdom.eala-dubh.tracing]
            [provisdom.eala-dubh.listeners]
            [cljs.pprint :refer [pprint]]))


#_(enable-console-print!)

(set! (.-onerror js/window) #(pprint %))

#_(defsession s [provisdom.eala-dubh.todo.rules/rules provisdom.eala-dubh.todo.queries/queries]
  {:fact-type-fn rules/spec-type})
(def session-key ::session)

(defn reload
  []
  #_(session/register s session-key)
  #_(session/reload-session :foo))

(defn init []
  #_(-> s
      #_(clara.tools.tracing/with-tracing)
      #_(session/register session-key)
      (rules/insert ::f/Start {::f/session s})
      (rules/insert ::f/Todo
                    {::f/id 1 ::f/title "Hi" ::f/edit false ::f/done false}
                    {::f/id 2 ::f/title "there!" ::f/edit false ::f/done false})
      (rules/insert ::f/Visibility {::f/visibility :all})

      (rules/fire-rules))

  (println "AWWWWDUNN!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
  #_(.log js/console "INIT" (vec (query @session/session q)))
  #_(.log js/console (mapv :?foo (query @foo/fuus dom/q)))

  (let [c (.. js/document (createElement "DIV"))]
    #_(aset c "innerHTML" "<p>i'm dynamically created</p>")
    #_(.. js/document (getElementById "container") (appendChild c))
    #_(dom/patch (.getElementById js/document "container") [:div {:id "fooble"} "Foo"])))
