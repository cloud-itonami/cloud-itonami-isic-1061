(ns millops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2: this repo previously had NO demo page
  and no generator at all -- and, unlike `cloud-itonami-isic-9522`, no
  usable demo driver either. `millops.sim` (`clojure -M:dev:run`) is a
  two-line stub that prints \"not yet implemented\" and names no subject
  ids, and `millops.store` ships NO seed data (the actor's store is plain
  data threaded through pure functions; every test builds its own store
  inline). So the scenario below is authored here rather than copied from
  the sim -- but it is authored OUT OF the repo's own regulatory facts,
  not out of thin air:

    - every milling window (moisture target/tolerance, ash-content
      min/max, granulation min/max, mycotoxin action level) is read from
      `millops.facts/product-types` at build time
    - every clean batch's actuals are DERIVED from those windows (moisture
      = the product's own target, ash/granulation = the midpoint of the
      product's own range, mycotoxin = a quarter of the product's own
      action level)
    - every failing batch's actual is DERIVED as a named excursion past
      the product's own limit (mycotoxin = action level + 25 ppb;
      moisture = target + tolerance + 0.4)
    - every evidence checklist is the jurisdiction's own
      `:required-evidence` list from `millops.facts/jurisdictions` (the
      incomplete one is that same list with one item removed)
    - every declared-allergen set is computed by
      `millops.facts/grain-source-allergen-set` off the batch's real grain
      sources (the mismatching one is that same set with one allergen
      dropped)
    - the action-gate table is derived from `millops.governor/allowed-ops`,
      `/high-stakes`, `/always-escalate-ops` and `/confidence-floor`, not
      hand-described

  Every disposition, hold reason, violation detail and confidence value on
  the page is real output of `millops.operation/run-operation` ->
  `millops.governor/check` -> `millops.store` at build time. Nothing on
  the page is hand-typed HTML describing a decision. No operator names, no
  companies and no figures are invented: the actor id `op-1` is the
  repo's own test convention (`test/millops/operation_test.cljc`) and the
  approver is a ROLE (`plant-operator`), the term
  `millops.governor/high-stakes` itself uses (\"plant operator sign-off\").

  This repo does NOT depend on langgraph. `deps.edn` lists it only under
  `:dev :override-deps`, which is a no-op when the dependency is absent
  from `:deps`, and `millops.operation` is a single pure function
  (`run-operation`) rather than a compiled StateGraph -- so there is no
  `langgraph.graph/run*` to drive here, unlike isic-9522. The real actor
  entry point IS `run-operation`, and that is what this driver calls.

  Determinism: byte-identical across reruns. The page contains no
  timestamps, no random values and no map-iteration-order-dependent
  content (batches are held in an ordered vector, and every set/map that
  reaches the page is sorted). The only host-clock read is
  `magnet-last-calibration-date`, seeded as a RELATIVE offset (10 days ago
  = current, 200 days ago = overdue) so the governor's 90-day calibration
  verdict is stable forever; the epoch value itself never reaches the
  page.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [jp-go-dds.skin]
            [millops.facts :as facts]
            [millops.governor :as governor]
            [millops.operation :as operation]
            [millops.store :as store]))

;; ─────────────────────────── actor identity ───────────────────────────

(def ^:private actor-id
  "The advisor's actor id. `op-1` is this repo's own convention -- every
  case in `test/millops/operation_test.cljc` passes exactly this."
  "op-1")

(def ^:private approver-role
  "Human sign-off is recorded as a ROLE, not a fabricated person. The term
  is the Governor's own: `millops.governor/high-stakes` documents that both
  actuation events \"require plant operator sign-off\"."
  "plant-operator")

;; ───────────────────────── seed, derived from facts ────────────────────

(defn- round2 [x]
  (/ (Math/round (* (double x) 100.0)) 100.0))

(def ^:private day-ms (* 24 60 60 1000))

