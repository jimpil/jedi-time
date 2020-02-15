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

(defn local-datetime-of
  ^LocalDateTime [x]
  (LocalDateTime/of
    ^int (get-in x [:year :value])
    ^int (get-in x [:month :value])
    ^int (get-in x [:month-day :value])
    ^int (get x :day/hour)
    ^int (get x :hour/minute)
    ^int (get x :minute/second)
    ^int (get x :second/nano)))

(defn local-date-of
  ^LocalDate [x]
  (LocalDate/of
    ^int (get-in x [:year :value])
    ^int (get-in x [:month :value])
    ^int (get-in x [:month-day :value])))

(defn local-time-of
  ^LocalTime [x]
  (LocalTime/of
    ^int (get x :day/hour)
    ^int (get x :hour/minute)
    ^int (get x :minute/second)
    ^int (get x :second/nano)))

(defn year-month-of
  ^YearMonth [x]
  (YearMonth/of
    ^int (get-in x [:year :value])
    ^int (get-in x [:month :value])))

(defn dissoc-optional
  [k m]
  (case k
    :month (update m k dissoc :length :name)
    :year  (update m k dissoc :length :leap?)
    :week-day (update m k dissoc :name)
    :year-month (->> m
                    (dissoc-optional :month)
                    (dissoc-optional :year))
    :local-time (dissoc m :second/milli :second/micro)
    :local-date (dissoc m :year/week :year/day)
    :local-datetime (->> m
                        (dissoc-optional :local-time)
                        (dissoc-optional :local-date))
    :offset-datetime (update (dissoc-optional :local-datetime m)
                             dissoc :seconds :hours)
    :zoned-datetime (dissoc (dissoc-optional :local-datetime m) :offset)
    :instant  (dissoc m :epoch/milli :epoch/micro :epoch/nano)
    m))
