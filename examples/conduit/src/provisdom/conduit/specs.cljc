(ns provisdom.conduit.specs
  (:require [clojure.spec.alpha :as s]))

(s/def ::count nat-int?)

(s/def ::page (s/or :home #{:home}
                    :profile (s/keys :req [::username])
                    :article (s/keys :req [::slug])
                    :editor ::Article
                    :login #{:login}
                    :register #{:register}
                    :settings #{:settings}
                    :favorited #{:favorited}))
(s/def ::ActivePage (s/keys :req [::page]))
(s/def ::page-name (->> (s/form ::page)
                       (drop 1)
                       (partition 2)
                       (map first)
                       set))
(s/def ::PageName (s/keys :req [::page-name]))

(s/def ::token (s/nilable string?))
(s/def ::Token (s/keys :req [::token]))

(s/def ::username string?)
(s/def ::image (s/nilable string?))
(s/def ::bio (s/nilable string?))
(s/def ::email string?)
(s/def ::User (s/keys :req-un [::username ::image ::bio ::email] :opt-un [::following]))

(s/def ::following boolean?)
(s/def ::Profile (s/keys :req-un [::username ::image ::bio ::following]))

(s/def ::tag string?)
(s/def ::Tag (s/keys :req [::tag]))

(s/def ::author ::Profile)
(s/def ::body string?)
(s/def ::createdAt string?)
(s/def ::description string?)
(s/def ::favorited boolean?)
(s/def ::favoritesCount ::count)
(s/def ::slug string?)
(s/def ::tagList (s/coll-of ::tag))
(s/def ::title string?)
(s/def ::updatedAt string?)
(s/def ::Article (s/keys :req-un [::author ::body ::createdAt ::description ::favorited ::favoritesCount
                               ::slug ::tagList ::title ::updatedAt]))

(s/def ::id int?)
(s/def ::Comment (s/keys :req-un [::author ::body ::createdAt ::updatedAt ::id]))

(s/def ::ActiveArticle (s/keys :req [::slug]))

(s/def ::ArticleCount (s/keys :req [::count]))

(s/def ::section #{:articles :article :tags :comments :login
                   :register-user :update-user :toggle-follow-user
                   :toggle-favorite-article})

(s/def ::Loading (s/keys :req [::section]))

(s/def ::offset ::count)
(s/def ::favorites boolean?)
(s/def ::feed boolean?)
(s/def ::Filter (s/keys :opt [::author ::tag ::offset ::favorites ::favorited ::feed]))

(s/def ::error (s/tuple keyword? string?))
(s/def ::Error (s/keys :req [::error]))

(s/def ::hash string?)
(s/def ::Hash (s/keys :req [::hash]))

(s/def ::Entity (s/or ::ActivePage ::ActivePage
                      ::ActiveArticle ::ActiveArticle
                      ::User ::User
                      ::Profile ::Profile
                      ::Tag ::Tag
                      ::Article ::Article
                      ::Comment ::Comment
                      ::ArticleCount ::ArticleCount
                      ::Loading ::Loading
                      ::Filter ::Filter
                      ::Error ::Error
                      ::Hash ::Hash))

;;; Requests to server
(s/def ::request map?)
(s/def ::request-type #{:articles :comments :profile :tags})
(s/def ::Request (s/keys :req [::request ::request-type]))
(s/def ::Pending (s/keys :req [::request]))

;;; Responses from server
(s/def ::response map?)
(s/def ::Response (s/keys :req [::response ::request]))
