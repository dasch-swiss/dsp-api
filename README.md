# Knora

[Knora](http://www.knora.org/) (Knowledge Organization, Representation, and Annotation) is a software
framework for storing, sharing, and working with primary sources and data in the humanities. 

It is developed by the [Digital Humanities Lab](http://www.dhlab.unibas.ch/) at the [University of Basel](https://www.unibas.ch/en.html), and is supported by the [Swiss Academy of Humanities and Social Sciences](http://www.sagw.ch/en/sagw.html).

Knora is [free software](http://www.gnu.org/philosophy/free-sw.en.html), released under the [GNU Affero General Public License](http://www.gnu.org/licenses/agpl-3.0.en.html).

## Features

* Stores humanities data as industry-standard [RDF](http://www.w3.org/TR/2014/NOTE-rdf11-primer-20140624/) graphs, plus files for binary data such as digitized primary sources.
    * Tested with [Ontotext GraphDB](http://ontotext.com/products/graphdb/) and [Apache Jena](https://jena.apache.org/). Should work with any standards-compliant RDF triplestore.
* Based on [OWL](http://www.w3.org/TR/2012/REC-owl2-primer-20121211/) ontologies that express abstract, cross-disciplinary commonalities in the structure and semantics of research data.
* Can query, annotate, and link together heterogeneous data in a unified way.
* Offers a generic HTTP-based API for accessing and updating data, implemented in [Scala](http://www.scala-lang.org/).
* Includes a high-performance media server called Sipi (to be released soon), implemented in C++.
* Provides a general-purpose, browser-based Virtual Research Environment called SALSAH (to be released soon).

## Status

### More or less complete

* The OWL ontologies
* API operations for querying and updating data
* The unit testing framework, which includes many tests

### Less complete

* API operations dealing with binary files and Sipi
* Integration of the SALSAH GUI
* Documentation

### Not yet started

* API operations for administering Knora
* A simple GUI for creating ontologies (for now you can use an application such as [Protégé](http://protege.stanford.edu/) or [TopBraid Composer](http://www.topquadrant.com/tools/modeling-topbraid-composer-standard-edition/))
* Distribution packaging using [Docker](https://www.docker.com/)

## Requirements

* [Java Development Kit 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
* [SBT](http://www.scala-sbt.org/)

[Apache Jena](https://jena.apache.org/) is included.

## What you can do

* Help with testing (please contact us first).
* Projects that used the earlier SALSAH server and GUI can convert their data to RDF (contact us about how to do this). Soon they will be able to switch to Knora for higher performance, greater reliability, and a storage system that is suitable for long-term accessibility of data.
* New projects can begin designing OWL ontologies for their data based on Knora's ontologies, in preparation for entering data into Knora.
