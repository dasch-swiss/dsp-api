package org.knora.webapi.core

import zio.logging._
import zio.logging.backend.SLF4J._
import zio.logging.backend.SLF4J
import zio.LogLevel
import zio.RuntimeConfigAspect

object Logging {
  val logFormat             = "[correlation-id = %s] %s"
  def generateCorrelationId = Some(java.util.UUID.randomUUID())

  val live: RuntimeConfigAspect = {
    SLF4J.slf4j(
      logLevel = LogLevel.Debug,
      format = LogFormat.default,
      _ => "dsp"
    )
  }

  val console: RuntimeConfigAspect = {
    zio.logging.console(
      logLevel = LogLevel.Info,
      format = LogFormat.default
    )
  }

}
