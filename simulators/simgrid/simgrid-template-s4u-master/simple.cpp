
#include <iostream>
#include <fstream>
#include <sstream>
#include <vector>
#include <string>
#include <filesystem>
#include <simgrid/s4u.hpp>
#include "simgrid/plugins/energy.h"


XBT_LOG_NEW_DEFAULT_CATEGORY(sample_simulator, "Messages specific for this simulator");
namespace sg4 = simgrid::s4u;

char *test_system, *scenario, *users, *repetition, *run;

struct ServiceProperties {
    std::string name;
    int cpu_count;
    int mem;
};

std::vector<ServiceProperties> read_properties(const std::string &filename) {
    std::vector<ServiceProperties> data;
    std::ifstream file(filename);

    std::string line;
    while (std::getline(file, line)) {
        std::vector<std::string> row;
        std::stringstream ss(line);
        std::string cell;

        while (std::getline(ss, cell, ' ')) {
            row.push_back(cell.c_str());
        }

        struct ServiceProperties props = {row[0], std::stoi(row[1]), std::stoi(row[2])};

        data.push_back(props);
    }

    file.close();
    return data;
}


std::vector<double> readTrace(const std::string &filename) {
    std::vector<double> data;
    std::ifstream file(filename);

    if (!file.is_open()) {
        std::cerr << "Failed to open file: " << filename << std::endl;
        return data;
    }

    std::string line;
    while (std::getline(file, line)) {
        line.erase(std::remove_if(line.begin(), line.end(), ::isspace), line.end());
        data.push_back(std::stod(line.c_str()));
    }

    file.close();
    return data;
}

static void run_traces(int sample_time, std::vector<double> data) {
    double start, host_speed, flop_amount, run_time, cpu_frac;
    sg4::Host* host = sg4::this_actor::get_host();
    for (double d : data) {
        // d is the fraction of cpu usage from 0 to 16 over the service's cpus.
        d = std::min(d, (double) host->get_core_count());
        start = sg4::Engine::get_clock();
        host_speed = host->get_speed();
        cpu_frac = d/host->get_core_count();
        flop_amount = sample_time*cpu_frac*host_speed;
        XBT_INFO("Run a computation of %.0E flops, should run for %.3f seconds", flop_amount, sample_time*cpu_frac);
        sg4::this_actor::execute(flop_amount);
        run_time = sg4::Engine::get_clock() - start;
        XBT_INFO(
            "Computation done (duration: %.2f s, cpu_load given: %.5f). Current peak speed=%.0E flop/s; Current consumption: from %.0fW to %.0fW"
            " depending on load; Energy dissipated=%.0f J",
            run_time, d, host->get_speed(), sg_host_get_wattmin_at(host, host->get_pstate()),
            sg_host_get_wattmax_at(host, host->get_pstate()), sg_host_get_consumed_energy(host));
        sg4::this_actor::sleep_for(sample_time - run_time);
    }
}

static void service_run() {
    std::ostringstream oss;
    oss << "../../../../traces/" << test_system << "/" << scenario << "/" << users << "/" << repetition << "/services/" << sg4::this_actor::get_cname() << "_cpu_usage.data";
    std::vector<double> data = readTrace(oss.str());

    run_traces(5, data);
    return;
}

static void monitor_energy() {
    double start = simgrid::s4u::Engine::get_clock();
    double last = start;
    double total_elapsed = 0;
    double since_last, energy;

    std::ostringstream oss;
    oss << "../../outputs/simple/" << test_system << "/" << scenario << "/" << users << "/" << repetition << "/" << run;
    std::filesystem::create_directories(oss.str());
    std::ofstream out(oss.str() + "/energy.data");

    XBT_INFO("outstream created");

    sg4::Host* host1 = simgrid::s4u::Host::by_name("host1");

    out << "t,total_energy\n";

    while (true) {
        total_elapsed = simgrid::s4u::Engine::get_clock() - start;
        if (total_elapsed > 300) {
            return;
        }

        energy = sg_host_get_consumed_energy(host1);

        // Write to file
        out << total_elapsed << "," << energy << "\n";
        out.flush();

        since_last = simgrid::s4u::Engine::get_clock() - last;
        sg4::this_actor::sleep_for(1 - since_last);
        last = simgrid::s4u::Engine::get_clock();
    }
}

int main(int argc, char* argv[]) {
    sg_host_energy_plugin_init();
    simgrid::s4u::Engine e(&argc, argv); // contains all the main functions of the simulation.

    // Load platform from file
    xbt_assert(argc == 6, "Usage: %s <tested_system> <scenario> <users> <repetition> <run>\n\tExample: %s sockshop A 100 1 1", argv[0], argv[0]);
    test_system = argv[1];
    scenario = argv[2];
    users = argv[3];
    repetition = argv[4];
    run = argv[5];
    e.load_platform("../../../../traces/platform.xml");

    std::vector<ServiceProperties> properties = read_properties(std::string("../../../../traces/") + test_system + "/" + scenario + "/" + users + "/" + repetition + "/services-overview.data");
    std::sort(properties.begin(), properties.end(), [](ServiceProperties& a, ServiceProperties& b) {
        return a.cpu_count > b.cpu_count;
    });


    for (ServiceProperties props : properties) {
        e.add_actor(props.name.c_str(), e.host_by_name("host1"), service_run);
    }

    e.add_actor("energy_monitor", e.host_by_name("host3"), monitor_energy);

    e.run();

    XBT_INFO("Total simulation time: %.3f", e.get_clock());

    return 0;
}
