(ns millops.facts
  "Reference facts for grain-mill-products manufacturing: product-type
  milling parameters (moisture/ash-content/granulation/mycotoxin windows),
  jurisdiction allergen-declaration and evidence-checklist requirements,
  and per-grain-source allergen data. This namespace contains pure lookup
  functions for regulatory/food-safety compliance checks -- the Governor
  calls these to independently validate proposals; the advisor's
  confidence is never sufficient on its own."
  (:require [clojure.set :as set]))

(def product-types
  "Valid grain-mill product categories and their safe milling windows.
  `ash-content` is the mineral residue left after incineration -- a core
  milling-quality/purity indicator (higher ash indicates more bran/germ
  contamination in a supposedly refined flour). `granulation` is the
  target particle-size window in microns. `mycotoxin-max-ppb` is the
  maximum allowable mycotoxin level (parts per billion) for the product --
  deliberately per-product-type since corn products carry a much stricter
  aflatoxin action level than wheat products carry for deoxynivalenol
  (DON/vomitoxin)."
  {:flour/wheat-white
   {:id :flour/wheat-white
    :name "白小麦粉"
    :moisture-target-percent 14.0
    :moisture-tolerance-percent 0.5
    :ash-content-min-percent 0.38
    :ash-content-max-percent 0.48
    :granulation-min-microns 120
    :granulation-max-microns 150
    :mycotoxin-max-ppb 1000}

   :flour/whole-wheat
   {:id :flour/whole-wheat
    :name "全粒粉"
    :moisture-target-percent 13.5
    :moisture-tolerance-percent 0.5
    :ash-content-min-percent 1.40
    :ash-content-max-percent 1.90
    :granulation-min-microns 150
    :granulation-max-microns 200
    :mycotoxin-max-ppb 1000}

   :semolina/durum
   {:id :semolina/durum
    :name "デュラムセモリナ"
    :moisture-target-percent 14.5
    :moisture-tolerance-percent 0.5
    :ash-content-min-percent 0.70
    :ash-content-max-percent 0.90
    :granulation-min-microns 200
    :granulation-max-microns 400
    :mycotoxin-max-ppb 1000}

   :meal/corn
   {:id :meal/corn
    :name "コーンミール"
    :moisture-target-percent 12.0
    :moisture-tolerance-percent 0.5
    :ash-content-min-percent 0.40
    :ash-content-max-percent 0.70
    :granulation-min-microns 300
    :granulation-max-microns 600
    :mycotoxin-max-ppb 20}})

(defn product-type-by-id [id]
  (get product-types id))

(def jurisdictions
  "Grain-mill-products jurisdictions and their allergen-declaration and
  evidence-checklist requirements."
  {:jp/prefectural
   {:id :jp/prefectural
    :name "日本 (食品表示法・都道府県)"
    :allergen-declaration-required true
    :major-allergens #{:wheat :soy :tree-nuts :sesame}
    :required-evidence
    [:grain-intake-record
     :milling-log
     :moisture-test
     :ash-content-test
     :mycotoxin-test
     :allergen-declaration
     :weight-check]}

   :us/fda
   {:id :us/fda
    :name "United States (FDA/FALCPA)"
    :allergen-declaration-required true
    :major-allergens #{:wheat :soy :tree-nuts :sesame}
    :required-evidence
    [:grain-intake-record
     :milling-log
     :moisture-test
     :ash-content-test
     :mycotoxin-test
     :allergen-declaration
     :weight-check]}

   :eu/efsa
   {:id :eu/efsa
    :name "European Union (EFSA)"
    :allergen-declaration-required true
    :major-allergens #{:wheat :soy :tree-nuts :sesame :mustard}
    :required-evidence
    [:grain-intake-record
     :milling-log
     :moisture-test
     :ash-content-test
     :mycotoxin-test
     :allergen-declaration
     :weight-check]}})

(defn jurisdiction-by-id [id]
  (get jurisdictions id))

