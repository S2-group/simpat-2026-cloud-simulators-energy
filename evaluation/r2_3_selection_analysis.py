#!/usr/bin/env python3
from __future__ import annotations

import csv
import glob
import re
import statistics
from collections import defaultdict
from pathlib import Path

# --------------------------------------------------------------------------- #
# Configuration
# --------------------------------------------------------------------------- #
ROOT = Path(__file__).resolve().parents[1]          # repo root
GT_GLOB = (
    ROOT / "ground-truth" / "vuDevOps" / "data_collection"
    / "*-data" / "scenario_*" / "baseline" / "*" / "repetition_*" / "metrics.csv"
)
SIM_ENERGY_DIR = ROOT / "evaluation" / "energy"
SELECTION_CSV = ROOT / "trace-generation" / "trace-selection" / "trace_selection.csv"
OUT_DIR = Path(__file__).resolve().parent / "outputs"

SIMULATORS = ["cloudsim", "networkcloudsim", "dissectcf", "opendc", "simgrid"]
# Best power model per simulator (consistent with the paper's fig:bestOverTime).
BEST_PM = {
    "cloudsim": "cubic",
    "networkcloudsim": "cubic",
    "dissectcf": "constant",
    "opendc": "constant",
    "simgrid": "linear",
}
T_END = 300.0  # seconds; treatments are 5 min measurements


def ground_truth_totals() -> dict[tuple[str, str, int, int], float]:
    """Total host energy (J) per (system, scenario, users, repetition) for all 30 reps.

    Reproduces the paper's ground-truth construction (eq. energy_trapezoid):
    E = sum_i (p_{i-1} + p_i)/2 * (t_i - t_{i-1}).
    """
    totals: dict[tuple[str, str, int, int], float] = {}
    for path in sorted(glob.glob(str(GT_GLOB))):
        m = re.search(
            r"(\w+)-data/scenario_(\w)/baseline/(\d+)/repetition_(\d+)", str(path)
        )
        system, scenario, users, rep = (
            m.group(1), m.group(2), int(m.group(3)), int(m.group(4))
        )
        with open(path, newline="") as fh:
            rows = list(csv.DictReader(fh))
        host_col = next(k for k in rows[0] if "host_power" in k)
        prev = None  # (power_W, timestamp)
        total_j = 0.0
        for row in rows:
            pw = (
                float(row[host_col])
                if row[host_col] not in ("", "nan", "NaN", None)
                else None
            )
            t = float(row["time"])
            if prev is not None and pw is not None:
                total_j += (prev[0] + pw) / 2.0 * (t - prev[1])
            prev = (pw, t)
        totals[(system, scenario, users, rep)] = total_j
    return totals


def selected_reps() -> set[tuple[str, str, int, int]]:
    """The 4 repetitions per treatment selected by median-energy trace selection."""
    out: set[tuple[str, str, int, int]] = set()
    with open(SELECTION_CSV, newline="") as fh:
        for row in csv.DictReader(fh):
            if int(row["selection_rank"]) < 4:
                out.add(
                    (
                        row["test_system"],
                        row["scenario"],
                        int(row["users"]),
                        int(row["repetition"]),
                    )
                )
    return out


def sim_mean_totals(path: Path) -> dict[tuple, dict[str, float]]:
    """Mean simulated total energy (J) at t=300 over the 30 simulation runs,
    keyed by (system, scenario, users, repetition) -> {powermodel: joules}."""
    out: dict[tuple, dict[str, float]] = defaultdict(dict)
    acc: dict[tuple, dict[str, list[float]]] = defaultdict(lambda: defaultdict(list))
    with open(path, newline="") as fh:
        for row in csv.DictReader(fh):
            if float(row["time"]) != T_END:
                continue
            key = (
                row["test_system"],
                row["scenario"],
                int(row["users"]),
                int(row["repetition"]),
            )
            acc[key][row["powermodel"]].append(float(row["joules_total"]))
    for key, per_pm in acc.items():
        for pm, vals in per_pm.items():
            out[key][pm] = statistics.mean(vals)
    return out


def gt_mean_for(
    treatment: tuple[str, str, int],
    totals: dict,
    restrict: set | None,
) -> float:
    """Mean GT total over reps of a treatment, optionally restricted to selected."""
    system, scenario, users = treatment
    vals = [
        v for (s, sc, u, r), v in totals.items()
        if (s, sc, u) == treatment and (restrict is None or (s, sc, u, r) in restrict)
    ]
    return statistics.mean(vals)


