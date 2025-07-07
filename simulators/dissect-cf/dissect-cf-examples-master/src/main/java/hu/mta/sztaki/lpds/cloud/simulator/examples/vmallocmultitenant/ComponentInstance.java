package hu.mta.sztaki.lpds.cloud.simulator.examples.vmallocmultitenant;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

	/**
	 * Instance of a ComponentType. It is hosted on a specific VM as a 
	 * ComputeTask.
	 * 
	 * This class refers to the ComponentInstance out of the paper "Optimized Cloud 
	 * Deployment of Multi-tenant Software Considering Data Protection Concerns" 
	 * by Zoltan Adam Mann and Andreas Metzger, published in CCGrid 2017.
	 * 
	 * TODO for improvement:
	 * - Handle the ConsumptionEvent, at the moment it might be wrong.
	 * 
	 * @author Rene Ponto
	 */

public class ComponentInstance {
	
	/** The name of this instance. */
	private String name;
	
	/** Determines if this instance hosts critical data. */
	private boolean crit;
	
	/** The hosting VM. */
	private VirtualMachine vm;
	
	/** Determines the type of this instance. */
	private ComponentType type;
	
	/** All existing requests of this instance. */
    private HashSet<Request> requests;
    
    /** TODO */
    private ConsumptionEventAdapter e;

    /** Represents the actual resource consumption. */
	private ResourceConsumption consumption;
	
	/** The base resource need because of the type of this instance. */
	private AlterableResourceConstraints constraints;

	/**
	 * The constructor.
	 * @param name
	 * 			The name of this instance.
	 * @param crit
	 * 			Determines if the instance is hosting critical data.
	 * @param componentType
	 * 			The underlying ComponentType.
	 * @param custom
	 * 			Determines if this instance is a custom implementation.
	 */
	public ComponentInstance(String name, boolean crit, ComponentType componentType) {
		
		requests = new HashSet<Request>();
		
		this.name = name;
		this.vm = null;
		this.crit = crit;
		this.type = componentType;
		constraints = new AlterableResourceConstraints(type.getResources());
		
		// TODO
		e = new ConsumptionEventAdapter();
	}
	
	/**
	 * 
	 * @return All existing requests.
	 */
	public HashSet<Request> getRequests() {
		return requests;
	}
	
	/** 
	 * 
	 * @return The base constraints.
	 */
	public ResourceConstraints getResources() {
		return constraints;
	}
	
	/**
	 * 
	 * @return The hosting VM of this instance.
	 */
	public VirtualMachine getVm() {
		return vm;
	}
	
	/**
	 * Sets the VM and adjusts the resource consumption.
	 * @param vm
	 */
	public void setVm(VirtualMachine vm) {
		this.vm = vm;
		adjustTask();
	}
	
	/**
	 * 
	 * @return The name of this instance for identifying.
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * 
	 * @return The type of this instance.
	 */
	public ComponentType getType() {
		return type;
	}
	
	/**
	 * 
	 * @return True if this instance hosts critical data.
	 */
	public boolean isCritical() {
		return crit;
	}	
	
	/**
	 * 
	 * @return The actual consumption regarding to the tenants of this instance.
	 */
	public ResourceConsumption getConsumption() {
		return consumption;
	}
	
	/**
	 * Cancels the previous declared resource consumption and creates a new task with the actual consumption.
	 * Gets called after a new Request arrives.
	 */
	private void adjustTask() {
		this.consumption.cancel();
		try {
			consumption = vm.newComputeTask(constraints.getTotalProcessingPower(), ResourceConsumption.unlimitedProcessing, e);	
		} catch (NetworkException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Adds this Request to the set with the other ones. If a host VM exists, the resource consumption gets adjusted.
	 * @param r
	 * 			The Request which shall be added.
	 */
	public void addRequest(Request r) {
		requests.add(r);
		r.setHost(this);
		constraints.add(r.getResources());
		if(vm != null){
			adjustTask();
		}
	}
	
	/**
	 * Remove this Request from its set and the instance itself if it was the last Request. If a host VM exists, 
	 * the resource consumption gets adjusted.
	 * @param r
	 * 			The Request which shall be removed.
	 */
	public void removeRequest(Request r) {
		requests.remove(r);
		r.setHost(null);
		constraints.subtract(r.getResources());
		if(vm != null) {
			adjustTask();
		}
		if(requests.isEmpty())
			type.removeInstance(this);
	}

	/**
	 * Analyzes this instance and checks, if it can be used by a given tenant. This is the case,
	 * if it and the data of the tenant (which shall be hosted) are not critical or if it only serves 
	 * one tenant at all.
	 * @param tenant
	 * 				The tenant who wants to use this instance.
	 * @param critForTenant
	 * 				Defines if the data is critcal for the tenant.
	 * @return true if it and the data of the tenant (which shall be hosted) are not critical or if it only serves 
	 * 			one tenant at all, false otherwise.
	 */
	public boolean mayBeUsedBy(String tenant, boolean critForTenant) {
		if((!crit)&&(!critForTenant))
			return true;
		
		boolean onlyServesTenant=true;
		for(Request r : requests) {
			if(r.getTenant() != tenant)
				onlyServesTenant = false;
		}
		return onlyServesTenant;
	}

	/**
	 * @return All tenants ordered inside an ArrayList.
	 */
	public List<String> getTenants() {
		List<String> result = new ArrayList<String>();
		for(Request r : requests) {
			result.add(r.getTenant());
		}
		return result;
	}

	/**
	 * @return All tenants inside one String.
	 */
	public String getTenantsToString() {
		String s = "";
		for(Request r : requests) {
			if(s != "")
				s += ", ";
			s = s + r.getTenant();
		}
		return s;
	}

}
