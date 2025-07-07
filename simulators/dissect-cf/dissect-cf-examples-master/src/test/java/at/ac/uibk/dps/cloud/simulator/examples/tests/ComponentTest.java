package at.ac.uibk.dps.cloud.simulator.examples.tests;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;
import hu.mta.sztaki.lpds.cloud.simulator.examples.vmallocmultitenant.ComponentInstance;
import hu.mta.sztaki.lpds.cloud.simulator.examples.vmallocmultitenant.ComponentType;
import hu.mta.sztaki.lpds.cloud.simulator.examples.vmallocmultitenant.MultiTenantPMScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.examples.vmallocmultitenant.Request;
import hu.mta.sztaki.lpds.cloud.simulator.examples.vmallocmultitenant.Request.Type;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.NonQueueingScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;

public class ComponentTest extends IaaSRelatedFoundation {
	
	IaaSService basic;
	Repository r;
	VirtualMachine vm1;
	PhysicalMachine pm1;
	
	ComponentType ctype;
	ComponentInstance cinstance;
	String[] init = new String[]{"StoreMgr", "Provide", "0.2", "0.001", "20", "true"};

	@Before
	public void setUpBeforeClass() throws Exception {
		ctype = new ComponentType(init[0], init[1], new ConstantConstraints(Double.parseDouble(init[2]), 
				Double.parseDouble(init[3]), Long.parseLong(init[4])), Boolean.parseBoolean(init[5]));		
	}

	@Test(timeout = 100)
	public void testCreationOfComponentType() {
		Assert.assertNotNull(ctype);
	}
	
	@Test(timeout = 100)
	public void testCreationOfComponentInstance() {
		cinstance = ctype.createInstance(false);
		Assert.assertNotNull(cinstance);
		Assert.assertEquals(ctype, cinstance.getType());
		Assert.assertEquals(null, cinstance.getConsumption());
		Assert.assertEquals("StoreMgr0", cinstance.getName());
	}
	
	@Test(timeout = 100)
	public void testRequestFunctionality() { 
		cinstance = ctype.createInstance(false);
		Request r = new Request("A", ctype, ConstantConstraints.noResources, false, false, false, 0, 0, Type.NEW_REQUEST);
		cinstance.addRequest(r);
		
		Assert.assertEquals(1, cinstance.getRequests().size());
		Assert.assertEquals(null, cinstance.getConsumption());
		Assert.assertEquals(null, cinstance.getVm());
	}
	
	@Test(timeout = 100)
	public void testResourceConsumptionFunctionality() throws Exception { 
		
		IaaSService basic = new IaaSService(NonQueueingScheduler.class,	MultiTenantPMScheduler.class);
		pm1 = dummyPMcreator();
		basic.registerHost(pm1);
		r = dummyRepoCreator(true);
		basic.registerRepository(r);
		
		//TODO
	}

}
