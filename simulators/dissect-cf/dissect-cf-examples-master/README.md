## Overview
This package represents a set of examples for the DIScrete event baSed Energy
Consumption simulaTor for Clouds and Federations (DISSECT-CF).

It is intended as a demonstration for the lightweight cloud simulator. It was
designed for demonstration purposes only so researchers could see how they could
implement their own experiments.

The development of DISSECT-CF-Examples started in MTA SZTAKI in 2012, major
contirbutions and design elements were incorporated at the University of Innsbruck. 

Website:
https://github.com/kecskemeti/dissect-cf-examples

Licensing:
GNU General Public License 3 and later

## Compilation & Installation

Prerequisites: Apache Maven 3, Java 1.6, [DistSysJavaHelpers 1.0.1](https://github.com/kecskemeti/DistSysJavaHelpers), [DISSECT-CF 0.9.6-SNAPSHOT](https://github.com/kecskemeti/dissect-cf)

After cloning and installing the prerequisites, run the following in the main dir of the checkout:

`mvn clean install javadoc:javadoc`

The installed examples will be located in the default maven repository's (e.g., `~/.m2/repository`) following directory: 
`hu/mta/sztaki/lpds/cloud/simulator/dissect-cf-examples[VERSION]/dissect-cf-examples-[VERSION].jar`

Where `[VERSION]` stands for the currently installed version of the example set.

The documentation for the example java code will be generated in the following subfolder of the main dir of the checkout:

`target/site/apidocs`

## Getting started

Currently the example set contains 4 more complex sample codes which show some more advanced use of the DISSECT-CF simulator than one can already see in its original test cases. These four samples are all CLI applications and are listed below:
* `hu.mta.sztaki.lpds.cloud.simulator.examples.TransferDemo` shows how one can store data in repositories and how these storage objects can be transferred amongst them.
* `hu.mta.sztaki.lpds.cloud.simulator.examples.MigrationModeling` shows an a simple two physical machine setup with a single VM migrating between them, during the migration process, the simulator is also instructed to collect energy readings for both machines.
* `hu.mta.sztaki.lpds.cloud.simulator.examples.SingleVMOverloader` shows a technique to use randomly generated traces to overload a single virtual machine that completely occupies a phyisical machine.
* `hu.mta.sztaki.lpds.cloud.simulator.examples.jobhistoryprocessor.JobDispatchingDemo` reveals the most complex sample, where a trace (either loaded from a file or generated) of jobs is executed on automatically created virtual machines in one or more cloud infrastructures.

Please note that all these examples are provided as simple samples and not inteded for advanced use. Most of them are simplified to allow easy understanding of the underlying concepts in the simulator.

## Remarks

For a more complex example one can have a look at [the GroudSim-DISSECT-CF integration](http://www.dps.uibk.ac.at/projects/groudsim/).

##### Warning: the master branch of the examples is intended as a development branch, and might not contain a functional version!
