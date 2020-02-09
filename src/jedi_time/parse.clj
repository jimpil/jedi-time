(ns jedi-time.parse
  (:require [jedi-time.internal :as internal])
  (:import (java.time LocalDate LocalDateTime ZonedDateTime
                      YearMonth LocalTime OffsetDateTime
                      Instant ZoneId ZoneOffset)
           (java.time.format DateTimeFormatter)))

(defn- parser-for
  "Returns a 2-arity parser-fn. First one accepts a single CharSequence,
   whereas the second one also accepts a DateTimeFormatter."
  [x]
  (case x
    :instant (fn parse*
               ([^CharSequence x]
                (Instant/parse x))
               ([x _]
                (parse* x)))
    :year-month      (internal/parser-variant YearMonth)
    :local-time      (internal/parser-variant LocalTime)
    :local-date      (internal/parser-variant LocalDate)
    :local-datetime  (internal/parser-variant LocalDateTime)
    :zoned-datetime  (internal/parser-variant ZonedDateTime)
    :offset-datetime (internal/parser-variant OffsetDateTime)
    ))


;;Fully type-hinted public API:
;;=============================

(let [parse-fn (parser-for :year-month)]

  (defn parse-year-month
    "Parses the <ym> String into a YearMonth object,
     using either the specified, or the default, <formatter>."
    (^YearMonth [ym]
     (parse-fn ym))
    (^YearMonth [ym formatter]
     (parse-fn ym formatter)))
  )

(let [parse-fn (parser-for :local-time)]

  (defn parse-local-time
    (^LocalTime [t]
     (parse-fn t))
    (^LocalTime [t formatter]
     (parse-fn t formatter)))
  )

(let [parse-fn (parser-for :local-date)]

  (defn parse-local-date
    (^LocalDate [d]
     (parse-fn d))
    (^LocalDate [d formatter]
     (parse-fn d formatter)))
  )

(let [parse-fn (parser-for :local-datetime)]

  (defn parse-local-datetime
    (^LocalDateTime [dt]
     (parse-fn dt))
    (^LocalDateTime [dt formatter]
     (parse-fn dt formatter)))
  )

(let [parse-fn (parser-for :zoned-datetime)]

  (defn parse-zoned-datetime
    (^ZonedDateTime [dt]
     (parse-fn dt))
    (^ZonedDateTime [dt formatter]
     (parse-fn dt formatter)))
  )

(let [parse-fn (parser-for :offset-datetime)]

  (defn parse-offset-datetime
    (^OffsetDateTime [dt]
     (parse-fn dt))
    (^OffsetDateTime [dt formatter]
     (parse-fn dt formatter)))
  )

(defn- fmt-of
  [x]
  (if (string? x)
    (DateTimeFormatter/ofPattern x)
    x))

(defn dt-formatter
  (^DateTimeFormatter [x]
   (DateTimeFormatter/ofPattern x))
  (^DateTimeFormatter [x default]
   (if (or (nil? x)
           (= :format/default x))
     (fmt-of default)
     (fmt-of x))))

(defn zone-id
  ^ZoneId [x]
  (if (or (nil? x)
          (= :system x))
    (ZoneId/systemDefault)
    (if (string? x)
      (ZoneId/of ^String x)
      x)))

(defn zone-offset
  ^ZoneOffset [x ^LocalDateTime ldt]
   (if (or (nil? x)
           (= :system x))
     (-> (ZoneId/systemDefault)
         .getRules
         (.getOffset ldt))
     (if (string? x)
       (ZoneOffset/of ^String x)
       x)))
