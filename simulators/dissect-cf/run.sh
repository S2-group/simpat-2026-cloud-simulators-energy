#!/bin/bash
TIMEFORMAT=%R

if [[ $1 == "0" ]]; then
    echo "rank,test_system,scenario,users,repetition,powermodel,run,time"> ./runs.time

    cd dissect-cf-master/
    mvn clean install -U -Dmaven.test.skip=true
    if [ $? -ne 0 ]; then
        exit 1;
    fi

    cd ../dissect-cf-examples-master
    mvn clean install -U -Dmaven.test.skip=true
    if [ $? -ne 0 ]; then
        exit 1;
    fi
    cd ..

    mkdir outputs
    mkdir outputs/constant
    mkdir outputs/linear
fi

cd dissect-cf-examples-master

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
' ../../../trace-generation/trace-selection/trace_selection.csv | while IFS=',' read test_system scenario users repetition; do
    for powerModel in "constant" "linear"; do
        for r in {0..29}; do
            echo DISSECT-CF $1 $test_system $scenario $users $repetition $powerModel $r;
            real_time=$({ time mvn exec:java -Dexec.mainClass="hu.mta.sztaki.lpds.cloud.simulator.examples.custom.SWFExperiment" -Dexec.args="${test_system} ${scenario} ${users} ${repetition} ${powerModel} ${r}" -Dhu.mta.sztaki.lpds.cloud.simulator.examples.verbosity=false > /dev/null 2> /dev/null; } 2>&1)
            echo "$1,$test_system,$scenario,$users,$repetition,$powerModel,$r,${real_time//,/.}" >> ../runs.time
        done
    done
done
