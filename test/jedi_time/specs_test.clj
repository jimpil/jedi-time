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
  )
