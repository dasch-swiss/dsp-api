#!/usr/bin/env bash

curl -X DELETE -u test:xyzzy http://localhost:10035/repositories/knora-test-unit
curl -X PUT -u test:xyzzy http://localhost:10035/repositories/knora-test-unit
