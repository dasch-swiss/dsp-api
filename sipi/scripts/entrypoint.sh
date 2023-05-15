#!/bin/bash

set -o pipefail

if [ -n "$CLEAN_TMP_DIR_CRON_SCHEDULE" ]; then
  parts=$(echo "$CLEAN_TMP_DIR_CRON_SCHEDULE" | wc -w)
  command="/bin/bash /sipi/scripts/clean_temp_dir.sh"

  # Validate and install clean temp dir crontab
  [ "$parts" -eq 5 ] && (crontab -l 2>/dev/null; echo "$CLEAN_TMP_DIR_CRON_SCHEDULE $command") | crontab - 2>/dev/null
  if [ $? -ne 0 ]; then
    echo "Invalid clean temp dir cron schedule: $CLEAN_TMP_DIR_CRON_SCHEDULE" >&2
    exit 1
  fi

  # Start cron process in background
  cron &
fi

# Start SIPI
cd /sipi && ./sipi "$@"
