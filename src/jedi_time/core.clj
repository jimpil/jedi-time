(ns jedi-time.core
  "Navigable datafied versions of the core `java.time` objects."
  (:require [clojure.core.protocols :as p]
            [clojure.datafy :as d]
            [jedi-time
             [protocols :as jp]
             [internal :as internal]
             [parse    :as parse]])
  (:import (java.time YearMonth Month DayOfWeek Instant
                      LocalTime LocalDate LocalDateTime
                      ZonedDateTime OffsetDateTime ZoneId Clock ZoneOffset Year MonthDay)
           (java.time.format DateTimeFormatter)
           (java.time.temporal IsoFields Temporal TemporalAccessor JulianFields)
           (java.math RoundingMode)))

(defn ^:dynamic invalid!
  "Controls what happens when a key is not recognised.
   Throws ex-info (by default), but can be dynamically rebound."
  [k]
  (throw (ex-info "Invalid argument!"
                  (if (map? k) k {:key k}))))

(defn- original-object
  [datafied]  ;; `d/datafy` adds the original object in the metadata
  (-> datafied meta ::d/obj))

(defn- reconstruct-object
  [x]
  (if-let [epoch-second (:epoch/second x)] ;; fast path
    (Instant/ofEpochSecond epoch-second (:second/nano x))

    (let [yd? (contains? x :year/day)
          dh? (contains? x :day/hour)]

      (if-let [zone-id (and yd? dh? (get-in x [:zone :id]))]
        (let [zone (ZoneId/of zone-id)
              zone-rules (.getRules zone)
              ldt (internal/local-datetime-of x)
              offset (.getOffset zone-rules ldt)]
          (ZonedDateTime/ofStrict ldt offset zone))

        (if-let [offset-id (and (contains? x :year)
                                (get-in x [:offset :id]))]
          (OffsetDateTime/of
            (internal/local-datetime-of x)
            (ZoneOffset/of ^String offset-id))

          (if (and yd? dh?)
            (internal/local-datetime-of x)

            (if (and yd? (nil? (get x :day/hour)))
              (internal/local-date-of x)

              (if dh?
                (internal/local-time-of x)

                (if-let [i (get-in x [:week-day :value])]
                  (DayOfWeek/of i)

                  (if (and (contains? x :year)
                           (contains? x :month))
                    (internal/year-month-of x)

                    (if-let [yv (and (not (contains? x :month))
                                     (get-in x [:year :value]))]
                      (Year/of yv)

                      (if-let [mv (get-in x [:month :value])]
                        (Month/of mv)

                        (if-let [zid (get-in x [:zone :id])]
                          (ZoneId/of zid)

                          (when-let [^String off-id (get-in x [:offset :id])]
                            (ZoneOffset/of off-id)))))))))))))))

(defn- format*
  [fmt default-fmt ^TemporalAccessor obj]
  (-> fmt
      (parse/dt-formatter default-fmt)
      (.format obj)))

(defn undatafy
  "Looks up the `:clojure.datafy/obj` key in the metadata of <x>,
   and makes sure it matches <x>. If it fails, reconstructs the
   appropriate temporal object from scratch (assuming keys per `d/datafy`).
   If that fails, returns/throws `(invalid! {:datafied/not x})`."
  ^Temporal [x]
  (when (some? x)
    (let [oo (original-object x)]
      (or (and (= x (d/datafy oo)) oo)
          (reconstruct-object x)
          (invalid! {:datafied/not x})))))

(def redatafy
  "Composes `datafy` with`undatafy` (via `comp`).
   Useful for re-obtaining `nav` capabilities
   if the metadata was lost (e.g by serialisation)."
  (comp d/datafy undatafy))

