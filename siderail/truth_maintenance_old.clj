(require '[clojure.spec.alpha :as s]
         '[provisdom.maali.rules :refer [defrules create-session def-derive] :as rules]
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

(defrules rules
  [::insert-temperature-records!
   [?min-temp <- (acc/min ::temperature) :from [::Temperature (= ?loc location)]]
   [?max-temp <- (acc/max ::temperature) :from [::Temperature (= ?loc location)]]
   =>
   (rules/insert! ::LocalTemperatureRecords {::high ?max-temp ::low ?min-temp ::location ?loc})]

  [::always-over-zero!
   [::LocalTemperatureRecords (> low 0) (= ?loc location)]
   =>
   (rules/insert! ::AlwaysOverZeroLocation {::location ?loc})]

  ;; When a Temperature fact is inserted or retracted, the output of insert-temperature-records will
  ;; be adjusted to compensate, and the output of this rule will in turn be adjusted to compensate for the
  ;; change in the LocalTemperatureRecords facts in the session.
  [::insert-cold-temperature!
   [::Temperature (= ?temperature temperature) (< temperature 30)]
   =>
   (rules/insert! ::Cold {::temperature ?temperature})])


(defqueries queries
            [::cold-facts
             "Query for Cold facts"
             []
             [::Cold (= ?temperature temperature)]]

            [::records-facts
             "Query for LocalTemperatureRecord facts"
             []
             [::LocalTemperatureRecords (= ?high high) (= ?low low) (= ?loc location)]]

            [::always-over-zero-facts
             "Query for AlwaysOverZeroLocation facts"
             []
             [::AlwaysOverZeroLocation (= ?loc location)]])

(defsession initial-session [provisdom.examples.truth-maintenance/rules
                             provisdom.examples.truth-maintenance/queries])

(defn run-examples []
  (let [initial-session (-> initial-session
                            (rules/insert ::Temperature
                                          {::temperature -10 ::location "MCI"}
                                          {::temperature 110 ::location "MCI"}
                                          {::temperature 20 ::location "LHR"}
                                          {::temperature 90 ::location "LHR"})
                            rules/fire-rules)]

    (println "Initial cold temperatures: ")
    (pprint (rules/query initial-session ::cold-facts))
    (newline)

    (println "Initial local temperature records: ")
    (pprint (rules/query initial-session ::records-facts))
    (newline)

    (println "Initial locations that have never been below 0: ")
    (pprint (rules/query initial-session ::always-over-zero-facts))
    (newline)

    (println "Now add a temperature of -5 to LHR and a temperature of 115 to MCI")

    (let [with-mods-session (-> initial-session
                                (rules/insert ::Temperature
                                              {::temperature -5 ::location "LHR"}
                                              {::temperature 115 ::location "MCI"})
                                rules/fire-rules)]
      (println "New cold temperatures: ")
      (pprint (rules/query with-mods-session ::cold-facts))
      (newline)

      (println "New local temperature records: ")
      (pprint (rules/query with-mods-session ::records-facts))
      (newline)

      (println "New locations that have never been below 0: ")
      (pprint (rules/query with-mods-session ::always-over-zero-facts))

      (let [with-retracted-session (-> with-mods-session
                                       (rules/retract ::Temperature {::temperature -5 ::location "LHR"})
                                       rules/fire-rules)]

        (newline)
        (println "Now we retract the temperature of -5 at LHR")
        (println "Cold temperatures with this retraction: ")
        (pprint (rules/query with-retracted-session ::cold-facts))
        (newline)

        (println "Local temperature records with this retraction: ")
        (pprint (rules/query with-retracted-session ::records-facts))
        (newline)

        (println "Locations that have never been below zero with this retraction: ")
        (pprint (rules/query with-retracted-session ::always-over-zero-facts))))))