package hu.mta.sztaki.lpds.cloud.simulator.examples.vmallocmultitenant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.consolidation.Consolidator;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

	/**
	 * A consolidator which supports secure enclaves on physical machines.
	 * 
	 * The algorithms in this class are taken out of the paper "Optimized Cloud 
	 * Deployment of Multi-tenant Software Considering Data Protection Concerns" 
	 * by Zoltan Adam Mann and Andreas Metzger, published in CCGrid 2017.
	 *
	 * @author Rene Ponto
	 */

public class MultiTenantConsolidator extends Consolidator implements Helpers {
	
	private HashMap<VirtualMachine, ArrayList<ComponentInstance>> mapping;
	
	public static int reoptimizations;

	/**
	 * The constructor of this class. It expects a HashMap with the actual mapping of
	 * VMs to ComponentInstances to work properly.
	 * 
	 * @param toConsolidate
	 * 			The used IaaSService.
	 * @param consFreq
	 *          This value determines, how often the consolidation should run.	  				
	 * @param mapping
	 * 			The actual mapping of VMs to ComponentInstances.
	 */
	public MultiTenantConsolidator(IaaSService toConsolidate, long consFreq, HashMap<VirtualMachine, ArrayList<ComponentInstance>> mapping) {
		super(toConsolidate, consFreq);
		this.mapping = mapping;
	}

