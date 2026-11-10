#!/usr/bin/env python3
"""
Revised figures for the revision (responds to R1.5 and R2.8).

R1.5: Figures with overlapping curves are split per simulator; power models become
      the within-plot dimension.
R2.8: confidence/variability indication everywhere + consistent terminology.

Figures generated (PDF for the paper, PNG previews next to them):

1. overtimes_per_simulator.pdf
   One subfigure per simulator (5 in a vertical stack), each showing the
   2 x 4 application-scenario-users grids (mean cumulative energy over time) with
   power models as hue/style. Ground truth drawn as black line with a band spanning
   the min..max total energy of the 4 selected ground-truth repetitions (R2.8).

2. double_cdf_per_simulator.pdf
   Per system (Sock Shop / Train Ticket) a row of 5 small CDF subplots, one per
   simulator (power models as curves), ground truth black. DKW simultaneous
   confidence bands (alpha=0.05) around the ground-truth CDF (R2.8). Simulator curves
   are 30-run means per repetition, so no band besides GT.

3. cdf_best_per_simulator.pdf   (revision of `double_cdf.pdf`, fig:cdf)
   Previously: one plot per system with all simulators. Now: same layout but one
   subplot per simulator (best power model only) + GT with DKW band.

4. mean_total_pwr_per_simulator.pdf   (revision of `mean_total_pwr.pdf`, fig:bestOverTime)
   Faceted by simulator (best power model), GT band from the 4 repetitions.

5. runtimes_revised.pdf   (revision of `runtimes-1.pdf`, fig:runtimes)
   Box + jitter (as before) with a legend naming each simulator's sample size n
   (R2.8: explicit variability indication). Data from evaluation/runtime/runtimes.csv.

Terminology used consistently (R2.8):
  x of over-time figures: "time (s)"
  y of over-time figures: "cumulative energy consumption (J)"
  CDF x: "total energy consumption (J)", y: "CDF"
  runtime y: "simulation time (s)"

Usage:
    .venv/bin/python evaluation/analysis/gen_revised_figures.py [which ...]
    `which` in {overtimes, fig6, cdf, runtimes} (default: all)
Outputs written to paper/figures/ with a `_revised` suffix (nothing overwritten).
"""

from __future__ import annotations

import json
import sys
from itertools import product
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

ROOT = Path(__file__).resolve().parents[2]
ENERGY_DIR = ROOT / "evaluation" / "energy"
GT_DIR = (
    ROOT / "evaluation" / "energy"
)  # selected GT traces live as {system}/{scenario}/{users}/{rep}.energy
RUNTIMES_CSV = ROOT / "evaluation" / "runtime" / "runtimes.csv"
OUT_DIR = ROOT / "paper" / "figures"

SIMULATORS = ["cloudsim", "networkcloudsim", "dissectcf", "opendc", "simgrid"]
#Pretty names in paper style
PRETTY = {
    "cloudsim": "CloudSim",
    "networkcloudsim": "NetworkCloudSim",
    "dissectcf": "DISSECT-CF",
    "opendc": "OpenDC",
    "simgrid": "SimGrid",
}
POWER_MODELS = ["constant", "linear", "square", "sqrt", "cubic"]
PM_STYLE = {  # dash cycle + colour cycle shared across figures
    "constant": dict(color="tab:blue", linestyle="-"),
    "linear": dict(color="tab:orange", linestyle="--"),
    "cubic": dict(color="tab:green", linestyle="-."),
    "square": dict(color="tab:red", linestyle=":"),
    "sqrt": dict(color="tab:purple", linestyle=(0, (3, 1, 1, 1))),
}
TREATMENTS = list(product(["sockshop", "trainticket"], ["A", "B"], [100, 1000]))
T_END = 300.0
GT_LABEL = "ground truth"

plt.rcParams.update({"font.size": 9, "axes.titlesize": 9, "legend.fontsize": 7})


# --------------------------------------------------------------------------- #
# Data loading helpers
# --------------------------------------------------------------------------- #
_SIM_CACHE: dict[str, pd.DataFrame] = {}


def sim_df(simulator: str) -> pd.DataFrame:
    if simulator not in _SIM_CACHE:
        df = pd.read_csv(ENERGY_DIR / f"{simulator}.energy")
        _SIM_CACHE[simulator] = df[df["time"] <= T_END]
    return _SIM_CACHE[simulator]


