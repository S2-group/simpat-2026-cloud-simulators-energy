# R1.4 - Verification of RQ2 Assessments

---

## A. RQ2.1 — Ease of use: Simulation process

### A1 — GUI availability

- [x] **A1**
  - Verdict: PARTLY
  - Notes:
    - OpenDC: OpenDC still ships with a GUI, but yes, it still cannot really
      do the kind of network topology that Meret mentions. (AGREE)
    - CloudSim: The two tools she mentions are like 9-10 years old.
      I haven't tried them, but I would guess that they no longer work with
      the most recent version of cloudsim which released last year. I dont
      think these are viable alternatives for a GUI these days. (DISAGREE)
  - Arbiter:

- Meret:
  - Only OpenDC has a GUI (a web application). It is very limited: predefined
    topology elements only (CPU, GPU, memory, …), only one predefined workload →
    GUI unsuitable for microservice systems.
  - CloudSim itself has no GUI, but several simulators built on the CloudSim
    engine do (CloudReports, CloudAnalyst).
- What I Checked: https://github.com/atlarge-research/opendc,
  https://github.com/vondrt4/CloudAnalyst-interactive,
  https://github.com/thiagotts/CloudReports
- Paper ref: `results.tex` RQ2.1 ¶1.

### A2 — Energy monitoring & output, per simulator

- [x] **A2**
  - Verdict: PARTLY
  - Notes:
    - CloudSim: It actually logs energy not power. Also its maximum frequency is not 1Hz. Actually it can
      log at any frequency. Its measured in seconds, but you can specify e.g. 0.1s.
      Cloudsim also does offer some terminal output by default, but yes you can also add to it.
    - DISSECT-CF: AGREE
    - OpenDC: It technically writes both power_draw, and both energy_usage to its output.
      Also energy_usage is in joules, the evaluation assumes watts here (i think).
      Ill run the adjusted calculations to see if there is a difference.
    - SimGrid: On the energy per host point: "only via user code" is a single call
      to sg_host_energy_plugin_init().
  - Arbiter:

- Meret:
  - All five simulators can retrieve consumed energy at a user-defined frequency
    (e.g. every second).
  - CloudSim & NetworkCloudSim: log power per host and per datacenter, write total
    energy (J) to CSV; sampling in seconds → max 1 Hz; terminal output largely
    user-defined.
  - DISSECT-CF: the user must implement energy monitoring via the provided
    `EnergyMeter` classes; tick-based time granularity is user-defined; terminal
    output user-defined.
  - OpenDC: writes host power (W) to parquet files, max sampling frequency 1 s;
    terminal output very limited.
  - SimGrid: can retrieve total energy per host, but only via user-implemented code.
- What I Checked: The source code for all of the of simulators
- Paper ref: `results.tex` RQ2.1 ¶2.

---

## B. RQ2.1 — Ease of use: Documentation

### B1 — Limited documentation: CloudSim family and DISSECT-CF

- [x] **B1**
  - Verdict: AGREE
  - Notes: The docs for both of these are very limited, essentially javadoc
    with a bit more description in some places. I would categorize DISSECT-CF as
    having the worse documentation of the two.
  - Arbiter:

- Meret:
  - CloudSim, being very widely used, has a relatively wide range of documentation
    and examples; distribution docs generally up to date but not very descriptive
    (e.g. units missing in in-code docs, such as storage size, bandwidth).
  - For the power and network capabilities, CloudSim's documentation is less
    extensive.
  - NetworkCloudSim: same as CloudSim — sparse, not very descriptive.
  - DISSECT-CF: documentation is quite little, but an examples repo exists and was
    used for this study; the lack of clear/extensive docs may steepen the learning
    curve.
- What I Checked: Source code included in the project. http://cloudbus.org/cloudsim/, https://kecskemeti.github.io/dissect-cf/
- Paper ref: `results.tex` Documentation ¶.

### B2 — Extensive documentation: OpenDC and SimGrid; overall sufficiency

- [x] **B2**
  - Verdict: PARTLY
  - Notes:
    - I would actually argue that while OpenDC's documentation is certainly more
      modern and accessible than CloudSim and dissect-cf, its actually still quite limited.
      Not much more than a few examples and function definitions.
    - I actually liked SimGrids docs the most. They had diagrams and written explanations of concepts.
      Almost a bit too verbose, but thats OK in docs imo.
    - This part is a bit subjective. Both OpenDC and SimGrid have sufficient documentation I would say.
    - Also I think OpenDC documentation was moved from its previous location, and updated? It seems up to date.
      There is now OpenDC2 and OpenDC3
  - Arbiter:

