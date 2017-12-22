(ns provisdom.eala-dubh.todo.commands.view
  (:require [provisdom.eala-dubh.todo.rules :as todo]
            [net.cgrand.xforms :as xforms]))


(defmulti query-result->command key)

(defmethod query-result->command [::todo/visible-todos]
  [[_ todos]]
  [])