import json
import os
import pandas as pd
import sys

selection_rank = int(sys.argv[1])
run = int(sys.argv[2])

powerModels = ["constant", "sqrt", "linear", "square", "cubic"]  # See CPUPowerModelsFactory.kt
output_folder = f"outputs/rank-{selection_rank}/{run}"

with open("topologies/base.json", "r") as f:
    topology = json.load(f)

with open("experiments/base.json", "r") as f:
    exp = json.load(f)

workloads = []
# Get selected traces.
selection_df = pd.read_csv("../../trace-generation/trace-selection/trace_selection.csv")
# Select repetitions with given rank
selection_df.apply(lambda row: workloads.append({
                        "pathToFile": f"../../traces/{row.test_system}/{row.scenario}/{row.users}/{row.repetition}",
                        "type": "ComputeWorkload"
                    }) if row.selection_rank == selection_rank else None, axis=1)

exp["workloads"] = workloads

for powermodel in powerModels:
    for host in topology["clusters"][0]["hosts"]:
        host["powerModel"]["modelType"] = powermodel
    with open(f"topologies/{powermodel}.json", "w+") as f:
        json.dump(topology, f)

    exp["name"] = powermodel
    exp["outputFolder"] = output_folder
    exp["topologies"] = [{"pathToFile": f"topologies/{powermodel}.json"}]

    os.makedirs(f"experiments/rank-{selection_rank}", exist_ok=True)
    with open(f"experiments/rank-{selection_rank}/experiment_{powermodel}.json", "w+") as f:
        json.dump(exp, f)
