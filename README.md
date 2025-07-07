Replication package for _A Comparative Study on the Suitability of Cloud Simulators for
Estimating Energy Usage of Microservices_.

To replicate the results presented in this study, the following steps can be followed:
# 1. Ground Truth Collection
[Ground truth data collection](ground-truth) based on: Berta Rodriguez Sanchez. 2024. _Multivariate Anomaly Detection and Root Cause Analysis of Energy Issues in Microservice-based Systems_. Master’s thesis. University of Amsterdam and Vrije Universiteit Amsterdam, Amsterdam. [https://github.com/bertars/Thesis](https://github.com/bertars/Thesis).

The systems used for this study are [SockShop](https://github.com/microservices-demo/microservices-demo) and [TrainTicket](https://github.com/FudanSELab/train-ticket). [ExperimentRunner](https://github.com/S2-group/experiment-runner) is used to automate the data collection.

For how run the ground truth data collection, see the [ReadMe](ground-truth/vuDevOps/data_collection/README.md).

# 2. Trace Generation
1. **Trace selection**: To select the repetitions closest to the mean behaviour for each treatment, run [select_traces.py](trace-generation/trace-selection/select_traces.py).
2. **Extract idle power values**: To extract the idle power values from the ground truth, run [get_idle_power.py](trace-generation/get_idle_power.py). The max power values are extracted in step 4.
3. To get the IPC values for sockshop and trainticket, run [get_ipc.sh](trace-generation/get_ipc.sh) on the server running the microservice system.
4. **Trace generation**: To generate the traces, run [gen_tracs.py](trace-generation/gen_traces.py).

# 3. Simulations Execution
All simulations can be carried out by running the [run_all.sh](run_all.sh) script, which in turn runs each simulator's run script, located in their respective folder in the [simulators](simulators/) directory.

## Simulators under test
The simulators under test are as follows:
- [_CloudSim_/_NetworkCloudSim_ v7.0.0](https://github.com/Cloudslab/cloudsim/releases/tag/7.0) ([simulators/cloudsim](simulators/cloudsim))
- [_DISSECT-CF_](https://github.com/kecskemeti/dissect-cf) (([simulators/dissectcf](simulators/dissectcf)))
- [_OpenDC_ v2.4d](https://github.com/atlarge-research/opendc/releases/tag/v2.4d) ([simulators/openDC](simulators/openDC))
- [_SimGrid_ v4.0](https://github.com/simgrid/simgrid/releases/tag/v4.0) (([simulators/simgrid](simulators/simgrid)))

# 4. Evaluation
The scripts in the [evaluation](evaluation) folder can be used to replicate the analysis presented in the study.

In order to do so, first run [gen_energy.py](evaluation/gen_energy.py) and [gen_sim_energy.py](evaluation/gen_sim_energy.py).
Because of the large number of measures, the dataset has to be split into different csv files, which are too big to upload to GitHub.

Then, [evaluation/get_stats.ipynb](evaluation/get_stats.ipynb) and [evaluation/runtime/get_runtimes.py](get_runtimes.py) can be run to carry out the analysis.
