(ns provisdom.conduit.app
  (:require [provisdom.conduit.specs :as specs]
            [provisdom.maali.rules :refer-macros [defsession] :as rules]
            [provisdom.conduit.rules :as todo]
            [provisdom.maali.listeners :as listeners]
            [provisdom.maali.pprint]
            [provisdom.conduit.commands :as commands]
            [provisdom.conduit.effects :as effects]
            [provisdom.conduit.view :as view]
            [clojure.spec.test.alpha :as st]
            [cljs.core.async :as async]
            [cljs.pprint :refer [pprint]]
            [clara.tools.inspect]))

#_(enable-console-print!)

(set! (.-onerror js/window) #(do
                               (println "FAARK!!!!!!!!!!!!!!!!!")
                               (when-let [explanation (-> % ex-data :explanation)] (pprint explanation))
                               (pprint %)))

(defn init
  [])