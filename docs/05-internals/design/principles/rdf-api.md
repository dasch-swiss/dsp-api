<!---
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# RDF Processing API

Knora provides an API for parsing and formatting RDF data and
for working with RDF graphs. This allows Knora developers to use a single,
idiomatic Scala API as a façade for a Java RDF library.
By using a feature toggle, you can choose either
[Jena](https://jena.apache.org/tutorials/rdf_api.html)
or
[RDF4J](https://rdf4j.org/documentation/programming/)
as the underlying implementation.


## Overview

The API is in the package `org.knora.webapi.messages.util.rdf`. It includes:

- `RdfModel`, which represents a set of RDF graphs (a default graph and/or one or more named graphs).
  A model can be constructed from scratch, modified, and searched.

- `RdfNode` and its subclasses, which represent RDF nodes (IRIs, blank nodes, and literals).

- `Statement`, which represents a triple or quad.

- `RdfNodeFactory`, which creates nodes and statements.

- `RdfModelFactory`, which creates empty RDF models.

- `RdfFormatUtil`, which parses and formats RDF models.

- `JsonLDUtil`, which provides specialised functionality for working
  with RDF in JSON-LD format, and for converting between RDF models
  and JSON-LD documents. `RdfFormatUtil` uses `JsonLDUtil` when appropriate.
  
- `ShaclValidator`, which validates RDF models using SHACL shapes.

To work with RDF models, start with `RdfFeatureFactory`, which returns instances
of `RdfNodeFactory`, `RdfModelFactory`, `RdfFormatUtil`, and `ShaclValidator`,
using feature toggle configuration. `JsonLDUtil` does not need a feature factory.

To iterate efficiently over the statements in an `RdfModel`, use its `iterator` method.
An `RdfModel` cannot be modified while you are iterating over it.
If you are iterating to look for statements to modify, you can
collect a `Set` of statements to remove and a `Set` of statements
to add, and perform these update operations after you have finished
the iteration.

## RDF stream processing

To read or write a large amount of RDF data without generating a large string
object, you can use the stream processing methods in `RdfFormatUtil`.

To parse an `InputStream` to an `RdfModel`, use `inputStreamToRdfModel`.
To format an `RdfModel` to an `OutputStream`, use `rdfModelToOutputStream`.

To parse RDF data from an `InputStream` and process it one statement at a time,
you can write a class that implements the `RdfStreamProcessor` trait, and
use it with the `RdfFormatUtil.parseWithStreamProcessor` method.
Your `RdfStreamProcessor` can also send one statement at a time to a
formatting stream processor, which knows how to write RDF to an `OutputStream`
in a particular format. Use `RdfFormatUtil.makeFormattingStreamProcessor` to
construct one of these.


## SPARQL queries

In tests, it can be useful to run SPARQL queries to check the content of
an `RdfModel`. To do this, use the `RdfModel.asRepository` method, which
returns an `RdfRepository` that can run `SELECT` queries.

The configuration of the default graph depends on which underlying
RDF library is used. If you are querying data in named graphs, use `FROM`
or quad patterns rather than the default graph.


## SHACL validation

On startup, graphs of SHACL shapes are loaded from Turtle files in a directory specified
by `app.shacl.shapes-dir` in `application.conf`, and in subdirectories of
that directory. To validate the default graph of an `RdfModel` using a graph of
SHACL shapes, call `ShaclValidator.validate`, specifying the relative path of the
Turtle file containing the graph of shapes.


## Implementations

- The Jena-based implementation, in package `org.knora.webapi.messages.util.rdf.jenaimpl`.

- The RDF4J-based implementation, in package `org.knora.webapi.messages.util.rdf.rdf4jimpl`.


## Feature toggle

For an overview of feature toggles, see [Feature Toggles](feature-toggles.md).

The RDF API uses the feature toggle `jena-rdf-library`:

- `on`: use the Jena implementation.

- `off` (the default): use the RDF4J implementation.

The default setting is used on startup, e.g. to read ontologies from the
repository. After startup, the per-request setting is used.


## TODO

- SHACL validation.