(defn- days-ago
  "Epoch-ms `n` days back. Used ONLY for `:magnet-last-calibration-date`,
  which `millops.governor` compares against its own 90-day window. Seeded
  relative (not absolute) so the verdict never drifts with the wall clock;
  the value never reaches the rendered page."
  [n]
  (- (System/currentTimeMillis) (* n day-ms)))

(defn- base-batch
  "A batch that is clean BY CONSTRUCTION against every one of the
  Governor's independent checks, with each actual derived from the
  product's / jurisdiction's own registered window in `millops.facts`."
  [product-id jurisdiction-id grain-sources]
  (let [p (facts/product-type-by-id product-id)
        j (facts/jurisdiction-by-id jurisdiction-id)]
    {:product-type product-id
     :jurisdiction jurisdiction-id
     :moisture-percent (round2 (:moisture-target-percent p))
     :ash-content-percent (round2 (/ (+ (:ash-content-min-percent p)
                                        (:ash-content-max-percent p))
                                     2))
     :granulation-microns (quot (+ (:granulation-min-microns p)
                                   (:granulation-max-microns p))
                                2)
     :mycotoxin-ppb (quot (:mycotoxin-max-ppb p) 4)
     :foreign-material-detected? false
     :magnet-last-calibration-date (days-ago 10)
     :weight-variance-grams 20
     :grain-sources (vec grain-sources)
     :declared-allergens (facts/grain-source-allergen-set grain-sources)
     :sanitation-score 92
     :evidence-checklist (vec (:required-evidence j))
     :safety-concern-raised? false
     :safety-concern-resolved? false}))

