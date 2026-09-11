(ns millops.phase
  "Phase machine: the states a grain-mill batch transits through.

  State machine:
    :intake -> :clean -> :mill -> :inspect -> :package -> :audit -> :archived

  `:intake` is grain receiving; `:clean` is grain cleaning/destoning/
  tempering (conditioning moisture ahead of the roller mills); `:mill` is
  the milling/grinding run itself; `:inspect` is quality inspection
  (moisture/ash-content/mycotoxin/granulation/foreign-material); `:package`
  is finished-product packaging; `:audit` is compliance audit;
  `:archived` is the terminal state.

  Each transition can accept a proposal and yield an audit fact.")

(def all-phases
  "All valid phases in the grain-mill production workflow."
  [:intake :clean :mill :inspect :package :audit :archived])

(def phase-sequence
  "Ordered phases representing normal batch progression."
  [:intake :clean :mill :inspect :package :audit :archived])

(defn valid-phase?
  "Check if a phase is valid."
  [phase]
  (contains? (set all-phases) phase))

(defn- index-of
  "Portable (Clojure/ClojureScript) index lookup -- `.indexOf` is a
  JVM-only `java.util.List` method that ClojureScript's PersistentVector
  does not implement, so it is avoided here even though `phase-sequence`
  is a plain vector. Returns -1 when `x` is not found, matching
  `java.util.List/indexOf`'s contract."
  [coll x]
  (or (first (keep-indexed (fn [i v] (when (= v x) i)) coll)) -1))

(defn can-transition?
  "Check if a transition from one phase to another is valid
  (must be forward-only in the sequence, no backtracking). Always returns a
  boolean (never nil), including when either phase is invalid."
  [from-phase to-phase]
  (boolean
   (and (valid-phase? from-phase) (valid-phase? to-phase)
        (let [from-idx (index-of phase-sequence from-phase)
              to-idx (index-of phase-sequence to-phase)]
          (and (>= from-idx 0) (>= to-idx 0) (< from-idx to-idx))))))
