#!/usr/bin/env python3
from __future__ import annotations

import glob
import json
import re
import sys
from itertools import product
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from matplotlib.gridspec import GridSpec

ROOT = Path(__file__).resolve().parents[1]
ENERGY_DIR = ROOT / "evaluation" / "energy"   # sims: {sim}.energy; GT: {system}/{scenario}/{users}/{rep}.energy
RAW_GT_DIR = ROOT / "ground-truth" / "vuDevOps" / "data_collection"
OUT_DIR = ROOT / "figures"

SIMULATORS = ["cloudsim", "networkcloudsim", "dissectcf", "opendc", "simgrid"]
PRETTY = {"cloudsim": "CloudSim", "networkcloudsim": "NetworkCloudSim",
          "dissectcf": "DISSECT-CF", "opendc": "OpenDC", "simgrid": "SimGrid"}
ROW_LABEL = {**PRETTY, "networkcloudsim": "Network-\nCloudSim"}
SYS_PRETTY = {"sockshop": "Sock Shop", "trainticket": "Train Ticket"}
BEST_PM = {"cloudsim": "cubic", "networkcloudsim": "cubic", "dissectcf": "constant",
           "opendc": "constant", "simgrid": "linear"}
PM_ORDER = ["constant", "linear", "cubic", "square", "sqrt"]
PM_STYLE = {  # colour + dash pattern shared by every figure
    "constant": dict(color="tab:blue", linestyle="-"),
    "linear": dict(color="tab:orange", linestyle="--"),
    "cubic": dict(color="tab:green", linestyle="-."),
    "square": dict(color="tab:red", linestyle=":"),
    "sqrt": dict(color="tab:purple", linestyle=(0, (3, 1, 1, 1))),
}
SYSTEMS = ["sockshop", "trainticket"]
SCEN_USERS = list(product(["A", "B"], [100, 1000]))
TREATMENTS = [(s, sc, u) for s in SYSTEMS for sc, u in SCEN_USERS]
T_END = 300.0
GRID = np.arange(5.0, T_END + 1, 5.0)          # GT sampling grid (5 s)

TEXTWIDTH_IN = 6.5                              # elsarticle 3p \textwidth = 468 pt
SIM_LW, GT_LW = 1.6, 1.3
SIM_BAND_ALPHA, GT_BAND_ALPHA = 0.18, 0.22
GT_LABEL = "ground truth (mean of 4 selected reps)"
GT_BAND_LABEL = "ground truth P10–P90 (all 30 reps)"
SIM_BAND_LABEL = "simulator min–max (4 traces)"

plt.rcParams.update({
    "font.size": 7, "axes.titlesize": 7.5, "axes.labelsize": 7.5,
    "xtick.labelsize": 6.5, "ytick.labelsize": 6.5,
    "legend.fontsize": 7, "legend.title_fontsize": 7.5,
    "axes.linewidth": 0.6, "xtick.major.width": 0.5, "ytick.major.width": 0.5,
    "xtick.major.size": 2, "ytick.major.size": 2,
    "axes.grid": True, "grid.linewidth": 0.4, "grid.color": "0.88",
})

# Label hierarchy (same in every figure):
#   outer axis labels  9 pt            e.g. "time (s)"
#   group headers      8.5 pt          e.g. "Sock Shop" (with a rule underneath)
#   panel labels       7.5 pt  (simulator / treatment / application)
#   tick labels        6.5 pt
OUTER = dict(fontsize=9)
GROUP = dict(fontsize=8.5)
STRIP = dict(fontsize=7.5)


# --------------------------------------------------------------------------- #
# Data helpers
# --------------------------------------------------------------------------- #
_SIM_CACHE: dict[str, pd.DataFrame] = {}


def sim_df(simulator: str) -> pd.DataFrame:
    if simulator not in _SIM_CACHE:
        df = pd.read_csv(ENERGY_DIR / f"{simulator}.energy")
        df = df[df["time"] <= T_END].copy()
        df["time"] = df["time"].round(2)
        _SIM_CACHE[simulator] = df
    return _SIM_CACHE[simulator]


