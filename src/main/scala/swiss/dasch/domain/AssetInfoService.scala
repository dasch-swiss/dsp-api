/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto.autoUnwrap
import eu.timepit.refined.numeric.Positive
import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.domain.SupportedFileType.{MovingImage, Other, StillImage}
import zio.json.interop.refined.{decodeRefined, encodeRefined}
import zio.json.{DeriveJsonCodec, JsonCodec}
import zio.nio.file.{Files, Path}
import zio.stream.ZStream
import zio.{IO, Task, UIO, ZIO, ZLayer}

import java.io.FileNotFoundException

type PositiveInt    = Int Refined Positive
type PositiveDouble = Double Refined Positive

final private case class AssetInfoFileContent(
  internalFilename: NonEmptyString,
  originalInternalFilename: NonEmptyString,
  originalFilename: NonEmptyString,
  checksumOriginal: Sha256Hash,
  checksumDerivative: Sha256Hash,
  width: Option[PositiveInt] = None,
  height: Option[PositiveInt] = None,
  duration: Option[PositiveDouble] = None,
  fps: Option[PositiveDouble] = None,
  internalMimeType: Option[NonEmptyString] = None,
  originalMimeType: Option[NonEmptyString] = None
) {
  def withDerivativeChecksum(checksum: Sha256Hash): AssetInfoFileContent = copy(checksumDerivative = checksum)
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
      dim.map(_.width),
      dim.map(_.height),
      metadata.durationOpt,
      metadata.fpsOpt,
      metadata.internalMimeType.map(_.value),
      metadata.originalMimeType.map(_.value)
    )
  }

  given codec: JsonCodec[AssetInfoFileContent] = DeriveJsonCodec.gen[AssetInfoFileContent]
}

final case class FileAndChecksum(file: Path, checksum: Sha256Hash) {
  lazy val filename: NonEmptyString = NonEmptyString.unsafeFrom(file.filename.toString)
}

final case class AssetInfo(
  assetRef: AssetRef,
  original: FileAndChecksum,
  originalFilename: NonEmptyString,
  derivative: FileAndChecksum,
  metadata: AssetMetadata
)

trait AssetInfoService {
  def loadFromFilesystem(infoFile: Path, shortcode: ProjectShortcode): Task[AssetInfo]
  def getInfoFilePath(asset: AssetRef): UIO[Path]
  def findByAssetRef(asset: AssetRef): Task[Option[AssetInfo]]
  def save(assetInfo: AssetInfo): Task[Unit]
  def findAllInPath(path: Path, shortcode: ProjectShortcode): ZStream[Any, Throwable, AssetInfo]
  def updateAssetInfoForDerivative(derivative: Path): Task[Unit]
  def createAssetInfo(asset: Asset): IO[FileNotFoundException, AssetInfo]
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
  def createAssetInfo(asset: Asset): ZIO[AssetInfoService, FileNotFoundException, AssetInfo] =
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
    storage.getAssetDirectory(asset).map(_ / infoFilename(asset))

  private def infoFilename(asset: AssetRef): String = infoFilename(asset.id)
  private def infoFilename(id: AssetId): String     = s"$id.info"

  private def parseAssetInfoFile(asset: AssetRef, infoFile: Path): Task[AssetInfo] =
    storage.loadJsonFile[AssetInfoFileContent](infoFile).map(toAssetInfo(_, infoFile.parent.orNull, asset))

  private def toAssetInfo(
    raw: AssetInfoFileContent,
    infoFileDirectory: Path,
    asset: AssetRef
  ): AssetInfo = {
    val typ              = SupportedFileType.fromPath(Path(raw.originalFilename.value)).getOrElse(Other)
    val dim              = raw.width.flatMap(w => raw.height.flatMap(h => Dimensions.from(w, h).toOption))
    val internalMimeType = raw.internalMimeType.flatMap(it => MimeType.from(it.value).toOption)
    val originalMimeType = raw.originalMimeType.flatMap(it => MimeType.from(it.value).toOption)
    val metadata = typ match {
      case StillImage if dim.isDefined => StillImageMetadata(dim.get, internalMimeType, originalMimeType)
      case MovingImage if dim.isDefined && raw.duration.exists(_ > 0) && raw.fps.exists(_ > 0) => {
        val fps      = Fps.unsafeFrom(raw.fps.get)
        val duration = DurationSecs.unsafeFrom(raw.duration.get)
        MovingImageMetadata(dim.get, duration, fps, internalMimeType, originalMimeType)
      }
      case _ => OtherMetadata(internalMimeType, originalMimeType)
    }
    AssetInfo(
      assetRef = asset,
      original = FileAndChecksum(infoFileDirectory / raw.originalInternalFilename.toString, raw.checksumOriginal),
      originalFilename = raw.originalFilename,
      derivative = FileAndChecksum(infoFileDirectory / raw.internalFilename.toString, raw.checksumDerivative),
      metadata = metadata
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
    _           <- storage.saveJsonFile(infoFile, content.withDerivativeChecksum(newChecksum))
  } yield ()

  override def createAssetInfo(asset: Asset): IO[FileNotFoundException, AssetInfo] = for {
    checksumOriginal   <- FileChecksumService.createSha256Hash(asset.original.file.toPath)
    original            = FileAndChecksum(asset.original.file.toPath, checksumOriginal)
    checksumDerivative <- FileChecksumService.createSha256Hash(asset.derivative.toPath)
    derivative          = FileAndChecksum(asset.derivative.toPath, checksumDerivative)
  } yield AssetInfo(asset.ref, original, asset.original.originalFilename, derivative, asset.metadata)
}

object AssetInfoServiceLive {
  val layer = ZLayer.derive[AssetInfoServiceLive]
}
