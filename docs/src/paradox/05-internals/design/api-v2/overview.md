<!---
Copyright © 2015-2018 the contributors (see Contributors.md).

This file is part of Knora.

Knora is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published
by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Knora is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public
License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
-->

# API v2 Design Overview

@@toc

## General Principles

The design of Knora API v2 is intended to provide:

- Scalability
- Support for the development of different types of clients:
  - Simple, project-specific web sites
  - Powerful virtual research environments
  - Clients that are designed to work with Linked Open Data but know as little
    as possible about Knora
- The benefits of a SPARQL endpoint for searches, along with better scalability,
  as well as support for filtering of data according to permissions, versioning of data,
  and Knora's humanities-focused data types.
- The ability to query, create, and edit ontologies via the API

### Scalability

To favour scalability, API v2 aims to minimise the amount of work the
server has to do per request:

- Responses are small by design. Large amounts of data must be
  retrieved by requesting small pages of data, one after the other.
  For example, the paging of search results is enforced.
- Responses that provide data are distinct from responses that provide
  definitions (i.e. ontology entities). Data responses indicate which
  types are used, and the client can request ontology information about these
  types separately.
- Knora caches ontology information, so requests for this information are
  relatively inexpensive (they do not involve the triplestore). A separate
  caching service also could be put in front of Knora so that these requests
  would not involve Knora at all. Clients can also cache ontology information
  that they receive from Knora.

### Support for Different Types of Clients

- By default, RDF data is represented in [JSON-LD](https://json-ld.org/),
  using meaningful prefixes to shorten IRIs. This is intended to facilitate
  two use cases:
  1. A simple web site that is designed to use data from a specific project
     in Knora can be written by treating the JSON-LD as ordinary JSON, without
     considering it as RDF data.
  2. A more powerful client, designed to work with data from multiple projects,
     can use the [JSON-LD API](https://www.w3.org/TR/json-ld-api/) to process
     Knora API responses as RDF data.
     - For clients that work with RDF but not JSON-LD, any API v2
       response can also be returned as [Turtle](https://www.w3.org/TR/turtle/),
       or [RDF/XML](https://www.w3.org/TR/rdf-syntax-grammar/), using HTTP
       content negotiation.
- The same response formats are reused for different requests whenever
  possible, to minimise the number of different response formats a
  client has to handle.
- Responses are available in different @ref:[schemas](../../../03-apis/api-v2/introduction.md#api-schema):
  - A simple schema that supports read-only access, simplifies the use of Knora
    data as Linked Open Data, and facilitates the use of standard ontologies.
    With the simple schema, the need for detailed knowledge about Knora is
    minimised.
  - A complex schema, for read-write access that takes advantage of all
    of Knora's capabilities.

### Improving on the SPARQL Endpoint

SPARQL endpoints offer powerful, standards-based search capabilities for RDF
data, but they suffer from a number of
[drawbacks](https://daverog.wordpress.com/2013/06/04/the-enduring-myth-of-the-sparql-endpoint/).
@ref:[Gravsearch](../../../03-apis/api-v2/query-language.md), Knora's built-in dialect
of SPARQL, aims to provide the benefits of a SPARQL endpoint, while avoiding these
problems. It provides:

- Filtering of queried data according to the user's permissions.
- Support for Knora's built-in versioning of data.
- Support for Knora's humanities-focused data types, such as
  @ref:[calendar-independent dates](../../../02-knora-ontologies/knora-base.md#datevalue)
  and @ref:[standoff text markup](../../../02-knora-ontologies/knora-base.md#text-with-standoff-markup).
- Built-in paging of search results to improve scalability.

### Ontology Construction

Writing ontologies by hand to conform to
@ref:[the Knora base ontology](../../../02-knora-ontologies/knora-base.md) can
be a complex task. Knora API v2 therefore supports
@ref:[the creation and editing of ontologies](../../../03-apis/api-v2/ontology-information.md#ontology-updates),
while ensuring that these ontologies meet Knora's requirements.
