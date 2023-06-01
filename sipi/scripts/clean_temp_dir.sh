#!/bin/bash

set -eo pipefail

error_msg() {
  echo "$(date): failed cleaning temp dir" >> "$log_file"
}
trap error_msg ERR

# Clear log
log_file="/var/log/cleanTempDir.log"
> "$log_file"

echo "$(date): calling clean_temp_dir route" >> "$log_file"

# Call route
curl -u "${CLEAN_TMP_DIR_USER}:${CLEAN_TMP_DIR_PW}" -sS -L --fail 'http://localhost:1024/clean_temp_dir' >> "$log_file" 2>&1
if [ $? -ne 0 ]; then
  echo "$(date): route returned an error status" >> "$log_file"
  exit 1
fi

echo "$(date): successfully called clean_temp_dir route" >> "$log_file"
