package hu.mta.sztaki.lpds.cloud.simulator.examples.vmallocmultitenant;


import java.util.Vector;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.ConsolidationFriendlyPmScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler.QueueingEvent;

	/**
	 * A PMScheduler supporting secure enclaves. 
	 * 
	 * The algorithms in this class are taken out of the paper "Optimized Cloud 
	 * Deployment of Multi-tenant Software Considering Data Protection Concerns" 
	 * by Zoltan Adam Mann and Andreas Metzger, published in CCGrid 2017.
	 * 
	 * TODO for improvement:
	 * - Extend the scheduling logic for requests and components
	 * - fill mapping while scheduling
	 * 
	 * @author Rene Ponto
	 */

public class MultiTenantPMScheduler extends ConsolidationFriendlyPmScheduler implements Helpers {
		
	public MultiTenantPMScheduler(IaaSService parent) {
		super(parent);
	}
	
	/**
	 * The VM scheduler alarms us that there are not enough running PMs -> we
	 * should turn on one or more PMs, if we can and unless sufficient PMs are 
	 * being turned on already.
	 * TODO
	 */
	@Override
	protected QueueingEvent getQueueingEvent() {
		return new Scheduler.QueueingEvent() {
			@Override
			public void queueingStarted() {
				//First we determine the set of PMs that are off 
				//and the total capacity of the PMs that are currently being turned on
				Vector<PhysicalMachine> offPms=new Vector<>();
				AlterableResourceConstraints capacityTurningOn=AlterableResourceConstraints.getNoResources();
				for(PhysicalMachine pm : parent.machines) {
					if(PhysicalMachine.ToOfforOff.contains(pm.getState())) {
						offPms.add(pm);
					}
					if(pm.getState().equals(PhysicalMachine.State.SWITCHINGON)) {
						capacityTurningOn.singleAdd(pm.getCapacities());
					}
				}
				//We should turn on PMs as long as there are PMs that are off 
				//and the capacity of the PMs being turned on is not sufficient for the requests in the queue
				while(offPms.size()>0 && capacityTurningOn.compareTo(parent.sched.getTotalQueued())<0) {
					PhysicalMachine pm=offPms.remove(offPms.size()-1);
					capacityTurningOn.singleAdd(pm.getCapacities());
					pm.turnon();
				}
			}
		};
	}

	
	
}
