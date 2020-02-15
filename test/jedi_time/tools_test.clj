(ns jedi-time.tools-test
  (:require [clojure.test :refer :all]
            [jedi-time.core :as jdt]
            [jedi-time.datafied.tools :as tools]
            [clojure.datafy :as d]
            [jedi-time.units :as units])
  (:import (java.time ZonedDateTime ZoneId OffsetDateTime ZoneOffset)))

(defn- strip-meta
  [x]
  (with-meta x {}))

(deftest comparing-tests
  (testing "Chrono comparison - date + time"
    (doseq [t [:zoned-datetime
               :offset-datetime
               :local-datetime
               :local-time
               :instant]]
      (let [now (jdt/now! {:as t})
            datafied (d/datafy now)
            now-later (jdt/now! {:as t})
            datafied-later (d/datafy now-later)]
        (is (tools/before? datafied datafied-later))
        (is (tools/after? datafied-later datafied)))))

  (testing "Chrono comparison - year + month"
    (doseq [t [:year :year-month]]
      (let [now (jdt/now! {:as t})
            datafied (d/datafy now)
            now-later (jdt/now! {:as t})
            datafied-later (d/datafy now-later)]
        (is (not (tools/before? datafied datafied-later)))
        (is (not (tools/after? datafied-later datafied)))))
    )
  )


(deftest shifting-tests
  (testing "datetime roundtrip via :+/:- (all ChronoUnits)"
    (doseq [t [:zoned-datetime :offset-datetime :local-datetime]
            u (keys units/chrono-units)]
      (let [now (jdt/now! {:as t})
            datafied (d/datafy now)
            modified-datafied (tools/shift+ datafied [4 u])
            modified-datafied-back (tools/shift- modified-datafied [4 u])]
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
              modified-datafied (tools/shift+ datafied [4 u])
              modified-datafied-back (tools/shift- modified-datafied [4 u])]
          (is (= datafied modified-datafied-back)
              (format "%s doesn't match!" [:instant u]))
          (is (= now (jdt/undatafy (strip-meta modified-datafied-back)))
              (format "%s doesn't match after undatafy!" [:instant u])))))
    ))


(deftest translating-tests
  (testing "zone translation"
    (let [^ZonedDateTime now (jdt/now! {:as :zoned-datetime
                                        :zone "UTC"})
          zone (-> now d/datafy (d/nav :zone nil))
          ^ZonedDateTime now-athens (-> now
                                        d/datafy
                                        (d/nav :zone {:id "Europe/Athens"
                                                      :same :instant}))]
      (is (instance? ZonedDateTime now-athens))
      (is (instance? ZoneId zone))
      (is (not= now now-athens))
      (is (= (.toInstant now)
             (.toInstant now-athens)))
      ))

  (testing "offset translation"
    (let [^OffsetDateTime now (jdt/now! {:as :offset-datetime
                                         :zone "UTC"})
          offset (-> now d/datafy (d/nav :offset nil))
          ^OffsetDateTime now-plus2 (-> now
                                        d/datafy
                                        (d/nav :offset {:id "+02:00"
                                                        :same :instant}))]
      (is (instance? OffsetDateTime now-plus2))
      (is (instance? ZoneOffset offset))
      (is (not= now now-plus2))
      (is (= (.toInstant now)
             (.toInstant now-plus2)))
      ))

  )
