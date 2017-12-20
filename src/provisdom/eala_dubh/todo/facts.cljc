(ns provisdom.eala-dubh.todo.facts
  (:require [clojure.spec.alpha :as s]
    #?(:clj [provisdom.eala-dubh.rules :refer [deffacttype]]
       :cljs [provisdom.eala-dubh.rules :refer-macros [deffacttype]])))

(s/def ::session-key keyword?)
(s/def ::Start (s/keys :req [::session-key]))

(s/def ::id int?)
(s/def ::title string?)
(s/def ::edit boolean?)
(s/def ::done boolean?)
(s/def ::Todo (s/keys :req [::id ::title ::edit ::done]))

(s/def ::count (s/or :zero zero? :pos pos-int?))
(s/def ::Active (s/keys :req [::count]))
(s/def ::Done (s/keys :req [::count]))
(s/def ::Total (s/keys :req [::count]))

(s/def ::visibility string?)
(s/def ::Visibility (s/keys :req [::visibility]))