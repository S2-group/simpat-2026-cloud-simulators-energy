/*
 * Title: CloudSim Toolkit Description: CloudSim (Cloud Simulation) Toolkit for Modeling and
 * Simulation of Clouds Licence: GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2024, The University of Melbourne, Australia
 */

 package org.cloudbus.cloudsim.container.core;

 import org.cloudbus.cloudsim.*;
 import org.cloudbus.cloudsim.VmAllocationPolicy.GuestMapping;
 import org.cloudbus.cloudsim.container.utils.CustomCSVWriter;
 import org.cloudbus.cloudsim.network.datacenter.*;
import org.cloudbus.cloudsim.core.CloudActionTags;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.GuestEntity;
import org.cloudbus.cloudsim.core.HostEntity;
import org.cloudbus.cloudsim.core.NetworkedEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.core.VirtualEntity;
import org.cloudbus.cloudsim.core.predicates.PredicateType;
 import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.PowerNetworkHost;

import com.google.common.graph.Network;

import java.io.IOException;
 import java.util.ArrayList;
 import java.util.HashMap;
 import java.util.List;
 import java.util.Map;
 import java.util.Map.Entry;

 /**
  * Created by sareh on 20/07/15.
  * Modified by Remo Andreoli (Feb 2024)
  */
 public class PowerContainerNetworkDatacenter extends PowerContainerDatacenter {

    DatacenterCharacteristics characteristics;

    /**
	 * A map between VMs and Switches, where each key
	 * is a VM id and the corresponding value is the id of the switch where the VM is connected to.
	 */
	public Map<Integer, Integer> VmToSwitchid;

	/**
	 * A map between hosts and Switches, where each key
	 * is a host id and the corresponding value is the id of the switch where the host is connected to.
	 */
	public Map<Integer, Integer> HostToSwitchid;

	/**
	 * A map of datacenter switches where each key is a switch id
	 * and the corresponding value is the switch itself.
	 */
	private final Map<Integer, PowerSwitch> SwitchList;

	/**
	 * A map between VMs and Hosts, where each key
	 * is a VM id and the corresponding value is the id of the host where the VM is placed.
	 */
	public Map<Integer, Integer> VmtoHostlist;

	/** Total data transmitted through the network of this datacenter (in bytes) */
	public double totalDataTransfer = 0;


     /**
      * Instantiates a new datacenter.
      *
      * @param name               the name
      * @param characteristics    the res config
      * @param schedulingInterval the scheduling interval
      *                           //         * @param utilizationBound the utilization bound
      * @param vmAllocationPolicy the vm provisioner
      * @param storageList        the storage list
      * @throws Exception the exception
      */
     public PowerContainerNetworkDatacenter(
             String name,
             DatacenterCharacteristics characteristics,
             VmAllocationPolicy vmAllocationPolicy,
             VmAllocationPolicy containerAllocationPolicy,
             List<Storage> storageList,
             double schedulingInterval, String experimentName, String logAddress) throws Exception {
        super(name, characteristics, vmAllocationPolicy, containerAllocationPolicy, storageList, schedulingInterval, experimentName, logAddress);
        VmToSwitchid = new HashMap<>(); // from NetworkDatacenter
        HostToSwitchid = new HashMap<>(); // from NetworkDatacenter
        VmtoHostlist = new HashMap<>(); // from NetworkDatacenter
        SwitchList = new HashMap<>(); // from NetworkDatacenter
    }

    public Map<Integer, PowerSwitch> getSwitchList() { return SwitchList; }

    // public NetworkDatacenter toNetworkDatacenter() throws Exception {
    //     NetworkDatacenter networkDC = new NetworkDatacenter(
    //         getName(), characteristics, getVmAllocationPolicy(), getStorageList(), getSchedulingInterval(),
    //         VmToSwitchid, HostToSwitchid, VmtoHostlist, SwitchList
    //     );

    //     return networkDC;
    // }

    @Override
	protected void processVmCreate(SimEvent ev, boolean ack) { // from NetworkDatacenter
		super.processVmCreate(ev, ack);
		GuestEntity guest = (GuestEntity) ev.getData();
		HostEntity host = guest.getHost();

		if (host != null) {
			// very ugly, but no other way to support nested virtualization with the current network routing logic
			while (host instanceof VirtualEntity vm) {
				host = vm.getHost();
			}

			VmToSwitchid.put(guest.getId(), ((PowerNetworkHost) host).getPowerSwitch().getId());
			VmtoHostlist.put(guest.getId(), host.getId());
		}
	}

    @Override
	protected void processCloudletSubmit(SimEvent ev, boolean ack) { // from NetworkDatacenter
		super.processCloudletSubmit(ev, ack);
        setCloudletSubmitted(CloudSim.clock()); // from PowerContainerDatacenter

		NetworkCloudlet ncl = (NetworkCloudlet) ev.getData();

		int userId = ncl.getUserId();
		int vmId = ncl.getGuestId();
		NetworkedEntity host = (NetworkedEntity) getVmAllocationPolicy().getHost(vmId, userId);

		host.getNics().put(ncl.getCloudletId(), ncl.getNic());
	}

    /** From NetworkDatacenter
	 * Gets a map of all EdgeSwitches in the Datacenter network.
         * One can design similar functions for other type of switches.
	 *
         * @return a EdgeSwitches map, where each key is the switch id
         * and each value it the switch itself.
	 */
	public Map<Integer, PowerSwitch> getEdgeSwitch() { // from NetworkDatacenter
		Map<Integer, PowerSwitch> edgeswitch = new HashMap<>();
		for (Entry<Integer, PowerSwitch> es : SwitchList.entrySet()) {
			if (es.getValue().level == PowerSwitch.SwitchLevel.EDGE_LEVEL) {
				edgeswitch.put(es.getKey(), es.getValue());
			}
		}
		return edgeswitch;

	}

    public void registerSwitch(PowerSwitch sw) { // from NetworkDatacenter
		if (!getSwitchList().containsKey(sw.getId())) {
			getSwitchList().put(sw.getId(), sw);
		}
	}

	public void attachSwitchToHost(PowerSwitch sw, PowerNetworkHost netHost) { // from NetworkDatacenter
		if (!getSwitchList().containsKey(sw.getId()) || !getHostList().contains(netHost)) {
			throw new IllegalArgumentException("Switch or Host are not part of this Datacenter");
		}

		if (sw.level != PowerSwitch.SwitchLevel.EDGE_LEVEL) {
			throw new IllegalArgumentException("Switch is not at the edge level");
    }

		sw.hostList.put(netHost.getId(), netHost);
		sendNow(sw.getId(), CloudActionTags.NETWORK_ATTACH_HOST, netHost);
		HostToSwitchid.put(netHost.getId(), sw.getId());
		netHost.setSwitch(sw);
	}

	public void attachSwitchToSwitch(PowerSwitch sw1, PowerSwitch sw2) { // from NetworkDatacenter
		if (!getSwitchList().containsKey(sw1.getId()) || !getSwitchList().containsKey(sw2.getId())) {
			throw new IllegalArgumentException("One or both switches are not part of this Datacenter");
		}

		// Switches are already connected
		if (sw1.downlinkSwitches.contains(sw2) || sw1.uplinkSwitches.contains(sw2)) {
			return;
		}

		if (sw1.level == PowerSwitch.SwitchLevel.EDGE_LEVEL) {
			if (sw2.level != PowerSwitch.SwitchLevel.AGGR_LEVEL) {
				throw new IllegalArgumentException("Edge switch can only be attached to Aggregate switch");
			} else {
				sw1.uplinkSwitches.add(sw2);
				sw2.downlinkSwitches.add(sw1);
			}
		} else if (sw1.level == PowerSwitch.SwitchLevel.AGGR_LEVEL) {
			if (sw2.level == PowerSwitch.SwitchLevel.ROOT_LEVEL) {
				sw1.uplinkSwitches.add(sw2);
				sw2.downlinkSwitches.add(sw1);
			} else if (sw2.level == PowerSwitch.SwitchLevel.EDGE_LEVEL) {
				sw1.downlinkSwitches.add(sw2);
				sw2.uplinkSwitches.add(sw1);
			} else {
				throw new IllegalArgumentException("Cannot attach to switch of same level");
			}
		} else { // root-level sw1
			if (sw2.level != PowerSwitch.SwitchLevel.AGGR_LEVEL) {
				throw new IllegalArgumentException("Root switch can only be attached to Aggregate switch");
			} else {
				sw1.downlinkSwitches.add(sw2);
				sw2.uplinkSwitches.add(sw1);
			}
		}
	}
 }
