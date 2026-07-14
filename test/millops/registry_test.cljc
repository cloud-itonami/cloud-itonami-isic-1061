(ns millops.registry-test
  (:require [clojure.test :refer [deftest is testing]]
            [millops.registry :as registry]))

;; ──────────────────────── Moisture Target ──────────────────────

(deftest moisture-out-of-target-test
  (testing "moisture at target with no tolerance returns false"
    (is (false? (registry/moisture-out-of-target? 14.0 14.0 0.5))))

  (testing "moisture within tolerance range returns false"
    (is (false? (registry/moisture-out-of-target? 13.7 14.0 0.5))))

  (testing "moisture below tolerance returns true (violation)"
    (is (true? (registry/moisture-out-of-target? 13.0 14.0 0.5))))

  (testing "moisture above tolerance returns true (violation)"
    (is (true? (registry/moisture-out-of-target? 14.6 14.0 0.5)))))

;; ──────────────────────── Mycotoxin Level ──────────────────────

(deftest mycotoxin-level-exceeded-test
  (testing "level within limit returns false (no violation)"
    (is (false? (registry/mycotoxin-level-exceeded? 15 20))))

  (testing "level at limit returns false"
    (is (false? (registry/mycotoxin-level-exceeded? 20 20))))

  (testing "level exceeding limit returns true (violation)"
    (is (true? (registry/mycotoxin-level-exceeded? 21 20)))))

;; ──────────────────────── Ash Content ──────────────────────

(deftest ash-content-out-of-range-test
  (testing "ash content within range returns false (no violation)"
    (is (false? (registry/ash-content-out-of-range? 0.43 0.38 0.48))))

  (testing "ash content at minimum boundary returns false"
    (is (false? (registry/ash-content-out-of-range? 0.38 0.38 0.48))))

  (testing "ash content at maximum boundary returns false"
    (is (false? (registry/ash-content-out-of-range? 0.48 0.38 0.48))))

  (testing "ash content below minimum returns true (violation)"
    (is (true? (registry/ash-content-out-of-range? 0.30 0.38 0.48))))

  (testing "ash content above maximum returns true (violation)"
    (is (true? (registry/ash-content-out-of-range? 0.60 0.38 0.48)))))

;; ──────────────────────── Granulation ──────────────────────

(deftest granulation-out-of-range-test
  (testing "granulation within range returns false (no violation)"
    (is (false? (registry/granulation-out-of-range? 135 120 150))))

  (testing "granulation below minimum returns true (violation)"
    (is (true? (registry/granulation-out-of-range? 90 120 150))))

  (testing "granulation above maximum returns true (violation)"
    (is (true? (registry/granulation-out-of-range? 180 120 150)))))

;; ──────────────────────── Magnet Calibration ──────────────────────

(deftest magnet-calibration-overdue-test
  (testing "recent calibration returns false (no violation)"
    ;; Assume calibrated 30 days ago
    (let [now #?(:clj (System/currentTimeMillis) :cljs (.now js/Date))
          thirty-days-ago (- now (* 30 24 60 60 1000))]
      (is (false? (registry/magnet-calibration-overdue? thirty-days-ago now)))))

  (testing "overdue calibration returns true (violation)"
    (let [now #?(:clj (System/currentTimeMillis) :cljs (.now js/Date))
          hundred-days-ago (- now (* 100 24 60 60 1000))]
      (is (true? (registry/magnet-calibration-overdue? hundred-days-ago now))))))

;; ──────────────────────── Weight Variance ──────────────────────

(deftest weight-variance-excessive-test
  (testing "variance within tolerance returns false (no violation)"
    (is (false? (registry/weight-variance-excessive? 45 50))))

  (testing "variance at tolerance returns false"
    (is (false? (registry/weight-variance-excessive? 50 50))))

  (testing "variance exceeding tolerance returns true (violation)"
    (is (true? (registry/weight-variance-excessive? 51 50)))))

;; ──────────────────────── Allergen Labeling ──────────────────────

(deftest allergen-label-risk-test
  (testing "declared allergens match formulation returns false (no risk)"
    (let [formula #{:wheat :soy}
          declared #{:wheat :soy}]
      (is (false? (registry/allergen-label-risk? formula declared)))))

  (testing "declared allergens exceed formulation returns false (conservative)"
    (let [formula #{:wheat}
          declared #{:wheat :soy}]
      (is (false? (registry/allergen-label-risk? formula declared)))))

  (testing "formulation allergen undeclared returns true (risk)"
    (let [formula #{:wheat :soy}
          declared #{:wheat}]
      (is (true? (registry/allergen-label-risk? formula declared))))))

;; ──────────────────────── Foreign Material ──────────────────────

(deftest foreign-material-detected-test
  (testing "no detection returns false"
    (is (false? (registry/foreign-material-detected? false)))
    (is (false? (registry/foreign-material-detected? nil))))

  (testing "detection returns true"
    (is (true? (registry/foreign-material-detected? true)))))

;; ──────────────────────── Sanitation Score ──────────────────────

(deftest sanitation-score-insufficient-test
  (testing "score at minimum returns false (no violation)"
    (is (false? (registry/sanitation-score-insufficient? 75 75))))

  (testing "score above minimum returns false"
    (is (false? (registry/sanitation-score-insufficient? 85 75))))

  (testing "score below minimum returns true (violation)"
    (is (true? (registry/sanitation-score-insufficient? 74 75)))))
