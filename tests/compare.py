#!/usr/bin/env python3
"""Create a side-by-side comparison from actual suite CSV outputs."""
import argparse
import collections
import csv
from pathlib import Path

ap = argparse.ArgumentParser(__doc__)
ap.add_argument('tmr', type=Path)
ap.add_argument('baseline', type=Path)
ap.add_argument('output', type=Path)
args = ap.parse_args()
data = []
for file in [args.tmr, args.baseline]:
    rows = list(csv.DictReader(file.open()))
    if len({r['test'] for r in rows}) != len(rows):
        raise SystemExit('Duplicate test names')
    data.append({r['test']: r for r in rows})
tmr, baseline = data
if set(tmr) != set(baseline):
    raise SystemExit('Test selections differ')
out = ['# ISA test comparison', '', '| ISA Test | Category | Original Rocket | TMR Rocket |',
       '|---|---|---|---|']
groups = collections.defaultdict(list)
for name, t in tmr.items():
    b = baseline[name]
    if t['sha256'] != b['sha256'] or t['category'] != b['category']:
        raise SystemExit('Binary/category mismatch: '+name)
    out.append(f"| {name} | {t['category']} | {b['result']} | {t['result']} |")
    groups[(b['result'], t['result'])].append(name)
for label, rows in [('Original Rocket', baseline), ('TMR Rocket', tmr)]:
    counts = collections.Counter(r['result'] for r in rows.values())
    attempted = sum(r['ran'] == 'yes' for r in rows.values())
    rate = f"{100*counts['PASS']/attempted:.1f}%" if attempted else 'N/A (no attempts)'
    out += ['', f"{label}: selected {len(rows)}, attempted {attempted}, passed {counts['PASS']}, "
            f"failed {counts['FAIL']}, skipped {counts['SKIP']}, timed out {counts['TIMEOUT']}; "
            f'pass rate {rate}. Skips are excluded; timeouts count as attempted nonpasses.']
out += ['', '## Outcome groups', '']
for (b, t), names in groups.items():
    out.append(f"- Baseline {b}, TMR {t}: {', '.join(names)}")
args.output.write_text('\n'.join(out)+'\n')
