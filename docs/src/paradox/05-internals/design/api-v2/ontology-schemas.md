<!---
Copyright © 2015-2019 the contributors (see Contributors.md).

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

# Ontology Schemas

@@toc

## OntologySchema Type

As explained in @ref:[API Schema](../../../03-apis/api-v2/introduction.md#api-schema),
Knora can represent the same RDF data in different forms: an "internal schema"
for use in the triplestore, and different "external schemas" for use in Knora
API v2. Different schemas use different IRIs, as explained in
@ref:[Knora IRIs](../../../03-apis/api-v2/knora-iris.md). Internally,
Knora uses a @ref:[SmartIri](smart-iris.md) class to convert IRIs between
schemas.

The data type representing a schema itself is `OntologySchema`, which
uses the [sealed trait](https://alvinalexander.com/scala/benefits-of-sealed-traits-in-scala-java-enums)
pattern:

@@snip [OntologySchema.scala]($src$/org/knora/webapi/OntologySchema.scala) { #OntologySchema }

This class hierarchy allows method declarations to restrict the schemas
they accept. A method that can accept any schema can take a parameter of type
`OntologySchema`, while a method that accepts only external schemas can take
a parameter of type `ApiV2Schema`. For examples, see @ref:[Content Wrappers](content-wrappers.md).

## Generation of Ontologies in External Schemas

Ontologies are stored only in the internal schema, and are converted on the fly
to external schemas. For each external schema, there is a Scala object in
`org.knora.webapi.messages.v2.responder.ontologymessages` that provides rules
for this conversion:

- `KnoraApiV2SimpleTransformationRules` for the API v2 simple schema
- `KnoraApiV2WithValueObjectsTransformationRules` for the API v2 complex schema

Since these are Scala objects rather than classes, they are initialised before
the Akka `ActorSystem` starts, and therefore need a special instance of
Knora's `StringFormatter` class (see @ref:[Smart IRIs](smart-iris.md#implementation)).

Each of these rule objects implements this trait:

@@snip [KnoraBaseTransformationRules.scala]($src$/org/knora/webapi/messages/v2/responder/ontologymessages/KnoraBaseTransformationRules.scala) { #KnoraBaseTransformationRules }

These rules are applied to `knora-base` as well as to user-created ontologies.
For example, `knora-base:Resource` has different cardinalities depending on its
schema (`knora-api:Resource` has an additional cardinality on `knora-api:hasIncomingLink`),
and this is therefore also true of its user-created subclasses. The transformation
is implemented:

- In the implementations of the `toOntologySchema` method in classes defined in
  `OntologyMessagesV2.scala`: `ReadOntologyV2`, `ReadClassInfoV2`, `ClassInfoContentV2`,
  `PropertyInfoContentV2`, and `OntologyMetadataV2`.
- In `OntologyResponderV2.getEntityInfoResponseV2`, which handles requests for
  specific ontology entities. If the requested entity is hard-coded in a transformation
  rule, this method returns the hard-coded external entity, otherwise it returns the relevant
  internal entity.
