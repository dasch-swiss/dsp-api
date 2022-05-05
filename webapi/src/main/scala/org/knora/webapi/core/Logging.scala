package org.knora.webapi.core

import zio.LogLevel
import zio.RuntimeConfigAspect
import zio.logging.LogFormat._
import zio.logging._

object Logging {
  val logFormat             = "[correlation-id = %s] %s"
  def generateCorrelationId = Some(java.util.UUID.randomUUID())

  val textFormat: LogFormat =
    timestamp.fixed(32).color(LogColor.BLUE) |-| level.highlight.fixed(14) |-| line.highlight

  val fromDebug: RuntimeConfigAspect = {
    console(
      logLevel = LogLevel.Debug,
      format = textFormat
    )
  }

  val fromInfo: RuntimeConfigAspect = {
    console(
      logLevel = LogLevel.Info,
      format = textFormat
    )
  }

}
