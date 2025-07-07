package hu.mta.sztaki.lpds.cloud.simulator.examples.jobhistoryprocessor;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
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

/* Uses PowerTransitionGenerator (Linear) */
public class Simple {
    public static void main(String[] args) throws Exception {
        Timed.resetTimed();

        // Topology setup
        Class<? extends Scheduler> vmScheduler = FirstFitScheduler.class;
		Class<? extends PhysicalMachineController> pmController = SchedulingDependentMachines.class;
        IaaSService iaasservice = new IaaSService(vmScheduler, pmController);

        double minpower = 0;
        double idlepower = 9.829087373984885e-10;
        double maxpower = 1e10;
        // double maxpower = 0.526594848735254;
        final EnumMap<PowerTransitionGenerator.PowerStateKind, Map<String, PowerState>> transitions = PowerTransitionGenerator
				.generateTransitions(minpower, idlepower, maxpower, 1000, 1000);
		final Map<String, PowerState> cpuTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.host);
        System.out.println(cpuTransitions);
		final Map<String, PowerState> stTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.storage);
		final Map<String, PowerState> nwTransitions = transitions.get(PowerTransitionGenerator.PowerStateKind.network);


        int numNodes = 2;
        double cores = 8;

        // Repository setup
        final String repoid = "Storage";
		// scaling the bandwidth accroding to the size of the cloud TODO
		final double bwRatio = (cores * numNodes) / (7f * 64f);
		// A single repo will hold 36T of data TODO
		HashMap<String, Integer> latencyMapRepo = new HashMap<String, Integer>(numNodes + 2);
		Repository mainStorage = new Repository(36000000000000l, repoid, (long) (bwRatio * 1250000),
				(long) (bwRatio * 1250000), (long) (bwRatio * 250000), latencyMapRepo, stTransitions, nwTransitions);
        iaasservice.registerRepository(mainStorage);

        // Setup PMs
        double perCoreProcessing = 1; // Instructions/tick
        long memory = 378000; // 378 GB
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
			PhysicalMachine pm = new PhysicalMachine(cores, perCoreProcessing, memory,
					new Repository(memory, currid, (long) (pmBWRatio * 250000), (long) (pmBWRatio * 250000),
							(long) (pmBWRatio * 50000), latencyMapMachine, stTransitions, nwTransitions),
					onDelay, offDelay, cpuTransitions);
			latencyMapRepo.put(currid, 5);
			latencyMapMachine.put(currid, 3);
			completePMList.add(pm);
		}
        iaasservice.bulkHostRegistration(completePMList);
        List<IaaSService> iaasList = new ArrayList<IaaSService>();
        iaasList.add(iaasservice);

        // // Basic PM construction
        // PhysicalMachine pm1, pm2;
        // pm1 = new PhysicalMachine(cores, perCoreProcessing, memory,
		// 		new Repository(memory, "pm1", diskBW, diskBW, diskBW, latencyMap, stTransitions, nwTransitions), onDelay, offDelay,
		// 		cpuTransitions);
		// pm2 = new PhysicalMachine(1, 1, 1000,
		// 		new Repository(disksize, "pm2", 10000, 10000, 10000, latencyMap, stTransitions, nwTransitions), onDelay, offDelay,
		// 		cpuTransitions);

        // // Meter setup for the PMs
        // final PhysicalMachineEnergyMeter pmm1, pmm2; // Energy meters for Physical Machines.
        // final long freq = 100; // event frequence of measuring (in ms?)
		// pmm1 = new PhysicalMachineEnergyMeter(pm1);
		// pmm2 = new PhysicalMachineEnergyMeter(pm2);
		// class MeteredDataCollector extends Timed {
		// 	// Call when we need to initiate data collection
		// 	public void start() {
		// 		subscribe(freq);
		// 	}

		// 	// Call when we need to terminate data collection
		// 	public void stop() {
		// 		unsubscribe();
		// 	}

		// 	// Automatically called with the frequency specified above
		// 	@Override
		// 	public void tick(final long fires) {
		// 		// Actual periodic data collection
		// 		readingtime.add(fires);
		// 		readingpm1.add(pmm1.getTotalConsumption());
		// 		readingpm2.add(pmm2.getTotalConsumption());
		// 	}
		// }
		// final MeteredDataCollector mdc = new MeteredDataCollector();

        // Wait until the PM Controllers finish their initial activities
        Timed.simulateUntilLastEvent(); // Setting up??

        System.out.println("Done setting up Taxonomy");

        // Load trace
        GenericTraceProducer producer = FileBasedTraceProducerFactory.getProducerFromFile(args[0], 0, Integer.parseInt(args[1]), false, (int)iaasservice.getCapacities().getRequiredCPUs(), DCFJob.class);
        MultiIaaSJobDispatcher dispatcher = new MultiIaaSJobDispatcher(producer, iaasList);
        // TODO load traces.

        long beforeSimu = Calendar.getInstance().getTimeInMillis();
		System.err.println(
				"Job dispatcher (with " + dispatcher.getNumJobs() + " jobs) is completely prepared at " + beforeSimu);
		// Moving the simulator's time just before the first event would come
		// from the dispatcher
		Timed.skipEventsTill(dispatcher.getMinsubmittime() * 1000);
		System.err.println("Current simulation time: " + Timed.getFireCount());
        int interval = 5000; // ms

        new StateMonitor(args[0], dispatcher, iaasList, interval, true);
        // The actual simulation
		Timed.simulateUntilLastEvent();
		// The simulation is complete all activities have finished by the
		// dispatcher and monitor
		long afterSimu = Calendar.getInstance().getTimeInMillis();
		long duration = afterSimu - beforeSimu;

		// Printing out generic timing and performance statistics:
		System.err.println("Simulation terminated " + afterSimu + " (took " + duration + "ms in realtime)");
		System.err.println("Current simulation time: " + Timed.getFireCount());
		System.err.println("Simulated timespan: " + (Timed.getFireCount() - dispatcher.getMinsubmittime() * 1000));
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
