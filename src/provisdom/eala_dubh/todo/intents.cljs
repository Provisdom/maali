(ns provisdom.eala-dubh.todo.intents
  (:require [clojure.core.async :as async]
            [dommy.core :refer-macros [sel sel1] :as dommy]))

(defonce intent-ch (async/chan))

(defn dispatch
  [command]
  (async/put! intent-ch command))