def sim_band(sim: str, system: str, scenario: str, users: int) -> pd.DataFrame:
    """Per (powermodel, time): mean / min / max over the 4 simulated traces.

    Each trace's curve is first averaged over its 30 simulation runs.
    """
    df = sim_df(sim)
    sub = df[(df["test_system"] == system) & (df["scenario"] == scenario)
             & (df["users"] == users)]
    per_trace = (sub.groupby(["powermodel", "repetition", "time"])["joules_total"]
                 .mean().reset_index())
    return (per_trace.groupby(["powermodel", "time"])["joules_total"]
            .agg(["mean", "min", "max"]).reset_index())


def gt_selected(system: str, scenario: str, users: int) -> pd.DataFrame:
    """Mean cumulative energy of the 4 selected GT repetitions on GRID."""
    curves = []
    for f in sorted((ENERGY_DIR / system / scenario / str(users)).glob("*.energy")):
        d = pd.read_csv(f).dropna(subset=["time"])
        curves.append(np.interp(GRID, d["time"].values, d["joules_total"].values))
    return pd.DataFrame({"time": GRID, "mean": np.mean(curves, axis=0)})


_GT30_CACHE: dict[tuple, np.ndarray] = {}


def gt_all30(system: str, scenario: str, users: int) -> np.ndarray:
    """Cumulative energy curves (30 x len(GRID)) of ALL measured repetitions.

    Trapezoid rule over the raw host power (paper eq. energy_trapezoid); verified to
    reproduce evaluation/energy/{...}.energy exactly for the selected repetitions.
    """
    key = (system, scenario, users)
    if key not in _GT30_CACHE:
        pattern = str(RAW_GT_DIR / f"{system}-data" / f"scenario_{scenario}" / "baseline"
                      / str(users) / "repetition_*" / "metrics.csv")
        curves = []
        for path in sorted(glob.glob(pattern), key=lambda p: int(re.search(r"repetition_(\d+)", p).group(1))):
            d = pd.read_csv(path).sort_values("time")
            pw = d[[c for c in d.columns if "host_power" in c][0]].values
            t = d["time"].values - d["time"].values[0]
            ok = ~np.isnan(pw)
            t, pw = t[ok], pw[ok]
            e = np.concatenate([[0.0], np.cumsum((pw[1:] + pw[:-1]) / 2 * np.diff(t))])
            curves.append(np.interp(GRID, t, e))
        _GT30_CACHE[key] = np.array(curves)
    return _GT30_CACHE[key]


def gt_band(system: str, scenario: str, users: int) -> tuple[np.ndarray, np.ndarray]:
    c = gt_all30(system, scenario, users)
    return np.percentile(c, 10, axis=0), np.percentile(c, 90, axis=0)


def gt_cdf_vals(system: str) -> np.ndarray:
    """Total energy of each selected GT repetition of `system` (16 values).

    The sim .energy files repeat each GT total on every run row; de-duplicate to one
    value per (scenario, users, repetition) so n is right for the DKW band.
    """
    df = sim_df("cloudsim")
    sub = df[(df["time"].round() == T_END) & (df["test_system"] == system)]
    return np.sort(sub.groupby(["scenario", "users", "repetition"])
                   ["joules_total_correct"].first().dropna().values)


