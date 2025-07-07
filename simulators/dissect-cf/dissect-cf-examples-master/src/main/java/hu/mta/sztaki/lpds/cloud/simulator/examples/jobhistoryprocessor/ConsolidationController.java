package hu.mta.sztaki.lpds.cloud.simulator.examples.jobhistoryprocessor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.simple.AbcConsolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.simple.GaConsolidator;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmconsolidation.pso.PsoConsolidator;

/**
 * @author Rene Ponto
 * 
 *         This class manages the consolidation algorithms, which means do
 *         several test cases with different values for the constants of the
 *         consolidators. The results are going to be saved inside a seperate
 *         file. There are also methods to set the needed values for
 *         consolidation of some algorithms.
 */
public class ConsolidationController {
	private int propCounter = 0;
	Properties props; // the properties-file, contains the constants of the pso-, abc- and
						// ga-consolidator

	private String psoDefaultSwarmSize = "20";
	private String psoDefaultNrIterations = "50";
	private String psoDefaultC1 = "2";
	private String psoDefaultC2 = "2";
	private String abcDefaultPopulationSize = "10";
	private String abcDefaultNrIterations = "50";
	private String abcDefaultLimitTrials = "5";
	private String gaDefaultPopulationSize = "10";
	private String gaDefaultNrIterations = "50";
	private String gaDefaultNrCrossovers = "10";
	private String upperThreshold = "1.0";
	private String lowerThreshold = "0.25";
	private String mutationProb = "0.2";
	private String seed = "123";
	private String doLocalSearch2 = "false";
	private String doLocalSearch1 = "false";

	public static void main(String[] args) throws IOException {
		new ConsolidationController().runTestcaseOne(false);
	}

	/**
	 * Sets all default values (which are the origin ones) and reads the
	 * properties-file. The file is saved in .xml in the root of the simulator.
	 * 
	 * @param trace
	 *            The location of the trace-file.
	 * @throws IOException
	 */
	public ConsolidationController() throws IOException {

		props = new Properties();
		File file = new File("consolidationProperties.xml");
		FileInputStream fileInput = new FileInputStream(file);
		props.loadFromXML(fileInput);
		fileInput.close();

		// set the default values

		props.setProperty("upperThreshold", upperThreshold);
		props.setProperty("lowerThreshold", lowerThreshold);
		props.setProperty("mutationProb", mutationProb);
		props.setProperty("seed", seed);
		props.setProperty("doLocalSearch1", doLocalSearch1);
		props.setProperty("doLocalSearch2", doLocalSearch2);
		
		setPsoProperties(psoDefaultSwarmSize, psoDefaultNrIterations, psoDefaultC1, psoDefaultC2, doLocalSearch1, 
				doLocalSearch2, lowerThreshold, true);
		setAbcProperties(abcDefaultPopulationSize, abcDefaultNrIterations, abcDefaultLimitTrials, mutationProb,
				doLocalSearch1, doLocalSearch2, lowerThreshold, true);
		setGaProperties(gaDefaultPopulationSize, gaDefaultNrIterations, gaDefaultNrCrossovers, mutationProb, 
				doLocalSearch1, doLocalSearch2, lowerThreshold, true);

	}

