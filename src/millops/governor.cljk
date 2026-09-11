(ns millops.governor
  "Mill Governor -- the independent compliance layer that earns the
  MillOpsAdvisor the right to commit. The LLM has no notion of:
    - Whether a batch's finished-product moisture stayed within its safe
      storage window
    - Whether the batch's mycotoxin level (aflatoxin/DON/ochratoxin)
      exceeds the product's regulatory action level
    - Whether ash content falls within the product's purity/grade window
    - Whether particle-size (granulation) falls within the product's
      grade window
    - Whether foreign material (metal/stone/glass/insect fragments) was
      detected in the batch
    - Whether the magnet/metal-detection equipment's calibration is
      current
    - Whether final product weight variance is acceptable
    - Whether allergen labeling (esp. gluten cross-contact from
      shared-line oat milling) is complete and accurate
    - Whether sanitation/pest-control verification is passed
    - Whether an open food-safety concern has been resolved

  This MUST be a separate system able to *reject* a proposal and fall back
  to HOLD.

  Unlike direct milling-line control (NEVER done by this actor -- roller
  mill, sifter, and purifier operation remain exclusive to plant staff),
  the Governor operates on batch metadata: provenance, milling parameters,
  sanitation records, and food-safety flags. This is plant-operations
  coordination, not process control.

  CRITICAL: Any proposal involving food-safety concerns (mycotoxin
  contamination, foreign-material detection, allergen mislabeling,
  sanitation failures) ALWAYS escalates to human operator for final
  sign-off. The LLM's confidence is never sufficient for food-safety
  decisions.

  Hard violations (always HOLD, no override):
    1. No jurisdiction citation (jurisdiction unknown -> can't verify reqs)
    2. Evidence incomplete (missing required-evidence per jurisdiction)
    3. Moisture out of target range (storage/quality safety)
    4. Mycotoxin level exceeds the product's regulatory action level
    5. Ash content out of range (milling purity/grade)
    6. Granulation (particle size) out of range (grade misclassification)
    7. Foreign material detected (metal/stone/glass/insect fragments)
    8. Magnet/metal-detection calibration overdue
    9. Weight variance excessive (packaging scale drift risk)
   10. Allergen labeling mismatch (food-safety / labeling violation)
   11. Plant sanitation/pest-control score insufficient
   12. Food-safety flag unresolved (open concern, escalate required)

  Soft gates (always escalate for human):
    - Low confidence
    - Real actuation (`:log-production-batch`, `:coordinate-shipment`)
    - `:flag-food-safety-concern` (never auto-resolved by confidence alone)

  This design mirrors `bakeryops.governor` and `dairyops.governor` but
  specializes on grain-mill-specific concerns: mycotoxin contamination,
  ash content/granulation grading, and magnet/metal-detection integrity --
  rather than baking temperature/time or cold-chain management."
  (:require [millops.facts :as facts]
            [millops.registry :as registry]
            [millops.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Logging a batch into production records (`:log-production-batch`) and
  coordinating shipment of finished product (`:coordinate-shipment`) are
  the two real-world actuation events this actor performs. Both require
  plant operator sign-off."
  #{:log-production-batch :coordinate-shipment})

(def always-escalate-ops
  "Operations that always require human sign-off, even when the Governor's
  hard checks are clean and confidence is high: the two high-stakes
  actuation events (`high-stakes`) plus `:flag-food-safety-concern` --
  a food-safety concern (mycotoxin contamination, foreign-material
  detection) is never auto-resolved by advisor confidence alone, it
  always needs a human look."
  (conj high-stakes :flag-food-safety-concern))

(def allowed-ops
  "Closed allowlist of proposal operations this actor may ever make. Any
  proposal for an operation outside this set -- most importantly direct
  milling-line control (roller mill/sifter/purifier operation) or
  food-safety certification authority -- is a hard, permanent block: this
  actor coordinates plant operations, it does not operate equipment and
  it does not certify food safety."
  #{:log-production-batch :schedule-maintenance :flag-food-safety-concern :coordinate-shipment})

;; ────────────────────────── Checks ──────────────────────────

(defn- op-not-allowed-violations
  "HARD, permanent block: any proposal outside the closed operation
  allowlist (e.g. direct milling-line control, or a food-safety
  certification action) is refused unconditionally -- this actor has no
  authority to make such a proposal at all, let alone commit it."
  [{:keys [op]} _proposal]
  (when-not (contains? allowed-ops op)
    [{:rule :op-not-allowed
      :detail (str op " はこのactorの許可された提案種別 (log-production-batch/"
                  "schedule-maintenance/flag-food-safety-concern/coordinate-shipment) "
                  "に含まれない -- milling-line制御やfood-safety認証権限はこのactorに無い")}]))

(defn- effect-not-propose-violations
  "HARD invariant: this actor's proposals are always `:effect :propose` --
  it never claims direct write/actuation authority for itself. A proposal
  asserting any other effect is refused unconditionally."
  [_request proposal]
  (when-let [effect (:effect proposal)]
    (when (not= effect :propose)
      [{:rule :effect-not-propose
        :detail (str "この actor の提案は :propose 以外の :effect を持てない (got " effect ")")}])))

(defn- shipment-batch-not-registered-violations
  "HARD invariant: a plant/batch record must be verified/registered in the
  store before `:coordinate-shipment` can be proposed against it --
  coordinating shipment of a batch this plant never checked in is out of
  scope for this actor."
  [{:keys [op subject]} st]
  (when (= op :coordinate-shipment)
    (when-not (store/production-batch st subject)
      [{:rule :batch-not-registered
        :detail (str subject " はプラントに登録されたバッチ記録が無い -- 出荷調整提案は進められない")}])))

(defn- spec-basis-violations
  "A proposal with no jurisdiction citation is a HARD violation -- never
  invent a jurisdiction's food-safety requirements."
  [{:keys [op]} proposal]
  (when (contains?
         #{:log-production-batch :coordinate-shipment :flag-food-safety-concern}
         op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :jurisdiction) (nil? (:jurisdiction value))))
        [{:rule :no-spec-basis
          :detail "公式仕様の引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:log-production-batch`, verify the batch's evidence checklist is
  complete per jurisdiction requirements."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/production-batch st subject)]
      (when-not (and b
                     (facts/required-evidence-satisfied?
                      (:jurisdiction b)
                      (:evidence-checklist b)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(grain-intake-record/milling-log/moisture-test/ash-content-test/mycotoxin-test等)が充足していない状態での提案"}]))))

(defn- moisture-out-of-target-violations
  "For `:log-production-batch`, INDEPENDENTLY verify that the batch's
  finished-product moisture falls within tolerance via
  `registry/moisture-out-of-target?`. Evaluated UNCONDITIONALLY."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/production-batch st subject)
          p (when b (facts/product-type-by-id (:product-type b)))]
      (when (and b p (:moisture-percent b)
                 (registry/moisture-out-of-target?
                  (:moisture-percent b)
                  (:moisture-target-percent p)
                  (:moisture-tolerance-percent p)))
        [{:rule :moisture-out-of-target
          :detail (str subject " の水分(" (:moisture-percent b)
                      "%)が目標範囲外 -- バッチ登録提案は進められない")}]))))

(defn- mycotoxin-level-exceeded-violations
  "For `:log-production-batch`, INDEPENDENTLY verify that the batch's
  mycotoxin level stays at or below the product's maximum allowable ppb
  via `registry/mycotoxin-level-exceeded?`. Evaluated UNCONDITIONALLY --
  this is the single most serious food-safety hazard specific to grain
  milling."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/production-batch st subject)
          p (when b (facts/product-type-by-id (:product-type b)))]
      (when (and b p (:mycotoxin-ppb b)
                 (registry/mycotoxin-level-exceeded?
                  (:mycotoxin-ppb b)
                  (:mycotoxin-max-ppb p)))
        [{:rule :mycotoxin-level-exceeded
          :detail (str subject " のマイコトキシン濃度(" (:mycotoxin-ppb b)
                      " ppb)が規制上限(" (:mycotoxin-max-ppb p)
                      " ppb)を超過 -- バッチ登録提案は進められない")}]))))

(defn- ash-content-out-of-range-violations
  "For `:log-production-batch`, INDEPENDENTLY verify that the batch's ash
  content falls within the product's expected range via
  `registry/ash-content-out-of-range?`."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/production-batch st subject)
          p (when b (facts/product-type-by-id (:product-type b)))]
      (when (and b p (:ash-content-percent b)
                 (registry/ash-content-out-of-range?
                  (:ash-content-percent b)
                  (:ash-content-min-percent p)
                  (:ash-content-max-percent p)))
        [{:rule :ash-content-out-of-range
          :detail (str subject " の灰分(" (:ash-content-percent b)
                      "%)が製品規格範囲外 -- バッチ登録提案は進められない")}]))))

(defn- granulation-out-of-range-violations
  "For `:log-production-batch`, INDEPENDENTLY verify that the batch's
  particle-size (granulation) falls within the product's expected range
  via `registry/granulation-out-of-range?`."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/production-batch st subject)
          p (when b (facts/product-type-by-id (:product-type b)))]
      (when (and b p (:granulation-microns b)
                 (registry/granulation-out-of-range?
                  (:granulation-microns b)
                  (:granulation-min-microns p)
                  (:granulation-max-microns p)))
        [{:rule :granulation-out-of-range
          :detail (str subject " の粒度(" (:granulation-microns b)
                      "μm)が製品規格範囲外 -- バッチ登録提案は進められない")}]))))

