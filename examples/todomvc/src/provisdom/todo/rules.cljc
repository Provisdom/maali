(ns provisdom.todo.rules
  (:require [clojure.spec.alpha :as s]
    #?(:clj
            [clojure.core.async :as async]
       :cljs [cljs.core.async :as async])
            [provisdom.todo.specs :as specs]
            [provisdom.maali.rules #?(:clj :refer :cljs :refer-macros) [defrules defqueries defsession] :as rules]
            [clara.rules.accumulators :as acc]
            [provisdom.maali.listeners :as listeners]
            [net.cgrand.xforms :as xforms]))

(enable-console-print!)

(def current (acc/max ::specs/time :returns-fact true))

(def response-ch (async/chan 10))

(defn response
  [spec response]
  (if (s/valid? spec response)
    (async/put! response-ch [spec response])
    (throw (ex-info (str "Invalid response - must conform to spec " spec)
                    {:response response :spec spec :explanation (s/explain-data spec response)}))))

(defn handle-response
  [session [spec response]]
  (if session
    (-> session
        (rules/insert spec response)
        (rules/fire-rules))
    response))


(def handle-response-xf
  (comp
    (xforms/reductions (listeners/session-reducer-with-query-listener handle-response) nil)
    (drop 1)))                                              ;;; drop the initial nil value

