/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import org.apache.commons.io.FilenameUtils
import swiss.dasch.domain.AugmentedPath.Conversions.given_Conversion_AugmentedPath_Path
import swiss.dasch.domain.AugmentedPath.MovingImageDerivativeFile
import swiss.dasch.infrastructure.CommandExecutor
import zio.json.{DecoderOps, DeriveJsonDecoder, JsonDecoder}
import zio.nio.file.Path
import zio.{Task, ZIO, ZLayer}

case class MovingImageService(storage: StorageService, executor: CommandExecutor, mimeTypeGuesser: MimeTypeGuesser) {

  def createDerivative(original: Original, assetRef: AssetRef): Task[MovingImageDerivativeFile] =
    for {
      fileExtension <- ensureSupportedFileType(original.originalFilename.toString)
      assetDir      <- storage.getAssetFolder(assetRef)
      derivative     = MovingImageDerivativeFile.unsafeFrom(assetDir / s"${assetRef.id}.$fileExtension")
      _             <- storage.copyFile(original.file, derivative)
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
      absPath <- file.toAbsolutePath
      cmd     <- executor.buildCommand("/sipi/scripts/export-moving-image-frames.sh", "-i", absPath.toString)
      _       <- executor.executeOrFail(cmd)
    } yield ()

  def extractMetadata(original: Original, derivative: MovingImageDerivativeFile): Task[MovingImageMetadata] = for {
    _                          <- ZIO.when(original.assetId != derivative.assetId)(ZIO.die(new Exception("Asset IDs do not match")))
    _                          <- ZIO.logInfo(s"Extracting metadata for ${derivative.assetId}")
    ffprobeInfo                <- extractWithFfprobe(derivative)
    (dimensions, duration, fps) = ffprobeInfo
    derivativeMimeType          = mimeTypeGuesser.guess(derivative.path)
    originalMimeType            = mimeTypeGuesser.guess(original.originalFilename)
  } yield MovingImageMetadata(dimensions, duration, fps, derivativeMimeType, originalMimeType)

  private def extractWithFfprobe(derivative: MovingImageDerivativeFile) = for {
    absPath <- derivative.path.toAbsolutePath
    cmd <-
      executor.buildCommand(
        "ffprobe",
        s"-v",
        "error",
        "-select_streams",
        "v:0",
        "-show_entries",
        "stream=width,height,duration,r_frame_rate",
        "-print_format",
        "json",
        "-i",
        absPath.toString,
      )
    outStr <- executor.executeOrFail(cmd).map(_.stdout)
    ffprobeOut <- ZIO
                    .succeed(outStr.fromJson[FfprobeOut].toOption.flatMap(extractDimDurFps))
                    .someOrFail(new RuntimeException(s"Failed parsing metadata: $outStr"))
  } yield ffprobeOut

  private def extractDimDurFps(ffprobeOut: FfprobeOut): Option[(Dimensions, DurationSecs, Fps)] =
    // Convert the the first present stream stream to [[MovingImageMetadata]].
    // Assumes only a single stream and ignore other streams if present.
    ffprobeOut.streams.headOption.flatMap { stream =>
      // The frame rate is given as a fraction, e.g. 30000/1001
      // See ffprobe documentation https://ffmpeg.org/doxygen/1.0/structAVStream.html#d63fb11cc1415e278e09ddc676e8a1ad
      // Convert this fraction to a double value.
      val fpsFraction = stream.r_frame_rate.split("/")
      if (fpsFraction.length != 2) {
        None
      } else {
        for {
          dim         <- Dimensions.from(stream.width, stream.height).toOption
          duration    <- DurationSecs.from(stream.duration).toOption
          numerator   <- fpsFraction(0).trim.toDoubleOption
          denominator <- fpsFraction(1).trim.toDoubleOption.filter(_ != 0)
          fps         <- Fps.from(numerator / denominator).toOption
        } yield (dim, duration, fps)
      }
    }

  final case class FfprobeStream(width: Int, height: Int, duration: Double, r_frame_rate: String)
  object FfprobeStream {
    implicit val decoder: JsonDecoder[FfprobeStream] = DeriveJsonDecoder.gen[FfprobeStream]
  }

  final case class FfprobeOut(streams: Array[FfprobeStream]) {}
  object FfprobeOut {
    implicit val decoder: JsonDecoder[FfprobeOut] = DeriveJsonDecoder.gen[FfprobeOut]
  }

}

object MovingImageService {
  def createDerivative(
    original: Original,
    assetRef: AssetRef,
  ): ZIO[MovingImageService, Throwable, MovingImageDerivativeFile] =
    ZIO.serviceWithZIO(_.createDerivative(original, assetRef))

  def extractKeyFrames(file: MovingImageDerivativeFile, assetRef: AssetRef): ZIO[MovingImageService, Throwable, Unit] =
    ZIO.serviceWithZIO(_.extractKeyFrames(file, assetRef))

  def extractMetadata(
    original: Original,
    file: MovingImageDerivativeFile,
  ): ZIO[MovingImageService, Throwable, MovingImageMetadata] =
    ZIO.serviceWithZIO[MovingImageService](_.extractMetadata(original, file))

  val layer = ZLayer.derive[MovingImageService]
}
