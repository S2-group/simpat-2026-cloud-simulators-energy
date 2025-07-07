import json
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import seaborn as sns


def to_sec(t_str):
    sec = float(t_str.split("m")[1].replace(",", ".")[:-2])
    min = int(t_str.split("m")[0])

    return (min * 60) + sec


def opendc_row(row, runtimes):
    opendc_path = "../../simulators/openDC/"

    with open(opendc_path + row.file, "r") as f:
        experiment = json.load(f)

    runtimes += [row.time / len(experiment["workloads"])]*len(experiment["workloads"])


def get_runtimes(path):
    runtimes = []

    time_df = pd.read_csv(path)

    if "openDC" in path:
        time_df.apply(lambda row: opendc_row(row, runtimes), axis=1)
    else:
        runtimes = time_df.time.values

    return runtimes


if __name__ == "__main__":
    cloudsim = get_runtimes("../../simulators/cloudsim/runs_trace.time")
    networkcloudsim = get_runtimes("../../simulators/cloudsim/runs_trace_network.time")
    dissectcf = get_runtimes("../../simulators/dissect-cf/runs.time")
    opendc = get_runtimes("../../simulators/openDC/runs.time")
    simgrid = get_runtimes("../../simulators/simgrid/runs.time")
    simgrid_with_comm = get_runtimes("../../simulators/simgrid/runs_with_comms.time")

    with open("runtimes.csv", "w+") as f:
        f.writelines([
            "simulator,mean,median,std,min,max\n",
            f"CloudSim,{np.mean(cloudsim)},{np.median(cloudsim)},{np.std(cloudsim)},{min(cloudsim)},{max(cloudsim)}\n",
            f"DISSECT-CF,{np.mean(dissectcf)},{np.median(dissectcf)},{np.std(dissectcf)},{min(dissectcf)},{max(dissectcf)}\n",
            f"NetworkCloudSim,{np.mean(networkcloudsim)},{np.median(networkcloudsim)},{np.std(networkcloudsim)},{min(networkcloudsim)},{max(networkcloudsim)}\n",
            f"OpenDC,{np.mean(opendc)},{np.median(opendc)},{np.std(opendc)},{min(opendc)},{max(opendc)}\n",
            f"SimGrid,{np.mean(simgrid)},{np.median(simgrid)},{np.std(simgrid)},{min(simgrid)},{max(simgrid)}\n",
            f"SimGrid with communication,{np.mean(simgrid_with_comm)},{np.median(simgrid_with_comm)},{np.std(simgrid_with_comm)},{min(simgrid_with_comm)},{max(simgrid_with_comm)}\n"
        ])

    data = {
        "CloudSim": cloudsim,
        "NetworkCloudSim": networkcloudsim,
        "DISSECT-CF": dissectcf,
        "OpenDC": opendc,
        "SimGrid": simgrid,
        "SimGrid with\ncommunication": simgrid_with_comm
    }

    df = pd.DataFrame([
        {"simulator": sim_name, "simulation time (s)": rt}
        for sim_name, runtimes in data.items()
        for rt in runtimes
    ])

    scatter_color = "#E57E00"
    line_color = "black"

    # sns.swarmplot(x="simulator", y="simulation time (s)", data=df, dodge=True, color="orange", s=7)
    sns.stripplot(x="simulator", y="simulation time (s)", data=df, dodge=True, color=scatter_color, jitter=0.35, s=7, alpha=0.3)
    ax = sns.boxplot(
        x="simulator",
        y="simulation time (s)",
        data=df,
        showfliers=False,
        width=0.8,
        linewidth=1.5,
        linecolor=line_color,
        color=line_color
    )
    ax.set_xlim(-0.5, len(df["simulator"].unique()) - 0.5)

    # Set alpha for each box
    for patch in ax.patches:
        patch.set_zorder(10)
        face_color = patch.get_facecolor()
        new_face_color = (face_color[0], face_color[1], face_color[2], 0.2)
        patch.set_facecolor(new_face_color)
        patch.set_edgecolor(line_color)
        patch.set_linewidth(1.5)

    for line in ax.lines:
        line.set_zorder(11)

    fs = 23
    ax.set_xlabel("simulator", fontsize=fs)
    ax.set_ylabel("simulation time (s)", fontsize=fs)
    ax.tick_params(axis="both", labelsize=fs)

    # plt.tight_layout()
    plt.show()
    # fig = plt.gcf()
    # fig.set_size_inches((8.5, 11), forward=False)
    # fig.savefig("runtimes.png", dpi=500)
