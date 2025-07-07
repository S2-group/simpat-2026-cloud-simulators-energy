package hu.mta.sztaki.lpds.cloud.simulator.examples.vmallocmultitenant;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.InvalidPropertiesFormatException;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import hu.mta.sztaki.lpds.cloud.simulator.examples.vmallocmultitenant.Request.Type;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;

	/**
	 * This class is similar to the consolidation controller. It creates
	 * requests, the component types which shall be used and manages the 
	 * different schedulers and the consolidator.
	 * 
	 * TODO for improvement:
	 * - finish setUpInfrastructure
	 * - finish doOneRound
	 * 
	 * @author Rene Ponto 
	 */
public class MultiTenantController {
	
	/** Contains the values for doing one round.*/
	private Properties options;
	private IaaSService toConsolidate;
	public MultiTenantComponentScheduler componentScheduler;
	public MultiTenantConsolidator consolidator;
	
	private boolean logging;
	private String pmValues;
	private int normalPmNum;
	private int securePmNum;
	private String requestSource;
	
	/** The existing tenants. */
//	private String[] tenants = new String[]{"A","B","C"};

	/** Used for creating all necessary component types. */
	private HashMap<String, ArrayList<String>> initialCompTypes;
	
	/** Used for creating all necessary requests. */
	private List<Request> initialRequests;
	Random random;
	
	public MultiTenantController() {
		random = new Random(123);
		try {
			readOptions();
		} catch (InvalidPropertiesFormatException e) {
			throw new RuntimeException("The formate of the options file is wrong.", e);
		} catch (IOException e) {
			throw new RuntimeException("An IOException occured while reading the options.", e);
		}
		
		initialCompTypes = new HashMap<String, ArrayList<String>>();
		initialRequests = new ArrayList<Request>();
		
		main(null); 	// invoke the main method
	}
	
	public void main(int[] args) {	
		
		if(requestSource == "file") {
			// init comptypes and requests for the different schedulers
			readCompTypes();
			int amountOfRequests = readRequests();
			doOneRound(amountOfRequests);
		}
		else {
			// init comptypes and requests for the different schedulers
			readGWFFile();
			int amountOfRequests = generateRequests();
			doOneRound(amountOfRequests);
		}
	}
	
	private void readOptions() throws InvalidPropertiesFormatException, IOException {
		// path to the options
		Path path = Paths.get("src/main/java/hu/mta/sztaki/lpds/cloud/simulator/examples/vmallocmultitenant/options.xml");
		
		options = new Properties();
		File file = new File(path.toString());
		FileInputStream fileInput = new FileInputStream(file);
		options.loadFromXML(fileInput);
		fileInput.close();
		
		// set the actual values
		logging = Boolean.parseBoolean(options.getProperty("logging"));
		pmValues = options.getProperty("pmValues");
		normalPmNum = Integer.parseInt(options.getProperty("normalPmNum"));
		securePmNum = Integer.parseInt(options.getProperty("securePmNum"));
		requestSource = options.getProperty("requestSource");
		
		// close the inputstream
		FileOutputStream fileOutput = new FileOutputStream(file);
		options.storeToXML(fileOutput, null);
		fileOutput.close();
	}
	
	/**
	 * Reads the comptypes.txt file to set the different component types inside the MultiTenantPMScheduler for
	 * initialising this scheduler to work properly. Otherwise there would be problems with arriving requests
	 * of those component types. The path is set relative, so there are no problems on different platforms.
	 */
	public void readCompTypes() {
		BufferedReader reader;
		try {
			Path path = Paths.get("src/main/java/hu/mta/sztaki/lpds/cloud/simulator/examples/vmallocmultitenant/comptypes.txt");			
			reader = new BufferedReader(new FileReader(path.toString()));
			
			String line = null;         
		    while ((line = reader.readLine()) != null) {
		    	if(line.startsWith("#"))
					continue;
		    	
		    	// we use the whitespace to seperate the different values
		    	String[] strings = line.split(" ");
		    	ArrayList<String> list = new ArrayList<String>();
		    	
		    	list.add(strings[1]);
		    	list.add(strings[2]);
		    	list.add("0.001");		// processing power
		    	list.add(strings[3]);
		    	list.add(strings[4]);		    	
		    	
		    	initialCompTypes.put(strings[0], list);
		    }
		    reader.close();
		    MultiTenantComponentScheduler.instantiateTypes(initialCompTypes);
		} catch (FileNotFoundException e) {
			throw new RuntimeException("This path is not leading to the wanted file.", e);
		} catch (IOException e) {
			e.printStackTrace();
		} 
		
	}

