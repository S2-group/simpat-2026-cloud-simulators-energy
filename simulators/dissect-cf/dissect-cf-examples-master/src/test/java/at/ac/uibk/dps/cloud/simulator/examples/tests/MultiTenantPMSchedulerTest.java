package at.ac.uibk.dps.cloud.simulator.examples.tests;

import java.util.ArrayList;
import java.util.HashMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.examples.vmallocmultitenant.ComponentType;
import hu.mta.sztaki.lpds.cloud.simulator.examples.vmallocmultitenant.MultiTenantComponentScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.examples.vmallocmultitenant.MultiTenantPMScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.NonQueueingScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

public class MultiTenantPMSchedulerTest extends IaaSRelatedFoundation {
	IaaSService basic;
	Repository r;
	PhysicalMachine testPM1;
	PhysicalMachine testPM2;
	PhysicalMachine testPM3;
	PhysicalMachine testPM4;
	
	VirtualMachine vm1;
	VirtualAppliance va1;
	
	final ResourceConstraints smallConstraints = new ConstantConstraints(1, 1, 2);	
	

	@Before
	public void resetSim() throws Exception {
		basic = new IaaSService(NonQueueingScheduler.class,	MultiTenantPMScheduler.class);
		testPM1 = dummyPMcreator();
		testPM2 = dummyPMcreator();
		basic.registerHost(testPM1);
		basic.registerHost(testPM2);
		r = dummyRepoCreator(true);
		basic.registerRepository(r);
	}

	@Test(timeout = 100)
	public void turnonTest() {
		Assert.assertEquals("There should be no running machines at the beginning of the simulation",
				0, basic.runningMachines.size());
		Timed.simulateUntilLastEvent();
		testPM1.turnon();
		testPM2.turnon();
		Timed.simulateUntilLastEvent();
		
		Assert.assertEquals("Did not switch on all machines as expected",
				basic.machines.size(), basic.runningMachines.size());
	}
	
	@Test(timeout = 100)
	public void addMorePMsTest() {
		
		testPM1.turnon();
		testPM2.turnon();
		Timed.simulateUntilLastEvent();
		
		testPM3 = dummyPMcreator();
		testPM4 = dummyPMcreator();
		basic.registerHost(testPM3);
		basic.registerHost(testPM4);
		
		Timed.simulateUntilLastEvent();
		
		testPM3.turnon();
		testPM4.turnon();
		
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Did not switch on all machines as expected",
				basic.machines.size(), basic.runningMachines.size());
	}
	
	//This method deploys one VM to a target PM	
	private void switchOnVM(VirtualMachine vm, ResourceConstraints cons, PhysicalMachine pm, boolean simulate) throws VMManagementException, NetworkException {
		vm.switchOn(pm.allocateResources(cons, true, PhysicalMachine.defaultAllocLen), r);
		if(simulate) {
			Timed.simulateUntilLastEvent();
		}
	}
	
	@Ignore
	@Test(timeout = 100)
	public void testProcessRequestWithCompType() throws VMManagementException, NetworkException {
		testPM1.turnon();
		Timed.simulateUntilLastEvent();
		
		va1 = new VirtualAppliance("VM 1", 1, 0, false, 1);
		vm1 = new VirtualMachine(va1);
		Timed.simulateUntilLastEvent();
		switchOnVM(vm1, smallConstraints, testPM1, true);
		
		HashMap<String, ArrayList<String>> initialCompType = new HashMap<String, ArrayList<String>>();
		ArrayList<String> list = new ArrayList<String>();
		list.add("Provider");
    	list.add("2.0");
    	list.add("0.001");		// processing power
    	list.add("20.0");
    	list.add("true");
		initialCompType.put("StoreMgr", list);
		MultiTenantComponentScheduler.instantiateTypes(initialCompType);
		
		ComponentType type = MultiTenantComponentScheduler.getTypes().get(0);
		
		//TODO need to check and improve the logic of the mapping-object inside the ComponentScheduler
		
		
	}

}
