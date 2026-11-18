# Rocket SEU injection in Verilator 5.022

| Target | Simulated upset | Result |
|---|---|---|
| Rocket, Frontend, IBuf and TMRShiftQueue | Each emitted A/B/C replica bit across 310 fields | 7,110/7,110 matched the fault-free model |
| EX, MEM and WB program-counter registers | Each of 40 bits in each A/B/C copy while holding and while advancing | 720/720 matched the fault-free core |
| Frontend queue | Bit 0 of instruction data in replica B, entry 0, during a hold | Output stayed correct and B recovered on the next edge |

The bit-by-bit sweep reached all 62 inventoried Rocket EX/MEM/WB declarations, plus 19 frontend, three IBuf and two queue declarations or aggregates. The earlier PC and queue experiments independently tested correction feedback during a known hold. The [sweep report](state-sweep/README.md) details the stimulus and pass criteria. The [pipeline inventory](pipeline/coverage.csv) shows how many injections each campaign contributed to each Rocket declaration.

All campaigns toggle a storage bit between clock edges in generated RocketConfig RTL using Verilator's public read/write interface.

The [pipeline harness](pipeline/campaign.cpp) drives a stream of ADDI instructions with gaps into two copies of the Rocket core. For each injection it verifies that exactly one copy changed, the voted PC and expected next PC agree with the fault-free reference, and all three copies contain that next PC after the edge. Hold cases check repair of the old value. Advance cases require a different next PC. Both models accepted and retired 625 instructions. A separate two-replica control produced the expected wrong majority. See the [individual cases](pipeline/recorded/cases.csv) and [run summary](pipeline/recorded/result.txt).

The [queue harness](queue/queue_seu.cpp) holds one valid entry with enqueue and dequeue disabled. It observes the corrupted replica and the voted output independently, then clocks once and drains the entry. See the [observed values](queue/recorded/result.txt).

To reproduce on the original Chipyard installation, run `bash tests/seu/pipeline/run.sh` and `bash tests/seu/queue/run.sh` from the repository root. On another machine adapt the environment activation and generated RTL paths in those scripts. Each script builds in its own directory. The recorded subdirectories contain the original evidence and RTL input hashes.
