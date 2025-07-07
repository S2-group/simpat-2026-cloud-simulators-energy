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
 import org.cloudbus.cloudsim.container.core.PowerContainerDatacenter;
 import org.cloudbus.cloudsim.container.core.PowerContainerDatacenterCM;
 import org.cloudbus.cloudsim.container.core.PowerContainerVm;
 import org.cloudbus.cloudsim.container.core.Container;
 import org.cloudbus.cloudsim.container.core.ContainerDatacenterBroker;
 import org.cloudbus.cloudsim.container.resourceAllocatorMigrationEnabled.PowerContainerVmAllocationPolicyMigrationAbstractHostSelection;
 import org.cloudbus.cloudsim.core.CloudSim;
 import org.cloudbus.cloudsim.core.GuestEntity;
 import org.cloudbus.cloudsim.power.PowerDatacenter;
 import org.cloudbus.cloudsim.power.PowerHost;
 import org.cloudbus.cloudsim.power.PowerVm;
 import org.cloudbus.cloudsim.power.PowerVmAllocationPolicyMigrationStaticThreshold;
 import org.cloudbus.cloudsim.power.models.*;
 import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
 import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
 import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
 import org.cloudbus.cloudsim.selectionPolicies.SelectionPolicy;
 import org.cloudbus.cloudsim.selectionPolicies.SelectionPolicyMaximumUsage;
 import org.cloudbus.cloudsim.selectionPolicies.SelectionPolicyMinimumUtilization;
 import org.cloudbus.cloudsim.selectionPolicies.SelectionPolicyRandomSelection;
 import org.cloudbus.cloudsim.selectionPolicies.SelectionPolicyFirstFit;

 import com.fasterxml.jackson.databind.ObjectMapper;

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
 public class TraceExperiment {
     public static DatacenterBroker broker;

     /** The cloudlet list. */
     private static List<Cloudlet> cloudletList;

     /**
      * The containerList.
      */

     private static List<Container> containerList;

     private static List<PowerContainerVm> vmList;

     /**
      * Creates main() to run this example
      */
     public static void main(String[] args) throws FileNotFoundException, IOException {
         // Setup

         for (int i = 0; i < args.length; i++) {
             System.out.println("args[" + i + "] = " + args[i]);
         }

         String system = args[0].strip().toLowerCase();
         String scenario = args[1].strip().toUpperCase();
         int users = Integer.parseInt(args[2].strip());
         int repetitiion = Integer.parseInt(args[3].strip());
         int run = Integer.parseInt(args[6].strip());
         String propertiesPath = String.format("../../traces/properties.json", system, scenario, users, repetitiion);

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
         int mips = (int) Math.round(coreFreq * 1e9 * ipc / pesNumber / 1e6); // Hz * IPC / n_cores / 10^6
         int ram = ((Number) host.get("ram_gb")).intValue() * (int) 1e3; // in MB double val = ((Number)
                                                                         // properties.get("someKey")).doubleValue();
         long bw = ((Number) network.get("bandwidth_gbps")).longValue() * (long) 1e9; // in bytes
         double maxPower = ((Number) hostPower.get("max")).doubleValue();
         double idlePowerFrac = ((Number) hostPower.get("idle")).doubleValue() / maxPower; // idlePower as fraction of
                                                                                           // maxPower

         // Set power model
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

         String vmSelectionStr = args[5].strip().toLowerCase();
         SelectionPolicy<GuestEntity> vmSelectionPolicy = null;
         switch (vmSelectionStr) {
             case "minutil":
                 vmSelectionPolicy = new SelectionPolicyMinimumUtilization();
                 break;
             case "firstfit":
                 vmSelectionPolicy = new SelectionPolicyFirstFit<>();
                 break;
             case "random":
                 vmSelectionPolicy = new SelectionPolicyRandomSelection<>();
                 break;
             default:
                 Log.printlnConcat("Selection Policy given is invalid, must be minUtil, firstFit, random. Instead got ",
                                     vmSelectionStr);
                 System.exit(1);
         }

         String experimentName = String.format("%s_%s_%s_%s_%d_%d", powerModelStr, vmSelectionStr, system, scenario, users, repetitiion);

         Log.println("Starting TraceExperiment...");
         String servicesPath = String.format("../../traces/%s/%s/%d/%d/services-overview.data", system, scenario, users, repetitiion);
         BufferedReader input = new BufferedReader(new FileReader(servicesPath));
         ArrayList<String> services = new ArrayList<>();
         ArrayList<Integer> vmNumPes = new ArrayList<>();
         ArrayList<Integer> vmRam = new ArrayList<>();
         String line;
         int ramSum = 0;
         int pesSum = 0;
         while ((line = input.readLine()) != null) {
             services.add(line.split(" ")[0]);
             int thisPes = Integer.parseInt(line.split(" ")[1]);
             vmNumPes.add(thisPes);
             pesSum += thisPes;
             int thisRam = Integer.parseInt(line.split(" ")[2]);
             vmRam.add(thisRam);
             ramSum += thisRam;

         }
         int numServices = services.size();
         pesNumber = Math.max(pesNumber, pesSum); // Make sure there are enough CPUs.
         ram = Math.max(ram, ramSum); // Make sure there is enough ram.

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
                 broker = new ContainerDatacenterBroker("Broker", 80); // TODO check overBookingFactor
             } catch (Exception var2) {
                 var2.printStackTrace();
                 System.exit(0);
             }
             int brokerId = broker.getId();

             // 2. A Machine contains one or more PEs or CPUs/Cores. For experiment set-up,
             // two machines with 8 cores each
             int vmBw = (int) Math.floor(bw / numServices);

             List<Pe> peList = new ArrayList<>();

             // 3. Create PEs and add these into a list.
             for (int peId = 0; peId < pesNumber; peId++) {
                 peList.add(new Pe(peId, new PeProvisionerSimple(mips))); // need to store Pe id and MIPS Rating
             }

             // Create hostList
             List<PowerHost> hostList = new ArrayList<PowerHost>();
             hostList.add(new PowerHost(0,
                             new RamProvisionerSimple(ram*2),
                             new BwProvisionerSimple(bw),
                             ram*2,
                             peList,
                             new VmSchedulerTimeShared(peList),
                             powermodel));

             // Fourth step: Create virtual machines

             /** The vmlist. */
             vmList = new ArrayList<>();
             // Vm description
             // Log.printlnConcat("DEBUG numServices=", numServices);
             long vmSize = (long) Math.floor(ram/numServices); // image size (MB), arbitrarily set
             String vmm = "Xen"; // VmM name
             double schedulingInterval = 1; // The scheduling interval in seconds to update the processing of bs
                                            // running in this VM.

             // 13 services
             // VM properties
             // Cloudlet properties
             cloudletList = new ArrayList<>();
             int experimentDuration = 5 * 60; // 5 minutes in seconds.
             long cloudletLength = experimentDuration * (long) mips; // Millions of instructions per processor, so
                                                                     // MIPS/length = execution time in seconds. 5
                                                                     // minutes total.
             long fileSize = 1; // This affects bandwidth consumed, not relevant to power, so arbitrarily set.
             long outputSize = 1; // This affects bandwidth consumed, not relevant to power, so arbitrarily set.

             containerList = new ArrayList<>();

             for (int i = 0; i < numServices; i++) {
                 if (vmNumPes.get(i) == 0) {
                     continue;
                 }
                 ArrayList<Pe> vmPes = new ArrayList<>();
                 for (int j=0; j<vmNumPes.get(i); j++) {
                     vmPes.add(new Pe(j, new PeProvisionerSimple(mips)));
                 }

                 UtilizationModel utilizationModel = new UtilizationModelMicroservices(system, services.get(i), scenario,
                                                                                       users, repetitiion, vmNumPes.get(i));
                 CloudletScheduler cloudletScheduler = new CloudletSchedulerTimeShared(); // each VM has to have its own
                                                                                          // instance of the
                                                                                          // cloudletScheduler
                 PowerContainerVm vm = new PowerContainerVm(i, brokerId,
                         mips, vmRam.get(i), vmBw, vmSize, vmm,
                         new VmSchedulerTimeShared(vmPes),
                         new RamProvisionerSimple(vmRam.get(i)),
                         new BwProvisionerSimple(vmBw),
                         vmPes, schedulingInterval);
                 vm.setCloudletScheduler(cloudletScheduler);
                 vmList.add(vm);

                 Cloudlet cloudlet = new Cloudlet(i, cloudletLength, vmNumPes.get(i), fileSize, outputSize,
                                                 utilizationModel, utilizationModel, utilizationModel);
                 cloudlet.setUserId(brokerId);
                 cloudletList.add(cloudlet);

                 containerList.add(new PowerContainer(i, brokerId,
                                                     mips, vmNumPes.get(i), vmRam.get(i), vmBw, vmSize, vmm,
                                                     cloudletScheduler, schedulingInterval));
             }

             PowerContainerDatacenter datacenter = createDatacenter("Datacenter", schedulingInterval, hostList,
                                                                     experimentName, vmSelectionPolicy, run);

             // submit cloudlet list to the broker
             Log.println("Submitting Cloudletlist");
             broker.submitCloudletList(cloudletList);
             broker.submitContainerList(containerList);
             broker.submitGuestList(vmList);

             // will submit the bound cloudlets only to the specific ContainerW
             Log.println("Binding cloudlets to Containers");
             for (Cloudlet c : cloudletList) {
                 broker.bindCloudletToContainer(c.getCloudletId(), c.getCloudletId());
             }
             // Sixth step: Starts the simulation
             Log.println("Starting simulation");
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

             Log.println("TraceExperiment finished!");
         } catch (Exception e) {
             e.printStackTrace();
             Log.println("The simulation has been terminated due to an unexpected error");
         }
     }

     private static PowerContainerDatacenter createDatacenter(
             String name, double schedulingInterval, List<PowerHost> hostList, String experimentName, SelectionPolicy<GuestEntity> vmSelectionPolicy, int run) {

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

             VmAllocationPolicy vmAllocationPolicy = new PowerContainerVmAllocationPolicyMigrationAbstractHostSelection(
                     hostList, vmSelectionPolicy, new SelectionPolicyFirstFit<>(), 1.0, 0.0 // Threshold are set to [0,1] so there is no migration.
             );
             String logAddress = String.format("outputs/trace/%d", run);

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
