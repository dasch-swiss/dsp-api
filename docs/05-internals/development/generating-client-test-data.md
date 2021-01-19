<!---
Copyright © 2015-2021 the contributors (see Contributors.md).

This file is part of Knora.

Knora is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published
by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Knora is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public
License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
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
