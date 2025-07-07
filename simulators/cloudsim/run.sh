#!/bin/bash
TIMEFORMAT=%R

if [[ $1 == "0" ]]; then
    mvn clean install -U -Dmaven.test.skip=true
    echo "rank,test_system,scenario,users,repetition,powermodel,run,time">runs_trace.time
    echo "rank,test_system,scenario,users,repetition,powermodel,run,time">runs_trace_network.time
fi

awk -F',' -v rank="$1" '
    BEGIN { OFS="," }
    NR==1 {
        for (i=1; i<=NF; i++) {
            if ($i == "test_system") ts=i;
            if ($i == "scenario") sc=i;
            if ($i == "users") us=i;
            if ($i == "repetition") rp=i;
            if ($i == "selection_rank") sel=i;
        }
        next;
    }
    $sel == rank {
        print $ts, $sc, $us, $rp;
    }
' ../../trace-generation/trace-selection/trace_selection.csv | while IFS=',' read test_system scenario users repetition; do
    for r in {0..29}; do
        for powerModel in "cubic" "linear" "sqrt" "square"; do
            echo CloudSim $1 $test_system $scenario $users $repetition $r $powerModel;
            real_time=$({ time mvn exec:java -pl modules/cloudsim-examples -Dexec.mainClass=org.cloudbus.cloudsim.examples.TraceExperiment -Dexec.args="${test_system} ${scenario} ${users} ${repetition} ${powerModel} random ${r}" > /dev/null 2> /dev/null; } 2>&1)
            real_time_network=$({ time mvn exec:java -pl modules/cloudsim-examples -Dexec.mainClass=org.cloudbus.cloudsim.examples.TraceNetworkExperiment -Dexec.args="${test_system} ${scenario} ${users} ${repetition} ${powerModel} random ${r}" > /dev/null 2> /dev/null; } 2>&1)
            echo "$1,$test_system,$scenario,$users,$repetition,$powerModel,$r,${real_time//,/.}" >> runs_trace.time
            echo "$1,$test_system,$scenario,$users,$repetition,$powerModel,$r,${real_time_network//,/.}" >> runs_trace_network.time
        done
    done
done
