(ns jedi-time.datafied
  (:require [jedi-time.protocols :as jp]
            [jedi-time.core :as core]
            [clojure.datafy :as d]))

(defmacro ^:private try-with-redatafy
  [[f datafied & more]]
  `(try
     (~f ~datafied ~@more)
     (catch IllegalArgumentException _#
       (-> ~datafied core/redatafy (~f ~@more)))))


(defn format-dt
  ""
  ^String [datafied fmt]
  (try-with-redatafy
    (jp/format-as datafied fmt)))

(defn shift+
  ""
  ([datafied by]
   (shift+ datafied by true))
  ([datafied [n unit] safe?]
   (d/datafy
     (try-with-redatafy
       (jp/shift+ datafied n unit safe?)))))

(defn shift-
  ""
  ([datafied by]
   (shift- datafied by true))
  ([datafied [n unit] safe?]
   (d/datafy
     (try-with-redatafy
       (jp/shift- datafied n unit safe?)))))

(defn before?
  ""
  [datafied other]
  (try-with-redatafy
    (jp/before? datafied other)))

(defn after?
  ""
  [datafied other]
  (try-with-redatafy
    (jp/after? datafied other)))

(defn at-zone
  ""
  ([datafied zone-id]
   (at-zone datafied zone-id :same-instant))
  ([datafied zone-id mode]
   (d/datafy
     (try-with-redatafy
       (jp/at-zone datafied zone-id mode)))))

(defn at-offset
  ""
  ([datafied offset-id]
   (at-offset datafied offset-id :same-instant))
  ([datafied offset-id mode]
   (d/datafy
     (try-with-redatafy
       (jp/at-offset datafied offset-id mode)))))
