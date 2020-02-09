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
    ^int (get-in x [:month :day])
    ^int (get x :hour)
    ^int (get x :minute)
    ^int (get x :second)
    ^int (get x :second-nano)))

(defn local-date-of
  ^LocalDate [x]
  (LocalDate/of
    ^int (get-in x [:year :value])
    ^int (get-in x [:month :value])
    ^int (get-in x [:month :day])))

(defn local-time-of
  ^LocalTime [x]
  (LocalTime/of
    (get x :hour)
    (get x :minute)
    (get x :second)
    (get x :second-nano)))

(defn year-month-of
  ^YearMonth [x]
  (YearMonth/of
    ^int (get-in x [:year :value])
    ^int (get-in x [:month :value])))