(defn- foreign-material-detected-violations
  "For `:log-production-batch`, INDEPENDENTLY verify the batch's own
  foreign-material-detection result via `registry/foreign-material-
  detected?`. A detection on THIS batch's own inspection is a hard,
  physical-hazard block -- distinct from `food-safety-flag-unresolved-
  violations` below, which covers a separately-raised, not-yet-resolved
  concern."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/production-batch st subject)]
      (when (and b (registry/foreign-material-detected? (:foreign-material-detected? b)))
        [{:rule :foreign-material-detected
          :detail (str subject " で異物(金属/石/ガラス/昆虫混入)が検出された -- バッチ登録提案は進められない")}]))))

(defn- now-epoch-ms
  "Current time in epoch milliseconds, portable across Clojure/
  ClojureScript. Isolated to this single call site so the rest of the
  namespace (and all of `millops.registry`) stays free of host-clock
  calls."
  []
  #?(:clj (System/currentTimeMillis)
     :cljs (js/Date.now)))

(defn- magnet-calibration-overdue-violations
  "For `:log-production-batch`, INDEPENDENTLY verify that the
  magnet/metal-detection equipment's calibration is current (recalibration
  required every 90 days)."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/production-batch st subject)]
      (when (and b (:magnet-last-calibration-date b)
                 (registry/magnet-calibration-overdue? (:magnet-last-calibration-date b) (now-epoch-ms)))
        [{:rule :magnet-calibration-overdue
          :detail (str subject " の異物検出機(マグネット)校正が期限切れ -- バッチ登録提案は進められない")}]))))