;;; Convenience function to create new ::Todo facts
(def id (atom 0))
(defn next-id [] (swap! id inc))
(defn new-todo
  ([title] (new-todo (next-id) title))
  ([id title]
   #::specs{:id id :title title :done false}))

;;; Rules
(defrules rules
  [::retracted-request!
   [?response <- ::specs/Response (= ?request Request)]
   [:not [?request <- ::specs/Request]]
   =>
   (rules/retract! ::specs/Response ?response)]

  [::anchor!
   [::specs/Anchor (= ?time time)]
   =>
   (rules/insert-unconditional! ::specs/Visibility {::specs/visibility :all})
   (rules/insert-unconditional! ::specs/NewTodoRequest {::specs/response-fn (partial response ::specs/NewTodoResponse)})]

  [::new-todo-response!
   [?request <- ::specs/NewTodoRequest]
   [::specs/NewTodoResponse (= ?request Request) (= ?todo Todo)]
   =>
   (rules/insert-unconditional! ::specs/Todo ?todo)
   (rules/upsert! ::specs/NewTodoRequest ?request assoc ::specs/time (specs/now))]

  [::update-request!
   [::specs/Visibility (= ?visibility visibility)]
   [?todo <- ::specs/Todo (condp = ?visibility
                            :all true
                            :active (= false done)
                            :completed (= true done))]
   =>
   (rules/insert! ::specs/UpdateTitleRequest #::specs{:Todo ?todo :response-fn (partial response ::specs/UpdateTitleResponse)})
   (rules/insert! ::specs/UpdateDoneRequest #::specs{:Todo ?todo :response-fn (partial response ::specs/UpdateDoneResponse)})
   (rules/insert! ::specs/RetractTodoRequest #::specs{:Todo ?todo :response-fn (partial response ::specs/Response)})]

  [::update-title-response!
   [?request <- ::specs/UpdateTitleRequest (= ?todo Todo)]
   [::specs/UpdateTitleResponse (= ?request Request) (= ?title title)]
   =>
   (rules/upsert! ::specs/Todo ?todo assoc ::specs/title ?title)]

  [::update-done-response!
   [?request <- ::specs/UpdateDoneRequest (= ?todo Todo)]
   [::specs/UpdateDoneResponse (= ?request Request) (= ?done done)]
   =>
   (rules/upsert! ::specs/Todo ?todo assoc ::specs/done ?done)]

  [::retract-todo-response!
   [?request <- ::specs/RetractTodoRequest (= ?todo Todo)]
   [::specs/Response (= ?request Request)]
   =>
   (rules/retract! ::specs/Todo ?todo)]

  [::complete-all-request!
   [?todos <- (acc/grouping-by ::specs/done) :from [::specs/Todo]]
   =>
   (rules/insert! ::specs/CompleteAllRequest #::specs{:done        (not= 0 (count (?todos false)))
                                                      :response-fn (partial response ::specs/Response)})]

  [::complete-all-response!
   [?request <- ::specs/CompleteAllRequest (= ?done done)]
   [::specs/Response (= ?request Request)]
   [?todos <- (acc/all) :from [::specs/Todo (= (not ?done) done)]]
   =>
   (rules/upsert-seq! ::specs/Todo ?todos update ::specs/done not)]

  [::retract-completed-request!
   [?todos <- (acc/all) :from [::specs/Todo (= true done)]]
   [:test (not-empty ?todos)]
   =>
   (rules/insert! ::specs/RetractCompletedRequest #::specs{:response-fn (partial response ::specs/Response)})]

  [::retract-completed-response!
   [?request <- ::specs/RetractCompletedRequest]
   [::specs/Response (= ?request Request)]
   [?todos <- (acc/all) :from [::specs/Todo (= true done)]]
   =>
   (apply rules/retract! ::specs/Todo ?todos)]

  [::visibility-request!
   [::specs/Visibility (= ?visibility visibility)]
   [?todos <- (acc/grouping-by ::specs/done) :from [::specs/Todo]]
   =>
   (let [visibilities (cond-> #{:all}
                              (not= 0 (count (?todos true))) (conj :completed)
                              (not= 0 (count (?todos false))) (conj :active))]
     (rules/insert! ::specs/VisibilityRequest #::specs{:visibility   ?visibility
                                                       :visibilities visibilities
                                                       :response-fn  (partial response ::specs/VisibilityResponse)}))]

  [::visibility-response!
   [?request <- ::specs/VisibilityRequest (= ?visibilities visibilities)]
   [::specs/VisibilityResponse (= ?request Request) (= ?visibility visibility) (contains? ?visibilities visibility)]
   [?Visibility <- ::specs/Visibility]
   =>
   (rules/upsert! ::specs/Visibility ?Visibility assoc ::specs/visibility ?visibility)])

;;; Queries
(defqueries queries
  [::new-todo-request [] [?request <- ::specs/NewTodoRequest]]
  [::update-title-requests [] [?request <- ::specs/UpdateTitleRequest]]
  [::update-done-requests [] [?request <- ::specs/UpdateDoneRequest]]
  [::retract-todo-requests [] [?request <- ::specs/RetractTodoRequest]]
  [::complete-all-request [] [?request <- ::specs/CompleteAllRequest]]
  [::retract-complete-request [] [?request <- ::specs/RetractCompletedRequest]]
  [::visibility-request [] [?request <- ::specs/VisibilityRequest]]
  [::active-count [] [?count <- (acc/count) :from [::specs/Todo (= false done)]]]
  [::completed-count [] [?count <- (acc/count) :from [::specs/Todo (= true done)]]]
  [::responses [] [?request <- ::specs/Response]])

(defn many-query-xf
  [results]
  (mapv :?request results))

(defn single-query-xf
  [results]
  (-> results first :?request))

(def query-xfs
  {::new-todo-request         single-query-xf
   ::update-title-requests    many-query-xf
   ::update-done-requests     many-query-xf
   ::retract-todo-requests    many-query-xf
   ::complete-all-request     single-query-xf
   ::retract-complete-request single-query-xf
   ::visibility-request       single-query-xf
   ::active-count             #(-> % first :?count)
   ::completed-count          #(-> % first :?count)
   ::responses                many-query-xf})

(defn handle-query-result
  [result]
  (into {} (map (fn [[k v]] [k ((or (query-xfs k) identity) v)]) result)))

(def query-xf (map handle-query-result))

(defsession init-session [provisdom.todo.rules/rules provisdom.todo.rules/queries]
  {:fact-type-fn rules/spec-type})

(def session (-> init-session
                 (listeners/with-listener listeners/query-listener)
                 (rules/insert ::specs/Anchor {::specs/time (specs/now)})
                 (rules/fire-rules)))

(def response->q-results-xf (comp handle-response-xf listeners/query-bindings-xf query-xf))