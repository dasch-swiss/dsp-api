/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import org.apache.commons.io.FilenameUtils
import swiss.dasch.domain.DerivativeFile.MovingImageDerivativeFile
import swiss.dasch.infrastructure.CommandExecutor
import zio.json.{DecoderOps, DeriveJsonDecoder, JsonDecoder}
import zio.nio.file.Path
import zio.{Task, ZIO, ZLayer}

final case class MovingImageMetadata(width: Int, height: Int, duration: Double, fps: Double)

case class MovingImageService(storage: StorageService, executor: CommandExecutor) {

  def createDerivative(original: Original, assetRef: AssetRef): Task[MovingImageDerivativeFile] =
    for {
      fileExtension <- ensureSupportedFileType(original.originalFilename.toString)
      assetDir      <- storage.getAssetDirectory(assetRef)
      derivativePath = assetDir / s"${assetRef.id}.$fileExtension"
      derivative     = MovingImageDerivativeFile.unsafeFrom(derivativePath)
      _             <- storage.copyFile(original.file.toPath, derivativePath).as(Asset.makeOther(assetRef, original, derivative))
    } yield derivative

  private def ensureSupportedFileType(file: Path | String) = {
    val fileExtension = file match {
      case path: Path  => FilenameUtils.getExtension(path.toString)
      case str: String => FilenameUtils.getExtension(str)
    }
    ZIO
      .die(new IllegalArgumentException(s"File extension $fileExtension is not supported for moving images"))
      .unless(SupportedFileType.MovingImage.acceptsExtension(fileExtension))
      .as(fileExtension)
  }

  def extractKeyFrames(file: MovingImageDerivativeFile, assetRef: AssetRef): Task[Unit] =
    for {
      _       <- ZIO.logInfo(s"Extracting key frames for $file, $assetRef")
      absPath <- file.toPath.toAbsolutePath
      cmd     <- executor.buildCommand("/sipi/scripts/export-moving-image-frames.sh", s"-i $absPath")
      _       <- executor.executeOrFail(cmd)
    } yield ()

  def extractMetadata(file: MovingImageDerivativeFile, assetRef: AssetRef): Task[MovingImageMetadata] =
    for {
      _       <- ZIO.logInfo(s"Extracting metadata for $file, $assetRef")
      absPath <- file.toPath.toAbsolutePath
      cmd <-
        executor.buildCommand(
          "ffprobe",
          s"-v error -select_streams v:0 -show_entries stream=width,height,duration,r_frame_rate -print_format json -i $absPath"
        )
      metadata <- executor.executeOrFail(cmd).flatMap(out => parseMetadata(out.stdout))
    } yield metadata

  final case class FfprobeStream(width: Int, height: Int, duration: Double, r_frame_rate: String)
  object FfprobeStream {
    implicit val decoder: JsonDecoder[FfprobeStream] = DeriveJsonDecoder.gen[FfprobeStream]
  }

  final case class FfprobeOut(streams: Array[FfprobeStream]) {
    def toMetadata: Option[MovingImageMetadata] =
      // Convert the the first present stream stream to [[MovingImageMetadata]].
      // Assumes only a single stream and ignore other streams if present.
      streams.headOption.flatMap { stream =>
        // The frame rate is given as a fraction, e.g. 30000/1001
        // See ffprobe documentation https://ffmpeg.org/doxygen/1.0/structAVStream.html#d63fb11cc1415e278e09ddc676e8a1ad
        // Convert this fraction to a double value.
        val fpsFraction = stream.r_frame_rate.split("/")
        if (fpsFraction.length == 2) {
          for {
            numerator   <- fpsFraction(0).trim.toDoubleOption
            denominator <- fpsFraction(1).trim.toDoubleOption.filter(_ != 0)
            fps          = numerator / denominator
          } yield MovingImageMetadata(stream.width, stream.height, stream.duration, fps)
        } else { None }
      }
  }
  object FfprobeOut {
    implicit val decoder: JsonDecoder[FfprobeOut] = DeriveJsonDecoder.gen[FfprobeOut]
  }

  private def parseMetadata(ffprobeJson: String) =
    ZIO
      .fromOption(ffprobeJson.fromJson[FfprobeOut].toOption.flatMap(_.toMetadata))
      .orElseFail(new RuntimeException(s"Failed parsing metadata: $ffprobeJson"))
}

object MovingImageService {
  def createDerivative(
    original: Original,
    assetRef: AssetRef
  ): ZIO[MovingImageService, Throwable, MovingImageDerivativeFile] =
    ZIO.serviceWithZIO(_.createDerivative(original, assetRef))

  def extractKeyFrames(file: MovingImageDerivativeFile, assetRef: AssetRef): ZIO[MovingImageService, Throwable, Unit] =
    ZIO.serviceWithZIO(_.extractKeyFrames(file, assetRef))

  def extractMetadata(
    file: MovingImageDerivativeFile,
    assetRef: AssetRef
  ): ZIO[MovingImageService, Throwable, MovingImageMetadata] =
    ZIO.serviceWithZIO[MovingImageService](_.extractMetadata(file, assetRef))

  val layer = ZLayer.derive[MovingImageService]
}
