#!/bin/bash
TIMEFORMAT=%R

if [[ $1 == "0" ]]; then
    # Set everything up
    echo "Generating OpenDC Experiment Files..."
    python gen_base_topology.py

    echo "rank,file,run,time">runs.time
fi

for r in {0..29}; do
    python gen_experiment_files.py $1 $repetition
    for file in experiments/rank-$1/*.json; do
        if [[ "$file" == *base.json ]]; then
            continue
        fi
        echo OpenDC $1 $file $repetition
        real_time=$({ time ./OpenDCExperimentRunner/bin/OpenDCExperimentRunner --experiment-path "${file}" > /dev/null 2> /dev/null; } 2>&1)
        echo "$1,$file,$r,${real_time//,/.}" >> runs.time
    done
done
