(ns jedi-time.core
  "Navigable datafied versions of the core `java.time` objects."
  (:require [clojure.core.protocols :as p]
            [clojure.datafy :as d]
            [jedi-time
             [internal :as internal]
             [parse    :as parse]])
  (:import (java.time YearMonth Month DayOfWeek Instant
                      LocalTime LocalDate LocalDateTime
                      ZonedDateTime OffsetDateTime ZoneId Clock)
           (java.time.format DateTimeFormatter)
           (java.time.temporal IsoFields Temporal TemporalAccessor JulianFields)
           (java.math RoundingMode)))

(defn ^:dynamic invalid!
  "Controls what happens when a key is not recognised.
   Throws ex-info (by default), but can be dynamically rebound."
  [k]
  (throw (ex-info "Invalid argument!"
                  (if (coll? k) k {:key k}))))

(defn- original-object
  [datafied]  ;; `d/datafy` adds the original object in the metadata
  (-> datafied meta ::d/obj))

(defn- reconstruct-object
  [x]
  (if-let [epoch (:epoch x)]
    (let [nano-of-second (-> x :second :nano)
          second (:second epoch)]
      (Instant/ofEpochSecond second nano-of-second))

    (if-let [zone-id (get-in x [:zone :id])]
      (ZonedDateTime/ofStrict
        (internal/local-datetime-of x)
        (parse/zone-offset (get-in x [:offset :id]))
        (parse/zone-id zone-id))

      (if-let [offset-id (get-in x [:offset :id])]
        (OffsetDateTime/of
          (internal/local-datetime-of x)
          (parse/zone-offset offset-id))

        (if-let [year-day (get-in x [:year :day])]
          (internal/local-datetime-of x)

          (if-let [year-week (get-in x [:year :week])]
            (internal/local-date-of x)

            (if (contains? x :day)
              (internal/local-time-of x)

              (if-let [i (get-in x [:week :day :value])]
                (DayOfWeek/of i)

                (if (contains? x :year)
                  (internal/year-month-of x)

                  (when-let [i (get-in x [:month :value])]
                    (Month/of i)))))))))))

(defn- format*
  ^String [fmt iso-variant ^TemporalAccessor obj]
  (-> fmt
      (parse/dt-formatter iso-variant)
      (.format obj)))

(defn- julian-field*
  [^TemporalAccessor ta x]
  (case x
    :day          (.getLong ta JulianFields/JULIAN_DAY)
    :modified-day (.getLong ta JulianFields/MODIFIED_JULIAN_DAY)
    :rata-die     (.getLong ta JulianFields/RATA_DIE)
    (invalid! x)))

(defn undatafy
  "Looks up the `:clojure.datafy/obj` key in the metadata of <x>.
   If that fails, reconstructs the appropriate temporal object from scratch
   (assuming keys per the result of `datafy`). If that fails, returns/throws
   `(invalid! {:not-datafied x})`."
  ^Temporal [x]
  (when (some? x)
    (or (original-object x)
        (reconstruct-object x)
        (invalid! {:not-datafied x}))))

(def redatafy
  "Composes `datafy` with`undatafy` (via `comp`).
   Useful for re-obtaining `nav` capabilities
   if the metadata was lost (e.g by serialisation)."
  (comp d/datafy undatafy))

