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
   is before the <other> - false otherwise."
  [datafied other]
  (with-meta-check
    (jedi-time.protocols/before? datafied other)))

(defn after?
  "Returns true if <this> datafied representation
   is after the <other> - false otherwise."
  [datafied other]
  (with-meta-check ;; has to be a fully qualified call
    (jedi-time.protocols/after? datafied other)))

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
