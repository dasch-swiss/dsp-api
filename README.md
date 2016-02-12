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
* Includes a high-performance media server called Sipi (to be released soon), implemented in C++.
* Provides a general-purpose, browser-based Virtual Research Environment called SALSAH (to be released soon).

## Status

### Early alpha stage

* The OWL ontologies
* API operations for querying and updating data
* The unit testing framework, which includes many tests

### Currently being implemented

* API operations dealing with binary files and Sipi
* A simple GUI for creating ontologies (for now you can use an application such as [Protégé](http://protege.stanford.edu/) or [TopBraid Composer](http://www.topquadrant.com/tools/modeling-topbraid-composer-standard-edition/))
* Integration of the SALSAH GUI
* Documentation

### Planned

* API operations for administering Knora
* Distribution packaging using [Docker](https://www.docker.com/)

## Requirements

### For developing and testing the API server

* Linux or Mac OS X
* [Java Development Kit 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
* [SBT](http://www.scala-sbt.org/)

[Apache Jena](https://jena.apache.org/) is included.

### For building the documentation

See `docs/Readme.md`.

## Try it out

### Run the Knora API server

Start the built-in Fuseki triplestore:

```
$ cd webapi/_fuseki
$ ./fuseki-server
```

Then in another terminal, load some test data into the triplestore:

```
$ cd webapi/scripts
$ ./fuseki-load-test-data.sh
```

Then go back to the project's root directory and use SBT to start the API server:

```
$ cd ../..
$ sbt
> project webapi
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

## What you can do

* Help with testing (please contact us first).
* Projects currently using the earlier SALSAH server and GUI can convert their data to RDF (contact us about how to do this). Soon they will be able to switch to Knora for better performance, greater reliability, and a storage system that is suitable for long-term accessibility of data.
* New projects can begin designing OWL ontologies for their data based on Knora's ontologies, in preparation for entering data into Knora.

## Contact information

### Technical

Please use the [knora-user](https://www.maillist.unibas.ch/mailman/listinfo/knora-user) mailing list for technical questions.

### Administrative

Lukas Rosenthaler `<lukas.rosenthaler@unibas.ch>`

### Commit Message Schema ###

When writing commit messages, we stick to this schema: type (title): subject body

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