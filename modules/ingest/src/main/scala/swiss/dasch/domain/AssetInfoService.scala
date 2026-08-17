/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto.autoUnwrap
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.domain.AugmentedPath.Conversions.given
import swiss.dasch.domain.SupportedFileType.MovingImage
import swiss.dasch.domain.SupportedFileType.OtherFiles
import swiss.dasch.domain.SupportedFileType.StillImage
import zio.IO
import zio.Task
import zio.UIO
import zio.ZIO
import zio.ZLayer
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec
import zio.json.interop.refined.decodeRefined
import zio.json.interop.refined.encodeRefined
import zio.nio.file.Files
import zio.nio.file.Path
import zio.stream.ZStream

import java.io.IOException
import scala.language.implicitConversions

type PositiveInt    = Int Refined Positive
type PositiveDouble = Double Refined Positive

final private case class AssetInfoFileContent(
  internalFilename: NonEmptyString,
  originalInternalFilename: NonEmptyString,
  originalFilename: NonEmptyString,
  checksumOriginal: Sha256Hash,
  checksumDerivative: Sha256Hash,
  sizeOriginal: Option[SizeInBytes] = None,
  sizeDerivative: Option[SizeInBytes] = None,
  width: Option[PositiveInt] = None,
  height: Option[PositiveInt] = None,
  duration: Option[PositiveDouble] = None,
  fps: Option[PositiveDouble] = None,
  internalMimeType: Option[NonEmptyString] = None,
  originalMimeType: Option[NonEmptyString] = None,
) {
  def withDerivative(checksum: Sha256Hash, size: SizeInBytes): AssetInfoFileContent =
    copy(checksumDerivative = checksum, sizeDerivative = Some(size))
}

private object AssetInfoFileContent {
  def from(assetInfo: AssetInfo): AssetInfoFileContent = {
    val metadata = assetInfo.metadata
    val dim      = metadata.dimensionsOpt
    AssetInfoFileContent(
      assetInfo.derivative.filename,
      assetInfo.original.filename,
      assetInfo.originalFilename,
      assetInfo.original.checksum,
      assetInfo.derivative.checksum,
      assetInfo.original.size,
      assetInfo.derivative.size,
      dim.map(_.width),
      dim.map(_.height),
      metadata.durationOpt,
      metadata.fpsOpt,
      metadata.internalMimeType.map(_.value),
      metadata.originalMimeType.map(_.value),
    )
  }

  given codec: JsonCodec[AssetInfoFileContent] = DeriveJsonCodec.gen[AssetInfoFileContent]
}

final case class FileAndChecksum(file: Path, checksum: Sha256Hash, size: Option[SizeInBytes] = None) {
  lazy val filename: NonEmptyString = NonEmptyString.unsafeFrom(file.filename.toString)
}

final case class AssetInfo(
  assetRef: AssetRef,
  original: FileAndChecksum,
  originalFilename: NonEmptyString,
  derivative: FileAndChecksum,
  metadata: AssetMetadata,
)

trait AssetInfoService {
  def loadFromFilesystem(infoFile: Path, shortcode: ProjectShortcode): Task[AssetInfo]
  def getInfoFilePath(asset: AssetRef): UIO[Path]
  def findByAssetRef(asset: AssetRef): Task[Option[AssetInfo]]
  def save(assetInfo: AssetInfo): Task[Unit]
  def findAllInPath(path: Path, shortcode: ProjectShortcode): ZStream[Any, Throwable, AssetInfo]
  def updateAssetInfoForDerivative(derivative: Path): Task[Unit]
  def createAssetInfo(asset: Asset): IO[IOException, AssetInfo]
}

object AssetInfoService {
  def findByAssetRef(asset: AssetRef): ZIO[AssetInfoService, Throwable, Option[AssetInfo]] =
    ZIO.serviceWithZIO[AssetInfoService](_.findByAssetRef(asset))
  def loadFromFilesystem(infoFile: Path, shortcode: ProjectShortcode): ZIO[AssetInfoService, Throwable, AssetInfo] =
    ZIO.serviceWithZIO[AssetInfoService](_.loadFromFilesystem(infoFile, shortcode))
  def updateAssetInfoForDerivative(derivative: Path): ZIO[AssetInfoService, Throwable, Unit] =
    ZIO.serviceWithZIO[AssetInfoService](_.updateAssetInfoForDerivative(derivative))
  def getInfoFilePath(asset: AssetRef): ZIO[AssetInfoService, Nothing, Path] =
    ZIO.serviceWithZIO[AssetInfoService](_.getInfoFilePath(asset))
  def createAssetInfo(asset: Asset): ZIO[AssetInfoService, IOException, AssetInfo] =
    ZIO.serviceWithZIO[AssetInfoService](_.createAssetInfo(asset))
}

