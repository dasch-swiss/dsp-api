#!/usr/bin/env bash

docker run -i --name=build --rm=true -v /Users/subotic/_git.iml/rapier-scala:/builds/salsah-suite/rapier-scala -v /Users/subotic/.ivy2:/root/.ivy2 subotic/graphdb-free:6.6.2-sbt /bin/bash < build_graphdb_free_script