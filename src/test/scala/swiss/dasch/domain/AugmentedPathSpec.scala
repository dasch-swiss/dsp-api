/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.domain.AugmentedPath.*
import swiss.dasch.domain.AugmentedPath.JpxDerivativeFile.given
import swiss.dasch.domain.AugmentedPath.MovingImageDerivativeFile.given
import swiss.dasch.domain.AugmentedPath.ProjectFolder.given
import swiss.dasch.domain.AugmentedPathSpec.ExpectedErrorMessages.{
  hiddenFile,
  noAssetIdInFilename,
  notAProjectFolder,
  unsupportedFileType
}
import swiss.dasch.test.SpecConstants
import zio.nio.file.Path
import zio.test.{Gen, Spec, ZIOSpecDefault, assertTrue, check}

object AugmentedPathSpec extends ZIOSpecDefault {

  private val someAssetId   = SpecConstants.AssetIds.existingAsset.value
  private val someProjectId = SpecConstants.Projects.existingProject.value

  object ExpectedErrorMessages {
    val hiddenFile: String          = "Hidden file."
    val unsupportedFileType: String = "Unsupported file type."
    val noAssetIdInFilename: String = "No AssetId in filename."
    val notAProjectFolder: String   = "Not a project folder."
  }

  private val projectFolderSuite = suite("ProjectFolder")(
    test("can be created from a Path which is a project folder") {
      val actual = ProjectFolder.from(Path(s"/tmp/$someProjectId/"))
      assertTrue(
        actual.map(_.path).contains(Path(s"/tmp/$someProjectId/")),
        actual.map(_.shortcode).contains(someProjectId)
      )
    },
    test("cannot be created from a Path which is hidden") {
      assertTrue(ProjectFolder.from(Path(s"/tmp/.$someProjectId/")) == Left(hiddenFile))
    },
    test("cannot be created from a Path which is not a project folder") {
      assertTrue(ProjectFolder.from(Path("/tmp/not-a-project-folder/")) == Left(notAProjectFolder))
    }
  )

  private val jpxDerivativeFileSuite =
    suite("JpxDerivativeFile")(
      test("can be created from a Path which is a derivative file jpx") {
        val gen = Gen.fromIterable(List("jpx", "JPX", "jp2", "JP2"))
        check(gen) { extension =>
          val actual = JpxDerivativeFile.from(Path(s"/tmp/$someAssetId.$extension"))
          assertTrue(
            actual.map(_.path).contains(Path(s"/tmp/$someAssetId.$extension")),
            actual.map(_.assetId).contains(someAssetId)
          )
        }
      },
      test("cannot be created if filename is not a valid AssetId") {
        assertTrue(JpxDerivativeFile.from(Path("/tmp/this_is_no_asset_id!.jpx")) == Left(noAssetIdInFilename))
      },
      test("cannot be created from original file") {
        assertTrue(JpxDerivativeFile.from(Path(s"/tmp/$someAssetId.orig")) == Left(unsupportedFileType))
      },
      test("cannot be created from directory") {
        assertTrue(JpxDerivativeFile.from(Path(s"/tmp/hello/")) == Left(unsupportedFileType))
      },
      test("cannot be created from hidden file") {
        val hiddenFiles = Gen.fromIterable(List(s".$someAssetId.txt", s".$someAssetId.jpx"))
        check(hiddenFiles) { filename =>
          assertTrue(JpxDerivativeFile.from(Path(s"/tmp/$filename")) == Left(hiddenFile))
        }
      }
    )

