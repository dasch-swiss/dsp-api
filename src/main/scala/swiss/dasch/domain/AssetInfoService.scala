/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.domain.Asset.MovingImageAsset
import zio.json.interop.refined.{decodeRefined, encodeRefined}
import zio.json.{DeriveJsonCodec, JsonCodec}
import zio.nio.file.{Files, Path}
import zio.stream.ZStream
import zio.{Task, UIO, ZIO, ZLayer}

final private case class AssetInfoFileContent(
  internalFilename: NonEmptyString,
  originalInternalFilename: NonEmptyString,
  originalFilename: NonEmptyString,
  checksumOriginal: Sha256Hash,
  checksumDerivative: Sha256Hash,
  width: Option[Int] = None,
  height: Option[Int] = None,
  duration: Option[Double] = None,
  fps: Option[Double] = None
) {
  def withDerivativeChecksum(checksum: Sha256Hash): AssetInfoFileContent = copy(checksumDerivative = checksum)
}
private object AssetInfoFileContent {
  def make(
    asset: Asset,
    originalChecksum: Sha256Hash,
    derivativeChecksum: Sha256Hash,
    metadata: Option[MovingImageMetadata]
  ): AssetInfoFileContent =
    AssetInfoFileContent(
      asset.derivative.filename,
      asset.original.internalFilename,
      asset.original.originalFilename,
      originalChecksum,
      derivativeChecksum,
      metadata.map(_.width),
      metadata.map(_.height),
      metadata.map(_.duration),
      metadata.map(_.fps)
    )

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
  movingImageMetadata: Option[MovingImageMetadata] = None
)

trait AssetInfoService {
  def loadFromFilesystem(infoFile: Path, shortcode: ProjectShortcode): Task[AssetInfo]
  def getInfoFilePath(asset: AssetRef): UIO[Path]
  def findByAssetRef(asset: AssetRef): Task[AssetInfo]
  def findAllInPath(path: Path, shortcode: ProjectShortcode): ZStream[Any, Throwable, AssetInfo]
  def updateAssetInfoForDerivative(derivative: Path): Task[Unit]
  def createAssetInfo(asset: Asset): Task[Unit]
}
object AssetInfoService {
  def findByAssetRef(asset: AssetRef): ZIO[AssetInfoService, Throwable, AssetInfo] =
    ZIO.serviceWithZIO[AssetInfoService](_.findByAssetRef(asset))
  def loadFromFilesystem(infoFile: Path, shortcode: ProjectShortcode): ZIO[AssetInfoService, Throwable, AssetInfo] =
    ZIO.serviceWithZIO[AssetInfoService](_.loadFromFilesystem(infoFile, shortcode))
  def updateAssetInfoForDerivative(derivative: Path): ZIO[AssetInfoService, Throwable, Unit] =
    ZIO.serviceWithZIO[AssetInfoService](_.updateAssetInfoForDerivative(derivative))
  def getInfoFilePath(asset: AssetRef): ZIO[AssetInfoService, Nothing, Path] =
    ZIO.serviceWithZIO[AssetInfoService](_.getInfoFilePath(asset))
  def createAssetInfo(asset: Asset): ZIO[AssetInfoService, Throwable, Unit] =
    ZIO.serviceWithZIO[AssetInfoService](_.createAssetInfo(asset))
}

final case class AssetInfoServiceLive(storageService: StorageService) extends AssetInfoService {
  override def loadFromFilesystem(infoFile: Path, shortcode: ProjectShortcode): Task[AssetInfo] =
    for {
      content   <- storageService.loadJsonFile[AssetInfoFileContent](infoFile)
      assetMaybe = AssetId.makeFromPath(Path(content.internalFilename.toString)).map(id => AssetRef(id, shortcode))
      assetInfo <- assetMaybe match {
                     case Some(asset) => ZIO.succeed(toAssetInfo(content, infoFile.parent.orNull, asset))
                     case None        => ZIO.fail(IllegalArgumentException(s"Unable to parse asset id from $infoFile"))
                   }
    } yield assetInfo

  override def findByAssetRef(asset: AssetRef): Task[AssetInfo] =
    getInfoFilePath(asset).flatMap(parseAssetInfoFile(asset, _))

  def getInfoFilePath(asset: AssetRef): UIO[Path] =
    storageService.getAssetDirectory(asset).map(_ / infoFilename(asset))

  private def infoFilename(asset: AssetRef): String = infoFilename(asset.id)
  private def infoFilename(id: AssetId): String     = s"$id.info"

  private def parseAssetInfoFile(asset: AssetRef, infoFile: Path): Task[AssetInfo] =
    storageService.loadJsonFile[AssetInfoFileContent](infoFile).map(toAssetInfo(_, infoFile.parent.orNull, asset))

  private def toAssetInfo(
    raw: AssetInfoFileContent,
    infoFileDirectory: Path,
    asset: AssetRef
  ): AssetInfo = {
    val movingImageMetadata = for {
      width    <- raw.width
      height   <- raw.height
      duration <- raw.duration
      fps      <- raw.fps
    } yield MovingImageMetadata(width, height, duration, fps)
    AssetInfo(
      assetRef = asset,
      original = FileAndChecksum(infoFileDirectory / raw.originalInternalFilename.toString, raw.checksumOriginal),
      originalFilename = raw.originalFilename,
      derivative = FileAndChecksum(infoFileDirectory / raw.internalFilename.toString, raw.checksumDerivative),
      movingImageMetadata = movingImageMetadata
    )
  }

  override def findAllInPath(path: Path, shortcode: ProjectShortcode): ZStream[Any, Throwable, AssetInfo] =
    StorageService
      .findInPath(path, FileFilters.hasFileExtension("info"))
      .mapZIOPar(StorageService.maxParallelism())(loadFromFilesystem(_, shortcode))

  override def updateAssetInfoForDerivative(derivative: Path): Task[Unit] = for {
    assetId <- ZIO
                 .fromOption(AssetId.makeFromPath(derivative))
                 .orElseFail(IllegalArgumentException(s"Unable to parse asset id from $derivative"))
    infoFile = derivative.parent.map(_ / infoFilename(assetId)).orNull
    _       <- ZIO.whenZIO(Files.exists(infoFile))(updateDerivativeChecksum(infoFile, derivative))
  } yield ()

  private def updateDerivativeChecksum(infoFile: Path, derivative: Path) = for {
    content     <- storageService.loadJsonFile[AssetInfoFileContent](infoFile)
    newChecksum <- FileChecksumService.createSha256Hash(derivative)
    _           <- storageService.saveJsonFile(infoFile, content.withDerivativeChecksum(newChecksum))
  } yield ()

  override def createAssetInfo(asset: Asset): Task[Unit] = for {
    assetDir           <- storageService.getAssetDirectory(asset.ref)
    infoFile            = assetDir / infoFilename(asset.ref)
    checksumOriginal   <- FileChecksumService.createSha256Hash(asset.original.file.toPath)
    checksumDerivative <- FileChecksumService.createSha256Hash(asset.derivative.toPath)
    metadata = asset match {
                 case mi: MovingImageAsset => Some(mi.metadata)
                 case _                    => None
               }
    content = AssetInfoFileContent.make(asset, checksumOriginal, checksumDerivative, metadata)
    _      <- Files.createFile(infoFile)
    _      <- storageService.saveJsonFile(infoFile, content)
  } yield ()
}

object AssetInfoServiceLive {
  val layer = ZLayer.derive[AssetInfoServiceLive]
}
