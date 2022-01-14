<!---
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Testing

Prerequisite: Before running any tests, a supported triplestore needs to
be started and initialized through a script inside the "scripts" folder.
For example, when using "GraphDB Free", the nedded script is
"graphdb-free-init-knora-test-unit.sh". Please note the occurrence of
"test-unit" in the name of the script.

## How to Write Unit Tests

1)  Inside a test, at the beginning, add the following (change the paths
    to the test data as needed):

```scala
val rdfDataObjects = List (
       RdfDataObject(path = "test_data/responders.v1.ValuesResponderV1Spec/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula")
)
```
The data will be automatically loaded before any tests are executed. These tests should be stored inside
the `src/test` folder hierarchy.

2)  Call the test from terminal:

```
$ make test-unit
$ make test-e2e
```

## How to Write Integration Tests

The only difference between Integration and Unit tests is the location
where they are stored and the way how they are called:

1)  Store tests inside the `src/it` folder hierarchy.
2)  Call the tests from the terminal: `make test-it`