def add_legends(fig, pms, gt_band_label, sim_band=True, gt_label=GT_LABEL,
                y=1.0, pm_ncol=3, x_split=0.5):
    """Two titled legends at the top: 'Power model' (left) and
    'Ground truth & variability' (right)."""
    pm_h = [plt.Line2D([], [], linewidth=SIM_LW, **PM_STYLE[p]) for p in pms]
    gt_h = [plt.Line2D([], [], color="black", linewidth=GT_LW),
            plt.Rectangle((0, 0), 1, 1, color="black", alpha=GT_BAND_ALPHA, linewidth=0)]
    gt_l = [gt_label, gt_band_label]
    if sim_band:
        gt_h.append(plt.Rectangle((0, 0), 1, 1, facecolor="0.45", alpha=SIM_BAND_ALPHA + 0.07,
                                  linewidth=0))
        gt_l.append(SIM_BAND_LABEL)
    common = dict(frameon=False, handlelength=2.6, columnspacing=1.2, borderaxespad=0.2)
    leg1 = fig.legend(pm_h, list(pms), title="Power model", ncol=pm_ncol,
                      loc="upper right", bbox_to_anchor=(x_split - 0.01, y), **common)
    leg2 = fig.legend(gt_h, gt_l, title="Ground truth & variability", ncol=1,
                      loc="upper left", bbox_to_anchor=(x_split + 0.01, y), **common)


def save(fig, name: str) -> None:
    for ext in ("pdf", "png"):
        fig.savefig(OUT_DIR / f"{name}.{ext}", dpi=220 if ext == "png" else None)
    plt.close(fig)


# --------------------------------------------------------------------------- #
# fig:cdf -- 2 x 5 small multiples (rows: application, cols: simulator)
# --------------------------------------------------------------------------- #
def fig_cdf() -> None:
    fig, axes = plt.subplots(len(SYSTEMS), len(SIMULATORS), figsize=(TEXTWIDTH_IN, 3.3),
                             sharex=True, sharey=True,
                             gridspec_kw=dict(wspace=0.1, hspace=0.14, left=0.085,
                                              right=0.945, top=0.735, bottom=0.125))
    for r, system in enumerate(SYSTEMS):
        gtv = gt_cdf_vals(system)
        n = len(gtv)
        eps = np.sqrt(np.log(2 / 0.05) / (2 * n))
        for c, sim in enumerate(SIMULATORS):
            ax = axes[r, c]
            df = sim_df(sim)
            end = df[(df["time"].round() == T_END) & (df["test_system"] == system)]
            for pm in PM_ORDER:
                sub = end[end["powermodel"] == pm].dropna(subset=["joules_total"])
                if sub.empty:
                    continue
                t = np.sort(sub.groupby(["scenario", "users", "repetition"])
                            ["joules_total"].mean().values)
                k = len(t)
                ax.step(np.r_[t[0], t, t[-1]], np.r_[0, np.arange(1, k + 1) / k, 1],
                        where="post", linewidth=SIM_LW, **PM_STYLE[pm])
            xs = np.r_[gtv[0], gtv, gtv[-1]]
            ys = np.r_[0, np.arange(1, n + 1) / n, 1]
            ax.fill_between(xs, np.clip(ys - eps, 0, 1), np.clip(ys + eps, 0, 1),
                            step="post", color="black", alpha=GT_BAND_ALPHA,
                            linewidth=0, zorder=3)
            ax.step(xs, ys, where="post", color="black", linewidth=GT_LW, zorder=4)
            if r == 0:
                ax.set_title(PRETTY[sim], pad=5, **STRIP)
            if c == len(SIMULATORS) - 1:   # application label on the right edge
                ax.text(1.06, 0.5, SYS_PRETTY[system], transform=ax.transAxes,
                        rotation=270, ha="left", va="center", **STRIP)
    axes[0, 0].set_xlim(600, 2450)
    axes[0, 0].set_xticks([1000, 1500, 2000])
    axes[0, 0].set_yticks([0, 0.5, 1])
    fig.text(0.515, 0.015, "total energy consumption (J)", ha="center", **OUTER)
    fig.text(0.01, 0.43, "CDF", rotation=90, va="center", **OUTER)
    add_legends(fig, PM_ORDER, "95% DKW confidence band", sim_band=False,
                gt_label="ground truth (16 selected reps)", x_split=0.52)
    save(fig, "cdf_per_simulator")


