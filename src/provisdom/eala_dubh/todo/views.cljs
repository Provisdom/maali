(ns provisdom.eala-dubh.todo.views
  (:require [provisdom.eala-dubh.session :as session]
            [provisdom.eala-dubh.todo.queries :as todo]
            [provisdom.eala-dubh.todo.facts :as facts]
            [provisdom.eala-dubh.rules :as rules]))

(defn then [_])
(defn subscribe [_])

;;; TODO- this seems weird mish-mash
(defn update-or-create
  [old-todo session title]
  (if old-todo
    (assoc old-todo :title title :edit false)
    (let [id (inc (-> (session/query session ::todo/total) first :?total ::facts/count))]
      (println "ID" id)
      (rules/spec-type #::facts{:id id :title title :edit false :done false} ::facts/Todo))))

(defn update-todo
  [session id title]
  (-> session
      (session/upsert (partial todo/find-todo id) update-or-create session title)
      (session/fire-rules!)))

(defn input [props]
  [:input (merge {:type "text" :auto-focus true} props)])

(defn todo-item [session {::facts/keys [id done edit title] :as todo}]
  [:li {:class (str (when done "completed ") (when edit "editing"))}
   [:div.view {:key id}
    [:input.toggle
     {:type     "checkbox"
      :checked  (if done true false)
      :onchange #(-> session
                     (session/upsert (partial todo/find-todo id) assoc ::facts/done (not done))
                     (session/fire-rules!))}]
    [:label
     {:ondblclick #(-> session
                        (session/upsert (partial todo/find-todo id) assoc ::facts/edit title)
                        (session/fire-rules!))}

     title]
    [:button.destroy
     {:onclick #(-> session
                    (session/upsert (partial todo/find-todo id) nil)
                    (session/fire-rules!))}]]
   [input
    {:class     "edit"
     :value     edit
     :onkeydown #(let [key (.-which %)]
                   (when (= 13 key)
                     (update-todo session id (-> % .-target .-value))))
     :onblur    #(when edit (update-todo session id (-> % .-target .-value)))}]])


(defn task-list [session visible-todos all-complete?]
  [:section#main
   [:input#toggle-all
    {:type      "checkbox"
     :checked   (not all-complete?)
     :on-change #(then [:transient :mark-all-done true])}]
   [:label
    {:for "toggle-all"}
    "Mark all as complete"]
   [:ul#todo-list
    (for [todo visible-todos]
      [todo-item session todo])]])


(defn footer [session active-count done-count visibility-filter]
  (let [a-fn (fn [filter-kw txt]
               [:a {:class (when (= filter-kw visibility-filter) "selected")
                    :href  (str "#/" (name filter-kw))}
                txt])]
    [:footer#footer
     [:span#todo-count
      [:strong active-count] " " (case active-count 1 "item" "items") " left"]
     [:ul#filters
      [:li (a-fn :all "All")]
      [:li (a-fn :active "Active")]
      [:li (a-fn :done "Completed")]]
     (when (pos? done-count)
       [:button#clear-completed {:on-click #(then [:transient :clear-completed true])}
        "Clear completed"])]))

(defn task-entry [session]
  (println session)
  (let [{:keys [db/id entry/title]} (subscribe [:task-entry])]
    [:header#header
     [:h1 "todos"]
     [input
      {:id          "new-todo"
       :placeholder "What needs to be done?"
       :value       title
       :onkeydown   #(let [key (.-which %)]
                       (when (= 13 key)
                         (update-todo session :new (-> % .-target .-value))))}]]))

(defn app [session]
  (println "******")
  [:div
   [:section#todoapp
    [:div#task-entry [task-entry session]]
    [:div#task-list [task-list session]]
    [:div#footer [footer session]]]
   [:footer#info
    [:p "Double-click to edit a todo"]]])


