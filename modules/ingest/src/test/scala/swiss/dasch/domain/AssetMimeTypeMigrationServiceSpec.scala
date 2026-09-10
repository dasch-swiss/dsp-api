/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import org.junit.runner.RunWith
import swiss.dasch.domain.AssetInfoFileTestHelper.*
import swiss.dasch.domain.AugmentedPath.AssetFolder
import swiss.dasch.test.SpecConfigurations
import swiss.dasch.util.TestUtils
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class AssetMimeTypeMigrationServiceSpec extends ZIOSpecDefault {

  private val migration = ZIO.service[AssetMimeTypeMigrationService]

  private def infoFileOf(assetDir: AssetFolder) = assetDir / s"${assetDir.assetId}.info"

  private def originalMimeTypeOf(assetDir: AssetFolder) =
    AssetInfoService
      .findByAssetRef(assetDir.assetRef)
      .map(_.head.metadata.originalMimeType.map(_.stringValue))

  // The extensions actually present in the store, each mapping to a type SupportedFileType already
  // defines — the migration must never invent a value outside that set.
  private val extensionCases = Seq(
    "jpg"  -> "image/jpeg",
    "jpeg" -> "image/jpeg",
    "tif"  -> "image/tiff",
    "tiff" -> "image/tiff",
    "png"  -> "image/png",
    "pdf"  -> "application/pdf",
    "mp3"  -> "audio/mpeg",
    "mp4"  -> "video/mp4",
    "txt"  -> "text/plain",
    "zip"  -> "application/zip",
    "csv"  -> "text/csv",
    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "xsl"  -> "application/xslt+xml",
  )

  private val perExtension = suite("backfills each extension found in the store")(
    extensionCases.map { case (ext, expected) =>
      test(s"test.$ext :: $expected") {
        for {
          assetDir <- createInfoFile(originalFileExt = ext, derivativeFileExt = "jp2")
          before   <- originalMimeTypeOf(assetDir)
          _        <- migration.flatMap(_.migrateProject(testProject))
          after    <- originalMimeTypeOf(assetDir)
        } yield assertTrue(before.isEmpty, after == Some(expected))
      }
    },
  )

  private val perExtensionCaseInsensitive =
    suite("backfills regardless of how the extension is cased")(
      // 15136 of the sampled sidecars name a ".TIF" original.
      test("test.TIF :: image/tiff") {
        for {
          assetDir <- createInfoFile(originalFileExt = "TIF", derivativeFileExt = "jp2")
          _        <- migration.flatMap(_.migrateProject(testProject))
          after    <- originalMimeTypeOf(assetDir)
        } yield assertTrue(after == Some("image/tiff"))
      },
    )

  private val idempotence = suite("leaves a sidecar that already has the field alone")(
    test("an existing originalMimeType is never overwritten, even a surprising one") {
      for {
        assetDir <- createInfoFile(
                      originalFileExt = "jpg",
                      derivativeFileExt = "jp2",
                      customJsonProps = Some(""""originalMimeType" : "image/tiff""""),
                    )
        _     <- migration.flatMap(_.migrateProject(testProject))
        after <- originalMimeTypeOf(assetDir)
      } yield assertTrue(after == Some("image/tiff"))
    },
    test("a second run changes nothing") {
      for {
        assetDir <- createInfoFile(originalFileExt = "pdf", derivativeFileExt = "pdf")
        _        <- migration.flatMap(_.migrateProject(testProject))
        first    <- originalMimeTypeOf(assetDir)
        report   <- migration.flatMap(_.migrateProject(testProject))
        second   <- originalMimeTypeOf(assetDir)
      } yield assertTrue(
        first == Some("application/pdf"),
        second == first,
        // everything is already filled in by now, so nothing is left to update
        report.updated == 0,
        report.alreadyPresent == report.found,
      )
    },
  )

  private val unknownExtension = suite("an extension the guesser does not know")(
    test("is left absent, logged, and counted rather than failing the run") {
      for {
        assetDir <- createInfoFile(originalFileExt = "qqq", derivativeFileExt = "jp2")
        report   <- migration.flatMap(_.migrateProject(testProject))
        after    <- originalMimeTypeOf(assetDir)
        warnings <- ZTestLogger.logOutput.map(_.filter(_.logLevel == LogLevel.Warning).map(_.message()))
      } yield assertTrue(
        after.isEmpty,
        report.unknownExtension >= 1,
        report.failed == 0,
        warnings.exists(_ == s"No mime type for extension of 'test.qqq' in ${infoFileOf(assetDir)}, left absent"),
      )
    },
  )

  private val reporting = suite("migrateProject")(
    test("reports one found per sidecar, with updated and alreadyPresent adding up") {
      for {
        _      <- createInfoFile(originalFileExt = "jpg", derivativeFileExt = "jp2")
        _      <- createInfoFile(originalFileExt = "tif", derivativeFileExt = "jp2")
        report <- migration.flatMap(_.migrateProject(testProject))
      } yield assertTrue(
        // the store is shared across this spec, so assert on the invariant rather than an exact count
        report.found >= 2,
        report.updated + report.alreadyPresent + report.unknownExtension + report.failed == report.found,
        report.failed == 0,
      )
    },
    test("fails for a project that does not exist") {
      for {
        result <- migration.flatMap(_.migrateProject(ProjectShortcode.unsafeFrom("9999"))).flip
      } yield assertTrue(
        result.isInstanceOf[IllegalArgumentException],
        result.getMessage == "Project 9999 not found",
      )
    },
  )

  private val discovery = suite("findAllInfoFiles")(
    test("finds the sidecar of a newly created asset") {
      for {
        assetDir  <- createInfoFile(originalFileExt = "pdf", derivativeFileExt = "pdf")
        infoFiles <- migration.flatMap(_.findAllInfoFiles().runCollect)
      } yield assertTrue(infoFiles.contains(infoFileOf(assetDir)))
    },
  )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("AssetMimeTypeMigrationServiceSpec")(
      perExtension,
      perExtensionCaseInsensitive,
      idempotence,
      unknownExtension,
      reporting,
      discovery,
    ).provide(
      AssetInfoServiceLive.layer,
      AssetMimeTypeMigrationService.layer,
      FileChecksumServiceLive.layer,
      MimeTypeGuesser.layer,
      ProjectRepositoryLive.layer,
      ProjectService.layer,
      SpecConfigurations.storageConfigLayer,
      StorageServiceLive.layer,
      TestUtils.testDbLayerWithEmptyDb,
    )
}
