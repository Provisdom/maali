(ns provisdom.eala-dubh.todo.rules
  (:require [clojure.spec.alpha :as s]
    #?(:clj [provisdom.eala-dubh.rules :refer [defrules defqueries] :as rules]
       :cljs [provisdom.eala-dubh.rules :refer-macros [defrules defqueries] :as rules])
            [clara.rules.accumulators :as acc]))

(s/def ::id uuid?)
(s/def ::title string?)
(s/def ::edit boolean?)
(s/def ::done boolean?)
(s/def ::Todo (s/keys :req [::id ::title ::edit ::done]))

(s/def ::visibility #{:all :active :completed})
(s/def ::Visibility (s/keys :req [::visibility]))

(defrules rules)

(defqueries queries
  [::all-todos
   []
   [?todos <- (acc/all) :from [::Todo]]]
  [::active-todos
   []
   [?todos <- (acc/all) :from [::Todo (= done false)]]]
  [::completed-todos
   []
   [?todos <- (acc/all) :from [::Todo (= done true)]]]
  [::todo-by-id
   [:?id]
   [?todo <- ::Todo [{::keys [id]}] (= ?id id)]]
  [::todo-count
   []
   [?count <- (acc/count) :from [::Todo (= done false)]]]
  [::visibility
   []
   [::Visibility (= ?visibility visibility)]])