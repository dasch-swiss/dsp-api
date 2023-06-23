/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure
import zio.*
import zio.logging.LogFormat.*
import zio.logging.*
import zio.logging.slf4j.bridge.Slf4jBridge

object Logger {

  private val logFilter: LogFilter[String] = LogFilter.logLevelByName(
    LogLevel.Info
      // Example of how to change log levels for loggers by name
      // "zio.logging.slf4j" -> LogLevel.Debug,
      // "SLF4J-LOGGER"      -> LogLevel.Warning
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
    ConsoleLoggerConfig(logFormatText, logFilter)
  )

  private val logFormatJson: LogFormat =
    LogFormat.label("name", LoggerNameExtractor.loggerNameAnnotationOrTrace.toLogFormat()) +
      LogFormat.default +
      LogFormat.annotations +
      LogFormat.spans

  private val jsonLogger: ZLayer[Any, Nothing, Unit] = consoleJsonLogger(
    ConsoleLoggerConfig(logFormatJson, logFilter)
  )

  private val useJsonLogger = sys.env.getOrElse("LOG_FORMAT", "TEXT") == "JSON"

  private val logger: ZLayer[Any, Nothing, Unit] =
    if (useJsonLogger) jsonLogger
    else textLogger

  def fromEnv(): ZLayer[Any, Nothing, Unit with Unit] =
    Runtime.removeDefaultLoggers >>> logger >+> Slf4jBridge.initialize
}
