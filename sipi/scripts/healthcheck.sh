#!/bin/bash

if [ -n "$CLEAN_TMP_DIR_CRON_SCHEDULE" ]; then
  pgrep -x "cron" >/dev/null
  if [ $? -ne 0 ]; then
    echo "Cron is not running"
    exit 1
  fi
fi

curl -sS --fail 'http://localhost:1024/server/test.html'
if [ $? -ne 0 ]; then
  echo "SIPI did not respond to /server/test.html route"
  exit 1
fi
