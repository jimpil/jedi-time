(ns jedi-time.datafied.batteries
  (:require [jedi-time.protocols :as jp]
            [jedi-time.core :as core]
            [clojure.datafy :as d]
            [clojure.core.protocols :as p]))

(defmacro ^:private with-meta-check
  [[f datafied & more]]
  `(if (contains? (meta ~datafied) ~f)
     (~f ~datafied ~@more)
     (-> ~datafied core/redatafy (~f ~@more))))


(defn shift+
  ""
  ([datafied by]
   (shift+ datafied by true))
  ([datafied [n unit] safe?]
   (d/datafy
     (with-meta-check
       (jp/shift+ datafied n unit safe?)))))

(defn shift-
  ""
  ([datafied by]
   (shift- datafied by true))
  ([datafied [n unit] safe?]
   (d/datafy
     (with-meta-check
       (jp/shift- datafied n unit safe?)))))

(defn before?
  ""
  [datafied other]
  (with-meta-check
    (jp/before? datafied other)))

(defn after?
  ""
  [datafied other]
  (with-meta-check
    (jp/after? datafied other)))

(defn at-zone
  ""
  ([datafied zone-id]
   (at-zone datafied zone-id :instant))
  ([datafied zone-id same]
   (d/datafy
     (with-meta-check
       (p/nav datafied :zone {:id zone-id :same same})))))

(defn at-offset
  ""
  ([datafied offset-id]
   (at-offset datafied offset-id :instant))
  ([datafied offset-id same]
   (d/datafy
     (with-meta-check
       (p/nav datafied :offset {:id offset-id :same same})))))
