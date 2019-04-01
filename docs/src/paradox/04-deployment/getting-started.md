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

# Getting Started with Knora

There is a quick installation guide [here](https://github.com/dhlab-basel/Knora/wiki/Quick-Installation-Guide-for-Knora,-Salsah,-Sipi-and-GraphDB).

## Choosing and Setting Up a Triplestore

Knora requires a standards-compliant
[RDF](https://www.w3.org/TR/rdf11-primer/) triplestore. A number of
triplestore implementations are available, including [free
software](http://www.gnu.org/philosophy/free-sw.en.html) as well as
proprietary options.

Knora is designed to work with any standards-compliant
triplestore. It is primarily tested with [Ontotext
GraphDB](http://ontotext.com/products/graphdb/), a high-performance,
proprietary triplestore. We recommend GraphDB Standard Edition, but
GraphDB Free (which is proprietary but available free of charge) also
works.

Knora includes support for [Apache Jena](https://jena.apache.org/),
which is [free software](http://www.gnu.org/philosophy/free-sw.en.html),
but use of Jena is deprecated, and support for it will probably be
removed in the future.

Built-in support and configuration for other triplestores is planned.

See the chapters on @ref:[Starting GraphDB](../05-internals/development/graphdb.md) and
@ref:[Starting Fuseki](../05-internals/development/fuseki.md) for more details.

### Creating Repositories and Loading Test Data

To create a test repository called `knora-test` and load test data into
it, go to `webapi/scripts` and run the script for the triplestore you
have chosen.

  - For GraphDB-SE:

    - If you are running GraphDB-SE directly from its installation directory (using the `bin/graphdb` script), run
      `graphdb-se-local-init-knora-test.sh`.
    - If you are running GraphDB-SE from a Docker image, run `graphdb-se-docker-init-knora-test.sh`.

  - For GraphDB-Free:
    
    - If you are running GraphDB-Free directly from its installation directory (using the `bin/graphdb` script), run
      `graphdb-free-local-init-knora-test.sh`.
    - If you are running GraphDB-Free from a Docker image, run `graphdb-free-docker-init-knora-test.sh`.

  - For Fuseki, run `fuseki-load-test-data.sh`.

You can create your own scripts based on these scripts, to create new
repositories and optionally to load existing Knora-compliant RDF data
into them.

If you are using GraphDB, you must create your repository using a
repository configuration file that specifies the file `KnoraRules.pie`
as its `owlim:ruleset`. This enables RDFS inference and Knora-specific
consistency rules. When using GraphDB, Knora uses RDFS
inference to improve query performance. The Knora-specific consistency
rules help ensure that your data is internally consistent and conforms
to the Knora ontologies.

When testing with GraphDB, you may sometimes get an error when loading
the test data that says that there are multiple IDs for the same
repository `knora-test`. In that case, something went wrong when
dropping and recreating the repository. You can solve this by deleting
the repository manually and starting over. **Make sure you don't delete
important data.** To delete the repository, stop GraphDB, delete the
`data` directory in your GraphDB installation, and restart GraphDB.

## Creating a Test Installation

### Run a supported triplestore

See the chapters on @ref:[Starting GraphDB](../05-internals/development/graphdb.md) and
@ref:[Starting Fuseki](../05-internals/development/fuseki.md) on how to start a supported
triplestore.

### Creating and running the WEBAPI Server distribution package

To create a deployment package for the WEBAPI Server, please run from
inside the top level `knora` folder the following commands:

    $ sbt webapi/stage
    $ cd webapi/target/universal/stage
    $ ./bin/webapi -J-Xms1G J-Xmx1G

### Downloading and running Sipi

Download [Sipi](https://github.com/dhlab-basel/Sipi) and
install from source by following the
[Sipi Manual](https://dhlab-basel.github.io/Sipi/documentation/index.html).
For running and setting up Sipi for Knora, please see
@ref:[The Sipi Media Server](../07-sipi/index.md).

## Selectively Disabling Routes

In `application.conf` the setting `app.routes-to-reject` contains a list
of strings, representing routes which should be rejected.

For Example, the string `"v1/users"` would lead to rejection of any
route which contains this string.

## Startup Flags

There is a number of flags that can be set on startup, they will
override any value set in the application configuration file:

  - `loadDemoData`, `--loadDemoData`, `-d`: Loads the demo data.
  - `allowReloadOverHTTP`, `--allow-reload-over-http`, `-r`: Allows
    reloading of data over HTTP.
  - `-p`: Starts the Prometheus monitoring reporter.
  - `-z`: Starts the Zipkin monitoring reporter.
  - `-j`: Starts the Jaeger monitoring reporter.
  - `-c`: Print the configuration at startup.
  - `--help`: Shows the help message with all startup flags.
