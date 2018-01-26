;;; Derived from precept todomvc example: https://github.com/CoNarrative/precept/tree/master/examples/todomvc

(ns provisdom.todo.view
  (:require [reagent.core :as r]
            [provisdom.todo.specs :as specs]
            [provisdom.todo.rules :as todo]))

;;; View state should contain the latest map of query results
(defonce view-state (r/atom {}))

;;; Some useful reagent cursors
(def new-todo-request (r/cursor view-state [::todo/new-todo-request]))
(def update-title-requests (r/cursor view-state [::todo/update-title-requests]))
(def update-done-requests (r/cursor view-state [::todo/update-done-requests]))
(def retract-todo-requests (r/cursor view-state [::todo/retract-todo-requests]))
(def complete-all-request (r/cursor view-state [::todo/complete-all-request]))
(def retract-complete-request (r/cursor view-state [::todo/retract-complete-request]))
(def visibility-request (r/cursor view-state [::todo/visibility-request]))
(def active-count (r/cursor view-state [::todo/active-count]))
(def completed-count (r/cursor view-state [::todo/completed-count]))

;;; Wrapper function for updating view state
(defn update-view
  [query-results]
  (swap! view-state merge query-results))

;;; Convenience function to respond to a request
(defn respond-to
  ([request] (respond-to request {}))
  ([request response]
   (let [response-fn (::specs/response-fn request)]
     (response-fn (merge {::specs/Request request} response)))))

;;; View components
(defn todo-input [{:keys [title on-stop]}]
  (let [val (r/atom title)
        stop #(do (reset! val "")
                  (if on-stop (on-stop)))
        save (fn [on-save]
               (let [v (-> @val str clojure.string/trim)]
                 (if-not (empty? v) (on-save v))
                 (stop)))]
    (fn [{:keys [id class placeholder on-save]}]
      [:input {:type        "text" :value @val
               :id          id :class class :placeholder placeholder
               :on-blur     #(save on-save)
               :on-change   #(reset! val (-> % .-target .-value))
               :on-key-down #(case (.-which %)
                               13 (save on-save)
                               27 (stop)
                               nil)}])))

(def todo-edit (with-meta todo-input
                          {:component-did-mount #(.focus (r/dom-node %))}))

(defn stats []
  (let [visibilities (or (::specs/visibilities @visibility-request) #{})
        props-for (fn [name]
                    (cond->
                      {:class (cond
                                (= name (::specs/visibility @visibility-request)) "selected"
                                (not (visibilities name)) "inactive")}
                      (visibilities name) (assoc :on-click #(respond-to @visibility-request #::specs{:visibility name}))))]
    [:div
     [:span#todo-count
      [:strong @active-count] " " (case @active-count 1 "item" "items") " left"]
     [:ul#filters
      [:li [:a (props-for :all) "All"]]
      [:li [:a (props-for :active) "Active"]]
      [:li [:a (props-for :completed) "Completed"]]]
     (when @retract-complete-request
       [:button#clear-completed {:on-click #(respond-to @retract-complete-request)}
        "Clear completed " @completed-count])]))

(defn item []
  (let [edit (r/atom false)]
    (fn [{::specs/keys [done title]} title-request done-request retract-request]
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
         [todo-edit {:class   "edit" :title title
                     :on-save (fn [title] (respond-to title-request {::specs/title title}))
                     :on-stop #(reset! edit false)}])])))

(defn header
  []
  [:header#header
   [:h1 "todos"]
   (when @new-todo-request
     [todo-input {:id          "new-todo"
                  :placeholder "What needs to be done?"
                  :on-save     (fn [title] (respond-to @new-todo-request {::specs/Todo (todo/new-todo title)}))}])])

(defn complete-all
  []
  (when @complete-all-request
    (let [complete-all (::specs/done @complete-all-request)]
      [:div
       [:input#toggle-all {:type      "checkbox" :checked (not complete-all)
                           :on-change #(respond-to @complete-all-request #::specs{:done complete-all})}]
       [:label {:for "toggle-all"} "Mark all as complete"]])))

(defn app [_]
  (fn []
    (let [title-requests-by-todo (group-by ::specs/Todo @update-title-requests)
          done-requests-by-todo (group-by ::specs/Todo @update-done-requests)
          retract-requests-by-todo (group-by ::specs/Todo @retract-todo-requests)
          todos (set (concat (keys title-requests-by-todo) (keys done-requests-by-todo) (keys retract-requests-by-todo)))]
      [:div
       [:section#todoapp
        [header]
        [:div
         [:section#main
          [complete-all]
          [:ul#todo-list
           (for [todo (sort-by ::specs/id todos)]
             ^{:key (::specs/id todo)} [item todo (first (title-requests-by-todo todo))
                                        (first (done-requests-by-todo todo)) (first (retract-requests-by-todo todo))])]]
         [:footer#footer
          [stats]]]]
       [:footer#info
        [:p "Double-click to edit a todo"]]])))

(defn ^:export run []
  (r/render [app]
            (js/document.getElementById "app")))
