#!/usr/bin/env bash
set -eo pipefail
cd "$(dirname "$0")"
source /home/dkhalkha/miniforge3/etc/profile.d/conda.sh
source /home/dkhalkha/chipyard/env.sh
rtl=/home/dkhalkha/chipyard/sims/verilator/generated-src/chipyard.harness.TestHarness.RocketConfig/gen-collateral
verilator --cc --exe --public-flat-rw --top-module Rocket -Wno-fatal -DPRINTF_COND=0 -y "$rtl" +libext+.sv+.v "$rtl/Rocket.sv" "$PWD/campaign.cpp" --Mdir obj >verilate.log 2>&1
make -C obj -j2 -f VRocket.mk >build.log 2>&1
obj/VRocket | tee result.log
