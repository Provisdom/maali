(ns provisdom.conduit.rules
  (:require [clojure.spec.alpha :as s]
            [provisdom.conduit.specs :as specs]
            [provisdom.maali.rules #?(:clj :refer :cljs :refer-macros) [defrules defqueries] :as rules]
            [clara.rules.accumulators :as acc]))

(defrules rules
  [::comments-response!
   [::specs/ActiveArticle (= ?slug slug)]
   [::specs/CommentsResponse (= ?slug slug) (= ?response response)]
   =>
   (apply rules/insert! ::specs/Comment (:comments ?response))]

  [::handle-cancelled-request!
   [?response <- ::specs/CommentsResponse (= ?slug slug)]
   [:not [::specs/ActiveArticle (= ?slug slug)]]
   =>
   (rules/retract! ::specs/CommentsResponse ?response)])

#_(enable-console-print!)
(cljs.pprint/pprint rules)

(defqueries queries
  [::active-page [] [?active-page <- ::specs/ActivePage (= ?page page)]]
  [::articles [] [?article <- ::specs/Article]]
  [::article-count [] [::specs/ArticleCount (= ?count count)]]
  [::active-article [] [::specs/ActiveArticle (= ?slug slug)]]
  [::tags [] [?tag <- ::specs/Tag]]
  [::comments [] [?comment <- ::specs/Comment]]
  [::comments-response [] [?comments-response <- ::specs/CommentsResponse]]
  [::profile [] [?profile <- ::specs/Profile]]
  [::loading [] [?loading <- ::specs/Loading]]
  [::filter [] [?filter <- ::specs/Filter]]
  [::errors [] [?errors <- ::specs/Error]]
  [::user [] [?user <- ::specs/User]])
