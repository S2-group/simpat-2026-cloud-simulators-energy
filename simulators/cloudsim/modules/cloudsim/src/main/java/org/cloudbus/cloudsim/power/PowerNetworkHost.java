package org.cloudbus.cloudsim.power;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.lists.VmList;
import org.cloudbus.cloudsim.network.datacenter.HostPacket;
import org.cloudbus.cloudsim.network.datacenter.NetworkDatacenter;
import org.cloudbus.cloudsim.network.datacenter.NetworkInterfaceCard;
import org.cloudbus.cloudsim.network.datacenter.NetworkPacket;
import org.cloudbus.cloudsim.network.datacenter.PowerSwitch;
import org.cloudbus.cloudsim.network.datacenter.Switch;
import org.cloudbus.cloudsim.provisioners.BwProvisioner;
import org.cloudbus.cloudsim.provisioners.RamProvisioner;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.HostDynamicWorkload;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.VmScheduler;
import org.cloudbus.cloudsim.core.CloudActionTags;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.GuestEntity;
import org.cloudbus.cloudsim.core.NetworkedEntity;
import org.cloudbus.cloudsim.core.PowerHostEntity;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.power.models.PowerModel;
import org.cloudbus.cloudsim.provisioners.BwProvisioner;
import org.cloudbus.cloudsim.provisioners.RamProvisioner;


public class PowerNetworkHost extends PowerHost implements NetworkedEntity {
    /** Edge switch to which the Host is connected. */
	private PowerSwitch sw;

	/** cloudlet -> nic
	 * @TODO: Ideally the nic shouldn't skip the guest entity; to be fixed
	 */
	private Map<Integer, NetworkInterfaceCard> nics;
	private Map<Integer, List<NetworkPacket>> sendPktExternally;

    /**
	 * Instantiates a new PowerHost.
	 *
	 * @param id the id of the host
	 * @param ramProvisioner the ram provisioner
	 * @param bwProvisioner the bw provisioner
	 * @param storage the storage capacity
	 * @param peList the host's PEs list
	 * @param vmScheduler the vm scheduler
	 * @param powerModel the power consumption model
	 */
	public PowerNetworkHost(
    int id,
    RamProvisioner ramProvisioner,
    BwProvisioner bwProvisioner,
    long storage,
    List<? extends Pe> peList,
    VmScheduler vmScheduler,
    PowerModel powerModel) {
        super(id, ramProvisioner, bwProvisioner, storage, peList, vmScheduler, powerModel);

        // From NetworkHost:
        nics = new HashMap<>();
        sendPktExternally = new HashMap<>();
    }

