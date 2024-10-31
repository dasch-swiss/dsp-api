/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure
import swiss.dasch.config.Configuration.ServiceConfig
import zio.*
import zio.logging.*
import zio.logging.LogFormat.*
import zio.logging.slf4j.bridge.Slf4jBridge

object Logger {

  private def logFilter(logLevel: String) = LogFilter.LogLevelByNameConfig(
    logLevel.toLowerCase() match {
      case "trace" => LogLevel.Trace
      case "debug" => LogLevel.Debug
      case "info"  => LogLevel.Info
      case "warn"  => LogLevel.Warning
      case "error" => LogLevel.Error
      case "fatal" => LogLevel.Fatal
      case _       => LogLevel.Info
    },
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

  private def textLogger(logLevel: String): ZLayer[Any, Nothing, Unit] = consoleLogger(
    ConsoleLoggerConfig(logFormatText, logFilter(logLevel)),
  )

  private val logFormatJson: LogFormat =
    LogFormat.label("name", LoggerNameExtractor.loggerNameAnnotationOrTrace.toLogFormat()) +
      LogFormat.default +
      LogFormat.annotations +
      LogFormat.spans

  private def jsonLogger(logLevel: String): ZLayer[Any, Nothing, Unit] = consoleJsonLogger(
    ConsoleLoggerConfig(logFormatJson, logFilter(logLevel)),
  )

  private val logger: ZLayer[ServiceConfig, Nothing, Unit] = ZLayer.service[ServiceConfig].flatMap { config =>
    val value: ServiceConfig = config.get
    val logLevel             = value.logLevel
    if (value.logFormat.toLowerCase() == "json") jsonLogger(logLevel)
    else textLogger(logLevel)
  }

  val layer: URLayer[ServiceConfig, Unit] =
    Runtime.removeDefaultLoggers >>> logger >+> Slf4jBridge.initialize
}
