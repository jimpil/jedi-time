(ns jedi-time.datafied.specs.year
  (:require [clojure.spec.alpha :as s]))

(s/def ::value   pos-int?)
(s/def ::length  #(<= 1 % 366))
(s/def ::leap?  boolean?)

(s/def ::year
  (s/keys :req-un [::leap? ::value ::length]))
