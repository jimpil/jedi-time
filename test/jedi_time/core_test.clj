(ns jedi-time.core-test
  (:require [clojure.test :refer :all]
            [jedi-time.core :as jdt]))

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

    (let [now      (jdt/now! :as t)
          datafied (jdt/datafy now)
          datafied-no-meta (strip-meta datafied)]

      (is (= now
             (jdt/undatafy datafied)
             (jdt/undatafy datafied-no-meta))
          (format "%s doesn't match!" t))
      )))

(deftest nav-tests
  (testing "roundtrip via :+/:-"
      (doseq [t [:zoned-datetime
                 :offset-datetime
                 :local-datetime
                 :local-date]]
        (let [now (jdt/now! :as t)
              datafied (jdt/datafy now)
              modified (jdt/nav datafied :+ [1 :weeks])
              modified-datafied (jdt/datafy modified)
              modified-datafied-back (jdt/nav modified-datafied :- [1 :weeks])
              modified-datafied-back-datafied (jdt/datafy modified-datafied-back)]
          (is (= datafied modified-datafied-back-datafied)
              (format "%s doesn't match!" t))
          (is (= now (jdt/undatafy (strip-meta modified-datafied-back-datafied)))
              (format "%s doesn't match!" t))))

      ))

(deftest undatafy-tests
  (testing "undatafy slow path"
    (let [month {:month {:name "MARCH"
                         :value 3
                         :length 31}}
          dates (map #(strip-meta (jdt/datafy (jdt/now! :as %))) test-keys)]

      (doseq [d (cons month dates)]
        (is (= d (jdt/redatafy d))
            (format "%s doesn't match after redatafy!" d)))
      )))
