#!/usr/bin/env python3
"""Import device rules; Startup Profile rules must also be in the Baseline Profile."""
import argparse
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('outputs', type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
collected = {}
for name in ('baseline-prof.txt', 'startup-prof.txt'):
    paths = list(args.outputs.rglob('*' + name))
    if not paths:
        raise SystemExit('No generated ' + name + ' found')
    collected[name] = {line.strip() for p in paths for line in p.read_text().splitlines()
                       if 'Lcom/personal/wo/' in line and not line.lstrip().startswith('#')}
    if not collected[name]:
        raise SystemExit('No app-owned rules in ' + name)

# BaselineProfileRule emits startup collections as startup-prof.txt only. Both
# startup and interaction code need ART compilation; only startup code needs DEX layout.
collected['baseline-prof.txt'] |= collected['startup-prof.txt']
merged = {}
for name, rules in collected.items():
    target = root / 'app/src/main/baselineProfiles' / name
    current = target.read_text().splitlines() if target.exists() else []
    merged[name] = {line for line in current if line and not line.startswith('#')} | rules
    if name == 'startup-prof.txt' and any('*' in line for line in merged[name]):
        raise SystemExit('Startup Profiles require exact bytecode descriptors')

for name, rules in merged.items():
    target = root / 'app/src/main/baselineProfiles' / name
    target.write_text('# Source rules plus device-generated rules; benchmark output retained in CI.\n'
                      + '\n'.join(sorted(rules)) + '\n')
    print(name + ': ' + str(len(collected[name])) + ' collected app rules')
