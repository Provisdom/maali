(ns provisdom.eala-dubh.util)

(defn force-children [x]
  (cond->> x
           (seq? x) (into [] (map force-children))))