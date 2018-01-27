(ns provisdom.todo.rules
  (:require [clojure.spec.alpha :as s]
            [clojure.core.async :as async]
            [provisdom.todo.specs :as specs]
            [provisdom.maali.rules #?(:clj :refer :cljs :refer-macros) [defrules defqueries defsession] :as rules]
            [clara.rules.accumulators :as acc]
            [provisdom.maali.listeners :as listeners]
            [net.cgrand.xforms :as xforms]))

;;; Input channel for responses
(def response-ch (async/chan 10))

;;; Used to fill in the ::specs/response-fn field in requests. The code which responds
;;; to a request can use ::specs/response-fn to provide the response.
(defn response
  [spec response]
  (if (s/valid? spec response)
    (async/put! response-ch [spec response])
    (throw (ex-info (str "Invalid response - must conform to spec " spec)
                    {:response response :spec spec :explanation (s/explain-data spec response)}))))

;;; Reducing function to produce the new session from a supplied response.
(defn handle-response
  [session [spec response]]
  (if session
    (-> session
        (rules/insert spec response)
        (rules/fire-rules))
    response))

;;; Transducer which takes in responses and provides reductions over the handle-response function,
;;; i.e. updated sessions.
(def handle-response-xf
  (comp
    (xforms/reductions (listeners/session-reducer-with-query-listener handle-response) nil)
    (drop 1)))                                              ;;; drop the initial nil value

;;; Convenience function to create new ::Todo facts
(defn new-todo
  ([title] (new-todo (random-uuid) title))
  ([id title]
   #::specs{:id id :title title :done false}))

;;; Rules
(defrules rules
  [::retract-orphan-response!
   "Responses are inserted unconditionally from outside the rule engine, so
    explicitly retract any responses without a corresponding request."
   [?response <- ::specs/Response (= ?request Request)]
   [:not [?request <- ::specs/Request]]
   =>
   (rules/retract! ::specs/Response ?response)]

  [::anchor!
   "Initialize visibility and the request for a new todo. Both are singletons
    so insert unconditionally here."
   [::specs/Anchor (= ?time time)]
   =>
   (rules/insert-unconditional! ::specs/Visibility {::specs/visibility :all})
   (rules/insert-unconditional! ::specs/NewTodoRequest {::specs/response-fn (partial response ::specs/NewTodoResponse)})]

  [::new-todo-response!
   "Handle a new todo."
   [?request <- ::specs/NewTodoRequest]
   [::specs/NewTodoResponse (= ?request Request) (= ?todo Todo)]
   =>
   (rules/insert-unconditional! ::specs/Todo ?todo)
   (rules/upsert! ::specs/NewTodoRequest ?request assoc ::specs/time (specs/now))]

  [::update-request!
   "When visibility changes or a new todo is inserted, conditionally insert
    requests to update todos."
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
   "Handle response to a title update request."
   [?request <- ::specs/UpdateTitleRequest (= ?todo Todo)]
   [::specs/UpdateTitleResponse (= ?request Request) (= ?title title)]
   =>
   (rules/upsert! ::specs/Todo ?todo assoc ::specs/title ?title)]

  [::update-done-response!
   "Handle response to a one update request."
   [?request <- ::specs/UpdateDoneRequest (= ?todo Todo)]
   [::specs/UpdateDoneResponse (= ?request Request) (= ?done done)]
   =>
   (rules/upsert! ::specs/Todo ?todo assoc ::specs/done ?done)]

  [::retract-todo-response!
   "Handle response to retract todo request."
   [?request <- ::specs/RetractTodoRequest (= ?todo Todo)]
   [::specs/Response (= ?request Request)]
   =>
   (rules/retract! ::specs/Todo ?todo)]

  [::complete-all-request!
   "Toggles the done attribute of all todos. If all todos are not done,
    then the request implies we set them all to done. If all todos are
    done, then the request means we will set them all to not done."
   [?todos <- (acc/grouping-by ::specs/done) :from [::specs/Todo]]
   =>
   (rules/insert! ::specs/CompleteAllRequest #::specs{:done        (not= 0 (count (?todos false)))
                                                      :response-fn (partial response ::specs/Response)})]

  [::complete-all-response!
   "Handle response to complete all request."
   [?request <- ::specs/CompleteAllRequest (= ?done done)]
   [::specs/Response (= ?request Request)]
   [?todos <- (acc/all) :from [::specs/Todo (= (not ?done) done)]]
   =>
   (rules/upsert-seq! ::specs/Todo ?todos update ::specs/done not)]

  [::retract-completed-request!
   "Request to retract all todo's marked done."
   [?todos <- (acc/all) :from [::specs/Todo (= true done)]]
   [:test (not-empty ?todos)]
   =>
   (rules/insert! ::specs/RetractCompletedRequest #::specs{:response-fn (partial response ::specs/Response)})]

  [::retract-completed-response!
   "Handle response to retract completed request."
   [?request <- ::specs/RetractCompletedRequest]
   [::specs/Response (= ?request Request)]
   [?todos <- (acc/all) :from [::specs/Todo (= true done)]]
   =>
   (apply rules/retract! ::specs/Todo ?todos)]

  [::visibility-request!
   "Request to update the visibility. Includes the valid choices for
    visibility given the current set of todos, e.g. if no todos are
    marked done, don't include completed as a valid option."
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
   "Handle response to visibility request."
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
  [::completed-count [] [?count <- (acc/count) :from [::specs/Todo (= true done)]]])

(defn many-query-xf
  [map-fn results]
  (mapv map-fn results))

(defn single-query-xf
  [map-fn results]
  (-> results first map-fn))

(def many-requests-xf (partial many-query-xf :?request))
(def single-request-xf (partial single-query-xf :?request))
(def count-xf (partial single-query-xf :?count))

(def query-xfs
  {::new-todo-request         single-request-xf
   ::update-title-requests    many-requests-xf
   ::update-done-requests     many-requests-xf
   ::retract-todo-requests    many-requests-xf
   ::complete-all-request     single-request-xf
   ::retract-complete-request single-request-xf
   ::visibility-request       single-request-xf
   ::active-count             count-xf
   ::completed-count          count-xf})

(defn handle-query-result
  [result]
  (into {} (map (fn [[k v]] [k ((or (query-xfs k) identity) v)]) result)))

(def query-xf (map handle-query-result))

(defsession init-session [provisdom.todo.rules/rules provisdom.todo.rules/queries]
  {:fact-type-fn rules/spec-type})

(def session (-> init-session
                 (listeners/with-listener listeners/query-listener)
                 (rules/insert ::specs/Anchor {} #_{::specs/time (specs/now)})
                 (rules/fire-rules)))

(def response->q-results-xf (comp handle-response-xf listeners/query-bindings-xf query-xf))