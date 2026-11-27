/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.domain.SipiCommand.ApplyTopLeft
import swiss.dasch.domain.SipiCommand.Query
import swiss.dasch.domain.SipiCommand.Transcode
import swiss.dasch.domain.SipiReport.Phase
import swiss.dasch.infrastructure.Command
import swiss.dasch.infrastructure.CommandExecutor
import swiss.dasch.infrastructure.CorrelationId
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

  /**
   * Every command renders `--json`: on failure stdout holds one `SipiReport` error document, and log lines go to
   * stderr. On success `convert` prints an `ok` document, while `query` keeps its text dump, which
   * `StillImageService` parses from stdout.
   */
  def render(): UIO[List[String]]

  /** Decides whether a content error is the user's (422) or ingest's (500). */
  def target: SipiTarget
}

object SipiCommand {
  final case class Query(fileIn: Path) extends SipiCommand {
    def subcommand(): String        = "query"
    val target: SipiTarget          = SipiTarget.Derivative
    def render(): UIO[List[String]] =
      fileIn.toAbsolutePath.orDie.map(abs => List(subcommand(), abs.toString, "--json"))
  }

  final case class Transcode(
    outputFormat: SipiImageFormat,
    fileIn: Path,
    fileOut: Path,
  ) extends SipiCommand {
    def subcommand(): String        = "convert"
    val target: SipiTarget          = SipiTarget.Upload
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
    val target: SipiTarget          = SipiTarget.Derivative
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

  /** Scoped, so that `buildCommand`, `execute` and a failure without a report all use the same correlation ID. */
  private def execute(command: SipiCommand): IO[IOException, ProcessOutput] =
    CorrelationId.scoped(for {
      sipiParams <- command.render()
      cmd        <- executor.buildCommand(sipiPrefix, sipiParams*)
      out        <- runWithJsonReport(command, cmd) @@ timer.tagged("command", command.subcommand()).trackDuration
    } yield out)

  /**
   * Exit 0 returns the output unparsed, because `query` prints text on success. Exit 137 and 139 are classified
   * before stdout is parsed: a killed or crashed sipi writes no report.
   */
  private def runWithJsonReport(command: SipiCommand, cmd: Command): IO[IOException, ProcessOutput] =
    executor.execute(cmd).flatMap { out =>
      if (out.exitCode == 0) ZIO.logInfo(s"Sipi ${command.subcommand()}: status=ok").as(out)
      else
        CorrelationId.current.flatMap { id =>
          val error = sipiCliError(command, out, id.value)
          ZIO.logWarning(
            s"Sipi ${command.subcommand()}: status=error phase=${error.phase} error_code=${error.errorCode}",
          ) *> ZIO.fail(error)
        }
    }

  private def sipiCliError(command: SipiCommand, out: ProcessOutput, correlationId: String): SipiCliError = {
    def withoutReport(code: String, message: String) =
      SipiCliError(Phase.Internal, code, correlationId, command.target, message, Nil, None, out.exitCode)
    out.exitCode match {
      case 137 => withoutReport(SipiCliError.killed, "sipi was killed (exit 137)")
      case 139 => withoutReport(SipiCliError.crashed, "sipi crashed (exit 139)")
      case _   =>
        out.stdout.fromJson[SipiReport] match {
          case Right(err: SipiReport.Err) =>
            SipiCliError(
              phase = err.phase,
              errorCode = err.errorCode,
              correlationId = err.correlationId.getOrElse(correlationId),
              target = command.target,
              message = err.errorMessage,
              reportedPaths = err.inputFile.toList ++ err.outputFile.toList,
              imageMeta = err.image,
              exitCode = out.exitCode,
            )
          case _ => withoutReport(SipiCliError.noReport, s"sipi exited with ${out.exitCode} and wrote no report")
        }
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
