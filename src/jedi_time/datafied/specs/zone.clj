(ns jedi-time.datafied.specs.zone
  (:require [clojure.spec.alpha :as s])
  (:import (java.time ZoneId)))

(defonce valid-zone-ids
  (set (ZoneId/getAvailableZoneIds)))

(s/def ::id   valid-zone-ids)
(s/def ::same #{:instant :local})

(s/def ::zone
  (s/keys :req-un [::id]
          :opt-un [::same]))
