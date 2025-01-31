/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import org.apache.pekko
import zio.*

import java.net.URLDecoder
import java.util.UUID

import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import dsp.valueobjects.UuidUtil
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.IRI
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

import pekko.http.scaladsl.server.RequestContext

object RouteUtilZ {

  /**
   * Url decodes a [[String]].
   * Fails if String is not a well formed utf-8 [[String]] in `application/x-www-form-urlencoded` MIME format.
   *
   * Wraps Java's [[java.net.URLDecoder#decode(java.lang.String, java.lang.String)]] into the zio world.
   *
   * @param value The value in utf-8 to be url decoded
   * @param errorMsg Custom error message for the error type
   * @return '''success''' the decoded string
   *
   *         '''failure''' A [[BadRequestException]] with the `errorMsg`
   */
  def urlDecode(value: String, errorMsg: String = ""): IO[BadRequestException, IRI] =
    ZIO
      .attempt(URLDecoder.decode(value, "utf-8"))
      .orElseFail(
        BadRequestException(
          if (!errorMsg.isBlank) errorMsg else s"Not an url encoded utf-8 String '$value'",
        ),
      )

  def decodeUuid(uuidStr: String): IO[BadRequestException, UUID] =
    ZIO.attempt(UuidUtil.decode(uuidStr)).orElseFail(BadRequestException(s"Invalid value UUID: $uuidStr"))

  def ensureExternalOntologyName(iri: SmartIri): IO[BadRequestException, SmartIri] =
    if (StringFormatter.isKnoraOntologyIri(iri)) {
      ZIO.fail(BadRequestException(s"Internal ontology <$iri> cannot be served"))
    } else {
      ZIO.succeed(iri)
    }

  def ensureIsKnoraOntologyIri(iri: SmartIri): IO[BadRequestException, SmartIri] =
    ZIO
      .succeed(iri)
      .filterOrFail(_.isKnoraOntologyIri)(BadRequestException(s"Iri is not a Knora ontology iri: $iri"))

  def ensureIsNotKnoraOntologyIri(iri: SmartIri): IO[BadRequestException, SmartIri] =
    ZIO
      .succeed(iri)
      .filterOrFail(!_.isKnoraOntologyIri)(BadRequestException(s"Iri is a Knora ontology iri: $iri"))

  def ensureIsKnoraResourceIri(iri: SmartIri): IO[BadRequestException, SmartIri] =
    ZIO.succeed(iri).filterOrFail(_.isKnoraResourceIri)(BadRequestException(s"Invalid resource IRI: $iri"))

  def ensureIsNotKnoraResourceIri(iri: SmartIri): IO[BadRequestException, SmartIri] =
    ZIO.succeed(iri).filterOrFail(!_.isKnoraResourceIri)(BadRequestException(s"Invalid resource IRI: $iri"))

  def ensureIsKnoraBuiltInDefinitionIri(iri: SmartIri): IO[BadRequestException, SmartIri] =
    ZIO
      .succeed(iri)
      .filterOrFail(_.isKnoraBuiltInDefinitionIri)(BadRequestException(s"Iri is not a Knora build in definition: $iri"))

  def ensureApiV2ComplexSchema(iri: SmartIri): IO[BadRequestException, SmartIri] =
    ZIO
      .succeed(iri)
      .filterOrFail(_.getOntologySchema.contains(ApiV2Complex))(BadRequestException(s"Invalid schema for <$iri>"))

  def validateAndEscapeIri(s: String, errorMsg: String): IO[BadRequestException, IRI] =
    Iri.validateAndEscapeIri(s).toZIO.orElseFail(BadRequestException(errorMsg))

  def toSmartIri(s: String): ZIO[IriConverter, Throwable, SmartIri] =
    ZIO.serviceWithZIO[IriConverter](_.asSmartIri(s))

  def toSmartIri(s: String, errorMsg: String): ZIO[IriConverter, BadRequestException, SmartIri] =
    toSmartIri(s).orElseFail(BadRequestException(errorMsg))

  def randomUuid(): UIO[UUID] = Random.nextUUID

  def getStringValueFromQuery(ctx: RequestContext, key: String): Option[String] = ctx.request.uri.query().get(key)

  def toSparqlEncodedString(s: String, errorMsg: String): IO[BadRequestException, String] =
    ZIO.fromOption(Iri.toSparqlEncodedString(s)).orElseFail(BadRequestException(errorMsg))
}
