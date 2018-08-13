#!/usr/bin/env bash

extension=tdump
if [ $# -eq 0 ]; then
    echo >&2 "Usage: jstackSeriesSum <pid> [ <suffix> [ <count> [ <delay> ] ] ]"
    echo >&2 "    Defaults: suffix = "dump", count = 720, delay = 60 (seconds)"
    exit 1
fi
pid=$1          # required

suffix=${2:-dump} # defaults to "dump"
count=${3:-720}  # defaults to 720 times
delay=${4:-60} # defaults to 60 seconds
echo $pid $suffix $count $delay
while [ $count -gt 0 ]
do
    jstack -l $pid >jstack.$suffix.$pid.$(date +%H%M%S).$extension
    sleep $delay
    let count--
    echo -n "."
done

global=ForkJoinPool-2-worker
common=ForkJoinPool.commonPool-worker
akkadefault=akka.actor.default-dispatcher
akkadefaultblocking=akka.actor.default-blocking-io-dispatcher
akkapinned=akka.io.pinned-dispatcher
akkastreamblocking=akka.stream.default-blocking-io-dispatcher
triplestore=my-httpTriplestoreConnector-dispatcher
admin=my-admin-dispatcher
v1=my-v1-dispatcher
v2=my-v2-dispatcher
metrics=metrics-console-reporter
kamon=kamon-scheduler


echo "" > ./tmp.txt
for f in *$suffix*.tdump
do
echo "===========> $f" >> ./tmp.txt
#scala.concurrent.ExecutionContext.Implicits.global
echo "$global: $(grep "$global" $f | wc -l)" >> ./tmp.txt
#java.util.concurrent.ForkJoinPool.common
echo "$common: $(grep "$common" $f | wc -l)" >> ./tmp.txt
echo "$akkadefault: $(grep "$akkadefault" $f | wc -l)" >> ./tmp.txt
echo "$akkadefaultblocking: $(grep "$akkadefaultblocking" $f | wc -l)" >> ./tmp.txt
echo "$akkapinned: $(grep "$akkapinned" $f | wc -l)" >> ./tmp.txt
echo "$akkastreamblocking: $(grep "$akkastreamblocking" $f | wc -l)" >> ./tmp.txt
echo "$triplestore: $(grep "$triplestore" $f | wc -l)" >> ./tmp.txt
echo "$admin: $(grep "$admin" $f | wc -l)" >> ./tmp.txt
echo "$v1: $(grep "$v1" $f | wc -l)" >> ./tmp.txt
echo "$v2: $(grep "$v2" $f | wc -l)" >> ./tmp.txt
echo "$metrics: $(grep "$metrics" $f | wc -l)" >> ./tmp.txt
echo "$kamon: $(grep "$kamon" $f | wc -l)" >> ./tmp.txt
done

echo "===========  FILES ===========" > result.$suffix.txt
grep ".$extension" ./tmp.txt >> result.$suffix.txt
echo "===========  START ===========" >> result.$suffix.txt
grep "$global" ./tmp.txt >> result.$suffix.txt
echo "=======================" >> result.$suffix.txt
grep "$common" ./tmp.txt >> result.$suffix.txt
echo "=======================" >> result.$suffix.txt
grep "$akkadefault" ./tmp.txt >> result.$suffix.txt
echo "=======================" >> result.$suffix.txt
grep "$akkadefaultblocking" ./tmp.txt >> result.$suffix.txt
echo "=======================" >> result.$suffix.txt
grep "$akkapinned" ./tmp.txt >> result.$suffix.txt
echo "=======================" >> result.$suffix.txt
grep "$akkastreamblocking" ./tmp.txt >> result.$suffix.txt
echo "=======================" >> result.$suffix.txt
grep "$triplestore" ./tmp.txt >> result.$suffix.txt
echo "=======================" >> result.$suffix.txt
grep "$admin" ./tmp.txt >> result.$suffix.txt
echo "=======================" >> result.$suffix.txt
grep "$v1" ./tmp.txt >> result.$suffix.txt
echo "=======================" >> result.$suffix.txt
grep "$v2" ./tmp.txt >> result.$suffix.txt
echo "=======================" >> result.$suffix.txt
grep "$metrics" ./tmp.txt >> result.$suffix.txt
echo "=======================" >> result.$suffix.txt
grep "$kamon" ./tmp.txt >> result.$suffix.txt
echo "===========  END ===========" >> result.$suffix.txt

rm ./tmp.txt
