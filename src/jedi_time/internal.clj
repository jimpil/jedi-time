(ns jedi-time.internal
  (:require [jedi-time.units :as units])
  (:import (java.time.format DateTimeFormatter)
           (java.time.temporal Temporal TemporalUnit TemporalAmount)
           (java.time LocalDateTime LocalDate LocalTime YearMonth Period Duration)))

(defmacro now-variant
  [klass clock zone]
  `(cond
     (some? ~clock) (. ~klass now ~clock)
     (some? ~zone) (. ~klass now ~zone)
     :else (. ~klass now))
  )

(defmacro parser-variant
  [klass]
  `(fn* parse*
     ([^CharSequence ~'input]
      (. ~klass parse ~'input))
     ([^CharSequence ~'input ^DateTimeFormatter ~'formatter]
      (. ~klass parse ~'input ~'formatter))))

(defn- period-or-duration
  "Returns the right concrete instance of TemporalAmount
   (Period vs Duration), depending on <mode> (:time vs :date)."
  ^TemporalAmount [mode n unit tu]
  (case mode
    :time (Duration/of n tu)
    :date (case unit
            :half-days (Period/ofDays (int (/ n 2)))
            :days      (Period/ofDays   n)
            :weeks     (Period/ofWeeks  n)
            :months    (Period/ofMonths n)
            :years     (Period/ofYears  n)
            :decades   (Period/ofYears (* n 10))
            :centuries (Period/ofYears (* n 100))
            :millenia  (Period/ofYears (* n 1000))
            (Duration/of n tu))))

(defn plus
  ^Temporal [mode ^Temporal t unit n safe?]
  (when-let [^TemporalUnit u  (units/chrono-units unit)]
    (if safe?
      (when (.isSupported t u)
        (.plus t (period-or-duration mode n unit u)))
      (.plus t (period-or-duration mode n unit u)))))


(defn minus
  ^Temporal [mode ^Temporal t unit n safe?]
  (when-let [^TemporalUnit u  (units/chrono-units unit)]
    (if safe?
      (when (.isSupported t u)
        (.minus t (period-or-duration mode n unit u)))
      (.minus t (period-or-duration mode n unit u)))))

(defn local-date-of
  ^LocalDate [x]
  (let [month (:month x)]
    (LocalDate/of
      ^int (get-in x [:year :year/value])
      ^int (get month :month/value)
      ^int (get month :month/day))))

(defn local-time-of
  ^LocalTime [x]
  (LocalTime/of
    ^int (get x :day/hour)
    ^int (get x :hour/minute)
    ^int (get x :minute/second)
    ^int (get x :second/nano)))

(defn local-datetime-of
  ^LocalDateTime [x]
  (let [{:keys [local-date local-time]} x
        month (:month local-date)]
    (LocalDateTime/of
      ^int (get-in local-date [:year :year/value])
      ^int (:month/value month)
      ^int (:month/day month)
      ^int (:day/hour local-time)
      ^int (:hour/minute local-time)
      ^int (:minute/second local-time)
      ^int (:second/nano local-time))))

(defn year-month-of
  ^YearMonth [x]
  (YearMonth/of
    ^int (get-in x [:year :year/value])
    ^int (get-in x [:month :month/value])))

(defn dissoc-optional
  [m k]
  (case k
    :month (dissoc m :month/length :month/name)
    :year  (dissoc m :year/length :year/leap?)
    :week-day (dissoc m :day/name)
    :year-month (-> m
                    (update :year dissoc-optional :year)
                    (update :month dissoc-optional :month))
    :local-time (update m :local-time dissoc :second/milli :second/micro)
    :local-date (update m :local-date dissoc :year/week :year/day)
    :local-datetime (-> m
                        (update :local-time dissoc-optional :local-time)
                        (update :local-date dissoc-optional :local-date))
    :offset-datetime (-> m
                         (dissoc-optional :local-datetime)
                         (update :offset dissoc :offset/seconds :offset/hours))
    :zoned-datetime (-> m
                        (dissoc-optional :local-datetime)
                        (dissoc :offset))
    :instant  (dissoc m :epoch/milli :epoch/micro :epoch/nano)
    m))
