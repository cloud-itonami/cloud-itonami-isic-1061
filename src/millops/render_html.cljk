(ns millops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2608090800 /
  ADR-2607189300): this repo previously had no demo page and no generator
  at all. This namespace drives the REAL actor stack
  (`millops.operation/run-operation` -> `millops.governor/check` ->
  `millops.store`) through a scenario and renders whatever that run
  actually produced. Every batch id, rule keyword, violation detail
  string, disposition and count on the page is read back off the store
  and its append-only ledger -- nothing on the page is hand-typed.

  What this repo's scaffold does and does not provide (stated plainly so
  the page is not read as claiming more than it is):

    * `millops.operation/run-operation` is a pure function, not a
      langgraph StateGraph actor -- unlike the langgraph-based siblings
      (`cloud-itonami-isic-9522`, `-0113`), `millops.advisor` is an empty
      skeleton and `millops.sim` still prints \"not yet implemented\", so
      `clojure -M:dev:run` yields no ledger and there is no `store/seed-db`
      to read demo subjects from. The scenario below therefore seeds its
      own batches (`seed-store`) from the repo's OWN reference data
      (`millops.facts/product-types`, `/jurisdictions`,
      `/grain-source-allergen-table`) and renders only those ids. No id
      appears on the page that this file did not put in the store.
    * `run-operation` collapses BOTH dispositions it can refuse into one
      `:governor-hold` fact (this repo's own `operation_test.cljc` notes
      it at the `run-operation-escalate-test` comment). A hard violation
      therefore differs from a soft escalation only by `:basis` being
      non-empty. The renderer keeps that distinction visible rather than
      papering over it, and `-main` refuses to write a console unless a
      real HARD hold (non-empty `:basis`) is on the ledger.
    * `millops.store` models batches and the ledger only -- it has no
      maintenance-record or shipment-document entity and no approver
      field. An approved proposal is therefore shown from the ledger
      timeline (where the approver is real) and NOT as an `:approved-by`
      column on a batch (where the store holds nothing).

  Determinism: no timestamps, no randomness, no network in the page.
  `:magnet-last-calibration-date` is seeded relative to the host clock
  (the Governor's 90-day recalibration check reads the host clock), but
  the raw epoch value is never rendered -- only the boolean the real
  `millops.registry/magnet-calibration-overdue?` predicate returns for
  it. Re-running writes a byte-identical file.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [kotoba.lang.text :as str]
            [jp-go-dds.skin]
            [millops.facts :as facts]
            [millops.governor :as governor]
            [millops.operation :as operation]
            [millops.registry :as registry]
            [millops.store :as store]))

;; ───────────────────────────── scenario seed ─────────────────────────────

(def ^:private operator
  "The human plant operator this demo run signs off as, and the actor id
  the Governor stamps onto every hold fact it writes."
  {:actor-id "millops-actor-1"
   :operator-id "plant-operator-01"
   :hold-fact-fn governor/hold-fact})

(def ^:private day-ms (* 24 60 60 1000))

(defn- days-ago [n]
  (- (System/currentTimeMillis) (* n day-ms)))

(def ^:private full-evidence
  "Every evidence item `:jp/prefectural` requires, read straight off
  `millops.facts/jurisdictions` so the seed can never drift away from
  the requirement list the Governor actually checks."
  (vec (:required-evidence (facts/jurisdiction-by-id :jp/prefectural))))

(def ^:private clean-white-flour
  "A batch that passes every one of the Governor's 17 hard checks. Each
  defective batch below is this map with exactly ONE field changed, so
  each hold on the page names exactly one rule."
  {:product-type :flour/wheat-white
   :jurisdiction :jp/prefectural
   :moisture-percent 14.0
   :ash-content-percent 0.43
   :granulation-microns 135
   :mycotoxin-ppb 100
   :foreign-material-detected? false
   :magnet-last-calibration-date (days-ago 12)
   :weight-variance-grams 20
   :grain-sources [:wheat/hard-red]
   :declared-allergens #{:wheat}
   :sanitation-score 85
   :evidence-checklist full-evidence})

