/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing
import zio.Task
import zio.ZIO

import java.net.URLDecoder

import dsp.errors.BadRequestException

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
}
