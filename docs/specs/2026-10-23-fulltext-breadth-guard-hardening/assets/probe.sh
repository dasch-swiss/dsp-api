#!/bin/bash
# usage: probe.sh "<lucene term>"  -> prints term, count, seconds
term="$1"
q="SELECT (COUNT(*) AS ?count) WHERE { { SELECT DISTINCT ?m WHERE { ?m <http://jena.apache.org/text#query> (\"$term\" 1000000) . } } }"
s=$(python3 -c 'import time;print(time.time())')
out=$(dsp vre sparql query -s stage --timeout 180 --query "$q" 2>&1)
e=$(python3 -c 'import time;print(time.time())')
c=$(echo "$out" | python3 -c 'import sys,json
try:
  d=json.load(sys.stdin);print(d["results"]["bindings"][0]["count"]["value"])
except Exception as ex: print("ERR")')
printf "%s\t%s\t%.2f\n" "$term" "$c" "$(python3 -c "print($e-$s)")"
