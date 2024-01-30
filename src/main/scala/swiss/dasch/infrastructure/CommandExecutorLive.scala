/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import swiss.dasch.config.Configuration.SipiConfig
import swiss.dasch.domain.StorageService
import swiss.dasch.version.BuildInfo
import zio.{IO, UIO, ZIO, ZLayer}

import java.io.IOException
import scala.sys.process.{ProcessLogger, stringToProcess}

final case class ProcessOutput(stdout: String, stderr: String, exitCode: Int)
final case class Command private[infrastructure] (cmd: String)

trait CommandExecutor {

  /**
   * Builds a command to execute.
   * The command is built from the given command and parameters.
   *
   * In local development mode, the command is executed in a docker container.
   * Otherwise, the command is executed directly.
   *
   * @param command the command to execute.
   * @param params  the parameters to pass to the command.
   * @return the command to execute.
   */
  def buildCommand(command: String, params: String): UIO[Command]

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
      ZIO.fail(new IOException(s"Command failed: '${command.cmd}' $out"))
    )
}

object CommandExecutor {
  def buildCommand(command: String, params: String): ZIO[CommandExecutor, Nothing, Command] =
    ZIO.serviceWithZIO[CommandExecutor](_.buildCommand(command, params))
}

final case class CommandExecutorLive(sipiConfig: SipiConfig, storageService: StorageService) extends CommandExecutor {

  override def buildCommand(command: String, params: String): UIO[Command] =
    if (sipiConfig.useLocalDev) {
      for {
        assetDir <- storageService.getAssetsBaseFolder().flatMap(_.toAbsolutePath).orDie
      } yield Command(
        s"docker run --entrypoint $command -v $assetDir:$assetDir daschswiss/knora-sipi:${BuildInfo.sipiVersion} $params"
      )
    } else {
      ZIO.succeed(Command(s"$command $params"))
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
      _   <- ZIO.logInfo(s"Executing command: ${command.cmd}")
      out <- ZIO.attemptBlockingIO(command.cmd !< logger).map(logger.buildOutput)
      _   <- ZIO.logWarning(s"Command '${command.cmd}' has err output: '$out'").when(out.stderr.nonEmpty)
    } yield out
  }
}

object CommandExecutorLive {
  val layer = ZLayer.derive[CommandExecutorLive]
}
