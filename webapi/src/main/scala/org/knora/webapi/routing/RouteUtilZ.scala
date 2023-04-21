/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing
import akka.http.scaladsl.server.RequestContext
import zio._

import java.net.URLDecoder
import java.util.UUID

import dsp.errors.BadRequestException
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.IRI
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

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
          if (!errorMsg.isBlank) errorMsg else s"Not an url encoded utf-8 String '$value'"
        )
      )

  def decodeUuid(uuidStr: String): ZIO[StringFormatter, BadRequestException, UUID] =
    ZIO.serviceWithZIO[StringFormatter] { sf =>
      ZIO.attempt(sf.decodeUuid(uuidStr)).orElseFail(BadRequestException(s"Invalid value UUID: $uuidStr"))
    }

  def ensureExternalOntologyName(iri: SmartIri): ZIO[StringFormatter, BadRequestException, SmartIri] =
    ZIO.serviceWithZIO[StringFormatter] { sf =>
      if (sf.isKnoraOntologyIri(iri)) {
        ZIO.fail(BadRequestException(s"Internal ontology <$iri> cannot be served"))
      } else {
        ZIO.succeed(iri)
      }
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
    StringFormatter.validateAndEscapeIri(s).toZIO.orElseFail(BadRequestException(errorMsg))

  def toSmartIri(s: String): ZIO[IriConverter, Throwable, SmartIri] =
    ZIO.serviceWithZIO[IriConverter](_.asSmartIri(s))

  def toSmartIri(s: String, errorMsg: String): ZIO[IriConverter, BadRequestException, SmartIri] =
    toSmartIri(s).orElseFail(BadRequestException(errorMsg))

  def randomUuid(): UIO[UUID] = ZIO.random.flatMap(_.nextUUID)

  def getStringValueFromQuery(ctx: RequestContext, key: String): Option[String] = ctx.request.uri.query().get(key)

  def toSparqlEncodedString(s: String, errorMsg: String): ZIO[StringFormatter, BadRequestException, String] =
    ZIO.serviceWithZIO[StringFormatter](sf =>
      ZIO.fromOption(sf.toSparqlEncodedString(s)).orElseFail(BadRequestException(errorMsg))
    )
}
