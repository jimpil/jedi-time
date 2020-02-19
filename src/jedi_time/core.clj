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

    (let [ld (contains? x :local-date)
          lt (contains? x :local-time)
          ;y?  (contains? x :year)
          ;m?  (contains? x :month)
          ;dh? (some? (get-in  x [:local-time :hour]))
          ;ym? (and y? m?)
          ]

      (if-let [zone-id (and ld lt (get-in x [:zone :zone/id]))]
        (let [zone (ZoneId/of zone-id)
              zone-rules (.getRules zone)
              ldt (internal/local-datetime-of x)
              offset (.getOffset zone-rules ldt)]
          (ZonedDateTime/ofStrict ldt offset zone))

        (if-let [offset-id (and ld lt (get-in x [:offset :offset/id]))]
          (OffsetDateTime/of
            (internal/local-datetime-of x)
            (ZoneOffset/of ^String offset-id))

          (if (and ld lt)
            (internal/local-datetime-of x)

            (if (and (contains? x :month)
                     (contains? x :year)
                     (contains? x :week-day))
              (internal/local-date-of x)

              (if (contains? x :second/nano)
                (internal/local-time-of x)

                (if-let [i (get x :day/value)]
                  (DayOfWeek/of i)

                  (if (and (contains? x :year)
                           (contains? x :month))
                    (internal/year-month-of x)

                    (if-let [yv (and (not (contains? x :month))
                                     (get x :year/value))]
                      (Year/of yv)

                      (if-let [mv (get x :month/value)]
                        (Month/of mv)

                        (if-let [zid (get x :zone/id)]
                          (ZoneId/of zid)

                          (when-let [^String off-id (get x :offset/id)]
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
    (-> {:year/value  (.getValue y)
         :year/leap?  (.isLeap y)
         :year/length (.length y)}
        (with-meta
          {`jp/before? (fn [this v] (.isBefore ^Year (undatafy this) (undatafy v)))
           `jp/after?  (fn [this v] (.isAfter  ^Year (undatafy this) (undatafy v)))

           `p/nav (fn [datafied k v]
                    (if (= k :year)
                      (undatafy
                        (internal/dissoc-optional (or v datafied) :year))
                      (get datafied k)))})))

  Month
  (datafy [m]
    (-> {:month/name   (.name m)
         :month/value  (.getValue m)
         ;; not accounting for leap-year precision on plain Month objects
         :month/length (.length m false)}
        (with-meta
          {`p/nav (fn [datafied k v]
                    (if (= k :month)
                      (undatafy
                        (internal/dissoc-optional (or v datafied) :month))
                      (get datafied k)))})))

  MonthDay
  (datafy [md]
    (let [month (.getMonth md)
          ret (-> (d/datafy month)
                  (assoc :month/day (.getDayOfMonth md)))]
      (-> ret
        (with-meta
          {`p/nav
           (fn [datafied k v]
             (let [^MonthDay md (undatafy
                                  (internal/dissoc-optional (or v datafied) :month-day))]
               (case k
                 :format (format* v "MM-dd" md)
                 :month-day md
                 :month/day (or v (.getDayOfMonth md))
                 :month (or (some-> v undatafy) (.getMonth md))
                 nil)))}))))

  DayOfWeek
  (datafy [d]
    (-> {:day/name  (.name d)
         :day/value (.getValue d)}
        (with-meta
          {`p/nav
           (fn [datafied k v]
             (let [^DayOfWeek wd (undatafy (internal/dissoc-optional (or v datafied) :week-day))]
               (case k
                 :week-day wd
                 :day/name  (or v (.name wd))
                 :day/value (or v (.getValue wd))
                 nil)))})))

  YearMonth
  (datafy [ym]
    (let [month-obj (.getMonth ym)
          year-obj  (Year/of (.getYear ym))
          month (-> (d/datafy month-obj)
                    ;; correct the length here
                    (assoc :month/length (.lengthOfMonth ym)))
          ret {:month month
               :year (d/datafy year-obj)}]
      (with-meta ret
        {`jp/shift+ (fn [this n unit safe?] (internal/plus :date (undatafy this) unit n safe?))
         `jp/shift- (fn [this n unit safe?] (internal/minus :date (undatafy this) unit n safe?))

         `jp/before? (fn [this v] (.isBefore ^YearMonth (undatafy this) (undatafy v)))
         `jp/after?  (fn [this v] (.isAfter  ^YearMonth (undatafy this) (undatafy v)))

         `p/nav
         (fn [datafied k v]
           (let [^YearMonth ym (undatafy (internal/dissoc-optional datafied :year-month))]
             (case k
               :format         (format* v "yyyy-MM" ym)
               :year-month     ym
               :year           (Year/of  (or (undatafy v)
                                             (.getYear ym)))
               :month          (Month/of (or (undatafy v)
                                             (.getValue (.getMonth ym))))
               :instant        (-> datafied (d/nav :local-datetime v) d/datafy (d/nav :instant v))
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
          nanos  (.getNano lt) ;; nano of second
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
                  (let [^LocalTime lt (undatafy (internal/dissoc-optional datafied :local-time))]
                    (case k
                      :local-time lt
                      :format (format* v DateTimeFormatter/ISO_LOCAL_TIME lt)
                      (when (contains? datafied k)
                        (or v (get datafied k))))))})))

  LocalDate
  (datafy [ld]
    (let [weekday (.getDayOfWeek ld)
          month   (.getMonth ld)
          year    (.getYear ld)
          month-day (MonthDay/of month (.getDayOfMonth ld))
          ym      (YearMonth/of year month)
          ret     (merge-with merge
                              {:week-day (d/datafy weekday)
                               :month    (d/datafy month-day)}
                              (-> (d/datafy ym)
                                  (update :year merge
                                          {:year/week (.get ld IsoFields/WEEK_OF_WEEK_BASED_YEAR)
                                           :year/day  (.getDayOfYear ld)})


                                     ))]
      (with-meta ret
        {`jp/shift+ (fn [this n unit safe?] (internal/plus :date (undatafy this) unit n safe?))
         `jp/shift- (fn [this n unit safe?] (internal/minus :date (undatafy this) unit n safe?))

         `jp/before? (fn [this v] (.isBefore ^LocalDate (undatafy this) (undatafy v)))
         `jp/after?  (fn [this v] (.isAfter  ^LocalDate (undatafy this) (undatafy v)))

         `p/nav
         (fn [datafied k v]
           (let [^LocalDate ld (undatafy (internal/dissoc-optional datafied :local-date))]
             (case k
               :format (format* v DateTimeFormatter/ISO_LOCAL_DATE ld)
               :local-datetime (.atStartOfDay ld)
               :local-date ld
               :instant (-> datafied (d/nav :local-datetime nil) d/datafy (d/nav :instant v))
               :offset (-> ld
                           (d/nav :local-datetime nil)
                           d/datafy
                           (d/nav :offset v))
               :zone   (-> ld
                           (d/nav :local-datetime nil)
                           d/datafy
                           (d/nav :zone v))
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
          ret {:local-date (d/datafy ld)
               :local-time (d/datafy lt)}]
      (with-meta ret
        {`jp/shift+ (fn [this n unit safe?] (internal/plus :date (undatafy this) unit n safe?))
         `jp/shift- (fn [this n unit safe?] (internal/minus :date (undatafy this) unit n safe?))

         `jp/before? (fn [this v] (.isBefore ^LocalDateTime (undatafy this) (undatafy v)))
         `jp/after?  (fn [this v] (.isAfter  ^LocalDateTime (undatafy this) (undatafy v)))

         `p/nav
         (fn [datafied k v]
           (let [^LocalDateTime ldt (undatafy (internal/dissoc-optional datafied :local-datetime))
                 lt (.toLocalTime ldt)
                 ld (.toLocalDate ldt)]
             (case k
               :format (format* v DateTimeFormatter/ISO_LOCAL_DATE_TIME ldt)
               :instant (-> datafied (d/nav :offset v) d/datafy (d/nav :instant nil))
               :local-datetime ldt
               :local-time lt
               :local-date ld
               :offset (let [^ZoneOffset zoff (if (string? v)
                                                (ZoneOffset/of ^String v)
                                                (undatafy v))]
                         (.atOffset ldt zoff))
               :zone (let [^ZoneId zid (if (string? v)
                                         (ZoneId/of v)
                                         (undatafy v))]
                       (.atZone ldt zid))
               (or
                 (d/nav (d/datafy lt) k v)
                 (d/nav (d/datafy ld) k v)))))})))

  ZoneOffset
  (datafy [offset]
    (let [off-seconds (.getTotalSeconds offset)]
      {:offset/id     (.getId offset)
       :offset/seconds off-seconds
       :offset/hours   (with-precision 4
                         :rounding RoundingMode/HALF_EVEN
                         (double (/ off-seconds 60M)))}))

  OffsetDateTime
  (datafy [odt]
    (let [ldt    (.toLocalDateTime odt)
          datafied-ldt (d/datafy ldt)
          offset (.getOffset odt)
          ret    (assoc datafied-ldt :offset (d/datafy offset))]
      (with-meta ret
        {`jp/shift+ (fn [this n unit safe?] (internal/plus :date (undatafy this) unit n safe?))
         `jp/shift- (fn [this n unit safe?] (internal/minus :date (undatafy this) unit n safe?))

         `jp/before? (fn [this v] (.isBefore ^OffsetDateTime (undatafy this) (undatafy v)))
         `jp/after?  (fn [this v] (.isAfter  ^OffsetDateTime (undatafy this) (undatafy v)))

         `p/nav
         (fn [datafied k v]
           (let [^OffsetDateTime odt (undatafy (internal/dissoc-optional datafied :offset-datetime))]
             (case k
               :format (format* v DateTimeFormatter/ISO_OFFSET_DATE_TIME odt)
               :instant (.toInstant odt)
               :offset-datetime odt
               :local-datetime (.toLocalDateTime odt)
               :offset (cond
                         (string? v) (ZoneOffset/of ^String v)
                         (nil? v) (d/nav datafied k (get datafied :offset))
                         (map? v) (let [{:keys [same]} v
                                        offset (undatafy v)]
                                    (case (or same (get-in datafied [:offset :same]))
                                      :local   (.withOffsetSameLocal odt offset)
                                      :instant (.withOffsetSameInstant odt offset)
                                      offset)))
               (-> odt
                   (.toLocalDateTime)
                   d/datafy
                   (d/nav k v)))))})))

  ZoneId
  (datafy [zone]
    {:zone/id (.getId zone)})

  ZonedDateTime
  (datafy [zdt]
    (let [odt  (.toOffsetDateTime zdt)
          zone (.getZone zdt)
          ret  (assoc (d/datafy odt) :zone (d/datafy zone))]
      (with-meta ret
        {`jp/shift+ (fn [this n unit safe?] (internal/plus :date (undatafy this) unit n safe?))
         `jp/shift- (fn [this n unit safe?] (internal/minus :date (undatafy this) unit n safe?))

         `jp/before? (fn [this v] (.isBefore ^ZonedDateTime (undatafy this) (undatafy v)))
         `jp/after?  (fn [this v] (.isAfter  ^ZonedDateTime (undatafy this) (undatafy v)))


         `p/nav (fn [datafied k v]
                  ;; don't let the offset interfere with translations - don't need for undatafy anyway
                  (let [^ZonedDateTime zdt (undatafy (internal/dissoc-optional datafied :zoned-datetime))]
                    (case k
                      :format (format* v DateTimeFormatter/ISO_ZONED_DATE_TIME zdt)
                      :instant (.toInstant zdt)
                      :zoned-datetime zdt
                      :offset-datetime (.toOffsetDateTime zdt)
                      :zone (cond
                              (string? v) (ZoneId/of v)
                              (nil? v) (d/nav datafied k (get datafied :zone))
                              (map? v) (let [{:keys [same]} v
                                             ^ZoneId zone (undatafy v)]
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
                ;; Java-9 added micro precision
                ;; (https://bugs.openjdk.java.net/browse/JDK-8068730)
                :epoch/micro  epoch-micro
                ;; some exotic Clocks even attempt to do nano - generally not useful and unreliable
                ;; (https://jenetics.io/javadoc/jenetics/5.0/index.html?io/jenetics/util/NanoClock.html)
                :epoch/nano   epoch-nano}]
      (with-meta ret
        {`jp/shift+ (fn [this n unit safe?] (internal/plus :time (undatafy this) unit n safe?))
         `jp/shift- (fn [this n unit safe?] (internal/minus :time (undatafy this) unit n safe?))

         `jp/before? (fn [this v] (.isBefore ^Instant (undatafy this) (undatafy v)))
         `jp/after?  (fn [this v] (.isAfter  ^Instant (undatafy this) (undatafy v)))


         `p/nav
         (fn [datafied k v]
           (let [^Instant inst (undatafy (internal/dissoc-optional datafied :instant))]
             (case k
               :format (format* v DateTimeFormatter/ISO_INSTANT inst)
               :instant inst
               :offset (let [v (or v (get datafied :offset))
                             ^ZoneOffset zo (if (string? v)
                                              (ZoneOffset/of ^String v)
                                              (undatafy v))
                             ldt (LocalDateTime/ofEpochSecond
                                   (.getEpochSecond inst)
                                   (.getNano inst)
                                   zo)]
                         (OffsetDateTime/of ldt zo))
               :zone (let [v (or v (get datafied :zone))
                           ^ZoneId zid (if (string? v)
                                         (ZoneId/of v)
                                         (undatafy v))]
                       (ZonedDateTime/ofInstant inst zid))
               (get datafied k))))})))
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
