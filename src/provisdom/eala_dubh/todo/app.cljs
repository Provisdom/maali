(ns provisdom.eala-dubh.todo.app
  (:require-macros [provisdom.eala-dubh.rules :refer [deffacttype defrules defsession]])
  (:require [provisdom.eala-dubh.dom :as dom]
            [clara.rules :refer [insert insert-all retract fire-rules query insert! retract!]]
            [clara.rules.accumulators :as acc]
            [provisdom.eala-dubh.todo.rules :as todo]
            [provisdom.eala-dubh.todo.facts :as f]
            [provisdom.eala-dubh.session :as session]
            [cljs.pprint :refer [pprint]]))


(enable-console-print!)

(defsession s [provisdom.eala-dubh.todo.rules/rules provisdom.eala-dubh.todo.queries/queries] {:fact-type-fn session/gettype})
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
        (f/->Start session-key)
        (f/->Todo 1 "Hi" false false)
        (f/->Todo 2 "there!" false false)
        (f/->Visibility :all))

      (session/fire-rules!))

  (println "AWWWWDUNN!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
  #_(.log js/console "INIT" (vec (query @session/session q)))
  #_(.log js/console (mapv :?foo (query @foo/fuus dom/q)))

  (let [c (.. js/document (createElement "DIV"))]
    #_(aset c "innerHTML" "<p>i'm dynamically created</p>")
    #_(.. js/document (getElementById "container") (appendChild c))
    #_(dom/patch (.getElementById js/document "container") [:div {:id "fooble"} "Foo"])))

(deffacttype Foo [foo])

(defrules rools
          [::foob [[`Foo [{foo :foo}] (= ?foo foo)]
                  =>
                  (println "FOOOOO" ?foo)]]
          [::baab [[`Foo [{foo :foo}] (= foo "bar")]
                  =>
                  (println "BAAAAA")]])

#_(defrule foob
           [`Foo [{foo :foo}] (= ?foo foo)]
           =>
           (println "FOOOOO" ?foo))

#_(defrule baab
           [`Foo [{foo :foo}] (= foo "bar")]
           =>
           (println "BAAAAA"))

(defsession poo [provisdom.eala-dubh.todo.app/rools] {:fact-type-fn session/gettype})

#_(defsession poo 'provisdom.eala-dubh.app :fact-type-fn session/gettype)


(-> poo
    (insert (->Foo "foo") (->Foo "bar"))
    (fire-rules))