	/**
	 * This testcase is to find the best configuration of the parameters of the
	 * consolidators. For that, we define a list of values to test and this method
	 * runs the appropriate consolidators for all possible combinations of the
	 * values. All results are saved inside a csv file.
	 * 
	 * @param test
	 *            If set to true, default values are taken, otherwise the lists get
	 *            filled and all combinations out of these values are taken
	 */
	public void runTestcaseOne(boolean test) {
		// defining lists with values for each parameter of each relevant algorithm
		List<Integer> psoSwarmSizeValues = new ArrayList<>();
		List<Integer> psoNrIterationsValues = new ArrayList<>();
		List<Double> psoC1Values = new ArrayList<>();
		List<Double> psoC2Values = new ArrayList<>();

		List<Integer> gaPopulationSizeValues = new ArrayList<>();
		List<Integer> gaNrIterationsValues = new ArrayList<>();
		List<Integer> gaNrCrossoversValues = new ArrayList<>();
		List<Double> gaMutationProbValues = new ArrayList<>();

		List<Integer> abcPopulationSizeValues = new ArrayList<>();
		List<Integer> abcNrIterationsValues = new ArrayList<>();
		List<Integer> abcLimitTrialsValues = new ArrayList<>();
		List<Double> abcMutationProbValues = new ArrayList<>();

		List<Boolean> doLocalSearch1Values = new ArrayList<>();
		List<Boolean> doLocalSearch2Values = new ArrayList<>();
		List<Double> lowerThresholdValues = new ArrayList<>();

		// fill the lists with values

		if (!test) {

			int i = 3;
			while (i < 101) {
				psoSwarmSizeValues.add(i);
				gaPopulationSizeValues.add(i);
				abcPopulationSizeValues.add(i);
				psoNrIterationsValues.add(i);
				gaNrIterationsValues.add(i);
				abcNrIterationsValues.add(i);
				gaNrCrossoversValues.add(i);
				i += i < 21 ? 2 : 20; // increase the variable by 2 to cover the <20 range better
			}

			i = 1;
			while (i < 15) {
				abcLimitTrialsValues.add(i);
				i += 3;
			}
			
			double j=.05; 
			while (j < .41) {
				psoC1Values.add(j);
				psoC2Values.add(j);
				j = j + .05;
			}
			j=1.6;
			while (j < 2.81) {
				psoC1Values.add(j);
				psoC2Values.add(j);
				j = j + .1;
			}

			j = 0.1;
			while (j <= 1) {
				abcMutationProbValues.add(j);
				gaMutationProbValues.add(j);
				j = j + 0.1;
			}
			doLocalSearch1Values.add(false);
			doLocalSearch1Values.add(true);
			
			doLocalSearch2Values.add(false);
			doLocalSearch2Values.add(true);
			
			lowerThresholdValues.add(0.2);
			lowerThresholdValues.add(0.4);
			lowerThresholdValues.add(0.6);
		}

		// test values, only one run with defaults
		if (test) {
			psoSwarmSizeValues.add(20);
			psoNrIterationsValues.add(50);
			psoC1Values.add(2.0);
			psoC2Values.add(2.0);

			gaPopulationSizeValues.add(10);
			gaNrIterationsValues.add(50);
			gaNrCrossoversValues.add(10);
			gaMutationProbValues.add(0.2);

			abcPopulationSizeValues.add(10);
			abcNrIterationsValues.add(50);
			abcLimitTrialsValues.add(5);
			abcMutationProbValues.add(0.2);

			doLocalSearch1Values.add(false);
			doLocalSearch2Values.add(false);
			lowerThresholdValues.add(0.25);
		}

		// now run the consolidators with every possible combination of their parameters
		// and save the results afterwards

		// pso consolidator
		for (int first : psoSwarmSizeValues) {
			for (int second : psoNrIterationsValues) {
				for (double third : psoC1Values) {
					for (double fourth : psoC2Values) {
						for (boolean fifth : doLocalSearch1Values) {
							for (boolean sixth : doLocalSearch2Values) {
								
								// if both values are true, we skip this run, because only ls1 would be taken
								if(fifth && sixth) {
									continue;
								}									
								for (double seventh : lowerThresholdValues) {
									setPsoProperties(
											psoSwarmSizeValues.get(psoSwarmSizeValues.indexOf(first)).toString(),
											psoNrIterationsValues.get(psoNrIterationsValues.indexOf(second)).toString(),
											psoC1Values.get(psoC1Values.indexOf(third)).toString(),
											psoC2Values.get(psoC2Values.indexOf(fourth)).toString(),
											doLocalSearch1Values.get(doLocalSearch1Values.indexOf(fifth)).toString(),
											doLocalSearch2Values.get(doLocalSearch2Values.indexOf(sixth)).toString(),
											Double.toString(seventh), false);
									if (!fifth) // if no local search -> value of lowerThreshold plays no role -> there is
										// no point in testing more than one value
										break;
								}
							}
						}
					}
				}
			}
		}

		// ga consolidator
		for (int first : gaPopulationSizeValues) {
			for (int second : gaNrIterationsValues) {
				for (int third : gaNrCrossoversValues) {
					for (double fourth : gaMutationProbValues) {
						for (boolean fifth : doLocalSearch1Values) {
							for (boolean sixth : doLocalSearch2Values) {

								// if both values are true, we skip this run, because only ls1 would be taken
								if(fifth && sixth) {
									continue;
								}	
								for (double seventh : lowerThresholdValues) {
									setGaProperties(
											gaPopulationSizeValues.get(gaPopulationSizeValues.indexOf(first)).toString(),
											gaNrIterationsValues.get(gaNrIterationsValues.indexOf(second)).toString(),
											gaNrCrossoversValues.get(gaNrCrossoversValues.indexOf(third)).toString(),
											gaMutationProbValues.get(gaMutationProbValues.indexOf(fourth)).toString(),
											doLocalSearch1Values.get(doLocalSearch1Values.indexOf(fifth)).toString(),
											doLocalSearch2Values.get(doLocalSearch2Values.indexOf(sixth)).toString(),
											lowerThresholdValues.get(lowerThresholdValues.indexOf(seventh)).toString(),
											true);

									if (!fifth) // if no local search -> value of lowerThreshold plays no role -> there is
										// no point in testing more than one value
										break;
								}
							}
						}
					}
				}
			}
		}

		// abc consolidator
		for (int first : abcPopulationSizeValues) {
			for (int second : abcNrIterationsValues) {
				for (int third : abcLimitTrialsValues) {
					for (double fourth : abcMutationProbValues) {
						for (boolean fifth : doLocalSearch1Values) {
							for (boolean sixth : doLocalSearch2Values) {

								// if both values are true, we skip this run, because only ls1 would be taken
								if(fifth && sixth) {
									continue;
								}	
								for (double seventh : lowerThresholdValues) {
									setAbcProperties(
											abcPopulationSizeValues.get(abcPopulationSizeValues.indexOf(first)).toString(),
											abcNrIterationsValues.get(abcNrIterationsValues.indexOf(second)).toString(),
											abcLimitTrialsValues.get(abcLimitTrialsValues.indexOf(third)).toString(),
											abcMutationProbValues.get(abcMutationProbValues.indexOf(fourth)).toString(),
											doLocalSearch1Values.get(doLocalSearch1Values.indexOf(fifth)).toString(),
											doLocalSearch2Values.get(doLocalSearch2Values.indexOf(sixth)).toString(),
											lowerThresholdValues.get(lowerThresholdValues.indexOf(seventh)).toString(),
											true);
									if (!fifth) // if no local search -> value of lowerThreshold plays no role -> there is
										// no point in testing more than one value
										break;
								}
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Setter for the constant values of the pso algorithm.
	 * 
	 * @param swarmSize
	 *            	This value defines the amount of particles.
	 * @param iterations
	 *            	This value defines the number of iterations.
	 * @param c1
	 *            	This value defines the first learning factor.
	 * @param c2
	 *            	This value defines the second learning factor.
	 * @param doLocalSearch1
	 * 			  	If activated, Solution.improve() is additionally used.
	 * @param doLocalSearch2
	 * 			  	If activated, Solution.simpleConsolidatorImprove() is additionally used.
	 * @param lowerThreshold
	 * 			  	The value used for the lowerThreshold.
	 * @param noWrite
	 * 			  	Determines if the properties shall be saved.
	 */
	private void setPsoProperties(String swarmSize, String iterations, String c1, String c2, String doLocalSearch1,
			String doLocalSearch2, String lowerThreshold, boolean noWrite) {
		setBaseProps(lowerThreshold, swarmSize, iterations, doLocalSearch1, doLocalSearch2,"0.2");
		props.setProperty("psoC1", c1);
		props.setProperty("psoC2", c2);

		this.saveProps(PsoConsolidator.class.getName(), noWrite);
	}

	/**
	 * Setter for the constant values of the abc algorithm.
	 * 
	 * @param populationSize
	 *            	This value defines the amount of individuals in the population.
	 * @param iterations
	 *            	This value defines the number of iterations.
	 * @param limitTrials
	 *            	This value defines the maximum number of trials for improvement
	 *            	before a solution is abandoned.
	 * @param mutationProb
	 * 			  	The value for mutationProb.
	 * @param doLocalSearch1
	 * 			  	If activated, Solution.improve() is additionally used.
	 * @param doLocalSearch2
	 * 			  	If activated, Solution.simpleConsolidatorImprove() is additionally used.
	 * @param lowerThreshold
	 * 			  	The value used for the lowerThreshold.
	 * @param noWrite
	 * 			  	Determines if the properties shall be saved.
	 */
	private void setAbcProperties(String populationSize, String iterations, String limitTrials, String mutationProb,
			String doLocalSearch1, String doLocalSearch2, String lowerThreshold, boolean noWrite) {
		setBaseProps(lowerThreshold, populationSize, iterations, doLocalSearch1, doLocalSearch2, mutationProb);
		props.setProperty("abcLimitTrials", limitTrials);
		this.saveProps(AbcConsolidator.class.getName(), noWrite);
	}

	/**
	 * Setter for the constant values of the ga algorithm.
	 * 
	 * @param populationSize
	 *           	This value defines the amount of individuals in the population.
	 * @param iterations
	 *            	This value defines the number of iterations.
	 * @param crossovers
	 *            	This value defines the number of recombinations to perform in each generation.
 	 * @param mutationProb
	 * 			  	The value for mutationProb.
	 * @param doLocalSearch1
	 * 			  	If activated, Solution.improve() is additionally used.
	 * @param doLocalSearch2
	 * 			  	If activated, Solution.simpleConsolidatorImprove() is additionally used.
	 * @param lowerThreshold
	 * 			  	The value used for the lowerThreshold.
	 * @param noWrite
	 * 				Determines if the properties shall be saved.
	 * 			  
	 */
	private void setGaProperties(String populationSize, String iterations, String crossovers, String mutationProb,
			String doLocalSearch1, String doLocalSearch2, String lowerThreshold, boolean noWrite) {
		setBaseProps(lowerThreshold, populationSize, iterations, doLocalSearch1, doLocalSearch2, mutationProb);
		props.setProperty("gaNrCrossovers", crossovers);

		this.saveProps(GaConsolidator.class.getName(), noWrite);
	}
	
	private void setBaseProps(final String lowerThreshold, final String populationSize, final String iterations,
			String doLocalSearch1, String doLocalSearch2, String mutationProb) {
		props.setProperty("lowerThreshold", lowerThreshold);
		props.setProperty("populationSize", populationSize);
		props.setProperty("nrIterations", iterations);
		props.setProperty("doLocalSearch1", doLocalSearch1);
		props.setProperty("doLocalSearch2", doLocalSearch2);
		props.setProperty("mutationProb", mutationProb);
	}

	/**
	 * Saves the properties in the data after changing them.
	 * 
	 * @param consType
	 * 				The type of the consolidator.
	 * @param noWrite
	 * 				If true, the properties do not get stored.
	 */
	private void saveProps(String consType, boolean noWrite) {
		if (noWrite)
			return;
		try {
			FileOutputStream fileOutput = new FileOutputStream(
					new File(consType + "-consolidationProperties" + propCounter++ + ".xml"));
			props.storeToXML(fileOutput, null);
			fileOutput.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
