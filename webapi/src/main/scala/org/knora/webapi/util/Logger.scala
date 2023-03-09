package org.knora.webapi.util
import zio._
import zio.logging.LogFormat._
import zio.logging._
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
      label("annotations", bracketed(annotations)) |-|
      label("spans", bracketed(spans)) +
      (space + label("cause", cause).highlight).filter(LogFilter.causeNonEmpty)

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

  def fromEnv(): ZLayer[Any, Nothing, Unit with Unit] =
    Runtime.removeDefaultLoggers >>> logger >+> Slf4jBridge.initialize

  def json(): ZLayer[Any, Nothing, Unit with Unit] =
    Runtime.removeDefaultLoggers >>> jsonLogger >+> Slf4jBridge.initialize

  def text(): ZLayer[Any, Nothing, Unit with Unit] =
    Runtime.removeDefaultLoggers >>> textLogger >+> Slf4jBridge.initialize
}
