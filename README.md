# Knora &mdash; Knowledge Organization, Representation, and Annotation

[![Github](https://img.shields.io/github/v/tag/dasch-swiss/dsp-api?include_prereleases&label=Github%20tag)](https://github.com/dasch-swiss/dsp-api)
[![Docker](https://img.shields.io/docker/v/daschswiss/knora-api?label=Docker%20image)](https://hub.docker.com/r/daschswiss/knora-api)
[![CI](https://github.com/dasch-swiss/dsp-app/workflows/CI/badge.svg)](https://github.com/dasch-swiss/dsp-api/actions?query=workflow%3ACI)

[Knora](https://www.knora.org/) is a server application for storing, sharing, and working with primary sources and data in the humanities.

It is developed by the [Swiss National Data and Service Center for the Humanities](https://dasch.swiss)
at the [University of Basel](https://www.unibas.ch), and is supported by the
[Swiss Academy of Humanities and Social Sciences](https://www.sagw.ch) and
the [Swiss National Science Foundation](https://snf.ch).

Knora is [free software](http://www.gnu.org/philosophy/free-sw.en.html),
released under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

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
* [sbt](https://www.scala-sbt.org/)
* [Bazel](https://bazel.build)

#### Java Adoptopenjdk 11

To install, follow these steps:

```shell
brew tap AdoptOpenJDK/openjdk
brew install AdoptOpenJDK/openjdk/adoptopenjdk11 --cask
```

To pin the version of Java, please add this environment variable to you startup script (bashrc, etc.):

```shell
export JAVA_HOME=`/usr/libexec/java_home -v 11`
```

#### Bazel build tools

To install, follow these steps:

```shell
npm install -g @bazel/bazelisk
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

```shell
make init-db-test
```

Then we need to start knora-api after loading the data:

```shell
make stack-up
```

Then try opening [http://localhost:3333/v2/resources/http%3A%2F%2Frdfh.ch%2F0803%2Fc5058f3a](http://localhost:3333/v2/resources/http%3A%2F%2Frdfh.ch%2F0803%2Fc5058f3a) in a web browser. You should see a response in JSON-LD describing a book.

On first installation, errors similar to the following can come up:
```
error decoding 'Volumes[0]': invalid spec: :/fuseki:delegated: empty section between colons
```
To solve this you need to deactivate Docker Compose V2. This can be done in Docker Desktop either by unchecking the "Use Docker Compose V2"-flag under "Preferences/General" or by running
 ```
docker-compose disable-v2
```

To shut down the Knora-Stack:

```shell
make stack-down
```

### Run the automated tests

Run :

```shell
make test
```

## How to Contribute

You can help by testing Knora with your data, making bug reports, improving the
documentation, and adding features that you need.

First, open an [issue](https://github.com/dasch-swiss/knora-api/issues) to
describe your problem or idea. We may ask you to submit a
[pull request](https://help.github.com/articles/about-pull-requests/)
implementing the desired functionality.

### Coding conventions

Use `camelCase` for names of classes, variables, and functions. Make names descriptive, and don't worry if they're long.

Use [Scalafmt](https://scalameta.org/scalafmt/) in [IntelliJ IDEA](https://www.jetbrains.com/idea) to format Scala code.

Use whitespace to make your code easier to read.
Add lots of implementation comments describing what your code is doing,
how it works, and why it works that way.

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
made. See the [documentation README](https://github.com/dasch-swiss/knora-api/blob/main/docs/Readme.md)
for information on writing Knora documentation.

## Commit Message Schema

When writing commit messages, we follow the [Conventional Commit messages](https://www.conventionalcommits.org/) rules.
Get more information in our official [DSP Contribution Documentation](https://docs.dasch.swiss/developers/dsp/contribution/#git-commit-guidelines)

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

## Release Notes Generation

A pull request usually resolves one issue or user story defined on [Youtrack](https://dasch.myjetbrains.com/youtrack/). Since we started to use the [release-please-action](https://github.com/marketplace/actions/release-please-action) it's very important to set the PR title in the correct way, especially becuase all commits added within the pull request are squashed. Please read the official [DSP Contribution Documentation](https://docs.dasch.swiss/developers/dsp/contribution/#pull-request-guidelines) carefully!

## Acknowledgments

![YourKit](https://www.yourkit.com/images/yklogo.png)

The Knora project is using YourKit for profiling.

YourKit supports open source projects with its full-featured Java Profiler.
YourKit, LLC is the creator of [YourKit Java Profiler](https://www.yourkit.com/java/profiler/)
and [YourKit .NET Profiler](https://www.yourkit.com/.net/profiler/),
innovative and intelligent tools for profiling Java and .NET applications.
