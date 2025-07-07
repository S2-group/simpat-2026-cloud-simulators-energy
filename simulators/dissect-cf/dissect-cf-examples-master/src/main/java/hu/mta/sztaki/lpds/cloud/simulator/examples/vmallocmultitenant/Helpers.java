package hu.mta.sztaki.lpds.cloud.simulator.examples.vmallocmultitenant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import hu.mta.sztaki.lpds.cloud.simulator.examples.vmallocmultitenant.Request;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;

	/**
	 * The intention of this Interface is to make the three methods 
	 * 
	 * --'isInstanceAbleToHostTenant(ComponentInstance i, Request r, boolean crit)'
	 * 
	 * --'isVmAbleToHostInstance(VirtualMachine vm, ComponentInstance i, 
	 * HashMap<VirtualMachine, ArrayList<ComponentInstance>> mapping)'
	 * 
	 * --'isPmAbleToHostVm(PhysicalMachine pm, VirtualMachine vm, HashMap<VirtualMachine, 
	 * ArrayList<ComponentInstance>> mapping)'
	 * 
	 * less redundant in the MultiTenantConsolidator and the MultiTenantScheduler, because by 
	 * implementing them in this way we only need one implementation for both classes.
	 * 
	 * @author Rene Ponto
	 * 
	 */

interface Helpers {

	/**
	 * Checks if the given ComponentInstance is able to serve a given tenant. In order to determine that,
	 * the criticality is checked and the capacity of the PhysicalMachine which hosts the VirtualMachine with
	 * the ComponentInstance.
	 * 
	 * Should be finished.
	 * @param i
	 * 			The ComponentInstance which shall serve the tenant.
	 * @param r
	 * 			The Request of the tenant.
	 * @param crit
	 * 			Is the hosted data critical?
	 * @return true if there are enough resources on the hostPm to host the additional resources of the
	 * 		   ComponentInstance on its bounded Vm and both the instance and the to hosted data are not critical.
	 */
	default boolean isInstanceAbleToHostTenant(ComponentInstance i, Request r, boolean crit) {
		
		PhysicalMachine host = i.getVm().getResourceAllocation().getHost();		
		if(!(host.availableCapacities.getTotalProcessingPower() + r.getResources().getTotalProcessingPower() 
				<= host.getCapacities().getTotalProcessingPower()) && !(host.availableCapacities.getRequiredMemory() + 
				r.getResources().getRequiredMemory() <= host.getCapacities().getRequiredMemory())) {
			return false;
		}
		else {
			if(i.mayBeUsedBy(r.getTenant(), crit)) {
				return true;
			}
			
			return false;
		}
	}
	
	/**
	 * Firstly it has to be checked that the load of the PMhosting the given VM does not
	 * grow to large becouse of the new component instance. If the new component instance is 
	 * a critical component dedicated to tenant x, then there must be no custom components 
	 * created by another tenant in the VM and vice versa, if there is a critical component 
	 * instance in the VM, then the new component instance must not be a custom component 
	 * instance of a different tenant.
	 * 
	 * Should be finished. 
	 * @param vm
	 * 			The VirtualMachine which is going to be checked.
	 * @param i
	 * 			The ComponentInstance which shall be hosted.
	 * @param mapping
	 * 			The actual mapping of VMs to ComponentInstances.
	 * @return
	 */
	default boolean isVmAbleToHostInstance(VirtualMachine vm, ComponentInstance i, HashMap<VirtualMachine, ArrayList<ComponentInstance>> mapping) {
		
		//At first check the load of the PM which hosts the given VM. If there is not
		//enough capacity to host the given ComponentInstance, return false.
		PhysicalMachine host = vm.getResourceAllocation().getHost();		
		if(!(host.availableCapacities.getTotalProcessingPower() + i.getResources().getTotalProcessingPower() 
				<= host.getCapacities().getTotalProcessingPower()) && !(host.availableCapacities.getRequiredMemory() + 
				i.getResources().getRequiredMemory() <= host.getCapacities().getRequiredMemory())) {
			return false;
		}
		
		List<ComponentInstance> allInstancesOnVm = new ArrayList<ComponentInstance>();
		allInstancesOnVm.addAll(mapping.get(vm));
		//if the instance is critical, there must not be a custom instance
		if(i.isCritical()) {					
			for(ComponentInstance instance : allInstancesOnVm) {
				if(!instance.getType().getProvidedBy().equals("Provider") || instance.getTenants().size() > 1)
					return false;
			}
		}
		//if there are critical instances on the VM, this one must not be custom
		else {
			boolean critOnVm = false;
			String tenant = "";
			for(ComponentInstance instance : allInstancesOnVm) {
				if(instance.isCritical()) {
					critOnVm = true;
					tenant = instance.getTenants().get(0);
					break;
				}
			}
			if(critOnVm && tenant != "") {
				if(!i.getType().getProvidedBy().equals("Provider") && i.getTenants().get(0) != tenant) {
					return false;
				}
			}
			else
				return false;
		}		
		return true;
	}
	
	/**
	 * Firstly it has to be ensured that the aggregate size of the VMs remains below the capacity 
	 * of the PM. Furthermore, it is checked whether there is a component instance in the VM and 
	 * another in the PM or vice versa that would violate the data protection constraint, taking 
	 * into account the criticality and custom nature of the components, as well as the security 
	 * capabilities of the PM and whether the critical component could take advantage of such 
	 * capabilities.
	 * 
	 * Should be finished.
	 * @param pm
	 * 			The PhysicalMachine which is going to be checked.
	 * @param vm
	 * 			The VirtualMachine which shall be hosted.
	 * @param mapping
	 * 			The actual mapping of VMs to ComponentInstances.
	 * @return
	 */
	default boolean isPmAbleToHostVm(PhysicalMachine pm, VirtualMachine vm, HashMap<VirtualMachine, ArrayList<ComponentInstance>> mapping) {
		
		//ensures that the aggregate size of the VMs remains below the capacity of the PM
		if(!(pm.availableCapacities.getTotalProcessingPower() + vm.getResourceAllocation().allocated.getTotalProcessingPower() 
				<= pm.getCapacities().getTotalProcessingPower()) && !(pm.availableCapacities.getRequiredMemory() + 
				vm.getResourceAllocation().allocated.getRequiredMemory() <= pm.getCapacities().getRequiredMemory())) {
			return false;
		}
		
		//it is checked whether there is a component instance in the VM and another in the PM 
		//or vice versa that would violate the data protection constraint		
		List<ComponentInstance> allInstancesOnVm = new ArrayList<ComponentInstance>();
		allInstancesOnVm.addAll(mapping.get(vm));
		
		//check if there are critical instances on this VM
		boolean hostsCriticals = false;
		for(ComponentInstance instance : allInstancesOnVm) {
			if(!instance.getType().getProvidedBy().equals("Provider") || instance.getTenants().size() > 1)
				hostsCriticals = true;
		}		
		
		//check if the PM supports secure enclaves, so there can be critical instances of different hosts be hosted
		if(hostsCriticals) {
			if(!pm.isSecure())
				return false;
			
			boolean cannotHost = false;
			for(ComponentInstance instance : allInstancesOnVm) {
				if(!instance.getType().isSgxSupported())
					cannotHost = true;
			}			
			return !cannotHost;	
		}
		else {
			return true;
		}
	}
	
}
