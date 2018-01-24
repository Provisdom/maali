(ns provisdom.todo.view
  (:require [cljs.core.async :as async :refer [put!]]
            [cljs.spec.alpha :as s]
            [reagent.core :as r]
            [lambdaisland.uniontypes :refer-macros [case-of]]
            [provisdom.todo.specs :as specs]
            [provisdom.todo.rules :as todo]
            [provisdom.maali.pprint :refer-macros [pprint]]))

(enable-console-print!)

(defonce view-state (r/atom {}))
(defonce intent-ch (async/chan))

(defn update-view
  [query-results]
  (println "QUERY RESULTS **********")
  (pprint query-results)
  (println "************")
  (swap! view-state merge query-results)
  query-results)

(defn respond-to
  ([request] (respond-to request {}))
  ([request response]
   (let [response-fn (::specs/response-fn request)]
     (response-fn (merge {::specs/Request request} response)))))

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

(defn todo-stats [active-count completed-count visibility-request retract-complete-request]
  (let [visibilities (or (::specs/visibilities visibility-request) #{})
        props-for (fn [name]
                    (cond->
                      {:class    (cond
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

(defn todo-item []
  (let [edit (r/atom false)]
    (fn [{::specs/keys [id done title]} new-todo-request title-request done-request retract-request]
      [:li {:class (str (if done "completed ")
                        (if @edit "editing"))}
       [:div.view
        [:input.toggle {:type      "checkbox" :checked done
                        :on-change #(respond-to done-request #::specs{:done (not done)})}]
        [:label {:on-double-click #(reset! edit true)} title]
        [:button.destroy {:on-click #(respond-to retract-request)}]]
       (when @edit
         [todo-edit {:class   "edit" :title title
                     :on-save (fn [title] (respond-to title-request {::specs/title title}))
                     :on-stop #(reset! edit false)}])])))

(defn todo-app [props]
  (fn []
    (enable-console-print!)
    (let [{::todo/keys [new-todo-request update-title-requests update-done-requests
                        retract-todo-requests complete-all-request retract-complete-request
                        visibility-request active-count completed-count]} @view-state
          title-requests-by-todo (group-by ::specs/Todo update-title-requests)
          done-requests-by-todo (group-by ::specs/Todo update-done-requests)
          retract-requests-by-todo (group-by ::specs/Todo retract-todo-requests)
          todos (set (concat (keys title-requests-by-todo) (keys done-requests-by-todo) (keys retract-requests-by-todo)))]
      (pprint update-done-requests)
      [:div
       [:section#todoapp
        [:header#header
         [:h1 "todos"]
         [todo-input {:id          "new-todo"
                      :placeholder "What needs to be done?"
                      :on-save     (fn [title] (respond-to new-todo-request {::specs/Todo (todo/new-todo title)}))}]]
        [:div
         (when complete-all-request
           (let [complete-all (::specs/done complete-all-request)]
             [:section#main
              [:input#toggle-all {:type      "checkbox" :checked (not complete-all)
                                  :on-change #(respond-to complete-all-request #::specs{:done complete-all})}]
              [:label {:for "toggle-all"} "Mark all as complete"]
              [:ul#todo-list
               (for [todo (sort-by ::specs/id todos)]
                 ^{:key (::specs/id todo)} [todo-item todo new-todo-request (first (title-requests-by-todo todo))
                                            (first (done-requests-by-todo todo)) (first (retract-requests-by-todo todo))])]]))
         [:footer#footer
          [todo-stats active-count completed-count visibility-request retract-complete-request]]]]
       [:footer#info
        [:p "Double-click to edit a todo"]]])))

(defn ^:export run []
  (r/render [todo-app]
            (js/document.getElementById "app")))
