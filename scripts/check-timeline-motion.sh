#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
command -v kotlinc >/dev/null || { echo 'Kotlin CLI (kotlinc) is required.' >&2; exit 1; }
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
kotlinc "$root/app/src/main/kotlin/com/personal/wo/ui/TimelineMotion.kt" \
  "$root/scripts/TimelineMotionCheck.kt" -include-runtime -d "$work/timeline-motion.jar"
java -jar "$work/timeline-motion.jar"
