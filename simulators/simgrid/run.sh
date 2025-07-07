#!/bin/bash
TIMEFORMAT=%R

cd simgrid-template-s4u-master/build

if [[ $1 == "0" ]]; then
    cmake ..
    make -j8
    echo "rank,test_system,scenario,users,repetition,run,time">../../runs.time
    echo "rank,test_system,scenario,users,repetition,run,time">../../runs_with_comms.time
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
' ../../../../trace-generation/trace-selection/trace_selection.csv | while IFS=',' read test_system scenario users repetition; do
    echo SimGrid $1 $test_system $scenario $users
    for r in {0..29}; do
        echo SimGrid $1 $test_system $scenario $users $repetition;
        real_time=$({ time ./simple $test_system $scenario $users $repetition $r > /dev/null 2> /dev/null; } 2>&1)
        real_time_comm=$({ time ./simple-with-comms $test_system $scenario $users $repetition $r > /dev/null 2> /dev/null; } 2>&1)
        echo "$1,$test_system,$scenario,$users,$repetition,$r,${real_time//,/.}" >> ../../runs.time
        echo "$1,$test_system,$scenario,$users,$repetition,$r,${real_time_comm//,/.}" >> ../../runs_with_comms.time
    done
done
