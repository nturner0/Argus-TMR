# Reproduction

## Source and dependencies

Use Chipyard commit `371ab92dd09917b6ab05d58bf762b173c52f4224` with the recursive submodule revisions in submodule-revisions.txt and its configured environment. The local environment is recorded in environment.txt. Chisel is 6.7.0, Scala 2.13.16, SBT 1.8.2. RocketConfig instantiates one WithNHugeCores Rocket with M and A support. The existing emitted device tree advertises `rv64imafdcbzicsr_zifencei_zihpm_zfh_zba_zbb_zbs_xrocket`; regenerated device trees should be checked after build.

Create two independent Chipyard checkouts. In the baseline checkout, use clean Rocket commit `55bcad0f59436de98ea510334121de8546b9e9d7`. In the TMR checkout, start at that same Rocket revision and overlay the five Scala source files from this branch. The local Rocket HEAD already includes IBuf TMR and is not a valid clean baseline. Verify the source checksums. Preserve initialized submodule Git metadata: Chipyard checks .git existence when building generators. Do not reuse generated hardware, simulators or generator JARs between implementations.

Activate the same installed Chipyard environment for both builds:

```bash
source /home/dkhalkha/miniforge3/etc/profile.d/conda.sh
source /home/dkhalkha/chipyard/env.sh
cd /path/to/baseline-chipyard/sims/verilator
make CONFIG=RocketConfig -j2
cd /path/to/tmr-chipyard/sims/verilator
make CONFIG=RocketConfig -j2
```

Paths in these commands describe this machine; adapt the activation paths elsewhere. Use independent writable build directories and preserve complete build logs locally.

## Publication prerequisite

Before the full suite, publish the initial source commit and verify its remote SHA. The local publishing repository is an independent root history. Once Git credentials work:

```bash
cd /tmp/argus-daniel-20261110/publish
git push -u origin daniel_dev
git ls-remote origin refs/heads/daniel_dev
```

Never force-push. If daniel_dev appears remotely before the push, inspect it and stop on any conflict. Verify remote source bytes against source-sha256.json and confirm no build artifacts are in the tree. Record the verified source commit SHA below when invoking the suite.

## Identical regression suite

selection.csv contains 30 real installed RV64 physical-environment ELF tests, categories and SHA-256 hashes. It covers base integer execution, branches/jumps, all four requested memory widths, multiplication, division/remainder and atomics. Only I/M/A instructions are selected; F/D/C-specific, virtual-memory and privileged tests remain outside this subset. No test was replaced or declared unsupported without execution.

From the publishing checkout after environment activation:

```bash
python3 tests/run_suite.py --label tmr \
  --sim-dir /path/to/tmr-chipyard/sims/verilator \
  --isa-dir "$RISCV/riscv64-unknown-elf/share/riscv-tests/isa" \
  --results-dir /path/to/local-results \
  --source-published-sha VERIFIED_REMOTE_SOURCE_COMMIT
python3 tests/run_suite.py --label baseline \
  --sim-dir /path/to/baseline-chipyard/sims/verilator \
  --isa-dir "$RISCV/riscv64-unknown-elf/share/riscv-tests/isa" \
  --results-dir /path/to/local-results \
  --source-published-sha VERIFIED_REMOTE_SOURCE_COMMIT
python3 tests/compare.py /path/to/local-results/tmr_results.csv \
  /path/to/local-results/baseline_results.csv tests/comparison.md
```

Each invocation uses `make CONFIG=RocketConfig BREAK_SIM_PREREQ=1 BINARY=<ELF> RANDOM_SEED=1 TIMEOUT_CYCLES=10000000 EXTRA_SIM_FLAGS=+verbose run-binary-fast`. The verbose flag enables TestDriver's positive PASSED marker. Full stdout/stderr are captured locally; no waves are requested. A PASS requires zero exit code plus `*** PASSED ***` and no `*** FAILED ***`. A zero exit code without positive completion is a FAIL requiring investigation. The process group is terminated after 120 wall seconds, with a five-second grace before killing a hang. Simulator timeouts are counted separately from FAIL but remain attempted nonpasses in the pass-rate denominator. Missing/mismatched ELF files are SKIP and excluded from attempted counts. Exact commands and evidence are stored in each CSV.

The main reports should summarize all skips and failures; inspect complete local logs before attributing a failure to processor hardware. Publish only small relevant excerpts, CSVs, scripts and Markdown reports. Do not publish generator JARs, executables, full logs, generated RTL, or waveforms.

## Next verification milestone

Inject an SEU into one replica, check the voted architectural behavior, and observe correction of that replica after an enabled clock edge. These ISA tests alone cannot establish SEU recovery or radiation tolerance.
