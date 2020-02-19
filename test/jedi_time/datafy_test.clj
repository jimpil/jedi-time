(ns jedi-time.datafy-test
  (:require [clojure.test :refer :all]
            [clojure.datafy :as d]
            [jedi-time.core :as jdt])
  (:import (java.time DayOfWeek Year Month)))

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

  (testing "composite objects"
    (doseq [t chrono-keys]

      (let [now      (jdt/now! {:as t})
            datafied (d/datafy now)
            datafied-no-meta (strip-meta datafied)]

        (is (= now
               (jdt/undatafy datafied)
               (jdt/undatafy datafied-no-meta))
            (format "%s doesn't match!" t))
        )))

  (testing "atom objects"
    (doseq [t [(Year/of 2020)
               (Month/of 5)
               (DayOfWeek/of 2)]]

      (let [datafied (d/datafy t)
            datafied-no-meta (strip-meta datafied)]
        (is (= t
               (jdt/undatafy datafied)
               (jdt/undatafy datafied-no-meta))
            (format "%s doesn't match!" t)))
      )))

(deftest undatafy-tests
  (testing "undatafy slow path"
    (let [month #:month{:name "MARCH"
                        :value 3
                        :length 31}
          dates (map
                  #(-> (jdt/now! {:as %})
                       d/datafy
                       strip-meta)
                  chrono-keys)]

      (doseq [d (cons month dates)]
        (is (= d (jdt/redatafy d))
            (format "%s doesn't match after redatafy!" d)))
      )))

