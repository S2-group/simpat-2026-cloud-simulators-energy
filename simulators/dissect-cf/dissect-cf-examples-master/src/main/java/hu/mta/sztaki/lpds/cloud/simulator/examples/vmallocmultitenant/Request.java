package hu.mta.sztaki.lpds.cloud.simulator.examples.vmallocmultitenant;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;

/**
 * This class represents a request of a ComponentType. The variables of a request
 * are necessary to find/create a matching component instance.
 * 
 * @author Rene Ponto	 
 */
public class Request {
	
	private String tenant;
	private ComponentType ctype;
	private ResourceConstraints cons;
	private boolean crit, custom, supportsSecureEnclaves;
	private double startTime, duration;
	private Type event;
	
	private ComponentInstance host;


	/**
	 * Defines a Request for a ComponentInstance from a tenant.
	 * @param tenant
	 * 			The name of the requesting tenant.
	 * @param type
	 * 			The requested component type.
	 * @param cons
	 * 			The requested resources.
	 * @param crit
	 * 			Determines the criticality of this request.
	 * @param custom
	 * 			Determines the usage of a custom component type.
	 * @param supportsSecureEnclaves
	 * 			Determines the support of secure enclaves.
	 * @param startTime
	 * 			The time where this request occur.
	 * @param duration
	 * 			The duration of this request.
	 * @param event
	 * 			The type of this request.
	 */
	public Request(String tenant, ComponentType ctype, ResourceConstraints cons, boolean crit, boolean custom, boolean supportsSecureEnclaves, 
			double startTime, double duration, Type event) {
		
		this.tenant = tenant;
		this.ctype = ctype;
		this.cons = cons;
		this.crit = crit;
		this.custom = custom;
		this.supportsSecureEnclaves = supportsSecureEnclaves;
		this.startTime = startTime;
		this.duration = duration;
		this.event = event;
		host = null;
	}
	
	/**
	 * 
	 * @return The event of this request.
	 */
	public Type getType() {
		return event;
	}
	
	public static enum Type {
		/**
		 * This request is a new incoming request of a component type.
		 */
		NEW_REQUEST,
		
		/**
		 * This request shall terminate an active request of an component type.
		 */
		TERMINATE_REQUEST,
		
		/**
		 * This request shall start the consolidator.
		 */
		REOPTIMIZATION
	}
	
	/**
	 * 
	 * @return The name of the tenant.
	 */
	public ComponentInstance getHost() {
		return host;
	}
	
	/**
	 * 
	 * @param newHost
	 */
	public void setHost(ComponentInstance newHost) {
		this.host = newHost;
	}
	
	/**
	 * 
	 * @return The name of the tenant.
	 */
	public String getTenant() {
		return tenant;
	}
	
	/**
	 * 
	 * @return The requested ComponentType.
	 */
	public ComponentType getComponentType() {
		return ctype;
	}
	
	/**
	 * 
	 * @return The ResourceVector with the requested resources.
	 */
	public ResourceConstraints getResources() {
		return cons;
	}
	
	/**
	 * 
	 * @return True if critical data is stored.
	 */
	public boolean isCrit() {
		return crit;
	}
	
	/**
	 * 
	 * @return True if a custom component type shall be used.
	 */
	public boolean isCustom() {
		return custom;
	}
	
	/**
	 * 
	 * @return True if secure enclaves are supported.
	 */
	public boolean supportsSecureEnclaves() {
		return supportsSecureEnclaves;
	}
	
	/**
	 * 
	 * @return The duration of fulfilling this Request.
	 */
	public double getDuration() {
		return duration;
	}
	
	/**
	 * 
	 * @return The time where the request shall occur.
	 */
	public double getStartTime() {
		return startTime;
	}
}
