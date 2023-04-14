/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing
import zio.IO
import zio.Task
import zio.UIO
import zio.ZIO

import java.net.URLDecoder
import java.util.UUID

import dsp.errors.BadRequestException
import org.knora.webapi.ApiV2Complex
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
  def urlDecode(value: String, errorMsg: String = ""): Task[String] =
    ZIO
      .attempt(URLDecoder.decode(value, "utf-8"))
      .orElseFail(
        BadRequestException(
          if (!errorMsg.isBlank) errorMsg else s"Not an url encoded utf-8 String '$value'"
        )
      )

  def randomUuid(): UIO[UUID] = ZIO.random.flatMap(_.nextUUID)

  def toSmartIri(str: String, errMsg: String): ZIO[IriConverter, BadRequestException, SmartIri] =
    ZIO.serviceWithZIO[IriConverter](_.asSmartIri(str)).orElseFail(BadRequestException(errMsg))

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

  def ensureIsKnoraBuiltInDefinitionIri(iri: SmartIri): IO[BadRequestException, SmartIri] =
    ZIO
      .succeed(iri)
      .filterOrFail(_.isKnoraBuiltInDefinitionIri)(BadRequestException(s"Iri is not a Knora build in definition: $iri"))

  def ensureApiV2ComplexSchema(iri: SmartIri): IO[BadRequestException, SmartIri] =
    ZIO
      .succeed(iri)
      .filterOrFail(_.getOntologySchema.contains(ApiV2Complex))(BadRequestException(s"Invalid schema for <$iri>"))
}
