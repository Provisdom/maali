(ns provisdom.eala-dubh.todo.view
  (:require [reagent.core :as r]
            [cljs.core.match :refer-macros [match]]
            [provisdom.eala-dubh.todo.rules :as todo]))

(defonce todos (r/atom (sorted-map)))
(defonce filt (r/atom :all))
(defonce active-count (r/atom nil))
(defonce completed-count (r/atom nil))

(defn update-view
  [commands]
  (doseq [command commands]
    (match command
           [:render :todo-list todo-list] (reset! todos todo-list)
           [:render :visibility vis] (reset! filt vis)
           [:render :active-count count] (reset! active-count count)
           [:render :completed-count count] (reset! completed-count count)
           :else nil)))

(defn toggle [id] (swap! todos update-in [id :done] not))
(defn save [id title] (swap! todos assoc-in [id :title] title))
(defn delete [id] (swap! todos dissoc id))

(defn mmap [m f a] (->> m (f a) (into (empty m))))
(defn complete-all [v] (swap! todos mmap map #(assoc-in % [1 :done] v)))
(defn clear-done [] (swap! todos mmap remove #(get-in % [1 :done])))

(defn todo-input [{:keys [title on-save on-stop]}]
  (let [val (r/atom title)
        stop #(do (reset! val "")
                  (if on-stop (on-stop)))
        save #(let [v (-> @val str clojure.string/trim)]
                (if-not (empty? v) (on-save v))
                (stop))]
    (fn [{:keys [id class placeholder]}]
      [:input {:type "text" :value @val
               :id id :class class :placeholder placeholder
               :on-blur save
               :on-change #(reset! val (-> % .-target .-value))
               :on-key-down #(case (.-which %)
                               13 (save)
                               27 (stop)
                               nil)}])))

(def todo-edit (with-meta todo-input
                          {:component-did-mount #(.focus (r/dom-node %))}))

(defn todo-stats [{:keys [filt active done]}]
  (let [props-for (fn [name]
                    {:class (if (= name filt) "selected")
                     :on-click #(reset! filt name)})]
    [:div
     [:span#todo-count
      [:strong active] " " (case active 1 "item" "items") " left"]
     [:ul#filters
      [:li [:a (props-for :all) "All"]]
      [:li [:a (props-for :active) "Active"]]
      [:li [:a (props-for :done) "Completed"]]]
     (when (pos? done)
       [:button#clear-completed {:on-click clear-done}
        "Clear completed " done])]))

(defn todo-item []
  (let [editing (r/atom false)]
    (fn [{::todo/keys [id done title]}]
      [:li {:class (str (if done "completed ")
                        (if @editing "editing"))}
       [:div.view
        [:input.toggle {:type "checkbox" :checked done
                        :on-change #(toggle id)}]
        [:label {:on-double-click #(reset! editing true)} title]
        [:button.destroy {:on-click #(delete id)}]]
       (when @editing
         [todo-edit {:class "edit" :title title
                     :on-save #(save id %)
                     :on-stop #(reset! editing false)}])])))

(defn todo-app [props]
  (let []
    (fn []
      [:div
       [:section#todoapp
        [:header#header
         [:h1 "todos"]
         [todo-input {:id          "new-todo"
                      :placeholder "What needs to be done?"
                      :on-save     add-todo}]]
        (when (-> @todos count pos?)
          [:div
           [:section#main
            [:input#toggle-all {:type      "checkbox" :checked (zero? @active-count)
                                :on-change #(complete-all (pos? @active-count))}]
            [:label {:for "toggle-all"} "Mark all as complete"]
            [:ul#todo-list
             (for [todo (filter (case @filt
                                  :active (complement :done)
                                  :done :done
                                  :all identity) @todos)]
               ^{:key (::todo/id todo)} [todo-item todo])]]
           [:footer#footer
            [todo-stats {:active @active-count :done @completed-count :filt @filt}]]])]
       [:footer#info
        [:p "Double-click to edit a todo"]]])))

(defn ^:export run []
  (r/render [todo-app]
            (js/document.getElementById "app")))