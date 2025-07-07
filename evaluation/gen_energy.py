import os
import pandas as pd
from datetime import datetime


MAX_RANK = 3


def gen_energy_trace(row: pd.Series):
    metrics_df = pd.read_csv(f"../ground-truth/vuDevOps/data_collection/{row.test_system}-data/scenario_{row.scenario}/baseline/{row.users}/repetition_{row.repetition}/metrics.csv")
    metrics_df["time"] = metrics_df.apply(lambda r: datetime.fromtimestamp(round(r.time)), axis=1)
    metrics_df["time_diff"] = (metrics_df.time - metrics_df.time.shift(1)).map(lambda x: x.total_seconds())  # time since previous measurement in seconds
    metrics_df[metrics_df.time != metrics_df.time.min()]

    energy_df = metrics_df[["time_diff"]]
    energy_df["time"] = energy_df["time_diff"].cumsum()

    host_power_col = metrics_df.filter(regex="host_power$").columns[0]
    energy_df["host_power"] = metrics_df[host_power_col]
    energy_df["joules"] = (metrics_df[host_power_col] + metrics_df[host_power_col].shift(1))/2 * metrics_df["time_diff"]
    energy_df["joules_total"] = energy_df["joules"].cumsum()

    os.makedirs(f"energy/{row.test_system}/{row.scenario}/{row.users}", exist_ok=True)
    energy_df.to_csv(f"energy/{row.test_system}/{row.scenario}/{row.users}/{row.repetition}.energy", index=False)


if __name__ == "__main__":
    trace_selection_df = pd.read_csv("../trace-generation/trace-selection/trace_selection.csv")
    trace_selection_df = trace_selection_df[trace_selection_df["selection_rank"] <= MAX_RANK]  # only select used traces
    trace_selection_df.apply(lambda row: gen_energy_trace(row), axis=1)
