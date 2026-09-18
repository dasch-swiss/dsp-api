#!/usr/bin/env bash
# Spike runner (Phase 3, DEV-7287). Runs one layout file on the stage triplestore via dsp-cli,
# prints wall-clock seconds and row count, and appends one line to results.csv.
#
# Usage: ./stage.sh <case> <layout> <run> <query-file>
#   e.g. ./stage.sh S1 A 1 S1-A.rq
# A dsp-cli failure (including the 120 s timeout) is recorded as seconds=120, rows=-1.
set -uo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
results="$here/results.csv"
[ -f "$results" ] || echo "case,layout,run,seconds,rows" > "$results"

case_id="$1"; layout="$2"; run="$3"; qfile="$4"
[ -f "$qfile" ] || qfile="$here/$qfile"

out="$here/last-$case_id-$layout.csv"
start=$(python3 -c 'import time;print(time.time())')
dsp vre sparql query -s stage --timeout 120 --accept csv --query-file "$qfile" > "$out" 2>/dev/null
rc=$?
end=$(python3 -c 'import time;print(time.time())')
secs=$(python3 -c "print(f'{$end-$start:.2f}')")

if [ $rc -ne 0 ]; then
  secs=120
  rows=-1
else
  rows=$(( $(wc -l < "$out") - 1 ))
fi

echo "$case_id,$layout,$run,$secs,$rows" | tee -a "$results"
