(ns jedi-time.protocols
  "Mirroring `clojure.core.protocols/Datafiable` & `clojure.core.protocols/Navigable`.")

(defprotocol Datafiable
  :extend-via-metadata true
  (datafy [x]))

(defprotocol Navigable
  :extend-via-metadata true
  (nav [x k v]))

(extend-protocol Datafiable
  nil
  (datafy [_] nil)

  Object
  (datafy [x] x))

(extend-protocol Navigable
  Object
  (nav [_ _ x] x))
