(ns provisdom.todo.specs
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sg]
            [clojure.test.check.generators]
            [provisdom.maali.rules #?(:clj :refer :cljs :refer-macros) [def-derive]]))

(s/def ::time nat-int?)
(s/def ::Anchor (s/keys :req [::time]))

;;; Fact specs. Use convention that specs for fact "types" are camel-cased.
(s/def ::id uuid?)
(s/def ::title string?)
(s/def ::done boolean?)
(s/def ::created-at ::time)
(s/def ::Todo (s/keys :req [::id ::title ::done]))

(s/def ::visibility #{:all :active :completed})
(s/def ::Visibility (s/keys :req [::visibility]))

(s/def ::Request (s/keys :req [::response-fn]))
(s/def ::Response (s/keys :req [::Request]))
;;; TODO - add predicate that ensures request conforms to spec?
(s/def ::response-fn (s/with-gen fn? #(sg/return (fn [_ _]))) #_(s/fspec :args (s/cat :spec qualified-keyword? :request ::Request)
                            :ret any?))
(def-derive ::NewTodoRequest ::Request)
(def-derive ::NewTodoResponse ::Response (s/merge ::Response (s/keys :req [::Todo])))
(def-derive ::UpdateTodoRequest ::Request (s/merge ::Request (s/keys :req [::Todo])))
(def-derive ::UpdateTitleRequest ::UpdateTodoRequest)
(def-derive ::UpdateTitleResponse ::Response (s/merge ::Response (s/keys :req [::title])))
(def-derive ::UpdateDoneRequest ::UpdateTodoRequest)
(def-derive ::UpdateDoneResponse ::Response (s/merge ::Response (s/keys :req [::done])))
(def-derive ::RetractTodoRequest ::UpdateTodoRequest)
(def-derive ::CompleteAllRequest ::Request (s/merge ::Request (s/keys :req [::done])))
(def-derive ::RetractCompletedRequest ::Request)
(s/def ::visibilities (s/coll-of ::visibility :kind set?))
(def-derive ::VisibilityRequest ::Request (s/merge ::Request (s/keys :req [::visibilities])))
(def-derive ::VisibilityResponse ::Response (s/merge ::Response (s/keys :req [::visibility])))

(def request->response
  {::NewTodoRequest ::NewTodoResponse
   ::UpdateTitleRequest ::UpdateTitleResponse
   ::UpdateDoneRequest ::UpdateDoneResponse
   ::RetractTodoRequest ::Response
   ::CompleteAllRequest ::Response
   ::RetractCompletedRequest ::Response
   ::VisibilityRequest ::VisibilityResponse})