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
                      ZonedDateTime OffsetDateTime ZoneId Clock ZoneOffset Year)
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
  (if-let [epoch-second (:epoch-second x)]
    (Instant/ofEpochSecond epoch-second (:second-nano x))

    (if-let [zone-id (and (contains? x :offset)
                          (get-in x [:zone :id]))]
      (ZonedDateTime/ofStrict
        (internal/local-datetime-of x)
        (ZoneOffset/of ^String (get-in x [:offset :id]))
        (parse/zone-id zone-id))

      (if-let [offset-id (and (contains? x :year)
                              (get-in x [:offset :id]))]
        (let [ldt (internal/local-datetime-of x)]
          (OffsetDateTime/of ldt
            (ZoneOffset/of ^String offset-id)))

        (if (and (get-in x [:year :day])
                 (get x :hour))
          (internal/local-datetime-of x)

          (if (and (get-in x [:year :day])
                   (nil? (get x :hour)))
            (internal/local-date-of x)

            (if (contains? x :hour)
              (internal/local-time-of x)

              (if-let [i (get-in x [:weekday :value])]
                (DayOfWeek/of i)

                (if (and (contains? x :year)
                         (contains? x :month))
                  (internal/year-month-of x)

                  (if-let [i (get-in x [:month :value])]
                    (Month/of i)

                    (if-let [zid (get-in x [:zone :id])]
                      (ZoneId/of zid)

                      (when-let [^String off-id (get-in x [:offset :id])]
                        (ZoneOffset/of off-id)))))))))))))

(defn- format*
  [fmt default-fmt ^TemporalAccessor obj]
  (-> fmt
      (parse/dt-formatter default-fmt)
      (.format obj)))

