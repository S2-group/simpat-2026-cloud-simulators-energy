import json
import os
import pandas as pd
import pyarrow.parquet as pq


def get_error(merged_df):
    merged_df["error_total"] = merged_df["joules_total_correct"] - merged_df["joules_total"]
    merged_df["error"] = merged_df["joules_correct"] - merged_df["joules"].rolling(window=5).sum()
    merged_df["rel_error_total"] = (merged_df["joules_total_correct"] - merged_df["joules_total"]) / merged_df["joules_total_correct"]
    merged_df["rel_error"] = (merged_df["joules_correct"] - merged_df["joules"].rolling(window=5).sum()) / merged_df["joules_correct"]

    return merged_df


def get_opendc(test_system, scenario, users, repetition, rank, energy_df, opendc):
    # Find iteration
    base_path = f"../simulators/openDC/outputs/rank-{rank}"

    # get rank
    with open(f"../simulators/openDC/experiments/rank-{rank}/experiment_constant.json", "r") as f:
        experiment_def = json.load(f)
    repetition_i = next(i for i, d in enumerate(experiment_def["workloads"]) if f"{test_system}/{scenario}/{users}/{repetition}" in d["pathToFile"])

    for i in range(30):  # iterate through runs
        for powermodel in ["constant", "cubic", "linear", "sqrt", "square"]:
            experiment_folder = f"{base_path}/{i}/{powermodel}/raw-output/{repetition_i}/seed=0"
            host_df = pq.read_table(experiment_folder + "/host.parquet").to_pandas()
            powersource_df = pq.read_table(experiment_folder + "/powerSource.parquet").to_pandas()
            assert all(host_df["energy_usage"] == powersource_df["energy_usage"])
            assert all(host_df["timestamp_absolute"] == powersource_df["timestamp_absolute"])

            df = pd.DataFrame()
            host_df["time_diff"] = host_df["timestamp"] - host_df["timestamp"].shift(1)
            df["joules"] = (host_df["energy_usage"] + host_df["energy_usage"].shift(1))/2 * (host_df["time_diff"]/1000)
            df["joules_total"] = df["joules"].cumsum()

            df["time"] = host_df["timestamp"] / 1000
            df["powermodel"] = powermodel
            df["run"] = i
            df["test_system"] = test_system
            df["scenario"] = scenario
            df["users"] = users
            df["repetition"] = repetition
            df["workload"] = "parquet"

            # Get error
            merged_df = df.merge(energy_df, on="time", how="left")
            get_error(merged_df)

            opendc.append(merged_df)


def get_cloudsims(test_system, scenario, users, repetition, energy_df, cloudsim, networkcloudsim):
    base_path = "../simulators/cloudsim/outputs"
    for i in range(30):
        for workload in ["trace", "trace-network"]:
            for policy in ["firstfit", "minutil", "random"]:
                for powermodel in ["cubic", "linear", "sqrt", "square"]:
                    experiment_path = f"{base_path}/{workload}/{i}/EnergyConsumption/{powermodel}_{policy}_{test_system}_{scenario}_{users}/{powermodel}_{policy}_{test_system}_{scenario}_{users}_{repetition}.csv"
                    try:
                        df = pd.read_csv(experiment_path, names=["time", "joules_total"])
                    except Exception:
                        print(experiment_path)
                        continue

                    df["time"] = df["time"].round()
                    df["joules"] = df["joules_total"].diff()

                    merged_df = df.merge(energy_df, on="time", how="left")
                    merged_df = get_error(merged_df)

                    merged_df["policy"] = policy
                    merged_df["powermodel"] = powermodel
                    merged_df["run"] = i
                    merged_df["workload"] = workload
                    merged_df["test_system"] = test_system
                    merged_df["scenario"] = scenario
                    merged_df["users"] = users
                    merged_df["repetition"] = repetition

                    if workload == "trace-network":
                        networkcloudsim.append(merged_df)
                    else:
                        cloudsim.append(merged_df)


