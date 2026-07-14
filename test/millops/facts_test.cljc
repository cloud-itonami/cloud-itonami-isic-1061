(ns millops.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [millops.facts :as facts]))

;; ──────────────────────── Product Type Lookups ──────────────────────

(deftest product-type-by-id-test
  (testing "white wheat flour product type exists"
    (let [p (facts/product-type-by-id :flour/wheat-white)]
      (is (some? p))
      (is (= (:id p) :flour/wheat-white))
      (is (= (:moisture-target-percent p) 14.0))
      (is (= (:mycotoxin-max-ppb p) 1000))))

  (testing "corn meal product type exists"
    (let [p (facts/product-type-by-id :meal/corn)]
      (is (some? p))
      (is (= (:ash-content-min-percent p) 0.40))
      (is (= (:mycotoxin-max-ppb p) 20))))

  (testing "nonexistent product type returns nil"
    (is (nil? (facts/product-type-by-id :flour/nonexistent)))))

;; ──────────────────────── Jurisdiction Lookups ──────────────────────

(deftest jurisdiction-by-id-test
  (testing "JP prefectural jurisdiction exists"
    (let [j (facts/jurisdiction-by-id :jp/prefectural)]
      (is (some? j))
      (is (true? (:allergen-declaration-required j)))
      (is (contains? (:major-allergens j) :wheat))))

  (testing "US FDA jurisdiction exists"
    (let [j (facts/jurisdiction-by-id :us/fda)]
      (is (some? j))
      (is (contains? (:major-allergens j) :sesame))))

  (testing "EU EFSA jurisdiction includes mustard as a major allergen"
    (let [j (facts/jurisdiction-by-id :eu/efsa)]
      (is (some? j))
      (is (contains? (:major-allergens j) :mustard))))

  (testing "nonexistent jurisdiction returns nil"
    (is (nil? (facts/jurisdiction-by-id :xx/unknown)))))

;; ──────────────────────── Allergen Lookups ──────────────────────

(deftest grain-source-allergens-test
  (testing "hard red wheat has wheat allergen"
    (let [a (facts/grain-source-allergens :wheat/hard-red)]
      (is (= (:primary-allergen a) :wheat))))

  (testing "durum wheat has wheat allergen"
    (let [a (facts/grain-source-allergens :durum/amber)]
      (is (= (:primary-allergen a) :wheat))))

  (testing "hulled oat has no primary allergen but carries wheat cross-contact risk"
    (let [a (facts/grain-source-allergens :oat/hulled)]
      (is (nil? (:primary-allergen a)))
      (is (contains? (:cross-contact-risk a) :wheat))))

  (testing "nonexistent grain source returns nil"
    (is (nil? (facts/grain-source-allergens :unknown/grain)))))

;; ──────────────────────── Milling Safety Predicates ──────────────────────

(deftest moisture-in-range-test
  (testing "moisture within tolerance passes"
    (let [p (facts/product-type-by-id :flour/wheat-white)]
      (is (true? (facts/moisture-in-range? 14.0 p)))))

  (testing "moisture at lower tolerance boundary passes"
    (let [p (facts/product-type-by-id :flour/wheat-white)]
      (is (true? (facts/moisture-in-range? 13.5 p)))))

  (testing "moisture below range fails"
    (let [p (facts/product-type-by-id :flour/wheat-white)]
      (is (false? (facts/moisture-in-range? 13.0 p)))))

  (testing "moisture above range fails"
    (let [p (facts/product-type-by-id :flour/wheat-white)]
      (is (false? (facts/moisture-in-range? 15.0 p))))))

(deftest ash-content-in-range-test
  (testing "ash content within range passes"
    (let [p (facts/product-type-by-id :flour/wheat-white)]
      (is (true? (facts/ash-content-in-range? 0.43 p)))))

  (testing "ash content below minimum fails"
    (let [p (facts/product-type-by-id :flour/wheat-white)]
      (is (false? (facts/ash-content-in-range? 0.30 p)))))

  (testing "ash content above maximum fails"
    (let [p (facts/product-type-by-id :flour/wheat-white)]
      (is (false? (facts/ash-content-in-range? 0.60 p))))))

