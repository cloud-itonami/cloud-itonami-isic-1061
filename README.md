# cloud-itonami-isic-1061: Grain Mill Products Coordination Actor

**ISIC Rev. 5 1061** — Manufacture of Grain Mill Products

A distributed actor for autonomous, compliant coordination of grain-mill-products manufacturing plant operations: grain intake → cleaning/conditioning → milling → moisture/ash-content/granulation/mycotoxin inspection → allergen labeling → finished-product logistics. Sealed LLM advisor; independent Governor enforcement; append-only audit ledger. **Not equipment control.** Roller-mill/sifter/purifier operation and food-safety certification authority remain exclusive to licensed grain-mill plant staff and regulators.

## Scope

This actor coordinates **plant-operations workflow** for grain-mill-products manufacturing (flour, meal, semolina, groats):
- Production batch logging (grain intake, milling parameters, evidence checklist)
- Equipment maintenance scheduling (roller mills, sifters, purifiers, magnets)
- Food-safety concern escalation (mycotoxin contamination, foreign-material detection)
- Finished-product shipment coordination

**Out of scope:**
- Direct milling-line equipment control (plant staff exclusive)
- Food-safety certification authority (human inspector/regulator only)
- Regulatory interpretation (proposals cite jurisdiction specifications; the Governor enforces only published requirements)

## Design

### Governor (Independent Compliance Layer)

The Governor is the separation-of-powers enforcement. It never trusts the advisor's confidence for anything safety- or compliance-relevant, and it always wins over the advisor.

- **Hard HOLD** (un-overridable):
  - Operation outside the closed allowlist (`:op-not-allowed`) — includes any proposal that would touch milling-line control or food-safety certification
  - Proposal asserting an `:effect` other than `:propose` (`:effect-not-propose`)
  - No jurisdiction citation (`:no-spec-basis`) — can't verify requirements without one
  - Evidence checklist incomplete, or the batch record isn't registered (`:evidence-incomplete`)
  - Finished-product moisture outside the product's safe storage/quality range (`:moisture-out-of-target`)
  - Mycotoxin level (aflatoxin/DON/ochratoxin) exceeds the product's regulatory action level (`:mycotoxin-level-exceeded`)
  - Ash content out of the product's purity/grade window (`:ash-content-out-of-range`)
  - Particle size (granulation) out of the product's grade window (`:granulation-out-of-range`)
  - Foreign material detected on the batch's own inspection — metal/stone/glass/insect fragments (`:foreign-material-detected`)
  - Magnet/metal-detection equipment calibration overdue (`:magnet-calibration-overdue`)
  - Finished-product weight variance excessive (`:weight-variance-excessive`)
  - Allergen label mismatch — declared allergens don't cover the grain-source formulation, including gluten cross-contact from shared-line oat milling (`:allergen-label-mismatch`)
  - Plant sanitation/pest-control score insufficient (`:sanitation-score-insufficient`)
  - Unresolved food-safety flag (`:food-safety-flag-unresolved`)
  - Batch already processed / shipment already finalized (double-commit guards)
  - `:coordinate-shipment` against a batch that was never registered (`:batch-not-registered`)
- **Escalate** (human sign-off always required):
  - `:log-production-batch` / `:coordinate-shipment` — real actuation events, always require plant-operator sign-off even when the Governor is otherwise clean
  - `:flag-food-safety-concern` — a food-safety concern (mycotoxin, foreign material) is never auto-resolved by advisor confidence alone
  - Low advisor confidence (below `governor/confidence-floor`, 0.6)
- **Commit** (advisor proposal approved; Governor clean; not a mandatory-escalation op):
  - Routine, low-stakes proposals only — in this actor's current allowlist that is effectively `:schedule-maintenance` when clean

### Operations (Proposals)

Closed allowlist — the advisor may **only** ever propose these four operation types, all `:effect :propose`:

- **`:log-production-batch`** — Log grain-intake → milling → inspection batch into production records (always requires human sign-off)
- **`:schedule-maintenance`** — Propose equipment maintenance for roller mills/sifters/purifiers/magnets (routine, low risk)
- **`:flag-food-safety-concern`** — Surface a food-safety or contamination concern (e.g. mycotoxin, foreign-material detection); always escalates
- **`:coordinate-shipment`** — Finalize shipment of finished product (always requires human sign-off)

Any proposal for an operation outside this allowlist — most importantly anything that would amount to direct milling-line control, or food-safety certification — is refused unconditionally by the Governor (`:op-not-allowed`), regardless of advisor confidence.

## Testing

```bash
# Run full test suite
clojure -M:test

# Check code quality
clojure -M:lint

# Run demo simulation
clojure -M:run
```

## Standalone Use

This repo is **forkable outside the workspace**. If cloning standalone (not in the kotoba-lang monorepo), override `:local/root` paths in `deps.edn`:

```clojure
{:deps {io.github.kotoba-lang/langchain {:git/url "https://github.com/kotoba-lang/langchain" :git/tag "v0.1.0"}
        io.github.kotoba-lang/langgraph {:git/url "https://github.com/kotoba-lang/langgraph" :git/tag "v0.1.0"}}}
```

## License

AGPL-3.0-or-later. Forking/contribution welcome; see `CONTRIBUTING.md`.

## Security

Report security issues to the issue tracker or private disclosure; see `SECURITY.md`.

---

Part of **cloud-itonami**: autonomous actor fleet for regulated industries. See [github.com/cloud-itonami](https://github.com/cloud-itonami).
