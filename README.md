[![Build Status](https://travis-ci.org/dhlab-basel/Knora.svg?branch=develop)](https://travis-ci.org/dhlab-basel/Knora)

# Knora

[Knora](http://www.knora.org/) (Knowledge Organization, Representation, and Annotation) is a software
framework for storing, sharing, and working with primary sources and data in the humanities. 

It is developed by the [Digital Humanities Lab](http://www.dhlab.unibas.ch/) at the [University of Basel](https://www.unibas.ch/en.html), and is supported by the [Swiss Academy of Humanities and Social Sciences](http://www.sagw.ch/en/sagw.html).

Knora is [free software](http://www.gnu.org/philosophy/free-sw.en.html), released under the [GNU Affero General Public License](http://www.gnu.org/licenses/agpl-3.0.en.html).

## Features

* Stores humanities data as industry-standard [RDF](http://www.w3.org/TR/2014/NOTE-rdf11-primer-20140624/) graphs, plus files for binary data such as digitized primary sources.
    * Designed to work with any standards-compliant RDF triplestore. Tested with [Ontotext GraphDB](http://ontotext.com/products/graphdb/) and [Apache Jena](https://jena.apache.org/).
* Based on [OWL](http://www.w3.org/TR/2012/REC-owl2-primer-20121211/) ontologies that express abstract, cross-disciplinary commonalities in the structure and semantics of research data.
* Offers a generic HTTP-based API, implemented in [Scala](http://www.scala-lang.org/), for querying, annotating, and linking together heterogeneous data in a unified way.
    * Handles authentication and authorization.
    * Provides automatic versioning of data.
* Includes [Sipi](https://github.com/dhlab-basel/Sipi), a high-performance media server implemented in C++.
* Provides a general-purpose, browser-based Virtual Research Environment called SALSAH (to be released soon).

## Status

### Beta stage

* The OWL ontologies
* API operations for querying and updating data
* API operations dealing with binary files and Sipi
* The testing framework, which includes many tests
* Integration of the SALSAH GUI
* API operations for administering Knora
* Documentation

### Planned

* Distribution packaging using [Docker](https://www.docker.com/)
* A simple GUI for creating ontologies (for now you can use an application such as [Protégé](http://protege.stanford.edu/) or [TopBraid Composer](http://www.topquadrant.com/tools/modeling-topbraid-composer-standard-edition/))

## Requirements

### For developing and testing the API server

* Linux or Mac OS X
* [Java Development Kit 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
* [SBT](http://www.scala-sbt.org/)

[Apache Jena](https://jena.apache.org/) is included, [Ontotext GraphDB](http://ontotext.com/products/graphdb/) is recommended.

### For building the documentation

See [docs/Readme.md](docs/Readme.md).

## Try it out

### Run the Knora API server

Start the built-in Fuseki triplestore:

```
$ cd triplestores/fuseki
$ ./fuseki-server
```

Then in another terminal, load some test data into the triplestore:

```
$ cd webapi/scripts
$ ./fuseki-load-test-data.sh
```

Then go back to the webapi root directory and use SBT to start the API server:

```
$ cd ..
$ sbt
> compile
> re-start
```

Then try opening [http://localhost:3333/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a](http://localhost:3333/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a) in a web browser. You should see a response in JSON describing a book.

To shut down the Knora API server:

```
> re-stop
```

### Run the automated tests

Make sure you've started Fuseki as shown above. Then at the SBT prompt:

```
> fuseki:test
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

* Unit tests, route-to-route tests, and end-to-end tests are under `webapi/src/test`. To run these, type `graphdb:test` or `fuseki:test` (depending on which triplestore you're using) at the SBT console in the `webapi` project. To run a single test, use `graphdb:test-only *NameOfTestSpec`.
* Integration tests, which can involve [Sipi](https://github.com/dhlab-basel/Sipi), are under `src/it`. To run these, first start Sipi, then type `it:test` at the SBT console in the `webapi` project.
* Browser interaction tests are under `salsah/src/test`, and are written using [Selenium](http://www.seleniumhq.org/). To run these, you will need to unpack the correct [ChromeDriver](https://sites.google.com/a/chromium.org/chromedriver/) for your platform found under `salsah/lib/chromedriver` and put it in the same folder. Then start Sipi and the Knora API server, and type `test` at the SBT console in the `salsah` project.

Whenever you add a new feature or fix a bug, you should add one or more tests for the change you made.

### Documentation

A pull request should include tests and documentation for the changes that were made. Design and user documentation go under `docs` and are written in [reStructuredText](http://docutils.sourceforge.net/rst.html) format using the [Sphinx](http://www.sphinx-doc.org/en/stable/) documentation generator.



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
