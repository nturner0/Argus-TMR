#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")"
source /home/dkhalkha/miniforge3/etc/profile.d/conda.sh
set +u
source /home/dkhalkha/chipyard/env.sh
set -u
rtl=/home/dkhalkha/chipyard/sims/verilator/generated-src/chipyard.harness.TestHarness.RocketConfig/gen-collateral/TMRShiftQueue.sv
sha256sum "$rtl" > rtl.sha256
verilator --version
verilator --cc --exe --build -j 2 --public-flat-rw --top-module TMRShiftQueue "$rtl" "$PWD/queue_seu.cpp" >build.log 2>&1
set -o pipefail
obj_dir/VTMRShiftQueue | tee result.log
