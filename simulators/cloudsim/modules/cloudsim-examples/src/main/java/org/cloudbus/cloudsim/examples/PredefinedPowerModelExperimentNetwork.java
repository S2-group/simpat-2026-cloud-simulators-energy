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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
 import org.cloudbus.cloudsim.DatacenterBroker;
 import org.cloudbus.cloudsim.DatacenterCharacteristics;
 import org.cloudbus.cloudsim.Host;
 import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.NetworkTopology;
import org.cloudbus.cloudsim.Pe;
 import org.cloudbus.cloudsim.Storage;
 import org.cloudbus.cloudsim.UtilizationModel;
 import org.cloudbus.cloudsim.UtilizationModelFull;
 import org.cloudbus.cloudsim.UtilizationModelMicroservices;
 import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.VmAllocationPolicySimpler;
 import org.cloudbus.cloudsim.VmSchedulerTimeShared;
 import org.cloudbus.cloudsim.container.core.PowerContainer;
 import org.cloudbus.cloudsim.container.core.PowerContainerNetworkDatacenter;
import org.cloudbus.cloudsim.container.core.PowerContainerVm;
import org.cloudbus.cloudsim.container.core.Container;
import org.cloudbus.cloudsim.container.core.ContainerDatacenterBroker;
import org.cloudbus.cloudsim.container.resourceAllocatorMigrationEnabled.PowerContainerVmAllocationPolicyMigrationAbstractHostSelection;
 import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.GuestEntity;
import org.cloudbus.cloudsim.network.datacenter.AppCloudlet;
import org.cloudbus.cloudsim.network.datacenter.NetworkCloudlet;
import org.cloudbus.cloudsim.network.datacenter.NetworkDatacenter;
import org.cloudbus.cloudsim.network.datacenter.PowerSwitch;
import org.cloudbus.cloudsim.network.datacenter.Switch;
import org.cloudbus.cloudsim.power.PowerDatacenter;
 import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.PowerNetworkHost;
import org.cloudbus.cloudsim.power.PowerVm;
 import org.cloudbus.cloudsim.power.PowerVmAllocationPolicyMigrationStaticThreshold;
 import org.cloudbus.cloudsim.power.models.*;
 import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
 import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
 import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
 import org.cloudbus.cloudsim.selectionPolicies.SelectionPolicy;
import org.cloudbus.cloudsim.selectionPolicies.SelectionPolicyMaximumUsage;
import org.cloudbus.cloudsim.selectionPolicies.SelectionPolicyRandomSelection;

import com.google.common.graph.Network;

