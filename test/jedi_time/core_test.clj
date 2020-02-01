(ns jedi-time.core-test
  (:require [clojure.test :refer :all]
            [clojure.datafy :as d]
            [jedi-time.core :as jdt]
            [jedi-time.units :as units])
  (:import (java.time ZonedDateTime OffsetDateTime LocalDateTime LocalDate LocalTime Instant DayOfWeek YearMonth)))

(defn- strip-meta
  [x]
  (with-meta x {}))

(def test-keys
  [:zoned-datetime
   :offset-datetime
   :local-datetime
   :local-date
   :local-time
   :year-month
   :instant])

(deftest datafy-tests

  (doseq [t test-keys]

    (let [now      (jdt/now! {:as t})
          datafied (d/datafy now)
          datafied-no-meta (strip-meta datafied)]

      (is (= now
             (jdt/undatafy datafied)
             (jdt/undatafy datafied-no-meta))
          (format "%s doesn't match!" t))
      )))

(deftest nav-tests
  (testing "datetime roundtrip via :+/:- (all ChronoUnits)"
    (doseq [t [:zoned-datetime :offset-datetime :local-datetime]
            u (keys units/chrono-units)]
      (let [now (jdt/now! {:as t})
            datafied (d/datafy now)
            modified (d/nav datafied :+ [4 u])
            modified-datafied (d/datafy modified)
            modified-datafied-back (d/nav modified-datafied :- [4 u])
            modified-datafied-back-datafied (d/datafy modified-datafied-back)]
        (is (= datafied modified-datafied-back-datafied)
            (format "%s doesn't match!" [t u]))
        (is (= now (jdt/undatafy (strip-meta modified-datafied-back-datafied)))
            (format "%s doesn't match after undatafy!" [t u]))))

    ;; do the same for Instant
    (testing "instant roundtrip via :+/:- (supported ChronoUnits)"
      (doseq [u (-> units/chrono-units
                    (dissoc :weeks :months :years :decades  :centuries :millenia)
                    keys)] ;; weeks, months, years etc not supported for Instant
        (let [now (jdt/now! {:as :instant})
              datafied (d/datafy now)
              modified (d/nav datafied :+ [4 u])
              modified-datafied (d/datafy modified)
              modified-datafied-back (d/nav modified-datafied :- [4 u])
              modified-datafied-back-datafied (d/datafy modified-datafied-back)]
          (is (= datafied modified-datafied-back-datafied)
              (format "%s doesn't match!" [:instant u]))
          (is (= now (jdt/undatafy (strip-meta modified-datafied-back-datafied)))
              (format "%s doesn't match after undatafy!" [:instant u])))))
    ))

(deftest undatafy-tests
  (testing "undatafy slow path"
    (let [month {:month {:name "MARCH"
                         :value 3
                         :length 31}}
          dates (map
                  #(-> (jdt/now! {:as %})
                       d/datafy
                       strip-meta)
                  test-keys)]

      (doseq [d (cons month dates)]
        (is (= d (jdt/redatafy d))
            (format "%s doesn't match after redatafy!" d)))
      )))


(deftest before?-after?-tests
  (testing "Comparison for before/after"
    (doseq [t [:zoned-datetime
               :offset-datetime
               :local-datetime
               :local-time
               :instant]]
      (let [now (jdt/now! {:as t})
            datafied (d/datafy now)
            now-later (jdt/now! {:as t})
            datafied-later (d/datafy now-later)]
        (is (d/nav datafied :before? datafied-later))
        (is (d/nav datafied-later :after? datafied)))))
  )


(deftest julian-fields-tests
  (testing "Julian field access where supported"
    (doseq [t [:zoned-datetime
               :offset-datetime
               :local-datetime
               :local-date]]
      (let [now (jdt/now! {:as t})
            datafied (d/datafy now)]
        (is (pos-int? (d/nav datafied :julian :day)))
        (is (pos-int? (d/nav datafied :julian :modified-day)))
        (is (pos-int? (d/nav datafied :julian :rata-die))))
      )))


(deftest conversion-tests

  (testing "Instant supported conversions"
    (let [now (jdt/now! {:as :instant})
          datafied (d/datafy now)]
      (is (instance? ZonedDateTime  (d/nav datafied :to [:zoned-datetime])))
      (is (instance? OffsetDateTime (d/nav datafied :to [:offset-datetime "Australia/North"])))
      (is (instance? LocalDateTime  (d/nav datafied :to [:local-datetime "America/Jamaica"])))
      (is (instance? LocalDate      (d/nav datafied :to [:local-date])))
      (is (instance? LocalTime      (d/nav datafied :to [:local-time]))))
    )

  (testing "zoned-datetime conversions"
    (let [now (jdt/now! {:as :zoned-datetime})
          datafied (d/datafy now)]
      (is (instance? OffsetDateTime (d/nav datafied :to :offset-datetime)))
      (is (instance? LocalDateTime  (d/nav datafied :to :local-datetime)))
      (is (instance? Instant        (d/nav datafied :to :instant)))))

  (testing "offset-datetime conversions"
    (let [now (jdt/now! {:as :offset-datetime})
          datafied (d/datafy now)]
      (is (instance? LocalDateTime (d/nav datafied :to :local-datetime)))
      (is (instance? Instant       (d/nav datafied :to :instant)))))

  (testing "local-datetime conversions"
    (let [now (jdt/now! {:as :local-datetime})
          datafied (d/datafy now)]
      (is (instance? LocalDate (d/nav datafied :to :local-date)))
      (is (instance? LocalTime (d/nav datafied :to :local-time)))
      (is (instance? Instant   (d/nav datafied :to [:instant :system])))
      (is (instance? Instant   (d/nav datafied :to [:instant]))) ;; same as above
      (is (instance? Instant   (d/nav datafied :to [:instant "+03:00"])))
      ))

  (testing "local-date conversions"
    (let [now (jdt/now! {:as :local-date})
          datafied (d/datafy now)]
      (is (instance? DayOfWeek (d/nav datafied :to :week-day)))
      (is (instance? YearMonth (d/nav datafied :to :year-month)))
      (is (instance? Instant   (d/nav datafied :to [:instant :system])))
      (is (instance? Instant   (d/nav datafied :to [:instant]))) ;; same as above
      (is (instance? Instant   (d/nav datafied :to [:instant "+03:00"])))
      ))

  (testing "year-month conversions"
    (let [now (jdt/now! {:as :year-month})
          datafied (d/datafy now)]
      (is (instance? Instant (d/nav datafied :to [:instant :system])))
      (is (instance? Instant (d/nav datafied :to [:instant]))) ;; same as above
      (is (instance? Instant (d/nav datafied :to [:instant "+03:00"])))
      ))

  )
