(ns jedi-time.datafied.specs.week
  (:require [clojure.spec.alpha :as s])
  (:import (java.time DayOfWeek)))

(defonce valid-day-names
  (->> (DayOfWeek/values)
       (map #(.name ^DayOfWeek %))
       set))

(s/def ::name  valid-day-names)
(s/def ::value #(<= 1 % 7))

(s/def ::week-day
  (s/keys :req-un [::value]
          ;; weekday-name not needed for `undatafy`
          :opt-un [::name]))
