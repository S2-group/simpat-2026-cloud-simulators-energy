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
def _overtimes_legend_handles(axes) -> tuple[list, list]:
    """Collect complete legend handles (some panels lack some power models)."""
    seen = {}
    for ax_row in axes:
        for ax in (ax_row if hasattr(ax_row, "__len__") else [ax_row]):
            h, l = ax.get_legend_handles_labels()
            for hi, li in zip(h, l):
                if li not in seen and li != "_nolegend_":
                    seen[li] = hi
    handles, labels = [], []
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
    return handles, labels


def fig_overtimes() -> None:
    """fig:overtimes revision (R1.5/R2.8) — ONE FIGURE PER SYSTEM.

    40 panels in one figure was unreadable (Max). Now:
      overtimes_sockshop_per_simulator.pdf  (4 wide x 5 tall = 20 panels)
      overtimes_trainticket_per_simulator.pdf
    Rows = simulators, columns = scenario/users treatments. Each panel:
    that simulator's power models + GT mean and min..max band of the 4 reps.
    """
    ncols = 4  # treatments: (A,100) (A,1000) (B,100) (B,1000)
    for system in ["sockshop", "trainticket"]:
        nrows = len(SIMULATORS)
        fig, axes = plt.subplots(nrows, ncols, figsize=(11.0, 2.4 * nrows),
                                 sharex=True, sharey=True)

        for r, sim in enumerate(SIMULATORS):
            for c, (scenario, users) in enumerate(
                    product(["A", "B"], [100, 1000])):
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
                if r == 0:
                    ax.set_title(f"scenario {scenario}, {users} users", fontsize=8)
                if c == 0:
                    ax.set_ylabel(f"{PRETTY[sim]}\ncumulative energy (J)",
                                  fontsize=8)
                ax.tick_params(labelsize=7)

        handles, labels = _overtimes_legend_handles(axes)
        fig.legend(handles, labels, loc="upper center", ncol=7,
                   bbox_to_anchor=(0.5, 1.003))
        fig.tight_layout(rect=(0, 0, 1, 0.985))
        fig.subplots_adjust(hspace=0.28)
        for ext in ("pdf", "png"):
            fig.savefig(OUT_DIR / f"overtimes_{system}_per_simulator.{ext}",
                        bbox_inches="tight", dpi=200 if ext == "png" else None)
        plt.close(fig)



# --------------------------------------------------------------------------- #
# 2. fig:bestOverTime revision — faceted per simulator + GT band
# --------------------------------------------------------------------------- #
def fig_best_over_time() -> None:
    """fig:bestOverTime revision (R1.5): one PANEL PER SIMULATOR, stacked vertically.

    Max: 5-across was too wide to read; a 5x1 vertical stack keeps each panel
    full text width and readable. Panels share the x axis (time) and y axis
    (cumulative energy). Each panel: simulator's best power model curve +
    ground truth mean over all 32 treatments and the mean-over-treatments of
    the per-treatment 4-rep min..max range (R2.8 variability indication).
    """
    best = {"cloudsim": "cubic", "networkcloudsim": "cubic", "dissectcf": "constant",
            "opendc": "constant", "simgrid": "linear"}
    fig, axes = plt.subplots(len(SIMULATORS), 1, figsize=(7.0, 2.1 * len(SIMULATORS)),
                             sharex=True, sharey=True)

    # Precompute GT curves (shared across panels)
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

    for i, sim in enumerate(SIMULATORS):
        ax = axes[i]
        df = sim_df(sim)
        pm = best[sim]
        mean_curve = sub = df[df["powermodel"] == pm]
        mean_curve = mean_curve.groupby("time")["joules_total"].mean()
        ax.plot(mean_curve.index, mean_curve.values, linewidth=1.4,
                color=PM_STYLE[pm]["color"], label=pm)
        ax.fill_between(ref["time"], mins, maxs, color="black", alpha=0.15,
                        linewidth=0)
        ax.plot(ref["time"], means, color="black", linewidth=1.8, label=GT_LABEL)
        ax.set_ylabel(f"{PRETTY[sim]}\n({pm})", fontsize=8)
        ax.legend(fontsize=7, loc="lower right")
        ax.tick_params(labelsize=7)

    axes[-1].set_xlabel("time (s)", fontsize=8)
    fig.supylabel("cumulative energy consumption (J)", fontsize=9)
    fig.tight_layout(rect=(0.015, 0, 1, 1))
    for ext in ("pdf", "png"):
        fig.savefig(OUT_DIR / f"mean_total_pwr_per_simulator.{ext}",
                    bbox_inches="tight", dpi=200 if ext == "png" else None)
    plt.close(fig)


