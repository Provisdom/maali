(ns provisdom.eala-dubh.todo.intents
  (:require [clojure.core.async :as async]))

(defonce intent-ch (async/chan))

(defn dispatch
  [command]
  (async/put! intent-ch command))