final case class AssetInfoServiceLive(storage: StorageService) extends AssetInfoService {
  override def loadFromFilesystem(infoFile: Path, shortcode: ProjectShortcode): Task[AssetInfo] =
    for {
      content   <- storage.loadJsonFile[AssetInfoFileContent](infoFile)
      assetMaybe = AssetId.fromPath(Path(content.internalFilename.toString)).map(id => AssetRef(id, shortcode))
      assetInfo <- assetMaybe match {
                     case Some(asset) => ZIO.succeed(toAssetInfo(content, infoFile.parent.orNull, asset))
                     case None        => ZIO.fail(IllegalArgumentException(s"Unable to parse asset id from $infoFile"))
                   }
    } yield assetInfo

  override def findByAssetRef(asset: AssetRef): Task[Option[AssetInfo]] =
    for {
      infoFile <- getInfoFilePath(asset)
      info     <- ZIO.whenZIO(storage.fileExists(infoFile))(parseAssetInfoFile(asset, infoFile))
    } yield info

  override def save(assetInfo: AssetInfo): Task[Unit] =
    getInfoFilePath(assetInfo.assetRef).flatMap(storage.saveJsonFile(_, AssetInfoFileContent.from(assetInfo)))

  def getInfoFilePath(asset: AssetRef): UIO[Path] =
    storage.getAssetFolder(asset).map(_ / infoFilename(asset))

  private def infoFilename(asset: AssetRef): String = infoFilename(asset.id)
  private def infoFilename(id: AssetId): String     = s"$id.info"

  private def parseAssetInfoFile(asset: AssetRef, infoFile: Path): Task[AssetInfo] =
    storage.loadJsonFile[AssetInfoFileContent](infoFile).map(toAssetInfo(_, infoFile.parent.orNull, asset))

  private def toAssetInfo(
    raw: AssetInfoFileContent,
    infoFileDirectory: Path,
    asset: AssetRef,
  ): AssetInfo = {
    val typ              = SupportedFileType.fromPath(Path(raw.originalFilename.value)).getOrElse(OtherFiles)
    val dim              = raw.width.flatMap(w => raw.height.flatMap(h => Dimensions.from(w, h).toOption))
    val internalMimeType = raw.internalMimeType.flatMap(it => MimeType.from(it.value).toOption)
    val originalMimeType = raw.originalMimeType.flatMap(it => MimeType.from(it.value).toOption)
    val metadata         = typ match {
      case StillImage if dim.isDefined                                                         => StillImageMetadata(dim.get, internalMimeType, originalMimeType)
      case MovingImage if dim.isDefined && raw.duration.exists(_ > 0) && raw.fps.exists(_ > 0) => {
        val fps      = Fps.unsafeFrom(raw.fps.get)
        val duration = DurationSecs.unsafeFrom(raw.duration.get)
        MovingImageMetadata(dim.get, duration, fps, internalMimeType, originalMimeType)
      }
      case _ => OtherMetadata(internalMimeType, originalMimeType)
    }
    AssetInfo(
      assetRef = asset,
      original = FileAndChecksum(
        infoFileDirectory / raw.originalInternalFilename.toString,
        raw.checksumOriginal,
        raw.sizeOriginal,
      ),
      originalFilename = raw.originalFilename,
      derivative =
        FileAndChecksum(infoFileDirectory / raw.internalFilename.toString, raw.checksumDerivative, raw.sizeDerivative),
      metadata = metadata,
    )
  }

  override def findAllInPath(path: Path, shortcode: ProjectShortcode): ZStream[Any, Throwable, AssetInfo] =
    StorageService
      .findInPath(path, FileFilters.hasFileExtension("info"))
      .mapZIOPar(StorageService.maxParallelism())(loadFromFilesystem(_, shortcode))

  override def updateAssetInfoForDerivative(derivative: Path): Task[Unit] = for {
    assetId <- ZIO
                 .fromOption(AssetId.fromPath(derivative))
                 .orElseFail(IllegalArgumentException(s"Unable to parse asset id from $derivative"))
    infoFile = derivative.parent.map(_ / infoFilename(assetId)).orNull
    _       <- ZIO.whenZIO(Files.exists(infoFile))(updateDerivativeChecksum(infoFile, derivative))
  } yield ()

  private def updateDerivativeChecksum(infoFile: Path, derivative: Path) = for {
    content     <- storage.loadJsonFile[AssetInfoFileContent](infoFile)
    newChecksum <- FileChecksumService.createSha256Hash(derivative)
    newSize     <- SizeInBytes.of(derivative)
    _           <- storage.saveJsonFile(infoFile, content.withDerivative(newChecksum, newSize))
  } yield ()

  override def createAssetInfo(asset: Asset): IO[IOException, AssetInfo] = for {
    checksumOriginal   <- FileChecksumService.createSha256Hash(asset.original.file)
    sizeOriginal       <- SizeInBytes.of(asset.original.file)
    original            = FileAndChecksum(asset.original.file, checksumOriginal, Some(sizeOriginal))
    checksumDerivative <- FileChecksumService.createSha256Hash(asset.derivative)
    sizeDerivative     <- SizeInBytes.of(asset.derivative)
    derivative          = FileAndChecksum(asset.derivative, checksumDerivative, Some(sizeDerivative))
  } yield AssetInfo(asset.ref, original, asset.original.originalFilename, derivative, asset.metadata)
}

object AssetInfoServiceLive {
  val layer = ZLayer.derive[AssetInfoServiceLive]
}
