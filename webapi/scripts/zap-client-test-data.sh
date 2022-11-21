#!/usr/bin/env bash

# Removes client test data.

set -e

# The name of the Zip file that contains client test data download from cache.
zip_file_name=client-test-data.zip

# Remove any existing client test data Zip file.
rm -f $zip_file_name

# Remove the temporary directory containing the generated test data.
rm -rf /tmp/client_test_data/