def gt_traces(system: str, scenario: str, users: int) -> dict[int, pd.DataFrame]:
    """The 4 selected ground-truth traces of a treatment: rep -> df(time, joules_total)."""
    out = {}
    base = GT_DIR / system / scenario / str(users)
    for f in sorted(base.glob("*.energy")):
        out[int(f.stem)] = pd.read_csv(f)
    return out


def gt_mean_curve(system: str, scenario: str, users: int) -> pd.DataFrame:
    """Mean cumulative energy over the 4 selected GT reps, joined on time."""
    traces = gt_traces(system, scenario, users)
    if not traces:
        raise FileNotFoundError(f"no GT traces for {system}/{scenario}/{users}")
    ref = next(iter(traces.values()))[["time"]].sort_values("time")
    cur = ref.copy()
    for rep, df in traces.items():
        cur = cur.merge(df[["time", "joules_total"]].rename(
            columns={"joules_total": f"r{rep}"}), on="time", how="inner")
    reps = [c for c in cur.columns if c.startswith("r")]
    cur["mean"] = cur[reps].mean(axis=1)
    cur["min"] = cur[reps].min(axis=1)
    cur["max"] = cur[reps].max(axis=1)
    return cur


def sim_curves(simulator: str, system: str, scenario: str, users: int) -> pd.DataFrame:
    """Mean cumulative energy per (powermodel, time) for one treatment."""
    df = sim_df(simulator)
    sub = df[(df["test_system"] == system) & (df["scenario"] == scenario)
             & (df["users"] == users)]
    return sub.groupby(["powermodel", "time"], as_index=False)["joules_total"].mean()


def dkw_band(n: int, alpha: float = 0.05) -> tuple[float, float]:
    """Half-widths of the DKW simultaneous confidence band for a CDF."""
    eps = np.sqrt(np.log(2.0 / alpha) / (2 * n))
    return eps, eps  # lower/upper half-width (symmetric)


# --------------------------------------------------------------------------- #
# 1. Over-time, per simulator (R1.5 split of fig:overtimes + R2.8 GT band)
# --------------------------------------------------------------------------- #
def fig_overtimes() -> None:
    """fig:overtimes revision (R1.5/R2.8).

    One 2x4 block of treatment subplots per simulator (rows: systems, columns:
    scenario/users); within each subplot only that simulator's power models plus
    the ground truth (mean line + min..max band of the 4 selected repetitions).
    """
    ncols = 4
    nrows = 2 * len(SIMULATORS)  # two treatment rows (systems) per simulator block
    fig, axes = plt.subplots(nrows, ncols, figsize=(16, 2.6 * nrows),
                             sharex=True, sharey="row")

    for b, sim in enumerate(SIMULATORS):
        for r_blk, system in enumerate(["sockshop", "trainticket"]):
            r = b * 2 + r_blk
            for c, (scenario, users) in enumerate(product(["A", "B"], [100, 1000])):
                ax = axes[r, c]
                curves = sim_curves(sim, system, scenario, users)
                for pm in curves["powermodel"].dropna().unique():
                    d = curves[curves["powermodel"] == pm]
                    ax.plot(d["time"], d["joules_total"], linewidth=1.1,
                            label=pm, **PM_STYLE.get(pm, {}))
                gt = gt_mean_curve(system, scenario, users)
                ax.fill_between(gt["time"], gt["min"], gt["max"],
                                color="black", alpha=0.18, linewidth=0,
                                label="_nolegend_")
                ax.plot(gt["time"], gt["mean"], color="black", linewidth=1.6,
                        label=GT_LABEL)
                if r == nrows - 1:
                    ax.set_xlabel("time (s)", fontsize=8)
                    ax.text(0.5, -0.30, f"scenario {scenario}, {users} users",
                            transform=ax.transAxes, ha="center", fontsize=8)
                if c == 0:
                    ax.set_ylabel(f"{PRETTY[sim]} — {system}\ncumulative energy (J)",
                                  fontsize=8)
                ax.tick_params(labelsize=7)

                if b == 0 and r_blk == 0:
                    ax.set_title(f"scenario {scenario}, {users} users", fontsize=8)

    # Legend must cover every PM; the first subplot (CloudSim) lacks `constant`.
    seen = {}
    for ax_row in axes:
        h, l = ax_row[0].get_legend_handles_labels()
        for hi, li in zip(h, l):
            if li not in seen and li != "_nolegend_":
                seen[li] = hi
    handles = []
    labels = []
    for pm in ["constant", "linear", "cubic", "square", "sqrt"]:
        if pm in seen:
            handles.append(seen[pm])
            labels.append(pm)
    if GT_LABEL in seen:
        handles.append(seen[GT_LABEL])
        labels.append(GT_LABEL)
    else:
        handles.append(plt.Line2D([], [], color="black", linewidth=1.6))
        labels.append(GT_LABEL)
    handles.append(plt.Rectangle((0, 0), 1, 1, color="black", alpha=0.18))
    labels.append("ground truth range (4 reps)")
    fig.legend(handles, labels, loc="upper center", ncol=7,
               bbox_to_anchor=(0.5, 1.003))
    fig.tight_layout(rect=(0, 0, 1, 0.99))
    fig.subplots_adjust(hspace=0.32)
    for ext in ("pdf", "png"):
        fig.savefig(OUT_DIR / f"overtimes_per_simulator.{ext}", bbox_inches="tight",
                    dpi=200 if ext == "png" else None)
    plt.close(fig)



