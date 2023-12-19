/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
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
  def from(assetInfo: AssetInfo): AssetInfoFileContent = {
    val dim = assetInfo.metadata match {
      case MovingImageMetadata(d, _, _) => Some(d)
      case d: Dimensions                => Some(d)
      case _                            => None
    }
    val duration = assetInfo.metadata match {
      case MovingImageMetadata(_, duration, _) => Some(duration)
      case _                                   => None
    }
    val fps = assetInfo.metadata match {
      case MovingImageMetadata(_, _, fps) => Some(fps)
      case _                              => None
    }
    AssetInfoFileContent(
      assetInfo.derivative.filename,
      assetInfo.original.filename,
      assetInfo.originalFilename,
      assetInfo.original.checksum,
      assetInfo.derivative.checksum,
      dim.map(_.width.value),
      dim.map(_.height.value),
      duration,
      fps
    )
  }

  def from(
    asset: Asset,
    originalChecksum: Sha256Hash,
    derivativeChecksum: Sha256Hash,
    metadata: AssetMetadata
  ): AssetInfoFileContent = {
    val dim = metadata match {
      case MovingImageMetadata(d, _, _) => Some(d)
      case d: Dimensions                => Some(d)
      case _                            => None
    }
    val duration = metadata match {
      case MovingImageMetadata(_, duration, _) => Some(duration)
      case _                                   => None
    }
    val fps = metadata match {
      case MovingImageMetadata(_, _, fps) => Some(fps)
      case _                              => None
    }
    AssetInfoFileContent(
      asset.derivative.filename,
      asset.original.internalFilename,
      asset.original.originalFilename,
      originalChecksum,
      derivativeChecksum,
      dim.map(_.width.value),
      dim.map(_.height.value),
      duration,
      fps
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
  metadata: AssetMetadata = EmptyMetadata
)

trait AssetInfoService {
  def loadFromFilesystem(infoFile: Path, shortcode: ProjectShortcode): Task[AssetInfo]
  def getInfoFilePath(asset: AssetRef): UIO[Path]
  def findByAssetRef(asset: AssetRef): Task[Option[AssetInfo]]
  def save(assetInfo: AssetInfo): Task[Unit]
  def findAllInPath(path: Path, shortcode: ProjectShortcode): ZStream[Any, Throwable, AssetInfo]
  def updateAssetInfoForDerivative(derivative: Path): Task[Unit]
  def createAssetInfo(asset: Asset): Task[Unit]
  def updateStillImageMetadata(assetRef: AssetRef, metadata: StillImageMetadata): Task[Unit]
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
  def createAssetInfo(asset: Asset): ZIO[AssetInfoService, Throwable, Unit] =
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
    val dimensions = for {
      width  <- raw.width
      height <- raw.height
      dim    <- Dimensions.from(width, height).toOption
    } yield dim
    val movingImageMetadata = for {
      dim      <- dimensions
      duration <- raw.duration
      fps      <- raw.fps
    } yield MovingImageMetadata(dim, duration, fps)
    val metadata = movingImageMetadata.orElse(dimensions).getOrElse(EmptyMetadata)
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

  override def updateStillImageMetadata(assetRef: AssetRef, metadata: StillImageMetadata): Task[Unit] = for {
    info <- findByAssetRef(assetRef).someOrFail(IllegalArgumentException(s"AssetInfo for $assetRef not found"))
    _ <- ZIO.when(info.metadata.isInstanceOf[MovingImageMetadata])(
           ZIO.fail(IllegalArgumentException(s"Asset $assetRef seems to be a moving image"))
         )
    _ <- ZIO.when(info.metadata != metadata)(save(info.copy(metadata = metadata))).unit
  } yield ()

  override def createAssetInfo(asset: Asset): Task[Unit] = for {
    assetDir           <- storage.getAssetDirectory(asset.ref)
    infoFile            = assetDir / infoFilename(asset.ref)
    checksumOriginal   <- FileChecksumService.createSha256Hash(asset.original.file.toPath)
    checksumDerivative <- FileChecksumService.createSha256Hash(asset.derivative.toPath)
    content             = AssetInfoFileContent.from(asset, checksumOriginal, checksumDerivative, asset.metadata)
    _                  <- Files.createFile(infoFile)
    _                  <- storage.saveJsonFile(infoFile, content)
  } yield ()
}

object AssetInfoServiceLive {
  val layer = ZLayer.derive[AssetInfoServiceLive]
}
