package at.ac.uibk.dps.cloud.simulator.examples.tests;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.examples.vmallocmultitenant.ComponentType;
import hu.mta.sztaki.lpds.cloud.simulator.examples.vmallocmultitenant.MultiTenantConsolidator;
import hu.mta.sztaki.lpds.cloud.simulator.examples.vmallocmultitenant.MultiTenantController;
import hu.mta.sztaki.lpds.cloud.simulator.examples.vmallocmultitenant.MultiTenantPMScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.examples.vmallocmultitenant.MultiTenantVMScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

public class VMAllocMultiTenantTest extends IaaSRelatedFoundation {

	/**
	 * @author Rene Ponto
	 * 
	 * Testcases:
	 * 
	 * Create situation with overAllocated PMs and check if the consolidation is done correctly
	 * Create situation with underAllocated PMs and check if the consolidation is done correctly
	 */

	// Creation of all necessary objects and variables

	IaaSService basic;
	MultiTenantConsolidator cons;
	PhysicalMachine testPM1;
	PhysicalMachine testPM2;
	PhysicalMachine testPM3;
	PhysicalMachine testPM4;

	VirtualMachine VM1;
	VirtualMachine VM2;
	VirtualMachine VM3;
	VirtualMachine VM4;
	VirtualMachine VM5;
	VirtualMachine VM6;
	VirtualMachine VM7;
	VirtualMachine VM8;

	VirtualAppliance VA1;	
	VirtualAppliance VA2;
	VirtualAppliance VA3;	
	VirtualAppliance VA4;
	VirtualAppliance VA5;
	VirtualAppliance VA6;
	VirtualAppliance VA7;
	VirtualAppliance VA8;

	HashMap<String, Integer> latmap = new HashMap<String, Integer>();	
	Repository centralRepo;

	final static int reqcores = 8, reqProcessing = 1, reqmem = 16,
			reqond = 2 * (int) aSecond, reqoffd = (int) aSecond, reqDisk=100000;

	//The different ResourceConstraints to get an other allocation for each PM

	final ResourceConstraints smallConstraints = new ConstantConstraints(1, 1, 2);	
	final ResourceConstraints mediumConstraints = new ConstantConstraints(3, 1, 6);	
	final ResourceConstraints bigConstraints = new ConstantConstraints(5, 1, 8);

	private PhysicalMachine createPm(String id, double cores, double perCoreProcessing, int ramMb, int diskMb) {
		long bandwidth=1000000;
		Repository disk = new Repository(diskMb*1024*1024, id, bandwidth, bandwidth, bandwidth, latmap, defaultStorageTransitions, defaultNetworkTransitions);
		PhysicalMachine pm = new PhysicalMachine(cores, perCoreProcessing, ramMb*1024*1024, disk, reqond, reqoffd, defaultHostTransitions);
		latmap.put(id,1);
		return pm;
	}

	//This method deploys one VM to a target PM	
	private void switchOnVM(VirtualMachine vm, ResourceConstraints cons, PhysicalMachine pm, boolean simulate) throws VMManagementException, NetworkException {
		vm.switchOn(pm.allocateResources(cons, true, PhysicalMachine.defaultAllocLen), centralRepo);
		if(simulate) {
			Timed.simulateUntilLastEvent();
		}
	}

	/**
	 * Now three PMs and four VMs are going to be instantiated.
	 * At first the PMs are created, after that the VMs are created and each of them deployed to one PM. 
	 */	
	@Before
	public void testSim() throws Exception {
		Handler logFileHandler;
		try {
			logFileHandler = new FileHandler("vmallocmultitenantlog.txt");
			logFileHandler.setFormatter(new SimpleFormatter());
			Logger.getGlobal().addHandler(logFileHandler);
		} catch (Exception e) {
			System.out.println("Could not open log file for output"+e);
			System.exit(-1);
		}

		basic = new IaaSService(MultiTenantVMScheduler.class, MultiTenantPMScheduler.class);

		//create central repository
		long bandwidth=1000000;
		centralRepo = new Repository(100000, "test1", bandwidth, bandwidth, bandwidth, latmap, defaultStorageTransitions, defaultNetworkTransitions);
		latmap.put("test1", 1);
		basic.registerRepository(centralRepo);

		//create PMs
		testPM1 = createPm("pm1", reqcores, reqProcessing, reqmem, reqDisk);
		testPM2 = createPm("pm2", reqcores, reqProcessing, reqmem, reqDisk);
		testPM3 = createPm("pm3", reqcores, reqProcessing, reqmem, reqDisk);
		testPM4 = createPm("pm4", reqcores, reqProcessing, reqmem, reqDisk);
		
		//register PMs
		basic.registerHost(testPM1);
		basic.registerHost(testPM2);
		basic.registerHost(testPM3);
		basic.registerHost(testPM4);

		// The four VMs set the Load of PM1 to overAllocated, PM2 to underoverAllocated and PM3 to normal.				
		VA1 = new VirtualAppliance("VM 1", 1, 0, false, 1);
		VA2 = new VirtualAppliance("VM 2", 1, 0, false, 1);
		VA3 = new VirtualAppliance("VM 3", 1, 0, false, 1);
		VA4 = new VirtualAppliance("VM 4", 1, 0, false, 1);
		VA5 = new VirtualAppliance("VM 5", 1, 0, false, 1);
		VA6 = new VirtualAppliance("VM 6", 1, 0, false, 1);
		VA7 = new VirtualAppliance("VM 7", 1, 0, false, 1);
		VA8 = new VirtualAppliance("VM 7", 1, 0, false, 1);

		//save the VAs in the repository
		centralRepo.registerObject(VA1);
		centralRepo.registerObject(VA2);
		centralRepo.registerObject(VA3);
		centralRepo.registerObject(VA4);		
		centralRepo.registerObject(VA5);
		centralRepo.registerObject(VA6);
		centralRepo.registerObject(VA7);
		centralRepo.registerObject(VA8);	
		
		VM1 = new VirtualMachine(VA1);
		VM2 = new VirtualMachine(VA2);
		VM3 = new VirtualMachine(VA3);
		VM4 = new VirtualMachine(VA4);
		VM5 = new VirtualMachine(VA5);
		VM6 = new VirtualMachine(VA6);
		VM7 = new VirtualMachine(VA7);
		VM8 = new VirtualMachine(VA8);
	}
	
	// testcases
	
//	@Test(timeout = 1000)
//	public void compTypesTest() {
//		MultiTenantController controller = new MultiTenantController();
//		controller.readCompTypes();
//		ArrayList<ComponentType> results = MultiTenantPMScheduler.getTypes();
//		
//		Assert.assertEquals(7, results.size());
//	}
	
	@Ignore
	@Test(timeout = 1000)
	public void requestsTest() {
		MultiTenantController controller = new MultiTenantController();
		controller.readCompTypes();
		int result = controller.readRequests();
		
		Assert.assertEquals(15, result);
	}
	
//	@Test(timeout = 1000)
//	public void overAllocSimpleTest() throws VMManagementException, NetworkException {
//		
//	}
//	
//	@Test(timeout = 1000)
//	public void overAllocComplexTest() throws VMManagementException, NetworkException {
//		
//	}
//
//	@Test(timeout = 1000)
//	public void underAllocSimpleTest() throws VMManagementException, NetworkException {
//		
//	}
//	
//	@Test(timeout = 1000)
//	public void underAllocComplexTest() throws VMManagementException, NetworkException {
//		
//	}
	
}
