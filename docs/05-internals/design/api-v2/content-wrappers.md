<!---
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Content Wrappers

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

In general, DSP-API v2 responders deal only with the internal schema.
(The exception is `OntologyResponderV2`, which can return ontology information
that exists only in an external schema.) Therefore, a content class needs
to be able to convert itself from the internal schema to an external schema
(when it is being used for output) and vice versa (when it is being used for
input). Each content class class should therefore extend `KnoraContentV2`, and
thus have a `toOntologySchema` method or converting itself between internal and
external schemas, in either direction:

```
/**
  * A trait for content classes that can convert themselves between internal and internal schemas.
  *
  * @tparam C the type of the content class that extends this trait.
  */
trait KnoraContentV2[C <: KnoraContentV2[C]] {
    this: C =>
    def toOntologySchema(targetSchema: OntologySchema): C
}
```

Since read wrappers are used only for output, they need to be able convert
themselves only from the internal schema to an external schema. Each read wrapper class
should extend `KnoraReadV2`, and thus have a method for doing this:

```
/**
  * A trait for read wrappers that can convert themselves to external schemas.
  *
  * @tparam C the type of the read wrapper that extends this trait.
  */
trait KnoraReadV2[C <: KnoraReadV2[C]] {
    this: C =>
    def toOntologySchema(targetSchema: ApiV2Schema): C
}
```