(deftest granulation-in-range-test
  (testing "granulation within range passes"
    (let [p (facts/product-type-by-id :flour/wheat-white)]
      (is (true? (facts/granulation-in-range? 135 p)))))

  (testing "granulation below minimum fails"
    (let [p (facts/product-type-by-id :flour/wheat-white)]
      (is (false? (facts/granulation-in-range? 90 p)))))

  (testing "granulation above maximum fails"
    (let [p (facts/product-type-by-id :flour/wheat-white)]
      (is (false? (facts/granulation-in-range? 180 p))))))

(deftest mycotoxin-in-range-test
  (testing "mycotoxin at or below the max passes"
    (let [p (facts/product-type-by-id :meal/corn)]
      (is (true? (facts/mycotoxin-in-range? 20 p)))
      (is (true? (facts/mycotoxin-in-range? 5 p)))))

  (testing "mycotoxin above the max fails"
    (let [p (facts/product-type-by-id :meal/corn)]
      (is (false? (facts/mycotoxin-in-range? 25 p))))))

;; ──────────────────────── Allergen Traceability ──────────────────────

(deftest grain-source-allergen-set-test
  (testing "wheat-only formulation collects wheat allergen"
    (let [grain-sources [:wheat/hard-red]
          allergens (facts/grain-source-allergen-set grain-sources)]
      (is (contains? allergens :wheat))))

  (testing "blended formulation includes multiple allergens"
    (let [grain-sources [:wheat/hard-red :soy/whole-bean :almond/whole]
          allergens (facts/grain-source-allergen-set grain-sources)]
      (is (contains? allergens :wheat))
      (is (contains? allergens :soy))
      (is (contains? allergens :tree-nuts))))

  (testing "allergen-free grain sources produce empty set"
    (let [grain-sources [:corn/yellow-dent :rice/long-grain]
          allergens (facts/grain-source-allergen-set grain-sources)]
      (is (empty? allergens))))

  (testing "oat alone contributes no primary allergen (only cross-contact risk, informational)"
    (let [grain-sources [:oat/hulled]
          allergens (facts/grain-source-allergen-set grain-sources)]
      (is (empty? allergens)))))

(deftest allergen-declaration-complete-test
  (testing "declaration matches formulation for jurisdiction"
    (let [j (facts/jurisdiction-by-id :jp/prefectural)
          grain-sources [:wheat/hard-red]
          declared #{:wheat}]
      (is (true? (facts/allergen-declaration-complete? j grain-sources declared)))))

  (testing "incomplete declaration fails"
    (let [j (facts/jurisdiction-by-id :jp/prefectural)
          grain-sources [:wheat/hard-red :soy/whole-bean]
          declared #{:wheat}]
      (is (false? (facts/allergen-declaration-complete? j grain-sources declared)))))

  (testing "extra declarations pass (conservative)"
    (let [j (facts/jurisdiction-by-id :jp/prefectural)
          grain-sources [:wheat/hard-red]
          declared #{:wheat :soy}]
      (is (true? (facts/allergen-declaration-complete? j grain-sources declared))))))

;; ──────────────────────── Evidence Completeness ──────────────────────

(deftest required-evidence-satisfied-test
  (testing "complete evidence checklist passes"
    (let [j (facts/jurisdiction-by-id :jp/prefectural)
          evidence [:grain-intake-record :milling-log :moisture-test
                    :ash-content-test :mycotoxin-test :allergen-declaration :weight-check]]
      (is (true? (facts/required-evidence-satisfied? j evidence)))))

  (testing "incomplete evidence fails"
    (let [j (facts/jurisdiction-by-id :jp/prefectural)
          evidence [:grain-intake-record :milling-log]]
      (is (false? (facts/required-evidence-satisfied? j evidence))))))
