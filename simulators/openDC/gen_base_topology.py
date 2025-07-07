import json

with open("../../traces/properties.json", "r") as f:
    properties = json.load(f)

topology = {
    "clusters": [{
        "name": "C01",
        "count": 1,
        "hosts": [{
            "name": "H01",
            "cpu": {
                "coreCount": properties["host"]["n_cores"],
                "coreSpeed": round(properties["host"]["core_freq_ghz"]*1000),  # in MHz
            },
            "memory": {
                "memorySize": properties["host"]["ram_gb"]*1073741824  # in bits
            },
            "powerModel": {
                "power": properties["host"]["power"]["avg"],
                "idlePower": properties["host"]["power"]["idle"],
                "maxPower": properties["host"]["power"]["max"]
            }
        }]
    }]
}


with open("topologies/base.json", "w+") as f:
    json.dump(topology, f)
