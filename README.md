[![Build Status](https://travis-ci.org/dhlab-basel/Knora.svg?branch=develop)](https://travis-ci.org/dhlab-basel/Knora)
[![codecov](https://codecov.io/gh/dhlab-basel/Knora/branch/develop/graph/badge.svg)](https://codecov.io/gh/dhlab-basel/Knora)
# Knora

[Knora](http://www.knora.org/) (Knowledge Organization, Representation, and Annotation) is a server
application for storing, sharing, and working with primary sources and data in the humanities.

It is developed by the [Digital Humanities Lab](http://www.dhlab.unibas.ch/) at the [University of Basel](https://www.unibas.ch/en.html), and is supported by the [Swiss Academy of Humanities and Social Sciences](http://www.sagw.ch/en/sagw.html).

Knora is [free software](http://www.gnu.org/philosophy/free-sw.en.html), released under the [GNU Affero General Public License](http://www.gnu.org/licenses/agpl-3.0.en.html).

## Features

* Stores humanities data as industry-standard [RDF](http://www.w3.org/TR/2014/NOTE-rdf11-primer-20140624/) graphs, plus files for binary data such as digitized primary sources.
    * Designed to work with any standards-compliant RDF triplestore. Tested with [Ontotext GraphDB](http://ontotext.com/products/graphdb/).
* Based on [OWL](http://www.w3.org/TR/2012/REC-owl2-primer-20121211/) ontologies that express abstract, cross-disciplinary commonalities in the structure and semantics of research data.
* Offers a generic HTTP-based API, implemented in [Scala](http://www.scala-lang.org/), for querying, annotating, and linking together heterogeneous data in a unified way.
    * Handles authentication and authorization.
    * Provides automatic versioning of data.
* Uses [Sipi](http://www.sipi.io/), a high-performance media server implemented in C++.
* Designed to be be used with [SALSAH](https://dhlab-basel.github.io/Salsah/), a general-purpose, browser-based virtual research environment,
  as well as with custom user interfaces.

## Status

### Stable

* [Knora Ontologies](https://docs.knora.org/paradox/02-knora-ontologies/index.html)
* [Knora API v1](https://docs.knora.org/paradox/03-apis/api-v1/index.html)

### Beta stage

* [Knora Admin API](https://docs.knora.org/paradox/03-apis/api-admin/index.html)
* Distribution packaging using [Docker](https://www.docker.com/)

### New features under development

* [Knora API v2](https://docs.knora.org/paradox/03-apis/api-v2/index.html)

## Requirements

### For developing and testing the API server

* Linux or Mac OS X
* [Java Development Kit 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
* [SBT](http://www.scala-sbt.org/)

[Ontotext GraphDB](http://ontotext.com/products/graphdb/) is recommended. Support for
other RDF triplestores is planned.

### For building the documentation

See [docs/Readme.md](docs/Readme.md).

## Try it out

### Quick Installation Guide for Knora, Salsah, Sipi and GraphDB
A manual to get all mentioned components locally up and running can be found [here](https://github.com/dhlab-basel/Knora/wiki/Quick-Installation-Guide-for-Knora,-Salsah,-Sipi-and-GraphDB).

### Run the Knora API server

With [Docker](https://www.docker.com/) installed, start the [GraphDB Free](http://graphdb.ontotext.com/documentation/free/) triplestore:

```
$ docker run --rm -p 7200:7200 dhlabbasel/graphdb-free
```

Then in another terminal, create a test repository and load some test data into the triplestore:

```
$ cd webapi/scripts
$ ./graphdb-free-docker-init-knora-test.sh
```

Then go back to the webapi root directory and use SBT to start the API server:

```
$ cd ..
$ sbt
> compile
> set reStart / javaOptions ++= Seq("-Dapp.triplestore.dbtype=graphdb-free")
> reStart
```

Then try opening [http://localhost:3333/v1/resources/http%3A%2F%2Frdfh.ch%2Fc5058f3a](http://localhost:3333/v1/resources/http%3A%2F%2Frdfh.ch%2Fc5058f3a) in a web browser. You should see a response in JSON describing a book.

To shut down the Knora API server:

```
> reStop
```

### Run the automated tests

Make sure you've started GraphDB Free as shown above. Create an empty repository for running the automated tests:

```
$ cd webapi/scripts
$ ./graphdb-free-init-knora-test-unit.sh
```

Then at the SBT prompt:

```
> GDBFree / test
```

## How to Contribute

You can help by testing Knora with your data, making bug reports, improving the documentation, and adding features that you need.

First, open an [issue](https://github.com/dhlab-basel/Knora/issues) to describe your problem or idea. We may ask you to submit a [pull request](https://help.github.com/articles/about-pull-requests/) implementing the desired functionality.

### Coding conventions

Use `camelCase` for names of classes, variables, and functions. Make names descriptive, and don't worry if they're long.

Format your code consistently. We [IntelliJ IDEA](https://www.jetbrains.com/idea/) to format code, with 4 spaces indentation. Use whitespace to make your code easier to read. Add lots of implementation comments describing what your code is doing, how it works, and why it works that way.

### Tests

We write automated tests using [ScalaTest](http://www.scalatest.org/). You can run them from the [SBT](http://www.scala-sbt.org/) console.

There are three sets of automated tests:

* Unit tests, route-to-route tests, and end-to-end tests are under `webapi/src/test`. To run these, type `graphdb:test` or `graphdb-free:test` (depending on which triplestore you're using) at the SBT console in the `webapi` project. To run a single test, use `graphdb:test-only *NameOfTestSpec`.
* Integration tests, which can involve [Sipi](https://github.com/dhlab-basel/Sipi), are under `src/it`. To run these, first start Sipi, then type `it:test` at the SBT console in the `webapi` project.
* Browser interaction tests are under `salsah/src/test`, and are written using [Selenium](http://www.seleniumhq.org/). To run these, you will need to unpack the correct [ChromeDriver](https://sites.google.com/a/chromium.org/chromedriver/) for your platform found under `salsah/lib/chromedriver` and put it in the same folder. Then start Sipi and the Knora API server, and type `test` at the SBT console in the `salsah` project.

Whenever you add a new feature or fix a bug, you should add one or more tests for the change you made.

### Documentation

A pull request should include tests and documentation for the changes that were made. See the [documentation README](https://github.com/dhlab-basel/Knora/blob/develop/docs/Readme.md) for information on writing Knora documentation.

## Contact information

### Technical

Please use the [knora-user](https://www.maillist.unibas.ch/mailman/listinfo/knora-user) mailing list for technical questions.

### Administrative

Lukas Rosenthaler `<lukas.rosenthaler@unibas.ch>`

## Commit Message Schema

When writing commit messages, we stick to this schema:

```
type (scope): subject
body
```

Types:

- feature (new feature for the user)
- fix (bug fix for the user)
- docs (changes to the documentation)
- style (formatting, etc; no production code change)
- refactor (refactoring production code, eg. renaming a variable)
- test (adding missing tests, refactoring tests; no production code change)
- build (changes to sbt tasks, CI tasks, deployment tasks, etc.; no production code changes)
- enhancement (residual category)

Example:

```
feature (resources route): add route for resource creation
- add path for multipart request
- adapt handling of resources responder

```

## Acknoledgments

![](https://www.yourkit.com/images/yklogo.png)

The Knora project is using YourKit for profiling.

YourKit supports open source projects with its full-featured Java Profiler.
YourKit, LLC is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>
and <a href="https://www.yourkit.com/.net/profiler/">YourKit .NET Profiler</a>,
innovative and intelligent tools for profiling Java and .NET applications.
