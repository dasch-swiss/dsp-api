package org.knora.webapi.core

import zio.LogLevel
import zio.logging.LogFormat._
import zio.logging._
import zio.ZLayer
import zio.logging.backend.SLF4J

object Logging {
  val logFormat             = "[correlation-id = %s] %s"
  def generateCorrelationId = Some(java.util.UUID.randomUUID())

  val textFormat: LogFormat =
    timestamp.fixed(32).color(LogColor.BLUE) |-| level.highlight.fixed(14) |-| line.highlight

  val colored: LogFormat =
    label("timestamp", timestamp.fixed(32)).color(LogColor.BLUE) |-|
      label("level", level).highlight |-|
      label("message", quoted(line)).highlight

  val fromDebug: ZLayer[Any, Nothing, Unit] =
    console(
      logLevel = LogLevel.Debug,
      format = textFormat
    )

  val fromInfo: ZLayer[Any, Nothing, Unit] =
    console(
      logLevel = LogLevel.Info,
      format = colored
    )

  val slf4jFormat = line

  val slf4j =
    SLF4J.slf4j(LogLevel.Debug, slf4jFormat, _ => "zio-slf4j-logger")

}
