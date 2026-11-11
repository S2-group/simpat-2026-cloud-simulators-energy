# Revision Plan — SIMPAT Energy Cloud Simulators Paper

> Exported from the working session so it survives chat context. Update the ☐/☑ marks as items complete.

Working plan for the revision. Source of review items: `paper/REVIEW_PLAN.md` (items R1.4, R1.5, R1.7, R1.8, R2.3, R2.8).
Division of labour agreed in chat (2026-11-09):

- **Max**: all writing changes/additions in the LaTeX sources (plus the actual RQ2 re-verification for R1.4).
- **Assistant**: graphs, new analysis code, and scaffolds (R1.4 checklist).
- No simulations are re-run. All needed data is local:
  - simulation outputs: `evaluation/energy/*.energy` (~7.8M rows, per-run time series + per-repetition ground真相 in `*_correct` columns)
  - raw ground truth: `ground-truth/vuDevOps/data_collection/{sockshop,trainticket}-data/scenario_{A,B}/baseline/{100,1000}/repetition_1..30/metrics.csv`
    (240 treatments × 30 reps, `*_host_power` column, 5 s sampling)
  - trace-selection ranks: `trace-generation/trace-selection/trace_selection.csv`
  - sim runtimes: `evaluation/runtime/runtimes.csv` + `simulators/*/runs*.time`
- Paper builds on Max's machine after fetching `elsarticle` (CTAN), `siunitx`, `ulem` — 34 pages, no errors, all 66 bib entries resolve. Build dir created on demand (see below).

Status legend: ☐ open · ◐ in progress · ☑ done

---

## R1.4 — Subjectivity of ease-of-use evaluation (needs Max)

Reviewer: documentation/adoption judged by one researcher; only 3 maintainers responded.