	/**
	 * Reads the manually defined requests out of the requests.txt file to safe them in order to
	 * start one simulation. The path is set relative, so there are no problems on different 
	 * platforms. 
	 *  
	 * @return The total amount of requests.
	 */
	@SuppressWarnings("resource")
	public int readRequests() {
		int nrOfRequests = 0;
		
		BufferedReader reader;
		try {
			Path path = Paths.get("src/main/java/hu/mta/sztaki/lpds/cloud/simulator/examples/vmallocmultitenant/requests.txt");			
			reader = new BufferedReader(new FileReader(path.toString()));			
			
			String line = null;         
		    while ((line = reader.readLine()) != null) {
		    	if(line.startsWith("#"))
					continue;
		    	
		    	nrOfRequests++;		//increase the counter
		    	
		    	// we use the whitespace to seperate the different values
		    	String[] strings = line.split(" ");
		    	
		    	// now the string array above is used to create a new request
		    	String tenant = strings[0];
		    	String componentTypeName = strings[1];
		    	ComponentType ctype = null;
		    	for(ComponentType type: MultiTenantComponentScheduler.getTypes()) {
		    		if(MultiTenantComponentScheduler.getTypes().isEmpty())
		    			throw new NullPointerException("There are no types inside the pm scheduler!");
		    		
		    		// with this expression we can determine the component type to use
		    		if(type.getName().equals(componentTypeName)) {
		    			ctype = type;
		    			break;
		    		}
		    	}
		    	if(ctype == null) {
		    		throw new NullPointerException("There is no such ComponentType as " + componentTypeName + "!");
		    	}		    	
		    	
		    	boolean crit = Boolean.getBoolean(strings[2]);
		    	boolean custom = random.nextBoolean();		// we decide this factor randomly
		    	boolean supportsSecureEnclaves = random.nextBoolean();		// we decide this factor randomly
		    	
		    	double startTime = Double.parseDouble(strings[3]);
		    	double duration = Double.parseDouble(strings[4]);
		    	
		    	double first = Double.parseDouble(strings[5]);
				double second = 0.001;		// processing power
				long third = Long.parseLong(strings[6]);
				
				// a new ResourceVector is created for passing it to the new request
		    	
		    	Request req = new Request(tenant, ctype, new ConstantConstraints(first, second, third), crit, custom, supportsSecureEnclaves, startTime, duration, Type.NEW_REQUEST);
			    initialRequests.add(req);
		    			    	
		    }
		    reader.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException("This path is not leading to the wanted file.", e);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return nrOfRequests;
	}
	
	// not necessary at the moment
	private int generateRequests() {
		int nrOfRequests = 0;
		return nrOfRequests;
	}
	
	// not necessary at the moment
	private int readGWFFile() {
		int jobsProcessed = 0;
		return jobsProcessed;
	}
	
	/**
	 * This method sets up an IaaS according to the values in the options to work with.
	 * @throws SecurityException 
	 * @throws NoSuchMethodException 
	 * @throws InvocationTargetException 
	 * @throws IllegalArgumentException 
	 * @throws IllegalAccessException 
	 * @throws InstantiationException 
	 */
	private void setUpInfrastructure() throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		String[] values = pmValues.split(" ");
		
		// set up the IaaS and the necessary schedulers
		toConsolidate = new IaaSService(MultiTenantVMScheduler.class, MultiTenantPMScheduler.class);
		componentScheduler = new MultiTenantComponentScheduler(toConsolidate);
		
		final ResourceConstraints pmConstraints = new ConstantConstraints(Double.parseDouble(values[0]), Double.parseDouble(values[1]), 
				Long.parseLong(values[2]));
		for(int i = 0; i < normalPmNum; i++) {
			//create pm
		}
		for(int j = 0; j < securePmNum; j++) {
			//create pm
		}
		
		//TODO
	}
	
	/**
	 * Creates at first an Infrastructure with the properties of the options file to work with. After
	 * that, all requests are going to get processed and the results are printed on the screen.
	 * @param amountOfRequests
	 */
	private void doOneRound(int amountOfRequests) {		
		try {
			setUpInfrastructure();
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
		}
		
		long beforeSimu;
		beforeSimu = Calendar.getInstance().getTimeInMillis();
		System.err.println("Simulation started at: " + beforeSimu);
		
		int processedRequests = 0;
		boolean changed = true;

		int counter = 0;
		while(initialRequests.get(counter) != null && processedRequests  < amountOfRequests) {
			Request actual = initialRequests.get(counter);
			switch(actual.getType()) {
			
			case NEW_REQUEST : 
				componentScheduler.processRequest(actual, actual.getComponentType(), actual.isCrit());
				break;
			case TERMINATE_REQUEST :
				componentScheduler.terminateRequest(actual, actual.getHost());
				break;
			case REOPTIMIZATION :
				if(changed) {
					consolidator = new MultiTenantConsolidator(toConsolidate, 0, componentScheduler.getMapping());
					consolidator.doConsolidation(toConsolidate.machines.toArray(new PhysicalMachine[toConsolidate.machines.size()]));
					break;
				}
				else {
					System.err.println("Nothing has changed after last reoptimization, action is not necessary");
					break;
				}
			}
			processedRequests++;
			initialRequests.remove(actual);
		}
		
		System.err.println("All requests are processed.");
		
		if(logging) {
			//TODO statistics
//			if(Options::get_option_value("sequence")=="on")
//				Statistics::write_detailed_csv("sequence_"+config_name+".csv");
			long afterSimu = Calendar.getInstance().getTimeInMillis();
			System.err.println("Time that elapsed in the simulation is: " + (afterSimu - beforeSimu));
			System.err.println("Number of reoptimizations: " + MultiTenantConsolidator.reoptimizations);
		}
				
	}
}
