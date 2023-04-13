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
import org.knora.webapi.IRI
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter

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

  def validateAndEscapeIri(s: String, errorMsg: String): ZIO[StringFormatter, BadRequestException, IRI] =
    ZIO.serviceWithZIO[StringFormatter] { stringFormatter =>
      stringFormatter
        .validateAndEscapeIri(s)
        .toZIO
        .orElseFail(BadRequestException(errorMsg))
    }

  def toSmartIri(s: String): ZIO[StringFormatter, Throwable, SmartIri] =
    ZIO.serviceWithZIO[StringFormatter](sf => ZIO.attempt(sf.toSmartIri(s)))

  def toSmartIri(s: String, errorMsg: String): ZIO[StringFormatter, BadRequestException, SmartIri] =
    toSmartIri(s).orElseFail(BadRequestException(errorMsg))

  def randomUuid(): UIO[UUID] = ZIO.random.flatMap(_.nextUUID)
}
