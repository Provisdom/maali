(ns provisdom.conduit.specs
  (:require [clojure.spec.alpha :as s]))

(s/def ::count #?(:clj Long/MAX_VALUE :cljs js/Number.MAX_SAFE_INTEGER))

(s/def ::page #{:home :login :register :settings :editor :article :profile :favorited})
(s/def ::ActivePage (s/keys :req [::page]))

(s/def ::token object?)
(s/def ::username string?)
(s/def ::image string?)
(s/def ::bio string?)
(s/def ::email string?)
(s/def ::User (s/keys :req [::username ::image ::bio ::email ::token ::following]))

(s/def ::following boolean?)
(s/def ::Profile (s/keys :req [::username ::image ::bio ::following]))

(s/def ::tag string?)
(s/def ::Tag (s/keys :req [::tag]))

(s/def ::author ::User)
(s/def ::body string?)
(s/def ::created-at inst?)
(s/def ::description string?)
(s/def ::favorited boolean?)
(s/def ::favorites-count ::count)
(s/def ::slug string?)
(s/def ::tag-list (s/coll-of ::tag))
(s/def ::title string?)
(s/def ::updated-at inst?)
(s/def ::Article (s/keys :req [::author ::body ::created-at ::description ::favorited ::favorites-count
                               ::slug ::tag-list ::title ::updated-at]))

(s/def ::id int?)
(s/def ::Comment (s/keys :req [::author ::body ::created-at ::updated-at ::id]))

(s/def ::ActiveArticle (s/keys :req [::slug]))

(s/def ::ArticleCount (s/keys :req [::count]))

(s/def ::section #{:articles :article :tags :comments :login
                   :register-user :update-user :toggle-follow-user
                   :toggle-favorite-article})

(s/def ::Loading (s/keys :req [::section]))

(s/def ::offset ::count)
(s/def ::favorites boolean?)
(s/def ::feed boolean?)
(s/def ::Filter (s/keys :opt [::author ::tag ::offset ::favorites ::feed]))

(s/def ::Error (s/tuple keyword? string?))

(s/def ::response map?)
(s/def ::CommentsResponse (s/keys :req [::response ::slug]))