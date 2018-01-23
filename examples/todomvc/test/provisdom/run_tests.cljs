(ns provisdom.run-tests
  (:require
    [doo.runner :refer-macros [doo-tests]]
    [provisdom.todo-test]))

(doo-tests 'provisdom.todo-test)