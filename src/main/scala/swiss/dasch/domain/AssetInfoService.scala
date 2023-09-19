/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import zio.*
import zio.json.{ DeriveJsonCodec, JsonCodec }
import zio.nio.file.{ Files, Path }
import zio.prelude.Validation
import zio.stream.ZStream

final private case class AssetInfoFileContent(
    internalFilename: String,
    originalInternalFilename: String,
    originalFilename: String,
    checksumOriginal: String,
    checksumDerivative: String,
  ) {
  def withDerivativeChecksum(checksum: Sha256Hash): AssetInfoFileContent = copy(checksumDerivative = checksum.toString)
}
private object AssetInfoFileContent {
  def make(
      imageAsset: ImageAsset,
      originalChecksum: Sha256Hash,
      derivativeChecksum: Sha256Hash,
    ): AssetInfoFileContent =
    AssetInfoFileContent(
      imageAsset.derivativeFilename,
      imageAsset.originalInternalFilename,
      imageAsset.originalFilename.value,
      originalChecksum.toString,
      derivativeChecksum.toString,
    )

  given codec: JsonCodec[AssetInfoFileContent] = DeriveJsonCodec.gen[AssetInfoFileContent]
}

final case class FileAndChecksum(file: Path, checksum: Sha256Hash)
final case class AssetInfo(
    asset: Asset,
    original: FileAndChecksum,
    originalFilename: NonEmptyString,
    derivative: FileAndChecksum,
  )

trait AssetInfoService  {
  def loadFromFilesystem(infoFile: Path, shortcode: ProjectShortcode): Task[AssetInfo]
  def getInfoFilePath(asset: Asset): UIO[Path]
  def findByAsset(asset: Asset): Task[AssetInfo]
  def findAllInPath(path: Path, shortcode: ProjectShortcode): ZStream[Any, Throwable, AssetInfo]
  def updateAssetInfoForDerivative(derivative: Path): Task[Unit]
  def createAssetInfo(asset: ImageAsset): Task[Unit]
}
object AssetInfoService {
  def findByAsset(asset: Asset): ZIO[AssetInfoService, Throwable, AssetInfo]                                       =
    ZIO.serviceWithZIO[AssetInfoService](_.findByAsset(asset))
  def loadFromFilesystem(infoFile: Path, shortcode: ProjectShortcode): ZIO[AssetInfoService, Throwable, AssetInfo] =
    ZIO.serviceWithZIO[AssetInfoService](_.loadFromFilesystem(infoFile, shortcode))
  def updateAssetInfoForDerivative(derivative: Path): ZIO[AssetInfoService, Throwable, Unit]                       =
    ZIO.serviceWithZIO[AssetInfoService](_.updateAssetInfoForDerivative(derivative))
  def getInfoFilePath(asset: Asset): ZIO[AssetInfoService, Nothing, Path]                                          =
    ZIO.serviceWithZIO[AssetInfoService](_.getInfoFilePath(asset))
  def createAssetInfo(asset: ImageAsset): ZIO[AssetInfoService, Throwable, Unit]                                   =
    ZIO.serviceWithZIO[AssetInfoService](_.createAssetInfo(asset))
}

final case class AssetInfoServiceLive(storageService: StorageService) extends AssetInfoService {
  override def loadFromFilesystem(infoFile: Path, shortcode: ProjectShortcode): Task[AssetInfo] =
    for {
      content   <- storageService.loadJsonFile[AssetInfoFileContent](infoFile)
      assetMaybe = AssetId.makeFromPath(Path(content.internalFilename)).map(id => SimpleAsset(id, shortcode))
      assetInfo <- assetMaybe match {
                     case Some(asset) => toAssetInfo(content, infoFile.parent.orNull, asset)
                     case None        => ZIO.fail(IllegalArgumentException(s"Unable to parse asset id from $infoFile"))
                   }
    } yield assetInfo

  override def findByAsset(asset: Asset): Task[AssetInfo] =
    getInfoFilePath(asset).flatMap(parseAssetInfoFile(asset, _))

  def getInfoFilePath(asset: Asset): UIO[Path] =
    storageService.getAssetDirectory(asset).map(_ / infoFilename(asset))

  private def infoFilename(asset: Asset): String = infoFilename(asset.id)
  private def infoFilename(id: AssetId): String  = s"$id.info"

  private def parseAssetInfoFile(asset: Asset, infoFile: Path): Task[AssetInfo] =
    storageService.loadJsonFile[AssetInfoFileContent](infoFile).flatMap(toAssetInfo(_, infoFile.parent.orNull, asset))

  private def toAssetInfo(
      raw: AssetInfoFileContent,
      infoFileDirectory: Path,
      asset: Asset,
    ): Task[AssetInfo] =
    Validation
      .validateWith(
        Validation.fromEither(Sha256Hash.make(raw.checksumOriginal)),
        Validation.fromEither(Sha256Hash.make(raw.checksumDerivative)),
        Validation.fromEither(NonEmptyString.from(raw.originalFilename)),
      ) {
        (
            origChecksum,
            derivativeChecksum,
            origFilename,
          ) =>
          AssetInfo(
            asset = asset,
            original = FileAndChecksum(infoFileDirectory / raw.originalInternalFilename, origChecksum),
            originalFilename = origFilename,
            derivative = FileAndChecksum(infoFileDirectory / raw.internalFilename, derivativeChecksum),
          )
      }
      .toZIO
      .mapError(e => new IllegalArgumentException(s"Invalid asset info file content $raw, $infoFileDirectory, $e"))

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

  override def createAssetInfo(asset: ImageAsset): Task[Unit] = for {
    assetDir           <- storageService.getAssetDirectory(asset)
    infoFile            = assetDir / infoFilename(asset)
    checksumOriginal   <- FileChecksumService.createSha256Hash(asset.original.toPath)
    checksumDerivative <- FileChecksumService.createSha256Hash(asset.derivative.toPath)
    content             = AssetInfoFileContent.make(asset, checksumOriginal, checksumDerivative)
    _                  <- Files.createFile(infoFile)
    _                  <- storageService.saveJsonFile(infoFile, content)
  } yield ()
}
object AssetInfoServiceLive {
  val layer = ZLayer.fromFunction(AssetInfoServiceLive.apply _)
}
