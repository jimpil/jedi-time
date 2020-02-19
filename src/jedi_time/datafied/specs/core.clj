(ns jedi-time.datafied.specs.core
  (:require [clojure.spec.alpha :as s])
  (:import (java.time DateTimeException ZoneOffset Month ZoneId DayOfWeek)
           (java.time.format DateTimeFormatter)))

(s/def ::default
  #{nil :iso :default :format/default})

(s/def ::format
  (s/or :default ::default
        :pattern string?
        :formatter (partial instance? DateTimeFormatter)))

(defonce valid-month?
  (->> (Month/values)
       (map #(.name ^Month %))
       set))

(s/def :month/value   #(<= 1 % 12))
(s/def :month/length  #(<= 1 % 31))
(s/def :month/name    valid-month?)
(s/def :month/day    :month/length)

(s/def ::month
  (s/keys :req [:month/value]
          :opt [:month/name :month/length]))
;----------------------------------

(s/def :year/value   pos-int?)
(s/def :year/length  #(<= 1 % 366))
(s/def :year/leap?  boolean?)

(s/def ::year
  (s/keys :req [:year/value]
          :opt [:year/length :year/leap?]))
;----------------------------------

(s/def ::month-day
  (s/merge ::month (s/keys :req [:month/day])))
;-----------------------------------

(defonce valid-day?
  (->> (DayOfWeek/values)
       (map #(.name ^DayOfWeek %))
       set))

(s/def :day/name  valid-day?)
(s/def :day/value #(<= 1 % 7))

(s/def ::week-day
  (s/keys :req [:day/name :day/value]))
;-----------------------------------

(s/def ::year-month
  (s/keys :req-un [::year ::month]
          :opt-un [::format]))
;-----------------------------------
(s/def :day/hour       #(<= 0 % 23))
(s/def :hour/minute    #(<= 0 % 59))
(s/def :minute/second  #(<= 0 % 59))
(s/def :second/nano    nat-int?)
(s/def :second/milli   nat-int?)
(s/def :second/micro   nat-int?)

(s/def ::local-time
  (s/keys :req [:day/hour
                :hour/minute
                :minute/second
                :second/nano]
          :opt [:second/micro
                :second/milli]
          :opt-un [::format]))
;-------------------------------------

(s/def :year/day  :year/length)
(s/def :year/week #(<= 1 % 53))

(s/def :enriched/month ::month-day)
(s/def :enriched/year
  (s/merge ::year (s/keys :opt [:year/day :year/week])))

(s/def ::local-date
  (s/keys :req-un [:enriched/year :enriched/month]
          :opt-un [::week-day]))
;--------------------------------------

(s/def ::local-datetime
  (s/keys :req-un [::local-date ::local-time]))
;-------------------------------------

(defn- valid-offset?
  [^String offset-id]
  (try (ZoneOffset/of offset-id)
       true
       (catch DateTimeException _ false)))

(s/def ::id      valid-offset?)
(s/def ::seconds nat-int?)
(s/def ::hours   (some-fn pos-int? zero?))

(s/def ::same #{:instant :local})

(s/def ::offset
  (s/keys :req [:offset/id]
          :opt-un [:offset/seconds
                   :offset/hours
                   ::same]))

(s/def ::offset-datetime
  (s/merge ::local-datetime
           (s/keys :req-un [::offset])))
;-------------------------------------

(defonce valid-zone?
  (set (ZoneId/getAvailableZoneIds)))

(s/def :zone/id valid-zone?)

(s/def ::zone
  (s/keys :req [:zone/id]
          :opt-un [::same]))

(s/def ::zoned-datetime
  (s/merge ::local-datetime
           (s/keys :req-un [::zone])
           ;; offset is not needed for `undatafy`
           (s/keys :opt-un [::offset])))
;--------------------------------------

(s/def :epoch/second nat-int?)
(s/def :epoch/milli  nat-int?)
(s/def :epoch/micro  nat-int?)
(s/def :epoch/nano   nat-int?)

(s/def ::instant
  (s/keys :req [:second/nano
                :epoch/second]
          :opt [:epoch/milli
                :epoch/micro
                :epoch/nano]
          :opt-un [::format
                   ::zone
                   ::offset]))
