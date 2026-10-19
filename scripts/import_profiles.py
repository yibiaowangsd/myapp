#!/usr/bin/env python3
"""Import real device-generated app rules while preserving the reviewed source seed rules."""
import argparse
from pathlib import Path

parser=argparse.ArgumentParser()
parser.add_argument("outputs",type=Path)
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
for name in ("baseline-prof.txt","startup-prof.txt"):
    paths=list(args.outputs.rglob("*"+name))
    if not paths:raise SystemExit("No generated "+name+" found")
    target=root/"app/src/main/baselineProfiles"/name
    current=target.read_text().splitlines() if target.exists() else []
    collected={line.strip() for p in paths for line in p.read_text().splitlines()
               if "Lcom/personal/wo/" in line and not line.lstrip().startswith("#")}
    if not collected:raise SystemExit("No app-owned rules in "+name)
    rules={line for line in current if line and not line.startswith("#")}|collected
    if name=="startup-prof.txt" and any("*" in line for line in rules):raise SystemExit("Startup Profiles require exact bytecode descriptors")
    target.write_text("# Source rules plus device-generated rules; benchmark output retained in CI.\n"+"\n".join(sorted(rules))+"\n")
    print(name+": "+str(len(collected))+" collected app rules")