(defn- weight-variance-excessive-violations
  "For `:log-production-batch`, INDEPENDENTLY verify the weight variance."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/production-batch st subject)]
      (when (and b (:weight-variance-grams b)
                 (registry/weight-variance-excessive? (:weight-variance-grams b) 50))
        [{:rule :weight-variance-excessive
          :detail (str subject " の重量分散(" (:weight-variance-grams b)
                      "g)が許容範囲(50g)を超過 -- バッチ登録提案は進められない")}]))))

(defn- allergen-label-mismatch-violations
  "For `:log-production-batch`, INDEPENDENTLY verify allergen declaration
  completeness and accuracy via `registry/allergen-label-risk?`."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/production-batch st subject)
          formula-allergens (facts/grain-source-allergen-set (:grain-sources b))]
      (when (and b formula-allergens (:declared-allergens b)
                 (registry/allergen-label-risk? formula-allergens (:declared-allergens b)))
        [{:rule :allergen-label-mismatch
          :detail (str subject " のアレルゲン宣言が不完全 -- バッチ登録提案は進められない")}]))))

(defn- sanitation-score-insufficient-violations
  "For `:log-production-batch`, INDEPENDENTLY verify that the plant's
  sanitation/pest-control score meets minimum requirements."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/production-batch st subject)]
      (when (and b (:sanitation-score b)
                 (registry/sanitation-score-insufficient? (:sanitation-score b) 75))
        [{:rule :sanitation-score-insufficient
          :detail (str subject " のプラント衛生/防虫スコア(" (:sanitation-score b)
                      ")が最低要件(75)を下回る -- バッチ登録提案は進められない")}]))))

