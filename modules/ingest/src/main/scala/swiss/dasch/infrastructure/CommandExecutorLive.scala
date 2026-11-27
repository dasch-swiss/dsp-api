/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import swiss.dasch.config.Configuration.SipiConfig
import swiss.dasch.domain.StorageService
import zio.IO
import zio.UIO
import zio.ZIO
import zio.ZLayer
import zio.json.*
import zio.json.ast.Json

import java.io.IOException
import scala.sys.process.Process
import scala.sys.process.ProcessLogger

final case class ProcessOutput(stdout: String, stderr: String, exitCode: Int)
final case class Command private[infrastructure] (cmd: List[String])

trait CommandExecutor {

  /**
   * Builds a command to execute.
   * The command is built from the given command and parameters.
   *
   * In local development mode, the command is executed in a docker container.
   * Otherwise, the command is executed directly, through a shell that first raises its `oom_score_adj` to 1000,
   * so the kernel OOM killer picks the child process before the embedding JVM.
   *
   * @param command the command to execute.
   * @param params  the parameters to pass to the command.
   * @return the command to execute.
   */
  def buildCommand(command: String, params: String*): UIO[Command]

  /**
   * Executes a command and returns the [[ProcessOutput]].
   *
   * @param command the [[Command]] to execute.
   * @return the [[ProcessOutput]] containing the standard output and standard error, and the exit code.
   */
  def execute(command: Command): IO[IOException, ProcessOutput]

  /**
   * Executes a command and returns the [[ProcessOutput]].
   * Fails if the command fails wih a non-zero exit code.
   *
   * @param command the command to execute.
   * @return the [[ProcessOutput]] containing the standard output and standard error, and the exit code.
   */
  final def executeOrFail(command: Command): IO[IOException, ProcessOutput] =
    execute(command).filterOrElseWith(_.exitCode == 0)(out =>
      ZIO.fail(new IOException(s"Command failed: '${command.cmd}' $out")),
    )

  /**
   * Parses Sipi's JSON logs and to prepare for reporting with ZIO's configured format.
   *
   * @param out All logs combined.
   * @return list item per input line
   */
  def parseSipiLogs(out: String): List[String] =
    out.linesIterator.toList.filter(_.replaceAll("\\s", "").nonEmpty).map { line =>
      val newLine = line
        .fromJson[Json.Obj]
        .fold(
          _ => "INFO: " ++ line.replaceFirst("\\s+", ""),
          o => {
            val getStr: String => Option[String] = s => o.toMap.get(s).flatMap(_.asString)
            getStr("level").getOrElse("INFO") ++ ": " ++ getStr("message").getOrElse(line)
          },
        )
        .replaceAll("[\"\\n]", ".")
      "Sipi: " ++ newLine
    }
}

object CommandExecutor {
  def buildCommand(command: String, params: String*): ZIO[CommandExecutor, Nothing, Command] =
    ZIO.serviceWithZIO[CommandExecutor](_.buildCommand(command, params*))
}

final case class CommandExecutorLive(
  sipiConfig: SipiConfig,
  storageService: StorageService,
  ingestExecutor: IngestExecutor,
) extends CommandExecutor {
  override def buildCommand(command: String, params: String*): UIO[Command] =
    if (sipiConfig.useLocalDev) {
      for {
        assetDir      <- storageService.getAssetsBaseFolder().flatMap(_.toAbsolutePath).orDie
        correlationId <- CorrelationId.current
      } yield {
        Command(
          List(
            List("docker", "run", "--entrypoint", command),
            List("-v", s"$assetDir:$assetDir"),
            List("-e", s"${CommandExecutorLive.correlationIdEnv}=${correlationId.value}"),
            List(sipiConfig.localDevImage),
            params,
          ).flatten,
        )
      }

    } else {
      ZIO.succeed(Command(CommandExecutorLive.oomExpendable ++ (command +: params.toList)))
    }

  private class InMemoryProcessLogger extends ProcessLogger {
    private val sbOut = new StringBuilder
    private val sbErr = new StringBuilder

    override def out(s: => String): Unit = sbOut.append(s + "\n")

    override def err(s: => String): Unit = sbErr.append(s + "\n")

    override def buffer[T](f: => T): T = f

    def buildOutput(exitCode: Int): ProcessOutput = ProcessOutput(sbOut.toString(), sbErr.toString(), exitCode)
  }

  override def execute(command: Command): IO[IOException, ProcessOutput] = {
    val logger = new InMemoryProcessLogger()
    for {
      correlationId <- CorrelationId.current
      process        = Process(command.cmd, None, CommandExecutorLive.correlationIdEnv -> correlationId.value)
      _             <- ZIO.logInfo(s"Executing command: ${command.cmd}")
      out           <- ingestExecutor.withPermit(ZIO.attemptBlockingIO(process !< logger)).map(logger.buildOutput)
      _             <- ZIO.foreachDiscard(parseSipiLogs(out.stderr))(ZIO.logInfo(_))
    } yield out
  }
}

object CommandExecutorLive {

  /** sipi prefixes its log lines and its report with this ID; allowed: `[A-Za-z0-9._:-]{1,128}`. */
  val correlationIdEnv: String = "SIPI_CORRELATION_ID"

  /** `sh -c` binds the arguments after the script to `$0`, `$@`, so `exec` replaces the shell with the command. */
  private[infrastructure] val oomExpendable: List[String] =
    List("/bin/sh", "-c", """echo 1000 > /proc/self/oom_score_adj && exec "$0" "$@"""")

  val layer = ZLayer.derive[CommandExecutorLive]
}
