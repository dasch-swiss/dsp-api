# DSP-API &mdash; DaSCH Service Platform API

[![Github](https://img.shields.io/github/v/tag/dasch-swiss/dsp-api?include_prereleases&label=Github%20tag)](https://github.com/dasch-swiss/dsp-api)
[![Docker](https://img.shields.io/docker/v/daschswiss/knora-api?label=Docker%20image)](https://hub.docker.com/r/daschswiss/knora-api)
[![CI](https://github.com/dasch-swiss/dsp-app/workflows/CI/badge.svg)](https://github.com/dasch-swiss/dsp-api/actions?query=workflow%3ACI)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/4c8f6736facf4e3ab6b0436c0c1ff197)](https://www.codacy.com/gh/dasch-swiss/dsp-api/dashboard?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=dasch-swiss/dsp-api&amp;utm_campaign=Badge_Grade)
[![Codacy Badge](https://app.codacy.com/project/badge/Coverage/4c8f6736facf4e3ab6b0436c0c1ff197)](https://www.codacy.com/gh/dasch-swiss/dsp-api/dashboard?utm_source=github.com&utm_medium=referral&utm_content=dasch-swiss/dsp-api&utm_campaign=Badge_Coverage)

[DSP](https://admin.dasch.swiss/) is a server application for storing, sharing, and working with primary sources and data in the humanities.

It is developed by the [Swiss National Data and Service Center for the Humanities](https://dasch.swiss)
at the [University of Basel](https://www.unibas.ch), and is supported by the
[Swiss Academy of Humanities and Social Sciences](https://www.sagw.ch) and
the [Swiss National Science Foundation](https://snf.ch).

DSP-API is [free software](http://www.gnu.org/philosophy/free-sw.en.html),
released under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

## Features

* Stores humanities data as industry-standard [RDF](http://www.w3.org/TR/2014/NOTE-rdf11-primer-20140624/) graphs, plus files for binary data such as digitized primary sources.
  * Designed to work with any standards-compliant RDF triplestore. Tested with [Jena Fuseki](https://jena.apache.org/).
* Based on [OWL](http://www.w3.org/TR/2012/REC-owl2-primer-20121211/) ontologies that express abstract, cross-disciplinary commonalities in the structure and semantics of research data.
* Offers a generic HTTP-based API, implemented in [Scala](https://www.scala-lang.org/), for querying, annotating, and linking together heterogeneous data in a unified way.
  * Handles authentication and authorization.
  * Provides automatic versioning of data.
* Uses [Sipi](https://sipi.io), a high-performance media server implemented in C++.
* Designed to be be used with [DSP-APP](https://docs.dasch.swiss/latest/DSP-APP/), a general-purpose, browser-based virtual research environment,
  as well as with custom user interfaces.

## Requirements

### For developing and testing DSP-API

Each developer machine should have the following prerequisites installed:

* Linux or macOS
* [Docker Desktop](https://www.docker.com/products/docker-desktop)
* [Homebrew](https://brew.sh) (macOS)
* JDK [Temurin 17](https://adoptium.net/en-GB/temurin/)
* [sbt](https://www.scala-sbt.org/)

#### JDK Temurin 17

To install, follow these steps:

```shell
brew tap homebrew/cask-versions
brew install --cask temurin17
```

To pin the version of Java, please add this environment variable to you startup script (bashrc, etc.):

```shell
export JAVA_HOME=`/usr/libexec/java_home -v 17`
```

### For building the documentation

See [docs/Readme.md](docs/Readme.md).

## Try it out

### Run DSP-API

Create a test repository and load some test data into the triplestore:

```shell
make init-db-test
```

Start DSP-API after loading the data:

```shell
make stack-up
```

Open [http://localhost:3333/v2/resources/http%3A%2F%2Frdfh.ch%2F0803%2Fc5058f3a](http://localhost:3333/v2/resources/http%3A%2F%2Frdfh.ch%2F0803%2Fc5058f3a) in a web browser. You should see a response in JSON-LD describing a book.

On first installation, errors similar to the following can come up:
```
error decoding 'Volumes[0]': invalid spec: :/fuseki:delegated: empty section between colons
```
To solve this, you need to deactivate Docker Compose V2. This can be done in Docker Desktop either by unchecking the "Use Docker Compose V2" flag under "Preferences > General" or by running
 ```
docker-compose disable-v2
```

Shut down DSP-API:

```shell
make stack-down
```

### Run the automated tests

Run all tests:

```shell
make test
```

## Release Versioning Convention

The DSP-API release versioning follows the [Semantic Versioning](https://semver.org) convention:

> Given a version number MAJOR.MINOR.PATCH, increment the:
>
> * MAJOR version when you make incompatible API changes,
> * MINOR version when you add functionality in a backwards-compatible manner, and
> * PATCH version when you make backwards-compatible bug fixes.

Additionally, we will also increment the MAJOR version in the case when any kind of changes to existing
data would be necessary, e.g., any changes to the [knora-base ontology](https://docs.dasch.swiss/latest/DSP-API/02-dsp-ontologies/knora-base/) which are not backwards compatible.
