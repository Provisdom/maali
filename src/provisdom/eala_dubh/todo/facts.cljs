(ns provisdom.eala-dubh.todo.facts
  (:require provisdom.eala-dubh.session)
  (:require-macros [provisdom.eala-dubh.rules :refer [deffacttype]]))

(deffacttype Start [session-key])

(deffacttype Todo [id title edit done])

(deffacttype Active [count])

(deffacttype Done [count])

(deffacttype Total [count])

(deffacttype Visibility [visibility])