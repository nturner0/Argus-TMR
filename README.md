# Argus-TMR: Daniel's Rocket state protection

Faithful source snapshot of Daniel Khalkhali's local implementation, taken 2026-11-10. This independent branch does not incorporate teammates' development branches.

## Revisions and reproduction

Chipyard: `371ab92dd09917b6ab05d58bf762b173c52f4224`.
Rocket local HEAD: `7a7c9df514d5adfe6e2cba6f040fe4fcfa5a8a1d` (already contains IBuf TMR).
Clean Rocket baseline: `55bcad0f59436de98ea510334121de8546b9e9d7` (parent of that TMR commit).
Use Chipyard at the stated revision with its recursive submodules and installed Chipyard toolchain. Check out the clean Rocket revision in generators/rocket-chip in a **separate** checkout, then overlay the five files in this branch's src/main/scala/rocket directory. Verify tests/source-sha256.json. Do not overlay the baseline checkout.

## Implementation

RocketCore.scala protects 62 EX/MEM/WB register declarations or aggregates, including control, validity, exceptions, PC/instruction, operands and bypass state. Frontend.scala protects selected fetch/control registers and uses TMRShiftQueue.scala for its five-entry instruction queue. IBuf.scala protects buffered instruction data, valid count and BTB state.

Protected state uses three replicas and bitwise majority `(a & b) | (a & c) | (b & c)`, recursively across aggregate leaves where needed. A common next-state wire defaults to the voted state and drives all replicas; normal updates override it, providing correction feedback on holds. Clock gating still controls when state can update.

TMRALU.scala is preserved existing auxiliary work; RocketCore currently instantiates `new ALU`, so this module is not active in this configuration. No ALU code was altered for this snapshot. These changes protect targeted state, and do not triplicate every combinational circuit across all five stages.

## Build and tests

Activate the Chipyard environment, then:

```bash
cd /path/to/chipyard/sims/verilator
make CONFIG=RocketConfig -j2
make CONFIG=RocketConfig BINARY=$RISCV/riscv64-unknown-elf/share/riscv-tests/isa/rv64ui-p-simple run-binary
```

Current verification: both fresh RocketConfig builds PASS. The same 30 selected ISA binaries PASS on both TMR and clean baseline Rocket (30/30 each after paired retries of two initial wall timeouts; no final failures, skips or timeouts). No functional regressions were observed in this subset. The source snapshot was published and independently verified at `06131946f449be5c0fcd0e09e473a49eb46093d4` before the full suite began. See [the report](tests/REPORT.md), [complete comparison](tests/comparison.md), and [reproduction instructions](tests/REPRODUCE.md). These tests establish normal execution only on the selected subset.

## Fault injection

SEU injection and replica correction are not yet demonstrated. Next work should corrupt one replica, verify voted state remains correct, and observe repair after an enabled clock edge. Auxiliary ALU injection code is not evidence of pipeline SEU recovery.
