[![Build Status](https://github.com/dasch-swiss/knora-api/workflows/CI/badge.svg?branch=develop)](https://github.com/dasch-swiss/knora-api/actions)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/7e7c734a37ef403a964345e29106b267)](https://app.codacy.com/app/dhlab-basel/Knora?utm_source=github.com&utm_medium=referral&utm_content=dhlab-basel/Knora&utm_campaign=Badge_Grade_Dashboard)
# Knora

[Knora](https://www.knora.org/) (Knowledge Organization, Representation, and
Annotation) is a server application for storing, sharing, and working with
primary sources and data in the humanities.

It is developed by the [Data and Service Center for the Humanities](https://dasch.swiss)
at the [University of Basel](https://www.unibas.ch), and is supported by the
[Swiss Academy of Humanities and Social Sciences](https://www.sagw.ch) and
the [Swiss National Science Foundation](https://snf.ch).

Knora is [free software](http://www.gnu.org/philosophy/free-sw.en.html),
released under the [GNU Affero General Public License](http://www.gnu.org/licenses/agpl-3.0.en.html).

## Features

* Stores humanities data as industry-standard [RDF](http://www.w3.org/TR/2014/NOTE-rdf11-primer-20140624/) graphs, plus files for binary data such as digitized primary sources.
    * Designed to work with any standards-compliant RDF triplestore. Tested with [Jena Fuseki](https://jena.apache.org/).
* Based on [OWL](http://www.w3.org/TR/2012/REC-owl2-primer-20121211/) ontologies that express abstract, cross-disciplinary commonalities in the structure and semantics of research data.
* Offers a generic HTTP-based API, implemented in [Scala](https://www.scala-lang.org/), for querying, annotating, and linking together heterogeneous data in a unified way.
    * Handles authentication and authorization.
    * Provides automatic versioning of data.
* Uses [Sipi](https://www.sipi.io), a high-performance media server implemented in C++.
* Designed to be be used with [DSP-APP](https://docs.dasch.swiss/user-guide/), a general-purpose, browser-based virtual research environment,
  as well as with custom user interfaces.

## Status

### Stable

* [Knora Ontologies](https://docs.knora.org/02-knora-ontologies/)
* [Knora API v1](https://docs.knora.org/03-apis/api-v1/)

### Beta stage

* [Knora API v2](https://docs.knora.org/03-apis/api-v2/)
* [Knora Admin API](https://docs.knora.org/03-apis/api-admin/)
* Distribution packaging using [Docker](https://www.docker.com/)

### New features under development

* See the [Roadmap](https://github.com/dasch-swiss/knora-api/wiki/Roadmap)

## Requirements

### For developing and testing the API server

Each developer machine should have the following prerequisites installed:

* Linux or macOS (with some caveats)
* Docker Desktop: https://www.docker.com/products/docker-desktop
* Homebrew (on macOS): https://brew.sh
* [OpenJDK](https://adoptopenjdk.net) 11
* [Bazel](https://bazel.build)

#### Java Adoptopenjdk 11

To install, follow these steps:

```bash
$ brew tap AdoptOpenJDK/openjdk
$ brew cask install AdoptOpenJDK/openjdk/adoptopenjdk11
```

To pin the version of Java, please add this environment variable to you startup script (bashrc, etc.):

```
export JAVA_HOME=`/usr/libexec/java_home -v 11`
```

#### Bazel build tools

To install, follow these steps:

```
$ npm install -g @bazel/bazelisk
$ npm install -g @bazel/buildozer
$ npm install -g @bazel/buildifier
```

This will install [bazelisk](https://github.com/bazelbuild/bazelisk) which is
a wrapper to the `bazel` binary. It will, when `bazel` is run on the command line,
automatically install the supported Bazel version, defined in the `.bazelversion`
file in the root of the `knora-api` repository.

### For building the documentation

See [docs/Readme.md](docs/Readme.md).

## Try it out

### Run the Knora API server

With [Bazel](https://docs.bazel.build/versions/3.3.0/install-os-x.html) and
[Docker](https://www.docker.com) installed, run the following to create a test
repository and load some test data into the triplestore:

```
$ make init-db-test
```

Then we need to start knora-api after loading the data:

```
$ make stack-up
```

Then try opening [http://localhost:3333/v1/resources/http%3A%2F%2Frdfh.ch%2F0803%2Fc5058f3a](http://localhost:3333/v1/resources/http%3A%2F%2Frdfh.ch%2F0803%2Fc5058f3a) in a web browser. You should see a response in JSON describing a book.

To shut down the Knora-Stack:

```
$ make stack-down
```

### Run the automated tests

Run :

```
$ make test
```

### Running with Custom Folders

The `$ make stack-up` target can be additionally configured thorough the
following environment variables:

- `KNORA_DB_HOME`: sets the path to the folder where the triplestore will store
the database files
- `KNORA_DB_IMPORT`: sets the path to the import directory accessible from
inside the docker image

If the import and/or data directory are not set, then Docker volumes will be
used instead. Be aware on macOS, that setting the `KNORA_DB_HOME` has a
significant negative impact on performance, because of how synchronization with
the VM, in which docker is running, is implemented.

## How to Contribute

You can help by testing Knora with your data, making bug reports, improving the
documentation, and adding features that you need.

First, open an [issue](https://github.com/dasch-swiss/knora-api/issues) to
describe your problem or idea. We may ask you to submit a
[pull request](https://help.github.com/articles/about-pull-requests/)
implementing the desired functionality.

### Coding conventions

Use `camelCase` for names of classes, variables, and functions. Make names descriptive, and don't worry if they're long.

Format your code consistently. We [IntelliJ IDEA](https://www.jetbrains.com/idea) to format code, with 4 spaces indentation. Use whitespace to make your code easier to read. Add lots of implementation comments describing what your code is doing, how it works, and why it works that way.

### Tests

We write automated tests using [ScalaTest](https://www.scalatest.org). You can run them from the [SBT](https://www.scala-sbt.org) console.

There are three sets of automated tests:

* Unit tests, route-to-route tests, and end-to-end tests are under `webapi/src/test`. To run these, type `graphdb:test` or `graphdb-free:test` (depending on which triplestore you're using) at the SBT console in the `webapi` project. To run a single test, use `graphdb:test-only *NameOfTestSpec`.
* Integration tests, which can involve [Sipi](https://github.com/daschswiss/sipi), are under `src/it`. To run these, first start Sipi, then type `it:test` at the SBT console in the `webapi` project.
* Browser interaction tests are under `salsah/src/test`, and are written using [Selenium](https://www.seleniumhq.org). To run these, you will need to unpack the correct [ChromeDriver](https://sites.google.com/a/chromium.org/chromedriver/) for your platform found under `salsah/lib/chromedriver` and put it in the same folder. Then start Sipi and the Knora API server, and type `test` at the SBT console in the `salsah` project.

Whenever you add a new feature or fix a bug, you should add one or more tests
for the change you made.

### Documentation

A pull request should include tests and documentation for the changes that were
made. See the [documentation README](https://github.com/dasch-swiss/knora-api/blob/develop/docs/Readme.md)
for information on writing Knora documentation.

## Contact information

### Technical

Please use the [discuss.dasch.swiss](https://discuss.dasch.swiss) forum for
technical questions.

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

## Release Versioning Convention

The Knora project is following the Semantic Versioning convention for numbering the releases
as defined by [http://semver.org]:

> Given a version number MAJOR.MINOR.PATCH, increment the:
>
> * MAJOR version when you make incompatible API changes,
> * MINOR version when you add functionality in a backwards-compatible manner, and
> * PATCH version when you make backwards-compatible bug fixes.

Additionally, we will also increment the MAJOR version in the case when any kind of changes to existing
data would be necessary, e.g., any changes to the Knora-Base ontologies which are not backwards compatible.

## Acknowledgments

![](https://www.yourkit.com/images/yklogo.png)

The Knora project is using YourKit for profiling.

YourKit supports open source projects with its full-featured Java Profiler.
YourKit, LLC is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>
and <a href="https://www.yourkit.com/.net/profiler/">YourKit .NET Profiler</a>,
innovative and intelligent tools for profiling Java and .NET applications.