(def grain-source-allergen-table
  "Per-grain-source primary allergen and cross-contact risk, used to
  derive a milling batch's allergen set for label-accuracy verification.
  Gluten-bearing cereals (wheat/durum/rye/barley) all map to the :wheat
  allergen id for labeling purposes. `:oat/hulled` has no primary
  allergen of its own but carries a real-world `:wheat` cross-contact
  risk -- oats are frequently milled on shared lines with wheat, which is
  exactly why gluten-free oat milling requires dedicated equipment or
  verified line-clearance. Grain sources with no allergen relevance map
  to nil."
  {:wheat/hard-red    {:primary-allergen :wheat :cross-contact-risk #{}}
   :wheat/soft-white  {:primary-allergen :wheat :cross-contact-risk #{}}
   :durum/amber       {:primary-allergen :wheat :cross-contact-risk #{}}
   :rye/whole         {:primary-allergen :wheat :cross-contact-risk #{}}
   :barley/pearled    {:primary-allergen :wheat :cross-contact-risk #{}}
   :oat/hulled        {:primary-allergen nil :cross-contact-risk #{:wheat}}
   :soy/whole-bean    {:primary-allergen :soy :cross-contact-risk #{}}
   :almond/whole      {:primary-allergen :tree-nuts :cross-contact-risk #{}}
   :sesame/whole-seed {:primary-allergen :sesame :cross-contact-risk #{}}
   :corn/yellow-dent  nil
   :rice/long-grain   nil})

(defn grain-source-allergens [id]
  (get grain-source-allergen-table id))

(defn grain-source-allergen-set
  "Given a milling batch's grain-source-id list, return the set of
  primary allergens actually present. Non-allergenic / unknown
  grain-source ids contribute nothing."
  [grain-sources]
  (into #{}
        (keep (fn [id] (:primary-allergen (grain-source-allergens id))))
        grain-sources))

(defn allergen-declaration-complete?
  "Verify that `declared` allergens are a superset of the batch's actual
  allergens for `grain-sources`. Extra (conservative) declarations pass;
  omissions fail. `jurisdiction` is accepted for call-site symmetry with
  other facts lookups."
  [_jurisdiction grain-sources declared]
  (set/subset? (grain-source-allergen-set grain-sources) (set declared)))

(defn required-evidence-satisfied?
  "Verify that every item in the jurisdiction's `:required-evidence` list
  is present in `evidence`. `jurisdiction` may be a resolved jurisdiction
  map (as returned by `jurisdiction-by-id`) or a raw jurisdiction id --
  both call conventions are in use (tests pass a resolved map; the
  Governor passes the raw id straight off batch metadata)."
  [jurisdiction evidence]
  (let [j (if (map? jurisdiction) jurisdiction (jurisdiction-by-id jurisdiction))]
    (if-not j
      false
      (set/subset? (set (:required-evidence j)) (set evidence)))))

(defn moisture-in-range?
  "Positive-sense convenience predicate: does `percent` fall within
  `product`'s moisture tolerance window (inclusive) around its target?
  Grain-mill products must stay within a narrow moisture band -- too high
  risks mold/spoilage in storage, too low degrades milling yield and
  product texture."
  [percent product]
  (boolean
   (and (some? product)
        (let [target (:moisture-target-percent product)
              tol (:moisture-tolerance-percent product)]
          (and (>= percent (- target tol))
               (<= percent (+ target tol)))))))

(defn ash-content-in-range?
  "Positive-sense convenience predicate: does `percent` fall within
  `product`'s expected ash-content window (inclusive)?"
  [percent product]
  (boolean
   (and (some? product)
        (>= percent (:ash-content-min-percent product))
        (<= percent (:ash-content-max-percent product)))))

(defn granulation-in-range?
  "Positive-sense convenience predicate: does `microns` fall within
  `product`'s expected particle-size window (inclusive)?"
  [microns product]
  (boolean
   (and (some? product)
        (>= microns (:granulation-min-microns product))
        (<= microns (:granulation-max-microns product)))))

(defn mycotoxin-in-range?
  "Positive-sense convenience predicate: does `ppb` stay at or below
  `product`'s maximum allowable mycotoxin level?"
  [ppb product]
  (boolean
   (and (some? product)
        (<= ppb (:mycotoxin-max-ppb product)))))
