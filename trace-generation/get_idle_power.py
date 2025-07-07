import requests
import time

METRIC_STEP = 5  # In seconds
MAX_RESOLUTION = 11_000  # Maximum resolution of Prometheus
DURATION = "1m"
TOTAL_TIME = 30*60  # Total time of measuring in seconds


#You'll receive data from Prometheus at 5-second intervals,
#and your query will aggregate that data over the last 1 minute
#to provide you with the rate of change in CPU usage by container names.
def _exec_query(query, start_time, end_time, prom_url):
    response = requests.get(
        f"http://{prom_url}/api/v1/query_range",
        params={
            "query": query,
            "start": start_time,
            "end": end_time,
            "step": f"{METRIC_STEP}s",
        },
    )
    results = response.json()["data"]["result"]

    for result in results:
        if all(
            k not in result["metric"].keys() for k in ["instance"]
        ):
            continue
        if "instance" in result["metric"] and "host" in query:
            print(result["values"])
            return result["values"]

    return None


# Given a valid query, extracts the relevant data
def exec_query(query, start, end, prom_url):
    # If all the data can be collected in only one request
    if not (end - start) / METRIC_STEP > MAX_RESOLUTION:
        return _exec_query(query, start, end, prom_url)

    data = []
    start_time = start
    end_time = start
    while end_time < end:
        end_time = min(end_time + MAX_RESOLUTION, end)
        print(f"Querying data from {start_time} to {end_time}")
        d = _exec_query(query, start_time, end_time, prom_url)
        print(d)
        if d is not None:
            data.append(d)
        start_time = end_time + 1

    return data


if __name__ == "__main__":
    start = time.time()
    time.sleep(TOTAL_TIME)
    end = time.time()

    query = f"sum(rate(scaph_host_power_microwatts[{DURATION}])) by (instance) / 1000000"

    data = exec_query(query, start, end, "0.0.0.0:30000")

    with open("idle_power.csv", "w+") as f:
        f.write("t,total_power\n" + "\n".join([f"{d[0]},{d[1]}" for d in data]))
