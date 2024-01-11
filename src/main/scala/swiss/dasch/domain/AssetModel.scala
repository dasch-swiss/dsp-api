/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.api.{Refined, RefinedTypeOps}
import eu.timepit.refined.string.MatchesRegex
import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.domain.DerivativeFile.JpxDerivativeFile
import swiss.dasch.domain.PathOps.fileExtension
import swiss.dasch.domain.SipiImageFormat.Jpx
import swiss.dasch.domain.SupportedFileType.OtherFiles
import swiss.dasch.domain.SupportedFileType.MovingImage
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

final case class Original(file: OriginalFile, originalFilename: NonEmptyString) {
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

def hasAssetIdInFilename(file: Path): Option[Path] = AssetId.fromPath(file).map(_ => file)

opaque type OriginalFile = Path

object OriginalFile {
  def from(file: Path): Option[OriginalFile] =
    file match {
      case hidden if hidden.filename.toString.startsWith(".")       => None
      case original if original.filename.toString.endsWith(".orig") => hasAssetIdInFilename(original)
      case _                                                        => None
    }

  def unsafeFrom(file: Path): OriginalFile = from(file).getOrElse(throw new Exception("Not an original file"))

  extension (file: OriginalFile) {
    def toPath: Path = file
  }

  extension (file: OriginalFile) {
    def filename: NonEmptyString = NonEmptyString.unsafeFrom(file.filename.toString)
  }

  extension (file: OriginalFile) {
    def assetId: AssetId = AssetId.fromPath(file).head
  }
}

sealed trait DerivativeFile(file: Path) {
  def assetId: AssetId
  final def toPath: Path             = file
  final def filename: NonEmptyString = NonEmptyString.unsafeFrom(file.filename.toString)
}

object DerivativeFile {
  private def from[A <: DerivativeFile](
    file: Path,
    isSupported: String => Boolean,
    f: (Path, AssetId) => A
  ): Either[String, A] =
    file match {
      case hidden if hidden.filename.toString.startsWith(".") => Left("Hidden file.")
      case derivative if isSupported(derivative.fileExtension) =>
        AssetId.fromPath(derivative).map(f(file, _)).toRight("No Asset ID in the filename.")
      case _ => Left("Unsupported file type.")
    }

  final case class JpxDerivativeFile private (file: Path, assetId: AssetId) extends DerivativeFile(file)
  object JpxDerivativeFile {
    def unsafeFrom(file: Path): JpxDerivativeFile =
      from(file).fold(e => throw new IllegalArgumentException(e), identity)
    def from(file: Path): Either[String, JpxDerivativeFile] =
      DerivativeFile.from(file, Jpx.acceptsExtension, JpxDerivativeFile.apply)
  }

  final case class OtherDerivativeFile private (file: Path, assetId: AssetId) extends DerivativeFile(file)
  object OtherDerivativeFile {
    def unsafeFrom(file: Path): OtherDerivativeFile =
      from(file).fold(e => throw new IllegalArgumentException(e), identity)
    def from(file: Path): Either[String, OtherDerivativeFile] =
      DerivativeFile.from(file, OtherFiles.acceptsExtension, OtherDerivativeFile.apply)
  }

  final case class MovingImageDerivativeFile private (file: Path, assetId: AssetId) extends DerivativeFile(file)
  object MovingImageDerivativeFile {
    def unsafeFrom(file: Path): MovingImageDerivativeFile =
      from(file).fold(e => throw new IllegalArgumentException(e), identity)
    def from(file: Path): Either[String, MovingImageDerivativeFile] =
      DerivativeFile.from(file, MovingImage.acceptsExtension, MovingImageDerivativeFile.apply)
  }
}
