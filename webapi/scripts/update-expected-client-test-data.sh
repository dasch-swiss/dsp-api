#!/usr/bin/env bash

# Updates the file listing the expected contents of the Zip file of client test data.

# The name of the Zip file that will contain client test data download from Redis.
zip_file_name=client-test-data.zip

# A file listing the expected contents of that Zip file.
expected_contents_file=$(dirname "$0")/expected-client-test-data.txt

zipinfo -1 $zip_file_name | sort > $expected_contents_file