import org.cloudbus.cloudsim.selectionPolicies.SelectionPolicyFirstFit;


 /**
  * A simple example showing how to create
  * a datacenter with one host and run two
  * cloudlets on it. The cloudlets run in
  * Vms with the same MIPS requirements.
  * The cloudlets will take the same time to
  * complete the execution. SOURCE: https://cloudsimtutorials.online/guide-to-power-aware-simulation-scenario-in-cloudsim/, ss_scenario_A_100_repetition_1
  */
 public class PredefinedPowerModelExperimentNetwork {
     public static DatacenterBroker broker;

     /** The cloudlet list. */
     private static List<NetworkCloudlet> cloudletList;

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
        double maxPower = 0.526594848735254;
        double idlePowerFrac = 9.829087373984885e-10/maxPower; // idlePower as fraction of maxPower

         Log.println("Starting PredefinedPowerModelExperimentNetwork...");

         // Set power model
         String powerModelStr = args[0].strip().toLowerCase();
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
                Log.printlnConcat("Powermodel given is invalid, must be cubic, linear, sqrt or square. Instead got ", powerModelStr);
                System.exit(1);
         }

         // Load experiment system properties
         String system = args[1].strip().toLowerCase();
         String scenario = args[2].strip().toUpperCase();
         int users = Integer.parseInt(args[3].strip());
         int repetitiion = Integer.parseInt(args[4].strip());
         String propertiesPath = String.format("/home/meret/Desktop/thesis/simulators/cloudsim-7.0/analysis/traces/%s_scenario_%s_%d_repetition_%d/properties.data", system, scenario, users, repetitiion); // TODO

        BufferedReader input = new BufferedReader(new FileReader(propertiesPath));
        ArrayList<String> services = new ArrayList<>();
        ArrayList<Integer> vmNumPes = new ArrayList<>();
        ArrayList<Integer> vmRam = new ArrayList<>();
        String line;
        while ((line = input.readLine()) != null) {
            services.add(line.split(" ")[0]);
            vmNumPes.add(Integer.parseInt(line.split(" ")[1]));
            vmRam.add(Integer.parseInt(line.split(" ")[2]));
        }
        int numServices = services.size();


        String experimentName = String.format("%s_%s_%s_%d_%d", powerModelStr, system, scenario, users, repetitiion);
             try {
                 // First step: Initialize the CloudSim package. It should be called
                     // before creating any entities.
                     int num_user = 1;   // number of cloud users
                     Calendar calendar = Calendar.getInstance();
                     boolean trace_flag = true;  // mean trace events

                     // Initialize the CloudSim library
                     CloudSim.init(num_user, calendar, trace_flag);

                     // Second step: Create Datacenters
                     //Datacenters are the resource providers in CloudSim. We need at list one of them to run a CloudSim simulation

                     //Third step: Create Broker
                     ContainerDatacenterBroker broker = null;
                     try{
                        broker = new ContainerDatacenterBroker("Broker", 80); // TODO check overBookingFactor
                     } catch (Exception var2) {
                        var2.printStackTrace();
                        System.exit(0);
                    }
                     int brokerId = broker.getId();

                     // Create hostList
                     List<PowerNetworkHost> hostList = new ArrayList<PowerNetworkHost>();

                    // 2. A Machine contains one or more PEs or CPUs/Cores. For experiment set-up, two machines with 8 cores each
                    int ram = 385024; //host memory (MB) = 376 GB
                    long storage = 1000000; //host storage TODO
                    int num_host = 2;
                    int vmBw = 1000;
                    int hostBw = numServices*numServices*vmBw;

                    for (int hostId=0; hostId<num_host; hostId++) {
                        List<Pe> peList = new ArrayList<>();

                        int mips = 10000;  // TODO

                        // 3. Create PEs and add these into a list.
                        for (int peId=0; peId<8; peId++) {
                            peList.add(new Pe(peId, new PeProvisionerSimple(mips))); // need to store Pe id and MIPS Rating
                        }

                        //4. Create Host with its id and list of PEs and add them to the list of machines

                        hostList.add(
                            new PowerNetworkHost(
                                hostId,
                                new RamProvisionerSimple(ram),
                                new BwProvisionerSimple(hostBw),
                                storage,
                                peList,
                                new VmSchedulerTimeShared(peList),
                                powermodel
                            )
                        );
                    }

                     //Fourth step: Create virtual machines
                     vmList = new ArrayList<>();
                     //Vm description
                     double mips = 250; // Millions Instructions per Second, "MIPS capacity of each Vm's PE" (Vm.java). Arbitrarily set, TODO.
                     long size = 10000; //image size (MB) TOOD
                     String vmm = "Xen"; //VmM name
                     double schedulingInterval = 1; // The scheduling interval in seconds to update the processing of cloudlets running in this VM.
                     int priority = 1;

                     // 13 services
                     // VM properties
                     // Cloudlet properties
                     cloudletList = new ArrayList<>();
                    int experimentDuration = 5 * 60; // 5 minutes in seconds.
                    long cloudletLength = experimentDuration * (long) mips; // Millions of instructions per processor, so MIPS/length = execution time in seconds. 5 minutes total.
                    long fileSize = 1; // This affects bandwidth consumed, not relevant to power.
                    long outputSize = 1; // This affects bandwidth consumed, not relevant to power.
                    // UtilizationModel utilizationModel = new UtilizationModelFull();

                    containerList = new ArrayList<>();

                    AppCloudlet app = new AppCloudlet(AppCloudlet.APP_Workflow, 0, 2000, brokerId);

                     for (int i=0; i<numServices; i++) {
                        UtilizationModel utilizationModel = new UtilizationModelMicroservices(system, services.get(i), scenario, users, repetitiion, vmNumPes.get(i));
                        CloudletScheduler cloudletScheduler= new CloudletSchedulerTimeShared(); // each VM has to have its own instance of the cloudletScheduler
                        ArrayList<Pe> peList = new ArrayList<>();
                        for (int j=0; j<vmNumPes.get(i); j++) {
                            peList.add(new Pe(j, new PeProvisionerSimple(mips)));
                        }
                        PowerContainerVm vm = new PowerContainerVm(i, brokerId,
                            mips, vmRam.get(i), vmBw, size, vmm,
                            new VmSchedulerTimeShared(peList),
                            new RamProvisionerSimple(vmRam.get(i)),
                            new BwProvisionerSimple(vmBw),
                            peList, schedulingInterval);
                        vm.setCloudletScheduler(cloudletScheduler);
                        vmList.add(vm);
                        NetworkCloudlet cloudlet = new NetworkCloudlet(i, cloudletLength, vmNumPes.get(i), fileSize, outputSize, utilizationModel, utilizationModel, utilizationModel);
                        cloudlet.addExecutionStage(cloudletLength);
                        cloudlet.setUserId(brokerId);
                        app.cList.add(cloudlet);
                        cloudletList.add(cloudlet);

                        containerList.add(new PowerContainer(i, brokerId,
                            mips, vmNumPes.get(i), vmRam.get(i), vmBw, size, vmm,
                            cloudletScheduler, schedulingInterval));
                     }

                     Log.printlnConcat("cloudletSize", cloudletList.size());

                    NetworkCloudlet cli;
                     for (int i=0; i<cloudletList.size(); i++) {
                        cli = cloudletList.get(i);
                        if (i > 0) {
                            cli.addRecvStage(cloudletList.get(i-1));
                            // // // Log.printlnConcat("DEBUG (Predefined) ", i, " receiving from", i-1);
                        }
                        if (i < cloudletList.size()-1) {
                            cli.addSendStage(100, cloudletList.get(i+1));
                            // // // Log.printlnConcat("DEBUG (Predefined) ", i, " sending to", i+1);
                        }
                     }


                     PowerContainerNetworkDatacenter servicesDatacenter = createDatacenter("Datacenter_0", schedulingInterval, hostList, experimentName);

                     //submit cloudlet list to the broker
                     Log.println("Submitting Cloudletlist");
                     broker.submitCloudletList(cloudletList);
                     broker.submitContainerList(containerList);
                     broker.submitGuestList(vmList);

                     // will submit the bound cloudlets only to the specific ContainerW
                     Log.println("Binding cloudlets to Containers");
                     for (int i=0; i < numServices; i++) {
                        broker.bindCloudletToContainer(i, i);
                     }

                     // Configure Network
                     NetworkTopology.addLink(servicesDatacenter.getId(), brokerId, 0, 1);
                    //  NetworkTopology.buildNetworkTopology(PredefinedPowerModelExperimentNetwork.class.getClassLoader().getResource("topology.brite").getPath());
                    //  NetworkTopology.mapNode(datacenter0.getId(), 0);
                    //  NetworkTopology.mapNode(broker.getId(), 1);

                     // Sixth step: Starts the simulationd
                     Log.println("Starting simulation");
                     CloudSim.startSimulation();

                     // Final step: Print results when simulation is over
                     List<Cloudlet> newList = broker.getCloudletReceivedList();

                     CloudSim.stopSimulation();

                     printCloudletList(newList);

                     Log.printlnConcat("datacenter power: ", servicesDatacenter.getPower(), " Joules"); // Since energy is checked every 1 seconds and datacenter.power is a sum of Watts for each check, it's Joules.

                     Log.println("PredefinedPowerModelExperimentNetwork finished!");
             }
             catch (Exception e) {
                 e.printStackTrace();
                 Log.println("The simulation has been terminated due to an unexpected error");
             }
         }

         private static PowerContainerNetworkDatacenter createDatacenter(
            String name, double schedulingInterval, List<PowerNetworkHost> hostList, String experimentName
        ){

             // Here are the steps needed to create a PowerDatacenter:
             // 1. We need to create a list to store
             //    our machine


             // 5. Create a DatacenterCharacteristics object that stores the
             //    properties of a data center: architecture, OS, list of
             //    Machines, allocation policy: time- or space-shared, time zone
             //    and its price (G$/Pe time unit).
             String arch = "Cascade Lake";      // system architecture
             String os = "Linux";          // operating system
             String vmm = "Xen";
             double time_zone = 1.0;         // time zone this resource located
             double cost = 0.0;              // the cost of using processing in this resource
             double costPerMem = 0.0;		// the cost of using memory in this resource
             double costPerStorage = 0.0;	// the cost of using storage in this resource
             double costPerBw = 0.0;			// the cost of using bw in this resource
             LinkedList<Storage> storageList = new LinkedList<>();	//we are not adding SAN devices by now

             DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                     arch, os, vmm, hostList, time_zone, cost, costPerMem, costPerStorage, costPerBw);


             // 6. Finally, we need to create a PowerDatacenter object.
             PowerContainerNetworkDatacenter datacenter = null;
             try {
                // // // Log.printlnConcat("DEBUG (Experiment): going to init VmAllocationPolicySimpler with hostList=", hostList);
                VmAllocationPolicy containerAllocationPolicy = new VmAllocationPolicySimpler(vmList);
                // // // Log.printlnConcat("DEBUG (Experiment): containerAllocationPolicy.getGuestList(): ", containerAllocationPolicy.getHostList());

                SelectionPolicy<GuestEntity> vmSelectionPolicy = new SelectionPolicyMaximumUsage<>();
                VmAllocationPolicy vmAllocationPolicy = new PowerContainerVmAllocationPolicyMigrationAbstractHostSelection(
                    hostList, vmSelectionPolicy, new SelectionPolicyFirstFit<>(), 1.0, 0.0 // Threshold are set to [0, 1] so there is no migration.
                );
                String logAddress = "outputs/predefined-network";

                datacenter = new PowerContainerNetworkDatacenter(name, characteristics,
                    vmAllocationPolicy, containerAllocationPolicy, storageList, schedulingInterval,
                    experimentName, logAddress);
            } catch (Exception e) {
                 e.printStackTrace();
             }

            //  CreateNetwork(datacenter, hostList.size());

             return datacenter;
         }

         /**
          * Prints the Cloudlet objects
          * @param list  list of Cloudlets
          */
         private static void printCloudletList(List<Cloudlet> list) {
             int size = list.size();
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

                     Log.println(indent + indent + cloudlet.getResourceId() + indent + indent + indent + cloudlet.getGuestId() +
                             indent + indent + dft.format(cloudlet.getActualCPUTime()) + indent + indent + dft.format(cloudlet.getExecStartTime()) +
                             indent + indent + dft.format(cloudlet.getExecFinishTime()));
                 }
             }

        }

        private static void CreateNetwork(PowerContainerNetworkDatacenter dc, double numHosts) {
            // Create ToR switch
            double edgeSwitchPort = numHosts; // number of hosts
            long bwEdgeHost = 100 * 1024 * 1024; // 100 Megabits
            long bwEdgeAgg = 100 * 1024 * 1024; // 100 Megabits

            PowerSwitch ToRSwitch = new PowerSwitch("Edge0", edgeSwitchPort, PowerSwitch.SwitchLevel.EDGE_LEVEL,
                        0, bwEdgeHost, bwEdgeAgg, dc);

            dc.registerSwitch(ToRSwitch);

            // Attach to hosts
            for (PowerNetworkHost netHost : dc.<PowerNetworkHost>getHostList()) {
                dc.attachSwitchToHost(ToRSwitch, netHost);
            }

        }
 }
