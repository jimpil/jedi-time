(ns jedi-time.specs-test
  (:require [clojure.test :refer :all]
            [jedi-time.core :as jdt]
            [jedi-time.datafied.specs.core :as specs]
            [clojure.spec.alpha :as s]
            [clojure.datafy :as d]))

(deftest datafy-ret

  (testing "`datafy` return value satisfies the corresponding spec"
    (doseq [t [:zoned-datetime
               :offset-datetime
               :local-datetime
               :local-time
               :year-month
               :year
               :instant]]
      (let [now (jdt/now! {:as t})
            datafied (d/datafy now)
            spec-key (keyword "jedi-time.datafied.specs.core" (name t))]
        (is (s/valid? spec-key datafied)))))

  (testing "minimal values"
    (let [minimal-inst {:epoch/second 1581866760
                        :second/nano 428017000
                        :zone {:zone/id "Europe/Athens"}}]
      (is (s/valid? ::specs/instant minimal-inst))
      (is (s/valid? ::specs/instant (dissoc minimal-inst :zone))))

    (let [minimal-year {:year/value 2020}]
      (is (s/valid? ::specs/year minimal-year)))

    (let [minimal-month {:month/value 6}]
      (is (s/valid? ::specs/month minimal-month)))

    )
  )
