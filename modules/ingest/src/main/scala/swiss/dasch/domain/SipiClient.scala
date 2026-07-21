/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.domain.SipiCommand.ApplyTopLeft
import swiss.dasch.domain.SipiCommand.Query
import swiss.dasch.domain.SipiCommand.Transcode
import swiss.dasch.infrastructure.CommandExecutor
import swiss.dasch.infrastructure.ProcessOutput
import zio.*
import zio.json.*
import zio.metrics.Metric
import zio.nio.file.Path

import java.io.IOException
import java.time.temporal.ChronoUnit

sealed trait SipiCommand {

  /** The Sipi v5 verb-noun subcommand name (e.g. "query", "convert"). */
  def subcommand(): String

  def render(): UIO[List[String]]

  /**
   * Whether this invocation emits the structured `--json` report on stdout
   * (`SipiReport` schema). Sipi v5's `convert` subcommand emits it for both
   * success and error; `query`'s `--json` flag is documented but in v5.0.0 the
   * success path still prints the human-readable text dump that
   * `StillImageService` parses, so `Query` stays `false` to avoid
   * routing through `runWithJsonReport`'s parse path.
   */
  def emitsJson: Boolean = false
}

object SipiCommand {
  final case class Query(fileIn: Path) extends SipiCommand {
    def subcommand(): String        = "query"
    def render(): UIO[List[String]] =
      fileIn.toAbsolutePath.orDie.map(abs => List(subcommand(), abs.toString))
  }

  final case class Transcode(
    outputFormat: SipiImageFormat,
    fileIn: Path,
    fileOut: Path,
  ) extends SipiCommand {
    def subcommand(): String        = "convert"
    override val emitsJson: Boolean = true
    def render(): UIO[List[String]] =
      (for {
        abs1 <- fileIn.toAbsolutePath
        abs2 <- fileOut.toAbsolutePath
      } yield List(
        subcommand(),
        abs1.toString,
        abs2.toString,
        "--json",
        "--format",
        outputFormat.toCliString,
        "--topleft",
      )).orDie
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
  final case class ApplyTopLeft(
    fileIn: Path,
    fileOut: Path,
  ) extends SipiCommand {
    def subcommand(): String        = "convert"
    override val emitsJson: Boolean = true
    def render(): UIO[List[String]] =
      (for {
        abs1 <- fileIn.toAbsolutePath
        abs2 <- fileOut.toAbsolutePath
      } yield List(subcommand(), abs1.toString, abs2.toString, "--json", "--topleft")).orDie
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

  private val sipiPrefix = "/sbin/sipi"
  private val timer      = Metric.timer("sipi_command_duration", ChronoUnit.MILLIS, Chunk.iterate(1.0, 6)(_ * 10))

  private def execute(command: SipiCommand): IO[IOException, ProcessOutput] =
    for {
      sipiParams <- command.render()
      cmd        <- executor.buildCommand(sipiPrefix, sipiParams*)
      timerTagged = timer.tagged("command", command.subcommand())
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
    execute(ApplyTopLeft(fileIn, fileOut))

  override def transcodeImageFile(
    fileIn: Path,
    fileOut: Path,
    outputFormat: SipiImageFormat,
  ): IO[IOException, ProcessOutput] =
    execute(Transcode(outputFormat, fileIn, fileOut))

  override def queryImageFile(file: Path): IO[IOException, ProcessOutput] =
    execute(Query(file))
}

object SipiClientLive {
  val layer = ZLayer.derive[SipiClientLive]
}
