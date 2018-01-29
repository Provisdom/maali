;;; Derived from precept todomvc example: https://github.com/CoNarrative/precept/tree/master/examples/todomvc

(ns provisdom.todo.view
  (:require [rum.core :as rum]
            [provisdom.todo.specs :as specs]
            [provisdom.todo.rules :as todo]
            [provisdom.maali.rules :as rules]))

;;; Convenience function to respond to a request
(defn respond-to
  ([request] (respond-to request {}))
  ([request response]
   (let [response-fn (::specs/response-fn request)]
     (response-fn (merge {::specs/Request request} response)))))

;;; Some query boilerplate functions
(defn query-many
  [map-fn session query & args]
  (mapv map-fn (apply rules/query session query args)))

(defn query-one
  [map-fn session query & args]
  (-> (apply rules/query session query args) first map-fn))

;;; View components
(def todo-input-title
  {:will-mount
   (fn [{[title] :rum/args :as state}]
     (let [local-state (atom title)
           component (:rum/react-component state)]
       (add-watch local-state :todo-input-title
                  (fn [_ _ _ _]
                    (rum/request-render component)))
       (assoc state :todo-input-title local-state)))})

(rum/defcs todo-input < todo-input-title
  [state title {:keys [ on-stop id class placeholder on-save]}]
  (let [val (:todo-input-title state)
        stop #(do (reset! val "")
                  (if on-stop (on-stop)))
        save (fn [on-save]
               (let [v (-> @val str clojure.string/trim)]
                 (if-not (empty? v) (on-save v))
                 (stop)))]
    [:input {:auto-focus  (boolean (not-empty title))
             :type        "text"
             :value       @val
             :id          id
             :class       class
             :placeholder placeholder
             :on-blur     #(save on-save)
             :on-change   #(reset! val (-> % .-target .-value))
             :on-key-down #(case (.-keyCode %)
                             13 (save on-save)
                             27 (stop)
                             nil)}]))

(def todo-edit todo-input #_(with-meta todo-input
                                       {:component-did-mount #(.focus (r/dom-node %))}))

(rum/defc stats [session]
  (let [visibility-request (query-one :?request session ::todo/visibility-request)
        retract-complete-request (query-one :?request session ::todo/retract-complete-request)
        active-count (query-one :?count session ::todo/active-count)
        completed-count (query-one :?count session ::todo/completed-count)
        visibilities (or (::specs/visibilities visibility-request) #{})
        props-for (fn [name]
                    (cond->
                      {:class (cond
                                (= name (::specs/visibility visibility-request)) "selected"
                                (not (visibilities name)) "inactive")}
                      (visibilities name) (assoc :on-click #(respond-to visibility-request #::specs{:visibility name}))))]
    [:div
     [:span#todo-count
      [:strong active-count] " " (case active-count 1 "item" "items") " left"]
     [:ul#filters
      [:li [:a (props-for :all) "All"]]
      [:li [:a (props-for :active) "Active"]]
      [:li [:a (props-for :completed) "Completed"]]]
     (when retract-complete-request
       [:button#clear-completed {:on-click #(respond-to retract-complete-request)}
        "Clear completed " completed-count])]))

(rum/defcs item < (rum/local false :edit) [state session todo]
  (let [edit (:edit state)
        {::specs/keys [id done title]} todo
        title-request (query-one :?request session ::todo/update-title-request :?todo todo)
        done-request (query-one :?request session ::todo/update-done-request :?todo todo)
        retract-request (query-one :?request session ::todo/retract-todo-request :?todo todo)]
    [:li {:class (str (if done "completed ")
                      (if @edit "editing"))}
     [:div.view
      ;;; Conditionally render controls only if the associated request exists
      (when done-request
        [:input.toggle {:type      "checkbox" :checked done
                        :on-change #(respond-to done-request #::specs{:done (not done)})}])
      [:label (when title-request {:on-double-click #(reset! edit true)}) title]
      (when retract-request
        [:button.destroy {:on-click #(respond-to retract-request)}])]
     (when @edit
       (todo-edit title {:id      id
                           :class   "edit"
                           :on-save (fn [title] (respond-to title-request {::specs/title title}))
                           :on-stop #(reset! edit false)}))]))

(rum/defc header [session]
  [:header#header
   [:h1 "todos"]
   (let [new-todo-request (query-one :?request session ::todo/new-todo-request)]
     (when new-todo-request
       (todo-input "" {:id          "new-todo"
                            :placeholder "What needs to be done?"
                            :on-save     (fn [title] (respond-to new-todo-request {::specs/Todo (todo/new-todo title)}))})))])

(rum/defc complete-all [session]
  (let [complete-all-request (query-one :?request session ::todo/complete-all-request)]
    (when complete-all-request
      (let [complete-all (::specs/done complete-all-request)]
        [:div
         [:input#toggle-all {:type      "checkbox" :checked (not complete-all)
                             :on-change #(respond-to complete-all-request #::specs{:done complete-all})}]
         [:label {:for "toggle-all"} "Mark all as complete"]]))))

(rum/defc app [session]
  (let [todos (query-many :?todo session ::todo/visible-todos)]
    [:div
     [:section#todoapp
      (header session)
      [:div
       [:section#main
        (complete-all session)
        [:ul#todo-list
         (for [todo (sort-by ::specs/created-at todos)]
           (-> (item session todo)
               (rum/with-key (::specs/id todo))))]]
       [:footer#footer
        (stats session)]]]
     [:footer#info
      [:p "Double-click to edit a todo"]]]))

(defn ^:export run [session]
  #_(rum/unmount (js/document.getElementById "app"))
  (rum/mount (app session) (js/document.getElementById "app")))
