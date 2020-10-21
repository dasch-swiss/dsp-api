#!/usr/bin/env bash

# Updates the file listing the expected contents of the Zip file of client test data.

zip_file="$1"

if [[ -z "${zip_file}" ]]; then
  echo "Usage: $(basename "$0") ZIP_FILE"
  exit 1
fi

# A file listing the expected contents of that Zip file.
expected_contents_file=$(dirname "$0")/expected-client-test-data.txt

zipinfo -1 $zip_file | sort > $expected_contents_file
