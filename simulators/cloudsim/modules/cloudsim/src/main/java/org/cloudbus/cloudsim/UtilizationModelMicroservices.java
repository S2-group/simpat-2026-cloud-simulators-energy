package org.cloudbus.cloudsim;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.UtilizationModel;

public class UtilizationModelMicroservices implements UtilizationModel {
    private final String service;

    private final ArrayList<Double> data;

    private double schedulingInterval;

    /*
     * Instantiates a new Microservice resource utilization model from a trace file.
     *
     * @param system The systen (ts = train ticket, ss = sock shop).
     * @param service The microservice.
     * @param scenario The experiment scenario (A or B).
     * @param users The number of users (100 or 1000)
     * @param repetition The repetitiion (1 to 30),
     * @throws NumberFormatException the number format exception
	 * @throws IOException Signals that an I/O exception has occurred.
     */
    public UtilizationModelMicroservices(String system, String service, String scenario, int users, int repetitiion, int n_procs)
        throws NumberFormatException, IOException {
            data = new ArrayList<>();
            this.service = service;

            if (n_procs == 0) {
                data.add(0.0);
                return;
            }

            double schedulingInterval = 5; // We have utilization data every 5 seconds.
            setSchedulingInterval(schedulingInterval); //
            String tracePath = "../../traces/" + String.format("%s/%s/%d/%d/services/%s_cpu_usage.data", system, scenario, users, repetitiion, service);
            // // // Log.printlnConcat("DEBUG (UtilizationModelMicroservices): tracePath=", tracePath);
            BufferedReader input = new BufferedReader(new FileReader(tracePath));
            String line;
            while ((line=input.readLine()) != null) {
                data.add(Double.parseDouble(line.trim()) / n_procs);
            }
            input.close();
        };

    @Override
    public double getUtilization(double time) {
        // Get weighted utilization, source: UtilizationModelPlanetLabMemory
        int iBefore = (int) Math.floor(time / getSchedulingInterval());
        if (iBefore >= data.size() - 1) {
            return data.get(data.size()-1);
        }

        if (time % getSchedulingInterval() == 0) { // direct hit, can just return
            return data.get((int) time/ (int) getSchedulingInterval());
        }

        int iAfter = (int) Math.ceil(time / getSchedulingInterval());
        double utilizationDelta = (data.get(iAfter) - data.get(iBefore)) / ((data.get(iBefore) - data.get(iAfter)) * getSchedulingInterval());
        return data.get(iBefore) + utilizationDelta * (time - iBefore * getSchedulingInterval());
    }

    /**
	 * Sets the scheduling interval.
	 *
	 * @param schedulingInterval the new scheduling interval
	 */
	public void setSchedulingInterval(double schedulingInterval) {
		this.schedulingInterval = schedulingInterval;
	}

    /**
	 * Gets the scheduling interval.
	 *
	 * @return the scheduling interval
	 */
	public double getSchedulingInterval() {
		return schedulingInterval;
	}

}
