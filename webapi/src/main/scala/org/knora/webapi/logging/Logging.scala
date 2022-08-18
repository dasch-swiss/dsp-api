/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.logging

import zio.LogLevel
import zio.logging.LogFormat._
import zio.logging._
import zio.ZLayer
import org.knora.webapi.config.AppConfig
import zio._

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

  val toConsole     = console(logLevel = LogLevel.Info, format = LogFormat.default)
  val toConsoleJson = consoleJson(logLevel = LogLevel.Info, format = LogFormat.default)

}
