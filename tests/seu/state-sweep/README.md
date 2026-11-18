# Single-bit upset sweep on RocketConfig

All 7,110 single-bit injections passed on the generated RocketConfig RTL. Each case flipped one bit in one replica. The affected field's three replicas matched the fault-free model after the next rising edge in every case.

| Module | Protected declarations or aggregates | Fields in one replica | Single-bit cases | Failed |
|---|---:|---:|---:|---:|
| Rocket EX/MEM/WB | 62 | 160 | 3,534 | 0 |
| Frontend fetch state | 19 | 46 | 915 | 0 |
| Instruction buffer (IBuf) | 3 | 24 | 591 | 0 |
| Five-entry fetch queue (TMRShiftQueue) | 2 | 80 | 2,070 | 0 |
| **Total** | **86** | **310** | **7,110** | **0** |

For example, the 40-bit first-stage fetch PC contributes 120 cases, one per bit in A, B and C. The inventory counts a bundle as one declaration and each of its emitted scalar or vector-word members as one field. The [raw summaries](recorded/result.txt) and per-field results for [Rocket](recorded/Rocket.csv), [Frontend](recorded/Frontend.csv), [IBuf](recorded/IBuf.csv) and [TMRShiftQueue](recorded/TMRShiftQueue.csv) record the measured counts. The last two CSV columns count passing injections that began with a one or a zero at the targeted bit.

## How an injection was scored

1. Build each module as a separate Verilator 5.022 simulation. Enumerate the A/B/C registers in its generated SystemVerilog and check that the three copies have equal width. For Rocket, match the names against the 62-declaration source inventory.
2. Start a fresh injected model and fault-free model of the same TMR RTL for each case. Use identical initialization, reset for four rising edges and supply the same input sequence to both. Rocket receives ADDI instructions interspersed with fetch gaps. The frontend receives periodic redirects with the CPU ready and no cache refill responses. The IBuf receives pairs of compressed NOPs with backpressure. The queue receives tagged data during enqueue and drain cycles.
3. Toggle the selected bit in simulated storage with the clock low, during one of cycles 12–23. Evaluate both models and verify that exactly the intended replica and bit differ within that field. Compare every top-level output port with the reference before and after injection and after clocking. On Rocket, also compare writeback-valid, instruction and PC when valid, and integer register-file write enable, destination and data when enabled.
4. After the first rising edge following injection and for six subsequent cycles, require all three copies of the field to match the reference. Count a pass only if every check succeeds.

This covers every bit of each identified replica in these four module tops under the stated stimulus. A normal write can also restore agreement on the next edge. The sweep does not distinguish a hold from an overwrite for each site. The earlier directed tests explicitly selected held, valid PCs and one held, valid queue entry to demonstrate correction feedback. The sweep gives limited functional coverage of exceptions, replay and other infrequently active control paths. Integrated software execution under faults and physical radiation response remain separate measurements. [Directed hold and advance results](../README.md)

## Fetch and decode protection

Rocket distributes the fetch-to-decode handoff across the frontend, the fetch queue and IBuf. The implementation triplicates selected fetch PCs and valid bits, queue entry storage and validity, and buffered instruction data and metadata. A response can also flow through without being stored. Decode derives control signals from the instruction using a combinational circuit. Its output is captured in the triplicated EX control register. A wrong result produced by that shared circuit can reach all three copies. Decode-local state such as `id_reg_pause` and `id_reg_fence` uses ordinary registers. Those bits are outside this TMR sweep. [Frontend](../../../src/main/scala/rocket/Frontend.scala) · [IBuf](../../../src/main/scala/rocket/IBuf.scala) · [RocketCore](../../../src/main/scala/rocket/RocketCore.scala)

## Reproduction

Run from this branch's repository root with the TMR RocketConfig RTL already generated. In the recorded WSL installation the commands were equivalent to the following.

```bash
source /home/dkhalkha/miniforge3/etc/profile.d/conda.sh
source /home/dkhalkha/chipyard/env.sh
rtl=/home/dkhalkha/chipyard/sims/verilator/generated-src/chipyard.harness.TestHarness.RocketConfig/gen-collateral
report_dir="$PWD/tests/seu/state-sweep/recorded"
(cd "$rtl" && sha256sum -c "$report_dir/rtl.sha256")
python3 tests/seu/state-sweep/sweep.py "$rtl" /tmp/argus-state-sweep-reproduction
```

The generator uses the register naming and declaration format emitted by firtool 1.75.0 for this snapshot. The frontend build also consumes Chipyard's generated cache memory library. Each module's output directory contains the Verilator build log, simulation summary and field-level CSV. A missing Rocket declaration, simulator build failure or failed injection causes a nonzero exit. [Implementation revisions and build instructions](../../../README.md)
