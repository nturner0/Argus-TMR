# Single-event upset testing on TMR Rocket

**All 7,110 single-bit cases passed** in the register sweep on the generated `RocketConfig` RTL. Two directed tests also passed. They show what happens across a particular clock edge when a valid program counter or instruction queue entry must retain its value.

| Simulation | Injection sites and workload | Observed result |
|---|---|---|
| Bit-by-bit sweep | Every bit of A, B and C for the 310 identified fields across Rocket EX/MEM/WB, Frontend, IBuf and TMRShiftQueue | 7,110 passed, 0 failed. All checked outputs matched the fault-free model and all three copies of the target field agreed with it after the next edge. |
| Directed EX/MEM/WB program-counter test | 3 stage PCs × 40 bits × 3 replicas × 2 conditions (hold and advance) during an ADDI instruction stream | 720 passed. Each stage had 120 holds and 120 advances. The stream accepted and retired 625 instructions. A separate two-copy corruption produced the expected wrong majority. |
| Directed fetch-queue test | One bit of one occupied entry while enqueue and dequeue were disabled | The voted instruction remained correct throughout. The corrupted copy recovered on the next edge, and the same instruction was dequeued. |

The PC and queue tests revisit sites in the sweep under explicitly controlled conditions. The results are reported separately so repeated sites are identifiable. [Sweep results by module and field](state-sweep/README.md) · [Pipeline coverage by source declaration](pipeline/coverage.csv)

## Simulation and environment

The hardware came from Daniel's five-file TMR source snapshot at `243fa54bb7f662a60192a7162282e0e4b05bb3d6`. Chipyard elaborated its Chisel into SystemVerilog. Verilator compiled the generated RTL into native C++ models. The testbenches stepped their clocks and flipped storage through Verilator's public read/write interface. The sweep ran four module tops independently, with their RTL submodules. The injected and reference instances used the **same TMR RTL**. The clean upstream Rocket served as the baseline for the separate fault-free ISA regression. [Source hashes](../source-sha256.json) · [ISA regression](../REPORT.md)

| Component | Version or setting |
|---|---|
| Host | Ubuntu 22.04.3 LTS, x86-64, WSL2, Linux `6.18.58.1-microsoft-standard-WSL2` |
| Chipyard and configuration | `371ab92dd09917b6ab05d58bf762b173c52f4224`, `RocketConfig` |
| Rocket source | Local HEAD `7a7c9df514d5adfe6e2cba6f040fe4fcfa5a8a1d` plus the preserved local edits. The five-file snapshot records the tested implementation. |
| RTL generation | Chisel 6.7.0, Scala 2.13.16, sbt 1.8.2, OpenJDK 20.0.2-internal, CIRCT firtool 1.75.0 |
| SEU simulation | Verilator 5.022 (conda-forge build 1), host C++ compiler GCC 15.3.0, GNU Make 4.4.1, Python 3.10.14 for the sweep driver |
| Build inputs | Generated RocketConfig SystemVerilog in `sims/verilator/generated-src/chipyard.harness.TestHarness.RocketConfig/gen-collateral`. The Frontend build also uses the generated cache memory model. |

The SEU benches supply their own port-level stimulus. The RISC-V ELF tests and their wall-time limits belong to the separate ISA run. [Tool version output](../environment.txt) · [Generated RTL checksums](state-sweep/recorded/rtl.sha256)

## What the test code does

