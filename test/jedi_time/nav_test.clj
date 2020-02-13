(ns jedi-time.nav-test
  (:require [clojure.test :refer :all]
            [jedi-time.core :as jdt]
            [clojure.datafy :as d])
  (:import (java.time ZonedDateTime
                      OffsetDateTime
                      LocalDateTime
                      LocalDate
                      LocalTime
                      Instant
                      DayOfWeek
                      YearMonth
                      Year
                      Month)))

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
        (is (pos-int? (d/nav datafied :julian/rata-die nil)))))))


(deftest navigation-tests

  (testing "graph traversal"
    (let [now (jdt/now! {:as :zoned-datetime})
          datafied (d/datafy now)]
      (is (instance? OffsetDateTime (d/nav datafied :offset-datetime nil)))
      (is (instance? LocalDateTime  (d/nav datafied :local-datetime nil)))
      (is (instance? LocalDate      (d/nav datafied :local-date nil)))
      (is (instance? LocalTime      (d/nav datafied :local-time nil)))
      (is (instance? YearMonth      (d/nav datafied :year-month nil)))
      (is (instance? Year           (d/nav datafied :year nil)))
      (is (instance? Month          (d/nav datafied :month nil)))
      (is (instance? DayOfWeek      (d/nav datafied :week-day nil)))
      (is (instance? Instant        (d/nav datafied :instant nil)))))

  )
