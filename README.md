# Knora

Knora (Knowledge Organization, Representation, and Annotation) is a software
framework for storing, sharing, and working with humanities data.

## Features

* Stores humanities data as industry-standard [RDF](http://www.w3.org/TR/2014/NOTE-rdf11-primer-20140624/) graphs.
    * Tested with [Ontotext GraphDB](http://ontotext.com/products/graphdb/) and [Apache Jena](https://jena.apache.org/). Should work with any standards-compliant RDF triplestore.
* Based on [OWL](http://www.w3.org/TR/2012/REC-owl2-primer-20121211/) ontologies that express abstract, cross-disciplinary commonalities in the structure and semantics of research data.
* Can query, annotate, and link together heterogeneous data in a unified way.
* Offers a generic HTTP-based API for accessing and updating data, implemented in scalable, high-performance [Scala](http://www.scala-lang.org/).
* Uses a high-performance media server called Sipi (to be released soon).
* Provides a general-purpose, browser-based Virtual Research Environment, called SALSAH (to be released soon).

## Status

### More or less complete

* The OWL ontologies
* API operations for querying and updating data
* The unit testing framework, which includes many tests

### Very incomplete

* API operations dealing with binary files and Sipi
* Integration of the SALSAH GUI
* Documentation

### Not yet started

* API operations for administering Knora

