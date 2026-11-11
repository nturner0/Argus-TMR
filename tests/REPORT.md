# Argus-TMR functional verification report

Date: 2026-11-11T04:58:30.047633+00:00. **Both RocketConfig builds PASS. All 30 selected ISA tests PASS on both processors after paired retries of two initial wall timeouts. Final outcomes have no failures, skips or timeouts.** The TMR source was pushed and independently verified on GitHub before the full regression began. This establishes normal functional execution on the selected subset; SEU recovery and radiation fault tolerance have not been tested.

## 1. Overview

The goal was to validate normal instruction execution in Daniel's targeted-state TMR Rocket processor and compare it with unmodified Rocket using identical test binaries, configuration, toolchain and pass criteria. The representative 30-test subset passed on both processors. Initial attempts passed 28/30 on each, with two wall timeouts; both affected tests passed paired longer-limit retries. No functional regression was observed in this subset; it does not verify all processor functionality.

The authoritative original source is `/home/dkhalkha/chipyard/generators/rocket-chip`. It was preserved without switching branches or modifying source. The separate publishing workspace is `/tmp/argus-daniel-20261110/publish`. The branch is [daniel_dev](https://github.com/nturner0/Argus-TMR/tree/daniel_dev). The initial local source snapshot commit is `243fa54bb7f662a60192a7162282e0e4b05bb3d6`; source and initial documentation were first pushed at verified remote commit `06131946f449be5c0fcd0e09e473a49eb46093d4`. An independent network clone of that commit verified all five SHA-256 source hashes and checked that its 31 tracked files contained no large/generated artifacts before either full regression runner was launched. See source-publication-verification.json.

## 2. Test environment

| Item | Value |
|---|---|
| OS | Ubuntu 22.04.3 LTS under WSL2; Linux 6.18.58.1-microsoft-standard-WSL2 |
| Chipyard | `371ab92dd09917b6ab05d58bf762b173c52f4224`, original branch main |
| Local Rocket HEAD | `7a7c9df514d5adfe6e2cba6f040fe4fcfa5a8a1d`, feature/rocket-tmr; includes committed IBuf TMR plus staged/unstaged work |
| Clean Rocket baseline | `55bcad0f59436de98ea510334121de8546b9e9d7` |
| Chisel / Scala / SBT | 6.7.0 / 2.13.16 / 1.8.2 |
| Java | OpenJDK 20.0.2-internal |
| Verilator | 5.022, conda-forge build 1 |
| RISC-V GCC / binutils | 16.1.0 (g6afcc4f6d) / 2.46 |
| Host C++ | conda-forge GCC 15.3.0 |
| CIRCT | firtool 1.75.0 |
| Configuration | RocketConfig; one WithNHugeCores Rocket |
| Regenerated ISA (both builds) | `rv64imafdcbzicsr_zifencei_zihpm_zfh_zba_zbb_zbs_xrocket` |
| Build settings | `make CONFIG=RocketConfig -j2`, same activated Chipyard environment |
| Simulation settings | seed 1; 10,000,000 cycle limit; 120-second initial wall timeout; 600-second paired retries; verbose completion markers |

Full version output is in environment.txt; recursive submodule revisions are in submodule-revisions.txt. Separate source/build trees are `/tmp/argus-daniel-20261110/tmr-chipyard` and `/tmp/argus-daniel-20261110/baseline-chipyard`. Both use the existing installed toolchain. Build/configuration files match as recorded in shared-build-inputs.json. Existing parent-tree environment changes and unrelated submodule changes were copied consistently; the clean Rocket baseline's 413 tracked paths were checked against the clean revision with no file-content mismatches. No targeted TMR modules or register changes remain in baseline Rocket. Source copying was followed by independent Git metadata setup because Chipyard checks submodule initialization; these are local isolated copies rather than new network clones.

## 3. TMR implementation summary

Published source files: RocketCore.scala, Frontend.scala, IBuf.scala, TMRShiftQueue.scala and TMRALU.scala, under src/main/scala/rocket. The exact source bytes and checksums are preserved. RocketCore has 62 targeted EX/MEM/WB register declarations or aggregates, enumerated in protected-pipeline-state.json. Frontend fetch/control registers, instruction-buffer state and queue entries/valid bits are also protected.

Protected state has three replicas and uses bitwise majority `(a & b) | (a & c) | (b & c)`. Aggregate voters recurse over scalar leaves. Voted values feed downstream logic; common next-state wiring defaults to the vote, correcting held state when the clock updates, and normal state updates override it for all replicas. Correction depends on enabled clock edges. These changes protect selected register/state elements; they do not triplicate all combinational logic in every pipeline stage.

The authoritative working-tree RocketCore instantiates standard `ALU`. A staged version had selected TMRALU, but the working-tree bytes take precedence. Existing TMRALU.scala is preserved to reproduce the full current Scala compilation input; its auxiliary triplicated combinational ALU is not instantiated in this RocketConfig. No ALU source was changed. The auxiliary ALU injection option was not enabled or tested, and is not evidence of pipeline fault recovery.

## 4. Test methodology

selection.csv identifies the 30 installed RV64 physical-environment ISA ELF tests and their SHA-256 hashes. Coverage includes basic execution, arithmetic, bitwise logic, shifts, four branch types, jumps, byte/halfword/word/doubleword loads and stores, multiplication, division/remainder, AMO and LR/SC. The configuration supports I/M/A. No selected test was unavailable, unsupported, skipped or replaced. Only sb and sh needed retries on both processors. Dedicated floating-point, compressed, virtual-memory and privileged suites remain outside this subset.

All selected ELF binaries were copied from the installed toolchain ISA directory to the writable directory `/tmp/argus-daniel-20261110/isa-binaries`; every copy was hash-verified. Both processors ran the same byte-identical files. Before execution, both previously built simulator hashes and all five original/published/isolated-TMR source hashes were rechecked against their saved records. Simulator hashes appear in execution-manifest.json. Independent source/build trees were used; no rebuild or source change was required on resumption.

After environment activation, the actual suite commands were:

```bash
python3 tests/run_suite.py --label tmr \
  --sim-dir /tmp/argus-daniel-20261110/tmr-chipyard/sims/verilator \
  --isa-dir /tmp/argus-daniel-20261110/isa-binaries \
  --results-dir /tmp/argus-daniel-20261110/regression-results \
  --source-published-sha 06131946f449be5c0fcd0e09e473a49eb46093d4
python3 tests/run_suite.py --label baseline \
  --sim-dir /tmp/argus-daniel-20261110/baseline-chipyard/sims/verilator \
  --isa-dir /tmp/argus-daniel-20261110/isa-binaries \
  --results-dir /tmp/argus-daniel-20261110/regression-results \
  --source-published-sha 06131946f449be5c0fcd0e09e473a49eb46093d4
```

The two suite runners overlapped in wall time but used independent simulator/build/output directories. Both use `make CONFIG=RocketConfig BREAK_SIM_PREREQ=1 BINARY=<ELF> RANDOM_SEED=1 TIMEOUT_CYCLES=10000000 EXTRA_SIM_FLAGS=+verbose run-binary-fast`. `BREAK_SIM_PREREQ=1` uses the completed, hash-verified simulator without recompiling it. Each CSV row records the exact command, binary hash, exit code, wall duration, local log path, source publication commit and simulator completion evidence. See REPRODUCE.md for the build, activation and comparison commands.

PASS requires exit code 0, an explicit simulator `*** PASSED ***` completion marker and no FAILED marker. TestDriver.v prints that marker only when the harness success signal is asserted, with verbose enabled. All 60 final-outcome logs were independently checked for a positive marker and zero exit code; the four initial timeout logs are retained separately. There were 64 total test invocations (32 per processor). A make exit code alone is insufficient. Each test has a 10,000,000 simulation-cycle limit. The initial wall limit was 120 seconds; sb and sh timed out on both processors. Both tests were rerun on both builds with --wall-timeout 600 --only rv64ui-p-sb rv64ui-p-sh, using all other settings unchanged. The corrected runner sends SIGTERM and then kills any remaining group members, even if Make exits first, with up to five seconds allowed for the leader. Initial timeouts count as attempted nonpasses in initial-pass statistics; the final table reports the verified retry results. Skips are excluded from the pass-rate denominator. See timeout-investigation.md and the initial/retry CSVs.

Complete logs remain local in `/tmp/argus-daniel-20261110/regression-results` and `/tmp/argus-daniel-20261110/regression-retries`; `/tmp/argus-daniel-20261110/regression-final-results` contains the merged final CSVs. simulation-log-manifest.csv records their paths, sizes and SHA-256 hashes. No full logs, simulators, waves or generated hardware are included in the published tree. Earlier single-test smoke checks remain as supplementary evidence; the result CSVs now contain the complete new post-publication regression executions, not reused smoke results.

## 5. Results

Both fresh builds completed successfully with exit code 0 using `make CONFIG=RocketConfig -j2`. These exact simulators were used for the full suite. Final outcomes: all 30 distinct selected tests passed on each processor, **30/30 = 100%**, with no remaining failures, skips or timeouts. Initial pass: 28 passed, 2 timed out, 0 failed and 0 skipped on each (28/30 = 93.3%). Paired retries: 2/2 passed on each with the extended wall limit. Each processor had 32 total invocations. The final pass rate is per distinct test after retries, not per invocation; the initial timeouts are not hidden or reclassified. Completion cycle counts matched for 30/30 tests; these observations are not a performance benchmark, and concurrent wall durations must not be interpreted as processor performance.

| ISA Test | Category | Original Rocket | TMR Rocket |
|---|---|---|---|
| rv64ui-p-simple | Basic | PASS | PASS |
| rv64ui-p-add | Arithmetic | PASS | PASS |
| rv64ui-p-addi | Arithmetic | PASS | PASS |
| rv64ui-p-sub | Arithmetic | PASS | PASS |
| rv64ui-p-and | Bitwise | PASS | PASS |
| rv64ui-p-or | Bitwise | PASS | PASS |
| rv64ui-p-xor | Bitwise | PASS | PASS |
| rv64ui-p-sll | Shifts | PASS | PASS |
| rv64ui-p-srl | Shifts | PASS | PASS |
| rv64ui-p-sra | Shifts | PASS | PASS |
| rv64ui-p-beq | Branches | PASS | PASS |
| rv64ui-p-bne | Branches | PASS | PASS |
| rv64ui-p-blt | Branches | PASS | PASS |
| rv64ui-p-bge | Branches | PASS | PASS |
| rv64ui-p-jal | Jumps | PASS | PASS |
| rv64ui-p-jalr | Jumps | PASS | PASS |
| rv64ui-p-lb | Loads | PASS | PASS |
| rv64ui-p-lh | Loads | PASS | PASS |
| rv64ui-p-lw | Loads | PASS | PASS |
| rv64ui-p-ld | Loads | PASS | PASS |
| rv64ui-p-sb | Stores | PASS | PASS |
| rv64ui-p-sh | Stores | PASS | PASS |
| rv64ui-p-sw | Stores | PASS | PASS |
| rv64ui-p-sd | Stores | PASS | PASS |
| rv64um-p-mul | Multiplication | PASS | PASS |
| rv64um-p-mulh | Multiplication | PASS | PASS |
| rv64um-p-div | Division | PASS | PASS |
| rv64um-p-rem | Division | PASS | PASS |
| rv64ua-p-amoadd_d | Atomic | PASS | PASS |
| rv64ua-p-lrsc | Atomic | PASS | PASS |

Original Rocket: selected 30, attempted 30, passed 30, failed 0, skipped 0, timed out 0; pass rate 100.0%. Skips are excluded; timeouts count as attempted nonpasses.

TMR Rocket: selected 30, attempted 30, passed 30, failed 0, skipped 0, timed out 0; pass rate 100.0%. Skips are excluded; timeouts count as attempted nonpasses.

## Outcome groups

- Baseline PASS, TMR PASS: rv64ui-p-simple, rv64ui-p-add, rv64ui-p-addi, rv64ui-p-sub, rv64ui-p-and, rv64ui-p-or, rv64ui-p-xor, rv64ui-p-sll, rv64ui-p-srl, rv64ui-p-sra, rv64ui-p-beq, rv64ui-p-bne, rv64ui-p-blt, rv64ui-p-bge, rv64ui-p-jal, rv64ui-p-jalr, rv64ui-p-lb, rv64ui-p-lh, rv64ui-p-lw, rv64ui-p-ld, rv64ui-p-sb, rv64ui-p-sh, rv64ui-p-sw, rv64ui-p-sd, rv64um-p-mul, rv64um-p-mulh, rv64um-p-div, rv64um-p-rem, rv64ua-p-amoadd_d, rv64ua-p-lrsc


Functional comparison:

- Pass on both: all 30 selected tests, listed above.
- Fail on both: none.
- Baseline pass / TMR fail: none; no suspected TMR functional regression in this subset.
- Baseline fail / TMR pass: none.
- Not comparable due to toolchain/configuration differences: none.

The paired wall timeouts were investigated and resolved by longer-limit reruns; no processor source correction was required. Successful results on this subset do not establish exhaustive ISA correctness, protection of unmodified state, or behavior under injected faults.

## 6. Issues and limitations

- The initial publication attempt was blocked by missing Git HTTPS credentials and GitHub-app HTTP 403 responses. The user completed authentication; command-line push then succeeded. Independent clone verification completed before the full suite. This issue is resolved.
- During the earlier isolated build setup, SBT required authorized access to its runtime socket/cache, and Chipyard required copied submodule Git metadata. Both setup issues were resolved before the successful builds. Copied generator JARs were excluded before independent builds to avoid baseline/TMR contamination.
- Existing build warnings include upstream Scala deprecations, procedure syntax and implicit types, multiple-main warnings, CIRCT printf-encoded verification deprecation warnings, and host C++ `_XOPEN_SOURCE` redefinition. Representative examples are in build-warning-excerpts.txt; complete build logs remain local. These warnings were not changed as part of this assignment.
- Make emits duplicate generated assembly/benchmark target-recipe warnings. An early smoke test using the read-only installed ELF path emitted an intermediate-cleanup warning after successful completion. Staging identical ELF copies in a writable directory resolved that cleanup issue for the reported full regression.
- Initial sb/sh runs timed out at 120 seconds on both processors. Logs subsequently showed late PASSED markers, but the wrapper exit was 124, so those attempts remain TIMEOUT. The cleanup waited only for Make; it was repaired to kill remaining process-group members even if the leader exited. The paired 600-second retries passed with exit 0 and explicit completion. No ISA assertion or simulator cycle-limit failure was observed. Exact host timing was not profiled; the initial wall policy was marginal. Future reproduction uses 600 seconds for the full suite. See timeout-investigation.md for last observed PCs/instructions, late markers, durations and the distinction between initial and final statistics. No TMR-only regression was observed and no processor source modifications were needed.
- Coverage excludes dedicated F/D/C, privileged, virtual-memory, multicore and system-software suites. No synthesis area/timing comparison, fault injection, replica repair observation or radiation-tolerance measurement was performed. Existing auxiliary ALU injection code was not enabled.

The original source hashes, branches, HEADs, staged and unstaged changes are preserved. Initial preservation records remain under `/tmp/argus-daniel-20261110/protection`; published source and baseline manifests identify the tested snapshots. The full upstream Chipyard installation, unrelated collaborator branches and generated build outputs were not uploaded.

## 7. Next steps

The next major verification milestone is separate **SEU fault injection**. Deliberately corrupt one replica, verify that the majority-voted processor state and normal instruction execution remain correct, and then observe correction feedback repairing the replica after an enabled clock edge. Exercise frontend registers, instruction buffer/queue and EX/MEM/WB state, including holds, flushes, resets and clock gating. Include faults in valid/control state as well as datapath state. Passing the functional ISA subset alone does not demonstrate these properties.

Additional functional coverage can expand to compressed and floating-point tests, privileged/VM suites, longer programs and more seeds. Fault-injection results should be reported separately from normal execution regressions.