# --------------------------------------------------------------------------- #
# 2. fig:bestOverTime revision — faceted per simulator + GT band
# --------------------------------------------------------------------------- #
def fig_best_over_time() -> None:
    best = {"cloudsim": "cubic", "networkcloudsim": "cubic", "dissectcf": "constant",
            "opendc": "constant", "simgrid": "linear"}
    # Aggregate over all treatments (paper's fig:bestOverTime is an overall average)
    fig, axes = plt.subplots(1, len(SIMULATORS), figsize=(16, 3.2), sharey=True)

    for i, sim in enumerate(SIMULATORS):
        ax = axes[i]
        df = sim_df(sim)
        pm = best[sim]
        sub = df[df["powermodel"] == pm]
        mean_curve = sub.groupby("time")["joules_total"].mean()
        ax.plot(mean_curve.index, mean_curve.values, linewidth=1.4,
                color=PM_STYLE[pm]["color"], label=pm)

        # GT mean band over the 32 treatments (mean of the 4 reps per treatment)
        ref = gt_mean_curve("sockshop", "A", 100)[["time"]]
        means, mins, maxs = [], [], []
        for _, row in ref.iterrows():
            t = row["time"]
            per_treat_mean, per_treat_min, per_treat_max = [], [], []
            for system, scenario, users in TREATMENTS:
                gt = gt_traces(system, scenario, users)
                vals = []
                for rep, tdf in gt.items():
                    d = tdf[np.isclose(tdf["time"], t)]
                    if len(d):
                        vals.append(float(d["joules_total"].iloc[0]))
                if vals:
                    per_treat_mean.append(np.mean(vals))
                    per_treat_min.append(np.min(vals))
                    per_treat_max.append(np.max(vals))
            means.append(np.mean(per_treat_mean))
            mins.append(np.mean(per_treat_min))
            maxs.append(np.mean(per_treat_max))
        ax.fill_between(ref["time"], mins, maxs, color="black", alpha=0.15,
                        linewidth=0)
        ax.plot(ref["time"], means, color="black", linewidth=1.8, label=GT_LABEL)
        # mean over the 32 treatments of the 4-rep mean
        ax.set_title(f"{PRETTY[sim]} ({pm})", fontsize=9)
        ax.set_xlabel("time (s)", fontsize=8)
        if i == 0:
            ax.set_ylabel("cumulative energy consumption (J)", fontsize=8)
        ax.legend(fontsize=7)
        ax.tick_params(labelsize=7)

    fig.tight_layout()
    for ext in ("pdf", "png"):
        fig.savefig(OUT_DIR / f"mean_total_pwr_per_simulator.{ext}",
                    bbox_inches="tight", dpi=200 if ext == "png" else None)
    plt.close(fig)


