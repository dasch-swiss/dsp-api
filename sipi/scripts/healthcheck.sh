#!/bin/bash

curl -sS --fail 'http://localhost:1024/server/test.html'
if [ $? -ne 0 ]; then
  echo "SIPI did not respond to /server/test.html route"
  exit 1
fi
