#!/usr/bin/env python3
"""One build-validation test, separate from the publication-gated regression suite."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import signal
import subprocess
import time

ap = argparse.ArgumentParser(__doc__)
ap.add_argument('--sim-dir', type=Path, required=True)
ap.add_argument('--isa-dir', type=Path, required=True)
ap.add_argument('--label', choices=['tmr', 'baseline'], required=True)
ap.add_argument('--results-dir', type=Path, required=True)
a = ap.parse_args()
binary = a.isa_dir / 'rv64ui-p-simple'
cmd = ['make', 'CONFIG=RocketConfig', 'BREAK_SIM_PREREQ=1', f'BINARY={binary}',
       'RANDOM_SEED=1', 'TIMEOUT_CYCLES=10000000', 'EXTRA_SIM_FLAGS=+verbose', 'run-binary-fast']
a.results_dir.mkdir(parents=True, exist_ok=True)
log = a.results_dir / f'{a.label}-smoke.log'
start = time.monotonic()
with log.open('w') as f:
    p = subprocess.Popen(cmd, cwd=a.sim_dir, stdout=f, stderr=subprocess.STDOUT,
                         start_new_session=True)
    try:
        code = p.wait(timeout=120)
    except subprocess.TimeoutExpired:
        os.killpg(p.pid, signal.SIGKILL)
        p.wait()
        code = 124
text = log.read_text(errors='replace')
evidence = re.findall(r'\*\*\* (?:PASSED|FAILED) \*\*\*[^\n]*', text)
result = 'PASS' if code == 0 and any('PASSED' in e for e in evidence) and not any('FAILED' in e for e in evidence) else 'TIMEOUT' if code == 124 or any('timeout' in e.lower() for e in evidence) else 'FAIL'
record = dict(test=binary.name, label=a.label, result=result, exit_code=code,
              command=shlex.join(cmd), cwd=str(a.sim_dir), evidence=evidence,
              seconds=round(time.monotonic()-start, 3), log=str(log),
              binary_sha256=hashlib.sha256(binary.read_bytes()).hexdigest())
(a.results_dir / f'{a.label}-smoke.json').write_text(json.dumps(record, indent=2)+'\n')
print(json.dumps(record, indent=2))
