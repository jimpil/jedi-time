(ns jedi-time.datafied.specs.offset
  (:require [clojure.spec.alpha :as s])
  (:import (java.time ZoneOffset DateTimeException)))

(defn valid-offset?
  [^String offset-id]
  (try (ZoneOffset/of offset-id)
       true
       (catch DateTimeException _ false)))

(s/def ::id valid-offset?)
(s/def ::seconds nat-int?)
(s/def ::hours   (some-fn pos-int? zero?))

(s/def ::same #{:instant :local})

(s/def ::offset
  (s/keys :req-un [::id]
          ;; offset seconds & hours not needed for `undatafy`
          :opt-un [::seconds
                   ::hours
                   ::same]))
