(ns jedi-time.datafied.specs.core
  (:require [clojure.spec.alpha :as s]
            [jedi-time.datafied.specs
             [year   :as year]
             [month  :as month]
             [week   :as week]
             [offset :as offset]
             [zone   :as zone]
             [format :as format]]))


(s/def ::month
  (s/keys :req-un [::month/month]))
;----------------------------------

(s/def ::year
  (s/keys :req-un [::year/year]))
;----------------------------------

(s/def ::month-day
  (s/keys :req-un [::month/month-day]))
;-----------------------------------

(s/def ::week-day
  (s/keys :req-un [::week/week-day]))
;-----------------------------------

(s/def ::year-month
  (s/merge ::year ::month (s/keys :opt-un [::format/format])))
;-----------------------------------
(s/def :day/hour       #(<= 0 % 23))
(s/def :hour/minute    #(<= 0 % 59))
(s/def :minute/second  #(<= 0 % 59))
(s/def :second/nano  nat-int?)
(s/def :second/milli nat-int?)
(s/def :second/micro nat-int?)

(s/def ::local-time
  (s/keys :req [:day/hour
                :hour/minute
                :minute/second
                :second/nano
                :second/milli
                :second/micro]
          :opt-un [::format/format]))
;-------------------------------------

(s/def :year/day  ::year/length)
(s/def :year/week #(<= 1 % 53))

(s/def ::local-date
  (s/merge ::month-day
           ::year-month
           ;; week-day, year-week & year-day not needed for `undatafy`
           (s/keys :opt-un [::week/week-day])
           (s/keys :opt [:year/week :year/day])))
;--------------------------------------

(s/def ::local-datetime
  (s/merge ::local-date ::local-time))
;-------------------------------------

(s/def ::offset
  (s/keys :req-un [::offset/offset]
          :opt-un [::same]))

(s/def ::offset-datetime
  (s/merge ::local-datetime ::offset))
;-------------------------------------

(s/def ::zone
  (s/keys :req-un [::zone/zone]
          :opt-un [::same]))

(s/def ::zoned-datetime
  (s/merge ::local-datetime
           ::zone
           ;; offset is not needed for `undatafy`
           (s/keys :opt-un [::offset/offset])))
;--------------------------------------

(s/def :epoch/second nat-int?)

(s/def :epoch/milli nat-int?)
(s/def :epoch/micro nat-int?)
(s/def :epoch/nano  nat-int?)

(s/def ::instant
  (s/keys :req [:second/nano
                :epoch/second]
          :opt [:epoch/milli
                :epoch/micro
                :epoch/nano]
          :opt-un [::format/format
                   ::zone/zone
                   ::offset/offset]))
