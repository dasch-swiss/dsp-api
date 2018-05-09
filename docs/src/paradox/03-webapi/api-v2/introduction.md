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

# Introduction: Using API V2

@@toc

Version 2 of the Knora API aims to make both the response and request
formats more generic and consistent. Version 1 was basically the result
of the reimplementation of the existing API of the SALSAH prototype.
Since the development of this prototype has a long history and the
specification of API V1 was an evolving process, V1 has various
inconsistencies and peculiarities. With V2, we would like to offer a
format that is consistent and hence easier to use for a client.

Please note that V2 is still in development. We do not yet recommend
using it on productive systems.

## V2 Path Segment

Every request to API V1 includes `v2` as a path segment, e.g.
`http://host/v2/resources/http%3A%2F%2Frdfh.ch%2Fc5058f3a`.
Accordingly, requests using any other version of the API will require
another path segment.

## JSON-LD

Our preferred format for data exchange is
[JSON-LD](https://json-ld.org/spec/latest/json-ld/). JSON-LD allows the
Knora API server to provide responses that are relatively easy for
automated processes to interpret, since their structure and semantics is
explicitly defined. For example, each project-specific Knora resource
property is identified by an IRI, which can be dereferenced to get more
information about it (e.g. its label in different languages). Moreover,
each value has a type represented by an IRI. These are either standard
RDF types (e.g. XSD datatypes) or more complex types whose IRIs can be
dereferenced to get more information about their structure.

At the same time, JSON-LD responses are relatively easy for software
developers to work with. Items in a response can have human-readable
names, which can nevertheless be expanded to full IRIs. Also, while a
format such as [Turtle](https://www.w3.org/TR/turtle/) just provides a
set of RDF triples, an equivalent JSON-LD response can explicitly
provide data in a hierarchical structure, with objects nested inside
other objects.

We designed the V2 routes in a way that would also allow for the usage
of other formats such as XML. We plan to implement support for XML once
the implementation of JSON-LD is completed. The client will be able to
use content negotiation to specify the preferred exchange format.

## Support of schema.org Entities

Some entities defined in [schema.org](http://www.schema.org) are used in
API v2 responses (e.g., `ResourcesSequence`, see
@ref:[Response Formats](response-formats.md)). For example,
`knora-api:Resource` is declared to be a subclass of
`http://schema.org/Thing`, so we can use a `knora-api:Resource` or
any of its subclasses where `http://schema.org` requires a
`http://schema.org/Thing`.

## Knora IRIs

Resources and entities are identified by IRIs. The format of these IRIs
is explained in @ref:[Knora IRIs](knora-iris.md).

## API Schema

Knora API V2 offers the query and response format in a complex schema
and a simple one. The main difference is that the complex schema exposes
the complexity of value objects, while the simple version does not. A
client that needs to edit values must use the complex schema in order to
obtain the IRI of each value. A client that reads but does not update
data can use the simplified schema. The simple schema is mainly intended
to facilitate interoperability with other RDF-based systems in the
context of Linked Open Data. It is therefore designed to use the
simplest possible datatypes and to require minimal knowledge of Knora.

In either case, the client deals only with data whose structure and
semantics are defined by Knora API ontologies, which are distinct from
the ontologies that are used to store date in the triplestore. The Knora
API server automatically converts back and forth between these internal
and external representations. This approach encapsulates the internals
and adds a layer of abstraction to them.

Some API operations inherently require the client to accept responses in
the complex schema, while others can return data in either schema. In
the latter case, the complex schema is used by default in the response,
unless the request specifically asks for the simple schema. For example,
if an ontology is requested using an IRI indicating the simple schema,
the ontology will be returned in the simple schema (see
@ref:[Querying, Creating, and Updating Ontologies](ontology-information.md)). The
client can also specify the desired schema by using an HTTP header or a
URL parameter:

  - the HTTP header `X-Knora-Accept-Schema`
  - the URL parameter `schema`

Both the HTTP header and the URL parameter accept the values `simple` or
`complex`.

Although the KnarQL query language
(see @ref:[KnarQL: Knora Query Language](query-language.md)) requires the simple
schema to be used in the request, search results are returned in the
complex schema by default, unless the client requests the simple schema
by using the HTTP header or the URL parameter.
