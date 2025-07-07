#!/bin/bash

>ipc.data

for repetition in {1..30}; do
    perf stat -a -e instructions,cycles sleep 60 2>&1 | sed -n 's/.*# *\([0-9.]\+\)  *insn per cycle.*/\1/p' >> ipc.data
done