(defn undatafy
  "Looks up the `:clojure.datafy/obj` key in the metadata of <x>.
   If that fails, reconstructs the appropriate temporal object from scratch
   (assuming keys per the result of `datafy`). If that fails, returns/throws
   `(invalid! {:datafied/not x})`."
  ^Temporal [x]
  (when (some? x)
    (or (original-object x)
        (reconstruct-object x)
        (invalid! {:datafied/not x}))))

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
          {`p/nav (fn [_ k v] (when (= k :year) y))})))

  Month
  (datafy [m]
    (-> {:month {:name   (.name m)
                 :value  (.getValue m)
                 ;; not accounting for leap-year precision on plain Month objects
                 :length (.length m false)}}
        (with-meta
          {`p/nav (fn [_ k v] (when (= k :month) m))})))

  DayOfWeek
  (datafy [d]
    (-> {:weekday {:name  (.name d)
                   :value (.getValue d)}}
        (with-meta
          {`p/nav (fn [_ k v] (when (= k :weekday) d))})))

  YearMonth
  (datafy [ym]
    (let [month-obj (.getMonth ym)
          year-obj  (Year/of (.getYear ym))
          year (d/datafy year-obj)
          month (-> (d/datafy month-obj)
                    ;; correct the length here
                    (assoc-in [:month :length] (.lengthOfMonth ym)))
          ret (merge month year)]
      (with-meta ret
        {`jp/format-as (fn [_ fmt] (format* fmt "yyyy-MM" ym))

         `jp/shift+ (fn [_ n unit safe?] (internal/plus :date ym unit n safe?))
         `jp/shift- (fn [_ n unit safe?] (internal/minus :date ym unit n safe?))

         `jp/before? (fn [_ v] (.isBefore ym (undatafy v)))
         `jp/after?  (fn [_ v] (.isAfter  ym (undatafy v)))

         `p/nav
         (fn [datafied k v]
           (case k
             :year-month ym
             :local-date     (-> ym (.atDay 1))
             :local-datetime (-> ym (.atDay 1) (.atStartOfDay))
             (or
               (p/nav year k v)
               (p/nav month k v))))})))

  LocalTime
  (datafy [lt]
    (let [second (.getSecond lt)
          nanos (.getNano lt) ;; nano of second

          ret {:hour   (.getHour lt)
               :minute (.getMinute lt)
               :second second
               :second-nano nanos
               :second-milli (long (/ nanos 1e6))
               :second-micro (long (/ nanos 1e3))}]
      (with-meta ret
        {`jp/format-as (fn [_ fmt] (format* fmt DateTimeFormatter/ISO_LOCAL_TIME lt))

         `jp/shift+ (fn [_ n unit safe?] (internal/plus :time lt unit n safe?))
         `jp/shift- (fn [_ n unit safe?] (internal/minus :time lt unit n safe?))

         `jp/before? (fn [_ v] (.isBefore lt (undatafy v)))
         `jp/after?  (fn [_ v] (.isAfter  lt (undatafy v)))

         ;; datafied LocalTime can't navigate to anything other than its own data
         `p/nav (fn [datafied k v] (get datafied k))
         })))

  LocalDate
  (datafy [ld]
    (let [weekday (.getDayOfWeek ld)
          datafied-wd (d/datafy weekday)
          month   (.getMonth ld)
          year    (.getYear ld)
          ym      (YearMonth/of year month)
          datafied-ym (d/datafy ym)
          ret     (merge datafied-wd
                         (-> datafied-ym
                             (assoc-in [:year :week] (.get ld IsoFields/WEEK_OF_WEEK_BASED_YEAR))
                             (assoc-in [:year :day]  (.getDayOfYear ld))
                             (assoc-in [:month :day] (.getDayOfMonth ld))))]
      (with-meta ret
        {`jp/format-as (fn [_ fmt] (format* fmt DateTimeFormatter/ISO_LOCAL_DATE ld))

         `jp/shift+ (fn [_ n unit safe?] (internal/plus :date ld unit n safe?))
         `jp/shift- (fn [_ n unit safe?] (internal/minus :date ld unit n safe?))

         `jp/before? (fn [_ v] (.isBefore ld (undatafy v)))
         `jp/after?  (fn [_ v] (.isAfter  ld (undatafy v)))

         `p/nav
         (fn [_ k v]
           (case k
             :local-date ld
             :local-datetime      (.atStartOfDay ld)
             :julian/day          (.getLong ld JulianFields/JULIAN_DAY)
             :julian/modified-day (.getLong ld JulianFields/MODIFIED_JULIAN_DAY)
             :julian/rata-die     (.getLong ld JulianFields/RATA_DIE)
             (or
               (p/nav datafied-ym k v)
               (p/nav datafied-wd k v))))})))

  LocalDateTime
  (datafy [ldt]
    (let [lt  (.toLocalTime ldt)
          ld  (.toLocalDate ldt)
          datafied-lt (d/datafy lt)
          datafied-ld (d/datafy ld)
          ret (merge datafied-lt datafied-ld)]
      (with-meta ret
        {`jp/format-as (fn [_ fmt] (format* fmt DateTimeFormatter/ISO_LOCAL_DATE_TIME ldt))

         `jp/shift+ (fn [_ n unit safe?] (internal/plus :date ldt unit n safe?))
         `jp/shift- (fn [_ n unit safe?] (internal/minus :date ldt unit n safe?))

         `jp/before? (fn [_ v] (.isBefore ldt (undatafy v)))
         `jp/after?  (fn [_ v] (.isAfter  ldt (undatafy v)))

         `jp/at-zone   (fn [_ zid]   (.atZone ldt (parse/zone-id zid)))
         `jp/at-offset (fn [_ offid] (.atOffset ldt (parse/zone-offset offid ldt)))

         `p/nav
         (fn [_ k v]
           (case k
             :local-time lt
             :local-date ld
             :local-datetime ldt
             (or
               (p/nav datafied-lt k v)
               (p/nav datafied-ld k v))))})))

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
        {`jp/format-as (fn [_ fmt] (format* fmt DateTimeFormatter/ISO_OFFSET_DATE_TIME odt))

         `jp/shift+ (fn [_ n unit safe?] (internal/plus :date odt unit n safe?))
         `jp/shift- (fn [_ n unit safe?] (internal/minus :date odt unit n safe?))

         `jp/before? (fn [_ v] (.isBefore odt (undatafy v)))
         `jp/after?  (fn [_ v] (.isAfter  odt (undatafy v)))


         `jp/at-offset (fn [_ offid mode]
                         (case mode
                           :same-instant (.withOffsetSameInstant odt (parse/zone-offset offid ldt))
                           :same-local   (.withOffsetSameLocal   odt (parse/zone-offset offid ldt))
                           (invalid! mode)))

         `p/nav
         (fn [_ k v]
           (case k
             :offset offset
             :local-datetime ldt
             :offset-datetime odt
             :instant (.toInstant odt)
             (p/nav datafied-ldt k v)))})))

  ZoneId
  (datafy [zone]
    {:zone {:id (.getId zone)}})

  ZonedDateTime
  (datafy [zdt]
    (let [odt  (.toOffsetDateTime zdt)
          zone (.getZone zdt)
          datafied-odt (d/datafy odt)
          ret  (merge datafied-odt (d/datafy zone))]
      (with-meta ret
        {`jp/format-as (fn [_ fmt] (format* fmt DateTimeFormatter/ISO_ZONED_DATE_TIME zdt))

         `jp/shift+ (fn [_ n unit safe?] (internal/plus :date zdt unit n safe?))
         `jp/shift- (fn [_ n unit safe?] (internal/minus :date zdt unit n safe?))

         `jp/before? (fn [_ v] (.isBefore zdt (undatafy v)))
         `jp/after?  (fn [_ v] (.isAfter  zdt (undatafy v)))

         `jp/at-zone (fn [_ zid mode]
                       (case mode
                         :same-instant (.withZoneSameInstant zdt (parse/zone-id zid))
                         :same-local (.withZoneSameLocal zdt (parse/zone-id zid))
                         (invalid! mode)))

         `p/nav (fn [_ k v]
                  (case k
                    :zoned-datetime zdt
                    :zone zone
                    (p/nav datafied-odt k v)))})))

  Instant
  ;; a count of nanoseconds since the epoch of the first moment of 1970 in UTC
  (datafy [inst]
    (let [epoch-second (.getEpochSecond inst) ;; total seconds *until* <inst>
          second-nanos (.getNano inst)        ;; total nanoseconds within current second
          epoch-nano   (-> epoch-second (* 1e9) long (+ second-nanos)) ;; total nanoseconds *until* <inst>
          epoch-milli  (long (/ epoch-nano 1e6))
          epoch-micro  (long (/ epoch-nano 1e3))
          ret  {:second-nano second-nanos
                :epoch-second epoch-second
                :epoch-milli  epoch-milli
                :epoch-micro  epoch-micro
                :epoch-nano   epoch-nano}]
      (with-meta ret
        {`jp/format-as (fn [_ fmt] (format* fmt DateTimeFormatter/ISO_INSTANT inst))

         `jp/shift+ (fn [_ n unit safe?] (internal/plus :time inst unit n safe?))
         `jp/shift- (fn [_ n unit safe?] (internal/minus :time inst unit n safe?))

         `jp/before? (fn [_ v] (.isBefore inst (undatafy v)))
         `jp/after?  (fn [_ v] (.isAfter  inst (undatafy v)))

         `jp/at-zone (fn [_ zid mode] (ZonedDateTime/ofInstant inst (parse/zone-id zid)))

         `p/nav
         (fn [datafied k v]
           (get datafied k))})))
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
