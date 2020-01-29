(ns jedi-time.units
  (:import (java.time.temporal ChronoUnit)))

(defonce chrono-units
  {:nanos   ChronoUnit/NANOS
   :micros  ChronoUnit/MICROS
   :millis  ChronoUnit/MILLIS
   :seconds ChronoUnit/SECONDS
   :minutes ChronoUnit/MINUTES
   :hours   ChronoUnit/HOURS
   :days    ChronoUnit/DAYS
   :half-days ChronoUnit/HALF_DAYS
   :weeks   ChronoUnit/WEEKS
   :months  ChronoUnit/MONTHS
   :years   ChronoUnit/YEARS
   :decades ChronoUnit/DECADES
   :centuries ChronoUnit/CENTURIES
   :millenia ChronoUnit/MILLENNIA
   :eras     ChronoUnit/ERAS}
  )