(defn- food-safety-flag-unresolved-violations
  "An unresolved food-safety flag is a HARD, un-overridable hold.
  Food-safety concerns (suspected mycotoxin contamination, foreign
  material, allergen cross-contact) raised during production or
  inspection MUST be resolved before the batch can be logged. Evaluated
  UNCONDITIONALLY at `:log-production-batch`."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (let [b (store/production-batch st subject)]
      (when (and (true? (:safety-concern-raised? b))
                 (not (true? (:safety-concern-resolved? b))))
        [{:rule :food-safety-flag-unresolved
          :detail (str subject " は未解決の食品安全フラグがある -- バッチ登録提案は進められない")}]))))

(defn- already-processed-violations
  "For `:log-production-batch`, refuse to process the SAME batch twice, off
  a dedicated `:processed?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (when (store/batch-already-processed? st subject)
      [{:rule :already-processed
        :detail (str subject " は既に登録済み")}])))

(defn- already-shipment-finalized-violations
  "For `:coordinate-shipment`, refuse to finalize the SAME batch's shipment
  twice, off a dedicated `:shipment-finalized?` fact."
  [{:keys [op subject]} st]
  (when (= op :coordinate-shipment)
    (when (store/batch-shipment-finalized? st subject)
      [{:rule :already-shipment-finalized
        :detail (str subject " は既に出荷確定済み")}])))

(defn check
  "Censors a MillOpsAdvisor proposal against the Governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}.

  Stakes (high-stakes actuation vs. always-escalate) are read off the
  REQUEST's `:op` -- not off the proposal -- since the operation being
  proposed (not the advisor's self-reported stake) is what determines
  whether a human must sign off."
  [request _context proposal st]
  (let [hard (into []
                   (concat (op-not-allowed-violations request proposal)
                           (effect-not-propose-violations request proposal)
                           (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (moisture-out-of-target-violations request st)
                           (mycotoxin-level-exceeded-violations request st)
                           (ash-content-out-of-range-violations request st)
                           (granulation-out-of-range-violations request st)
                           (foreign-material-detected-violations request st)
                           (magnet-calibration-overdue-violations request st)
                           (weight-variance-excessive-violations request st)
                           (allergen-label-mismatch-violations request st)
                           (sanitation-score-insufficient-violations request st)
                           (food-safety-flag-unresolved-violations request st)
                           (already-processed-violations request st)
                           (already-shipment-finalized-violations request st)
                           (shipment-batch-not-registered-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        actuation? (boolean (high-stakes (:op request)))
        escalate-op? (boolean (always-escalate-ops (:op request)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not escalate-op?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? escalate-op?))
     :high-stakes? actuation?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
