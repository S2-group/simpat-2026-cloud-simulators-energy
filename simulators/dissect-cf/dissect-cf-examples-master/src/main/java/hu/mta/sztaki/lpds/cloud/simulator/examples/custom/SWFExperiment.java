package hu.mta.sztaki.lpds.cloud.simulator.examples.custom;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.ConstantConsumptionModel;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.LinearConsumptionModel;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.energy.specialized.PhysicalMachineEnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.helpers.job.Job;
import hu.mta.sztaki.lpds.cloud.simulator.helpers.trace.GenericTraceProducer;
import hu.mta.sztaki.lpds.cloud.simulator.helpers.trace.FileBasedTraceProducerFactory;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.PhysicalMachineController;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.SchedulingDependentMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;


import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.lang.Math;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;


public class SWFExperiment {
    public static Thread mainThread;

    public static void main(String[] args) throws Exception {
        // GENERAL SETUP
        String system = args[0].strip().toLowerCase();
        String scenario = args[1].strip().toUpperCase();
        int users = Integer.parseInt(args[2].strip());
        int repetitiion = Integer.parseInt(args[3].strip());
        int run = Integer.parseInt(args[5].strip());
        String tracePath = String.format(
            "../../../traces/%s/%s/%s/%s/trace.swf",
            system, scenario, users, repetitiion);
        String propertiesPath = String.format(
            "../../../traces/properties.json",
            system, scenario, users, repetitiion);

        String powerModelStr = args[4].strip().toLowerCase();
        Supplier<? extends PowerState.ConsumptionModel> powermodel = null;
        switch (powerModelStr) {
            case "constant":
                powermodel = ConstantConsumptionModel::new;
                break;
            case "linear":
                powermodel = LinearConsumptionModel::new;
                break;
            default:
                System.exit(1);
        }
        String outPath = String.format("../outputs/%s/%s_%s_%d_%d_%d.data", powerModelStr, system, scenario, users, repetitiion, run);


        ObjectMapper propertiesMapper = new ObjectMapper();
        Map<String, Object> properties = new HashMap<>();
        try {
            File file = new File(propertiesPath);
            properties = propertiesMapper.readValue(file, Map.class);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        Map<String, Object> host = (Map<String, Object>) properties.get("host");
        Map<String, Object> hostPower = (Map<String, Object>) host.get("power");
        Map<String, Object> network = (Map<String, Object>) properties.get("network");

        double coreFreq = ((Number) host.get("core_freq_ghz")).doubleValue();
        int cores = ((Number) host.get("n_cores")).intValue();
        double ipc = ((Number) host.get(String.format("ipc_%s", system))).doubleValue();
        long ram = (long) ((Number) host.get("ram_gb")).intValue() * (long) 1e9 ; // in MB double val = ((Number) properties.get("someKey")).doubleValue();
        long bw = ((Number) network.get("bandwidth_gbps")).longValue() * (long) 1e9; // in bytes
        int latency = (int) Math.round(((Number) network.get("latency_ms")).doubleValue() / 1000); // in seconds
        double maxPower = ((Number) hostPower.get("max")).doubleValue();
        double idlePower = ((Number) hostPower.get("idle")).doubleValue();
        double minPower = 0;

        // DISSECT-CF SETUP
        mainThread = Thread.currentThread();

        Timed.resetTimed();

        // Topology setup
        Class<? extends Scheduler> vmScheduler = FirstFitScheduler.class;
		Class<? extends PhysicalMachineController> pmController = SchedulingDependentMachines.class;
        IaaSService iaasservice = new IaaSService(vmScheduler, pmController);

        // TODO
        final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = PowerTransitionGenerator
				.generateTransitions(minPower, idlePower, maxPower, 0, 0);
        // Powerstates CPU
        HashMap<String, PowerState> cpuTransitions = new HashMap<>();
        cpuTransitions.put(PhysicalMachine.State.OFF.toString(), new PowerState(minPower, minPower, ConstantConsumptionModel::new));
        final PowerState hostDefault = new PowerState(idlePower, maxPower - idlePower, powermodel);
        cpuTransitions.putAll(PhysicalMachine.StatesOfHighEnergyConsumption.stream().collect(Collectors.toMap(Enum::toString, astate -> hostDefault)));

        // Powerstates Storage
        HashMap<String, PowerState> storageTransitions = new HashMap<>();
        storageTransitions.put(NetworkNode.State.OFF.toString(), new PowerState(0, 0, ConstantConsumptionModel::new));
		storageTransitions.put(NetworkNode.State.RUNNING.toString(), new PowerState(0, 0, ConstantConsumptionModel::new));

        // Powerstates Network
        HashMap<String, PowerState> networkTransitions = new HashMap<>();
        networkTransitions.put(NetworkNode.State.OFF.toString(), new PowerState(0, 0, ConstantConsumptionModel::new));
		networkTransitions.put(NetworkNode.State.RUNNING.toString(), new PowerState(0, 0, ConstantConsumptionModel::new));

        int numNodes = 1;

        // Repository setup
        final String repoid = "Storage";
		// scaling the bandwidth accroding to the size of the cloud TODO
		// A single repo will hold 36T of data TODO
		HashMap<String, Integer> latencyMapRepo = new HashMap<String, Integer>(numNodes + 2);
		Repository mainStorage = new Repository(ram, repoid, (long) (bw),
				(long) (bw), (long) (bw), latencyMapRepo, storageTransitions, networkTransitions);
        iaasservice.registerRepository(mainStorage);

        // Setup PMs
        double perCoreProcessing = 1; // Instructions/tick
        long diskBW = 1000; // TODO: in, out, disk
        int onDelay = 0; // TODO: time delay start-up (machine's switch on and first time it can serve VM requests)
        int offDelay = 0; // TODO: time delay the machine needs to shut down all of its operations while it does not serve any more VMs

		ArrayList<PhysicalMachine> completePMList = new ArrayList<PhysicalMachine>(numNodes);
		HashMap<String, Integer> latencyMapMachine = new HashMap<String, Integer>(numNodes + 2);
		latencyMapMachine.put(repoid, 5); // 5 ms latency towards the repos TODO
		final String machineid = "Node";
		for (int i = 1; i <= numNodes; i++) {
			String currid = machineid + i;
			final double pmBWRatio = Math.max(cores / 7f, 1); // TODO
			PhysicalMachine pm = new PhysicalMachine(cores, perCoreProcessing, ram,
					new Repository(ram, currid, (long) (pmBWRatio * 250000), (long) (pmBWRatio * 250000),
							(long) (pmBWRatio * 50000), latencyMapMachine, storageTransitions, networkTransitions),
					onDelay, offDelay, cpuTransitions);
			latencyMapRepo.put(currid, latency);
			latencyMapMachine.put(currid, latency);
			completePMList.add(pm);
		}
        iaasservice.bulkHostRegistration(completePMList);
        List<IaaSService> iaasList = new ArrayList<IaaSService>();
        iaasList.add(iaasservice);

        // Meter setup for the PMs
        final ArrayList<Long> readingtime = new ArrayList<Long>();
		final ArrayList<Double> readingpm1 = new ArrayList<Double>();
        PhysicalMachine pm1 = completePMList.get(0);
        PhysicalMachineEnergyMeter pmm1 = new PhysicalMachineEnergyMeter(pm1);

        // Wait until the PM Controllers finish their initial activities
        Timed.simulateUntilLastEvent(); // Setting up

        System.out.println("Done setting up Taxonomy");

        // Load trace
        GenericTraceProducer producer = FileBasedTraceProducerFactory.getProducerFromFile(tracePath, 0, 1000, true, (int)iaasservice.getCapacities().getRequiredCPUs(), DCFJob.class);
        MultiIaaSJobDispatcher dispatcher = new MultiIaaSJobDispatcher(producer, iaasList, mainThread);
        // Thread.sleep(50000);
        long beforeSimu = Calendar.getInstance().getTimeInMillis();
		System.err.println(
				"Job dispatcher (with " + dispatcher.getNumJobs() + " jobs) is completely prepared at " + beforeSimu);
		// Moving the simulator's time just before the first event would come
		// from the dispatcher
		Timed.skipEventsTill(dispatcher.getMinsubmittime());

        new StateMonitor(outPath, dispatcher, iaasList, 1, mainThread);
        // The actual simulation
		Timed.simulateUntilLastEvent();
		// The simulation is complete all activities have finished by the
		// dispatcher and monitor
		long afterSimu = Calendar.getInstance().getTimeInMillis();
		long duration = afterSimu - beforeSimu;

		// Printing out generic timing and performance statistics:
		System.err.println("Simulation terminated " + afterSimu + " (took " + duration + "ms in realtime)");
		System.err.println("Simulated timespan: " + (Timed.getFireCount() - dispatcher.getMinsubmittime()) + " seconds");
		System.err.println("Final number of: Ignored jobs - " + dispatcher.getIgnorecounter() + " Destroyed VMs - "
				+ dispatcher.getDestroycounter() + " reused VMs: " + dispatcher.reuseCounter);

        System.err.println("Average number of PMs in running state: " + StateMonitor.averageRunningPMs);
        System.err.println(
                "Maximum number of running PMs during the whole simulation: " + StateMonitor.maxRunningPMs);

        long vmcount = 0;
        for (IaaSService lociaas : iaasList) {
            for (PhysicalMachine pm : lociaas.machines) {
                vmcount += pm.getCompletedVMs();
            }
        }
        System.err.println("Performance: " + (((double) vmcount) / duration) + " VMs/ms ");

    }
}
