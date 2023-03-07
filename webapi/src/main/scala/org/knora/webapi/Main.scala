/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import zio._

import org.knora.webapi.core._
import zio.logging.slf4j.bridge.Slf4jBridge
import zio.logging.consoleJson
import zio.logging.LogFormat
import zio.logging.LogFilter
import zio.logging.LoggerNameExtractor
import zio.logging.LogColor

object Main extends ZIOApp {

  override def environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  /**
   * The `Environment` that we require to exist at startup.
   */
  override type Environment = LayersLive.DspEnvironmentLive

  private val logFilter: LogFilter[String] = LogFilter.logLevelByName(
    LogLevel.Info,
    "zio.logging.slf4j" -> LogLevel.Debug,
    "SLF4J-LOGGER"      -> LogLevel.Warning
  )

  private val logFormatText: LogFormat = {
    import LogFormat._
    timestamp.fixed(32).color(LogColor.BLUE) |-|
      level.fixed(5).highlight |-|
      line.highlight |-|
      label("annotations", bracketed(annotations)) |-|
      label("spans", bracketed(spans)) +
      (space + label("cause", cause).highlight).filter(LogFilter.causeNonEmpty)
  }

  private val textLogger: ZLayer[Any, Nothing, Unit] = zio.logging.console(
    logFormatText,
    logFilter
  )

  private val logFormatJson: LogFormat =
    LogFormat.label("name", LoggerNameExtractor.loggerNameAnnotationOrTrace.toLogFormat()) +
      LogFormat.default +
      LogFormat.annotations +
      LogFormat.spans

  private val jsonLogger: ZLayer[Any, Nothing, Unit] = consoleJson(
    logFormatJson,
    logFilter
  )

  private val useJsonLogger = sys.env.getOrElse("DSP_API_LOG_APPENDER", "TEXT") == "JSON"

  private val logger: ZLayer[Any, Nothing, Unit] =
    if (useJsonLogger) jsonLogger
    else textLogger

  private val logEnv: ZLayer[Any, Nothing, Unit with Unit] =
    Runtime.removeDefaultLoggers >>> logger >+> Slf4jBridge.initialize

  /**
   * `Bootstrap` will ensure that everything is instantiated when the Runtime is created
   * and cleaned up when the Runtime is shutdown.
   */
  override def bootstrap: ZLayer[Any, Nothing, LayersLive.DspEnvironmentLive] =
    logEnv >>> LayersLive.dspLayersLive

  /**
   *  Entrypoint of our Application
   */
  override def run: ZIO[Environment with ZIOAppArgs with Scope, Any, Any] =
    InstrumentationServer.make *> AppServer.make *> ZIO.never
}
