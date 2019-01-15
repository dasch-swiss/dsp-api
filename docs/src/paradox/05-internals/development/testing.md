<!---
Copyright © 2015-2019 the contributors (see Contributors.md).

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
       RdfDataObject(path = "_test_data/responders.v1.ValuesResponderV1Spec/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula")
)

"Load test data" in {
    storeManager ! ResetTriplestoreContent(rdfDataObjects)
    expectMsg(300.seconds, ResetTriplestoreContentACK())

    responderManager ! LoadOntologiesRequest(SharedTestDataV1.rootUser)
    expectMsg(10.seconds, LoadOntologiesResponse())
}
```

These tests should be stored inside the `src/test` folder hierarchy.

2)  Call the test from SBT:

```
// when using a GraphDB-SE
sbt:webapi> test

// when using a GraphDB-Free
sbt:webapi> GDBFree / test
```

## How to Write Integration Tests

The only difference between Integration and Unit tests is the location
where they are stored and the way how they are called:

1)  Store tests inside the `src/it` folder hierarchy.
2)  Call the tests from SBT: `sbt:webapi> it:test`

## How to Write Performance / Simulation Tests

1)  Store the performance and simulation tests inside the `src/test` and
    the `src/it` folder hierarchy.
2)  To call them, execute either `gatling:test` or `gatling-it:test`
    from inside SBT.

Bellow is an example of an simulation calling the `/admin/users`
endpoint. The simulation ramps up 1000 users over 5 seconds, all
accessing the `/admin/users`
endpoint.

@@snip[ExampleE2ESimSpec.scala](../../../../../webapi/src/test/scala/org/knora/webapi/e2e/ExampleE2ESimSpec.scala) { }


## Custom SBT Test Configurations

For convenience, there are a number of custom test configurations defined inside `WebapiBuild.sbt`. These can be used together with the
built-in test tasks like `test`, `testOnly`, `testQuick`.

For use with GraphDB-SE (using `test/resources/graphdb-se.conf`) running normal tests:

```
sbt:webapi> test
sbt:webapi> GDBSE / test
sbt:webapi> gdbse:test
```

For use with GraphDB-SE (using `it/resources/graphdb-se.conf`) running integration tests:

```
sbt:webapi> it:test
sbt:webapi> GDBSEIt / test
sbt:webapi> gdbse-it:test
```

For use with GraphDB-Free (using `test/resources/graphdb-free.conf`) running normal tests:

```
sbt:webapi> GDBFree / test
sbt:webapi> gdbfree:test
```

For use with GraphDB-Free (using `it/resources/graphdb-free.conf`) running integration tests:

```
sbt:webapi> GDBFreeIt / test
sbt:webapi> gdbfree-it:test
```

For use with Fuseki (using `test/resources/fuseki.conf`) running normal tests:

```
sbt:webapi> Fuseki / test
sbt:webapi> fuseki:test
```

For use with Fuseki (using `it/resources/fuseki.conf`) running integration tests:

```
sbt:webapi> FusekiIt / test
sbt:webapi> fuseki-it:test
```