# --------------------------------------------------------------------------- #
# fig:bestOverTime -- 5 x 1, best power model per simulator, averaged over treatments
# --------------------------------------------------------------------------- #
def fig_best_over_time() -> None:
    gt_mean = np.mean([gt_selected(*t)["mean"].values for t in TREATMENTS], axis=0)
    gt_lo = np.mean([gt_band(*t)[0] for t in TREATMENTS], axis=0)
    gt_hi = np.mean([gt_band(*t)[1] for t in TREATMENTS], axis=0)

    fig, axes = plt.subplots(len(SIMULATORS), 1, figsize=(TEXTWIDTH_IN * 0.92, 6.4),
                             sharex=True, sharey=True,
                             gridspec_kw=dict(hspace=0.14, left=0.11, right=0.98,
                                              top=0.86, bottom=0.075))
    for ax, sim in zip(axes, SIMULATORS):
        pm = BEST_PM[sim]
        parts = []
        for t in TREATMENTS:
            b = sim_band(sim, *t)
            parts.append(b[b["powermodel"] == pm].set_index("time")[["mean", "min", "max"]])
        avg = pd.concat(parts).groupby(level=0).mean()
        ax.fill_between(avg.index, avg["min"], avg["max"], color=PM_STYLE[pm]["color"],
                        alpha=SIM_BAND_ALPHA, linewidth=0)
        ax.plot(avg.index, avg["mean"], linewidth=SIM_LW, **PM_STYLE[pm])
        ax.fill_between(GRID, gt_lo, gt_hi, color="black", alpha=GT_BAND_ALPHA,
                        linewidth=0, zorder=3)
        ax.plot(GRID, gt_mean, color="black", linewidth=GT_LW, zorder=4)
        ax.text(0.012, 0.9, f"{PRETTY[sim]} ({pm})", transform=ax.transAxes,
                ha="left", va="top", zorder=6, **STRIP)
    axes[0].set_xlim(0, T_END)
    axes[0].set_ylim(-50, 2150)
    axes[0].set_yticks([0, 500, 1000, 1500, 2000])
    fig.text(0.545, 0.012, "time (s)", ha="center", **OUTER)
    fig.text(0.012, 0.47, "cumulative energy consumption (J)", rotation=90,
             va="center", **OUTER)
    add_legends(fig, ["constant", "linear", "cubic"], GT_BAND_LABEL, x_split=0.47)
    save(fig, "mean_total_pwr_per_simulator")


# --------------------------------------------------------------------------- #
# fig:overtimes -- 5 x 8 small multiples (rows: simulators; cols: treatments)
# --------------------------------------------------------------------------- #
def fig_overtimes() -> None:
    nrows = len(SIMULATORS)
    fig = plt.figure(figsize=(TEXTWIDTH_IN, 5.6))
    gs = GridSpec(nrows, 9, figure=fig, width_ratios=[1, 1, 1, 1, 0.22, 1, 1, 1, 1],
                  wspace=0.12, hspace=0.16, left=0.205, right=0.995, top=0.765,
                  bottom=0.075)
    axes, first = {}, None
    for r, sim in enumerate(SIMULATORS):
        for si, system in enumerate(SYSTEMS):
            for ci, (scen, users) in enumerate(SCEN_USERS):
                col = ci + 5 * si
                ax = fig.add_subplot(gs[r, col], sharex=first, sharey=first)
                first = first or ax
                axes[r, col] = ax
                band = sim_band(sim, system, scen, users)
                for pm in PM_ORDER:
                    d = band[band["powermodel"] == pm]
                    if d.empty:
                        continue
                    ax.fill_between(d["time"], d["min"], d["max"],
                                    color=PM_STYLE[pm]["color"], alpha=SIM_BAND_ALPHA,
                                    linewidth=0)
                    ax.plot(d["time"], d["mean"], linewidth=SIM_LW, **PM_STYLE[pm])
                lo, hi = gt_band(system, scen, users)
                ax.fill_between(GRID, lo, hi, color="black", alpha=GT_BAND_ALPHA,
                                linewidth=0, zorder=3)
                ax.plot(GRID, gt_selected(system, scen, users)["mean"], color="black",
                        linewidth=GT_LW, zorder=4)
                if r == 0:
                    ax.set_title(f"{scen} · {users}", pad=5, **STRIP)
                if col == 0:
                    ax.set_ylabel(ROW_LABEL[sim], rotation=0, ha="center", va="center",
                                  labelpad=26, **STRIP)
                else:
                    ax.tick_params(labelleft=False)
                if r != nrows - 1:
                    ax.tick_params(labelbottom=False)
    first.set_xticks([0, 100, 200])
    first.set_yticks([0, 1000, 2000])
    first.set_xlim(0, T_END)
    first.set_ylim(-80, 2600)
    y_hdr = axes[0, 0].get_position().y1 + 0.045
    for si, system in enumerate(SYSTEMS):
        x0 = axes[0, 5 * si].get_position().x0
        x1 = axes[0, 5 * si + 3].get_position().x1
        fig.text((x0 + x1) / 2, y_hdr + 0.006, SYS_PRETTY[system], ha="center",
                 va="bottom", **GROUP)
        fig.add_artist(plt.Line2D([x0, x1], [y_hdr, y_hdr], color="0.3", linewidth=0.7))
    fig.text(0.6, 0.012, "time (s)", ha="center", **OUTER)
    fig.text(0.008, 0.42, "cumulative energy consumption (J)", rotation=90,
             va="center", **OUTER)
    add_legends(fig, PM_ORDER, GT_BAND_LABEL, x_split=0.47)
    save(fig, "overtimes_per_simulator")