# --------------------------------------------------------------------------- #
# 3. CDF per simulator with DKW band on GT
# --------------------------------------------------------------------------- #
def _gt_cdf_vals(system: str, scenario: str | None, users: int | None):
    """Total-energy samples of the (selected) GT runs for CDF construction."""
    df = sim_df("cloudsim")  # any sim file carries joules_total_correct per rep
    sub = df[(df["time"].round() == 300)]
    if scenario is not None:
        sub = sub[(sub["test_system"] == system) & (sub["scenario"] == scenario)
                  & (sub["users"] == users)]
    vals = sub["joules_total_correct"].dropna().values
    return np.sort(vals)


def _plot_cdf(ax, samples: np.ndarray, label: str, color: str,
              linestyle: str = "-", band: bool = False) -> None:
    n = len(samples)
    x = samples
    y = np.arange(1, n + 1) / n
    xs = np.r_[x[0], x, x[-1]]
    ys = np.r_[0.0, y, y[-1]]
    if band:
        eps = np.sqrt(np.log(2.0 / 0.05) / (2 * n))
        ax.fill_between(xs, np.clip(ys - eps, 0, 1), np.clip(ys + eps, 0, 1),
                        color=color, alpha=0.18, linewidth=0,
                        label="_nolegend_", step="pre")
    ax.step(xs, ys, where="post", label=label, color=color,
            linestyle=linestyle, linewidth=1.4)


def fig_cdf_per_simulator(systems=("sockshop", "trainticket")) -> None:
    best = {"cloudsim": "cubic", "networkcloudsim": "cubic", "dissectcf": "constant",
            "opendc": "constant", "simgrid": "linear"}
    ncols, nrows = len(SIMULATORS), len(systems)
    fig, axes = plt.subplots(nrows, ncols, figsize=(3.4 * ncols, 3.0 * nrows),
                             sharex=False, sharey=True)

    for r, system in enumerate(systems):
        for c, sim in enumerate(SIMULATORS):
            ax = axes[r, c]
            df = sim_df(sim)
            pm = best[sim]
            sub = df[(df["time"].round() == 300)
                     & (df["powermodel"] == pm)
                     & (df["test_system"] == system)].dropna(
                         subset=["joules_total"])
            totals = (sub.groupby(["test_system", "scenario", "users", "repetition"])
                      ["joules_total"].mean().values) if len(sub) else np.array([])
            pm_label = pm
            for pmm in sorted(df["powermodel"].dropna().unique()):
                sub = df[(df["time"].round() == 300) & (df["powermodel"] == pmm)
                         & (df["test_system"] == system)].dropna(
                             subset=["joules_total"])
                if len(sub) == 0:
                    continue
                t = (sub.groupby(["test_system", "scenario", "users", "repetition"])
                     ["joules_total"].mean().values)
                _plot_cdf(ax, np.sort(t), label=pmm, color=PM_STYLE[pmm]["color"],
                          linestyle=PM_STYLE[pmm]["linestyle"],
                          band=False)
            gt = _gt_cdf_vals(system, None, None)
            _plot_cdf(ax, gt, label=GT_LABEL, color="black", band=True)
            if r == nrows - 1:
                ax.set_xlabel("total energy consumption (J)", fontsize=8)
            if c == 0:
                ax.set_ylabel(f"{system}\nCDF", fontsize=8)
            ax.set_title(f"{PRETTY[sim]}" + (f" ({pm_label})" if len(df['powermodel'].dropna().unique()) == 1 else ""),
                         fontsize=8)
            ax.set_xlim(600, 2400)
            ax.tick_params(labelsize=7)

    seen = {}
    for ax_row in axes:
        for ax in ax_row:
            h, l = ax.get_legend_handles_labels()
            for hi, li in zip(h, l):
                if li not in seen and li != "_nolegend_":
                    seen[li] = hi
    handles = []
    labels = []
    for pm in ["constant", "linear", "cubic", "square", "sqrt"]:
        if pm in seen:
            handles.append(seen[pm])
            labels.append(pm)
    if GT_LABEL in seen:
        handles.append(seen[GT_LABEL])
    else:
        handles.append(plt.Line2D([], [], color="black", linewidth=1.4))
    labels.append(GT_LABEL)
    handles.append(plt.Rectangle((0, 0), 1, 1, color="black", alpha=0.18))
    labels.append("ground truth 95% band")
    fig.legend(handles, labels, loc="upper center", ncol=7,
               bbox_to_anchor=(0.5, 1.01))
    fig.tight_layout(rect=(0, 0, 1, 0.96))
    for ext in ("pdf", "png"):
        fig.savefig(OUT_DIR / f"double_cdf_per_simulator.{ext}",
                    bbox_inches="tight", dpi=200 if ext == "png" else None)
    plt.close(fig)


