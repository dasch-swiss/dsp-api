/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.domain.SipiImageFormat.Jpx
import zio.*
import zio.nio.file.{ Files, Path }

import java.io.IOException

trait ImageService {

  /** Apply top left correction to the image if needed.
    *
    * Creates a backup of the original image "${image.filename}.bak" in the same directory.
    *
    * Updates the asset info for the derivative.
    *
    * @param image
    *   the image to apply the correction to
    * @return
    *   the path to the corrected image or None if no correction was needed
    */
  def applyTopLeftCorrection(image: Path): Task[Option[Path]]

  def needsTopLeftCorrection(image: Path): IO[IOException, Boolean]

  def createDerivative(original: OriginalFile): Task[JpxDerivativeFile]
}

object ImageService {
  def applyTopLeftCorrection(image: Path): ZIO[ImageService, Throwable, Option[Path]]           =
    ZIO.serviceWithZIO[ImageService](_.applyTopLeftCorrection(image))
  def needsTopLeftCorrection(image: Path): ZIO[ImageService, IOException, Boolean]              =
    ZIO.serviceWithZIO[ImageService](_.needsTopLeftCorrection(image))
  def createDerivative(original: OriginalFile): ZIO[ImageService, Throwable, JpxDerivativeFile] =
    ZIO.serviceWithZIO[ImageService](_.createDerivative(original))
}

final case class ImageServiceLive(sipiClient: SipiClient, assetInfos: AssetInfoService) extends ImageService {

  override def applyTopLeftCorrection(image: Path): Task[Option[Path]] =
    ZIO.whenZIO(needsTopLeftCorrection(image))(
      ZIO.logInfo(s"Applying top left correction to $image") *>
        Files.copy(image, image.parent.map(_ / s"${image.filename}.bak").orNull) *>
        sipiClient.applyTopLeftCorrection(image, image) *>
        assetInfos.updateAssetInfoForDerivative(image).as(image)
    )

  override def needsTopLeftCorrection(image: Path): IO[IOException, Boolean] =
    FileFilters.isImage(image) &&
    sipiClient
      .queryImageFile(image)
      .map(_.stdOut.split('\n'))
      .map { lines =>
        // check if the image has an orientation tag and if it is not horizontal
        lines
          .filter(_.startsWith(Exif.Image.Orientation))
          .exists(_.lastOption.exists(_ != Exif.Image.OrientationValue.Horizontal.value))
      }

  override def createDerivative(original: OriginalFile): Task[JpxDerivativeFile] = {
    val imagePath      = original.toPath
    val derivativePath = imagePath.parent.head / s"${original.assetId}.${Jpx.extension}"
    ZIO.logInfo(s"Creating derivative for $imagePath") *>
      sipiClient.transcodeImageFile(imagePath, derivativePath, Jpx) *>
      ZIO
        .fail(new IOException(s"Sipi failed creating derivative for $imagePath"))
        .whenZIO(Files.notExists(derivativePath))
        .as(JpxDerivativeFile.unsafeFrom(derivativePath))
  }
}

object ImageServiceLive {
  val layer = ZLayer.fromFunction(ImageServiceLive.apply _)
}