**Agreed approach (Ivano's protocol)**: second researcher (Max) re-verifies every RQ2 assessment, notes agreement/disagreement with reasons; Ivano arbitrates conflicts; paper reports this 3-researcher process.

- ☑ Assistant: claim-by-claim checklist extracted → `plan/R1.4_verification_checklist.md`
  (8 key items A1–C4, condensed from 23 claims in RQ2.1/RQ2.2; evidence pointers: `figures/sc-*.png`, local
  `simulators/` sources, external docs; plus response-letter items D1–D3)
- ☐ **Max**: work through checklist; decide per item (agree / disagree + why / uncertain)
- ☐ Ivano: arbitrate disagreements
- ☐ **Max** (writing): update *Study Design* (`experimentPlan.tex`) and *Conclusion Validity* (`threats.tex`, line ~28 — currently only mentions the 3 maintainer responses); wrap new text in `\rone{}`; write response-letter entry

## R1.5 — Figures 6–7 overlap (assistant: graphs)

Reviewer: overlapping curves, insufficient colour differentiation. Final layout
(reworked after Max's feedback on readability):

- Fig 6 (`fig:bestOverTime`): ONE figure, 5 panels stacked VERTICALLY (5×1, shared
  axes; simulator + power model in each ylabel). 5-across was too wide to read.
  Included with `height=0.88\textheight` (at `\textwidth` the tall stack overflows
  the text height and LaTeX silently drops the float).
- Fig 7 (`fig:overtimes`): split into TWO figures — one per experiment — as
  subfigures (a) Sock Shop, (b) Train Ticket inside one `sidewaysfigure`, each
  4 wide × 5 tall (20 panels, one row per simulator), `0.48\linewidth` each.
- Fig 5 (`fig:cdf`): same density problem → same split into two full-width
  subfigures (a) Sock Shop, (b) Train Ticket, 5 panels per row, GT with DKW 95% band.
- Fig 8 (`fig:runtimes`): unchanged layout; n= sample sizes added.
- ☑ Assistant: script `evaluation/analysis/gen_revised_figures.py` + figures in
  `paper/figures/`: `mean_total_pwr_per_simulator.{pdf,png}`,
  `overtimes_{sockshop,trainticket}_per_simulator.{pdf,png}`,
  `cdf_{sockshop,trainticket}_per_simulator.{pdf,png}`,
  `runtimes_revised.{pdf,png}`. All visually verified (panels, legends, bands,
  no clipping); superseded `overtimes_per_simulator.*` and
  `double_cdf_per_simulator.*` removed.
- ☑ **Max-assisted writing**: `\includegraphics` swaps + captions in `results.tex`
  (committed `f62b4d0`, `eb074e9`); check captions when copying to Overleaf.
- Note for response letter: cite R1.5 split + R2.8 CI additions together (overlaps
  R2.8, as the plan's R2.8 item already anticipates).

## R1.7 — Interpret Figure 9 in Discussion (assistant drafts, Max integrates)

Reviewer: give brief interpretations per block of the summary figure in main text.

- Move `figure*` (`fig:final`) from `conclusion.tex` → `discussion.tex`; add subsection **"Main Implications"** (≤1 page), one short paragraph per block:
  Accuracy · Simulating Communication · Workload Traces · Structured Output · Documentation · Simulation Time · Power Simulation Capabilities · Power Models (blocks verified from `paper/figures/final.png`).
- ☐ Assistant: draft block-by-block content (facts already in Results/Discussion, no new claims)
- ☐ **Max** (writing): integrate prose, move figure, wrap in `\rone{}`

## R1.8 — Reference formatting (assistant)

**Status: FIXES APPLIED to `paper/references.bib` (2026-11-10; backup at
`/tmp/references.bib.bak`); rebuild verified — 34 pages, bibtex clean, no undefined
citations, GridSim entry now renders correctly (line 1744 of the built PDF's text).
Note: `paper/` is gitignored, so the bib has no git history — work directly on the file.**

Full audit of `paper/references.bib` (66 entries; 60 cited, 6 uncited):

**Defects to fix (assistant will apply):**
1. `buyya_gridsim_2002` (line ~27): title contains a raw browser-tab string
   `…- {Buyya} - 2002 - {Concurrency} and {Computation}…- {Wiley} {Online} {Library}`.
   Real title: *GridSim: A toolkit for the modeling and simulation of distributed
   resource management and scheduling for Grid computing*; URL duplicates the DOI → drop URL.
2. URL duplicates DOI in 6 entries: `siddik_environmental_2021`, `katal_energy_2023`,
   `dragoni_microservices_2017`, `teixeira_sa_cloudreports_2014`,
   `xiao_architectural_2024`, `kliazovich_greencloud_2010` → drop the URL, keep DOI.
3. DOI format inconsistent: 45 entries use bare `doi = {10.…}`, 2 use
   `doi = {https://doi.org/…}` (lines 142, 210) → normalize to bare DOI.
4. `iea_energy_2025`: URL lacks scheme (`www.iea.org/...`) → add `https://`.
5. `iea_energy_2025`: `@techreport` with `chapter = {Technology}` (a Zotero
   section artifact) → remove; also add `institution = {IEA}` + `type = {Special
   Report}` if not present.
6. `Monserrate2022Cloud`: `month = {jan 27}` (non-standard; rendered oddly) → `month = jan,`
   + add `note = {Published 27 January 2022}`; unbraced capital `On` in title →
   brace-protect per elsarticle-num lowercasing.
7. Page ranges with single hyphen → en-dash: `casanova_simgrid_2025` (`103-125`),
   `buyya_energy_2024` (`24-38`).
8. `wohlin12` entry (only used inside a commented-out `\TODO` in threats.tex): capitalize
   field names for consistency or leave; not cited in text → no output impact.

**Decisions for Max (none block the fixes):**
- 6 uncited entries (`andrae_globalenergy_2015`, `casanova_lowering_2025`,
  `glanz_power_2012`, `mastenbroek_github_opendc_2021`, `zhou_fault_2021`, `wohlin12`):
  harmless to BibTeX (never rendered); keep as provenance or delete for tidiness.
- `glanz_power_2012` is an NYT piece modelled as `@article` without volume/number;
  renders acceptably; could switch to `@misc`+`howpublished` — cosmetic only.
- 46 `urldate`, 48 `abstract`, 18 `issn`, 7 `language` fields are ignored by
  `elsarticle-num` — keep for provenance, no rendering impact.
- `note = {Publisher: IOP Publishing}` in `siddik_environmental_2021` renders literally
  in the reference list — recommend deleting the note (publisher is in the journal field
  anyway) or rephrasing; same for `casanova_lowering_2025`.
- 3 field-name case outliers (`wohlin12` uses `Title/Author/...`) — harmless.
- No extra publication info is REQUIRED: all 66 entries resolve, all have years;
  elsarticle-num never prints months, so `month = jun` etc. is inert.

Build status: all 66 entries resolve, 34-page PDF, no undefined citations (verified in
`/tmp/texbuild`); re-run build after the fixes.

## R2.3 — Quantitative justification of 4-of-30 trace selection (assistant: analysis)

- ☑ FIX+RUN — `evaluation/analysis/r2_3_selection_analysis.py` written, run, outputs in `evaluation/analysis/outputs/`; sel-4 MREs reconcile with paper Table 4 (see summary doc); summary at `evaluation/analysis/r2_3_analysis_summary.md`

**Feasibility (verified)**: ground-truth pipeline reproduced from raw data — trapezoid integration of `*_host_power` reproduces `evaluation/energy/sockshop/A/100/18.energy` exactly (1091.567 J). All 30 reps per treatment available. Simulations exist only for the selected 4 traces, therefore comparison = same simulated totals vs (a) selected-4 GT mean vs (b) all-30 GT mean.

Results (all 32 treatments, best power model per simulator; `evaluation/analysis/r2_3_selection_analysis.py`):

| Simulator (best PM) | MRE sel-4 | MRE all-30 | Δ |
|---|---|---|---|
| CloudSim (cubic) | 0.536 | 0.497 | −0.039 |
| NetworkCloudSim (cubic) | 0.541 | 0.502 | −0.039 |
| DISSECT-CF (constant) | 0.442 | 0.458 | +0.016 |
| OpenDC (constant) | 0.122 | 0.104 | −0.018 |
| SimGrid (linear) | 0.421 | 0.437 | +0.016 |

Interpretation: |Δ| ≤ 0.04, **direction changes per simulator** → selection does not systematically inflate perceived accuracy. CV example (sockshop/A/100): 14.3% over all 30 vs 1.2% within selected 4 — report transparently.

- ☑ Assistant: analysis script + outputs (`r2_3_selection_analysis.py`, CSVs, summary)
- ☐ **Max** (writing): justification paragraph in *Trace Construction* (`experimentExecution.tex`); one sentence in *Internal Validity* (`threats.tex`); optional per-treatment table in appendix; all in `\rtwo{}`
- Honest-limitation note for Max's text: no simulations ran on the other 26 traces (defuses reviewer's methodological concern without overclaiming)

Reconciliation note (aggregation convention): paper Table 4 averages `|rel_error_total|`
at run level (30 runs × 4 reps weighted equally; notebook cell 4); the analysis above
averages per treatment (30 runs → rep mean first). Both agree at 2 decimals for
cloudsim/networkcloudsim/dissectcf/simgrid; for OpenDC run-level = 0.136 ("0.14" in
the paper) vs 0.122 treatment-level. Both conventions are defensible; the
sel-4-vs-all-30 comparison is unaffected (convention fixed, only GT set varies).

## R2.8 — CIs / legends / consistent terminology in Figs 5–8 (assistant: graphs)

- CIs/variability without re-runs:
  - over-time figures: GT band from the 4 repetitions (real measurement variability; sim runs nearly deterministic — CloudSim per-timestep std ≈ 0 W, DISSECT-CF max ≈ 0.48 W, SimGrid ~3e-6)
  - CDF figures: DKW simultaneous confidence bands
  - Fig 8 (runtimes): box+jitter already carries variability → add legend + per-simulator n
- Consistent terminology sweep (e.g. "cumulative energy consumption (J)"), per-simulator colours consistent across all figures.
- ☑ Assistant: implemented in `gen_revised_figures.py` together with R1.5 (GT min–max bands over-time, DKW 95% band on GT CDFs, n= labels on runtimes, consistent terminology: "time (s)", "cumulative energy consumption (J)", "total energy consumption (J)", "simulation time (s)")
- ☐ **Max** (writing): captions + response-letter note referencing R1.8 overlap

---

1. ☐ R1.8 (mechanical fix-ready; final bib edits done by Max, then re-verify build)
2. ☐ R1.7 draft (assistant content ready to write)
3. ☑ R1.5 + R2.8 figures (`paper/figures/*_per_simulator.pdf`, `runtimes_revised.pdf` await Max's `\includegraphics` swap)
4. ☐ R2.3 paper text (analysis done — text sources in `evaluation/analysis/r2_3_analysis_summary.md`)
5. ☐ R1.4 last (checklist ready in `plan/R1.4_verification_checklist.md`, needs Max's re-verification)

## Build recipe (for compile checks)

```bash
B=/tmp/texbuild   # or any scratch dir
mkdir -p $B && cd $B
# one-time: fetch missing pieces from CTAN
# elsarticle.zip (run elsarticle.ins → elsarticle.cls), siunitx.tds.zip, ulem.zip
cp /home/max/University/VU_Amsterdam/PhD/simpat-2026-cloud-simulators-energy/paper/{main.tex,references.bib,*.sty,*.cls,*.bst} $B/
cp -r .../paper/sections $B/ && cp -r .../paper/figures $B/
mkdir -p $B/vu-cs-research-thesis && cp -r .../paper/figures $B/vu-cs-research-thesis/figures
cd $B && pdflatex main && bibtex main && pdflatex main && pdflatex main
```

## Code exported from chat analysis

- `evaluation/analysis/r2_3_selection_analysis.py` — reproduces the R2.3 numbers above (run with project `.venv` python)
- `evaluation/analysis/gen_revised_figures.py` — R1.5/R2.8 figure generation
- `evaluation/analysis/outputs/r2_3_per_treatment.csv` — per repetition × power model numbers (R2.3)
- `evaluation/analysis/outputs/r2_3_per_simulator.csv` — per treatment × power model MREs (R2.3)
- `evaluation/analysis/outputs/r2_3_best_powermodel.tex` — ready-to-include LaTeX table (R2.3 appendix/response)
- `paper/figures/overtimes_per_simulator.{pdf,png}` — Fig 7 revision (R1.5/R2.8)
- `paper/figures/mean_total_pwr_per_simulator.{pdf,png}` — Fig 6 revision
- `paper/figures/double_cdf_per_simulator.{pdf,png}` — Fig 5 revision w/ DKW band
- `paper/figures/runtimes_revised.{pdf,png}` — Fig 8 revision w/ n= labels
