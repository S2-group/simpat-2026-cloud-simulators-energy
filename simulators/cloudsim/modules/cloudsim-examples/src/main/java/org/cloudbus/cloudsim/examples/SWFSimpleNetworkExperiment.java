/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation
 *               of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.examples;

import java.lang.Math;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.VmAllocationPolicySimpler;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.container.core.PowerContainer;
import org.cloudbus.cloudsim.container.core.PowerContainerDatacenter;
import org.cloudbus.cloudsim.container.core.PowerContainerVm;
import org.cloudbus.cloudsim.container.core.Container;
import org.cloudbus.cloudsim.container.core.ContainerDatacenterBroker;
import org.cloudbus.cloudsim.container.resourceAllocatorMigrationEnabled.PowerContainerVmAllocationPolicyMigrationAbstractHostSelection;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.GuestEntity;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.models.*;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.selectionPolicies.SelectionPolicy;
import org.cloudbus.cloudsim.selectionPolicies.SelectionPolicyMaximumUsage;
import org.cloudbus.cloudsim.util.WorkloadFileReader;
import org.cloudbus.cloudsim.util.WorkloadModel;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.cloudbus.cloudsim.selectionPolicies.SelectionPolicyFirstFit;

/**
 * A simple example showing how to create
 * a datacenter with one host and run two
 * cloudlets on it. The cloudlets run in
 * Vms with the same MIPS requirements.
 * The cloudlets will take the same time to
 * complete the execution. SOURCE:
 * https://cloudsimtutorials.online/guide-to-power-aware-simulation-scenario-in-cloudsim/,
 * ss_scenario_A_100_repetition_1
 */
public class SWFSimpleNetworkExperiment {
    public static DatacenterBroker broker;

    /** The vmlist. */
    private static List<PowerContainerVm> vmList;

    /**
     * The containerList.
     */

    private static List<Container> containerList;

