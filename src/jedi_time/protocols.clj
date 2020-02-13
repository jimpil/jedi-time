(ns jedi-time.protocols)

(defprotocol IChronoComparable
  :extend-via-metadata true
  (before? [this other])
  (after?  [this other]))

(defprotocol IChronoShiftable
  :extend-via-metadata true
  (shift+ [this n unit safe?])
  (shift- [this n unit safe?]))
