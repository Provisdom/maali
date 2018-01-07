(ns provisdom.conduit.view
  (:require [clojure.spec.alpha :as s]
            [provisdom.conduit.specs :as specs]))

(s/def ::tags (s/cat :var #{:tags} :val (s/coll-of ::specs/tag)))
(s/def ::articles (s/cat :var #{:articles} :val (s/coll-of ::specs/Article)))
(s/def ::article-count (s/cat :var #{:article-count} :val ::specs/count))
(s/def ::comments (s/cat :var #{:comments} :val (s/coll-of ::specs/Comment)))
(s/def ::loading (s/cat :var #{:loading} :val (s/coll-of ::specs/section)))
(s/def ::page (s/cat :var #{:page} :val ::specs/page-name))
(s/def ::article (s/cat :var #{:article} :val ::specs/Article))
(s/def ::target (s/alt ::articles ::articles
                       ::article-count ::article-count
                       ::page ::page
                       ::tags ::tags
                       ::article ::article
                       ::comments ::comments
                       ::loading ::loading))

(defn render
  [var val]
  (println "RENDER" var val))