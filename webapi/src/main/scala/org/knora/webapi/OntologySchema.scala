/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

// #OntologySchema
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
// #OntologySchema
