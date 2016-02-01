#!/usr/bin/env bash

docker run -i --name=build --rm=true -v /Users/subotic/_git.iml/rapier-scala:/builds/salsah-suite/rapier-scala -v /Users/subotic/.ivy2:/root/.ivy2 subotic/openrdf-sesame:2.8.7-sbt /bin/bash < build_sesame_script