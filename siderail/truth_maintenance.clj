(require '[clojure.spec.alpha :as s]
         '[provisdom.maali.rules :refer [create-session def-derive] :as rules]
         '[clojure.pprint :refer [pprint]])


;;; Attribute specs
(s/def ::location string?)
(s/def ::temperature number?)
(s/def ::high ::temperature)
(s/def ::low ::temperature)

;;; Fact-type specs
(s/def ::Temperature (s/keys :req [::temperature ::location]))
(s/def ::LocalTemperatureRecords (s/keys :req [::high ::low ::location]))
(s/def ::Cold (s/keys :req [::temperature]))
(s/def ::AlwaysOverZeroLocation (s/keys :req [::location]))

(def rules
  {::insert-temperature-records! {:query '[:find (min ?temp) (max ?temp) ?loc
                                           :where
                                           [?e ::location ?loc]
                                           [?e ::temperature ?temp]]
                                  :rhs-fn (fn [min-temp max-temp loc]
                                            [{:type      ::LocalTemperatureRecords
                                              ::location loc
                                              ::high     max-temp
                                              ::low      min-temp}])}
   ::always-over-zero! {:query '[:find ?loc
                                 :where
                                 [?e ::location ?loc]
                                 [?e ::low ?low]
                                 [(> ?low 0)]]
                        :rhs-fn (fn [loc]
                                  [{::always-over-zero true
                                    ::location loc}])}
   ::insert-cold-temperature! {:query '[:find ?temperature
                                        :where
                                        [?e ::temperature ?temperature]
                                        [(< ?temperature 30)]]
                               :rhs-fn (fn [temp]
                                         [{::cold-temperature temp}])}})
(def queries
  {::cold-facts             '[:find ?temperature
                              :where
                              [?e ::cold-temperature ?temperature]]
   ::records-facts          '[:find ?high ?low ?loc
                              :where
                              [?e ::high ?high]
                              [?e ::low ?low]
                              [?e ::location ?loc]]
   ::always-over-zero-facts '[:find ?loc
                              :where
                              [?e ::always-over-zero true]
                              [?e ::location ?loc]]})

(def initial-session (create-session {::location {:db/unique :db.unique/identity}
                                      ::temperature {:db/cardinality :db.cardinality/many}}
                                     rules))

(defn run-examples []
  (let [initial-session (-> initial-session
                            (rules/transact
                              (map (partial merge {:type ::Temperature})
                                   [{::temperature -10 ::location "MCI"}
                                    {::temperature 110 ::location "MCI"}
                                    {::temperature 20 ::location "LHR"}
                                    {::temperature 90 ::location "LHR"}])))]

    (println initial-session)

    (println "Initial cold temperatures: ")
    (pprint (rules/query initial-session (queries ::cold-facts)))
    (newline)

    (println "Initial local temperature records: ")
    (pprint (rules/query initial-session (queries ::records-facts)))
    (newline)

    (println "Initial locations that have never been below 0: ")
    (pprint (rules/query initial-session (queries ::always-over-zero-facts)))
    (newline)

    (println "Now add a temperature of -5 to LHR and a temperature of 115 to MCI")

    (let [with-mods-session (-> initial-session
                                (rules/transact
                                  (map (partial merge {:type ::Temperature})
                                       [{::temperature -5 ::location "LHR"}
                                        {::temperature 115 ::location "MCI"}])))]
      (println "New cold temperatures: ")
      (pprint (rules/query with-mods-session (queries ::cold-facts)))
      (newline)

      (println "New local temperature records: ")
      (pprint (rules/query with-mods-session (queries ::records-facts)))
      (newline)

      (println "New locations that have never been below 0: ")
      (pprint (rules/query with-mods-session (queries ::always-over-zero-facts)))

      (let [with-retracted-session (-> with-mods-session
                                       (rules/transact (let [e (-> with-mods-session
                                                                   (rules/query '[:find ?e
                                                                                  :where
                                                                                  [?e ::location "LHR"]])
                                                                   ffirst)]
                                                         [[:db/retract e ::temperature -5]])))]

        (newline)
        (println "Now we retract the temperature of -5 at LHR")
        (println "Cold temperatures with this retraction: ")
        (pprint (rules/query with-retracted-session (queries ::cold-facts)))
        (newline)

        (println "Local temperature records with this retraction: ")
        (pprint (rules/query with-retracted-session (queries ::records-facts)))
        (newline)

        (println "Locations that have never been below zero with this retraction: ")
        (pprint (rules/query with-retracted-session (queries ::always-over-zero-facts)))))))