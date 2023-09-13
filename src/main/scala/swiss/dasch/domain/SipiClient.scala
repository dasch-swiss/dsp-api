/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.config.Configuration.{ SipiConfig, StorageConfig }
import swiss.dasch.domain.SipiCommand.{ FormatArgument, QueryArgument, TopLeftArgument }
import zio.*
import zio.metrics.Metric
import zio.nio.file.Path

import java.io.IOException
import java.time.temporal.ChronoUnit
import scala.sys.process.{ ProcessLogger, stringToProcess }

sealed trait SipiCommand {
  def flag(): String
  def render(): UIO[String]
}

object SipiCommand {
  final case class QueryArgument(fileIn: Path) extends SipiCommand {
    def flag(): String        = "--query"
    def render(): UIO[String] =
      fileIn.toAbsolutePath.orDie.map(abs => s"${flag()} $abs")
  }

  final case class FormatArgument(
      outputFormat: SipiImageFormat,
      fileIn: Path,
      fileOut: Path,
    ) extends SipiCommand {
    def flag(): String        = "--format"
    def render(): UIO[String] =
      (for {
        abs1 <- fileIn.toAbsolutePath
        abs2 <- fileOut.toAbsolutePath
      } yield s"${flag()} ${outputFormat.toCliString} $abs1 $abs2").orDie
  }

  /** Applies the top-left correction to the image.
    *
    * @param fileIn
    *   the image file to be corrected
    * @param fileOut
    *   the corrected image file,
    *
    * will be created if it does not exist,
    *
    * will be overwritten if it exists,
    *
    * if fileOut is the same as fileIn, the file will be overwritten
    */
  final case class TopLeftArgument(
      fileIn: Path,
      fileOut: Path,
    ) extends SipiCommand {
    def flag(): String        = "--topleft"
    def render(): UIO[String] =
      (for {
        abs1 <- fileIn.toAbsolutePath
        abs2 <- fileOut.toAbsolutePath
      } yield s"${flag()} $abs1 $abs2").orDie
  }
}

final case class SipiOutput(stdOut: String, stdErr: String)
trait SipiClient {

  def applyTopLeftCorrection(fileIn: Path, fileOut: Path): UIO[SipiOutput]

  def queryImageFile(file: Path): IO[IOException, SipiOutput]

  def transcodeImageFile(
      fileIn: Path,
      fileOut: Path,
      outputFormat: SipiImageFormat,
    ): UIO[SipiOutput]
}

object SipiClient {

  def applyTopLeftCorrection(fileIn: Path, fileOut: Path): RIO[SipiClient, SipiOutput] =
    ZIO.serviceWithZIO[SipiClient](_.applyTopLeftCorrection(fileIn, fileOut))

  def queryImageFile(file: Path): ZIO[SipiClient, IOException, SipiOutput] =
    ZIO.serviceWithZIO[SipiClient](_.queryImageFile(file))

  def transcodeImageFile(
      fileIn: Path,
      fileOut: Path,
      outputFormat: SipiImageFormat,
    ): RIO[SipiClient, SipiOutput] =
    ZIO.serviceWithZIO[SipiClient](_.transcodeImageFile(fileIn, fileOut, outputFormat))
}

final case class SipiClientLive(prefix: String) extends SipiClient {

  private val timer = Metric.timer("sipi_command_duration", ChronoUnit.MILLIS, Chunk.iterate(1.0, 6)(_ * 10))

  private def execute(command: SipiCommand): UIO[SipiOutput] =
    command
      .render()
      .map(prefix + " " + _)
      .flatMap { cmd =>
        val timerTagged = timer.tagged("command", command.flag())
        val logger      = new InMemoryProcessLogger
        ZIO.logInfo(s"Running sipi \n$cmd") *>
          (ZIO.succeed(cmd ! logger) @@ timerTagged.trackDuration)
            .as(logger.getOutput)
            .tap(out => ZIO.logDebug(out.toString))
      }
      .logError

  override def applyTopLeftCorrection(fileIn: Path, fileOut: Path): UIO[SipiOutput] =
    execute(TopLeftArgument(fileIn, fileOut))

  override def transcodeImageFile(
      fileIn: Path,
      fileOut: Path,
      outputFormat: SipiImageFormat,
    ): UIO[SipiOutput] =
    execute(FormatArgument(outputFormat, fileIn, fileOut))

  override def queryImageFile(file: Path): UIO[SipiOutput] =
    execute(QueryArgument(file))
}

final private class InMemoryProcessLogger extends ProcessLogger {
  private val sbOut                    = new StringBuilder
  private val sbErr                    = new StringBuilder
  override def out(s: => String): Unit = sbOut.append(s + "\n")
  override def err(s: => String): Unit = sbErr.append(s + "\n")
  override def buffer[T](f: => T): T   = f
  def getOutput: SipiOutput            = SipiOutput(sbOut.toString(), sbErr.toString())
}

object SipiClientLive {

  private val sipiPrefix = "/sipi/sipi"

  private def dockerPrefix(assetPath: Path) =
    s"docker run --entrypoint $sipiPrefix -v $assetPath:$assetPath daschswiss/knora-sipi:latest"

  val layer: URLayer[SipiConfig with StorageConfig, SipiClient] = ZLayer.fromZIO {
    for {
      config            <- ZIO.service[SipiConfig]
      absoluteAssetPath <- ZIO.serviceWithZIO[StorageConfig](_.assetPath.toAbsolutePath).orDie
      prefix             = if (config.useLocalDev) { dockerPrefix(absoluteAssetPath) }
                           else { sipiPrefix }
    } yield SipiClientLive(prefix)
  }
}
