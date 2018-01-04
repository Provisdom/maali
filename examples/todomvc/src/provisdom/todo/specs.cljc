(ns provisdom.todo.specs
  (:require [clojure.spec.alpha :as s]))

;;; Fact specs. Use convention that specs for fact "types" are camel-cased.
(s/def ::id int?)
(s/def ::title string?)
(s/def ::edit boolean?)
(s/def ::done boolean?)
(s/def ::Todo (s/keys :req [::id ::title ::edit ::done]))
(s/def ::todo-attrs (s/keys :opt [::title ::edit ::done]))

(s/def ::visibility #{:all :active :completed})
(s/def ::Visibility (s/keys :req [::visibility]))

(s/def ::count (s/int-in 0 #?(:clj Long/MAX_VALUE :cljs js/Number.MAX_SAFE_INTEGER)))
(s/def ::Active (s/keys :req [::count]))
(s/def ::Completed (s/keys :req [::count]))

(s/def ::all-completed boolean?)
(s/def ::All-Completed (s/keys :req [::all-completed]))

(s/def ::show-clear boolean?)
(s/def ::Show-Clear (s/keys :req [::show-clear]))

;;; Convenience function to create new ::Todo facts
(def next-id (atom 0))

(defn new-todo
  [title]
  #::{:id (swap! next-id inc) :title title :done false :edit false})
