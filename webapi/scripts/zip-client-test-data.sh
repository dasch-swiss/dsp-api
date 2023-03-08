#!/usr/bin/env bash

# Downloads client test data from cache into a Zip file.

# A pushd that produces no output.
pushd () {
    command pushd "$@" > /dev/null
}

# A popd that produces no output.
popd () {
    command popd "$@" > /dev/null
}

# The name of the Zip file that will contain the client test data.
zip_file_name=client-test-data.zip

# A file listing the expected contents of that Zip file.
expected_contents_file=$(dirname "$0")/expected-client-test-data.txt

# The temporary directory containing the generated test data.
temp_dir=/tmp/client_test_data

# The name of the top-level directory inside the temporary directory.
top_dir_name=test-data

# The absolute path of the top-level directory.
top_dir_path=$temp_dir/$top_dir_name
mkdir -p $top_dir_path

# Remove any existing client test data Zip file.
rm -f $zip_file_name

# Create the Zip file.
pushd $temp_dir
zip -rq $zip_file_name .
popd

# Move the Zip file to the directory in which this script was run.
mv $temp_dir/$zip_file_name .

# Delete the temporary directory.
rm -rf $temp_dir

# Make a temporary file to hold the file paths in the Zip file.
temp_contents=$(mktemp)

# Get the file paths from the Zip file.
zipinfo -1 $zip_file_name | sort > $temp_contents

# Compare them to the expected file paths.
diff -q $expected_contents_file $temp_contents > /dev/null
diff_result=$?

# Return an error if they're different.

if [ $diff_result -ne 0 ]; then
  echo "Generated client test data archive $zip_file_name does not have the expected contents:"
  diff -u $expected_contents_file $temp_contents
else
  echo "Generated client test data archive: $zip_file_name"
fi

rm -f $temp_contents
exit $diff_result
