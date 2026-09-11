(ns millops.registry
  "Pure validation functions for grain-mill-products production parameters.
  These are called by the Governor to independently verify
  physical/operational constraints -- the advisor's confidence is NOT
  sufficient to override these checks.

  All functions here are pure arithmetic/set/boolean predicates with no
  host-clock or I/O calls, so this namespace stays trivially portable
  across Clojure/ClojureScript. Callers that need the current time (see
  `magnet-calibration-overdue?`) obtain it themselves via a `:clj`/`:cljs`
  reader-conditional at the call site (see `millops.governor`)."
  (:require [clojure.set :as set]))

(defn moisture-out-of-target?
  "Independently verify that the batch's finished-product moisture falls
  within tolerance of the product's target moisture. Grain-mill products
  outside their moisture window risk mold growth in storage (too high) or
  degraded milling yield/texture (too low)."
  [actual-percent target-percent tolerance-percent]
  (or (< actual-percent (- target-percent tolerance-percent))
      (> actual-percent (+ target-percent tolerance-percent))))

(defn mycotoxin-level-exceeded?
  "Independently verify that the batch's actual mycotoxin level (ppb) does
  not exceed the product's maximum allowable level. Mycotoxin
  contamination (aflatoxin, deoxynivalenol/DON, ochratoxin) is one of the
  most serious food-safety hazards specific to grain milling -- levels
  above the regulatory action level are a hard, un-overridable stop."
  [actual-ppb max-ppb]
  (> actual-ppb max-ppb))

(defn ash-content-out-of-range?
  "Independently verify that the batch's ash content (mineral residue
  after incineration, a core milling-purity indicator) falls within the
  product's expected range [min,max]. Both bounds are inclusive of the
  in-range case; out-of-range indicates bran/germ contamination (too
  high) or a mislabeled/misclassified product grade (too low or too
  high)."
  [actual-percent min-percent max-percent]
  (or (< actual-percent min-percent)
      (> actual-percent max-percent)))

(defn granulation-out-of-range?
  "Independently verify that the batch's particle-size distribution
  (microns) falls within the product's expected range. Granulation
  outside range indicates a roll-gap/sifter fault and risks
  misclassifying the product grade (e.g. flour milled to semolina
  coarseness, or vice versa)."
  [actual-microns min-microns max-microns]
  (or (< actual-microns min-microns)
      (> actual-microns max-microns)))

(defn magnet-calibration-overdue?
  "Independently verify that the magnetic/metal-detection equipment
  (catches tramp metal before it reaches the mill rolls or the finished
  product) was calibrated within the last 90 days.
  `last-calibration-epoch-ms` and `now-epoch-ms` are both epoch
  milliseconds -- callers obtain `now` via a `:clj`/`:cljs`
  reader-conditional, keeping this namespace free of any host-clock
  call. A shorter interval than a generic scale calibration (bakery
  actors use 180 days) reflects the higher consequence of a missed
  metal-detection fault: tramp metal reaching a finished consumer
  product."
  [last-calibration-epoch-ms now-epoch-ms]
  (> (- now-epoch-ms last-calibration-epoch-ms)
     (* 90 24 60 60 1000)))

(defn weight-variance-excessive?
  "Independently verify that a batch's finished-product weight variance
  (drift from target, in grams) does not exceed the maximum tolerance.
  Excessive variance indicates the packaging scale is out of calibration
  or the milling yield was measured incorrectly."
  [actual-variance-grams max-variance-grams]
  (> actual-variance-grams max-variance-grams))

(defn allergen-label-risk?
  "True when the batch's grain-source formulation contains an allergen
  NOT present in the declared-allergens set (mislabeling /
  under-declaration risk -- a genuine food-safety hazard for allergic
  consumers, and especially for gluten-free-labeled oat products milled
  on shared lines). Declaring MORE allergens than the batch actually
  contains is conservative and never a risk."
  [formula-allergens declared-allergens]
  (not (set/subset? (set formula-allergens) (set declared-allergens))))

(defn foreign-material-detected?
  "Independently verify a batch's foreign-material-detection result
  (metal, stone, glass, or insect fragments caught by magnet/sifter/
  optical-sorter inspection). Any detection is a genuine physical hazard
  -- this predicate simply coerces the raw fact to a boolean so the
  Governor's check functions stay uniform in shape with every other
  independently-verified physical constraint in this namespace."
  [actual-detected?]
  (boolean actual-detected?))

(defn sanitation-score-insufficient?
  "Independently verify that the plant's pre-production sanitation/
  pest-control score meets the minimum required. Score is 0-100, assessed
  by a third-party auditor against food-safety sanitation and
  rodent/insect infestation-control standards (a significant HACCP
  concern specific to bulk grain storage and milling)."
  [actual-score min-score-required]
  (< actual-score min-score-required))