# --------------------------------------------------------------------------- #
# 3. CDF per simulator with DKW band on GT
# --------------------------------------------------------------------------- #
def _gt_cdf_vals(system: str, scenario: str | None, users: int | None):
    """Total energy of each independent selected GT repetition of `system`.

    The .energy files repeat each GT total on every simulation-run row (120x per
    repetition), so de-duplicate to one value per (scenario, users, repetition):
    16 per system (4 treatments x 4 reps). Using the duplicated rows would pool
    nothing wrong for the CDF shape but inflate n for the DKW band ~120x.
    """
    df = sim_df("cloudsim")  # any sim file carries joules_total_correct per rep
    sub = df[(df["time"].round() == 300) & (df["test_system"] == system)]
    if scenario is not None:
        sub = sub[(sub["scenario"] == scenario) & (sub["users"] == users)]
    vals = (sub.groupby(["scenario", "users", "repetition"])
            ["joules_total_correct"].first().dropna().values)
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
    """fig:cdf revision (R1.5/R2.8) — one figure PER SYSTEM (Max: 2x5 grid too dense).

    Each figure: 5 panels in a row (one per simulator). Panel: CDF step curves
    per power model of the per-repetition mean totals (30 simulation runs each);
    black GT step CDF with DKW simultaneous 95% confidence band.
    """
    for system in systems:
        fig, axes = plt.subplots(1, len(SIMULATORS),
                                 figsize=(3.0 * len(SIMULATORS), 3.4),
                                 sharey=True)
        axes = np.atleast_1d(axes)

        for c, sim in enumerate(SIMULATORS):
            ax = axes[c]
            df = sim_df(sim)
            for pmm in sorted(df["powermodel"].dropna().unique()):
                sub = df[(df["time"].round() == 300)
                         & (df["powermodel"] == pmm)
                         & (df["test_system"] == system)].dropna(
                             subset=["joules_total"])
                if len(sub) == 0:
                    continue
                t = (sub.groupby(["test_system", "scenario", "users", "repetition"])
                     ["joules_total"].mean().values)
                _plot_cdf(ax, np.sort(t), label=pmm, color=PM_STYLE[pmm]["color"],
                          linestyle=PM_STYLE[pmm]["linestyle"], band=False)
            gt = _gt_cdf_vals(system, None, None)
            _plot_cdf(ax, gt, label=GT_LABEL, color="black", band=True)
            ax.set_xlabel("total energy consumption (J)", fontsize=8)
            ax.set_title(f"{PRETTY[sim]}", fontsize=8)
            if c == 0:
                ax.set_ylabel(f"CDF — {system}", fontsize=8)
            ax.set_xlim(600, 2400)
            ax.tick_params(labelsize=7)

        seen = {}
        for ax in axes:
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
        fig.tight_layout(rect=(0, 0, 1, 0.94))
        fig.subplots_adjust(wspace=0.16)
        for ext in ("pdf", "png"):
            fig.savefig(OUT_DIR / f"cdf_{system}_per_simulator.{ext}",
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
        print("generating overtimes_{sockshop,trainticket}_per_simulator ...")
        fig_overtimes()
    if "fig6" in which:
        print("generating mean_total_pwr_per_simulator (vertical stack) ...")
        fig_best_over_time()
    if "cdf" in which:
        print("generating cdf_{sockshop,trainticket}_per_simulator ...")
        fig_cdf_per_simulator()
    if "runtimes" in which:
        print("generating runtimes_revised ...")
        fig_runtimes()
    print("done ->", OUT_DIR)


if __name__ == "__main__":
    main(sys.argv)