(extend-protocol p/Datafiable

  Month
  (datafy [m]
    {:month {:name   (.name m)
             :value  (.getValue m)
             ;; not accounting for leap-year precision on plain Month objects
             :length (.length m false)}})

  DayOfWeek
  (datafy [d]
    {:week {:day {:name  (.name d)
                  :value (.getValue d)}}})

  YearMonth
  (datafy [ym]
    (let [month (-> (d/datafy (.getMonth ym))
                    ;; correct the length here
                    (assoc-in [:month :length] (.lengthOfMonth ym)))
          ret {:year (merge month
                            {:length (.lengthOfYear ym) ;; 365 or 366 depending on `.isLeapYear()`
                             :leap?  (.isLeapYear ym)
                             :value  (.getYear ym)})}]
      (with-meta ret
        {`p/nav
         (fn [_ k v]
           (case k
             :before? (.isBefore ym (undatafy v))
             :after?  (.isAfter  ym (undatafy v))
             :to      (if (= :instant v)
                        (-> (.atDay ym 1)
                            (.atStartOfDay)
                            (.toInstant (parse/zone-offset v)))
                        (invalid! v))
             :format  (format* (or v "yyyy-MM") nil ym)
             :+       (let [[n unit] v] (internal/safe-plus :date ym unit n))
             :-       (let [[n unit] v] (internal/safe-minus :date ym unit n))
             (invalid! k)))})))

  LocalTime
  (datafy [lt]
    (let [nanos (.getNano lt)
          ret {:day    {:hour   (.getHour   lt)}
               :hour   {:minute (.getMinute lt)}
               :minute {:second (.getSecond lt)}
               :second {:nano  nanos
                        :milli (long (/ nanos 1e6))
                        :micro (long (/ nanos 1e3))}}]
      (with-meta ret
        {`p/nav
         (fn [_ k v]
           (case k
             :before? (.isBefore lt (undatafy v))
             :after?  (.isAfter  lt (undatafy v))
             :format  (format* v DateTimeFormatter/ISO_LOCAL_TIME lt)
             :+       (let [[n unit] v] (internal/safe-plus :time  lt unit n))
             :-       (let [[n unit] v] (internal/safe-minus :time lt unit n))
             (invalid! k)))})))

  LocalDate
  (datafy [ld]
    (let [weekday (.getDayOfWeek ld)
          ym      (YearMonth/of (.getYear ld) (.getMonth ld))
          ret     (merge (d/datafy weekday)
                         (-> (d/datafy ym)
                             (assoc-in [:year :week] (.get ld IsoFields/WEEK_OF_WEEK_BASED_YEAR))
                             (assoc-in [:year :month :day] (.getDayOfMonth ld))))]
      (with-meta ret
        {`p/nav
         (fn [_ k v]
           (case k
             :before? (.isBefore ld (undatafy v))
             :after?  (.isAfter  ld (undatafy v))
             :format  (format* v DateTimeFormatter/ISO_LOCAL_DATE ld)
             :julian  (julian-field* ld v)
             :+       (let [[n unit] v] (internal/safe-plus :date  ld unit n))
             :-       (let [[n unit] v] (internal/safe-minus :date ld unit n))
             :to      (case v
                        :instant (-> (.atStartOfDay ld)
                                     (.toInstant (parse/zone-offset v)))
                        :week-day weekday
                        :year-month ym
                        (invalid! v))
             (invalid! k)))})))

  LocalDateTime
  (datafy [ldt]
    (let [lt  (.toLocalTime ldt)
          ld  (.toLocalDate ldt)
          ret (-> (merge-with merge (d/datafy lt) (d/datafy ld))
                  (assoc-in [:year :day] (.getDayOfYear  ldt)))]
      (with-meta ret
        {`p/nav
         (fn [_ k v]
           (case k
             :before?   (.isBefore ldt (undatafy v))
             :after?    (.isAfter  ldt (undatafy v))
             :at-zone   (let [[zid] v]   (.atZone   ldt (parse/zone-id zid)))
             :at-offset (let [[offid] v] (.atOffset ldt (parse/zone-offset offid)))
             :format    (format* v DateTimeFormatter/ISO_LOCAL_DATE_TIME ldt)
             :julian    (julian-field* ldt v)
             :+         (let [[n unit] v] (internal/safe-plus :date  ldt unit n))
             :-         (let [[n unit] v] (internal/safe-minus :date ldt unit n))
             :to        (case v
                          :local-time lt
                          :local-date ld
                          (if (and (sequential? v)
                                   (= :instant (first v)))
                            (.toInstant ldt (parse/zone-offset (second v)))
                            (invalid! v)))
             (invalid! k)))})))

  OffsetDateTime
  (datafy [odt]
    (let [ldt         (.toLocalDateTime odt)
          offset      (.getOffset odt)
          off-seconds (.getTotalSeconds offset)
          ret         (-> (d/datafy ldt)
                          (assoc :offset {:id      (.getId offset)
                                          :seconds off-seconds
                                          :hours   (with-precision 3
                                                     :rounding RoundingMode/HALF_EVEN
                                                     (double (/ off-seconds 60M)))}))]
      (with-meta ret
        {`p/nav
         (fn [_ k v]
           (case k
             :before?   (.isBefore odt (undatafy v))
             :after?    (.isAfter  odt (undatafy v))
             :at-offset (let [[offid mode] v]
                          (case (or mode :same-instant)
                            :same-instant (.withOffsetSameInstant odt (parse/zone-offset offid))
                            :same-local   (.withOffsetSameLocal   odt (parse/zone-offset offid))
                            (invalid! mode)))
             :format    (format* v DateTimeFormatter/ISO_OFFSET_DATE_TIME odt)
             :julian    (julian-field* odt v)
             :+         (let [[n unit] v] (internal/safe-plus :date odt unit n))
             :-         (let [[n unit] v] (internal/safe-minus :date odt unit n))
             :to        (case v
                          :local-datetime ldt
                          :instant (.toInstant odt)
                          (invalid! v))
             (invalid! k)))})))

  ZonedDateTime
  (datafy [zdt]
    (let [odt  (.toOffsetDateTime zdt)
          zone (.getZone zdt)
          ret  (-> (d/datafy odt)
                   (assoc-in [:zone :id] (.getId zone)))]
      (with-meta ret
                 {`p/nav
                  (fn [_ k v]
                    (case k
                      :before? (.isBefore zdt (undatafy v))
                      :after? (.isAfter zdt (undatafy v))
                      :at-zone (let [[zid mode] v]
                                 (case (or mode :same-instant)
                                   :same-instant (.withZoneSameInstant zdt (parse/zone-id zid))
                                   :same-local (.withZoneSameLocal zdt (parse/zone-id zid))
                                   (invalid! mode)))
                      :format (format* v DateTimeFormatter/ISO_ZONED_DATE_TIME zdt)
                      :julian (julian-field* zdt v)
                      :+ (let [[n unit] v] (internal/safe-plus :date zdt unit n))
                      :- (let [[n unit] v] (internal/safe-minus :date zdt unit n))
                      :to (case v
                            :offset-datetime odt
                            :local-datetime (.toLocalDateTime zdt)
                            :instant (.toInstant odt)
                            (invalid! v))
                      (invalid! k)))})))

  Instant
  ;; a count of nanoseconds since the epoch of the first moment of 1970 in UTC
  (datafy [inst]
    (let [epoch-second (.getEpochSecond inst) ;; total seconds *until* <inst>
          nanos        (.getNano inst)        ;; total nanoseconds within current second
          epoch-nano   (-> epoch-second (* 1e9) long (+ nanos)) ;; total nanoseconds *until* <inst>
          epoch-milli  (long (/ epoch-nano 1e6))
          epoch-micro  (long (/ epoch-nano 1e3))
          ret  {:second {:nano nanos}
                :epoch {:second epoch-second
                        :milli  epoch-milli
                        :micro  epoch-micro
                        :nano   epoch-nano}}]
      (with-meta ret
        {`p/nav
         (fn [_ k v]
           (case k
             :before? (.isBefore inst (undatafy v))
             :after?  (.isAfter  inst (undatafy v))
             :format  (format* v DateTimeFormatter/ISO_INSTANT inst)
             :+       (let [[n unit] v] (internal/safe-plus :time inst unit n))
             :-       (let [[n unit] v] (internal/safe-minus :time inst unit n))
             :to      (let [[t z] v]
                        (case t
                          :local-time      (LocalTime/ofInstant      inst  (parse/zone-id z))
                          :local-date      (LocalDate/ofInstant      inst  (parse/zone-id z))
                          :local-datetime  (LocalDateTime/ofInstant  inst  (parse/zone-id z))
                          :offset-datetime (OffsetDateTime/ofInstant inst  (parse/zone-id z))
                          :zoned-datetime  (ZonedDateTime/ofInstant  inst  (parse/zone-id z))
                          (invalid! v)))
             (invalid! k)))})))
  )

(defn now!
  "Returns a Temporal object in the specified <as> mode
   (defaults to :instant) representing the current point in time.
   A custom Clock <clock>, and/or a specific time-zone <zone-id> can be provided.
   If both are provided, the Clock takes precedence.
   Consumers may need to type-hint (per <as>) at the call-site (in case Temporal doesn't suffice)."

  (^Temporal [] (now! nil))
  (^Temporal [{:keys [as ^ZoneId zone-id ^Clock clock]
               :or   {as :instant}}]
   (case as
    :instant         (internal/now-variant Instant clock zone-id)
    :year-month      (internal/now-variant YearMonth clock zone-id)
    :local-time      (internal/now-variant LocalTime clock zone-id)
    :local-date      (internal/now-variant LocalDate clock zone-id)
    :local-datetime  (internal/now-variant LocalDateTime clock zone-id)
    :offset-datetime (internal/now-variant OffsetDateTime clock zone-id)
    :zoned-datetime  (internal/now-variant ZonedDateTime clock zone-id)
    (invalid! as))))
