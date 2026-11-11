# Argus-TMR functional verification report

Date: 2026-11-11T04:28:00.140210+00:00. **Assignment incomplete: source publication and the full 30-test regression are blocked by GitHub access.** Both processors were freshly built, and only a single build smoke test was executed on each. The remaining 29 selected tests were deliberately deferred to honor the requirement to publish the source before the full regression suite. No results are inferred from historical runs.

## 1. Overview

The intended objective is to validate normal RISC-V instruction execution on Daniel's targeted-state TMR Rocket implementation and compare it with unmodified Rocket. Current evidence establishes successful compilation and `rv64ui-p-simple` completion on both builds. It does not establish the requested representative regression coverage.

The authoritative original source is `/home/dkhalkha/chipyard/generators/rocket-chip`. The separate publishing repository is `/tmp/argus-daniel-20261110/publish`, branch `daniel_dev`. Its initial local source commit is `243fa54bb7f662a60192a7162282e0e4b05bb3d6`. No remote source/report commit exists, and no daniel_dev branch exists on GitHub as of the final remote check. The intended destination is `https://github.com/nturner0/Argus-TMR/tree/daniel_dev`; it is not a published deliverable yet.

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
| Simulation settings | seed 1; 10,000,000 cycle limit; 120-second wall timeout; verbose completion markers |

Full version output is in environment.txt; recursive submodule revisions are in submodule-revisions.txt. Separate source/build trees are `/tmp/argus-daniel-20261110/tmr-chipyard` and `/tmp/argus-daniel-20261110/baseline-chipyard`. Both use the existing installed toolchain. Build/configuration files match as recorded in shared-build-inputs.json. Existing parent-tree environment changes and unrelated submodule changes were copied consistently; the clean Rocket baseline's 413 tracked paths were checked against the clean revision with no file-content mismatches. No targeted TMR modules or register changes remain in baseline Rocket. Source copying was followed by independent Git metadata setup because Chipyard checks submodule initialization; these are local isolated copies rather than new network clones.

## 3. TMR implementation summary

Published-ready source files: RocketCore.scala, Frontend.scala, IBuf.scala, TMRShiftQueue.scala and TMRALU.scala, under src/main/scala/rocket. The exact source bytes and checksums are preserved. RocketCore has 62 targeted EX/MEM/WB register declarations or aggregates, enumerated in protected-pipeline-state.json. Frontend fetch/control registers, instruction-buffer state and queue entries/valid bits are also protected.

Protected state has three replicas and uses bitwise majority `(a & b) | (a & c) | (b & c)`. Aggregate voters recurse over scalar leaves. Voted values feed downstream logic; common next-state wiring defaults to the vote, correcting held state when the clock updates, and normal state updates override it for all replicas. Correction depends on enabled clock edges. These changes protect selected register/state elements; they do not triplicate all combinational logic in every pipeline stage.

The authoritative working-tree RocketCore instantiates standard `ALU`. A staged version had selected TMRALU, but the working-tree bytes take precedence. Existing TMRALU.scala is preserved to reproduce the full current Scala compilation input; its auxiliary triplicated combinational ALU is not instantiated in this RocketConfig. No ALU source was changed. The auxiliary ALU injection option was not enabled or tested, and is not evidence of pipeline fault recovery.

## 4. Test methodology

selection.csv identifies 30 real installed RV64 physical-environment ELF tests and their SHA-256 hashes. Categories cover basic execution, integer arithmetic, bitwise logic, shifts, branches, jumps, all four requested load/store widths, multiplication, division/remainder, AMO and LR/SC. I/M/A are supported by the configuration; floating-point, compressed-specific, virtual-memory and privileged verification are outside this selected subset. No unsupported test was observed; the deferred tests have not been executed.

The same selected ELF bytes were copied to `/tmp/argus-daniel-20261110/isa-binaries`, and all hashes were verified against the installed binaries. Both reported smoke executions used that same directory. See REPRODUCE.md for build and suite commands. The exact executed smoke commands, directories, exit codes, durations and positive completion evidence are in the corresponding smoke JSON and CSV files. The command for both was:

```bash
make CONFIG=RocketConfig BREAK_SIM_PREREQ=1 \
  BINARY=/tmp/argus-daniel-20261110/isa-binaries/rv64ui-p-simple \
  RANDOM_SEED=1 TIMEOUT_CYCLES=10000000 \
  EXTRA_SIM_FLAGS=+verbose run-binary-fast
```

Each command ran in its own build's sims/verilator directory. `BREAK_SIM_PREREQ=1` uses the independently verified completed simulator build. PASS requires a successful exit code plus the simulator's explicit `*** PASSED ***` marker and no FAILED marker. This simulator uses TestDriver.v with Verilator's generated main; verbose enables its completion print. A make exit code alone is insufficient. Timeouts are attempted nonpasses; skipped tests are excluded from the pass-rate denominator. The runner terminates an entire process group on a wall timeout. The 30-test runner and comparison script were prepared and their CLI syntax checked, but the full runner has not been validated by an actual complete suite execution.

Full build logs and simulation traces are retained locally outside the publishing tree. Only compact smoke excerpts and machine-readable evidence are included in the branch-ready files. CSV rows marked PASS are actual pre-publication smoke observations; SKIP means intentionally deferred, not processor failure.

## 5. Results

Both fresh builds completed with exit code 0. Both smoke tests completed after **31,136 simulation cycles** with explicit PASSED output and exit code 0. The observed cycle count is one test's completion count, not a performance benchmark.

| ISA Test | Category | Original Rocket | TMR Rocket |
|---|---|---|---|
| rv64ui-p-simple | Basic | PASS | PASS |
| rv64ui-p-add | Arithmetic | SKIP | SKIP |
| rv64ui-p-addi | Arithmetic | SKIP | SKIP |
| rv64ui-p-sub | Arithmetic | SKIP | SKIP |
| rv64ui-p-and | Bitwise | SKIP | SKIP |
| rv64ui-p-or | Bitwise | SKIP | SKIP |
| rv64ui-p-xor | Bitwise | SKIP | SKIP |
| rv64ui-p-sll | Shifts | SKIP | SKIP |
| rv64ui-p-srl | Shifts | SKIP | SKIP |
| rv64ui-p-sra | Shifts | SKIP | SKIP |
| rv64ui-p-beq | Branches | SKIP | SKIP |
| rv64ui-p-bne | Branches | SKIP | SKIP |
| rv64ui-p-blt | Branches | SKIP | SKIP |
| rv64ui-p-bge | Branches | SKIP | SKIP |
| rv64ui-p-jal | Jumps | SKIP | SKIP |
| rv64ui-p-jalr | Jumps | SKIP | SKIP |
| rv64ui-p-lb | Loads | SKIP | SKIP |
| rv64ui-p-lh | Loads | SKIP | SKIP |
| rv64ui-p-lw | Loads | SKIP | SKIP |
| rv64ui-p-ld | Loads | SKIP | SKIP |
| rv64ui-p-sb | Stores | SKIP | SKIP |
| rv64ui-p-sh | Stores | SKIP | SKIP |
| rv64ui-p-sw | Stores | SKIP | SKIP |
| rv64ui-p-sd | Stores | SKIP | SKIP |
| rv64um-p-mul | Multiplication | SKIP | SKIP |
| rv64um-p-mulh | Multiplication | SKIP | SKIP |
| rv64um-p-div | Division | SKIP | SKIP |
| rv64um-p-rem | Division | SKIP | SKIP |
| rv64ua-p-amoadd_d | Atomic | SKIP | SKIP |
| rv64ua-p-lrsc | Atomic | SKIP | SKIP |

Original Rocket: selected 30, attempted 1, passed 1, failed 0, skipped 29, timed out 0; pass rate 100.0%. Skips are excluded; timeouts count as attempted nonpasses.

TMR Rocket: selected 30, attempted 1, passed 1, failed 0, skipped 29, timed out 0; pass rate 100.0%. Skips are excluded; timeouts count as attempted nonpasses.

## Outcome groups

