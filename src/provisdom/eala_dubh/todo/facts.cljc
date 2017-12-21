(ns provisdom.eala-dubh.todo.facts
  (:require [clojure.spec.alpha :as s]))

(s/def ::id uuid?)
(s/def ::title string?)
(s/def ::edit boolean?)
(s/def ::done boolean?)
(s/def ::Todo (s/keys :req [::id ::title ::edit ::done]))

(s/def ::visibility #{:all :active :completed})
(s/def ::Visibility (s/keys :req [::visibility]))