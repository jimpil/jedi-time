(ns jedi-time.datafied.specs.month
  (:require [clojure.spec.alpha :as s])
  (:import (java.time Month)))

(defonce valid-month-names
  (->> (Month/values)
       (map #(.name ^Month %))
       set))


(s/def ::value   #(<= 1 % 12))
(s/def ::length  #(<= 1 % 31))
(s/def ::name    valid-month-names)

(s/def :month-day/value ::length)

(s/def ::month-day
  (s/keys :req-un [:month-day/value]))

(s/def ::month
  (s/keys :req-un [::name ::value ::length]))