  private val movingImageDerivativeFileSuite = suite("MovingImageDerivativeFile")(
    test("can be created from a Path which is a derivative file mov") {
      val gen = Gen.fromIterable(List("mp4", "MP4"))
      check(gen) { extension =>
        val actual = MovingImageDerivativeFile.from(Path(s"/tmp/$someAssetId.$extension"))
        assertTrue(
          actual.map(_.path).contains(Path(s"/tmp/$someAssetId.$extension")),
          actual.map(_.assetId).contains(someAssetId)
        )
      }
    },
    test("cannot be created if filename is not a valid AssetId") {
      assertTrue(MovingImageDerivativeFile.from(Path("/tmp/this_is_no_asset_id!.mp4")) == Left(noAssetIdInFilename))
    },
    test("cannot be created from original file") {
      assertTrue(MovingImageDerivativeFile.from(Path(s"/tmp/$someAssetId.orig")) == Left(unsupportedFileType))
    },
    test("cannot be created from directory") {
      assertTrue(MovingImageDerivativeFile.from(Path(s"/tmp/hello/")) == Left(unsupportedFileType))
    },
    test("cannot be created from hidden file") {
      val hiddenFiles = Gen.fromIterable(List(s".$someAssetId.mp4", s".$someAssetId.MP4"))
      check(hiddenFiles) { filename =>
        assertTrue(MovingImageDerivativeFile.from(Path(s"/tmp/$filename")) == Left(hiddenFile))
      }
    }
  )

  private val origFileSuite = suite("OrigFile")(
    test("can be created if Path ends with .orig") {
      val path   = Path(s"/tmp/$someAssetId.orig")
      val actual = OrigFile.from(path)
      assertTrue(
        actual.map(_.path).contains(path),
        actual.map(_.assetId).contains(someAssetId)
      )
    },
    test("cannot be created if filename is not a valid AssetId") {
      assertTrue(OrigFile.from(Path("/tmp/this_is_no_asset_id!.orig")) == Left(noAssetIdInFilename))
    },
    test("cannot be created if file is not an .orig file, e.g. directory or other extension") {
      val invalidFileExtensions = Gen.fromIterable(List(".png", ".orig.tiff", "/", ".txt", ""))
      check(invalidFileExtensions) { extension =>
        assertTrue(OrigFile.from(Path(s"/tmp/$someAssetId$extension")) == Left(unsupportedFileType))
      }
    },
    test("cannot be created from hidden file") {
      val hiddenFiles = Gen.fromIterable(List(s".$someAssetId.txt", s".$someAssetId.orig", s".$someAssetId.tiff.orig"))
      check(hiddenFiles) { filename =>
        assertTrue(OrigFile.from(Path(s"/tmp/$filename")) == Left(hiddenFile))
      }
    }
  )

  private val otherDerivativeFileSuite = suite("OtherDerivativeFile")(
    test("can be created from a Path which is a derivative file") {
      val gen = Gen.fromIterable(SupportedFileType.OtherFiles.extensions)
      check(gen) { extension =>
        val actual = OtherDerivativeFile.from(Path(s"/tmp/$someAssetId.$extension"))
        assertTrue(
          actual.map(_.path).contains(Path(s"/tmp/$someAssetId.$extension")),
          actual.map(_.assetId).contains(someAssetId)
        )
      }
    },
    test("cannot be created if filename is not a valid AssetId") {
      val path = Path("/tmp/this_is_no_asset_id!.txt")
      assertTrue(OtherDerivativeFile.from(path) == Left(noAssetIdInFilename))
    },
    test("cannot be created from original file") {
      assertTrue(OtherDerivativeFile.from(Path(s"/tmp/$someAssetId.orig")) == Left(unsupportedFileType))
    },
    test("cannot be created from directory") {
      assertTrue(OtherDerivativeFile.from(Path(s"/tmp/hello/")) == Left(unsupportedFileType))
    },
    test("cannot be created from hidden file") {
      val hiddenFiles = Gen.fromIterable(List(s".$someAssetId.txt", s".$someAssetId.TXT"))
      check(hiddenFiles) { filename =>
        assertTrue(OtherDerivativeFile.from(Path(s"/tmp/$filename")) == Left(hiddenFile))
      }
    }
  )

  val spec: Spec[Any, Any] = suite("AugmentedPath")(
    projectFolderSuite,
    jpxDerivativeFileSuite,
    movingImageDerivativeFileSuite,
    origFileSuite,
    otherDerivativeFileSuite
  )
}
