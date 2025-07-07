/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim;

import org.cloudbus.cloudsim.core.GuestEntity;
import org.cloudbus.cloudsim.core.HostEntity;

import java.util.List;

/**
 * The most barebone implementation of a VmAllocationPolicy.
 * Corresponds to VmAllocationWithSelectionPolicy with SelectionPolicyFirstFit.
 *
 * @author Remo Andreoli
 * @since CloudSim Toolkit 7.0
 */
public class VmAllocationPolicySimpler extends VmAllocationPolicy {
	/**
	 * Creates a new VmAllocationPolicy object.
	 *
	 * @param list Machines available in a {@link Datacenter}
	 * @pre $none
	 * @post $none
	 */
	public VmAllocationPolicySimpler(List<? extends HostEntity> list) {
		super(list);
	}

	@Override
	public HostEntity findHostForGuest(GuestEntity guest) {
        // // // Log.printlnConcat("DEBUG (findHostForGuest Simpler) ", getHostList());
		for (HostEntity host : getHostList()) {
            // Log.printlnConcat("DEBUG Simpler going to isSuitableForGuest ", host);
			if (host.isSuitableForGuest(guest)) {
                // // // Log.printlnConcat("DEBUG simpler found host ", host);
				return host;
			}
		}
		return null;
	}
}
