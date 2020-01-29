(ns jedi-time.core
  "A datafiable/navigable view of the core `java.time` objects."
  (:require [jedi-time
             [internal  :as internal]
             [protocols :as p]
             [parse     :as parse]])
  (:import (java.time YearMonth Month DayOfWeek Instant
                      LocalTime LocalDate LocalDateTime
                      ZonedDateTime OffsetDateTime
                      ZoneOffset ZoneId Clock)
           (java.time.format DateTimeFormatter)
           (java.time.temporal IsoFields Temporal)
           (java.math RoundingMode)
           (clojure.lang IObj)))

(defn datafy
  "Mirroring `clojure.datafy/datafy`."
  [x]
  (let [v (p/datafy x)]
    (if (identical? v x)
      v
      (if (instance? IObj v)
        (vary-meta v assoc
                   ::obj x
                   ::class (-> x class .getName symbol))
        v))))

(defn nav
  "Mirroring `clojure.datafy/nav`."
  [coll k v]
  (p/nav coll k v))

(defn- original-object
  [datafied]  ;; `datafy` adds the original object in the metadata
  (-> datafied meta ::obj))

(defn undatafy
  "Looks up the `::obj` key in the metadata of <x>.
   If that fails, reconstructs the appropriate temporal object from scratch
   (assuming keys per the result of `datafy`)."
  ^Temporal [x]
  (when (some? x)
    (or
      (original-object x)

      (if-let [epoch (:epoch x)]
        (let [nano-of-second (-> x :second :nano)
              second (:second epoch)]
          (Instant/ofEpochSecond second nano-of-second))

        (if-let [zone-id (get-in x [:zone :id])]
          (ZonedDateTime/ofStrict
            (internal/local-datetime-of x)
            (ZoneOffset/of (get-in x [:offset :id]))
            (ZoneId/of zone-id))

          (if-let [offset-id (get-in x [:offset :id])]
            (OffsetDateTime/of
              (internal/local-datetime-of x)
              (ZoneOffset/of offset-id))

            (if-let [year-day (get-in x [:year :day])]
              (internal/local-datetime-of x)

              (if-let [year-week (get-in x [:year :week])]
                (internal/local-date-of x)

                (if (contains? x :day)
                  (internal/local-time-of x)

                  (if (contains? x :year)
                    (internal/year-month-of x)
                    (Month/of (get-in x [:month :value]))))))))))))

(def redatafy
  "Composes `datafy` with`undatafy` (via `comp`).
   Useful for re-obtaining nav capabilities the metadata lost (e.g by serialisation)."
  (comp datafy undatafy))

(defonce system-zone
  (delay (ZoneId/systemDefault)))

(defonce system-offset
  (delay (ZoneOffset/systemDefault)))