- Baseline PASS, TMR PASS: rv64ui-p-simple
- Baseline SKIP, TMR SKIP: rv64ui-p-add, rv64ui-p-addi, rv64ui-p-sub, rv64ui-p-and, rv64ui-p-or, rv64ui-p-xor, rv64ui-p-sll, rv64ui-p-srl, rv64ui-p-sra, rv64ui-p-beq, rv64ui-p-bne, rv64ui-p-blt, rv64ui-p-bge, rv64ui-p-jal, rv64ui-p-jalr, rv64ui-p-lb, rv64ui-p-lh, rv64ui-p-lw, rv64ui-p-ld, rv64ui-p-sb, rv64ui-p-sh, rv64ui-p-sw, rv64ui-p-sd, rv64um-p-mul, rv64um-p-mulh, rv64um-p-div, rv64um-p-rem, rv64ua-p-amoadd_d, rv64ua-p-lrsc


Only `rv64ui-p-simple` passed on both. There are no observed failures on both, no observed baseline-pass/TMR-fail cases, and no observed baseline-fail/TMR-pass cases. The remaining 29 tests cannot yet be compared because they were deferred for the publication prerequisite. A 100% pass rate over one attempted test is not representative regression coverage and cannot exclude TMR regressions.

## 6. Issues and limitations

- Git HTTPS push failed: `fatal: could not read Username for 'https://github.com': No such device or address`. No Git credential helper or SSH identity was available.
- The connected GitHub account reported repository push permission, but create-tree and create-branch API operations both returned HTTP 403, `Resource not accessible by integration`. No team branch was modified, no source was published, and no remote commit SHA can be reported.
- The full suite was not started, respecting the explicit source-publication-first requirement. This is the outstanding dependency, not a simulator timeout or hardware failure.
- Initial isolated build preparation hit a sandbox SBT runtime-socket write restriction; the build was rerun with authorized access. An initial baseline assembly also rejected missing submodule Git metadata. Independent copies of the original Git metadata resolved that setup error, and the final baseline build passed. Copied generator JARs were excluded before building to avoid reusing generated TMR hardware in the baseline.
- Builds emit upstream Scala deprecation/procedure-syntax/implicit-type warnings, multiple-main warnings, and CIRCT printf-encoded verification deprecation warnings. Host C++ also warns about `_XOPEN_SOURCE` redefinition. Representative excerpts are in build-warning-excerpts.txt; full warnings are in local logs. These warnings were not repaired as part of processor verification.
- Make warns about duplicate generated assembly/benchmark target recipes. An initial baseline smoke using the installed ELF path also emitted a read-only intermediate-cleanup warning after a successful simulation. Repeating the smoke using the writable staged ELF removed that cleanup warning and produced the same positive completion. The original log is retained locally.
- No fault injection, replica-repair observation, broad privileged/VM/FP execution, synthesis resource assessment, or radiation tolerance measurement was performed. No source fix was needed or attempted.

Original branches, HEADs, staged/unstaged diffs and Git statuses in both original repositories were compared to the pre-work records and were unchanged after both builds. All five original source SHA-256 values also match the publishing snapshot. Preservation records are local under `/tmp/argus-daniel-20261110/protection`; source and baseline manifests are included here.

## 7. Next steps

1. Authenticate command-line Git (for example `gh auth login` then `gh auth setup-git` if GitHub CLI is installed), or correct the connected app's repository write access. Do not place tokens in reports or scripts.
2. Push the initial source snapshot to daniel_dev without force, verify the remote commit and source checksums, then run the prepared full 30-test suite on both builds with identical staged binaries. Investigate any baseline-pass/TMR-fail differences before changing processor source.
3. Regenerate the comparison and report with the complete actual results, commit only compact evidence and scripts, and push the report commit. The present local source and report history is ready to publish; the required source-first remote milestone must still be verified before the full suite.
4. Perform separate SEU fault injection: corrupt one register replica, verify that voted state and instruction execution remain correct, then verify correction feedback repairs the replica after an enabled clock edge. Include frontend, instruction buffer/queue, and EX/MEM/WB state; exercise holds, flushes, resets and clock gating. ISA regression passes alone do not demonstrate this property.
