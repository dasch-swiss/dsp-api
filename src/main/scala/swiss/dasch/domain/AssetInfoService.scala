/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import zio.*
import zio.json.{ DeriveJsonCodec, JsonCodec, JsonDecoder }
import zio.nio.file.{ Files, Path }
import zio.prelude.Validation
import zio.stream.ZStream

final private case class AssetInfoFileContent(
    internalFilename: String,
    originalInternalFilename: String,
    originalFilename: String,
    checksumOriginal: String,
    checksumDerivative: String,
  )
private object AssetInfoFileContent {
  implicit val codec: JsonCodec[AssetInfoFileContent] = DeriveJsonCodec.gen[AssetInfoFileContent]
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
  def findByAsset(asset: Asset): Task[AssetInfo]
  def findAllInPath(path: Path, shortcode: ProjectShortcode): ZStream[Any, Throwable, AssetInfo]
}
object AssetInfoService {
  def findByAsset(asset: Asset): ZIO[AssetInfoService, Throwable, AssetInfo]                                       =
    ZIO.serviceWithZIO[AssetInfoService](_.findByAsset(asset))
  def loadFromFilesystem(infoFile: Path, shortcode: ProjectShortcode): ZIO[AssetInfoService, Throwable, AssetInfo] =
    ZIO.serviceWithZIO[AssetInfoService](_.loadFromFilesystem(infoFile, shortcode))
}

final case class AssetInfoServiceLive(storageService: StorageService) extends AssetInfoService {
  override def loadFromFilesystem(infoFile: Path, shortcode: ProjectShortcode): Task[AssetInfo] =
    for {
      content   <- storageService.loadJsonFile[AssetInfoFileContent](infoFile)
      assetMaybe = AssetId.makeFromPath(Path(content.internalFilename)).map(id => Asset(id, shortcode))
      assetInfo <- assetMaybe match {
                     case Some(asset) => toAssetInfo(content, infoFile.parent.orNull, asset)
                     case None        => ZIO.fail(IllegalArgumentException(s"Unable to parse asset id from $infoFile"))
                   }
    } yield assetInfo

  override def findByAsset(asset: Asset): Task[AssetInfo] =
    getInfoFilePath(asset).flatMap(parseAssetInfoFile(asset, _))

  private def getInfoFilePath(asset: Asset): UIO[Path] =
    storageService.getAssetDirectory(asset).map(_ / s"${asset.id.toString}.info")

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
    Files
      .walk(path)
      .filter(_.filename.toString.endsWith(".info"))
      .filterZIO(path => Files.isRegularFile(path) && Files.isHidden(path).negate)
      .mapZIOPar(StorageService.maxParallelism())(loadFromFilesystem(_, shortcode))
}
object AssetInfoServiceLive {
  val layer = ZLayer.fromFunction(AssetInfoServiceLive.apply _)
}