(extend-protocol p/Datafiable

  Month
  (datafy [m]
    {:month {:name   (.name m)
             :value  (.getValue m)
             ;; ignoring leap-year precision on plain Month objects
             :length (.length m false)}})

  DayOfWeek
  (datafy [d]
    {:week {:day {:name  (.name d)
                  :value (.getValue d)}}})

  YearMonth
  (datafy [ym]
    (let [month (-> (datafy (.getMonth ym))
                    ;; correct the length here
                    (assoc-in [:month :length] (.lengthOfMonth ym)))]
      (with-meta
        {:year (merge month
                      {:length (.lengthOfYear ym) ;; 365 or 366 depending on `.isLeapYear()`
                       :leap?  (.isLeapYear ym)
                       :value  (.getYear ym)})}

        {`p/nav (fn [_ k v]
                  (case k
                    :before? (.isBefore ym (undatafy v))
                    :after?  (.isAfter  ym (undatafy v))
                    :instant (-> (.atDay ym 1)
                                 (.atStartOfDay)
                                 (.toInstant (or (parse/zone-offset v) @system-offset)))
                    :format (.format (parse/dt-formatter (or v "yyyy-MM")) ym)
                    :+          (let [[n unit] v] (internal/safe-plus :date ym unit n))
                    :-          (let [[n unit] v] (internal/safe-minus :date ym unit n))
                    nil))})))

  LocalTime
  (datafy [lt]
    (let [nanos (.getNano lt)]
      (with-meta
        {:day    {:hour   (.getHour   lt)}
         :hour   {:minute (.getMinute lt)}
         :minute {:second (.getSecond lt)}
         :second {:nano  nanos
                  :milli (long (/ nanos 1e6))
                  :micro (long (/ nanos 1e3))}}

        {`p/nav  (fn [_ k v]
                   (case k
                     :before? (.isBefore lt (undatafy v))
                     :after?  (.isAfter  lt (undatafy v))
                     :format  (.format (if (= :iso v)
                                         DateTimeFormatter/ISO_LOCAL_TIME
                                         (parse/dt-formatter v))
                                       lt)
                     :+       (let [[n unit] v] (internal/safe-plus :time  lt unit n))
                     :-       (let [[n unit] v] (internal/safe-minus :time lt unit n))
                     nil))})))

  LocalDate
  (datafy [ld]
    (let [weekday (.getDayOfWeek ld)
          ym      (YearMonth/of (.getYear ld) (.getMonth ld))]
      (with-meta
        (merge (datafy weekday)
               (-> (datafy ym)
                   (assoc-in [:year :week]       (.get ld IsoFields/WEEK_OF_WEEK_BASED_YEAR))
                   (assoc-in [:year :month :day] (.getDayOfMonth ld))))

        {`p/nav (fn [_ k v]
                  (case k
                    :before?    (.isBefore ld (undatafy v))
                    :after?     (.isAfter  ld (undatafy v))
                    :format     (.format (if (= :iso v)
                                           DateTimeFormatter/ISO_LOCAL_DATE
                                           (parse/dt-formatter v))
                                         ld)
                    :julian     (internal/julian-field ld v)
                    :instant    (-> (.atStartOfDay ld)
                                    (.toInstant (or (parse/zone-offset v) @system-offset)))
                    :+          (let [[n unit] v] (internal/safe-plus :date  ld unit n))
                    :-          (let [[n unit] v] (internal/safe-minus :date ld unit n))
                    :weekday    weekday
                    :year-month ym
                    nil))})))

  LocalDateTime
  (datafy [ldt]
    (let [lt (.toLocalTime ldt)
          ld (.toLocalDate ldt)]
      (with-meta
        (-> (merge-with merge (datafy lt) (datafy ld))
            (assoc-in [:year :day]  (.getDayOfYear  ldt)))

        {`p/nav (fn [_ k v]
                  (case k
                    :before?    (.isBefore ldt (undatafy v))
                    :after?     (.isAfter  ldt (undatafy v))
                    :at-zone    (let [[zid] v]
                                  (.atZone   ldt (or (parse/zone-id zid) @system-zone)))
                    :at-offset  (let [[offid] v]
                                  (.atOffset ldt (or (parse/zone-offset offid) @system-offset)))
                    :format     (.format (if (= :iso v)
                                           DateTimeFormatter/ISO_LOCAL_DATE_TIME
                                           (parse/dt-formatter v))
                                         ldt)
                    :instant    (.toInstant ldt (or (parse/zone-offset v) @system-offset))
                    :julian     (internal/julian-field ldt v)
                    :+          (let [[n unit] v] (internal/safe-plus :date  ldt unit n))
                    :-          (let [[n unit] v] (internal/safe-minus :date ldt unit n))
                    :local-time lt
                    :local-date ld
                    nil))})))

  OffsetDateTime
  (datafy [odt]
    (let [ldt         (.toLocalDateTime odt)
          offset      (.getOffset odt)
          off-seconds (.getTotalSeconds offset)]
      (with-meta
        (-> (datafy ldt)
            (assoc :offset {:id      (.getId offset)
                            :seconds off-seconds
                            :hours   (with-precision 3
                                       :rounding RoundingMode/HALF_EVEN
                                       (double (/ off-seconds 60M)))}))

        {`p/nav (fn [_ k v]
                  (case k
                    :before?   (.isBefore odt (undatafy v))
                    :after?    (.isAfter  odt (undatafy v))
                    :at-offset (let [[offid mode] v]
                                 (case (or mode :same-instant)
                                   :same-instant (.withOffsetSameInstant odt (or (parse/zone-offset offid) @system-offset))
                                   :same-local   (.withOffsetSameLocal   odt (or (parse/zone-offset offid) @system-offset))))
                    :format  (.format (if (= :iso v)
                                        DateTimeFormatter/ISO_OFFSET_DATE_TIME
                                        (parse/dt-formatter v))
                                      odt)
                    :instant (.toInstant odt)
                    :julian  (internal/julian-field odt v)
                    :+       (let [[n unit] v] (internal/safe-plus :date odt unit n))
                    :-       (let [[n unit] v] (internal/safe-minus :date odt unit n))
                    :local-datetime ldt
                    nil))})))

  ZonedDateTime
  (datafy [zdt]
    (let [odt  (.toOffsetDateTime zdt)
          zone (.getZone zdt)]
      (with-meta
        (-> (datafy odt)
            (assoc-in [:zone :id] (.getId zone)))

        {`p/nav (fn [_ k v]
                  (case k
                    :before? (.isBefore zdt (undatafy v))
                    :after?  (.isAfter  zdt (undatafy v))
                    :at-zone (let [[zid mode] v]
                               (case (or mode :same-instant)
                                 :same-instant (.withZoneSameInstant zdt (or (parse/zone-id zid) @system-zone))
                                 :same-local   (.withZoneSameLocal   zdt (or (parse/zone-id zid) @system-zone))))
                    :format  (.format (if (= :iso v)
                                        DateTimeFormatter/ISO_ZONED_DATE_TIME
                                        (parse/dt-formatter v))
                                      zdt)
                    :instant (.toInstant zdt)
                    :julian  (internal/julian-field zdt v)
                    :+       (let [[n unit] v] (internal/safe-plus :date zdt unit n))
                    :-       (let [[n unit] v] (internal/safe-minus :date zdt unit n))
                    :local-datetime (.toLocalDateTime zdt)
                    :offset-datetime odt
                    nil))})))

  Instant
  ;; a count of nanoseconds since the epoch of the first moment of 1970 in UTC
  (datafy [inst]
    (let [epoch-second (.getEpochSecond inst) ;; total seconds *until* <inst>
          nanos        (.getNano inst)        ;; total nanoseconds within current second
          epoch-nano   (-> epoch-second (* 1e9) long (+ nanos)) ;; total nanoseconds *until* <inst>
          epoch-milli  (long (/ epoch-nano 1e6))
          epoch-micro  (long (/ epoch-nano 1e3))]
      (with-meta
        {:second {:nano nanos}
         :epoch {:second epoch-second
                 :milli  epoch-milli
                 :micro  epoch-micro
                 :nano   epoch-nano}}
        {`p/nav (fn [_ k v]
                  (case k
                    :before?         (.isBefore inst (undatafy v))
                    :after?          (.isAfter  inst (undatafy v))
                    :format          (.format (if (= :iso v)
                                                DateTimeFormatter/ISO_INSTANT
                                                (parse/dt-formatter v))
                                              inst)
                    :local-time      (LocalTime/ofInstant      inst  (or (parse/zone-id v) @system-zone))
                    :local-date      (LocalDate/ofInstant      inst  (or (parse/zone-id v) @system-zone))
                    :local-datetime  (LocalDateTime/ofInstant  inst  (or (parse/zone-id v) @system-zone))
                    :offset-datetime (OffsetDateTime/ofInstant inst  (or (parse/zone-id v) @system-zone))
                    :zoned-datetime  (ZonedDateTime/ofInstant  inst  (or (parse/zone-id v) @system-zone))
                    :+               (let [[n unit] v] (internal/safe-plus :time inst unit n))
                    :-               (let [[n unit] v] (internal/safe-minus :time inst unit n))
                    nil))})))
  )

(defn now!
  "Returns a Temporal object in the specified <as> mode
   (defaults to :instant) representing the current point in time.
   A custom Clock <clock>, and/or a specific time-zone <zone-id> can be provided.
   If both are provided, the Clock takes precedence.
   Consumers are encouraged to type-hint appropriately (per <as>) at the call-site."
  ^Temporal
  [& {:keys [as ^ZoneId zone-id ^Clock clock]
      :or   {as :instant}}]
  (case as
    :instant         (internal/now-variant Instant clock zone-id)
    :year-month      (internal/now-variant YearMonth clock zone-id)
    :local-time      (internal/now-variant LocalTime clock zone-id)
    :local-date      (internal/now-variant LocalDate clock zone-id)
    :local-datetime  (internal/now-variant LocalDateTime clock zone-id)
    :offset-datetime (internal/now-variant OffsetDateTime clock zone-id)
    :zoned-datetime  (internal/now-variant ZonedDateTime clock zone-id)
    (internal/throw-illegal-representation! as)))
