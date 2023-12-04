/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.api.Refined
import eu.timepit.refined.refineV
import eu.timepit.refined.string.MatchesRegex
import eu.timepit.refined.types.string.NonEmptyString
import org.apache.commons.io.FilenameUtils
import swiss.dasch.domain.DerivativeFile.JpxDerivativeFile
import swiss.dasch.domain.SipiImageFormat.Jpx
import swiss.dasch.infrastructure.Base62
import zio.json.JsonCodec
import zio.nio.file.Path
import zio.{Random, UIO}

opaque type AssetId = String Refined MatchesRegex["^[a-zA-Z0-9-_]{4,}$"]

object AssetId {
  def make(id: String): Either[String, AssetId] = refineV(id)

  def makeNew: UIO[AssetId] = Random.nextUUID
    .map(uuid =>
      // the unsafeApply is safe here because the [[Base62EncodedUuid]] is valid subset of AssetId
      Refined.unsafeApply(Base62.encode(uuid).value)
    )

  def makeFromPath(file: Path): Option[AssetId] = {
    val filename = file.filename.toString
    filename.contains(".") match {
      case true  => AssetId.make(filename.substring(0, filename.indexOf("."))).toOption
      case false => None
    }
  }

  given codec: JsonCodec[AssetId] = JsonCodec[String].transformOrFail(AssetId.make, _.toString)
}

final case class AssetRef(id: AssetId, belongsToProject: ProjectShortcode)

object AssetRef {
  def makeNew(project: ProjectShortcode): UIO[AssetRef] = AssetId.makeNew.map(id => AssetRef(id, project))
}

sealed trait Asset {
  def id: AssetId
  def belongsToProject: ProjectShortcode

  def ref: AssetRef = AssetRef(id, belongsToProject)
}

sealed trait ComplexAsset extends Asset {
  def originalFilename: NonEmptyString
  def original: OriginalFile
  def derivative: DerivativeFile
  final def originalInternalFilename: String = original.filename
  final def derivativeFilename: String       = derivative.filename
}
object ComplexAsset {
  final case class ImageAsset(
    id: AssetId,
    belongsToProject: ProjectShortcode,
    originalFilename: NonEmptyString,
    original: OriginalFile,
    derivative: DerivativeFile
  ) extends ComplexAsset

  def makeImageAsset(
    assetRef: AssetRef,
    originalFilename: NonEmptyString,
    original: OriginalFile,
    derivative: JpxDerivativeFile
  ): ComplexAsset.ImageAsset =
    ComplexAsset.ImageAsset(assetRef.id, assetRef.belongsToProject, originalFilename, original, derivative)
}

def hasAssetIdInFilename(file: Path): Option[Path] = AssetId.makeFromPath(file).map(_ => file)

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
    def filename: String = file.filename.toString
  }

  extension (file: OriginalFile) {
    def assetId: AssetId = AssetId.makeFromPath(file).head
  }
}

sealed trait DerivativeFile(file: Path) {
  def from(file: Path): Option[DerivativeFile]

  final def toPath: Path     = file
  final def filename: String = file.filename.toString
  final def assetId: AssetId = AssetId.makeFromPath(file).head
}

object DerivativeFile {
  final case class JpxDerivativeFile private (file: Path) extends DerivativeFile(file) {
    override def from(file: Path): Option[JpxDerivativeFile] = JpxDerivativeFile.from(file)
  }

  object JpxDerivativeFile {
    def from(file: Path): Option[JpxDerivativeFile] =
      file match {
        case hidden if hidden.filename.toString.startsWith(".") => None
        case derivative if Jpx.acceptsExtension(FilenameUtils.getExtension(file.filename.toString)) =>
          hasAssetIdInFilename(derivative).map(JpxDerivativeFile(_))
        case _ => None
      }
    def unsafeFrom(file: Path): JpxDerivativeFile = from(file).getOrElse(throw new Exception("Not a derivative file"))
  }
}
