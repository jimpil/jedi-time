(ns jedi-time.core-test
  (:require [clojure.test :refer :all]
            [clojure.datafy :as d]
            [jedi-time.core :as jdt]
            [jedi-time.datafied :as jdfd]
            [jedi-time.units :as units])
  (:import (java.time ZonedDateTime OffsetDateTime LocalDateTime LocalDate LocalTime Instant DayOfWeek YearMonth Year Month)))

(defn- strip-meta
  [x]
  (with-meta x {}))

(def chrono-keys
  [:zoned-datetime
   :offset-datetime
   :local-datetime
   :local-date
   :local-time
   :year-month
   :instant])

(deftest datafy-tests

  (doseq [t chrono-keys]

    (let [now      (jdt/now! {:as t})
          datafied (d/datafy now)
          datafied-no-meta (strip-meta datafied)]

      (is (= now
             (jdt/undatafy datafied)
             (jdt/undatafy datafied-no-meta))
          (format "%s doesn't match!" t))
      )))

(deftest shifting-tests
  (testing "datetime roundtrip via :+/:- (all ChronoUnits)"
    (doseq [t [:zoned-datetime :offset-datetime :local-datetime]
            u (keys units/chrono-units)]
      (let [now (jdt/now! {:as t})
            datafied (d/datafy now)
            modified-datafied (jdfd/shift+ datafied [4 u])
            modified-datafied-back (jdfd/shift- modified-datafied [4 u])]
        (is (= datafied modified-datafied-back)
            (format "%s doesn't match!" [t u]))
        (is (= now (jdt/undatafy (strip-meta modified-datafied-back)))
            (format "%s doesn't match after undatafy!" [t u]))))

    ;; do the same for Instant
    (testing "instant roundtrip via :+/:- (supported ChronoUnits)"
      (doseq [u (-> units/chrono-units
                    (dissoc :weeks :months :years :decades  :centuries :millenia)
                    keys)] ;; weeks, months, years etc not supported for Instant
        (let [now (jdt/now! {:as :instant})
              datafied (d/datafy now)
              modified-datafied (jdfd/shift+ datafied [4 u])
              modified-datafied-back (jdfd/shift- modified-datafied [4 u])]
          (is (= datafied modified-datafied-back)
              (format "%s doesn't match!" [:instant u]))
          (is (= now (jdt/undatafy (strip-meta modified-datafied-back)))
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
                  chrono-keys)]

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
        (is (jdfd/before? datafied datafied-later))
        (is (jdfd/after? datafied-later datafied)))))
  )


(deftest julian-fields-tests
  (testing "Julian field access where supported"
    (doseq [t [:zoned-datetime
               :offset-datetime
               :local-datetime
               :local-date]]
      (let [now (jdt/now! {:as t})
            datafied (d/datafy now)]
        (is (pos-int? (d/nav datafied :julian/day nil)))
        (is (pos-int? (d/nav datafied :julian/modified-day nil)))
        (is (pos-int? (d/nav datafied :julian/rata-die nil))))
      )))


(deftest navigation-tests

  (testing "zoned-datetime"
    (let [now (jdt/now! {:as :zoned-datetime})
          datafied (d/datafy now)]
      (is (instance? OffsetDateTime (d/nav datafied :offset-datetime nil)))
      (is (instance? LocalDateTime  (d/nav datafied :local-datetime nil)))
      (is (instance? LocalDate      (d/nav datafied :local-date nil)))
      (is (instance? LocalTime      (d/nav datafied :local-time nil)))
      (is (instance? YearMonth      (d/nav datafied :year-month nil)))
      (is (instance? Year           (d/nav datafied :year nil)))
      (is (instance? Month          (d/nav datafied :month nil)))
      (is (instance? DayOfWeek      (d/nav datafied :weekday nil)))
      (is (instance? Instant        (d/nav datafied :instant nil)))))

  )