	@Override
	protected void doConsolidation(PhysicalMachine[] pmList) {
		try {
			reoptimize(pmList);
		} catch (VMManagementException e) {
			e.printStackTrace();
		} catch (NetworkException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Inside this method the actual consolidation happens. Three kinds of optimization 
	 * opportunities are explored:
	 * 		- emptying an active PM by migrating all its VMs to some other already active PMs.
	 * 		- if a secure PM can be switched on and take the load of two unsecure PMs, the load 
	 * 		  gets migrated to the secure one, and the two emptied PMs can be switched off.
	 * 		- if there is an active secure PM, the VMs of which would not need separation, then 
	 * 		  they can all be migrated to a newly switched-on non-secure PM, and the secure PM 
	 * 		  can be switched off.
	 * 
	 * @param pms
	 * 			An array containing all existing PMs in the Iaas service.
	 * @throws VMManagementException
	 * @throws NetworkException
	 */
	private void reoptimize(PhysicalMachine[] pms) throws VMManagementException, NetworkException {
		reoptimizations++;	// increase the counter
		System.err.println("Starting reoptimization, round " + reoptimizations);
		
		// first, check if the number of active PMs can be minimized
		for(PhysicalMachine actualPm : pms) {
			// collect the migrations and only commit them, if every VM on this PM can be migrated
			Map<VirtualMachine, PhysicalMachine> migrations = new HashMap<VirtualMachine, PhysicalMachine>();
			
			for(VirtualMachine vm : actualPm.publicVms) {
				// make sure the actual VM could be migrated to another PM
				PhysicalMachine target = null;
				for(PhysicalMachine pm : pms) {
					if(isPmAbleToHostVm(pm, vm, mapping)) {
						pm.allocateResources(vm.getResourceAllocation().allocated, true, PhysicalMachine.migrationAllocLen);
						target = pm;
						break;
					}	
				}
				// mark this VM as migrateable and move on to the next one
				if(target != null)
					migrations.put(vm, target);
			}
			
			if(actualPm.publicVms.size() == migrations.size()) {
				// commit the tentative migrations only if every VM can be migrated
				for(VirtualMachine vm : migrations.keySet()) {
					actualPm.migrateVM(vm, migrations.get(vm));
				}
				
				// switch this PM off to save energy
				actualPm.switchoff(null);
			}
			else {
				migrations.clear();
			}
		}
		
		// check if a secure PM can take load from two (non-secure) PMs		
		boolean changed = true;
		while(changed) {
			changed = false;
			PhysicalMachine securePm = null;
			// take one secure PM which is actually not running
			for(PhysicalMachine pm : pms) {
				// take the first PM which fits the criteria
				if(pm.isSecure() && pm.getState().equals(State.OFF)) {
					securePm = pm;
					break;
				}
			}
			if(securePm != null) {
				PhysicalMachine chosenPm1 = null;
				PhysicalMachine chosenPm2 = null;
				
				// make sure the chosen PMs are non-secure
				for(PhysicalMachine pm1 : pms) {
					if(pm1.isSecure()) {
						continue;
					}
					for(PhysicalMachine pm2 : pms) {
						if(pm2.isSecure()) {
							continue;
						}
						if(pm1 == pm2) {
							continue;
						}						
						// compare the resources if there are two different non-secure PMs
						double occupiedTotalProcessingPowerPm1 = pm1.getCapacities().getTotalProcessingPower() - 
								pm1.availableCapacities.getTotalProcessingPower() - pm1.freeCapacities.getTotalProcessingPower();
						long occupiedMemoryPm1 = pm1.getCapacities().getRequiredMemory() - pm1.availableCapacities.getRequiredMemory() 
								- pm1.freeCapacities.getRequiredMemory();
						double occupiedTotalProcessingPowerPm2 = pm2.getCapacities().getTotalProcessingPower() - 
								pm2.availableCapacities.getTotalProcessingPower() - pm2.freeCapacities.getTotalProcessingPower();
						long occupiedMemoryPm2 = pm2.getCapacities().getRequiredMemory() - pm2.availableCapacities.getRequiredMemory() 
								- pm2.freeCapacities.getRequiredMemory();
						
						// if the securePM can take the load of the two PMs, then set both chosen PMs
						if(occupiedTotalProcessingPowerPm1 + occupiedTotalProcessingPowerPm2 <= 
								securePm.getCapacities().getTotalProcessingPower() && occupiedMemoryPm1 + occupiedMemoryPm2 <= 
								securePm.getCapacities().getRequiredMemory()) {
							chosenPm1 = pm1;
							chosenPm2 = pm2;
						}
					}
				}				
				if(chosenPm1 != null && chosenPm2 != null) {					
					Logger.getGlobal().info("Secure PM " + securePm.hashCode() + " takes load from PMs " + chosenPm1.hashCode() + 
							" and " + chosenPm2.hashCode());
					
					//start the secure PM and migrate all VMs of chosenPM1 and chosenPM2 to the secure one
					securePm.turnon();
					for(VirtualMachine vm : chosenPm1.listVMs()) {
						chosenPm1.migrateVM(vm, securePm);
					}
					for(VirtualMachine vm : chosenPm2.listVMs()) {
						chosenPm2.migrateVM(vm, securePm);
					}
					
					//switch of the empty PMs
					chosenPm1.switchoff(null);
					chosenPm2.switchoff(null);
					changed = true;
				}
				
			}
		}
		
		// check if the load of a secure PM can be moved to a non-secure PM
		boolean changed2 = true;
		while(changed2) {
			changed2 = false;
			PhysicalMachine securePM = null;
			PhysicalMachine unsecurePM = null;
			
			// get a secure PM which is actually running (which means, it hosts VMs)
			for(PhysicalMachine pm : pms) {
				if(pm.isSecure() && pm.isRunning()) {
					securePM = pm;
					break;
				}
			}
			
			// get a non-secure PM which is actually not running
			for(PhysicalMachine pm : pms) {				
				if(!pm.isSecure() && !pm.isRunning()) {
					unsecurePM = pm;
					break;
				}
			}
			
			// check if there are critical instances on the VMs, which means, they cannot run on a non-secure PM			
			boolean hostsCriticals = false;			
			for(VirtualMachine vm: securePM.publicVms) {				
				if(hostsCriticals) {
					break;
				}
				List<ComponentInstance> allInstancesOnVm = new ArrayList<ComponentInstance>();
				allInstancesOnVm.addAll(mapping.get(vm));
					
				//check if there are critical instances on this VM					
				for(ComponentInstance instance : allInstancesOnVm) {
					if(!instance.getType().getProvidedBy().equals("Provider") || instance.getTenants().size() > 1)
						hostsCriticals = true;
				}								
			}
			
			// if there is no possible injury of the safety, migrate all VMs from the secure PM to the non-secure one
			if(!hostsCriticals) {
				unsecurePM.turnon();
				securePM.switchoff(unsecurePM);
				changed2 = true;
			}			
		}
		System.err.println("Finished reoptimization, round " + reoptimizations);
	}
	
	
	
}
