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

In our API responses (e.g., `ResourcesSequence`, see
@ref:[Response Formats](response-formats.md)), we use entities
defined in [schema.org](http://schema.org/).

Our intent is that any client familiar with
[schema.org](http://schema.org/) should be able to understand our
response format.

A resource's `rdfs:label` is represented as a `http://schema.org/name`
although they might not be equivalent in a strict sense (see
[schemaorg#1762](https://github.com/schemaorg/schemaorg/issues/1762)).

Likewise, `knora-api:Resource` is declared to be a subclass of
`http://schema.org/Thing`, so we can use a `knora-api:Resource` or any of its subclasses where
`http://schema.org` requires a `http://schema.org/Thing`.

## API Schema

Knora API V2 offers the query and response format in a complex schema
and a simple one. The main difference is that the complex schema exposes
the complexity of value objects, while the simple version does not. A
client that needs to edit values must use the complex schema in order to
obtain the IRI of each value. A client that reads but does not update
data can use the simplified schema.

In either case, the client deals only with data whose structure and
semantics are defined by Knora API ontologies, which are distinct from
the ontologies that are used to store date in the triplestore. The Knora
API server automatically converts back and forth between these internal
and external representations. This approach encapsulates the internals
and adds a layer of abstraction to them. The client will be able to use
content negotiation to specify its preferred exchange format. For more
information, see
@ref:[Querying, Creating, and Updating Ontologies](ontology-information.md).

## Knora IRIs

Resources and entities are identified by IRIs. The format of these IRIs
is explained in @ref:[Knora IRIs](knora-iris.md).

## V2 Path Segment

Every request to API V1 includes `v2` as a path segment, e.g.
`http://host/v2/resources/http%3A%2F%2Frdfh.ch%2Fc5058f3a`.
Accordingly, requests using any other version of the API will require
another path segment.
