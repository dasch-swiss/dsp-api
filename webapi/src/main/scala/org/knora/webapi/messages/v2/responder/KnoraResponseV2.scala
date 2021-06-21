/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.v2.responder

import org.knora.webapi.{
  ApiV2Complex,
  ApiV2Schema,
  ApiV2Simple,
  InternalSchema,
  OntologySchema,
  SchemaOption,
  SchemaOptions
}
import org.knora.webapi.exceptions.{AssertionException, BadRequestException}
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.settings.KnoraSettingsImpl

/**
 * A trait for Knora API V2 response messages.
 */
trait KnoraResponseV2 {

  /**
   * Returns this response message in the requested format.
   *
   * @param rdfFormat            the RDF format selected for the response.
   * @param targetSchema         the response schema.
   * @param schemaOptions        the schema options.
   * @param settings             the application settings.
   * @param featureFactoryConfig the feature factory configuration.
   * @return a formatted string representing this response message.
   */
  def format(
    rdfFormat: RdfFormat,
    targetSchema: OntologySchema,
    schemaOptions: Set[SchemaOption],
    featureFactoryConfig: FeatureFactoryConfig,
    settings: KnoraSettingsImpl
  ): String
}

/**
 * A trait for Knora API V2 response messages that are constructed as JSON-LD documents.
 */
trait KnoraJsonLDResponseV2 extends KnoraResponseV2 {

  override def format(
    rdfFormat: RdfFormat,
    targetSchema: OntologySchema,
    schemaOptions: Set[SchemaOption],
    featureFactoryConfig: FeatureFactoryConfig,
    settings: KnoraSettingsImpl
  ): String = {
    val targetApiV2Schema = targetSchema match {
      case apiV2Schema: ApiV2Schema => apiV2Schema
      case InternalSchema           => throw AssertionException(s"Response cannot be returned in the internal schema")
    }

    // Convert this response message to a JsonLDDocument.
    val jsonLDDocument: JsonLDDocument = toJsonLDDocument(
      targetSchema = targetApiV2Schema,
      settings = settings,
      schemaOptions = schemaOptions
    )

    // Which response format was requested?
    rdfFormat match {
      case JsonLD =>
        // JSON-LD. Have the JsonLDDocument format itself.
        jsonLDDocument.toPrettyString(SchemaOptions.returnFlatJsonLD(schemaOptions))

      case nonJsonLD: NonJsonLD =>
        // Some other format. Convert the JSON-LD document to an RDF model.
        val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil(featureFactoryConfig)
        val rdfModel: RdfModel           = jsonLDDocument.toRdfModel(rdfFormatUtil.getRdfModelFactory)

        // Convert the model to the requested format.
        rdfFormatUtil.format(
          rdfModel = rdfModel,
          rdfFormat = nonJsonLD,
          schemaOptions = schemaOptions
        )
    }
  }

  /**
   * Converts the response to a data structure that can be used to generate JSON-LD.
   *
   * @param targetSchema the Knora API schema to be used in the JSON-LD document.
   * @return a [[JsonLDDocument]] representing the response.
   */
  protected def toJsonLDDocument(
    targetSchema: ApiV2Schema,
    settings: KnoraSettingsImpl,
    schemaOptions: Set[SchemaOption]
  ): JsonLDDocument
}

/**
 * A trait for Knora API V2 response messages that are constructed as
 * strings in Turtle format in the internal schema.
 */
trait KnoraTurtleResponseV2 extends KnoraResponseV2 {

  /**
   * A string containing RDF data in Turtle format.
   */
  protected val turtle: String

  override def format(
    rdfFormat: RdfFormat,
    targetSchema: OntologySchema,
    schemaOptions: Set[SchemaOption],
    featureFactoryConfig: FeatureFactoryConfig,
    settings: KnoraSettingsImpl
  ): String = {
    if (targetSchema != InternalSchema) {
      throw AssertionException(s"Response can be returned only in the internal schema")
    }

    // Which response format was requested?
    rdfFormat match {
      case Turtle =>
        // Turtle. Return the Turtle string as is.
        turtle

      case _ =>
        // Some other format. Parse the Turtle to an RdfModel.
        val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil(featureFactoryConfig)
        val rdfModel: RdfModel           = rdfFormatUtil.parseToRdfModel(rdfStr = turtle, rdfFormat = Turtle)

        // Return the model in the requested format.
        rdfFormatUtil.format(
          rdfModel = rdfModel,
          rdfFormat = rdfFormat,
          schemaOptions = schemaOptions
        )
    }
  }
}

/**
 * Provides a message indicating that the result of an operation was successful.
 *
 * @param message the message to be returned.
 */
case class SuccessResponseV2(message: String) extends KnoraJsonLDResponseV2 {
  def toJsonLDDocument(
    targetSchema: ApiV2Schema,
    settings: KnoraSettingsImpl,
    schemaOptions: Set[SchemaOption]
  ): JsonLDDocument = {
    val (ontologyPrefixExpansion, resultProp) = targetSchema match {
      case ApiV2Simple =>
        (OntologyConstants.KnoraApiV2Simple.KnoraApiV2PrefixExpansion, OntologyConstants.KnoraApiV2Simple.Result)
      case ApiV2Complex =>
        (OntologyConstants.KnoraApiV2Complex.KnoraApiV2PrefixExpansion, OntologyConstants.KnoraApiV2Complex.Result)
    }

    JsonLDDocument(
      body = JsonLDObject(
        Map(resultProp -> JsonLDString(message))
      ),
      context = JsonLDObject(
        Map(OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> JsonLDString(ontologyPrefixExpansion))
      )
    )
  }
}

/**
 * Indicates whether an operation can be performed.
 *
 * @param canDo `true` if the operation can be performed.
 */
case class CanDoResponseV2(canDo: Boolean) extends KnoraJsonLDResponseV2 {
  def toJsonLDDocument(
    targetSchema: ApiV2Schema,
    settings: KnoraSettingsImpl,
    schemaOptions: Set[SchemaOption]
  ): JsonLDDocument = {
    if (targetSchema != ApiV2Complex) {
      throw BadRequestException(s"Response is available only in the complex schema")
    }

    JsonLDDocument(
      body = JsonLDObject(
        Map(OntologyConstants.KnoraApiV2Complex.CanDo -> JsonLDBoolean(canDo))
      ),
      context = JsonLDObject(
        Map(
          OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> JsonLDString(
            OntologyConstants.KnoraApiV2Complex.KnoraApiV2PrefixExpansion
          )
        )
      )
    )
  }
}

/**
 * A trait for content classes that can convert themselves between internal and internal schemas.
 *
 * @tparam C the type of the content class that extends this trait.
 */
trait KnoraContentV2[C <: KnoraContentV2[C]] {
  this: C =>
  def toOntologySchema(targetSchema: OntologySchema): C
}

/**
 * A trait for read wrappers that can convert themselves to external schemas.
 *
 * @tparam C the type of the read wrapper that extends this trait.
 */
trait KnoraReadV2[C <: KnoraReadV2[C]] {
  this: C =>
  def toOntologySchema(targetSchema: ApiV2Schema): C
}

/**
 * Allows the successful result of an update operation to indicate which project was updated.
 */
trait UpdateResultInProject {

  /**
   * The project that was updated.
   */
  def projectADM: ProjectADM
}
