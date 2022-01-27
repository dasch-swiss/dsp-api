<!---
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
-->

# Ontology Schemas

## OntologySchema Type

As explained in [API Schema](../../../03-apis/api-v2/introduction.md#api-schema),
Knora can represent the same RDF data in different forms: an "internal schema"
for use in the triplestore, and different "external schemas" for use in Knora
API v2. Different schemas use different IRIs, as explained in
[Knora IRIs](../../../03-apis/api-v2/knora-iris.md). Internally,
Knora uses a [SmartIri](smart-iris.md) class to convert IRIs between
schemas.

The data type representing a schema itself is `OntologySchema`, which
uses the [sealed trait](https://alvinalexander.com/scala/benefits-of-sealed-traits-in-scala-java-enums)
pattern:

```scala
package org.knora.webapi

/**
  * Indicates the schema that a Knora ontology or ontology entity conforms to.
  */
sealed trait OntologySchema

/**
  * The schema of DSP ontologies and entities that are used in the triplestore.
  */
case object InternalSchema extends OntologySchema

/**
  * The schema of DSP ontologies and entities that are used in API v2.
  */
sealed trait ApiV2Schema extends OntologySchema

/**
  * The simple schema for representing DSP ontologies and entities. This schema represents values as literals
  * when possible.
  */
case object ApiV2Simple extends ApiV2Schema

/**
  * The default (or complex) schema for representing DSP ontologies and entities. This
  * schema always represents values as objects.
  */
case object ApiV2Complex extends ApiV2Schema

/**
  * A trait representing options that can be submitted to configure an ontology schema.
  */
sealed trait SchemaOption

/**
  * A trait representing options that affect the rendering of markup when text values are returned.
  */
sealed trait MarkupRendering extends SchemaOption

/**
  * Indicates that markup should be rendered as XML when text values are returned.
  */
case object MarkupAsXml extends MarkupRendering

/**
  * Indicates that markup should not be returned with text values, because it will be requested
  * separately as standoff.
  */
case object MarkupAsStandoff extends MarkupRendering

/**
  * Indicates that no markup should be returned with text values. Used only internally.
  */
case object NoMarkup extends MarkupRendering

/**
  * Utility functions for working with schema options.
  */
object SchemaOptions {
    /**
      * A set of schema options for querying all standoff markup along with text values.
      */
    val ForStandoffWithTextValues: Set[SchemaOption] = Set(MarkupAsXml)

    /**
      * A set of schema options for querying standoff markup separately from text values.
      */
    val ForStandoffSeparateFromTextValues: Set[SchemaOption] = Set(MarkupAsStandoff)

    /**
      * Determines whether standoff should be queried when a text value is queried.
      *
      * @param targetSchema the target API schema.
      * @param schemaOptions the schema options submitted with the request.
      * @return `true` if standoff should be queried.
      */
    def queryStandoffWithTextValues(targetSchema: ApiV2Schema, schemaOptions: Set[SchemaOption]): Boolean = {
        targetSchema == ApiV2Complex && !schemaOptions.contains(MarkupAsStandoff)
    }

    /**
      * Determines whether markup should be rendered as XML.
      *
      * @param targetSchema the target API schema.
      * @param schemaOptions the schema options submitted with the request.
      * @return `true` if markup should be rendered as XML.
      */
    def renderMarkupAsXml(targetSchema: ApiV2Schema, schemaOptions: Set[SchemaOption]): Boolean = {
        targetSchema == ApiV2Complex && !schemaOptions.contains(MarkupAsStandoff)
    }

    /**
      * Determines whether markup should be rendered as standoff, separately from text values.
      *
      * @param targetSchema the target API schema.
      * @param schemaOptions the schema options submitted with the request.
      * @return `true` if markup should be rendered as standoff.
      */
    def renderMarkupAsStandoff(targetSchema: ApiV2Schema, schemaOptions: Set[SchemaOption]): Boolean = {
        targetSchema == ApiV2Complex && schemaOptions.contains(MarkupAsStandoff)
    }
}
```

This class hierarchy allows method declarations to restrict the schemas
they accept. A method that can accept any schema can take a parameter of type
`OntologySchema`, while a method that accepts only external schemas can take
a parameter of type `ApiV2Schema`. For examples, see [Content Wrappers](content-wrappers.md).

## Generation of Ontologies in External Schemas

Ontologies are stored only in the internal schema, and are converted on the fly
to external schemas. For each external schema, there is a Scala object in
`org.knora.webapi.messages.v2.responder.ontologymessages` that provides rules
for this conversion:

- `KnoraApiV2SimpleTransformationRules` for the API v2 simple schema
- `KnoraApiV2WithValueObjectsTransformationRules` for the API v2 complex schema

Since these are Scala objects rather than classes, they are initialised before
the Akka `ActorSystem` starts, and therefore need a special instance of
Knora's `StringFormatter` class (see [Smart IRIs](smart-iris.md#implementation)).

Each of these rule objects implements this trait:

```scala
/**
  * A trait for objects that provide rules for converting an ontology from the internal schema to an external schema.
  * * See also [[OntologyConstants.CorrespondingIris]].
  */
trait OntologyTransformationRules {
    /**
      * The metadata to be used for the transformed ontology.
      */
    val ontologyMetadata: OntologyMetadataV2

    /**
      * Properties to remove from the ontology before converting it to the target schema.
      * See also [[OntologyConstants.CorrespondingIris]].
      */
    val internalPropertiesToRemove: Set[SmartIri]

    /**
      * Classes to remove from the ontology before converting it to the target schema.
      */
    val internalClassesToRemove: Set[SmartIri]

    /**
      * After the ontology has been converted to the target schema, these cardinalities must be
      * added to the specified classes.
      */
    val externalCardinalitiesToAdd: Map[SmartIri, Map[SmartIri, KnoraCardinalityInfo]]

    /**
      * Classes that need to be added to the ontology after converting it to the target schema.
      */
    val externalClassesToAdd: Map[SmartIri, ReadClassInfoV2]

    /**
      * Properties that need to be added to the ontology after converting it to the target schema.
      * See also [[OntologyConstants.CorrespondingIris]].
      */
    val externalPropertiesToAdd: Map[SmartIri, ReadPropertyInfoV2]
}
```

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
