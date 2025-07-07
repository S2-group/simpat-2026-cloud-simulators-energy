cd simulators


for rank in {0..3}; do
    cd openDC
    ./run.sh $rank
    cd ..

    cd cloudsim
    ./run.sh $rank
    cd ..

    cd simgrid
    ./run.sh $rank
    cd ..

    cd dissect-cf
    ./run.sh $rank
    cd ..
done