# --------------------------------------------------------------------------- #
# 4. Runtimes with explicit n (fig:runtimes)
# --------------------------------------------------------------------------- #
def _load_runtimes() -> pd.DataFrame:
    """Raw per-run simulation times, same sources as evaluation/runtime/get_runtimes.py."""
    frames = []
    raw_sources = [
        ("CloudSim", ROOT / "simulators/cloudsim/runs_trace.time"),
        ("DISSECT-CF", ROOT / "simulators/dissect-cf/runs.time"),
        ("NetworkCloudSim", ROOT / "simulators/cloudsim/runs_trace_network.time"),
        ("OpenDC", ROOT / "simulators/openDC/runs.time"),
        ("SimGrid", ROOT / "simulators/simgrid/runs.time"),
        ("SimGrid with comm.", ROOT / "simulators/simgrid/runs_with_comms.time"),
    ]
    for label, path in raw_sources:
        if not path.exists():
            print(f"  ! missing runtime source: {path}")
            continue
        d = pd.read_csv(path)
        if "users" in d.columns:
            d = d[d["users"] == 1000]  # get_runtimes.py filters users==1000
        if label == "OpenDC":
            # runs.time stores one row per experiment run; each experiment simulates
            # len(workloads) workloads -> expand to per-workload times (same as
            # get_runtimes.py opendc_row)
            times = []
            for _, row in d.iterrows():
                exp_path = ROOT / "simulators/openDC" / str(row["file"])
                with open(exp_path) as f:
                    n_workloads = len(json.load(f)["workloads"])
                times += [row["time"] / n_workloads] * n_workloads
            d = pd.DataFrame({"time": times})
        else:
            d = pd.DataFrame({"time": d["time"]})
        d.insert(0, "simulator", label)
        frames.append(d)
    return pd.concat(frames, ignore_index=True)


def fig_runtimes() -> None:
    df = _load_runtimes()
    order = ["CloudSim", "DISSECT-CF", "NetworkCloudSim", "OpenDC", "SimGrid",
             "SimGrid with comm."]
    fig, ax = plt.subplots(figsize=(9, 4.2))
    ax = sns_box(df, order, ax)
    counts = df.groupby("simulator")["time"].count()
    labels = [f"{o}\n(n={counts.get(o, 0)})" for o in order]
    ax.set_xticks(range(len(order)))
    ax.set_xticklabels(labels, fontsize=8)
    ax.set_xlabel("simulator", fontsize=9)
    ax.set_ylabel("simulation time (s)", fontsize=9)
    ax.tick_params(axis="y", labelsize=8)

    fig.tight_layout()
    for ext in ("pdf", "png"):
        fig.savefig(OUT_DIR / f"runtimes_revised.{ext}", bbox_inches="tight",
                    dpi=200 if ext == "png" else None)
    plt.close(fig)


def sns_box(df, order, ax):
    import seaborn as sns
    sns.stripplot(data=df, x="simulator", y="time", order=order, ax=ax,
                  color="#E57E00", alpha=0.35, jitter=0.35, s=4, zorder=1)
    ax = sns.boxplot(data=df, x="simulator", y="time", order=order, ax=ax,
                     showcaps=True, boxprops={"facecolor": "None", "zorder": 10},
                     medianprops={"color": "black", "zorder": 11},
                     whiskerprops={"zorder": 10}, capprops={"zorder": 10})
    return ax


# --------------------------------------------------------------------------- #
def main(argv: list[str]) -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    which = argv[1:] or ["overtimes", "fig6", "cdf", "runtimes"]
    if "overtimes" in which:
        print("generating overtimes_per_simulator ...")
        fig_overtimes()
    if "fig6" in which:
        print("generating mean_total_pwr_per_simulator ...")
        fig_best_over_time()
    if "cdf" in which:
        print("generating double_cdf_per_simulator ...")
        fig_cdf_per_simulator()
    if "runtimes" in which:
        print("generating runtimes_revised ...")
        fig_runtimes()
    print("done ->", OUT_DIR)


if __name__ == "__main__":
    main(sys.argv)
