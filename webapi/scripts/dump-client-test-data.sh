#!/usr/bin/env bash

# Downloads client test data from Redis into a Zip file.

set -e

pushd () {
    command pushd "$@" > /dev/null
}

popd () {
    command popd "$@" > /dev/null
}

hash_name=client-test-data
zip_file_name=client-test-data.zip

temp_dir=$(mktemp -d)
top_dir_name=test-data
top_dir_path=$temp_dir/$top_dir_name
mkdir -p $top_dir_path
rm -f $zip_file_name

redis-cli hkeys $hash_name | while read relative_file_path
do
  if [ -n "$relative_file_path" ]
  then
    abs_file_path="$top_dir_path/$relative_file_path"

    relative_dirs_to_create=$(dirname "$abs_file_path")
    mkdir -p "$relative_dirs_to_create"

    filename=$(basename "$abs_file_path")
    redis-cli hget $hash_name "$relative_file_path" > "$abs_file_path"
  fi
done

pushd $temp_dir
zip -rq $zip_file_name .
popd

mv $temp_dir/$zip_file_name .
rm -rf $temp_dir

redis-cli del $hash_name > /dev/null
