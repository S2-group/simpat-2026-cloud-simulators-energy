package hu.mta.sztaki.lpds.cloud.simulator.examples.vmallocmultitenant;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.pmiterators.PMIterator;

public class MultiTenantVMScheduler extends Scheduler {

	public MultiTenantVMScheduler(IaaSService parent) {
		super(parent);
		it = instantiateIterator();
	}
	
	private final PMIterator it;
	
	protected PMIterator instantiateIterator() {
		return new PMIterator(parent.runningMachines);
	}
	
	protected PMIterator getPMIterator() {
		it.reset();
		return it;
	}

	@Override
	protected ConstantConstraints scheduleQueued() {
		final PMIterator currIterator = getPMIterator();
		ConstantConstraints returner = new ConstantConstraints(getTotalQueued());
		//TODO
		return null;
	}

}
