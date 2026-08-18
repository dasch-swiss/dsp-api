/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import org.junit.runner.RunWith
import swiss.dasch.domain.AssetInfoFileTestHelper.*
import swiss.dasch.domain.AugmentedPath.AssetFolder
import swiss.dasch.test.SpecConfigurations
import swiss.dasch.util.TestUtils
import zio.*
import zio.nio.file.Files
import zio.nio.file.Path
import zio.test.*

import java.nio.file.StandardOpenOption.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class AssetSizeMigrationServiceSpec extends ZIOSpecDefault {

  private val migration = ZIO.service[AssetSizeMigrationService]

  // Original and derivative next to a sidecar that carries no sizes yet.
  private def createAssetWithFiles(origBytes: Int, derivativeBytes: Int) =
    for {
      assetDir <- createInfoFile(originalFileExt = "pdf", derivativeFileExt = "pdf")
      _        <- fillFile(assetDir / s"${assetDir.assetId}.pdf.orig", origBytes)
      _        <- fillFile(assetDir / s"${assetDir.assetId}.pdf", derivativeBytes)
    } yield assetDir

  private def fillFile(path: Path, size: Int) =
    Files.writeBytes(path, Chunk.fill(size)(0.toByte), WRITE, CREATE, TRUNCATE_EXISTING)

  // The group as the migration discovers it: paths read out of the sidecar.
  private def groupOf(assetDir: AssetFolder) =
    AssetInfoService
      .findByAssetRef(assetDir.assetRef)
      .map(_.head)
      .map(info =>
        AssetFileGroup(
          assetDir.assetRef,
          assetDir / s"${assetDir.assetId}.info",
          info.original.file,
          info.derivative.file,
        ),
      )

  /** The asset as [[createAssetWithFiles]] leaves it: both sizes absent until the migration supplies them. */
  private def assetInfoFixture(
    assetDir: AssetFolder,
    sizeOriginal: Option[Long] = None,
    sizeDerivative: Option[Long] = None,
  ) =
    AssetInfo(
      assetRef = assetDir.assetRef,
      original = FileAndChecksum(
        assetDir / s"${assetDir.assetId}.pdf.orig",
        testChecksumOriginal,
        sizeOriginal.map(SizeInBytes.apply),
      ),
      originalFilename = NonEmptyString.unsafeFrom("test.pdf"),
      derivative = FileAndChecksum(
        assetDir / s"${assetDir.assetId}.pdf",
        testChecksumDerivative,
        sizeDerivative.map(SizeInBytes.apply),
      ),
      metadata = OtherMetadata(None, None),
    )

  private val migrateProjectSuite = suite("migrateProject")(
    test("refreshes every asset of a project and reports per-group outcomes") {
      for {
        good <- createAssetWithFiles(origBytes = 33, derivativeBytes = 44)
        // no files on disk — both sizes stay absent, yet the asset counts as updated
        noFiles      <- createInfoFile(originalFileExt = "pdf", derivativeFileExt = "pdf")
        report       <- migration.flatMap(_.migrateProject(testProject))
        after        <- AssetInfoService.findByAssetRef(good.assetRef).map(_.head)
        afterNoFiles <- AssetInfoService.findByAssetRef(noFiles.assetRef).map(_.head)
      } yield assertTrue(
        after == assetInfoFixture(good, sizeOriginal = Some(33L), sizeDerivative = Some(44L)),
        afterNoFiles == assetInfoFixture(noFiles),
        // the store is shared across this spec, so assert on the invariant rather than an exact count
        report.found >= 2,
        report.updated == report.found,
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

  private val findGroupsSuite = suite("findAllGroups")(
    test("finds one group per info file, with the paths the sidecar points at") {
      for {
        assetDir <- createAssetWithFiles(origBytes = 11, derivativeBytes = 22)
        groups   <- migration.flatMap(_.findAllGroups().runCollect)
        // the store also holds checked-in fixtures, so look at this asset's dir only
        found = groups.filter(_.infoFile.parent.contains(assetDir.path)).toList
      } yield assertTrue(
        found == List(
          AssetFileGroup(
            assetDir.assetRef,
            assetDir / s"${assetDir.assetId}.info",
            assetDir / s"${assetDir.assetId}.pdf.orig",
            assetDir / s"${assetDir.assetId}.pdf",
          ),
        ),
      )
    },
  )

  private val refreshGroupSuite = suite("refreshGroup")(
    test("backfills sizeOriginal and sizeDerivative on a sidecar that lacks them") {
      for {
        assetDir <- createAssetWithFiles(origBytes = 11, derivativeBytes = 22)
        before   <- AssetInfoService.findByAssetRef(assetDir.assetRef).map(_.head)
        group     = AssetFileGroup(
                  assetDir.assetRef,
                  assetDir / s"${assetDir.assetId}.info",
                  before.original.file,
                  before.derivative.file,
                )
        _     <- migration.flatMap(_.refreshGroup(group))
        after <- AssetInfoService.findByAssetRef(assetDir.assetRef).map(_.head)
      } yield assertTrue(
        before == assetInfoFixture(assetDir),
        after == assetInfoFixture(assetDir, sizeOriginal = Some(11L), sizeDerivative = Some(22L)),
      )
    },
    test("leaves the derivative size absent when only the original is on disk") {
      for {
        assetDir <- createInfoFile(originalFileExt = "pdf", derivativeFileExt = "pdf")
        _        <- fillFile(assetDir / s"${assetDir.assetId}.pdf.orig", 11)
        group    <- groupOf(assetDir)
        _        <- migration.flatMap(_.refreshGroup(group))
        after    <- AssetInfoService.findByAssetRef(assetDir.assetRef).map(_.head)
      } yield assertTrue(after == assetInfoFixture(assetDir, sizeOriginal = Some(11L)))
    },
    test("leaves both sizes absent when neither file is on disk, and logs each") {
      for {
        assetDir <- createInfoFile(originalFileExt = "pdf", derivativeFileExt = "pdf")
        group    <- groupOf(assetDir)
        _        <- migration.flatMap(_.refreshGroup(group))
        after    <- AssetInfoService.findByAssetRef(assetDir.assetRef).map(_.head)
        warnings <- ZTestLogger.logOutput.map(_.filter(_.logLevel == LogLevel.Warning).map(_.message()))
      } yield assertTrue(
        after == assetInfoFixture(assetDir),
        warnings.exists(_.startsWith(s"No size for original ${assetDir / s"${assetDir.assetId}.pdf.orig"}")),
        warnings.exists(_.startsWith(s"No size for derivative ${assetDir / s"${assetDir.assetId}.pdf"}")),
      )
    },
  )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("AssetSizeMigrationServiceSpec")(
      migrateProjectSuite,
      findGroupsSuite,
      refreshGroupSuite,
    ).provide(
      AssetInfoServiceLive.layer,
      AssetSizeMigrationService.layer,
      FileChecksumServiceLive.layer,
      ProjectRepositoryLive.layer,
      ProjectService.layer,
      StorageServiceLive.layer,
      SpecConfigurations.storageConfigLayer,
      TestUtils.testDbLayerWithEmptyDb,
    )
}