(defn- seed-batches
  "Ordered `[batch-id batch]` pairs -- a vector, never a map, so page order
  can never depend on hash-map iteration order."
  []
  (let [corn (facts/product-type-by-id :meal/corn)
        white (facts/product-type-by-id :flour/wheat-white)
        b003 (base-batch :flour/whole-wheat :eu/efsa
                         [:wheat/hard-red :sesame/whole-seed])
        b007 (base-batch :meal/corn :eu/efsa [:corn/yellow-dent])]
    [["mill-batch-001"
      (base-batch :flour/wheat-white :jp/prefectural [:wheat/hard-red])]

     ;; aflatoxin excursion: corn carries a 20 ppb action level, 50x
     ;; stricter than the 1000 ppb wheat DON level -- the flagship
     ;; grain-milling food-safety hazard.
     ["mill-batch-002"
      (assoc (base-batch :meal/corn :us/fda [:corn/yellow-dent])
             :mycotoxin-ppb (+ (:mycotoxin-max-ppb corn) 25))]

     ;; sesame milled into the batch but dropped from the label.
     ["mill-batch-003"
      (assoc b003 :declared-allergens (disj (:declared-allergens b003) :sesame))]

     ["mill-batch-004"
      (assoc (base-batch :semolina/durum :jp/prefectural [:durum/amber])
             :foreign-material-detected? true)]

     ;; metal-detection calibration past the Governor's own 90-day window.
     ["mill-batch-005"
      (assoc (base-batch :flour/wheat-white :jp/prefectural [:wheat/soft-white])
             :magnet-last-calibration-date (days-ago 200))]

     ["mill-batch-006"
      (assoc (base-batch :flour/whole-wheat :us/fda [:wheat/hard-red])
             :safety-concern-raised? true
             :safety-concern-resolved? false)]

     ;; the jurisdiction's own required-evidence list, minus one item.
     ["mill-batch-007"
      (assoc b007 :evidence-checklist
             (vec (remove #{:mycotoxin-test} (:evidence-checklist b007))))]

     ["mill-batch-008"
      (assoc (base-batch :flour/wheat-white :jp/prefectural [:wheat/hard-red])
             :moisture-percent (round2 (+ (:moisture-target-percent white)
                                          (:moisture-tolerance-percent white)
                                          0.4)))]]))

(def ^:private batch-order
  (mapv first (seed-batches)))

(defn- seed-store []
  {:batches (into {} (seed-batches))
   :facts []})

;; ──────────────────────────── the driver ──────────────────────────────

(defn- cites [spec] [{:spec spec}])

(defn- context []
  {:actor-id actor-id :hold-fact-fn governor/hold-fact})

(defn- commit-effect!
  "Apply the store mutation a committed proposal earns. Only the two
  actuation ops mutate batch state; `:schedule-maintenance` and
  `:flag-food-safety-concern` are coordination facts with no store seam
  in `millops.store`, so they honestly write no batch mutation."
  [st* op subject]
  (case op
    :log-production-batch
    (swap! st* (fn [st] (store/log-batch st subject (store/production-batch st subject))))
    :coordinate-shipment
    (swap! st* store/finalize-shipment subject)
    nil))

(defn- attempt-approval!
  "A human plant-operator sign-off attempt.

  The gate is the REAL Governor verdict: `:hard? true` means the hold came
  out of the hard-violation vector in `millops.governor/check`, which
  `check` never lets a confidence value or an approval clear. Approval is
  therefore structurally unreachable for a hard hold, and this function
  records the refusal rather than pretending the choice exists."
  [st* {:keys [op subject]} verdict]
  (if (:hard? verdict)
    (do (swap! st* store/append-fact
               {:t :approval-refused
                :op op
                :actor actor-id
                :subject subject
                :by approver-role
                :disposition :refused
                :reason :hard-hold-not-overridable
                :basis (mapv :rule (:violations verdict))})
        :approval-refused)
    (do (commit-effect! st* op subject)
        (swap! st* store/append-fact
               {:t :approval-granted
                :op op
                :actor actor-id
                :subject subject
                :by approver-role
                :disposition :approved})
        (swap! st* store/append-fact
               {:t :committed
                :op op
                :actor actor-id
                :subject subject
                :disposition :commit
                :confidence (:confidence verdict)})
        :approved-committed)))

(defn- step!
  "Drive ONE proposal through the real actor
  (`millops.operation/run-operation`, which calls `millops.governor/check`)
  and record what actually came back.

  `opts`:
    :approve? -- a human operator looks at the result. Hard holds refuse
                 (see `attempt-approval!`); escalations commit."
  [st* request proposal & [{:keys [approve?]}]]
  (let [st @st*
        ctx (context)
        ;; The verdict is read straight off the Governor so the page's
        ;; hard/escalate distinction is governor output, not inference.
        verdict (governor/check request ctx proposal st)
        result (operation/run-operation request ctx proposal st governor/check)]
    (if (:ok? result)
      (do (commit-effect! st* (:op request) (:subject request))
          (swap! st* store/append-fact
                 {:t :committed
                  :op (:op request)
                  :actor actor-id
                  :subject (:subject request)
                  :disposition :commit
                  :confidence (:confidence verdict)
                  :auto? true})
          :auto-commit)
      (do
        ;; The hold fact is the actor's own output (`governor/hold-fact`
        ;; via the injected `:hold-fact-fn`); only the two verdict flags
        ;; are carried alongside so the ledger can tell a permanent hard
        ;; block apart from a soft escalation.
        (swap! st* store/append-fact
               (assoc (first (:facts result))
                      :hard? (boolean (:hard? verdict))
                      :escalate? (boolean (:escalate? verdict))))
        (if approve?
          (attempt-approval! st* request verdict)
          :held)))))

(defn run-demo!
  "Run a fresh seeded store through a scenario that reaches every
  disposition this actor can produce.

  `mill-batch-001` clears a FULL clean lifecycle: a maintenance schedule
  auto-commits (the only op in the allowlist that is neither high-stakes
  nor always-escalate), then a production-batch log and a shipment
  coordination each escalate -- both are permanently high-stakes actuation
  events, never auto at any confidence -- and are approved by the plant
  operator, committing `:processed?` and `:shipment-finalized?` to the
  store. Its own committed state then produces two further HARD holds when
  the same two ops are replayed (`:already-processed`,
  `:already-shipment-finalized`).

  Nine further HARD holds cover the grain-milling hazard surface:
  a corn batch over its aflatoxin action level; an undeclared sesame
  allergen; foreign material caught at inspection; metal-detection
  calibration past its window; an unresolved food-safety flag; an
  incomplete jurisdiction evidence checklist; moisture outside the
  product's storage window; a shipment for a batch this plant never
  registered; and a proposal claiming direct milling-line control, which
  is outside the actor's closed op allowlist entirely.

  Two of those hard holds are additionally put to a human operator to
  prove the refusal is structural (`attempt-approval!`), and
  `mill-batch-006` also raises a food-safety concern, which escalates
  softly even though the Governor's hard checks are clean -- the
  hard/soft contrast on one subject.

  Returns the resulting store."
  []
  (let [st* (atom (seed-store))
        propose (fn [spec conf] {:cites (cites spec)
                                 :value {:jurisdiction :jp/prefectural}
                                 :effect :propose
                                 :confidence conf})]

    ;; ── mill-batch-001: full clean lifecycle ──────────────────────────
    (step! st* {:op :schedule-maintenance :subject "mill-batch-001"}
           (propose "Roller-Mill-Maintenance-Manual" 0.91))

    (step! st* {:op :log-production-batch :subject "mill-batch-001"}
           (propose "JAS-Flour-Standard" 0.93) {:approve? true})

    (step! st* {:op :coordinate-shipment :subject "mill-batch-001"}
           (propose "Plant-Shipping-Procedure" 0.9) {:approve? true})

    ;; committed state now blocks its own replay -- HARD, state-derived
    (step! st* {:op :log-production-batch :subject "mill-batch-001"}
           (propose "JAS-Flour-Standard" 0.93))
    (step! st* {:op :coordinate-shipment :subject "mill-batch-001"}
           (propose "Plant-Shipping-Procedure" 0.9))

    ;; ── hard holds on the milling hazard surface ──────────────────────
    ;; aflatoxin over the corn action level -- put to a human, refused
    (step! st* {:op :log-production-batch :subject "mill-batch-002"}
           (propose "US-FDA-Aflatoxin-Action-Level" 0.97) {:approve? true})

    (step! st* {:op :log-production-batch :subject "mill-batch-003"}
           (propose "EU-1169-2011-Allergen-Labelling" 0.94))

    (step! st* {:op :log-production-batch :subject "mill-batch-004"}
           (propose "Plant-HACCP-Plan" 0.95))

    (step! st* {:op :log-production-batch :subject "mill-batch-005"}
           (propose "Metal-Detection-Calibration-SOP" 0.92))

    ;; unresolved food-safety flag -- put to a human, refused
    (step! st* {:op :log-production-batch :subject "mill-batch-006"}
           (propose "Plant-HACCP-Plan" 0.96) {:approve? true})

    ;; ...while flagging the concern itself escalates softly on the SAME
    ;; subject: hard checks clean, but never auto-resolved by confidence.
    (step! st* {:op :flag-food-safety-concern :subject "mill-batch-006"}
           (propose "Plant-HACCP-Plan" 0.99))

    (step! st* {:op :log-production-batch :subject "mill-batch-007"}
           (propose "EU-EFSA-Mycotoxin-Sampling" 0.9))

    (step! st* {:op :log-production-batch :subject "mill-batch-008"}
           (propose "JAS-Flour-Standard" 0.88))

    ;; ── holds that do not depend on batch metadata at all ─────────────
    ;; a batch this plant never registered
    (step! st* {:op :coordinate-shipment :subject "mill-batch-404"}
           (propose "Plant-Shipping-Procedure" 0.9))

    ;; direct milling-line control: outside the closed op allowlist.
    ;; This actor coordinates plant operations; it does not run equipment.
    (step! st* {:op :control-milling-line :subject "mill-batch-004"}
           (propose "Roller-Mill-Manual" 0.99))

    ;; a proposal claiming write authority for itself
    (step! st* {:op :schedule-maintenance :subject "mill-batch-005"}
           {:cites (cites "Roller-Mill-Maintenance-Manual")
            :value {:jurisdiction :jp/prefectural}
            :effect :commit
            :confidence 0.95})

    ;; a clean proposal the advisor is simply not sure enough about
    (step! st* {:op :schedule-maintenance :subject "mill-batch-003"}
           (propose "Roller-Mill-Maintenance-Manual" 0.41))

    @st*))

;; ───────────────────────────── rendering ──────────────────────────────

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw-list
  "Render a keyword collection sorted -- never in set/map iteration order."
  [coll]
  (if (seq coll)
    (str/join ", " (map name (sort-by str coll)))
    "—"))

(defn- holds [ledger]
  (filterv #(= :governor-hold (:t %)) ledger))

(defn- hard-holds [ledger]
  (filterv :hard? (holds ledger)))

(defn- approver-on-batch-record?
  "Does the STORE actually hold who approved a batch? `store/log-batch`
  writes the batch data plus `:processed?` and nothing else, so the
  approver reaches the audit ledger but never the batch record. This is
  checked at build time rather than asserted in prose."
  [db]
  (boolean
   (some (fn [id]
           (let [b (store/production-batch db id)]
             (some some? [(:approved-by b) (:approver b) (:by b)])))
         batch-order)))

(defn- last-fact-for [ledger batch-id]
  (last (filter #(= (:subject %) batch-id) ledger)))

(defn- disposition-cell [ledger batch-id]
  (let [f (last-fact-for ledger batch-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :approval-refused (:t f))
      "<span class=\"critical\">approval REFUSED &middot; hard hold</span>"
      (= :governor-hold (:t f))
      (if (:hard? f)
        (str "<span class=\"critical\">HARD hold &middot; "
             (esc (name (or (-> f :violations first :rule) :unknown)))
             "</span>")
        "<span class=\"warn\">escalated to operator</span>")
      :else "<span class=\"muted\">in progress</span>")))

(defn- lifecycle-cell [{:keys [processed? shipment-finalized?]}]
  (cond
    shipment-finalized? "<span class=\"ok\">logged &amp; shipped</span>"
    processed? "<span class=\"warn\">logged, not yet shipped</span>"
    :else "<span class=\"muted\">in production</span>"))

(defn- batch-row [db ledger batch-id]
  (let [b (store/production-batch db batch-id)
        p (facts/product-type-by-id (:product-type b))
        j (facts/jurisdiction-by-id (:jurisdiction b))]
    (format (str "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td>"
                 "<td class=\"num\">%s</td><td class=\"num\">%s</td>"
                 "<td class=\"num\">%s</td><td class=\"num\">%s / %s</td>"
                 "<td>%s</td><td>%s</td></tr>")
            (esc batch-id)
            (esc (:name p))
            (esc (:name j))
            (esc (:moisture-percent b))
            (esc (:ash-content-percent b))
            (esc (:granulation-microns b))
            (esc (:mycotoxin-ppb b))
            (esc (:mycotoxin-max-ppb p))
            (lifecycle-cell b)
            (disposition-cell ledger batch-id))))

(defn- product-row [product-id]
  (let [p (facts/product-type-by-id product-id)]
    (format (str "        <tr><td>%s</td><td><code>%s</code></td>"
                 "<td class=\"num\">%s &plusmn; %s</td>"
                 "<td class=\"num\">%s&ndash;%s</td>"
                 "<td class=\"num\">%s&ndash;%s</td>"
                 "<td class=\"num\">%s</td></tr>")
            (esc (:name p)) (esc product-id)
            (esc (:moisture-target-percent p)) (esc (:moisture-tolerance-percent p))
            (esc (:ash-content-min-percent p)) (esc (:ash-content-max-percent p))
            (esc (:granulation-min-microns p)) (esc (:granulation-max-microns p))
            (esc (:mycotoxin-max-ppb p)))))

(defn- gate-row
  "Derived from the Governor's own vars -- not a hand-written description."
  [op]
  (let [high? (contains? governor/high-stakes op)
        esc-op? (contains? governor/always-escalate-ops op)]
    (format "        <tr><td><code>:%s</code></td><td>%s</td><td>%s</td></tr>"
            (esc (name op))
            (if high?
              "<span class=\"critical\">high-stakes actuation</span>"
              "<span class=\"muted\">coordination</span>")
            (if esc-op?
              "<span class=\"warn\">ALWAYS human sign-off &middot; never auto at any confidence</span>"
              (format "<span class=\"ok\">auto-commit when hard checks clean and confidence &ge; %s</span>"
                      (esc governor/confidence-floor))))))

(defn- hold-row [{:keys [op subject violations confidence hard?]}]
  (let [v (first violations)]
    (format (str "        <tr><td><code>%s</code></td><td><code>:%s</code></td>"
                 "<td>%s</td><td class=\"num\">%s</td><td>%s</td><td>%s</td></tr>")
            (esc subject)
            (esc (name (or op :n-a)))
            (if hard?
              "<span class=\"critical\">HARD &middot; not overridable</span>"
              "<span class=\"warn\">escalation &middot; operator may sign off</span>")
            (esc confidence)
            (if v (str "<code>" (esc (name (:rule v))) "</code>")
                "<span class=\"muted\">—</span>")
            (if v (esc (:detail v)) "<span class=\"muted\">confidence / always-escalate gate</span>"))))

(defn- ledger-row [{:keys [t op subject disposition basis by]}]
  (format (str "        <tr><td>%s</td><td><code>:%s</code></td><td><code>%s</code></td>"
               "<td>%s</td><td>%s</td><td>%s</td></tr>")
          (esc (name t))
          (esc (name (or op :n-a)))
          (esc subject)
          (esc (name (or disposition :n-a)))
          (if (seq basis) (esc (kw-list basis)) "<span class=\"muted\">—</span>")
          (if by (esc by) "<span class=\"muted\">—</span>")))

(defn render
  "Render the full operator-console document from a store `db` that has
  already been through `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/audit-trail db))
        all-holds (holds ledger)
        hard (hard-holds ledger)
        soft (remove :hard? all-holds)
        refusals (filterv #(= :approval-refused (:t %)) ledger)
        approvals (filterv #(= :approval-granted (:t %)) ledger)
        commits (filterv #(= :committed (:t %)) ledger)
        product-ids (sort-by str (keys facts/product-types))
        gate-ops (sort-by str governor/allowed-ops)]
    (str
     "<html lang=\"en\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
     "<title>cloud-itonami-isic-1061 &middot; grain mill products</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Manufacture of grain mill products (ISIC 1061) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · batch logging &amp; shipment always human-approved</span>\n"
     "</header>\n"
     "<main>\n"

     "  <section class=\"card\">\n"
     "    <h2>Run summary</h2>\n"
     "    <p class=\"muted\">Generated at build time by <code>millops.render-html</code> (<code>clojure -M:dev:render-html</code>) from a single scenario driven through <code>millops.operation/run-operation</code> → <code>millops.governor/check</code> → <code>millops.store</code>. Every figure below is counted off the resulting audit ledger.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Ledger facts</th><th>Governor holds</th><th>of which HARD</th><th>Escalations</th><th>Approvals granted</th><th>Approvals refused</th><th>Commits</th></tr></thead>\n"
     "      <tbody>\n"
     (format (str "        <tr><td class=\"num\">%s</td><td class=\"num\">%s</td>"
                  "<td class=\"num critical\">%s</td><td class=\"num\">%s</td>"
                  "<td class=\"num\">%s</td><td class=\"num\">%s</td>"
                  "<td class=\"num\">%s</td></tr>")
             (count ledger) (count all-holds) (count hard) (count soft)
             (count approvals) (count refusals) (count commits))
     "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Production batches</h2>\n"
     "    <p class=\"muted\">Finished-product actuals as the Governor independently read them. Mycotoxin is shown as <span class=\"num\">actual / action level</span>; the action level is per product type — corn carries a 20 ppb aflatoxin limit, 50× stricter than the 1000 ppb DON limit wheat carries.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Batch</th><th>Product</th><th>Jurisdiction</th><th>Moisture %</th><th>Ash %</th><th>Granulation µm</th><th>Mycotoxin ppb</th><th>Lifecycle</th><th>Last disposition</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map (partial batch-row db ledger) batch-order)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Action gate (Mill Governor)</h2>\n"
     "    <p class=\"muted\">Derived at build time from <code>millops.governor/allowed-ops</code>, <code>/high-stakes</code>, <code>/always-escalate-ops</code> and <code>/confidence-floor</code>. Any op outside this closed allowlist — direct roller-mill / sifter / purifier control above all — is refused outright: this actor coordinates plant operations, it does not run equipment and it does not certify food safety.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Stakes</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map gate-row gate-ops)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Governor holds (this run)</h2>\n"
     "    <p class=\"muted\">A <span class=\"critical\">HARD</span> hold comes out of the Governor's hard-violation vector. No confidence value and no human sign-off can clear it — the scenario proves this by actually putting two of them to the plant operator and recording the refusal in the ledger below. An <span class=\"warn\">escalation</span> is the soft gate: hard checks clean, but the op is high-stakes actuation, always-escalate, or below the confidence floor.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Batch</th><th>Op</th><th>Class</th><th>Confidence</th><th>Rule</th><th>Governor detail</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map hold-row all-holds)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Product windows (regulatory facts)</h2>\n"
     "    <p class=\"muted\">Read from <code>millops.facts/product-types</code>. The Governor validates every batch against these independently; the advisor's confidence is never sufficient on its own.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Product</th><th>Id</th><th>Moisture %</th><th>Ash %</th><th>Granulation µm</th><th>Mycotoxin max ppb</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map product-row product-ids)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold, refusal, approval and commit this scenario produced, in order.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Batch</th><th>Disposition</th><th>Basis</th><th>By</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map ledger-row ledger)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Where the approver is actually recorded</h2>\n"
     "    <p>"
     (if (approver-on-batch-record? db)
       (str "Checked at build time: the batch record itself holds the approver.")
       (str "Checked at build time: the approver <strong>is not on the batch record</strong>. "
            "<code>millops.store/log-batch</code> writes the batch data plus <code>:processed?</code> and nothing else, "
            "so the <code>" (esc approver-role) "</code> sign-off reaches the append-only audit ledger "
            "(the <code>approval-granted</code> rows above) and only there. "
            "This page therefore joins the approver back from the ledger rather than printing a name the batch record does not hold."))
     "</p>\n"
     "  </section>\n"

     "</main>\n"
     "<footer>\n"
     "  <p>cloud-itonami · ISIC 1061 grain mill products · every disposition, hold reason and figure above is real build-time output of the actor stack; nothing on this page is hand-written HTML describing a decision.</p>\n"
     "</footer>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        ledger (vec (store/audit-trail db))
        all-holds (holds ledger)
        hard (hard-holds ledger)
        refusals (filterv #(= :approval-refused (:t %)) ledger)]
    ;; Build-time invariants, not conventions: a future regression that
    ;; stops the Governor from holding -- or that lets a human release a
    ;; hard hold -- fails the build instead of quietly shipping a page
    ;; that claims a governor exists while showing nothing it did.
    (when (zero? (count all-holds))
      (throw (ex-info "render-html: zero governor holds rendered -- the scenario no longer exercises millops.governor"
                      {:ledger-facts (count ledger) :holds 0})))
    (when (zero? (count hard))
      (throw (ex-info "render-html: zero HARD governor holds rendered -- every hold in this run is releasable by human approval"
                      {:ledger-facts (count ledger) :holds (count all-holds) :hard-holds 0})))
    (when (zero? (count refusals))
      (throw (ex-info "render-html: no hard hold was put to a human operator -- the refusal path is unproven in this run"
                      {:hard-holds (count hard) :approval-refused 0})))
    (spit out (render db))
    (println "wrote" out "-" (count ledger) "ledger facts,"
             (count all-holds) "governor holds ("
             (count hard) "HARD),"
             (count refusals) "approval refusals,"
             (count (filterv #(= :committed (:t %)) ledger)) "commits")))
