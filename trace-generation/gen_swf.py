import pandas as pd
from collections import defaultdict
# from datetime import datetime

job_num = 0
swf_lines = ""
cloudsim_lines = ""


def get_row(row, services, jobs, service_usage):
    global swf_lines
    global job_num

    for service_i, service in enumerate(services):
        cpu_usage = row[f"{service}_cpu"]
        if pd.isna(cpu_usage):
            service_usage[service_i+1].append("0")
            continue
        service_usage[service_i+1].append(str(cpu_usage))

        n_proc_used = row[f"{service}_cpu"]/100  # % (200 = 100% on 2 cpus) to number of cpus used
        mem_usage = row[f"{service}_memory"]/1000  # bytes to kB
        # mem_rss  = pd_row[f"{container}_memory_rss"]
        # mem_cache  = pd_row[f"{container}_memory_cache"]
        # disk  = pd_row[f"{container}_disk"]  # Total bytes read by the container
        # power = pd_row[f"{container}_power"]

        # See https://www.cs.huji.ac.il/labs/parallel/workload/swf.html for SWF fields (index starts at 1)
        submit_t = round(row.since_start)  # 2: submit time since start in seconds
        wait_time = -1  # 3: difference between submit time and actual begin of run in seconds
        run_t = round(row.time_diff/1000)  # 4: total execution time in seconds.
        num_proc = max(1, round(n_proc_used))  # 5: number of allocated processors = number of cores used
        avg_cpu_t = run_t*n_proc_used/num_proc  # 6: average CPU time used in seconds
        mem_usage = int(round(mem_usage/num_proc))  # 7: average used memory in kB per processor.
        req_num_proc = num_proc  # 8: requested number of cores, here the same as num_proc
        req_run_time = -1  # 9: requested time/"user estimated run time", not used here
        req_mem = mem_usage  # 10: requested memory in kB/processor, here the same as mem_usage
        status = 1  # 11: status. 1 if complete, 0 if failed, 5 if cancelled.
        user_id = -1  # 12: user ID.
        group_id = service_i+1  # 13: group ID > 0
        ex_num = service_i+1  # 14: Executable (application) number > 0.
        queue_num = -1  # 15: queue number .
        partition_num = -1  # 16: partition number.
        prev_job_num = jobs[service]  # 17: preceding job number.
        think_t_prev_job = 0  # 18: think time from preceding job, seconds between termination of prev job and submittal of this one

        line = f"{job_num} {submit_t} {wait_time} {run_t} {num_proc} {avg_cpu_t} {mem_usage} {req_num_proc} {req_run_time} {req_mem} {status} {user_id} {group_id} {ex_num} {queue_num} {partition_num} {prev_job_num} {think_t_prev_job}\n"
        swf_lines += line
        job_num += 1
        jobs[service] = job_num


def gen_swf(
    test_system: str, scenario: str, users: int, repetition: str, folder_name: str,
    metrics_df: pd.DataFrame, properties: dict[str, dict[str, int | float]]
):
    global swf_lines
    global job_num
    global cloudsim_lines

    services = []
    for col in metrics_df.columns:
        if col.endswith("_cpu"):
            services.append(col.split("_")[0])

    with open(f"{folder_name}/services-overview.data", "w+") as f:
        f.write("")

    services = sorted(services)
    for service_i, service in enumerate(services):
        max_cpu = metrics_df[f"{service}_cpu"].dropna().max()
        if pd.isna(max_cpu):  # No metrics for this service.
            continue

        cpu_count = max_cpu/100*properties['host']['n_cores']
        if cpu_count <= 1e-10:
            cpu_count = 0
        else:
            cpu_count = max(cpu_count, 1)
        mem_capacity = int(metrics_df[f"{service}_memory"].sum() / 1e6)  # sum(memory in bytes) -> MB

        with open(f"{folder_name}/services-overview.data", "a") as f:
            f.write(f"{service_i+1} {cpu_count} {mem_capacity}\n")

    swf_lines = f"""
; Version: 2
; MaxJobs: {len(metrics_df)}
; MaxRecords: {len(metrics_df)}
; Preemption: Yes
; UnixStartTime: {min(metrics_df.time)}
; TimeZoneString: Europe/Amsterdam
; StartTime: {min(metrics_df.time).strftime("%a %b %d %H:%M:%S CET %Y")}
; EndTime: {max(metrics_df.time).strftime("%a %b %d %H:%M:%S CET %Y")}
; MaxNodes: {properties['host']['n_cores']}
; MaxProcs: {properties['host']['n_cores']}
; MaxRuntime: {int(max(metrics_df.since_start))}
; MaxMemory: {properties['host']['ram_gb']*1000}
;
"""

    jobs = defaultdict(lambda: -1)  # keep track of job of last job of service
    service_usage = defaultdict(lambda: [])

    metrics_df.apply(lambda row: get_row(row, services, jobs, service_usage), axis=1)

    with open(f"{folder_name}/trace.swf", "w+") as f:
        f.write(swf_lines)
    for service_i in service_usage:
        with open(f"{folder_name}/services/{service_i}_cpu_usage.data", "w+") as f:
            f.write("\n".join(service_usage[service_i]))

    job_num = 0
    swf_lines = ""
    cloudsim_lines = ""