def get_dissectcf(test_system, scenario, users, repetition, energy_df, dissectcf):
    base_path = "../simulators/dissect-cf/outputs"
    for i in range(30):
        for powermodel in ["constant", "linear"]:
            experiment_path = f"{base_path}/{powermodel}/{test_system}_{scenario}_{users}_{repetition}_{i}.data"
            df = pd.read_csv(experiment_path, names=["time", "joules_total"])

            df["time"] = df["time"].round()
            df["joules"] = df["joules_total"].diff()

            merged_df = df.merge(energy_df, on="time", how="left")
            merged_df = get_error(merged_df)
            merged_df["test_system"] = test_system
            merged_df["scenario"] = scenario
            merged_df["users"] = users
            merged_df["repetition"] = repetition

            merged_df["powermodel"] = powermodel
            merged_df["run"] = i
            merged_df["workload"] = "swf"

            dissectcf.append(merged_df)


def get_simgrid(test_system, scenario, users, repetition, energy_df, simgrid):
    base_path = "../simulators/simgrid/outputs"
    for i in range(30):
        for workload in ["simple", "simple-with-comms"]:
            experiment_path = f"{base_path}/{workload}/{test_system}/{scenario}/{users}/{repetition}/{i}/energy.data"
            df = pd.read_csv(experiment_path)
            df = df.rename(columns={"t": 'time', "total_energy": "joules_total"})

            df["time"] = df["time"].round()
            df["joules"] = df["joules_total"].diff()
            df["test_system"] = test_system
            df["scenario"] = scenario
            df["users"] = users
            df["repetition"] = repetition
            df["powermodel"] = "linear"

            merged_df = df.merge(energy_df, on="time", how="left")
            merged_df = get_error(merged_df)

            merged_df["workload"] = workload
            merged_df["run"] = i

            simgrid.append(merged_df)


if __name__ == "__main__":
    selection_df = pd.read_csv("../trace-generation/trace-selection/trace_selection.csv")

    cloudsim = []
    networkcloudsim = []
    opendc = []
    dissectcf = []
    simgrid = []
    for test_system in ["sockshop", "trainticket"]:
        for scenario in ["A", "B"]:
            for users in [100, 1000]:
                for file in os.listdir(f"energy/{test_system}/{scenario}/{users}"):
                    repetition = int(file.split(".")[0])
                    print(test_system, scenario, users, file)
                    rank = selection_df[(selection_df["test_system"] == test_system) &
                                        (selection_df["scenario"] == scenario) &
                                        (selection_df["users"] == users) &
                                        (selection_df["repetition"] == repetition)]["selection_rank"].values[0]
                    energy_df = pd.read_csv(f"energy/{test_system}/{scenario}/{users}/{file}")
                    energy_df = energy_df.rename(columns={"joules": "joules_correct", "joules_total": "joules_total_correct"})

                    get_cloudsims(test_system, scenario, users, repetition, energy_df, cloudsim, networkcloudsim)
                    get_dissectcf(test_system, scenario, users, repetition, energy_df, dissectcf)
                    get_opendc(test_system, scenario, users, repetition, rank, energy_df, opendc)
                    get_simgrid(test_system, scenario, users, repetition, energy_df, simgrid)

    cloudsim_df = pd.concat(cloudsim, ignore_index=True)
    cloudsim_df.to_csv("energy/cloudsim.energy", index=False)
    print("cloudsim done")
    networkcloudsim_df = pd.concat(networkcloudsim, ignore_index=True)
    networkcloudsim_df.to_csv("energy/networkcloudsim.energy", index=False)
    print("networkcloudsim done")
    dissectcf_df = pd.concat(dissectcf, ignore_index=True)
    dissectcf_df.to_csv("energy/dissectcf.energy", index=False)
    print("dissectcf done")
    opendc_df = pd.concat(opendc, ignore_index=True)
    opendc_df.to_csv("energy/opendc.energy", index=False)
    print("opendc done")
    simgrid_df = pd.concat(simgrid, ignore_index=True)
    simgrid_df.to_csv("energy/simgrid.energy", index=False)
    print("simgrid done")
    simgrid_df = pd.concat(simgrid, ignore_index=True)
    simgrid_df.to_csv("energy/simgrid.energy", index=False)
    print("simgrid done")
