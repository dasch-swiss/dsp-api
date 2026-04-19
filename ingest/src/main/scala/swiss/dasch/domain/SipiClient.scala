/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.domain.SipiCommand.FormatArgument
import swiss.dasch.domain.SipiCommand.QueryArgument
import swiss.dasch.domain.SipiCommand.TopLeftArgument
import swiss.dasch.infrastructure.CommandExecutor
import swiss.dasch.infrastructure.ProcessOutput
import zio.*
import zio.json.*
import zio.metrics.Metric
import zio.nio.file.Path

import java.io.IOException
import java.time.temporal.ChronoUnit

sealed trait SipiCommand {
  def flag(): String
  def render(): UIO[List[String]]

  /**
   * Whether this invocation emits the structured `--json` report on stdout.
   * `--json` is mutually exclusive with `--salsah` and `--query` at parse time
   * (see `sipi/src/sipi.cpp:647`), so `QueryArgument` stays false.
   */
  def emitsJson: Boolean = false
}

object SipiCommand {
  final case class QueryArgument(fileIn: Path) extends SipiCommand {
    def flag(): String              = "--query"
    def render(): UIO[List[String]] =
      fileIn.toAbsolutePath.orDie.map(abs => List(flag(), abs.toString))
  }

  final case class FormatArgument(
    outputFormat: SipiImageFormat,
    fileIn: Path,
    fileOut: Path,
  ) extends SipiCommand {
    def flag(): String              = "--format"
    override val emitsJson: Boolean = true
    def render(): UIO[List[String]] =
      (for {
        abs1 <- fileIn.toAbsolutePath
        abs2 <- fileOut.toAbsolutePath
      } yield List("--json", flag(), outputFormat.toCliString, "--topleft", abs1.toString, abs2.toString)).orDie
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
    def flag(): String              = "--topleft"
    override val emitsJson: Boolean = true
    def render(): UIO[List[String]] =
      (for {
        abs1 <- fileIn.toAbsolutePath
        abs2 <- fileOut.toAbsolutePath
      } yield List("--json", flag(), abs1.toString, abs2.toString)).orDie
  }
}

trait SipiClient {

  def applyTopLeftCorrection(fileIn: Path, fileOut: Path): IO[IOException, ProcessOutput]

  def queryImageFile(file: Path): IO[IOException, ProcessOutput]

  def transcodeImageFile(
    fileIn: Path,
    fileOut: Path,
    outputFormat: SipiImageFormat,
  ): IO[IOException, ProcessOutput]
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

  private def execute(command: SipiCommand): IO[IOException, ProcessOutput] =
    for {
      sipiParams <- command.render()
      cmd        <- executor.buildCommand(sipiPrefix, sipiParams: _*)
      timerTagged = timer.tagged("command", command.flag())
      out        <- (
               if (command.emitsJson) runWithJsonReport(cmd)
               else executor.executeOrFail(cmd)
             ) @@ timerTagged.trackDuration
    } yield out

  /**
   * Runs a `--json`-emitting sipi command, parsing the structured report on
   * stdout. On a non-zero exit with a parseable `SipiReport.Err`, fails with
   * [[SipiCliError]]; otherwise falls back to the legacy command-failure
   * `IOException` so callers that don't care about the report still behave
   * identically to before.
   */
  private def runWithJsonReport(cmd: swiss.dasch.infrastructure.Command): IO[IOException, ProcessOutput] =
    executor
      .execute(cmd)
      .flatMap { out =>
        if (out.exitCode == 0) ZIO.succeed(out)
        else
          out.stdout.fromJson[SipiReport] match {
            case Right(err: SipiReport.Err) =>
              ZIO.fail(
                SipiCliError(
                  phase = err.phase,
                  message = err.errorMessage,
                  inputFile = Option(err.inputFile).filter(_.nonEmpty),
                  imageMeta = err.image,
                  exitCode = out.exitCode,
                ),
              )
            case _ =>
              ZIO.fail(new IOException(s"Command failed: '${cmd.cmd}' $out"))
          }
      }

  override def applyTopLeftCorrection(fileIn: Path, fileOut: Path): IO[IOException, ProcessOutput] =
    execute(TopLeftArgument(fileIn, fileOut))

  override def transcodeImageFile(
    fileIn: Path,
    fileOut: Path,
    outputFormat: SipiImageFormat,
  ): IO[IOException, ProcessOutput] =
    execute(FormatArgument(outputFormat, fileIn, fileOut))

  override def queryImageFile(file: Path): IO[IOException, ProcessOutput] =
    execute(QueryArgument(file))
}

object SipiClientLive {
  val layer = ZLayer.derive[SipiClientLive]
}
