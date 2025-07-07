import os
import pandas as pd
import pyarrow as pa
import pyarrow.parquet as pq
import sys




def add_fragments_row(service: str, cpu_count: int, row: pd.Series, fragments_df: pd.DataFrame, core_freq: float):
    """
    id: task id
    duration: microseconds since last sample
    cpu_count: number of CPUs
    cpu_usage: MHz, seems to be overall
    Note: no memory usage.
    """
    cpu_usage = (row[f"{service}_cpu"] / 100) * (core_freq * 1000)  # (cpu_usage in % / 100) * (core freq in GHz * 1000) --> MHz
    fragments_row = [service, row.time_diff, cpu_count, cpu_usage]
    fragments_df.loc[(len(fragments_df))] = fragments_row


def gen_traces_microservice(
    service: str, start: int, duration: int,
    metrics_df: pd.DataFrame, fragments_df: pd.DataFrame, tasks_df: pd.DataFrame,
    n_cores: int, core_freq: float
):
    df = metrics_df[metrics_df["time_diff"].notna()]
    max_cpu = metrics_df[f"{service}_cpu"].dropna().max()
    if pd.isna(max_cpu):  # No metrics for this service.
        return

    cpu_count = max(1, int(max_cpu/100*n_cores))  # max cpu usage in % / 100 * cores --> number of CPUs provisioned
    cpu_capacity = cpu_count*core_freq*1000  # cpu count * core frequency in GHz * 1000 --> total MHz provisioned
    mem_capacity = int(metrics_df[f"{service}_memory"].sum() / (1024*1024))  # sum(memory in bytes) -> MB

    tasks_row = [service, start, duration, cpu_count, cpu_capacity, mem_capacity]
    tasks_df.loc[(len(tasks_df))] = tasks_row

    df.apply(lambda row: add_fragments_row(service, cpu_count, row, fragments_df, core_freq), axis=1)


def gen_parquet(
    test_system: str, scenario: str, users: int, repetition: str, folder_name: str,
    metrics_df: pd.DataFrame, properties: dict[str, dict[str, int | float]]
):
    n_cores = properties["host"]["n_cores"]
    core_freq = properties["host"]["core_freq_ghz"]

    start_time = metrics_df.time.min()
    end_time = metrics_df.time.max()
    duration = (end_time - start_time).total_seconds()*1000

    services = []
    for col in metrics_df.columns:
        if col.endswith("_cpu"):
            services.append(col.split("_")[0])

    fragments_df = pd.DataFrame({
        "id": pd.Series(dtype="str"),
        "duration": pd.Series(dtype="int64"),
        "cpu_count": pd.Series(dtype="int64"),
        "cpu_usage": pd.Series(dtype="float64")
    })

    tasks_df = pd.DataFrame({
        "id": pd.Series(dtype="str"),
        "submission_time": pd.Series(dtype="datetime64[ms]"),
        "duration": pd.Series(dtype="int64"),
        "cpu_count": pd.Series(dtype="int64"),
        "cpu_capacity": pd.Series(dtype="float64"),
        "mem_capacity": pd.Series(dtype="int64")
    })

    for service in services:
        gen_traces_microservice(service, start_time, duration, metrics_df, fragments_df, tasks_df, n_cores, core_freq)

    tasks_schema = pa.schema([
        ("id", pa.string(), False),
        ("submission_time", pa.timestamp("ms"), False),
        ("duration", pa.int64(), False),
        ("cpu_count", pa.int32(), False),
        ("cpu_capacity", pa.float64(), False),
        ("mem_capacity", pa.int64(), False)
    ])
    fragments_schema = pa.schema([
        ("id", pa.string(), False),
        ("duration", pa.int64(), False),
        ("cpu_count", pa.int32(), False),
        ("cpu_usage", pa.float64(), False)
    ])

    tasks_table = pa.Table.from_pandas(tasks_df, tasks_schema, preserve_index=False)
    fragments_table = pa.Table.from_pandas(fragments_df, fragments_schema, preserve_index=False)
    pq.write_table(tasks_table, f"{folder_name}/tasks.parquet")
    pq.write_table(fragments_table, f"{folder_name}/fragments.parquet")


if __name__ == "__main__":
    if len(sys.argv) < 5:
        raise ValueError("Must give 4 arguments (system, scenario, users, repetition)")
    test_system = sys.argv[1].lower().strip()  # sockshop or trainticket
    scenario = f"scenario_{sys.argv[2].upper().strip()}"  # A or B
    users = int(sys.argv[3].lower().strip())  # 100 or 1000
    repetition = f"repetition_{sys.argv[4].lower().strip()}"

    # ground-truth/vuDevOps/data_collection/sockshop-data/scenario_A/baseline/100/repetition_1/metrics.csv
    # ../../vuDevOps/data-collection/sockshop-data/scenario_A/baseline/100/repetition_1/metrics.csv

    # with open("../traces/properties.json", "r") as f:
    #     properties = json.load(f)

    # metrics_df = pd.read_csv(f"{METRICS_BASE}/{test_system}-data/{scenario}/baseline/{users}/{repetition}/metrics.csv")
    # metrics_df["time"] = metrics_df.apply(lambda r: datetime.fromtimestamp(r.time), axis=1)
    # metrics_df = metrics_df.sort_values("time")
    # metrics_df["time_diff"] = (metrics_df.time - metrics_df.time.shift(1)).map(lambda x: x.total_seconds()*1000)
