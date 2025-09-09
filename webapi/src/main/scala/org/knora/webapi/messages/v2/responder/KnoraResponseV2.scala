/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder

import dsp.errors.AssertionException
import dsp.errors.BadRequestException
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.slice.admin.api.model.Project
import org.knora.webapi.slice.common.api.KnoraResponseRenderer

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
   * @param appConfig            the application configuration.
   * @return a formatted string representing this response message.
   */
  def format(
    rdfFormat: RdfFormat,
    targetSchema: OntologySchema,
    schemaOptions: Set[Rendering],
    appConfig: AppConfig,
  ): String

  def format(opts: KnoraResponseRenderer.FormatOptions, config: AppConfig): String =
    format(opts.rdfFormat, opts.schema, opts.rendering, config)
}

/**
 * A trait for Knora API V2 response messages that are constructed as JSON-LD documents.
 */
trait KnoraJsonLDResponseV2 extends KnoraResponseV2 {

  override def format(
    rdfFormat: RdfFormat,
    targetSchema: OntologySchema,
    schemaOptions: Set[Rendering],
    appConfig: AppConfig,
  ): String = {
    val targetApiV2Schema = targetSchema match {
      case apiV2Schema: ApiV2Schema => apiV2Schema
      case InternalSchema           => throw AssertionException(s"Response cannot be returned in the internal schema")
    }

    // Convert this response message to a JsonLDDocument.
    val jsonLDDocument: JsonLDDocument = toJsonLDDocument(
      targetSchema = targetApiV2Schema,
      appConfig = appConfig,
      schemaOptions = schemaOptions,
    )

    // Which response format was requested?
    rdfFormat match {
      case JsonLD =>
        // JSON-LD. Have the JsonLDDocument format itself.
        jsonLDDocument.toPrettyString(SchemaOptions.returnFlatJsonLD(schemaOptions))

      case nonJsonLD: NonJsonLD =>
        // Some other format. Convert the JSON-LD document to an RDF model.
        val rdfModel: RdfModel = jsonLDDocument.toRdfModel

        // Convert the model to the requested format.
        RdfFormatUtil.format(
          rdfModel = rdfModel,
          rdfFormat = nonJsonLD,
          schemaOptions = schemaOptions,
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
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
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
    schemaOptions: Set[Rendering],
    appConfig: AppConfig,
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
        val rdfModel: RdfModel = RdfModel.fromTurtle(turtle)

        // Return the model in the requested format.
        RdfFormatUtil.format(
          rdfModel = rdfModel,
          rdfFormat = rdfFormat,
          schemaOptions = schemaOptions,
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
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDDocument = {
    val (ontologyPrefixExpansion, resultProp) = targetSchema match {
      case ApiV2Simple =>
        (OntologyConstants.KnoraApiV2Simple.KnoraApiV2PrefixExpansion, OntologyConstants.KnoraApiV2Simple.Result)
      case ApiV2Complex =>
        (OntologyConstants.KnoraApiV2Complex.KnoraApiV2PrefixExpansion, OntologyConstants.KnoraApiV2Complex.Result)
    }

    JsonLDDocument(
      body = JsonLDObject(
        Map(resultProp -> JsonLDString(message)),
      ),
      context = JsonLDObject(
        Map(OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> JsonLDString(ontologyPrefixExpansion)),
      ),
    )
  }
}

/**
 * Indicates whether an operation can be performed.
 *
 * @param canDo `true` if the operation can be performed.
 */
final case class CanDoResponseV2(
  canDo: JsonLDBoolean,
  cannotDoReason: Option[JsonLDString] = None,
  cannotDoReasonContext: Option[JsonLDObject] = None,
) extends KnoraJsonLDResponseV2 {
  require((cannotDoReason.nonEmpty && !canDo.value) || cannotDoReason.isEmpty)
  require((cannotDoReasonContext.nonEmpty && !canDo.value) || cannotDoReasonContext.isEmpty)

  override def toJsonLDDocument(
    targetSchema: ApiV2Schema,
    appConfig: AppConfig,
    schemaOptions: Set[Rendering],
  ): JsonLDDocument = {
    if (targetSchema != ApiV2Complex) {
      throw BadRequestException(s"Response is available only in the complex schema")
    }
    val bodyMap: Map[IRI, JsonLDValue] = Map(OntologyConstants.KnoraApiV2Complex.CanDo -> canDo)
    val reasonMap: Map[IRI, JsonLDValue] = cannotDoReason
      .filter(_.value.nonEmpty)
      .map(reason => Map(OntologyConstants.KnoraApiV2Complex.CannotDoReason -> reason))
      .getOrElse(Map.empty)
    val cannotDoContextMap: Map[IRI, JsonLDValue] = cannotDoReasonContext
      .map(context => Map(OntologyConstants.KnoraApiV2Complex.CannotDoContext -> context))
      .getOrElse(Map.empty)

    val body = JsonLDObject(bodyMap ++ reasonMap ++ cannotDoContextMap)
    val context = JsonLDObject(
      Map(
        OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> JsonLDString(
          OntologyConstants.KnoraApiV2Complex.KnoraApiV2PrefixExpansion,
        ),
      ),
    )
    JsonLDDocument(body, context)
  }

  def assertGoodRequestEither: Either[BadRequestException, Unit] =
    if (canDo._1) Right(())
    else Left(BadRequestException(cannotDoReason.map(_._1).getOrElse("")))
}

object CanDoResponseV2 {
  val yes: CanDoResponseV2 = CanDoResponseV2(JsonLDBoolean.TRUE)
  val no: CanDoResponseV2  = CanDoResponseV2(JsonLDBoolean.FALSE)
  def of(boolean: Boolean): CanDoResponseV2 = boolean match {
    case true  => yes
    case false => no
  }
  def no(reason: String): CanDoResponseV2 =
    CanDoResponseV2(
      canDo = JsonLDBoolean.FALSE,
      cannotDoReason = Some(JsonLDString(reason)),
      cannotDoReasonContext = None,
    )
  def no(reason: String, context: JsonLDObject): CanDoResponseV2 =
    CanDoResponseV2(
      canDo = JsonLDBoolean.FALSE,
      cannotDoReason = Some(JsonLDString(reason)),
      cannotDoReasonContext = Some(context),
    )
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
  def projectADM: Project
}