(def ^:private clean-corn-meal
  (assoc clean-white-flour
         :product-type :meal/corn
         :moisture-percent 12.0
         :ash-content-percent 0.55
         :granulation-microns 420
         :mycotoxin-ppb 8
         :grain-sources [:corn/yellow-dent]
         :declared-allergens #{}))

(def ^:private clean-durum-semolina
  (assoc clean-white-flour
         :product-type :semolina/durum
         :moisture-percent 14.5
         :ash-content-percent 0.80
         :granulation-microns 300
         :grain-sources [:durum/amber]))

(def ^:private clean-whole-wheat
  (assoc clean-white-flour
         :product-type :flour/whole-wheat
         :moisture-percent 13.5
         :ash-content-percent 1.60
         :granulation-microns 175))

(def ^:private demo-batches
  "Ordered so the console's batch table is stable across runs (a plain
  map would not be). Each entry is [id batch note-for-the-page]."
  [["batch-101" clean-white-flour
    "clean -- carries the auto-commit / approval / double-commit path"]
   ["batch-102" (assoc clean-corn-meal :mycotoxin-ppb 45)
    "aflatoxin 45ppb against the corn action level of 20ppb"]
   ["batch-103" (assoc clean-durum-semolina :foreign-material-detected? true)
    "magnet/sifter inspection flagged tramp metal"]
   ["batch-104" (assoc clean-whole-wheat
                       :grain-sources [:wheat/hard-red :soy/whole-bean]
                       :declared-allergens #{:wheat})
    "soy in the grist, only wheat on the label"]
   ["batch-105" (assoc clean-white-flour :sanitation-score 62)
    "plant hygiene/pest-control audit below the floor of 75"]
   ["batch-106" (assoc clean-white-flour :moisture-percent 15.2)
    "finished moisture above the 14.0 +/- 0.5 storage window"]
   ["batch-107" (assoc clean-white-flour
                       :evidence-checklist (vec (remove #{:mycotoxin-test} full-evidence)))
    "mycotoxin test missing from the evidence checklist"]
   ["batch-108" (assoc clean-white-flour
                       :safety-concern-raised? true
                       :safety-concern-resolved? false)
    "open food-safety concern, not yet resolved"]
   ["batch-109" clean-white-flour
    "clean -- carries the operator-REJECTS path"]
   ["batch-110" (assoc clean-corn-meal :magnet-last-calibration-date (days-ago 200))
    "metal-detection calibration 200 days old (90-day interval)"]
   ["batch-111" (assoc clean-durum-semolina :ash-content-percent 1.20)
    "ash 1.20% against the durum purity window 0.70-0.90%"]
   ["batch-112" (assoc clean-durum-semolina :granulation-microns 480)
    "granulation 480um against the durum grade window 200-400um"]
   ["batch-113" (assoc clean-white-flour :weight-variance-grams 80)
    "packaging weight drift 80g against the 50g tolerance"]])

(def ^:private unregistered-batch-id
  "Never seeded -- exists only to prove `:batch-not-registered` fires."
  "batch-999")

(defn seed-store
  "A fresh store holding `demo-batches` and an empty ledger."
  []
  {:batches (into {} (map (fn [[id b _]] [id b])) demo-batches)
   :facts []})

;; ───────────────────────────── the demo run ─────────────────────────────

(defn- proposal
  "A `:propose`-effect advisor proposal. `spec` is the citation the
  advisor claims; pass `nil` to produce the un-cited proposal the
  `:no-spec-basis` hard gate exists to refuse."
  ([confidence spec] (proposal confidence spec :jp/prefectural))
  ([confidence spec jurisdiction]
   {:cites (if spec [{:spec spec}] [])
    :value {:jurisdiction jurisdiction}
    :effect :propose
    :confidence confidence}))

(defn- drive
  "Push ONE proposal through the real stack and fold whatever came back
  into the store.

  `run-operation` decides everything here: `:ok?` (clean and not a
  mandatory-escalation op) auto-commits, `(:hard? verdict)` is an
  un-overridable hold, and anything else is a soft escalation that a
  human must sign. The `:governor-hold` fact appended in the last two
  branches is the fact `millops.governor/hold-fact` itself built -- it
  is never reconstructed here. Only the `:committed` /
  `:approval-granted` / `:approval-denied` facts and the store mutation
  are this driver's own, because `millops.operation` has no commit path
  of its own yet (see the ns docstring)."
  [st {:keys [op subject proposal approval commit-fn]}]
  (let [request {:op op :subject subject}
        {:keys [ok? facts verdict]}
        (operation/run-operation request operator proposal st governor/check)
        st (reduce store/append-fact st facts)]
    (cond
      ok?
      (cond-> (store/append-fact st {:t :committed
                                     :op op
                                     :subject subject
                                     :actor (:actor-id operator)
                                     :disposition :auto-commit
                                     :confidence (:confidence proposal)})
        commit-fn (commit-fn subject))

      (:hard? verdict) st

      (= :approved approval)
      (cond-> (store/append-fact st {:t :approval-granted
                                     :op op
                                     :subject subject
                                     :by (:operator-id operator)
                                     :disposition :approved-commit
                                     :confidence (:confidence verdict)})
        commit-fn (commit-fn subject))

      :else
      (store/append-fact st {:t :approval-denied
                             :op op
                             :subject subject
                             :by (:operator-id operator)
                             :disposition :rejected
                             :confidence (:confidence verdict)}))))

(defn- log-batch!
  "Commit hook for `:log-production-batch`: re-register the batch with
  its own stored fields and the one-way `:processed?` flag."
  [st subject]
  (store/log-batch st subject (store/production-batch st subject)))

(defn run-demo!
  "Drive a fresh seeded store through every disposition this actor can
  reach and return the resulting store.

    * `batch-101` runs the full happy path -- a clean
      `:schedule-maintenance` AUTO-COMMITS (routine, not a
      mandatory-escalation op), a clean `:log-production-batch`
      ESCALATES (actuation always needs the plant operator) and is
      approved, a `:coordinate-shipment` on the now-registered batch
      escalates and is approved -- and then proves both double-commit
      guards by replaying each of those two actuation ops.
    * `batch-102` shows the soft confidence gate: a maintenance proposal
      at 0.45 is under `governor/confidence-floor` and escalates even
      though nothing is wrong with it.
    * `batch-108` raises a food-safety concern, which
      `governor/always-escalate-ops` never auto-resolves on confidence.
    * `batch-109` escalates the same clean actuation `batch-101` did and
      the operator REJECTS it -- the store is left untouched.
    * every remaining batch hard-holds on exactly one rule, and
      `batch-999` (never seeded) hard-holds on `:batch-not-registered`.

  All 17 of the Governor's hard rules are exercised."
  []
  (-> (seed-store)
      ;; ---- batch-101: routine maintenance, clean, low stakes -> auto-commit
      (drive {:op :schedule-maintenance :subject "batch-101"
              :proposal (proposal 0.92 "Roller-Mill-Maintenance-Manual-Rev4")})
      ;; ---- batch-101: actuation -> always escalates -> operator approves
      (drive {:op :log-production-batch :subject "batch-101"
              :proposal (proposal 0.94 "JAS-1061-Milling-Log")
              :approval :approved
              :commit-fn log-batch!})
      ;; ---- batch-101: shipment of the now-registered batch -> approved
      (drive {:op :coordinate-shipment :subject "batch-101"
              :proposal (proposal 0.90 "Shipment-Coordination-SOP-7")
              :approval :approved
              :commit-fn store/finalize-shipment})
      ;; ---- batch-101: double-commit guards (both HARD)
      (drive {:op :log-production-batch :subject "batch-101"
              :proposal (proposal 0.94 "JAS-1061-Milling-Log")})
      (drive {:op :coordinate-shipment :subject "batch-101"
              :proposal (proposal 0.90 "Shipment-Coordination-SOP-7")})
      ;; ---- batch-101: out-of-allowlist op, and a non-:propose effect (both HARD)
      (drive {:op :control-milling-line :subject "batch-101"
              :proposal (proposal 0.99 "Roller-Mill-Operating-Manual")})
      (drive {:op :schedule-maintenance :subject "batch-105"
              :proposal (assoc (proposal 0.95 "Sifter-Service-Bulletin-12")
                               :effect :commit)})
      ;; ---- batch-101: an un-cited food-safety flag (HARD :no-spec-basis)
      (drive {:op :flag-food-safety-concern :subject "batch-101"
              :proposal (proposal 0.88 nil)})
      ;; ---- batch-102: soft confidence gate on an otherwise fine proposal
      (drive {:op :schedule-maintenance :subject "batch-102"
              :proposal (proposal 0.45 "Magnet-Calibration-Procedure-3")
              :approval :approved})
      ;; ---- batch-108: a properly cited food-safety concern -> always escalates
      (drive {:op :flag-food-safety-concern :subject "batch-108"
              :proposal (proposal 0.97 "Plant-HACCP-Plan-Rev9")
              :approval :approved})
      ;; ---- batch-109: clean actuation the operator REJECTS
      (drive {:op :log-production-batch :subject "batch-109"
              :proposal (proposal 0.91 "JAS-1061-Milling-Log")
              :approval :rejected
              :commit-fn log-batch!})
      ;; ---- one hard hold per remaining defective batch
      (drive {:op :log-production-batch :subject "batch-102"
              :proposal (proposal 0.93 "JAS-1061-Milling-Log")})
      (drive {:op :log-production-batch :subject "batch-103"
              :proposal (proposal 0.93 "JAS-1061-Milling-Log")})
      (drive {:op :log-production-batch :subject "batch-104"
              :proposal (proposal 0.93 "JAS-1061-Milling-Log")})
      (drive {:op :log-production-batch :subject "batch-105"
              :proposal (proposal 0.93 "JAS-1061-Milling-Log")})
      (drive {:op :log-production-batch :subject "batch-106"
              :proposal (proposal 0.93 "JAS-1061-Milling-Log")})
      (drive {:op :log-production-batch :subject "batch-107"
              :proposal (proposal 0.93 "JAS-1061-Milling-Log")})
      (drive {:op :log-production-batch :subject "batch-108"
              :proposal (proposal 0.93 "JAS-1061-Milling-Log")})
      (drive {:op :log-production-batch :subject "batch-110"
              :proposal (proposal 0.93 "JAS-1061-Milling-Log")})
      (drive {:op :log-production-batch :subject "batch-111"
              :proposal (proposal 0.93 "JAS-1061-Milling-Log")})
      (drive {:op :log-production-batch :subject "batch-112"
              :proposal (proposal 0.93 "JAS-1061-Milling-Log")})
      (drive {:op :log-production-batch :subject "batch-113"
              :proposal (proposal 0.93 "JAS-1061-Milling-Log")})
      ;; ---- batch-999 was never seeded
      (drive {:op :coordinate-shipment :subject unregistered-batch-id
              :proposal (proposal 0.90 "Shipment-Coordination-SOP-7")})))

;; ───────────────────────────── ledger reading ─────────────────────────────

(defn- hard-hold?
  "A `:governor-hold` whose `:basis` is non-empty is an un-overridable
  hard violation; an empty `:basis` is the soft-escalation fact the
  scaffold routes through the same hold-fact path."
  [f]
  (and (= :governor-hold (:t f)) (seq (:basis f))))

(defn- escalation? [f]
  (and (= :governor-hold (:t f)) (empty? (:basis f))))

(defn hard-holds [db]
  (filterv hard-hold? (store/audit-trail db)))

;; ───────────────────────────── rendering ─────────────────────────────

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw-name [k]
  (if (keyword? k)
    (if-let [ns* (namespace k)] (str ns* "/" (name k)) (name k))
    (str k)))

(defn- td [& xs] (str "<td>" (apply str xs) "</td>"))

(defn- row [& cells] (str "        <tr>" (apply str cells) "</tr>"))

(defn- last-fact-for [ledger subject]
  (last (filter #(= subject (:subject %)) ledger)))

(defn- status-cell [ledger subject]
  (let [f (last-fact-for ledger subject)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (hard-hold? f)
      (str "<span class=\"critical\">HARD hold &middot; "
           (esc (str/join ", " (map kw-name (:basis f)))) "</span>")
      (= :committed (:t f)) "<span class=\"ok\">auto-committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :approval-denied (:t f)) "<span class=\"warn\">operator rejected</span>"
      (escalation? f) "<span class=\"warn\">awaiting operator sign-off</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- lifecycle-cell [b]
  (cond
    (:shipment-finalized? b) "<span class=\"ok\">logged &amp; shipped</span>"
    (:processed? b) "<span class=\"warn\">logged, not yet shipped</span>"
    :else "<span class=\"muted\">unlogged</span>"))

(defn- calibration-cell
  "Rendered from the real predicate rather than the raw epoch value, so
  the page carries no clock-derived bytes."
  [b]
  (if (registry/magnet-calibration-overdue? (:magnet-last-calibration-date b)
                                            (System/currentTimeMillis))
    "<span class=\"critical\">overdue</span>"
    "<span class=\"ok\">current</span>"))

(defn- batch-row [ledger [id b note]]
  (let [p (facts/product-type-by-id (:product-type b))
        j (facts/jurisdiction-by-id (:jurisdiction b))]
    (row (td "<code>" (esc id) "</code>")
         (td (esc (:name p)) " <span class=\"muted\">" (esc (kw-name (:product-type b))) "</span>")
         (td (esc (:name j)))
         (td (esc (:moisture-percent b)))
         (td (esc (:ash-content-percent b)))
         (td (esc (:granulation-microns b)))
         (td (esc (:mycotoxin-ppb b)) " <span class=\"muted\">/ " (esc (:mycotoxin-max-ppb p)) "</span>")
         (td (esc (:sanitation-score b)))
         (td (calibration-cell b))
         (td (lifecycle-cell b))
         (td "<span class=\"muted\">" (esc note) "</span>")
         (td (status-cell ledger id)))))

(defn- unregistered-row [ledger]
  (row (td "<code>" (esc unregistered-batch-id) "</code>")
       (td "<span class=\"muted\">(never seeded)</span>")
       ;; colspan spans the 9 middle columns (jurisdiction .. seeded-as) of
       ;; the 12-column batch table; id and status are rendered either side.
       "<td colspan=\"9\" class=\"muted\">no plant record exists for this id</td>"
       (td (status-cell ledger unregistered-batch-id))))

(defn- gate-label [op]
  (cond
    (governor/high-stakes op)
    "<span class=\"warn\">ALWAYS operator sign-off &middot; real actuation, never auto at any confidence</span>"
    (governor/always-escalate-ops op)
    "<span class=\"warn\">ALWAYS operator sign-off &middot; food-safety, never auto-resolved on confidence</span>"
    :else
    (str "<span class=\"ok\">auto-commits when the Governor is clean and confidence &ge; "
         governor/confidence-floor "</span>")))

(defn- action-gate-rows
  "Derived from `governor/allowed-ops` / `/high-stakes` /
  `/always-escalate-ops` / `/confidence-floor`, not hand-listed -- if
  the Governor's allowlist changes, this table changes with it."
  []
  (str/join "\n"
            (for [op (sort-by kw-name governor/allowed-ops)]
              (row (td "<code>:" (esc (kw-name op)) "</code>")
                   (td (gate-label op))))))

(defn- hard-rule-rows
  "One row per distinct hard rule this run actually reached, with the
  Governor's own detail text for the first occurrence."
  [ledger]
  (let [holds (filter hard-hold? ledger)
        by-rule (reduce (fn [acc f]
                          (reduce (fn [acc v]
                                    (update acc (:rule v) (fnil conj []) [f v]))
                                  acc (:violations f)))
                        {} holds)]
    (str/join "\n"
              (for [rule (sort-by kw-name (keys by-rule))
                    :let [hits (get by-rule rule)
                          [f v] (first hits)]]
                (row (td "<code>:" (esc (kw-name rule)) "</code>")
                     (td (esc (count hits)))
                     (td "<code>" (esc (:subject f)) "</code>")
                     (td (esc (:detail v))))))))

(defn- fact-kind [f]
  (cond
    (hard-hold? f) "<span class=\"critical\">HARD hold</span>"
    (escalation? f) "<span class=\"warn\">escalated to operator</span>"
    (= :committed (:t f)) "<span class=\"ok\">auto-commit</span>"
    (= :approval-granted (:t f)) "<span class=\"ok\">operator approved</span>"
    (= :approval-denied (:t f)) "<span class=\"warn\">operator rejected</span>"
    :else "<span class=\"muted\">&mdash;</span>"))

(defn- ledger-row [i f]
  (row (td (esc (inc i)))
       (td "<code>" (esc (kw-name (:t f))) "</code>")
       (td (fact-kind f))
       (td "<code>:" (esc (kw-name (or (:op f) :n-a))) "</code>")
       (td "<code>" (esc (:subject f)) "</code>")
       (td (esc (:confidence f)))
       (td (cond
             (seq (:basis f))
             (str "<code>" (esc (str/join ", " (map kw-name (:basis f)))) "</code>")
             ;; the entity is markup, so it is concatenated OUTSIDE `esc` --
             ;; escaping it would print the literal text "&mdash;".
             (:by f)
             (str "<span class=\"muted\">signed by " (esc (:by f)) "</span>")
             :else "<span class=\"muted\">&mdash;</span>"))))

(defn- summary-rows [ledger]
  (let [n (fn [pred] (count (filter pred ledger)))]
    (str/join "\n"
              [(row (td "ledger facts") (td (esc (count ledger))))
               (row (td "HARD holds (un-overridable)") (td "<span class=\"critical\">" (esc (n hard-hold?)) "</span>"))
               (row (td "distinct hard rules reached")
                    (td (esc (count (into #{} (mapcat :basis) (filter hard-hold? ledger))))))
               (row (td "escalated to the operator") (td "<span class=\"warn\">" (esc (n escalation?)) "</span>"))
               (row (td "auto-committed (Governor clean, low stakes)")
                    (td "<span class=\"ok\">" (esc (n #(= :committed (:t %)))) "</span>"))
               (row (td "operator approvals") (td "<span class=\"ok\">" (esc (n #(= :approval-granted (:t %)))) "</span>"))
               (row (td "operator rejections") (td "<span class=\"warn\">" (esc (n #(= :approval-denied (:t %)))) "</span>"))])))

(defn render
  "Render the console from a store `db` that has already run `run-demo!`."
  [db]
  (let [ledger (vec (store/audit-trail db))
        batches (map (fn [[id _ note]] [id (store/production-batch db id) note]) demo-batches)]
    (str
     "<!doctype html>\n"
     "<html lang=\"ja\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
     "<title>cloud-itonami-isic-1061 &middot; grain mill products operator console</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Grain mill products (ISIC 1061) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · batch logging &amp; shipment always operator-approved · not equipment control</span>\n"
     "</header>\n"
     "<main>\n"

     "  <section class=\"card\">\n"
     "    <h2>This run</h2>\n"
     "    <p class=\"muted\">Build-time generated by <code>millops.render-html</code> (<code>clojure -M:dev:render-html</code>) from one real pass of <code>millops.operation/run-operation</code> → <code>millops.governor/check</code> → <code>millops.store</code>. Every number below is counted off the ledger that pass produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Measure</th><th>Count</th></tr></thead>\n"
     "      <tbody>\n"
     (summary-rows ledger) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Production batches</h2>\n"
     "    <p class=\"muted\">Milling windows (moisture / ash / granulation / mycotoxin) come from <code>millops.facts/product-types</code>; the magnet column is the boolean <code>millops.registry/magnet-calibration-overdue?</code> returns, not a stored date.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Batch</th><th>Product</th><th>Jurisdiction</th><th>Moisture %</th><th>Ash %</th><th>Granulation μm</th><th>Mycotoxin ppb / max</th><th>Sanitation</th><th>Magnet cal.</th><th>Lifecycle</th><th>Seeded as</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map (partial batch-row ledger) batches)) "\n"
     (unregistered-row ledger) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Action gate (Mill Governor)</h2>\n"
     "    <p class=\"muted\">The closed allowlist. Anything outside it — roller-mill/sifter/purifier control, food-safety certification — is refused unconditionally as <code>:op-not-allowed</code>, whatever the advisor's confidence.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Hard gates this run actually reached</h2>\n"
     "    <p class=\"muted\">A HARD hold never reaches a human — the Governor refuses before escalation is even offered. The detail text is the Governor's own, quoted verbatim from the hold fact.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Rule</th><th>Hits</th><th>First subject</th><th>Governor detail (first occurrence)</th></tr></thead>\n"
     "      <tbody>\n"
     (hard-rule-rows ledger) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only. <code>millops.operation/run-operation</code> emits one <code>:governor-hold</code> fact for a refusal of either kind; a non-empty <code>:basis</code> is the hard, un-overridable case and an empty one is the soft escalation the operator then signs. The approver is shown here, on the timeline where it is real — <code>millops.store</code> has no approver field on a batch.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>#</th><th>Fact</th><th>Disposition</th><th>Op</th><th>Subject</th><th>Confidence</th><th>Basis / signer</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map-indexed ledger-row ledger)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>What this sample does not show</h2>\n"
     "    <p class=\"muted\">Stated so the page is not read as claiming more than it is: <code>millops.advisor</code> is still a skeleton, so no LLM ran — the proposals above are fixtures and only the Governor's verdicts on them are computed. <code>millops.sim</code> (<code>clojure -M:dev:run</code>) is still a stub, so this file seeds its own batches rather than reading a shared demo fixture. <code>millops.store</code> models batches and the ledger only: maintenance and shipment produce audit facts, not stored documents. And <code>millops.operation</code> has no commit path of its own — every <code>:governor-hold</code> row above is a fact the real actor stack built, but the <code>:committed</code> / <code>:approval-granted</code> / <code>:approval-denied</code> rows (and therefore the operator name on them) are written by this generator's driver, which is the only place in this repo where an approver is recorded at all.</p>\n"
     "  </section>\n"

     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        ledger (store/audit-trail db)
        holds (hard-holds db)]
    ;; A console that shows no real HARD hold is not evidence of a Governor.
    (when (empty? holds)
      (throw (ex-info "no HARD :governor-hold fact on the ledger — refusing to write a console that shows no real hold"
                      {:ledger-facts (count ledger)})))
    (let [f (java.io.File. ^String out)]
      (when-let [p (.getParentFile f)] (.mkdirs p))
      (spit f (render db)))
    (println "wrote" out
             (str "(" (count ledger) " ledger facts, "
                  (count holds) " HARD holds over "
                  (count (into #{} (mapcat :basis) holds)) " distinct rules)"))))
