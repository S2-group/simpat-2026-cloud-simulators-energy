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
 *  (C) Copyright 2016, Gabor Kecskemeti (g.kecskemeti@ljmu.ac.uk)
 *  (C) Copyright 2013-15, Gabor Kecskemeti (gkecskem@dps.uibk.ac.at,
 *   									  kecskemeti.gabor@sztaki.mta.hu)
 */
package hu.mta.sztaki.lpds.cloud.simulator.examples.jobhistoryprocessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.helpers.job.Job;

/**
 * Implements the abstract methods of the Job class for the DISSECT-CF
 * simulator. Basically allows the recording of simulation time to the original
 * job representation.
 * 
 * @author "Gabor Kecskemeti, Department of Computer Science, Liverpool John
 *         Moores University, (c) 2016"
 * @author "Gabor Kecskemeti, Laboratory of Parallel and Distributed Systems,
 *         MTA SZTAKI (c) 2012"
 */
public class DCFJob extends Job {

	private ArrayList<Job> afterThisJob = null;

	private List<Job> dependants = null;

	private boolean firstQuery = true;

	/**
	 * Forwards the constructor to the Job's appropriate constructor
	 * 
	 * @param submit
	 * @param queue
	 * @param exec
	 * @param nprocs
	 * @param user
	 * @param executable
	 */
	public DCFJob(String id, long submit, long queue, long exec, int nprocs, double ppCpu, long ppMem, String user,
			String group, String executable, Job preceding, long delayAfter) {
		super(id, submit, queue, exec, nprocs, ppCpu, ppMem, user, group, executable, preceding, delayAfter);
		if (preceding != null) {
			DCFJob j = (DCFJob) preceding;
			if (j.afterThisJob == null) {
				j.afterThisJob = new ArrayList<Job>();
			}
			j.afterThisJob.add(this);
		}
	}

	/**
	 * Records the duration this job spent in the queue.
	 */
	public void started() {
		setRealqueueTime(Timed.getFireCount() / 1000 - getSubmittimeSecs());
	}

	/**
	 * Records the duration this job was executed for.
	 */
	public void completed() {
		setRan(true);
		setRealstopTime(Timed.getFireCount() / 1000 - getSubmittimeSecs());
	}

	public List<Job> getDependants() {
		if (firstQuery) {
			if (afterThisJob != null) {
				dependants = Collections.unmodifiableList(afterThisJob);
			}
			firstQuery = false;
		}
		return dependants;
	}

}
