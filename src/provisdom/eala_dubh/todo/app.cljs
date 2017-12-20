(ns provisdom.eala-dubh.todo.app
  (:require-macros [provisdom.eala-dubh.rules :refer [deffacttype defrules defsession]])
  (:require [provisdom.eala-dubh.dom :as dom]
            [clara.rules :refer [insert insert-all retract fire-rules query insert! retract!]]
            [clara.rules.accumulators :as acc]
            [provisdom.eala-dubh.rules :as rules]
            [provisdom.eala-dubh.todo.rules :as todo]
            [provisdom.eala-dubh.todo.facts :as f]
            [provisdom.eala-dubh.session :as session]
            [cljs.pprint :refer [pprint]]))


(enable-console-print!)

(defsession s [provisdom.eala-dubh.todo.rules/rules provisdom.eala-dubh.todo.queries/queries]
  {:fact-type-fn rules/spec-type})
(def session-key ::session)

(defn reload
  []
  (session/register s session-key)
  #_(session/reload-session :foo))

(defn init []
  (-> s
      #_(clara.tools.tracing/with-tracing)
      (session/register session-key)
      (session/insert-unconditional
        (rules/spec-type {::f/session-key session-key} ::f/Start)
        (rules/spec-type {::f/id 1 ::f/title "Hi" ::f/edit false ::f/done false} ::f/Todo)
        (rules/spec-type {::f/id 2 ::f/title "there!" ::f/edit false ::f/done false} ::f/Todo)
        (rules/spec-type {::f/visibility :all} ::f/Visibility) )

      (session/fire-rules!))

  (println "AWWWWDUNN!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
  #_(.log js/console "INIT" (vec (query @session/session q)))
  #_(.log js/console (mapv :?foo (query @foo/fuus dom/q)))

  (let [c (.. js/document (createElement "DIV"))]
    #_(aset c "innerHTML" "<p>i'm dynamically created</p>")
    #_(.. js/document (getElementById "container") (appendChild c))
    #_(dom/patch (.getElementById js/document "container") [:div {:id "fooble"} "Foo"])))
