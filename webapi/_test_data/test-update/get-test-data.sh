#!/usr/bin/env bash

curl -X GET -H "Accept:application/x-trig" "http://localhost:7200/repositories/knora-test/statements?infer=false" > knora-test.trig