    /**
     * Creates main() to run this example
     */
    public static void main(String[] args) throws FileNotFoundException, IOException {
        // Setup
        String system = args[0].strip().toLowerCase();
        String scenario = args[1].strip().toUpperCase();
        int users = Integer.parseInt(args[2].strip());
        int repetitiion = Integer.parseInt(args[3].strip());
        String tracePath = String.format(
            "../../traces/%s/%s/%s/%s/trace.swf",
            system, scenario, users, repetitiion);
        String propertiesPath = String.format(
            "../../traces/properties.json",
            system, scenario, users, repetitiion);

        ObjectMapper propertiesMapper = new ObjectMapper();
        Map<String, Object> properties = new HashMap<>();
        try {
            File file = new File(propertiesPath);
            properties = propertiesMapper.readValue(file, Map.class);
            Log.println(properties);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        Map<String, Object> host = (Map<String, Object>) properties.get("host");
        Map<String, Object> hostPower = (Map<String, Object>) host.get("power");
        Map<String, Object> network = (Map<String, Object>) properties.get("network");

        double coreFreq = ((Number) host.get("core_freq_ghz")).doubleValue();
        int pesNumber = ((Number) host.get("n_cores")).intValue();
        double ipc = ((Number) host.get(String.format("ipc_%s", system))).doubleValue();
        int mips = (int) Math.round(coreFreq * 1e9 * ipc / pesNumber / 1e6);  // Hz * IPC / n_cores / 10^6
        int ram = ((Number) host.get("ram_gb")).intValue() * (int) 1e3 ; // in MB double val = ((Number) properties.get("someKey")).doubleValue();
        long bw = ((Number) network.get("bandwidth_gbps")).longValue() / (long) 1e9; // in bytes
        double maxPower = ((Number) hostPower.get("max")).doubleValue();
        double idlePowerFrac = ((Number) hostPower.get("idle")).doubleValue() / maxPower; // idlePower as fraction of maxPower

        // Set power model
        Log.println(args);
        String powerModelStr = args[4].strip().toLowerCase();
        PowerModel powermodel = null;
        switch (powerModelStr) {
            case "cubic":
                powermodel = new PowerModelCubic(maxPower, idlePowerFrac);
                break;
            case "linear":
                powermodel = new PowerModelLinear(maxPower, idlePowerFrac);
                break;
            case "sqrt":
                powermodel = new PowerModelSqrt(maxPower, idlePowerFrac);
                break;
            case "square":
                powermodel = new PowerModelSquare(maxPower, idlePowerFrac);
                break;
            default:
                Log.printlnConcat("Powermodel given is invalid, must be cubic, linear, sqrt or square. Instead got ",
                        powerModelStr);
                System.exit(1);
        }
        String experimentName = String.format("%s_firstFit_%s_%s_%d_%d", powerModelStr, system, scenario, users, repetitiion);

        Log.println("Starting SWFSimpleNetworkExperiment...");

        try {
            // First step: Initialize the CloudSim package. It should be called
            // before creating any entities.
            int num_user = 1; // number of cloud users
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = true; // mean trace events

            // Initialize the CloudSim library
            CloudSim.init(num_user, calendar, trace_flag);

            // Second step: Create Datacenters
            // Datacenters are the resource providers in CloudSim. We need at list one of
            // them to run a CloudSim simulation

            // Third step: Create Broker
            ContainerDatacenterBroker broker = null;
            try {
                broker = new ContainerDatacenterBroker("Broker", 80);
            } catch (Exception var2) {
                var2.printStackTrace();
                System.exit(0);
            }
            int brokerId = broker.getId();

            // Create PEs
            ArrayList<Pe> peList = new ArrayList<>();
            for (int j = 0; j < pesNumber; j++) {
                peList.add(new Pe(j, new PeProvisionerSimple(mips)));
            }

            // Create hostList
            List<PowerHost> hostList = new ArrayList<PowerHost>();
            hostList.add(new PowerHost(0,
                                        new RamProvisionerSimple(ram),
                                        new BwProvisionerSimple(bw),
                                        ram,
                                        peList,
                                        new VmSchedulerTimeShared(peList),
                                        powermodel));


            CloudletScheduler cloudletScheduler = new CloudletSchedulerTimeShared();

            // Create virtual machine
            long size = 10000; // image size (MB), arbitrarily set
            String vmm = "Xen"; // VmM name
            double schedulingInterval = 1; // The scheduling interval in seconds to update the processing of cloudlets running in this VM.
            vmList = new ArrayList<>();
            PowerContainerVm vm = new PowerContainerVm(0, brokerId,
                mips, ram, bw, size, vmm,
                new VmSchedulerTimeShared(peList),
                new RamProvisionerSimple(ram),
                new BwProvisionerSimple(bw),
                peList, schedulingInterval);
            vm.setCloudletScheduler(cloudletScheduler);
            vmList.add(vm);

            // Create cloudlets
            WorkloadModel r = new WorkloadFileReader(tracePath, mips);
            List<Cloudlet> cloudletList = r.generateWorkload();
            for (Cloudlet c : cloudletList) {
                c.setUserId(brokerId);
            }

            // Create container
            containerList = new ArrayList<>();
            containerList.add(new PowerContainer(0, brokerId,
                                mips, pesNumber, ram, bw, size, vmm,
                                cloudletScheduler, schedulingInterval));

            PowerContainerDatacenter datacenter = createDatacenter("datacenter", schedulingInterval, hostList, experimentName);

            // submit cloudlet list to the broker
            Log.println("Submitting Cloudletlist");
            broker.submitCloudletList(cloudletList);
            broker.submitContainerList(containerList);
            broker.submitGuestList(vmList);

            // Sixth step: Starts the simulation
            Log.println("Starting simulation SWFSimpleNetworkExperiment");
            CloudSim.startSimulation();

            // Final step: Print results when simulation is over
            List<Cloudlet> newList = broker.getCloudletReceivedList();

            CloudSim.stopSimulation();

            printCloudletList(newList);

            Log.printlnConcat("datacenter power: ", datacenter.getPower(), " Joules"); // Since energy is checked every
                                                                                        // 1 seconds and
                                                                                        // datacenter.power is a sum of
                                                                                        // Watts for each check, it's
                                                                                        // Joules.

            Log.println("SWFExperiment finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.println("The simulation has been terminated due to an unexpected error");
        }
    }

    private static PowerContainerDatacenter createDatacenter(
            String name, double schedulingInterval, List<PowerHost> hostList, String experimentName) {

        // Here are the steps needed to create a PowerDatacenter:
        // 1. We need to create a list to store
        // our machine

        // 5. Create a DatacenterCharacteristics object that stores the
        // properties of a data center: architecture, OS, list of
        // Machines, allocation policy: time- or space-shared, time zone
        // and its price (G$/Pe time unit).
        String arch = "Cascade Lake"; // system architecture
        String os = "Linux"; // operating system
        String vmm = "Xen";
        double time_zone = 1.0; // time zone this resource located
        double cost = 0.0; // the cost of using processing in this resource
        double costPerMem = 0.0; // the cost of using memory in this resource
        double costPerStorage = 0.0; // the cost of using storage in this resource
        double costPerBw = 0.0; // the cost of using bw in this resource
        LinkedList<Storage> storageList = new LinkedList<>(); // we are not adding SAN devices by now

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch, os, vmm, hostList, time_zone, cost, costPerMem, costPerStorage, costPerBw);

        // 6. Finally, we need to create a PowerDatacenter object.
        PowerContainerDatacenter datacenter = null;
        try {
            // // // Log.printlnConcat("DEBUG (Experiment): going to init VmAllocationPolicySimpler with hostList=", hostList);
            VmAllocationPolicy containerAllocationPolicy = new VmAllocationPolicySimpler(vmList);
            // // // Log.printlnConcat("DEBUG (Experiment): containerAllocationPolicy.getGuestList(): ",
                    // containerAllocationPolicy.getHostList();

            SelectionPolicy<GuestEntity> vmSelectionPolicy = new SelectionPolicyMaximumUsage<>();
            VmAllocationPolicy vmAllocationPolicy = new PowerContainerVmAllocationPolicyMigrationAbstractHostSelection(
                    hostList, vmSelectionPolicy, new SelectionPolicyFirstFit<>(), 1.0, 0.0 // Threshold are set to [0, 1] so there is no migration.
            );
            // // // Log.printlnConcat("DEBUG vmAllocationPolicy.getHostList", vmAllocationPolicy.getHostList());
            String logAddress = "outputs/swf";

            datacenter = new PowerContainerDatacenter(name, characteristics,
                    vmAllocationPolicy, containerAllocationPolicy, storageList, schedulingInterval,
                    experimentName, logAddress);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return datacenter;
    }

    /**
     * Prints the Cloudlet objects
     *
     * @param list list of Cloudlets
     */
    private static void printCloudletList(List<Cloudlet> list) {
        Cloudlet cloudlet;

        String indent = "    ";
        Log.println();
        Log.println("========== OUTPUT ==========");
        Log.println("Cloudlet ID" + indent + "STATUS" + indent +
                "Data center ID" + indent + "Vm ID" + indent + "Time" + indent + "Start Time" + indent + "Finish Time");

        DecimalFormat dft = new DecimalFormat("###.##");
        for (Cloudlet value : list) {
            cloudlet = value;
            Log.print(indent + cloudlet.getCloudletId() + indent + indent);

            if (cloudlet.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
                Log.print("SUCCESS");

                Log.println(
                        indent + indent + cloudlet.getResourceId() + indent + indent + indent + cloudlet.getGuestId() +
                                indent + indent + dft.format(cloudlet.getActualCPUTime()) + indent + indent
                                + dft.format(cloudlet.getExecStartTime()) +
                                indent + indent + dft.format(cloudlet.getExecFinishTime()));
            }
        }

    }
}
