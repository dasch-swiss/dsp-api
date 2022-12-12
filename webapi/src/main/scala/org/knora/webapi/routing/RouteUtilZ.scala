package org.knora.webapi.routing
import zio.Task
import zio.ZIO

import java.net.URLDecoder

import dsp.errors.BadRequestException

object RouteUtilZ {
  def decodeUrl(value: String, errorMsg: String = "Failed to decode IRI "): Task[String] = ZIO
    .attempt(URLDecoder.decode(value, "utf-8"))
    .orElseFail(BadRequestException(errorMsg + value))
}
