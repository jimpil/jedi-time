(ns jedi-time.core-test
  (:require [clojure.test :refer :all]
            [clojure.datafy :as d]
            [jedi-time.core :as jdt]
            [jedi-time.units :as units]))

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
