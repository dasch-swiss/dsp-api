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

# Content Wrappers

@@toc

Whenever possible, the same data structures are used to represent the same
types of data, regardless of the API operation (reading, creating, or
modifying). However, often more data is available in output than in input. For
example, when a value is read from the triplestore, its IRI is
available, but when it is being created, it does not yet have an IRI.

The implementation of API v2 therefore uses content wrappers. For each type,
there is a case class that represents the lowest common denominator of the
type, the data that will be present regardless of the API operation. For
example, the trait `ValueContentV2` represents a Knora value, regardless
of whether it is received as input or returned as output. Case classes
such as `DateValueContentV2` and `TextValueContentV2` implement this trait.

An instance of this lowest-common-denominator class, or "content class", can then
be wrapped in an instance of an operation-specific class that carries additional
data. For example, when a Knora value is returned from the triplestore, a
`ValueContentV2` is wrapped in a `ReadValueV2`, which additionally contains the
value's IRI. When a value is created, it is wrapped in a `CreateValueV2`, which
has the resource IRI and the property IRI, but not the value IRI.

A read wrapper can be wrapped in another read wrapper; for
example, a `ReadResourceV2` contains `ReadValueV2` objects.

In general, Knora API v2 responders deal only with the internal schema.
(The exception is `OntologyResponderV2`, which can return ontology information
that exists only in an external schema.) Therefore, a content class needs
to be able to convert itself from the internal schema to an external schema
(when it is being used for output) and vice versa (when it is being used for
input). Each content class class should therefore extend `KnoraContentV2`, and
thus have a `toOntologySchema` method or converting itself between internal and
external schemas, in either direction:

@@snip [KnoraResponseV2.scala]($src$/org/knora/webapi/messages/v2/responder/KnoraResponseV2.scala) { #KnoraContentV2 }

Since read wrappers are used only for output, they need to be able convert
themselves only from the internal schema to an external schema. Each read wrapper class
should extend `KnoraReadV2`, and thus have a method for doing this:

@@snip [KnoraResponseV2.scala]($src$/org/knora/webapi/messages/v2/responder/KnoraResponseV2.scala) { #KnoraReadV2 }
