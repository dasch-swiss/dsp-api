#!/usr/bin/env bash

extension=tdump
if [ $# -eq 0 ]; then
    echo >&2 "Usage: jstackSeriesSum <pid> [ <suffix> [ <count> [ <delay> ] ] ]"
    echo >&2 "    Defaults: suffix = "dump", count = 10, delay = 60 (seconds)"
    exit 1
fi
pid=$1          # required

suffix=${2:-dump} # defaults to "dump"
count=${3:-10}  # defaults to 10 times
delay=${4:-60} # defaults to 60 seconds
echo $pid $suffix $count $delay
while [ $count -gt 0 ]
do
    jstack -l $pid >jstack.$suffix.$pid.$(date +%H%M%S).$extension
    sleep $delay
    let count--
    echo -n "."
done

dispatcher=akka.actor.default-dispatcher
rediscala=rediscala.rediscala-client-worker-dispatcher
global=ForkJoinPool-2-worker
common=ForkJoinPool.commonPool-worker
apache=default-workqueue
statsd=StatsD-pool
log=logback

echo "" > ./tmp.txt
for f in *$suffix*.tdump
do
echo "===========> $f" >> ./tmp.txt
echo "$dispatcher: $(grep "$dispatcher" $f | wc -l)" >> ./tmp.txt
echo "$rediscala: $(grep "$rediscala" $f | wc -l)" >> ./tmp.txt
#scala.concurrent.ExecutionContext.Implicits.global
echo "$global: $(grep "$global" $f | wc -l)" >> ./tmp.txt
#java.util.concurrent.ForkJoinPool.common
echo "$common: $(grep "$common" $f | wc -l)" >> ./tmp.txt
echo "$apache: $(grep "$apache" $f | wc -l)" >> ./tmp.txt
echo "$statsd: $(grep "$statsd" $f | wc -l)" >> ./tmp.txt
echo "$log: $(grep "$log" $f | wc -l)" >> ./tmp.txt
done

echo "===========  FILES ===========" > result.$suffix.txt
grep ".$extension" ./tmp.txt >> result.$suffix.txt
echo "===========  START ===========" >> result.$suffix.txt
grep "$dispatcher" ./tmp.txt >> result.$suffix.txt
echo "=======================" >> result.$suffix.txt
grep "$rediscala" ./tmp.txt >> result.$suffix.txt
echo "=======================" >> result.$suffix.txt
grep "$global" ./tmp.txt >> result.$suffix.txt
echo "=======================" >> result.$suffix.txt
grep "$common" ./tmp.txt >> result.$suffix.txt
echo "=======================" >> result.$suffix.txt
grep "$apache" ./tmp.txt >> result.$suffix.txt
echo "=======================" >> result.$suffix.txt
grep "$statsd" ./tmp.txt >> result.$suffix.txt
echo "=======================" >> result.$suffix.txt
grep "$log" ./tmp.txt >> result.$suffix.txt
echo "===========  END ===========" >> result.$suffix.txt

rm ./tmp.txt
