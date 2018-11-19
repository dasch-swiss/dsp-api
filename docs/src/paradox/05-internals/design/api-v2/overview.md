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

The design of Knora API v2 is intended to facilitate:

- minimising the amount of work the server has to do to fulfil each request
- supporting automated clients as well as as graphical user interfaces
- supporting clients that understand Linked Open Data but know as little as
  possible about Knora

Therefore:

- Response size is limited by design. Large amounts of data must be
  retrieved by requesting small pages of data, one after the other.
- Knora API v2 requests and responses are RDF documents. Any API v2
  response can be returned as [JSON-LD](https://json-ld.org/spec/latest/json-ld/),
  [Turtle](https://www.w3.org/TR/turtle/),
  or [RDF/XML](https://www.w3.org/TR/rdf-syntax-grammar/).
- Each class or property used in a request or response has a
  definition in an ontology, which Knora can serve.
- Response formats are reused for different requests whenever
  possible, to minimise the number of different response formats a
  client has to handle. For example, most requests for one or more
  resources (such as a search result, or a request for one specific
  resource) return responses in the same format.
- Responses that provide data are distinct from responses that provide
  definitions (i.e. ontology entities). Data responses indicate which
  types are used, and the client can request information about these
  types separately.

### Operation Wrappers

Whenever possible, the same data structures are used for input and
output. Often more data is available in output than in input. For
example, when a value is read from the triplestore, its IRI is
available, but when it is being created, it does not yet have an IRI. In
such cases, there is a class like `ValueContentV2`, which represents the
data that is used both for input and for output. When a value is read, a
`ValueContentV2` is wrapped in a `ReadValueV2`, which additionally
contains the value's IRI. When a value is created, it is wrapped in a
`CreateValueV2`, which has the resource IRI and the property IRI, but
not the value IRI.

A `Read*` wrapper can be wrapped in another `Read*` wrapper; for
example, a `ReadResourceV2` contains `ReadValueV2` objects.

Each `*Content*` class should extend `KnoraContentV2` and thus have a
`toOntologySchema` method or converting itself between internal and
external schemas, in either direction.

Each `Read*` wrapper class should have a method for converting itself to
JSON-LD in a particular external schema. If the `Read*` wrapper is a
`KnoraResponseV2`, this method is `toJsonLDDocument`.
