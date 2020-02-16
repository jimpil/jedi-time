(ns jedi-time.datafied.tools
  (:require [jedi-time.protocols :as jp]
            [jedi-time.core :as jdt]
            [clojure.datafy :as d]
            [clojure.core.protocols :as p]))

(defmacro ^:private with-meta-check
  [[fsym datafied & more]]
  `(if (contains? (meta ~datafied) '~fsym)
     (~fsym ~datafied ~@more)
     (-> ~datafied jdt/redatafy (~fsym ~@more))))


(defn shift+
  "Shifts this datafied representation forward-in-time by:

   n     - a positive integer
   units - see `jedi-time.units/chrono-units` keys

   By default the shift happens safely (only if unit is supported),
   in which case may return nil - otherwise may throw."
  ([datafied by]
   (shift+ datafied by true))
  ([datafied [n unit] safe?]
   (d/datafy
     (with-meta-check ;; has to be a fully qualified call
       (jedi-time.protocols/shift+ datafied n unit safe?)))))

(defn shift-
  "Shifts this datafied representation backward-in-time by:

   n     - a positive integer
   units - see `jedi-time.units/chrono-units` keys

   By default the shift happens safely (only if unit is supported),
   in which case may return nil - otherwise may throw."
  ([datafied by]
   (shift- datafied by true))
  ([datafied [n unit] safe?]
   (d/datafy
     (with-meta-check ;; has to be a fully qualified call
       (jedi-time.protocols/shift- datafied n unit safe?)))))

(defn before?
  "Returns true if <this> datafied representation
   is before the <other> - false otherwise.
   Both arguments are expected to represent the same thing."
  [datafied other]
  (with-meta-check
    (jedi-time.protocols/before? datafied other)))

(defn after?
  "Returns true if <this> datafied representation
   is after the <other> - false otherwise.
   Both arguments are expected to represent the same thing."
  [datafied other]
  (with-meta-check ;; has to be a fully qualified call
    (jedi-time.protocols/after? datafied other)))


(defn same-instant?
  "Returns true if the first two arguments represent exactly the same
   point-in-time - false otherwise. First two arguments are not
   expected to (necessarily) represent the same thing.
   An offset can be provided as the 3rd arg, but will only be used
   if needed (i.e. for upgrading to Instant)."
  ([datafied other]
   (same-instant? datafied other {:id "Z"}))
  ([datafied other offset]
   (let [this-instant  (with-meta-check
                         (clojure.core.protocols/nav datafied :instant offset))
         other-instant (with-meta-check
                         (clojure.core.protocols/nav other :instant offset))]
     (= this-instant other-instant))))

(defn same-date?
  "Returns true if the two datafied representations fall
   under the same date (year/month/day) - false otherwise.
   The two arguments are not expected to (necessarily)
   represent the same thing."
  [datafied other]
  (let [di? (contains? datafied :epoch/second)
        oi? (contains? other    :epoch/second)]
    (if (or di? oi?)
      (recur
        (if di?
          (d/datafy
            (with-meta-check
              (clojure.core.protocols/nav datafied :offset {:id "Z"})))
          datafied)
        (if oi?
          (d/datafy
            (with-meta-check
              (clojure.core.protocols/nav other :offset {:id "Z"})))
          other))
      (let [this-date (with-meta-check
                        (clojure.core.protocols/nav datafied :local-date nil))
            other-date (with-meta-check
                         (clojure.core.protocols/nav other :local-date nil))]
        (= this-date other-date)))))

(defn at-zone
  ""
  ([datafied zone-id]
   (at-zone datafied zone-id :instant))
  ([datafied zone-id same]
   (d/datafy
     (with-meta-check ;; has to be a fully qualified call
       (clojure.core.protocols/nav datafied :zone {:id zone-id :same same})))))

(defn at-offset
  ""
  ([datafied offset-id]
   (at-offset datafied offset-id :instant))
  ([datafied offset-id same]
   (d/datafy
     (with-meta-check ;; has to be a fully qualified call
       (clojure.core.protocols/nav datafied :offset {:id offset-id :same same})))))
