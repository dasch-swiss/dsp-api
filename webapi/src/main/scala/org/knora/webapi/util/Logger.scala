package org.knora.webapi.util
import zio._
import zio.logging.LogFormat._
import zio.logging._
import zio.logging.slf4j.bridge.Slf4jBridge

object Logger {

  private val logFilter: LogFilter[String] = LogFilter.logLevelByName(
    LogLevel.Info,
    "zio.logging.slf4j" -> LogLevel.Debug,
    "SLF4J-LOGGER"      -> LogLevel.Warning
  )

  private val logFormatText: LogFormat =
    timestamp.fixed(32).color(LogColor.BLUE) |-|
      level.fixed(5).highlight |-|
      line.highlight |-|
      label("annotations", bracketed(annotations)) |-|
      label("spans", bracketed(spans)) +
      (space + label("cause", cause).highlight).filter(LogFilter.causeNonEmpty)

  private val textLog: ZLayer[Any, Nothing, Unit] = zio.logging.console(
    logFormatText,
    logFilter
  )

  private val logFormatJson: LogFormat =
    LogFormat.label("name", LoggerNameExtractor.loggerNameAnnotationOrTrace.toLogFormat()) +
      LogFormat.default +
      LogFormat.annotations +
      LogFormat.spans

  private val jsonLog: ZLayer[Any, Nothing, Unit] = consoleJson(
    logFormatJson,
    logFilter
  )

  private val useJsonLogger = sys.env.getOrElse("DSP_API_LOG_APPENDER", "TEXT") == "JSON"

  private val logger: ZLayer[Any, Nothing, Unit] =
    if (useJsonLogger) jsonLog
    else textLog

  def fromEnv(): ZLayer[Any, Nothing, Unit with Unit] =
    Runtime.removeDefaultLoggers >>> logger >+> Slf4jBridge.initialize

  def jsonLogger(): ZLayer[Any, Nothing, Unit with Unit] =
    Runtime.removeDefaultLoggers >>> jsonLog >+> Slf4jBridge.initialize

  def textLogger(): ZLayer[Any, Nothing, Unit with Unit] =
    Runtime.removeDefaultLoggers >>> textLog >+> Slf4jBridge.initialize
}
