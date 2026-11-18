# Rocket SEU injection in Verilator 5.022

| Target | Simulated upset | Result |
|---|---|---|
| EX, MEM and WB program-counter registers | Each of 40 bits in each A/B/C copy while holding and while advancing | 720/720 matched the fault-free core |
| Frontend queue | Bit 0 of instruction data in replica B, entry 0, during a hold | Output stayed correct and B recovered on the next edge |

The pipeline campaign directly targeted 3 of the 62 protected declarations or aggregates in RocketCore.scala. The other 59 await direct injection. The queue case belongs to a separate module and is counted separately. The [pipeline coverage inventory](pipeline/coverage.csv) lists each declaration and the number of recorded injections against it. These counts describe this RocketConfig RTL campaign. Some source declarations contain multiple fields or depend on configuration.

Both C++ testbenches run against generated RocketConfig RTL. They use Verilator's public read/write interface to toggle a storage bit while the simulated clock is low and then evaluate the model before raising the clock. This models a digital bit upset between edges. Coverage is limited to the listed sites and stimulus.

The [pipeline harness](pipeline/campaign.cpp) drives a stream of ADDI instructions with gaps into two copies of the Rocket core. For each injection it verifies that exactly one copy changed, the voted PC and expected next PC agree with the fault-free reference, and all three copies contain that next PC after the edge. Hold cases check repair of the old value. Advance cases require a different next PC. Both models accepted and retired 625 instructions. A separate two-replica control produced the expected wrong majority. See the [individual cases](pipeline/recorded/cases.csv) and [run summary](pipeline/recorded/result.txt).

The [queue harness](queue/queue_seu.cpp) holds one valid entry with enqueue and dequeue disabled. It observes the corrupted replica and the voted output independently, then clocks once and drains the entry. See the [observed values](queue/recorded/result.txt).

To reproduce on the original Chipyard installation, run `bash tests/seu/pipeline/run.sh` and `bash tests/seu/queue/run.sh` from the repository root. On another machine adapt the environment activation and generated RTL paths in those scripts. Each script builds in its own directory. The recorded subdirectories contain the original evidence and RTL input hashes.
