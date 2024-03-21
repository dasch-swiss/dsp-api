/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util
import zio.LogLevel
import zio._
import zio.logging.LogFilter
import zio.logging.LogFormat._
import zio.logging._
import zio.logging.slf4j.bridge.Slf4jBridge

object Logger {

  private val logFilter = LogFilter.LogLevelByNameConfig(
    LogLevel.Info,
      // Uncomment the following lines to change the log level for specific loggers:
      // , ("zio.logging.slf4j", LogLevel.Debug)
      // , ("SLF4J-LOGGER", LogLevel.Warning)
  )

  private val logFormatText: LogFormat =
    timestamp.fixed(32).color(LogColor.BLUE) |-|
      level.fixed(5).highlight |-|
      line.highlight |-|
      LoggerNameExtractor.loggerNameAnnotationOrTrace.toLogFormat() |-|
      label("annotations", bracketed(annotations)) |-|
      label("spans", bracketed(spans)) +
      (space + label("cause", cause).highlight).filter(LogFilter.causeNonEmpty)

  private val textLogger: ZLayer[Any, Nothing, Unit] = consoleLogger(
    ConsoleLoggerConfig(logFormatText, logFilter),
  )

  private val logFormatJson: LogFormat =
    LogFormat.label("name", LoggerNameExtractor.loggerNameAnnotationOrTrace.toLogFormat()) +
      LogFormat.default +
      LogFormat.annotations +
      LogFormat.spans

  private val jsonLogger: ZLayer[Any, Nothing, Unit] = consoleJsonLogger(
    ConsoleLoggerConfig(logFormatJson, logFilter),
  )

  private val useJsonLogger = sys.env.getOrElse("DSP_API_LOG_APPENDER", "TEXT") == "JSON"

  private val logger: ZLayer[Any, Nothing, Unit] =
    if (useJsonLogger) jsonLogger
    else textLogger

  def fromEnv(): ZLayer[Any, Nothing, Unit & Unit] =
    Runtime.removeDefaultLoggers >>> logger >+> Slf4jBridge.initialize

  def json(): ZLayer[Any, Nothing, Unit & Unit] =
    Runtime.removeDefaultLoggers >>> jsonLogger >+> Slf4jBridge.initialize

  def text(): ZLayer[Any, Nothing, Unit & Unit] =
    Runtime.removeDefaultLoggers >>> textLogger >+> Slf4jBridge.initialize
}
