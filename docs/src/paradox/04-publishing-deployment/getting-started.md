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

- [Apple Xcode](https://itunes.apple.com/us/app/xcode/id497799835)
- git
- expect
- sbt
- java 11

These can be easily installed on macOS using [Homebrew](https://brew.sh):

```bash
$ brew install git
$ brew install expect
$ brew install sbt
```

To install Adoptopenjdk Java 11 with [Homebrew](https://brew.sh):

```bash
$ brew tap AdoptOpenJDK/openjdk
$ brew cask install AdoptOpenJDK/openjdk/adoptopenjdk11
```

To pin the version of Java, please add this environment variable to you startup script (bashrc, etc.):

```
export JAVA_HOME=`/usr/libexec/java_home -v 11`
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

After having GraphDB licensed, you need to set some environment variables:

**GraphDB-Free**:

The following environment variables are **optional**:

```bash
$ export KNORA_GDB_IMPORT=/path/to/some/folder - sets the path to the import directory accessible from inside the GraphDB Workbench
$ export KNORA_GDB_HOME=/path/to/some/other_folder // sets the path to the folder where GraphDB will store the database files
```

**GraphDB-SE**:

The following environment variable is **required**:

```bash
export KNORA_GDB_LICENSE=/path/to/license/file - sets the path to the GraphDB-SE license file
```

The following environment variables are **optional**:

```bash
$ export KNORA_GDB_IMPORT=/path/to/some/folder - sets the path to the import directory accessible from inside the GraphDB Workbench
$ export KNORA_GDB_HOME=/path/to/some/other_folder // sets the path to the folder where GraphDB will store the database files
```

Then from inside the cloned `Knora` repository folder, run:

```bash
$ make stack-up
```

## Creating Repositories and Loading Test Data

To create a test repository called `knora-test` and load test data, run:

  - For GraphDB-SE: `$ make init-db-test`.

  - For GraphDB-Free: `$ make init-db-test-free`.

The scripts called by `make` can be found under `webapi/scripts`. You can
create your own scripts based on these scripts, to create new
repositories and optionally to load existing Knora-compliant RDF data
into them.

If you are using GraphDB, you must create your repository using a
repository configuration file that specifies the file `KnoraRules.pie`
as its `owlim:ruleset`. This enables RDFS inference and Knora-specific
consistency rules. When using GraphDB, Knora uses RDFS inference to improve
query performance. The Knora-specific consistency rules help ensure that your
data is internally consistent and conforms to the Knora ontologies.

This file is already packaged inside Knora's Docker images for GraphDB-SE and
GraphDB-Free.

When testing with GraphDB, you may sometimes get an error when loading
the test data that says that there are multiple IDs for the same
repository `knora-test`. In that case, something went wrong when
dropping and recreating the repository. You can solve this by deleting
the repository manually and starting over. **Make sure you don't delete
important data.** To delete the repository, stop GraphDB, delete the
`data` directory in your GraphDB installation, and restart GraphDB.

