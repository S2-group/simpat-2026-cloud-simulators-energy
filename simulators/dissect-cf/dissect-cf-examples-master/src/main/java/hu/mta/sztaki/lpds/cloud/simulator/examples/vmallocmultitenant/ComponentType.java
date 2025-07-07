package hu.mta.sztaki.lpds.cloud.simulator.examples.vmallocmultitenant;

import java.util.HashSet;
import java.util.logging.Logger;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;

	/**
	 * This class defines the type of a component, so there can be instances created of this type.
	 * It is important to mention that the String 'providedBy' has to be filled with 'Provider'
	 * if this type shall not define a custom implementation.
	 * 
	 * This class refers to the ComponentType out of the paper "Optimized Cloud 
	 * Deployment of Multi-tenant Software Considering Data Protection Concerns" 
	 * by Zoltan Adam Mann and Andreas Metzger, published in CCGrid 2017.
	 * 
	 * @author Rene Ponto
	 */

public class ComponentType {
	
	/** The name of this ComponentType. */
	private String name;
	
	/** This string determines if this type is custom or not. If it is
	 * 'Provider', it is no custom implementation. */
	private String providedBy;
	
	/** The base constraints for hosting an instance of this type. */
	private ResourceConstraints cons;
	
	/** All actually existing instances of this type. */
	private HashSet<ComponentInstance> instances;
	
	/** The counter for creating more instances of this type. */
	private int instanceCounter;
	
	/** Determines if this ComponentType supports sgx. */
	private boolean isSgxSupported;

	/**
	 * @param name
	 * 			The name of this type.
	 * @param providedBy
	 * 			The provider of this type.
	 * @param cons
	 * 			The base ResourceConstraints.
	 * @param sgxSupport
	 * 			Determines the support of sgx.
	 */
	public ComponentType(String name, String providedBy, ResourceConstraints cons, boolean sgxSupport) {
		this.name = name;
		this.cons = cons;
		this.providedBy = providedBy;
		this.isSgxSupported = sgxSupport;
		instanceCounter = 0;		
		
		instances = new HashSet<ComponentInstance>();
	}
	
	/**
	 * 
	 * @return Name of this type.
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * 
	 * @return The Provider of this type. If not custom, it is 'Provider'.
	 */
	public String getProvidedBy() {
		return providedBy;
	}
	
	/**
	 * 
	 * @return The necessary resources to host this type.
	 */
	public ResourceConstraints getResources() {
		return cons;
	}
	
	/**
	 * 
	 * @return The set of all existing ComponentInstances of this type.
	 */
	public HashSet<ComponentInstance> getInstances() {
		return instances;
	}
	
	/**
	 * 
	 * @return true if this type supports sgx.
	 */
	public boolean isSgxSupported() {
		return isSgxSupported;
	}
	
	/**
	 * Creates a new ComponentInstance of this ComponentType and adds it to the set of instances.
	 * Increases also the instanceCounter.
	 * @param crit
	 * 				Shall the instance host critical data?
	 * @return The created ComponentInstance.
	 */
	public ComponentInstance createInstance(boolean crit) {
		ComponentInstance inst;
		String instanceName = name + Integer.toString(instanceCounter);
		inst = new ComponentInstance(instanceName, crit, this);
		instances.add(inst);
		instanceCounter ++;
		return inst;
	}

	/**
	 * Removes the given ComponentInstance from the set.
	 * @param ci
	 * 			The ComponentInstance which shall be removed.
	 */
	public void removeInstance(ComponentInstance ci) {
		Logger.getGlobal().info("Removing an instance of type " + name);
		instances.remove(ci);
	}

}