| Test code | Role |
|---|---|
| [sweep.py](state-sweep/sweep.py) | Reads register declarations in each generated RTL top, locates the named A/B/C copies (including IBuf's buffer naming), checks equal widths, and checks Rocket's sites against the 62-declaration inventory. Generates C++ accessors and output comparisons, then runs Verilator with `--cc --exe --build --public-flat-rw`. |
| [sweep.cpp](state-sweep/sweep.cpp) | Starts a fresh injected model and a fault-free reference for each bit and replica. Drives identical inputs, deposits a single-bit upset, checks outputs and storage, and writes one CSV row per field. A failing case reports its field, replica, bit and failed check. |
| [campaign.cpp](pipeline/campaign.cpp) | Watches the PC update conditions in the generated Rocket core. Selects a valid stage during a hold or an advance to a different PC. Checks the voted PC, next PC, stored replicas, fetch control, retirement and register-file writeback. |
| [queue_seu.cpp](queue/queue_seu.cpp) | Loads an instruction into queue entry 0, holds it valid, flips bit 0 of replica B and inspects A, B, C and the dequeue output before injection, after injection, after the next edge and at dequeue. |

The HDL already contains the voters and feedback. Only the testbench writes the simulated fault. The unused auxiliary TMR ALU is outside the injection sites. [Rocket pipeline implementation](../../src/main/scala/rocket/RocketCore.scala) · [Queue implementation](../../src/main/scala/rocket/TMRShiftQueue.scala)

## Injection timing and pass criteria

1. In the sweep, reset both instances for four rising edges. Drive a deterministic workload per module: Rocket gets ADDI instructions with fetch gaps, Frontend gets redirects with no cache refill responses, IBuf gets compressed NOPs and backpressure, and the queue gets enqueue/drain cycles.
2. With the clock low, toggle the target bit once using XOR with `uint64_t{1} << bit`. The injection cycle is `12 + (bit + bank) % 12`, where banks A, B and C are numbered 0, 1 and 2. Evaluate the model before raising the clock. Verify that exactly the selected bit differs within the target field and that the two other copies retain the reference value.
3. Compare every module output against the reference before and after injection and after clocking. For Rocket, also compare writeback validity, retired instruction and PC when valid, register-file write enable, and destination and data when a write is enabled.
4. After the first rising edge following the upset, and for six more cycles, require all three copies of the target field to equal the reference. Count a pass only when all checks succeed. The 2,370 logical bit positions across the four tops yield 7,110 individual replica-bit injections. Each receives one flip from its value under that stimulus. [Per-module counts and flip directions](state-sweep/README.md)

**A concrete repair.** In the queue test, all copies initially stored `0x00100093`. After the upset, B read `0x00100092` while A, C and the voted output still read `0x00100093`. With enqueue and dequeue both disabled, the next rising edge restored B to `0x00100093`. Holding the entry rules out a new enqueue as the reason the three copies agreed again. The PC test also deliberately flipped the same bit in two replicas: the vote changed, confirming that the testbench could observe a corrupted majority. [Queue values at each step](queue/recorded/result.txt) · [720 individual PC cases](pipeline/recorded/cases.csv) · [PC run summary](pipeline/recorded/result.txt)

## Reproduce the three campaigns

Start from a checkout of this branch and generated RTL from the matching TMR source. The commands below use the original WSL paths. A temporary directory keeps build products and new results separate from the published evidence. [Chipyard build and source-overlay instructions](../../README.md)

```bash
set -eo pipefail
seu_work=$(mktemp -d /tmp/argus-seu-XXXXXX)
git archive HEAD tests/seu tests/protected-pipeline-state.json | tar -x -C "$seu_work"
cd "$seu_work"
source /home/dkhalkha/miniforge3/etc/profile.d/conda.sh
source /home/dkhalkha/chipyard/env.sh
rtl=/home/dkhalkha/chipyard/sims/verilator/generated-src/chipyard.harness.TestHarness.RocketConfig/gen-collateral
(cd "$rtl" && sha256sum -c "$seu_work/tests/seu/state-sweep/recorded/rtl.sha256")

bash tests/seu/queue/run.sh
bash tests/seu/pipeline/run.sh
python3 tests/seu/state-sweep/sweep.py "$rtl" "$seu_work/sweep" | tee sweep-summary.txt

cmp tests/seu/queue/result.log tests/seu/queue/recorded/result.txt
cmp tests/seu/pipeline/result.log tests/seu/pipeline/recorded/result.txt
cmp tests/seu/pipeline/cases.csv tests/seu/pipeline/recorded/cases.csv
cmp sweep-summary.txt tests/seu/state-sweep/recorded/result.txt
for top in Rocket Frontend IBuf TMRShiftQueue; do
    cmp "sweep/$top/coverage.csv" "tests/seu/state-sweep/recorded/$top.csv"
done
```

The shell and Python drivers propagate simulation failures. The `cmp` commands check byte-for-byte agreement with the recorded outputs. The sweep writes build logs, a summary and coverage CSV under `sweep/<module>/`. Each directed test writes its build and run logs in its own test directory. On another installation, adapt the activation and generated RTL paths in the shell commands and both directed test scripts.

**Reproduction check, November 18, 2026.** All three campaigns were rebuilt from the published test sources at `295fc3f356e0bda2996b3c0d1fb5651e36d6a3b8`, in a fresh temporary directory. All 25 generated RTL input hashes matched. The queue trace, PC summary, all 720 PC case records, sweep summary and four field-level CSVs reproduced byte for byte.

## What the results establish

The sweep established next-edge agreement for each identified replica bit under the tested workloads. An ordinary write can also overwrite a corrupted replica. The directed hold cases specifically demonstrate correction feedback in the three PCs and the held queue entry. Remaining questions include feedback during holds at other live registers, exceptions and replay under faults, and full ISA programs running with injected faults. Physical radiation response would require a separate measurement. [SEU sweep limitations](state-sweep/README.md) · [30-test fault-free comparison](../comparison.md)
