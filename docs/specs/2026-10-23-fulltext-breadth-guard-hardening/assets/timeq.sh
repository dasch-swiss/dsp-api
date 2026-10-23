#!/bin/bash
# timeq.sh <shape> <term> <runs> -> one TSV line per run: shape term run count seconds
shape="$1"; term="$2"; runs="$3"
python3 -I gen.py "$shape" "$term" > "q_${shape}.rq"
for i in $(seq 1 "$runs"); do
  s=$(python3 -c 'import time;print(time.time())')
  out=$(dsp vre sparql query -s stage --timeout 300 --query-file "q_${shape}.rq" 2>&1)
  e=$(python3 -c 'import time;print(time.time())')
  c=$(echo "$out" | python3 -c 'import sys,json
try:
  d=json.load(sys.stdin);print(d["results"]["bindings"][0]["count"]["value"])
except Exception as ex: print("ERR:"+sys.stdin.read()[:80].replace("\n"," "))')
  printf "%s\t%s\t%s\t%s\t%.2f\n" "$shape" "$term" "$i" "$c" "$(python3 -c "print($e-$s)")"
done
