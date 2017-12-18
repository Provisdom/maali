(ns provisdom.eala-dubh.todo.facts
  #?(:cljs
     (:require-macros [provisdom.eala-dubh.rules :refer [deffacttype]]))
  (:require #?(:clj [provisdom.eala-dubh.rules :refer [deffacttype]])))

(deffacttype Start [session-key])

(deffacttype Todo [id title edit done])

(deffacttype Active [count])

(deffacttype Done [count])

(deffacttype Total [count])

(deffacttype Visibility [visibility])