    @Override
	public double updateCloudletsProcessing(double currentTime) {
		double smallerTime = super.updateCloudletsProcessing(currentTime);
		setPreviousUtilizationMips(getUtilizationMips());
		setUtilizationMips(0);
		double hostTotalRequestedMips = 0;

        sendPackets(); // from NetworkHost

		for (GuestEntity vm : getGuestList()) {
			getGuestScheduler().deallocatePesForGuest(vm);
		}

		for (GuestEntity vm : getGuestList()) {
			getGuestScheduler().allocatePesForGuest(vm, vm.getCurrentRequestedMips());
		}

		for (GuestEntity vm : getGuestList()) {
			double totalRequestedMips = vm.getCurrentRequestedTotalMips();
			double totalAllocatedMips = getGuestScheduler().getTotalAllocatedMipsForGuest(vm);

			if (!Log.isDisabled()) {
				Log.printlnConcat(CloudSim.clock(),
						": [Host #", getId(), "] Total allocated MIPS for VM #", vm.getId()
								, " (Host #", vm.getHost().getId()
								, ") is ", totalAllocatedMips,", was requested ", totalRequestedMips
								, " out of total ",vm.getMips(), " (", totalRequestedMips / vm.getMips() * 100,"%.2f%%)");

				List<Pe> pes = getGuestScheduler().getPesAllocatedForGuest(vm);
				StringBuilder pesString = new StringBuilder();
				for (Pe pe : pes) {
					pesString.append(String.format(" PE #" + pe.getId() + ": %.2f.", pe.getPeProvisioner()
							.getTotalAllocatedMipsForGuest(vm)));
				}
				Log.printlnConcat(CloudSim.clock(),
						": [Host #", getId(), "] MIPS for VM #", vm.getId(), " by PEs ("
								, getNumberOfPes(), " * ", getGuestScheduler().getPeCapacity() + ")."
								, pesString);
			}

			if (getGuestsMigratingIn().contains(vm)) {
				Log.printlnConcat(CloudSim.clock(), ": [Host #", getId(), "] VM #" + vm.getId()
						, " is being migrated to Host #", getId());
			} else {
				if (totalAllocatedMips + 0.1 < totalRequestedMips) {
					Log.printlnConcat(CloudSim.clock(), ": [Host #", getId(), "] Under allocated MIPS for VM #", vm.getId()
							, ": ", totalRequestedMips - totalAllocatedMips);
				}

				vm.addStateHistoryEntry(
						currentTime,
						totalAllocatedMips,
						totalRequestedMips,
						(vm.isInMigration() && !getGuestsMigratingIn().contains(vm)));

				if (vm.isInMigration()) {
					Log.printlnConcat(CloudSim.clock(),
							": [Host #", getId(), "] VM #", vm.getId(), " is in migration");
					totalAllocatedMips /= 0.9; // performance degradation due to migration - 10%
				}
			}

			setUtilizationMips(getUtilizationMips() + totalAllocatedMips);
			hostTotalRequestedMips += totalRequestedMips;
		}

		addStateHistoryEntry(
				currentTime,
				getUtilizationMips(),
				hostTotalRequestedMips,
				(getUtilizationMips() > 0));

		return smallerTime;
	}

    /** From NetworkHost
	 * Sends packets checks whether a packet belongs to a local VM or to a
         * VM hosted on other machine.
	 */
    public void sendPackets() {
		boolean flag = false;

		for (NetworkInterfaceCard nic : nics.values()) {
			for (HostPacket hpkt : nic.getPktsToSend()) {
				GuestEntity receiver = VmList.getById(this.getGuestList(), hpkt.getReceiverGuestId());
				if (receiver != null) { // send locally to Vm, no network delay
					flag = true;
					hpkt.setRecvTime(CloudSim.clock());

					// insert the packet in received list on destination guest
					nics.get(hpkt.getReceiverCloudletId()).getReceivedPkts().add(hpkt);
				} else {
					sendPktExternally.computeIfAbsent(hpkt.getSenderGuestId(), k -> new ArrayList<>())
									 .add(new NetworkPacket(getId(), hpkt));
				}
			}
			nic.getPktsToSend().clear();
		}


		// send to edge switch, since destination guest is hosted on another host
		for (Integer guestId : sendPktExternally.keySet()) {
			GuestEntity sender = VmList.getById(this.getGuestList(), guestId);
			if (sender == null) {
				throw new RuntimeException("senderVm not found! is it nested?");
			}

			for (NetworkPacket npkt : sendPktExternally.get(guestId)) {
				// Assumption: no overprovisioning of guest's bandwidth
				double avband = (double) sender.getBw() / sendPktExternally.get(guestId).size();
				double delay = (8 * npkt.getPkt().getData() / avband) + npkt.getPkt().getAccumulatedVirtualizationOverhead();

				((NetworkDatacenter) getDatacenter()).totalDataTransfer += npkt.getPkt().getData();

				// send to switch with delay
				CloudSim.send(getDatacenter().getId(), sw.getId(), delay, CloudActionTags.NETWORK_PKT_UP, npkt);
			}
		}
		sendPktExternally.clear();

		if (flag) {
			for (GuestEntity guest : super.getGuestList()) {
				guest.updateCloudletsProcessing(CloudSim.clock(), getGuestScheduler().getAllocatedMipsForGuest(guest));
			}
		}
    }

    public Map<Integer, NetworkInterfaceCard> getNics() { // from NetworkHost
		return nics;
	}

	public PowerSwitch getPowerSwitch() { // from NetworkHost
		return sw;
	}

    public Switch getSwitch() { return null; }

    public void setSwitch(Switch sw) {}

	public void setSwitch(PowerSwitch sw) { // from NetworkHost
		this.sw = sw;
	}
}
