#!/usr/bin/env bash

# Removes client test data from Redis.

set -e

hash_name=client-test-data
zip_file_name=client-test-data.zip

redis-cli del $hash_name > /dev/null
rm -f $zip_file_name
