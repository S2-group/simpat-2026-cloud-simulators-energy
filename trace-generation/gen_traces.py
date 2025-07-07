import json
import numpy as np
import os
import pandas as pd
from collections import defaultdict
from datetime import datetime

from gen_parquet import gen_parquet
from gen_swf import gen_swf

METRICS_BASE = "../ground-truth/vuDevOps/data_collection"


def get_power_vals(metrics_df: pd.DataFrame):
    cpu_cols = metrics_df.filter(regex="_cpu$")
    host_power_col = metrics_df.filter(regex="host_power$").columns[0]

    max_rows = cpu_cols.sum(axis=1) >= properties["host"]["n_cores"]
    if max_rows.any():
        print(cpu_cols.sum(axis=1)[max_rows])
    maxs.extend(metrics_df.loc[max_rows, host_power_col].values)

    all_powers.extend(metrics_df[host_power_col])


def gen_traces(test_system: str, scenario: str, users: int, repetition: str):
    metrics_df = pd.read_csv(f"{METRICS_BASE}/{test_system}-data/scenario_{scenario}/baseline/{users}/repetition_{repetition}/metrics.csv")
    metrics_df.drop(columns=[col for col in metrics_df.columns if "host_power" in col])
    metrics_df["time"] = metrics_df.apply(lambda r: datetime.fromtimestamp(round(r.time)), axis=1)
    metrics_df = metrics_df.sort_values("time")
    metrics_df["since_start"] = (metrics_df.time - metrics_df.iloc[0].time).map(lambda x: x.total_seconds())  # time since start of this repetition in seconds
    metrics_df["time_diff"] = (metrics_df.time - metrics_df.time.shift(1)).map(lambda x: x.total_seconds()*1000)  # time since previous measurement in milliseconds
    metrics_df = metrics_df[metrics_df.time != metrics_df.time.min()]  # drop first row

    folder_name = f"../traces/{test_system}/{scenario}/{users}/{repetition}"
    os.makedirs(folder_name, exist_ok=True)
    os.makedirs(f"{folder_name}/services", exist_ok=True)

    get_power_vals(metrics_df)
    gen_parquet(test_system, scenario, users, repetition, folder_name, metrics_df, properties)
    gen_swf(test_system, scenario, users, repetition, folder_name, metrics_df, properties)

    locust_history_df = pd.read_csv((f"{METRICS_BASE}/{test_system}-data/scenario_{scenario}/baseline/{users}/repetition_{repetition}/Locust_log_stats_history.csv"))
    # Write requests to file.
    try:
        locust_history_df["Timestamp"] = locust_history_df.apply(lambda r: datetime.fromtimestamp(r.Timestamp), axis=1)
    except Exception:
        print(f"{METRICS_BASE}/{test_system}-data/scenario_{scenario}/baseline/{users}/repetition_{repetition}/Locust_log_stats_history.csv")
        exit()
    locust_history_df["Total Success Count"] = locust_history_df["Total Request Count"] - locust_history_df["Total Failure Count"]
    locust_history_df["Success Count"] = locust_history_df["Total Success Count"] - locust_history_df["Total Success Count"].shift(1)
    locust_history_df = locust_history_df[locust_history_df.Timestamp >= (metrics_df.time.min() - pd.Timedelta(seconds=5))]
    with open(f"{folder_name}/comms.data", "w+") as f:
        f.write("\n".join(locust_history_df["Success Count"].astype(str).tolist()))

    locust_stats_df = pd.read_csv((f"{METRICS_BASE}/{test_system}-data/scenario_{scenario}/baseline/{users}/repetition_{repetition}/Locust_log_stats.csv"))
    avg_req_size = locust_stats_df[locust_stats_df["Name"] == "Aggregated"]["Average Content Size"].values[0]
    with open(f"{folder_name}/comms_size.data", "w+") as f:
        f.write(str(avg_req_size))


if __name__ == "__main__":
    with open("../traces/properties.json", "r") as f:
        properties = json.load(f)

    maxs = []
    all_powers = []
    req_sizes = defaultdict(lambda: [])

    for test_system in ["sockshop", "trainticket"]:
        for scenario in ["A", "B"]:
            for users in [100, 1000]:
                for repetition in range(1, 31):
                    print(f"{test_system}\t{scenario}\t{users}\t{repetition}")
                    gen_traces(test_system, scenario, users, repetition)

    # Update properties.json with power level measurements.
    with open("max_power.data", "w+") as f:
        f.write("\n".join([str(m) for m in maxs]))
    properties["host"]["power"]["max"] = np.mean(maxs)

    idle_df = pd.read_csv("idle_power.csv")
    properties["host"]["power"]["idle"] = idle_df.total_power.mean()

    with open("all_power_measurements.log", "w+") as f:
        f.write(f"n={len(all_powers)} mean={np.mean(all_powers)} median={np.median(all_powers)} std={np.std(all_powers)} min={min(all_powers)} max={max(all_powers)}")
    properties["host"]["power"]["avg"] = np.mean(all_powers)

    with open("ipc_sockshop.data", "r") as f:
        ipcs_sockshop = [float(l.strip()) for l in f.readlines()]
    properties["host"]["ipc_sockshop"] = np.mean(ipcs_sockshop)

    with open("ipc_trainticket.data", "r") as f:
        ipcs_trainticket = [float(l.strip()) for l in f.readlines()]
    properties["host"]["ipc_trainticket"] = np.mean(ipcs_trainticket)

    with open("../traces/properties.json", "w") as f:
        json.dump(properties, f)

    # Make XML file for SimGrid
    simgrid_platform = f"""<?xml version='1.0'?>
<!DOCTYPE platform SYSTEM 'https://simgrid.org/simgrid.dtd'>
<platform version='4.1'>
  <zone id='world' routing='Full'>
    <!-- The resources -->
    <host id='host1' speed='{properties['host']['core_gflops']*1000}f' core='{properties['host']['n_cores']}'>
        <prop id='wattage_per_state' value='{properties['host']['power']['idle']}:{properties['host']['power']['max']}' />
        <prop id='wattage_off' value='{properties['host']['power']['off']}' />
    </host>
    <host id='host2' speed='217600f' core='8'>
        <prop id='wattage_per_state' value='0:0' />
        <prop id='wattage_off' value='0' />
    </host>
    <host id='host3' speed='217600f' core='8'>
        <prop id='wattage_per_state' value='0:0' />
        <prop id='wattage_off' value='0' />
    </host>
    <link id='1' bandwidth='{properties['network']['bandwidth_gbps']*1e9}bps' latency='{properties['network']['latency_ms']}ms'>
        <prop id='wattage_range' value='0.0:0.0' />
        <prop id='wattage_off' value='0.0' />
    </link>

    <!-- The routing between resources -->
    <route src='host1' dst='host2'>
      <link_ctn id='1'/>
    </route>
  </zone>
</platform>"""

    with open("../traces/platform.xml", "w+") as f:
        f.write(simgrid_platform)