(extend-protocol p/Datafiable

  Year
  (datafy [y]
    (-> {:year {:value  (.getValue y)
                :leap?  (.isLeap y)
                :length (.length y)}}
        (with-meta
          {`jp/before? (fn [this v] (.isBefore ^Year (undatafy this) (undatafy v)))
           `jp/after?  (fn [this v] (.isAfter  ^Year (undatafy this) (undatafy v)))

           `p/nav (fn [datafied k v]
                    (when (= k :year)
                      (undatafy {:year (or v (get datafied :year))})))})))

  Month
  (datafy [m]
    (-> {:month {:name   (.name m)
                 :value  (.getValue m)
                 ;; not accounting for leap-year precision on plain Month objects
                 :length (.length m false)}}
        (with-meta
          {`p/nav (fn [datafied k v]
                    (when (= k :month)
                      (undatafy {:month (or v (get datafied :month))})))})))

  MonthDay
  (datafy [md]
    (-> {:month-day {:value (.getDayOfMonth md)}}
        (with-meta
          {`p/nav
           (fn [datafied k v]
             (let [^MonthDay md (undatafy datafied)]
               (case k
                 :format (format* v "MM-dd" md)
                 :month-day (undatafy (merge datafied {:month-day v}))
                 nil)))})))

  DayOfWeek
  (datafy [d]
    (-> {:week-day {:name  (.name d)
                    :value (.getValue d)}}
        (with-meta
          {`p/nav
           (fn [datafied k v]
             (when (= k :week-day)
               (undatafy {:week-day (or v (get datafied :week-day))})))})))

  YearMonth
  (datafy [ym]
    (let [month-obj (.getMonth ym)
          year-obj  (Year/of (.getYear ym))
          month (-> (d/datafy month-obj)
                    ;; correct the length here
                    (assoc-in [:month :length] (.lengthOfMonth ym)))
          ret (merge month (d/datafy year-obj))]
      (with-meta ret
        {`jp/shift+ (fn [this n unit safe?] (internal/plus :date (undatafy this) unit n safe?))
         `jp/shift- (fn [this n unit safe?] (internal/minus :date (undatafy this) unit n safe?))

         `jp/before? (fn [this v] (.isBefore ^YearMonth (undatafy this) (undatafy v)))
         `jp/after?  (fn [this v] (.isAfter  ^YearMonth (undatafy this) (undatafy v)))

         `p/nav
         (fn [datafied k v]
           (let [^YearMonth ym (undatafy datafied)]
             (case k
               :format         (format* v "yyyy-MM" ym)
               :local-date     (.atDay ym 1)
               :local-datetime (let [^LocalDate ld (d/nav datafied :local-date v)]
                                 (.atStartOfDay ld))
               :offset (-> ym
                           (d/nav :local-datetime nil)
                           d/datafy
                           (d/nav :offset v))
               :zone   (-> ym
                           (d/nav :local-datetime nil)
                           d/datafy
                           (d/nav :zone v))
               (or
                 (-> (Year/of (.getYear ym))
                     d/datafy
                     (d/nav k v))
                 (-> (.getMonth ym)
                     d/datafy
                     (d/nav k v))))))})))

  LocalTime
  (datafy [lt]
    (let [second (.getSecond lt)
          nanos (.getNano lt) ;; nano of second
          ret {:day/hour    (.getHour lt)
               :hour/minute (.getMinute lt)
               :minute/second second
               :second/nano   nanos
               :second/milli (long (/ nanos 1e6))
               :second/micro (long (/ nanos 1e3))}]
      (with-meta ret
        {`jp/shift+ (fn [this n unit safe?] (internal/plus :time (undatafy this) unit n safe?))
         `jp/shift- (fn [this n unit safe?] (internal/minus :time (undatafy this) unit n safe?))

         `jp/before? (fn [this v] (.isBefore ^LocalTime (undatafy this) (undatafy v)))
         `jp/after?  (fn [this v] (.isAfter  ^LocalTime (undatafy this) (undatafy v)))

         ;; datafied LocalTime can't navigate to anything other than its own data
         `p/nav (fn [datafied k v]
                  (let [^LocalTime lt (undatafy datafied)]
                    (case k
                      :format (format* v DateTimeFormatter/ISO_LOCAL_TIME lt)
                      (when (contains? datafied k) v))))})))

  LocalDate
  (datafy [ld]
    (let [weekday (.getDayOfWeek ld)
          month   (.getMonth ld)
          year    (.getYear ld)
          month-day (MonthDay/of month (.getDayOfMonth ld))
          ym      (YearMonth/of year month)
          ret     (merge (d/datafy weekday)
                         (d/datafy ym)
                         (d/datafy month-day)
                         {:year/week (.get ld IsoFields/WEEK_OF_WEEK_BASED_YEAR)
                          :year/day  (.getDayOfYear ld)})]
      (with-meta ret
        {`jp/shift+ (fn [this n unit safe?] (internal/plus :date (undatafy this) unit n safe?))
         `jp/shift- (fn [this n unit safe?] (internal/minus :date (undatafy this) unit n safe?))

         `jp/before? (fn [this v] (.isBefore ^LocalDate (undatafy this) (undatafy v)))
         `jp/after?  (fn [this v] (.isAfter  ^LocalDate (undatafy this) (undatafy v)))

         `p/nav
         (fn [datafied k v]
           (let [^LocalDate ld (undatafy datafied)]
             (case k
               :format     (format* v DateTimeFormatter/ISO_LOCAL_DATE ld)
               :local-datetime      (.atStartOfDay ld)
               :offset (-> ld
                           (d/nav :local-datetime nil)
                           d/datafy
                           (d/nav :offset v))
               :zone   (-> ld
                           (d/nav :local-datetime nil)
                           d/datafy
                           (d/nav :zone v))
               :year-month (YearMonth/of (.getYear ld) (.getMonth ld))
               :julian/day          (.getLong ld JulianFields/JULIAN_DAY)
               :julian/modified-day (.getLong ld JulianFields/MODIFIED_JULIAN_DAY)
               :julian/rata-die     (.getLong ld JulianFields/RATA_DIE)
               (or
                 (-> (YearMonth/of (.getYear ld) (.getMonth ld))
                     d/datafy
                     (d/nav k v))
                 (-> (.getDayOfWeek ld)
                     d/datafy
                     (d/nav k v))
                 (-> (MonthDay/of month (.getDayOfMonth ld))
                     d/datafy
                     (d/nav k v))))))})))

  LocalDateTime
  (datafy [ldt]
    (let [lt  (.toLocalTime ldt)
          ld  (.toLocalDate ldt)
          ret (merge (d/datafy lt)
                     (d/datafy ld))]
      (with-meta ret
        {`jp/shift+ (fn [this n unit safe?] (internal/plus :date (undatafy this) unit n safe?))
         `jp/shift- (fn [this n unit safe?] (internal/minus :date (undatafy this) unit n safe?))

         `jp/before? (fn [this v] (.isBefore ^LocalDateTime (undatafy this) (undatafy v)))
         `jp/after?  (fn [this v] (.isAfter  ^LocalDateTime (undatafy this) (undatafy v)))

         `p/nav
         (fn [datafied k v]
           (let [^LocalDateTime ldt (undatafy datafied)
                 lt (.toLocalTime ldt)
                 ld (.toLocalDate ldt)]
             (case k
               :format (format* v DateTimeFormatter/ISO_LOCAL_DATE_TIME ldt)
               :local-time lt
               :local-date ld
               :offset (let [^ZoneOffset zoff (if (string? v)
                                                (ZoneOffset/of ^String v)
                                                (undatafy {:offset v}))]
                         (.atOffset ldt zoff))
               :zone (let [^ZoneId zid (if (string? v)
                                         (ZoneId/of v)
                                         (undatafy {:zone v}))]
                       (.atZone ldt zid))
               (or
                 (d/nav (d/datafy lt) k v)
                 (d/nav (d/datafy ld) k v)))))})))

  ZoneOffset
  (datafy [offset]
    (let [off-seconds (.getTotalSeconds offset)]
      {:offset {:id   (.getId offset)
                :seconds off-seconds
                :hours   (with-precision 4
                           :rounding RoundingMode/HALF_EVEN
                           (double (/ off-seconds 60M)))}}))

  OffsetDateTime
  (datafy [odt]
    (let [ldt    (.toLocalDateTime odt)
          datafied-ldt (d/datafy ldt)
          offset (.getOffset odt)
          ret    (merge datafied-ldt (d/datafy offset))]
      (with-meta ret
        {`jp/shift+ (fn [this n unit safe?] (internal/plus :date (undatafy this) unit n safe?))
         `jp/shift- (fn [this n unit safe?] (internal/minus :date (undatafy this) unit n safe?))

         `jp/before? (fn [this v] (.isBefore ^OffsetDateTime (undatafy this) (undatafy v)))
         `jp/after?  (fn [this v] (.isAfter  ^OffsetDateTime (undatafy this) (undatafy v)))

         `p/nav
         (fn [datafied k v]
           (let [^OffsetDateTime odt (undatafy datafied)]
             (case k
               :format (format* v DateTimeFormatter/ISO_OFFSET_DATE_TIME odt)
               :local-datetime (.toLocalDateTime odt)
               :offset (cond
                         (string? v) (ZoneOffset/of ^String v)
                         (nil? v) (d/nav datafied k (get datafied :offset))
                         (map? v) (let [{:keys [same]} v
                                        offset (undatafy {:offset v})]
                                    (case (or same (get-in datafied [:offset :same]))
                                      :local   (.withOffsetSameLocal odt offset)
                                      :instant (.withOffsetSameInstant odt offset)
                                      offset)))
               :instant (.toInstant odt)
               (-> odt
                   (.toLocalDateTime)
                   d/datafy
                   (d/nav k v)))))})))

  ZoneId
  (datafy [zone]
    {:zone {:id (.getId zone)}})

  ZonedDateTime
  (datafy [zdt]
    (let [odt  (.toOffsetDateTime zdt)
          zone (.getZone zdt)
          ret  (merge (d/datafy odt)
                      (d/datafy zone))]
      (with-meta ret
        {`jp/shift+ (fn [this n unit safe?] (internal/plus :date (undatafy this) unit n safe?))
         `jp/shift- (fn [this n unit safe?] (internal/minus :date (undatafy this) unit n safe?))

         `jp/before? (fn [this v] (.isBefore ^ZonedDateTime (undatafy this) (undatafy v)))
         `jp/after?  (fn [this v] (.isAfter  ^ZonedDateTime (undatafy this) (undatafy v)))


         `p/nav (fn [datafied k v]
                  ;; don't let the offset interfere with translations - don't need for undatafy anyway
                  (let [^ZonedDateTime zdt (undatafy (dissoc datafied :offset))]
                    (case k
                      :format (format* v DateTimeFormatter/ISO_ZONED_DATE_TIME zdt)
                      :offset-datetime (.toOffsetDateTime zdt)
                      :zone (cond
                              (string? v) (ZoneId/of v)
                              (nil? v) (d/nav datafied k (get datafied :zone))
                              (map? v) (let [{:keys [same]} v
                                             ^ZoneId zone (undatafy {:zone v})]
                                         (case (or same (get-in datafied [:zone :same]))
                                           :local   (.withZoneSameLocal zdt zone)
                                           :instant (.withZoneSameInstant zdt zone)
                                           zone)))
                      (d/nav (d/datafy (.toOffsetDateTime zdt)) k v))))})))

  Instant
  ;; a count of nanoseconds since the epoch of the first moment of 1970 in UTC
  (datafy [inst]
    (let [epoch-second (.getEpochSecond inst) ;; total seconds *until* <inst>
          second-nanos (.getNano inst)        ;; total nanoseconds within current second
          epoch-nano   (-> epoch-second (* 1e9) long (+ second-nanos)) ;; total nanoseconds *until* <inst>
          epoch-milli  (long (/ epoch-nano 1e6))
          epoch-micro  (long (/ epoch-nano 1e3))
          ret  {:second/nano second-nanos
                :epoch/second epoch-second
                :epoch/milli  epoch-milli
                :epoch/micro  epoch-micro
                :epoch/nano   epoch-nano}]
      (with-meta ret
        {`jp/shift+ (fn [this n unit safe?] (internal/plus :time (undatafy this) unit n safe?))
         `jp/shift- (fn [this n unit safe?] (internal/minus :time (undatafy this) unit n safe?))

         `jp/before? (fn [this v] (.isBefore ^Instant (undatafy this) (undatafy v)))
         `jp/after?  (fn [this v] (.isAfter  ^Instant (undatafy this) (undatafy v)))


         `p/nav
         (fn [datafied k v]
           (let [^Instant inst (undatafy datafied)]
             (case k
               :format (format* v DateTimeFormatter/ISO_INSTANT inst)
               :offset (let [v (or v (get datafied :offset))
                             ^ZoneOffset zo (if (string? v)
                                              (ZoneOffset/of ^String v)
                                              (undatafy {:offset v}))
                             ldt (LocalDateTime/ofEpochSecond
                                   (.getEpochSecond inst)
                                   (.getNano inst)
                                   zo)]
                         (OffsetDateTime/of ldt zo))
               :zone (let [v (or v (get datafied :zone))
                           ^ZoneId zid (if (string? v)
                                         (ZoneId/of v)
                                         (undatafy {:zone v}))]
                       (ZonedDateTime/ofInstant inst zid)))))})))
  )

(defn now!
  "Returns a Temporal object in the specified <as> mode
   (defaults to :instant) representing the current point in time.
   A custom Clock <clock>, and/or a specific time-zone <zone-id> can be provided.
   If both are provided, the Clock takes precedence.
   Consumers may need to type-hint (per <as>) at the call-site (in case Temporal doesn't suffice)."

  (^Temporal [] (now! nil))
  (^Temporal [{:keys [as zone ^Clock clock]
               :or   {as :instant}}]
   (let [^ZoneId zone-id (when (some? zone) (ZoneId/of zone))]
     (case as
       :instant         (internal/now-variant Instant clock nil)
       :year            (internal/now-variant Year clock zone-id)
       :year-month      (internal/now-variant YearMonth clock zone-id)
       :local-time      (internal/now-variant LocalTime clock zone-id)
       :local-date      (internal/now-variant LocalDate clock zone-id)
       :local-datetime  (internal/now-variant LocalDateTime clock zone-id)
       :offset-datetime (internal/now-variant OffsetDateTime clock zone-id)
       :zoned-datetime  (internal/now-variant ZonedDateTime clock zone-id)
       (invalid! as)))))
