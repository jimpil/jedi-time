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
     :else (. ~klass now))  ;; e.g. YearMonth/now
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

(defn safe-plus
  ^Temporal [mode ^Temporal t unit ^long n]
  (when-let [^TemporalUnit u  (units/chrono-units unit)]
    (when (.isSupported t u)
      (.plus t (period-or-duration mode n unit u)))))

(defn safe-minus
  ^Temporal [mode ^Temporal t unit ^long n]
  (when-let [^TemporalUnit u  (units/chrono-units unit)]
    (when (.isSupported t u)
      (.minus t (period-or-duration mode n unit u)))))

(defn local-datetime-of
  ^LocalDateTime [x]
  (LocalDateTime/of
    ^int (get-in x [:year :value])
    ^int (get-in x [:year :month :value])
    ^int (get-in x [:year :month :day])
    ^int (get-in x [:day :hour])
    ^int (get-in x [:hour :minute])
    ^int (get-in x [:minute :second])
    ^int (get-in x [:second :nano])))

(defn local-date-of
  ^LocalDate [x]
  (LocalDate/of
    ^int (get-in x [:year :value])
    ^int (get-in x [:year :month :value])
    ^int (get-in x [:year :month :day])))

(defn local-time-of
  ^LocalTime [x]
  (LocalTime/of
    (get-in x [:day :hour])
    (get-in x [:hour :minute])
    (get-in x [:minute :second])
    (get-in x [:second :nano])))

(defn year-month-of
  ^YearMonth [x]
  (YearMonth/of
    ^int (get-in x [:year :value])
    ^int (get-in x [:year :month :value])))
