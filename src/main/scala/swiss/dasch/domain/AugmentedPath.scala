/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.domain.PathOps.{fileExtension, isHidden}
import swiss.dasch.domain.SipiImageFormat.Jpx
import swiss.dasch.domain.SupportedFileType.{MovingImage, OtherFiles}
import zio.nio.file.Path

import scala.util.Left

trait AugmentedPathBuilder[A <: AugmentedPath] {
  final def unsafeFrom(str: String): A           = unsafeFrom(Path(str))
  final def unsafeFrom(path: Path): A            = from(path).fold(e => throw new IllegalArgumentException(e), identity)
  final def from(str: String): Either[String, A] = from(Path(str))
  def from(path: Path): Either[String, A]
}

trait AugmentedPath {
  def path: Path
}

trait AugmentedFile extends AugmentedPath {
  inline def file: Path        = path
  def filename: NonEmptyString = NonEmptyString.unsafeFrom(file.filename.toString)
}

trait AssetFile extends AugmentedFile {
  def assetId: AssetId
}

trait DerivativeFile extends AssetFile

object AugmentedPath {
  import ErrorMessages.*

  private[AugmentedPath] object ErrorMessages {
    val hiddenFile: String          = "Hidden file."
    val unsupportedFileType: String = "Unsupported file type."
    val noAssetIdInFilename: String = "No AssetId in filename."
    val notAProjectFolder: String   = "Not a project folder."
  }

  trait WithSmartConstructors[A <: AugmentedPath] {
    def unsafeFrom(str: String)(using builder: AugmentedPathBuilder[A]): A =
      builder.unsafeFrom(Path(str))
    def unsafeFrom(path: Path)(using builder: AugmentedPathBuilder[A]): A =
      builder.unsafeFrom(path)
    def from(path: Path)(using builder: AugmentedPathBuilder[A]): Either[String, A] =
      builder.from(path)
    def from(str: String)(using builder: AugmentedPathBuilder[A]): Either[String, A] =
      builder.from(str)
  }

  final case class ProjectFolder(path: Path, shortcode: ProjectShortcode) extends AugmentedPath
  object ProjectFolder extends WithSmartConstructors[ProjectFolder] {
    given AugmentedPathBuilder[ProjectFolder] with {
      def from(path: Path): Either[String, ProjectFolder] =
        path match {
          case _ if path.isHidden => Left(hiddenFile)
          case _ =>
            ProjectShortcode
              .from(path.elements.last.toString)
              .map(ProjectFolder(path, _))
              .left
              .map(_ => notAProjectFolder)
        }
    }
  }

  private def from[A <: AssetFile](
    path: Path,
    fileExtensionSupported: String => Boolean,
    create: (Path, AssetId) => A
  ): Either[String, A] =
    path match {
      case _ if path.isHidden => Left(hiddenFile)
      case _ if fileExtensionSupported(path.fileExtension) =>
        AssetId.fromPath(path).map(create(path, _)).toRight(noAssetIdInFilename)
      case _ => Left(unsupportedFileType)
    }

  final case class JpxDerivativeFile private (path: Path, assetId: AssetId) extends DerivativeFile
  object JpxDerivativeFile extends WithSmartConstructors[JpxDerivativeFile] {
    given AugmentedPathBuilder[JpxDerivativeFile] with {
      def from(path: Path): Either[String, JpxDerivativeFile] =
        AugmentedPath.from(path, Jpx.acceptsExtension, JpxDerivativeFile.apply)
    }
  }

  final case class MovingImageDerivativeFile private (path: Path, assetId: AssetId) extends DerivativeFile
  object MovingImageDerivativeFile extends WithSmartConstructors[MovingImageDerivativeFile] {
    given AugmentedPathBuilder[MovingImageDerivativeFile] with {
      def from(path: Path): Either[String, MovingImageDerivativeFile] =
        AugmentedPath.from(path, MovingImage.acceptsExtension, MovingImageDerivativeFile.apply)
    }
  }

  final case class OrigFile private (path: Path, assetId: AssetId) extends AssetFile
  object OrigFile extends WithSmartConstructors[OrigFile] {
    given AugmentedPathBuilder[OrigFile] with {
      def from(path: Path): Either[String, OrigFile] =
        AugmentedPath.from(path, _ == "orig", OrigFile.apply)
    }
  }

  final case class OtherDerivativeFile private (path: Path, assetId: AssetId) extends DerivativeFile
  object OtherDerivativeFile extends WithSmartConstructors[OtherDerivativeFile] {
    given AugmentedPathBuilder[OtherDerivativeFile] with {
      def from(path: Path): Either[String, OtherDerivativeFile] =
        AugmentedPath.from(path, OtherFiles.acceptsExtension, OtherDerivativeFile.apply)
    }
  }
}
