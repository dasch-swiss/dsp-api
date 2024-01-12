/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.api.{Refined, RefinedTypeOps}
import eu.timepit.refined.string.MatchesRegex
import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.domain.AugmentedPath.{JpxDerivativeFile, OrigFile}
import swiss.dasch.infrastructure.Base62
import zio.json.JsonCodec
import zio.nio.file.Path
import zio.{Random, UIO}

type AssetId = String Refined MatchesRegex["^[a-zA-Z0-9-_]{4,}$"]

object AssetId extends RefinedTypeOps[AssetId, String] {

  def makeNew: UIO[AssetId] = Random.nextUUID
    .map(uuid =>
      // the unsafeFrom is safe here because the [[Base62EncodedUuid]] is valid subset of AssetId
      AssetId.unsafeFrom(Base62.encode(uuid).value)
    )

  def fromPath(file: Path): Option[AssetId] = {
    val filename = file.filename.toString

    if (filename.contains(".")) AssetId.from(filename.substring(0, filename.indexOf("."))).toOption
    else None
  }

  given codec: JsonCodec[AssetId] = JsonCodec[String].transformOrFail(AssetId.from, _.toString)
}

final case class AssetRef(id: AssetId, belongsToProject: ProjectShortcode)

object AssetRef {
  def makeNew(project: ProjectShortcode): UIO[AssetRef] = AssetId.makeNew.map(id => AssetRef(id, project))
}

final case class Original(file: OrigFile, originalFilename: NonEmptyString) {
  def internalFilename: NonEmptyString = file.filename
  def assetId: AssetId                 = file.assetId
}

sealed trait Asset {
  def ref: AssetRef
  def original: Original
  def derivative: DerivativeFile
  def metadata: AssetMetadata
  final def id: AssetId                        = ref.id
  final def belongsToProject: ProjectShortcode = ref.belongsToProject
}

object Asset {
  final case class StillImageAsset(
    ref: AssetRef,
    original: Original,
    derivative: JpxDerivativeFile,
    metadata: StillImageMetadata
  ) extends Asset

  final case class MovingImageAsset(
    ref: AssetRef,
    original: Original,
    derivative: DerivativeFile,
    metadata: MovingImageMetadata
  ) extends Asset

  final case class OtherAsset(
    ref: AssetRef,
    original: Original,
    derivative: DerivativeFile,
    metadata: OtherMetadata
  ) extends Asset

  def makeStillImage(
    assetRef: AssetRef,
    original: Original,
    derivative: JpxDerivativeFile,
    metadata: StillImageMetadata
  ): StillImageAsset =
    StillImageAsset(assetRef, original, derivative, metadata)

  def makeMovingImageAsset(
    assetRef: AssetRef,
    original: Original,
    derivative: DerivativeFile,
    metadata: MovingImageMetadata
  ): MovingImageAsset = MovingImageAsset(assetRef, original, derivative, metadata)

  def makeOther(
    assetRef: AssetRef,
    original: Original,
    derivative: DerivativeFile,
    metadata: OtherMetadata
  ): OtherAsset = OtherAsset(assetRef, original, derivative, metadata)
}
