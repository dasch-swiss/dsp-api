/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

/**
 * Indicates the schema that a Knora ontology or ontology entity conforms to.
 */
sealed trait OntologySchema

/**
 * The schema of Knora ontologies and entities that are used in the triplestore.
 */
case object InternalSchema extends OntologySchema

/**
 * The schema of Knora ontologies and entities that are used in API v2.
 */
sealed trait ApiV2Schema extends OntologySchema

/**
 * The simple schema for representing Knora ontologies and entities. This schema represents values as literals
 * when possible.
 */
case object ApiV2Simple extends ApiV2Schema

/**
 * The default (or complex) schema for representing Knora ontologies and entities. This
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
 * A trait representing options that affect the format of JSON-LD responses.
 */
sealed trait JsonLDRendering extends SchemaOption

/**
 * Indicates that flat JSON-LD should be returned, i.e. objects with IRIs should be referenced by IRI
 * rather than nested. Blank nodes will still be nested in any case.
 */
case object FlatJsonLD extends JsonLDRendering

/**
 * Indicates that hierarchical JSON-LD should be returned, i.e. objects with IRIs should be nested when
 * possible, rather than referenced by IRI.
 */
case object HierarchicalJsonLD extends JsonLDRendering

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
   * @param targetSchema  the target API schema.
   * @param schemaOptions the schema options submitted with the request.
   * @return `true` if standoff should be queried.
   */
  def queryStandoffWithTextValues(targetSchema: ApiV2Schema, schemaOptions: Set[SchemaOption]): Boolean =
    targetSchema == ApiV2Complex && !schemaOptions.contains(MarkupAsStandoff)

  /**
   * Determines whether markup should be rendered as XML.
   *
   * @param targetSchema  the target API schema.
   * @param schemaOptions the schema options submitted with the request.
   * @return `true` if markup should be rendered as XML.
   */
  def renderMarkupAsXml(targetSchema: ApiV2Schema, schemaOptions: Set[SchemaOption]): Boolean =
    targetSchema == ApiV2Complex && !schemaOptions.contains(MarkupAsStandoff)

  /**
   * Determines whether markup should be rendered as standoff, separately from text values.
   *
   * @param targetSchema  the target API schema.
   * @param schemaOptions the schema options submitted with the request.
   * @return `true` if markup should be rendered as standoff.
   */
  def renderMarkupAsStandoff(targetSchema: ApiV2Schema, schemaOptions: Set[SchemaOption]): Boolean =
    targetSchema == ApiV2Complex && schemaOptions.contains(MarkupAsStandoff)

  /**
   * Determines whether flat JSON-LD should be returned, i.e. objects with IRIs should be referenced by IRI
   * rather than nested.
   *
   * @param schemaOptions the schema options submitted with the request.
   * @return `true` if flat JSON-LD should be returned.
   */
  def returnFlatJsonLD(schemaOptions: Set[SchemaOption]): Boolean =
    schemaOptions.contains(FlatJsonLD)
}
