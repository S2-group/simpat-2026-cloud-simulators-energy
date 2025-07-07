/*
 *  ========================================================================
 *  DISSECT-CF Examples
 *  ========================================================================
 *
 *  This file is part of DISSECT-CF Examples.
 *
 *  DISSECT-CF Examples is free software: you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or (at
 *  your option) any later version.
 *
 *  DISSECT-CF Examples is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with DISSECT-CF Examples.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  (C) Copyright 2017, Gabor Kecskemeti (g.kecskemeti@ljmu.ac.uk)
 *  (C) Copyright 2013-15, Gabor Kecskemeti (gkecskem@dps.uibk.ac.at,
 *   									  kecskemeti.gabor@sztaki.mta.hu)
 */
package hu.mta.sztaki.lpds.cloud.simulator.examples.custom;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.specialized.IaaSEnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.energy.specialized.PhysicalMachineEnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;

/**
 * Collects and aggregates statistical data representing a particular run of a
 * cloud system.
 *
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2017"
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
 *         MTA SZTAKI (c) 2012-5"
 */
class StateMonitor extends Timed {
	/**
	 * All collected data that has not been written out yet
	 */
	private ConcurrentLinkedQueue<OverallSystemState> monitoringDataQueue = new ConcurrentLinkedQueue<OverallSystemState>();
	private boolean continueRunning = true;

    private Thread mainThread = null;

	/**
	 * The list of energy meters controlled by this state monitor. During a regular
	 * runtime, here we will have a meter for every physical machine in the system.
	 * Please note that the monitor does not recognize the changes in the set of PMs
	 * in an IaaSService.
	 */
	private ArrayList<PhysicalMachineEnergyMeter> meters = new ArrayList<PhysicalMachineEnergyMeter>();
	/**
	 * The dispatcher which sends the jobs to the actual clouds in some VMs. The
	 * dispatcher is expected to unsubscribe from timing events if it is no longer
	 * having jobs to be executed in a trace. The monitor watches this case and
	 * ensures that the monitoring is terminated once the dispatcher is not pushing
	 * new jobs towards the clouds and there are no furhter activities observable on
	 * the cloud becaouse of the dispatcher.
	 */
	final MultiIaaSJobDispatcher dispatcher;
	/**
	 * The list if cloud services that should be monitored by this state monitor.
	 */
	final List<IaaSService> iaasList;

    private FileWriter fr;

	/**
	 * Initiates the state monitoring process by setting up the energy meters,
	 * creating the output csv file (called [tracefile].converted) and subscribing
	 * to periodic timing events (for every 5 minutes) so the metering queires can
	 * be made automatically.
	 *
	 * WARNING: this function keeps a file open until the dispatcher terminates its
	 * operation!
	 *
	 * @param outPath    the name of the output csv (without the .converted
	 *                   extension)
	 * @param dispatcher the dispatcher that sends its jobs to the clouds
	 * @param iaasList   the clouds that needs to be monitored
	 * @param interval   the energy metering interval to be applied
	 * @throws IOException if there was a output file creation error
	 */
	public StateMonitor(String outPath, MultiIaaSJobDispatcher dispatcher, List<IaaSService> iaasList, int interval, Thread mainThread) throws IOException {
        this.mainThread = mainThread;
		System.err.println("Power metering started with delay " + interval);

        File energyFile = new File(outPath);
        this.fr = new FileWriter(energyFile);
        fr.write("");

		this.iaasList = iaasList;
		this.dispatcher = dispatcher;
		for (IaaSService iaas : iaasList) {
			// IaaSEnergyMeter iaasMeter = new IaaSEnergyMeter(iaas);
			// iaasMeter.startMeter(interval, false);
			// meters.add(iaasMeter);
            for (PhysicalMachine pm : iaas.machines) {
                PhysicalMachineEnergyMeter pmm = new PhysicalMachineEnergyMeter(pm);
                pmm.startMeter(interval, true);
                meters.add(pmm);
            }
		}
		subscribe(interval); // in ms
	}

	public static double averageRunningPMs = 0;
	public static int maxRunningPMs = 0;
	private long measurementCount = 0;
	private double totRunningPMs = 0;

	/**
	 * The main event handling mechanism in this periodic state monitor. This
	 * function is called in every 5 simulated minutes.
	 */
	@Override
	public void tick(long fires) {
		measurementCount++;
		// Collecting the monitoring data
		OverallSystemState current = new OverallSystemState();
		final int iaasCount = iaasList.size();
		for (int i = 0; i < iaasCount; i++) {
			IaaSService iaas = iaasList.get(i);
			int msize = iaas.machines.size();
			for (int j = 0; j < msize; j++) {
				PhysicalMachine pm = iaas.machines.get(j);
				current.finishedVMs += pm.getCompletedVMs();
				current.runningVMs += pm.numofCurrentVMs();
				current.usedCores += pm.getCapacities().getRequiredCPUs() - pm.freeCapacities.getRequiredCPUs();
				current.runningPMs += pm.isRunning() ? 1 : 0;
			}
			current.queueLen += iaas.sched.getQueueLength();
			current.totalTransferredData += iaas.repositories.get(0).outbws.getTotalProcessed();
		}
		maxRunningPMs = Math.max(maxRunningPMs, current.runningPMs);
		totRunningPMs += current.runningPMs;
		current.timeStamp = Timed.getFireCount();
		// Recording it
		monitoringDataQueue.add(current);

        double energysum = 0;
        for (PhysicalMachineEnergyMeter m : meters) {
            energysum += m.getTotalConsumption();
        }

        try {
            fr.append(current.timeStamp + "," + energysum + "\n");
        } catch (IOException e) {
            System.err.println(e.getStackTrace());
            System.err.println("Problem writing to energy file!");
            System.exit(1);
        }

		// Checking for termination conditions:
		if (!dispatcher.isSubscribed() && current.queueLen == 0 && current.runningVMs == 0) {
			// We now terminate
			unsubscribe(); // first we cancel our future events
			// then we cancel the future energy monitoring of all PMs
			for (PhysicalMachineEnergyMeter em : meters) {
				em.stopMeter();
			}
			continueRunning = false;
			double sum = 0;
			// finally we collect and aggregate the energy consumption data
			for (PhysicalMachineEnergyMeter m : meters) {
				sum += m.getTotalConsumption();
			}
			averageRunningPMs = totRunningPMs / measurementCount;
			System.err.println("Total power consumption: " + sum + " Joules");

            try {
                fr.close();
            } catch (IOException e) {
                System.err.println("Problem closing energy file!");
            }
		}
	}
}
