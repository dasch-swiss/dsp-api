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

Running Knora locally or on a server requires [Docker](https://www.docker.com), which
can be freely downloaded. Please follow the instructions for installing
[Docker Desktop](https://www.docker.com/products/docker-desktop).

Additional software:

- git
- expect
- sbt

These can be easily installed on macOS using [Homebrew](https://brew.sh):

```bash
$ brew install git
$ brew install expect
$ brew install sbt
```

## Choosing a Triplestore

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
works, where both need to be licensed separately from
Ontotext (http://ontotext.com). GraphDB-Free can be simply licensed by filling out
their registration from.

Built-in support and configuration for other triplestores is planned.

## Running the Knora-Stack

Use `git` to clone the Knora repository from [Github](https://github.com/dhlab-basel/Knora).

After having GraphDB licensed, you need to set some environment variables, based
on the GraphDB version that you have licensed:

**GraphDB-Free**:

```bash
$ export KNORA_GDB_TYPE=graphdb-free
$ export KNORA_GDB_HOME=/path/to/some/folder // optional. default is './triplestores/graphdb/home'
```

**GraphDB-SE**:
```bash
$ export KNORA_GDB_TYPE=graphdb-se // optional. 'graphdb-se' is the default value
$ export KNORA_GDB_TYPE=/path/to/license/file // path to the GraphDB-SE license file. default is './triplestores/graphdb/graphdb.license'
$ export KNORA_GDB_HOME=/path/to/some/folder // optional. default is './triplestores/graphdb/home'
```

Then from inside the cloned `Knora` repository folder, run:

```bash
$ sbt
> dockerComposeUp
```

After some time, you should see the following output:

```
The following endpoints are available for your local instance: 335114
+---------+-----------+------------------+--------------+----------------+--------------+---------+
| Service | Host:Port | Tag Version      | Image Source | Container Port | Container Id | IsDebug |
+=========+===========+==================+==============+================+==============+=========+
| graphdb | :7200     | 8.0.0-7-ga7827e9 | build        | 7200           | 95d29c651090 |         |
| redis   | :6379     | 5                | defined      | 6379           | 1b47c3d27362 |         |
| salsah1 | :3335     | 8.0.0-7-ga7827e9 | build        | 3335           | 1795a58a4dd6 |         |
| sipi    | :1024     | 8.0.0-7-ga7827e9 | build        | 1024           | 8b916936bb38 |         |
| webapi  | :3333     | 8.0.0-7-ga7827e9 | build        | 3333           | 629588f04066 |         |
| webapi  | :10001    | 8.0.0-7-ga7827e9 | build        | 10001          | 629588f04066 |         |
+---------+-----------+------------------+--------------+----------------+--------------+---------+
Instance commands:
1) To stop instance from sbt run:
   dockerComposeStop 335114
2) To open a command shell from bash run:
   docker exec -it <Container Id> bash
3) To view log files from bash run:
   docker-compose -p 335114 -f /var/folders/y5/lzx7l8210lnd52jz23_tbztm0000gn/T/compose-updated8134225889726981844.yml logs -f
4) To execute test cases against instance from sbt run:
   dockerComposeTest 335114
```

## Creating Repositories and Loading Test Data

To create a test repository called `knora-test` and load test data into
it, go to `webapi/scripts` and run the script for the triplestore you
have chosen.

  - For GraphDB-SE, run `graphdb-se-docker-init-knora-test.sh`.

  - For GraphDB-Free, run `graphdb-free-docker-init-knora-test.sh`.

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

This file is already packaged inside
Knora's Docker images for GraphDB-SE and GraphDB-Free.

When testing with GraphDB, you may sometimes get an error when loading
the test data that says that there are multiple IDs for the same
repository `knora-test`. In that case, something went wrong when
dropping and recreating the repository. You can solve this by deleting
the repository manually and starting over. **Make sure you don't delete
important data.** To delete the repository, stop GraphDB, delete the
`data` directory in your GraphDB installation, and restart GraphDB.