- Merges: old B5 (OpenDC), B6 (SimGrid vs OpenDC), B7 (overall sufficiency).
- Meret:
  - OpenDC has external documentation that is clear and thorough.
  - SimGrid's external docs are significantly more extensive and up to date than
    OpenDC's.
  - All five simulators have some form of documentation, minimally sufficient.
- What I Checked: Source code included in the project. https://opendc.org/learn/intro, https://simgrid.org/doc/latest
- Paper ref: `results.tex` Documentation ¶.

---

## C. RQ2.2 — Configurability

### C1 — Topology definition: customizability vs. effort

- [x] **C1**
  - Verdict: MOSTLY AGREE
  - Notes: A few minor adjustments to wording to be more clear/accurate.
    - For DISSECT-CF, I would not say its more complex. Its just more detailed,
      you can specify a lot of hardware details.
    - I would say DISSECT-CF also needs policies, but CloudSim requires more, but
      does offer simple defaults.
    - I would qualify that their definitions are the simplest, because they are modeling
      the least. They do not have as many features, and model as many components as the
      other sims.
  - Arbiter:

- Meret:
  - DISSECT-CF offers the most settable topology parameters (table
    `tab:topologyParams`): most customizable, but defining a topology is more
    complex.
  - CloudSim and NetworkCloudSim require many policies (VM schedulers, host
    allocation) to be defined; simple defaults are provided and user-definable.
  - SimGrid (XML, or code) and OpenDC (JSON) offer the simplest topology
    definitions.
- What I Checked: Meret's code written for each simulator, some other included examples, the source code.
- Paper ref: `results.tex` RQ2.2 ¶1.

### C2 — IPC-based CPU speed definition

- [x] **C2**
  - Verdict: MOSTLY AGREE
  - Notes:
    - SimGrid actually measures with FLOPS, which is not necessarily a per
      cycle instruction measure, maybe worth qualifying.
    - I would also qualify the "in principle" part. Its more accurate if the workload
      is defined in instructions right? Our workloads are defined as %cpu usage for n seconds.
      And SimGrid only works in FLOPs anyways.
  - Arbiter:

- Meret: CloudSim, NetworkCloudSim, DISSECT-CF and SimGrid define host CPU speed via
  IPC variants — context-dependent (OS, apps), must be measured per configuration;
  more accurate in principle but harder to obtain.
- What I Checked: Source code
- Paper ref: `results.tex` RQ2.2 ¶1.

### C3 — Workload input: trace files vs. code

- [x] **C3**
  - Verdict: PARTLY
  - Notes:
    - CloudSim: AGREE
    - DISSECT-CF: AGREE, but also supports one2 (not mentioned in paper)
    - OpenDC: DISAGREE. The parquet only part applies only to the OpenDCExperimentRunner.
      Technically the full OpenDC API it does bundle parsers for other trace formats.
    - SimGrid: PARTLY. Has a kind of trace replay framework, but it does require the user to do
      some work setting up callbacks to invoke on different parts of the trace.
  - Arbiter:

- Meret:
  - CloudSim provides swf loading but it is flawed: the *submit time* field is
    disregarded, so all jobs enter at simulation start.
  - DISSECT-CF's trace reader is more functional: supports gwf, swf, srtg.
  - OpenDC workloads cannot be defined in code (parquet files only).
  - SimGrid workloads are code-only (trace loading is DIY); DAX/DOT loading exists
    but is DAG-only → irrelevant for microservices.
- What I Checked: Again the source code
- Paper ref: `results.tex` RQ2.2 ¶2; `tab:workloadParams`;
  `experimentExecution.tex` CloudSim subsection.

### C4 — Workload model semantics

- [x] **C4**
  - Verdict: MOSTLY AGREE
  - Notes:
    - OpenDC Claim: AGREE
    - SimGrid Claim: AGREE, but its actually disk I/O consumption, not memory.
    - Others Claim: CloudSim is also a "discrete event simulator", the other claims are fine.
  - Arbiter:

- Meret:
  - OpenDC uses two parquet files per workload (jobs + CPU usage snapshots), which
    suits microservices (services as jobs, periodic CPU per job); but memory is
    job-level only (less precise).
  - A SimGrid workload consists of computation duration, memory consumption, or
    message size, each defined separately.
  - CloudSim/NetworkCloudSim/DISSECT-CF workload definitions look similar but differ
    fundamentally: DISSECT-CF is a discrete event simulator with user-defined time
    granularity (ticks), and treats communication as resource consumption rather
    than messages passed between entities.
- Paper ref: `results.tex` RQ2.2 ¶2.

---

## Outcome totals

- FULLY AGREE:                1
- AGREE WITH QUALIFICATIONS:  3
- PARTLY AGREE:               4
- FULLY DISAGREE:             0

---
