#!/usr/bin/env bash

# Removes client test data from Redis.

set -e

# The name of the Redis hash in which client test data is collected.
hash_name=client-test-data

# The name of the Zip file that contains client test data download from Redis.
zip_file_name=client-test-data.zip

# Remove the hash from Redis.
redis-cli del $hash_name > /dev/null

# Remove any existing client test data Zip file.
rm -f $zip_file_name
