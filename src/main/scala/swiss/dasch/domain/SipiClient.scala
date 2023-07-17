/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.config.Configuration.{ SipiConfig, StorageConfig }
import zio.*
import zio.nio.file.Path

import java.io.IOError
import scala.sys.process.{ ProcessLogger, stringToProcess }

/** Defines the commands that can be executed with Sipi.
  *
  * See https://sipi.io/running/#command-line-options
  */
private trait SipiCommandLine      {
  def help(): UIO[String]                                    = ZIO.succeed("--help")
  def compare(file1: Path, file2: Path): IO[IOError, String] = for {
    abs1 <- file1.toAbsolutePath
    abs2 <- file2.toAbsolutePath
  } yield s"--compare $abs1 $abs2"

  def format(
      outputFormat: String,
      fileIn: Path,
      fileOut: Path,
    ): Task[String] = for {
    abs1 <- fileIn.toAbsolutePath
    abs2 <- fileOut.toAbsolutePath
  } yield s"--format $outputFormat $abs1 $abs2"
}
private object SipiCommandLineLive {
  private val sipiExecutable                          = "/sipi/sipi"
  def help(): ZIO[SipiCommandLine, Throwable, String] = ZIO.serviceWithZIO[SipiCommandLine](_.help())

  val layer: URLayer[SipiConfig with StorageConfig, SipiCommandLine] = ZLayer.fromZIO {
    for {
      config            <- ZIO.service[SipiConfig]
      absoluteAssetPath <- ZIO.serviceWithZIO[StorageConfig](_.assetPath.toAbsolutePath).orDie
      prefix             = if (config.useLocalDev) {
                             s"docker run " +
                               s"--entrypoint $sipiExecutable " +
                               s"-v $absoluteAssetPath:$absoluteAssetPath " +
                               s"daschswiss/knora-sipi:latest"
                           }
                           else { sipiExecutable }
    } yield SipiCommandLineLive(prefix)
  }
}

final private case class SipiCommandLineLive(prefix: String) extends SipiCommandLine {
  private def addPrefix[E](cmd: IO[E, String]): IO[E, String]         = cmd.map(cmdStr => s"$prefix $cmdStr")
  override def help(): UIO[String]                                    = addPrefix(super.help())
  override def compare(file1: Path, file2: Path): IO[IOError, String] = addPrefix(super.compare(file1, file2))
  override def format(
      outputFormat: String,
      fileIn: Path,
      fileOut: Path,
    ): Task[String] = addPrefix(super.format(outputFormat, fileIn, fileOut))
}

/** Defines the output format of the image. Used with the `--format` option.
  *
  * https://sipi.io/running/#command-line-options
  */
sealed trait SipiImageFormat {
  def toCliString: String
  def extension: String = toCliString
}
case object Jpx extends SipiImageFormat {
  override def toCliString: String = "jpx"
}
case object Jpg extends SipiImageFormat {
  override def toCliString: String = "jpg"
}
case object Tif extends SipiImageFormat {
  override def toCliString: String = "tif"
}
case object Png extends SipiImageFormat {
  override def toCliString: String = "png"
}

final case class SipiOutput(stdOut: String, stdErr: String)
trait SipiClient  {
  def transcodeImageFile(
      fileIn: Path,
      fileOut: Path,
      outputFormat: SipiImageFormat,
    ): Task[SipiOutput]
}
object SipiClient {
  def transcodeImageFile(
      fileIn: Path,
      fileOut: Path,
      outputFormat: SipiImageFormat,
    ): ZIO[SipiClient, Throwable, SipiOutput] =
    ZIO.serviceWithZIO[SipiClient](_.transcodeImageFile(fileIn, fileOut, outputFormat))
}

final case class SipiClientLive(cmd: SipiCommandLine) extends SipiClient    {
  private def execute(commandLineTask: Task[String]): Task[SipiOutput] =
    commandLineTask.flatMap { cmd =>
      val logger = new InMemoryProcessLogger
      ZIO.logDebug(s"Calling \n$cmd") *>
        ZIO.attemptBlocking(cmd ! logger).as(logger.getOutput).tap(out => ZIO.logInfo(out.toString))
    }.logError

  override def transcodeImageFile(
      fileIn: Path,
      fileOut: Path,
      outputFormat: SipiImageFormat,
    ): Task[SipiOutput] =
    execute(cmd.format(outputFormat.toCliString, fileIn, fileOut))
}
final private class InMemoryProcessLogger             extends ProcessLogger {
  private val sbOut                    = new StringBuilder
  private val sbErr                    = new StringBuilder
  override def out(s: => String): Unit = sbOut.append(s)
  override def err(s: => String): Unit = sbErr.append(s)
  override def buffer[T](f: => T): T   = f
  def getOutput: SipiOutput            = SipiOutput(sbOut.toString(), sbErr.toString())
}

object SipiClientLive {
  val layer: ZLayer[SipiConfig with StorageConfig, Nothing, SipiClient] =
    SipiCommandLineLive.layer >>> ZLayer.fromFunction(SipiClientLive.apply _)
}