def main() -> None:
    OUT_DIR.mkdir(exist_ok=True)

    print("Recomputing ground-truth totals for all treatments x 30 reps ...")
    gt_totals = ground_truth_totals()
    print(f"  {len(gt_totals)} ground-truth repetitions")

    selected = selected_reps()
    print(f"  {len(selected)} selected (rank<4) repetitions")

    per_treatment_rows = []
    per_sim_rows = []
    for sim in SIMULATORS:
        path = SIM_ENERGY_DIR / f"{sim}.energy"
        print(f"Processing {sim} ...")
        sim_totals = sim_mean_totals(path)

        abs_err_sel: dict[tuple, list[float]] = defaultdict(list)
        abs_err_all: dict[tuple, list[float]] = defaultdict(list)

        for (system, scenario, users, rep), per_pm in sim_totals.items():
            treatment = (system, scenario, users)
            gt_sel = gt_mean_for(treatment, gt_totals, restrict=selected)
            gt_all = gt_mean_for(treatment, gt_totals, restrict=None)
            for pm, joules in per_pm.items():
                e_sel = abs(gt_sel - joules) / gt_sel
                e_all = abs(gt_all - joules) / gt_all
                abs_err_sel[(system, scenario, users, pm)].append(e_sel)
                abs_err_all[(system, scenario, users, pm)].append(e_all)
                per_treatment_rows.append(
                    dict(
                        simulator=sim, system=system, scenario=scenario, users=users,
                        repetition=rep, powermodel=pm, sim_total_j=joules,
                        gt_sel4_j=gt_sel, gt_all30_j=gt_all,
                        rel_err_sel4=e_sel, rel_err_all30=e_all,
                    )
                )

        for (system, scenario, users, pm) in sorted(abs_err_sel):
            m_sel = statistics.mean(abs_err_sel[(system, scenario, users, pm)])
            m_all = statistics.mean(abs_err_all[(system, scenario, users, pm)])
            per_sim_rows.append(
                dict(
                    simulator=sim, system=system, scenario=scenario, users=users,
                    powermodel=pm,
                    mre_sel4=round(m_sel, 4),
                    mre_all30=round(m_all, 4),
                    delta=round(m_all - m_sel, 4),
                    best=(pm == BEST_PM[sim]),
                )
            )

    # ------------------------------------------------------------------ output
    with open(OUT_DIR / "r2_3_per_treatment.csv", "w", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=list(per_treatment_rows[0]))
        w.writeheader()
        w.writerows(per_treatment_rows)

    with open(OUT_DIR / "r2_3_per_simulator.csv", "w", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=list(per_sim_rows[0]))
        w.writeheader()
        w.writerows(per_sim_rows)

    # Aggregated per simulator over best power model (the paper's headline numbers)
    print("\n=== MRE: selected-4 vs all-30 (best power model, all 32 treatments) ===")
    print(f"{'simulator':16s} {'PM':10s} {'MRE sel4':>9s} {'MRE all30':>10s} {'delta':>8s}")
    latex_rows = []
    for sim in SIMULATORS:
        rows = [r for r in per_sim_rows if r["simulator"] == sim and r["best"]]
        m_sel = round(statistics.mean(r["mre_sel4"] for r in rows), 4)
        m_all = round(statistics.mean(r["mre_all30"] for r in rows), 4)
        print(f"{sim:16s} {BEST_PM[sim]:10s} {m_sel:9.4f} {m_all:10.4f} {m_all-m_sel:+8.4f}")
        latex_rows.append((sim, BEST_PM[sim], m_sel, m_all))

    with open(OUT_DIR / "r2_3_best_powermodel.tex", "w") as fh:
        fh.write("% Auto-generated by r2_3_selection_analysis.py -- R2.3 response table\n")
        fh.write("\\begin{tabular}{lrrr}\n")
        fh.write("\\textbf{Simulator} & \\textbf{Power model} & "
                 "\\textbf{MRE (selected 4)} & \\textbf{MRE (all 30)} \\\\ \\hline\n")
        for sim, pm, m_sel, m_all in latex_rows:
            fh.write(f"\\textit{{{sim}}} & {pm} & {m_sel} & {m_all} \\\\\n")
        fh.write("\\end{tabular}\n")

    # variability example (CV over reps) for sockshop/A/100
    system, scenario, users = "sockshop", "A", 100
    all_vals = [v for (s, sc, u, r), v in gt_totals.items()
                if (s, sc, u) == (system, scenario, users)]
    sel_vals = [v for (s, sc, u, r), v in gt_totals.items()
                if (s, sc, u) == (system, scenario, users)
                and (s, sc, u, r) in selected]
    cv_all = statistics.stdev(all_vals) / statistics.mean(all_vals)
    cv_sel = statistics.stdev(sel_vals) / statistics.mean(sel_vals)
    print(f"\nCV GT totals {system}/{scenario}/{users}: "
          f"all30={cv_all:.4f}  sel4={cv_sel:.4f}")

    print(f"\nWrote {OUT_DIR}/r2_3_per_treatment.csv, r2_3_per_simulator.csv, "
          f"r2_3_best_powermodel.tex")


if __name__ == "__main__":
    main()
