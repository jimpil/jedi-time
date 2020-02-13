(ns jedi-time.datafied.specs.format
  (:require [clojure.spec.alpha :as s])
  (:import (java.time.format DateTimeFormatter)))

(s/def ::default
  #{nil :iso :default :format/default})

(s/def ::format
  (s/or :default ::default
        :pattern string?
        :formatter (partial instance? DateTimeFormatter)))
