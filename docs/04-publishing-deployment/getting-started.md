<!---
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Getting Started with DSP-API

Running DSP-API locally or on a server requires [Docker](https://www.docker.com), which
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

DSP-API requires a standards-compliant
[RDF](https://www.w3.org/TR/rdf11-primer/) triplestore. A number of
triplestore implementations are available, including [free
software](http://www.gnu.org/philosophy/free-sw.en.html) as well as
proprietary options.

DSP-API is designed to work with any standards-compliant
triplestore. It is primarily tested with
[Apache Jena Fuseki](https://jena.apache.org), an open source triplestore.

Built-in support and configuration for a high-performance, proprietary
triplestore [Ontotext GraphDB](http://ontotext.com/products/graphdb/) is
provided but unmaintained (GraphDB must be licensed separately by the user).
Other triplestores are planned.

## Running the DSP-Stack

Use `git` to clone the DSP-API repository from [Github](https://github.com/dasch-swiss/dsp-api).

The following environment variables are **optional**:

- `KNORA_DB_HOME`: sets the path to the folder where the triplestore will store
the database files
- `KNORA_DB_IMPORT`: sets the path to the import directory accessible from
inside the docker image

```bash
$ export KNORA_DB_IMPORT=/path/to/some/folder
$ export KNORA_DB_HOME=/path/to/some/other_folder
```

Then from inside the cloned `DSP-API` repository folder, run:

```bash
$ make stack-up
```

## Creating Repositories and Loading Test Data

To create a test repository called `knora-test` and load test data, run:

```
$ make init-db-test
```

The scripts called by `make` can be found under `webapi/scripts`. You can
create your own scripts based on these scripts, to create new
repositories and optionally to load existing DSP-compliant RDF data
into them.

If you need to reload the test data, you need to stop and **delete** the
running Apache Fuseki instance. **Make sure you don't delete important data.**
To stop the instance and delete the repository, run the following command:

```
$ make stack-down-delete-volumes
```

after which you can start the stack again with `make stack-up`, recreate
the repository and load the data with `make init-db-test`.
