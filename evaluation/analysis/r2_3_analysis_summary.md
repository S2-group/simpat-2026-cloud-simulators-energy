# R2.3 Analysis Summary — Effect of the 4-of-30 Median Trace Selection

Companion to `evaluation/analysis/r2_3_selection_analysis.py` (run with the project
`.venv`; writes CSVs + a LaTeX table into `evaluation/analysis/outputs/`).

## Reviewer concern (R2.3)

Repetitions (4 of 30) closest to the median total energy are selected as simulated
traces. The reviewer worries this skews the ground truth toward an artificially clean
workload and inflates perceived simulator accuracy, and asks for a quantitative
comparison: all 30 vs the selected 4, with the influence on MRE discussed.

## What can (and cannot) be done without re-running simulations

Simulations exist **only** for the 4 selected traces per treatment
(`$32 \times (4+2+4+5+1) = 512$` simulation instances). The other 26 repetitions were
never simulated, so "results obtained with all 30" cannot mean re-running the
simulators on 26× more traces within this revision. The feasible, methodologically
sound comparison keeps the simulated values fixed and varies only the ground truth:

> MRE vs the **selected-4** GT mean   (as in the paper)
> MRE vs the **all-30** GT mean       (reviewer's request)

The all-30 GT totals were recomputed from the raw measurements
(`ground-truth/.../metrics.csv`, trapezoid rule, eq. `energy_trapezoid`), reproducing
the shipped `.energy` files exactly (e.g. sockshop/A/100/rep18 = 1091.567 J).

## Results (all 32 treatments, MRE per treatment then averaged)

Simulation side: mean total at t=300 s over the 30 simulation runs of each repetition,
as in the paper.

| Simulator (best PM) | MRE sel-4 | MRE all-30 | Δ |
|---|---|---|---|
| CloudSim (cubic) | 0.536 | 0.497 | −0.039 |
| NetworkCloudSim (cubic) | 0.541 | 0.502 | −0.039 |
| DISSECT-CF (constant) | 0.442 | 0.458 | +0.016 |
| OpenDC (constant) | 0.122 | 0.104 | −0.018 |
| SimGrid (linear) | 0.421 | 0.437 | +0.016 |

**Reading**: |Δ| ≤ 0.04 everywhere and the *direction differs per simulator* (three
slightly lower without selection, two slightly higher). The median-based selection does
**not** systematically inflate perceived accuracy; its effect is smaller than the
differences between simulators/power models.

Variability that the selection removes (example sockshop/A/100, GT totals of 30 reps):
CV = 14.3% over all 30 vs 1.2% within the selected 4. Honest framing for the paper:
the selection isolates workload-intrinsic energy behaviour from environment noise
(order effects, background activity); this is a *deliberate measurement-noise filter*
applied before simulation, and its influence on MRE is bounded by the table above.

Files:

- `outputs/r2_3_per_treatment.csv` — every repetition × power model: sim total, both GT means, both relative errors
- `outputs/r2_3_per_simulator.csv` — per treatment × power model MREs (sel-4, all-30, Δ, best-flag)
- `outputs/r2_3_best_powermodel.tex` — ready-to-paste appendix/response table (best power models)

## Suggested paper text placement (for Max, all in `\rtwo{}`)

1. `experimentExecution.tex`, *Trace Construction*: 2–3 sentences justifying the
   selection with the Δ numbers ("recomputing the ground truth over all 30 repetitions
   changes the MRE by at most 0.04 in absolute terms, and in different directions per
   simulator (Table X), indicating the selection does not bias the comparison").
2. `threats.tex`, *Internal Validity*: one sentence + pointer to the table/appendix;
   acknowledge that only the median-selected traces were simulated, so the all-30
   comparison varies the GT reference rather than the simulation inputs.
3. Optional: appendix table (`outputs/r2_3_best_powermodel.tex`).

## Reconciliation note (aggregation convention)

Table 4 in the paper (`tab:powermodelOverview`) averages `|rel_error_total|` at the
**run level** (30 runs × 4 reps × treatments; each run weighted equally; notebook cell
4). The analysis above averages per **treatment** (30 runs → 1 repetition mean first).
For most simulators both agree at 2 decimals (cloudsim cubic: 0.54 = 0.54,
simgrid: 0.42 = 0.42, dissectcf constant: 0.44 = 0.44); for OpenDC the run-level value
is 0.136 ("0.14" in the paper) vs 0.122 treatment-level. Both are correct under their
respective definitions of "execution"; the sel-4-vs-all-30 *comparison* is unaffected
because it holds the convention fixed while varying only the GT set. If Max wants the
paper's exact convention for the appendix table, the script can be adapted in one line
(weight per run instead of per treatment) — the conclusion is identical.
