(ns sandbox.foo
  (:require [provisdom.conduit.specs :as specs]
            [provisdom.maali.rules :refer-macros [defrules defqueries defsession] :as rules]
            [provisdom.conduit.rules :as conduit]
            [provisdom.maali.listeners :as listeners]
            [provisdom.maali.pprint :refer-macros [pprint]]
            [provisdom.conduit.commands :as commands]
            [clara.tools.inspect :as inspect]))

(defsession session [provisdom.conduit.rules/rules provisdom.conduit.rules/queries]
            {:fact-type-fn rules/spec-type})

(def user1 #::specs{:username "Foo" :image "" :bio "" :email "" :token #js {} :following false})
(def user2 #::specs{:username "Bar" :image "" :bio "" :email "" :token #js {} :following true})
(def comment1 #::specs{:id 1 :author user1 :created-at #inst "2018-01-01" :updated-at #inst "2018-01-02" :body "Comment 1"})
(def comment2 #::specs{:id 2 :author user2 :created-at #inst "2018-01-01" :updated-at #inst "2018-01-02" :body "Comment 2"})
(def aa {::specs/slug "foo"})

(def s1 (-> session
            (rules/insert ::specs/User user1 user2)
            (rules/insert ::specs/ActiveArticle aa)
            (rules/fire-rules)))

(rules/query s1 ::conduit/active-article)
(rules/query s1 ::conduit/comments-response)

(def s2 (-> s1
            (rules/insert ::specs/CommentsResponse {::specs/slug "foo" ::specs/response {:comments [comment1 comment2]}})
            (rules/fire-rules)))

(rules/query s2 ::conduit/comments-response)
(rules/query s2 ::conduit/comments)

(def s3 (-> s1
            (rules/retract ::specs/ActiveArticle aa)
            (rules/fire-rules)))

(rules/query s3 ::conduit/active-article)
(-> (rules/query s3 ::conduit/active-article) first :?aa)

(def s4 (-> s3
            (rules/insert ::specs/CommentsResponse {::specs/slug "foo" ::specs/response {:comments [comment1 comment2]}})
            (rules/fire-rules)))

(rules/query s4 ::conduit/comments-response)
(rules/query s4 ::conduit/comments)
(enable-console-print!)
(inspect/explain-activations s4)
(:rule-matches (inspect/inspect s4))