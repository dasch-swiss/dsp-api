/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.domain.SipiCommand.{FormatArgument, QueryArgument, TopLeftArgument}
import swiss.dasch.infrastructure.{CommandExecutor, ProcessOutput}
import zio.*
import zio.metrics.Metric
import zio.nio.file.Path

import java.io.IOException
import java.time.temporal.ChronoUnit

sealed trait SipiCommand {
  def flag(): String
  def render(): UIO[List[String]]
}

object SipiCommand {
  final case class QueryArgument(fileIn: Path) extends SipiCommand {
    def flag(): String = "--query"
    def render(): UIO[List[String]] =
      fileIn.toAbsolutePath.orDie.map(abs => List(flag(), abs.toString))
  }

  final case class FormatArgument(
    outputFormat: SipiImageFormat,
    fileIn: Path,
    fileOut: Path,
  ) extends SipiCommand {
    def flag(): String = "--format"
    def render(): UIO[List[String]] =
      (for {
        abs1 <- fileIn.toAbsolutePath
        abs2 <- fileOut.toAbsolutePath
      } yield List(flag(), outputFormat.toCliString, "--topleft", abs1.toString, abs2.toString)).orDie
  }

  /**
   * Applies the top-left correction to the image.
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
    def flag(): String = "--topleft"
    def render(): UIO[List[String]] =
      (for {
        abs1 <- fileIn.toAbsolutePath
        abs2 <- fileOut.toAbsolutePath
      } yield List(flag(), abs1.toString, abs2.toString)).orDie
  }
}

trait SipiClient {

  def applyTopLeftCorrection(fileIn: Path, fileOut: Path): UIO[ProcessOutput]

  def queryImageFile(file: Path): IO[IOException, ProcessOutput]

  def transcodeImageFile(
    fileIn: Path,
    fileOut: Path,
    outputFormat: SipiImageFormat,
  ): UIO[ProcessOutput]
}

object SipiClient {

  def applyTopLeftCorrection(fileIn: Path, fileOut: Path): RIO[SipiClient, ProcessOutput] =
    ZIO.serviceWithZIO[SipiClient](_.applyTopLeftCorrection(fileIn, fileOut))

  def queryImageFile(file: Path): ZIO[SipiClient, IOException, ProcessOutput] =
    ZIO.serviceWithZIO[SipiClient](_.queryImageFile(file))

  def transcodeImageFile(
    fileIn: Path,
    fileOut: Path,
    outputFormat: SipiImageFormat,
  ): RIO[SipiClient, ProcessOutput] =
    ZIO.serviceWithZIO[SipiClient](_.transcodeImageFile(fileIn, fileOut, outputFormat))
}

final case class SipiClientLive(executor: CommandExecutor) extends SipiClient {

  private val sipiPrefix = "/sipi/sipi"
  private val timer      = Metric.timer("sipi_command_duration", ChronoUnit.MILLIS, Chunk.iterate(1.0, 6)(_ * 10))

  private def execute(command: SipiCommand) =
    for {
      sipiParams <- command.render()
      cmd        <- executor.buildCommand(sipiPrefix, sipiParams: _*)
      timerTagged = timer.tagged("command", command.flag())
      out        <- executor.execute(cmd).orDie @@ timerTagged.trackDuration
    } yield out

  override def applyTopLeftCorrection(fileIn: Path, fileOut: Path): UIO[ProcessOutput] =
    execute(TopLeftArgument(fileIn, fileOut))

  override def transcodeImageFile(
    fileIn: Path,
    fileOut: Path,
    outputFormat: SipiImageFormat,
  ): UIO[ProcessOutput] =
    execute(FormatArgument(outputFormat, fileIn, fileOut))

  override def queryImageFile(file: Path): UIO[ProcessOutput] =
    execute(QueryArgument(file))
}

object SipiClientLive {
  val layer = ZLayer.derive[SipiClientLive]
}
