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

sealed trait Asset {
  def id: AssetId
  def belongsToProject: ProjectShortcode
}

object Asset {
  def makeNew(project: ProjectShortcode): UIO[SimpleAsset] = AssetId.makeNew.map(id => SimpleAsset(id, project))
}

final case class SimpleAsset(id: AssetId, belongsToProject: ProjectShortcode) extends Asset {
  def makeImageAsset(
    originalFilename: NonEmptyString,
    original: OriginalFile,
    derivative: JpxDerivativeFile
  ): ImageAsset =
    ImageAsset(id, belongsToProject, originalFilename, original, derivative)
}

final case class ImageAsset(
  id: AssetId,
  belongsToProject: ProjectShortcode,
  originalFilename: NonEmptyString,
  original: OriginalFile,
  derivative: JpxDerivativeFile
) extends Asset {
  def originalInternalFilename: String = original.filename
  def derivativeFilename: String       = derivative.filename
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

opaque type JpxDerivativeFile = Path

object JpxDerivativeFile {
  def from(file: Path): Option[JpxDerivativeFile] =
    file match {
      case hidden if hidden.filename.toString.startsWith(".") => None
      case derivative if Jpx.acceptsExtension(FilenameUtils.getExtension(file.filename.toString)) =>
        hasAssetIdInFilename(derivative)
      case _ => None
    }

  def unsafeFrom(file: Path): JpxDerivativeFile =
    from(file).getOrElse(throw new Exception("Not a derivative file"))

  extension (file: JpxDerivativeFile) {
    def toPath: Path = file
  }

  extension (file: JpxDerivativeFile) {
    def filename: String = file.filename.toString
  }

  extension (file: JpxDerivativeFile) {
    def assetId: AssetId = AssetId.makeFromPath(file).head
  }
}