# --------------------------------------------------------------------------- #
# fig:runtimes -- box + jitter, n per simulator
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
            # one row per experiment run, each simulating len(workloads) workloads ->
            # expand to per-workload times (same as get_runtimes.py opendc_row)
            times = []
            for _, row in d.iterrows():
                with open(ROOT / "simulators/openDC" / str(row["file"])) as f:
                    n_workloads = len(json.load(f)["workloads"])
                times += [row["time"] / n_workloads] * n_workloads
            d = pd.DataFrame({"time": times})
        else:
            d = pd.DataFrame({"time": d["time"]})
        d.insert(0, "simulator", label)
        frames.append(d)
    return pd.concat(frames, ignore_index=True)


def fig_runtimes() -> None:
    import seaborn as sns
    df = _load_runtimes()
    order = ["CloudSim", "DISSECT-CF", "NetworkCloudSim", "OpenDC", "SimGrid",
             "SimGrid with comm."]
    fig, ax = plt.subplots(figsize=(TEXTWIDTH_IN, 2.9))
    sns.stripplot(data=df, x="simulator", y="time", order=order, ax=ax,
                  color="#E57E00", alpha=0.3, jitter=0.35, s=2.5, zorder=1)
    sns.boxplot(data=df, x="simulator", y="time", order=order, ax=ax, width=0.6,
                showcaps=True, fliersize=0, linewidth=0.8,
                boxprops={"facecolor": "none", "zorder": 10},
                medianprops={"color": "black", "zorder": 11},
                whiskerprops={"zorder": 10}, capprops={"zorder": 10})
    counts = df.groupby("simulator")["time"].count()
    ax.set_xticks(range(len(order)))
    ax.set_xticklabels([f"{o}\n(n = {counts.get(o, 0)})" for o in order], fontsize=7.5)
    ax.set_xlabel("simulator", **OUTER)
    ax.set_ylabel("simulation time (s)", **OUTER)
    ax.grid(axis="x", visible=False)
    fig.tight_layout(pad=0.3)
    save(fig, "runtimes_revised")


# --------------------------------------------------------------------------- #
def main(argv: list[str]) -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    which = argv[1:] or ["cdf", "fig6", "overtimes", "runtimes"]
    for key, fn in [("cdf", fig_cdf), ("fig6", fig_best_over_time),
                    ("overtimes", fig_overtimes), ("runtimes", fig_runtimes)]:
        if key in which:
            print(f"generating {key} ...")
            fn()
    print("done ->", OUT_DIR)


if __name__ == "__main__":
    main(sys.argv)
