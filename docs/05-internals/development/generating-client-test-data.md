<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Generating Client Test Data

## Requirements

Generate test requests and responses for Knora's routes, to be used in testing
client code without the need for a running Knora instance.
  
## Implementation

Client test data is generated as a side effect of running Knora's E2E tests.
E2E tests use `ClientTestDataCollector` to collect test API requests and
responses. The implementation of `ClientTestDataCollector` collects these
in a Redis hash. When the E2E tests have completed, the script
`webapi/scripts/dump-client-test-data.sh` saves the collected test data
in a Zip file. It then checks the filenames in the Zip file by comparing them
with the list in `webapi/scripts/expected-client-test-data.txt`.

## Usage

On macOS, you will need to install Redis in order to have the `redis-cli` command-line tool:

```
brew install redis
```

To generate client test data, type:

```
make client-test-data
```

When the tests have finished running, you will find the file
`client-test-data.zip` in the current directory.

If generated client test data changes, run `make client-test-data`, then run
this script to update the list of expected test data files:

```
webapi/scripts/update-expected-client-test-data.sh client-test-data.zip
```
