#!/usr/bin/env python3
"""Run the selected identical ELF binaries using Chipyard's simulation flow."""
import argparse
import csv
import hashlib
import os
from pathlib import Path
import re
import shlex
import signal
import subprocess
import time


def main():
    ap = argparse.ArgumentParser(__doc__)
    ap.add_argument('--sim-dir', type=Path, required=True)
    ap.add_argument('--isa-dir', type=Path, required=True)
    ap.add_argument('--label', choices=['tmr', 'baseline'], required=True)
    ap.add_argument('--results-dir', type=Path, required=True)
    ap.add_argument('--source-published-sha', required=True,
                    help='Verified remote source commit; publish before regression.')
    args = ap.parse_args()
    if not re.fullmatch(r'[0-9a-f]{40}', args.source_published_sha):
        ap.error('Expected a full verified remote source commit SHA')
    rows = list(csv.DictReader((Path(__file__).parent / 'selection.csv').open()))
    simulator = args.sim_dir / 'simulator-chipyard.harness-RocketConfig'
    if not simulator.is_file() or not os.access(simulator, os.X_OK):
        ap.error('Build the RocketConfig simulator before running the suite')
    args.results_dir.mkdir(parents=True, exist_ok=True)
    fields = ['test', 'category', 'sha256', 'ran', 'result', 'exit_code',
              'seconds', 'command', 'evidence', 'log', 'source_published_sha']
    with (args.results_dir / f'{args.label}_results.csv').open('w') as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader()
        for row in rows:
            binary = args.isa_dir / row['test']
            log = args.results_dir / f"{args.label}-{row['test']}.log"
            cmd = ['make', 'CONFIG=RocketConfig', 'BREAK_SIM_PREREQ=1', f'BINARY={binary}',
                   'RANDOM_SEED=1', 'TIMEOUT_CYCLES=10000000',
                   'EXTRA_SIM_FLAGS=+verbose', 'run-binary-fast']
            result = dict(row, ran='no', result='SKIP', exit_code='', seconds=0,
                          command=shlex.join(cmd), evidence='', log=str(log),
                          source_published_sha=args.source_published_sha)
            if not binary.is_file():
                result['evidence'] = 'Missing test ELF'
            elif hashlib.sha256(binary.read_bytes()).hexdigest() != row['sha256']:
                result['evidence'] = 'ELF checksum differs from selected binary'
            else:
                start = time.monotonic()
                with log.open('w') as output:
                    proc = subprocess.Popen(cmd, cwd=args.sim_dir, stdout=output,
                                            stderr=subprocess.STDOUT, start_new_session=True)
                    timed_out = False
                    try:
                        code = proc.wait(timeout=120)
                    except subprocess.TimeoutExpired:
                        timed_out = True
                        os.killpg(proc.pid, signal.SIGTERM)
                        try:
                            proc.wait(timeout=5)
                        except subprocess.TimeoutExpired:
                            os.killpg(proc.pid, signal.SIGKILL)
                            proc.wait()
                        code = 124
                text = log.read_text(errors='replace')
                passed = re.findall(r'\*\*\* PASSED \*\*\*[^\n]*', text)
                failed = re.findall(r'\*\*\* FAILED \*\*\*[^\n]*', text)
                result.update(ran='yes', exit_code=code,
                              seconds=round(time.monotonic()-start, 3))
                if timed_out or any('timeout' in x.lower() for x in failed):
                    result.update(result='TIMEOUT', evidence='; '.join(failed) or '120s wall timeout')
                elif code == 0 and passed and not failed:
                    result.update(result='PASS', evidence='; '.join(passed))
                else:
                    result.update(result='FAIL', evidence='; '.join(failed) or
                                  'Missing positive simulator completion; inspect log')
            writer.writerow(result)
            f.flush()
            print(row['test'], result['result'], result['evidence'], flush=True)


if __name__ == '__main__':
    main()
