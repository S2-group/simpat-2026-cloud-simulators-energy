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
 *  (C) Copyright 2013-15, Gabor Kecskemeti (gkecskem@dps.uibk.ac.at,
 *   									  kecskemeti.gabor@sztaki.mta.hu)
 */
package hu.mta.sztaki.lpds.cloud.simulator.examples.jobhistoryprocessor;

/**
 * A class that allows the storage of system state and with its tostring method
 * it also allows the formatting of the state in a CSV format ready for analysis
 * by third party software.
 * 
 * @author 
 *         "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems, MTA SZTAKI (c) 2012-5"
 */
public class OverallSystemState {
	/**
	 * the time at which this state report was collected (in time ticks of the
	 * simulator, in most setups this is in ms)
	 */
	public long timeStamp;
	/**
	 * The number of virtual machines the simulated infrastructure clouds has
	 * processed by the given time. (i.e., those VMs that have been on the
	 * system but are no longer running)
	 */
	public int finishedVMs;
	/**
	 * The length of the VM scheduler's queue at the given time (i.e. how many
	 * VMs were requested but did not get the chance to be allocated to a PM).
	 */
	public int queueLen;
	/**
	 * The number of VMs that ran on the infrastructure at the given time
	 * instance.
	 */
	public int runningVMs;
	/**
	 * The number of CPU cores utilized by the running VMs at time the report
	 * was collected.
	 */
	public int usedCores;
	/**
	 * The number of PMs that were on at the moment of data collection.
	 */
	public int runningPMs;
	/**
	 * The total number of bytes transferred since the beginning of time until
	 * the reporting time
	 */
	public double totalTransferredData;

	/**
	 * Provides the following output format:
	 * 
	 * UnixTime*1000,NrFinished,NrQueued,VMNum,UsedCores,OnPMs,CentralRepoTX
	 */
	@Override
	public String toString() {
		return timeStamp + "," + finishedVMs + "," + queueLen + ","
				+ runningVMs + "," + usedCores + "," + runningPMs + ","
				+ (long) (totalTransferredData) + "\n";
	}
}
