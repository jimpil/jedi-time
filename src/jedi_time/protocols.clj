(ns jedi-time.protocols)

(defprotocol IChronoComparable
  :extend-via-metadata true
  (before? [this other])
  (after?  [this other]))

(defprotocol IChronoTranslatable
  :extend-via-metadata true
  (at-zone   [this zone-id mode])
  (at-offset [this offset-id mode]))

(defprotocol IChronoFormatable
  :extend-via-metadata true
  (format-as [this fmt]))

(defprotocol IChronoShiftable
  :extend-via-metadata true
  (shift+ [this n unit safe?])
  (shift- [this n unit safe?]))
