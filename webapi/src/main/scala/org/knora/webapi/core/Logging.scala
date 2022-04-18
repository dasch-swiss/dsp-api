package org.knora.webapi.core

import zio.logging._
import zio.logging.backend.SLF4J._
import zio.logging.backend.SLF4J
import zio.LogLevel
import zio.RuntimeConfigAspect
import zio.logging.LogFormat._

object Logging {
  val logFormat             = "[correlation-id = %s] %s"
  def generateCorrelationId = Some(java.util.UUID.randomUUID())

  val live: RuntimeConfigAspect = {
    SLF4J.slf4j(
      logLevel = LogLevel.Debug,
      format = LogFormat.colored,
      _ => "dsp"
    )
  }

  val textFormat: LogFormat =
    timestamp.fixed(32).color(LogColor.BLUE) |-| level.highlight |-| label("message", quoted(line)).highlight

  val testing: RuntimeConfigAspect = {
    SLF4J.slf4j(
      logLevel = LogLevel.Debug,
      format = textFormat,
      _ => "dsp"
    )
  }

}
