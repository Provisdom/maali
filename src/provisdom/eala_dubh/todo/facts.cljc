(ns provisdom.eala-dubh.todo.facts
  (:require #?(:clj [provisdom.eala-dubh.rules :refer [deffacttype]]
               :cljs [provisdom.eala-dubh.rules :refer-macros [deffacttype]])))

(deffacttype Start [session-key])

(deffacttype Todo [id title edit done])

(deffacttype Active [count])

(deffacttype Done [count])

(deffacttype Total [count])

(deffacttype Visibility [visibility])