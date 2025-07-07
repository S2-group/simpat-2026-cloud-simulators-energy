"""
Select median trace in terms of energy from each repetition and
write the statistics for each repetition to file.
"""
import numpy as np
import pandas as pd


data_path = "../../ground-truth/vuDevOps/data_collection"

data = []
for system in ["sockshop", "trainticket"]:
    for scenario in ["A", "B"]:
        for users in [100, 1000]:
            rep_lst = []
            for repetition in range(1, 31):
                metrics_df = pd.read_csv(f"{data_path}/{system}-data/scenario_{scenario}/baseline/{users}/repetition_{repetition}/metrics.csv")
                host_power_col = None
                for col in metrics_df.columns:
                    if "host_power" in col:
                        host_power_col = col
                        break
                if host_power_col is None:
                    print(system, scenario, users, repetition)
                    continue
                metrics_df[host_power_col] = metrics_df[host_power_col].astype('Float64')
                usage_sum = 0
                rep_lst.append([system, scenario, users, repetition, sum(metrics_df[host_power_col]), False])

            # Assign rank of according to distance to median
            med = np.median([r[-2] for r in rep_lst])
            sorted_reps = sorted(rep_lst, key=lambda x: abs(x[-2] - med))
            for rank, rep in enumerate(sorted_reps):
                rep[-1] = rank

            # selected_rep = min(rep_lst, key=lambda x: abs(x[-2] - med))
            # selected_rep[-1] = True
            data += rep_lst

df = pd.DataFrame(data, columns=["test_system", "scenario", "users", "repetition", "host_energy", "selection_rank"])
df.to_csv("trace_selection.csv", index=False)
