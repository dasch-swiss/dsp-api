/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import eu.timepit.refined.types.string.NonEmptyString
import org.junit.runner.RunWith
import swiss.dasch.config.Configuration.Features
import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.domain.AssetInfo
import swiss.dasch.domain.AssetInfoService
import swiss.dasch.domain.AssetInfoServiceLive
import swiss.dasch.domain.AssetRef
import swiss.dasch.domain.FileAndChecksum
import swiss.dasch.domain.FileChecksumServiceLive
import swiss.dasch.domain.ImportServiceLive
import swiss.dasch.domain.MimeTypeGuesser
import swiss.dasch.domain.OtherMetadata
import swiss.dasch.domain.ProjectService as IngestProjectService
import swiss.dasch.domain.ProjectShortcode
import swiss.dasch.domain.Sha256Hash
import swiss.dasch.domain.StorageService
import swiss.dasch.domain.StorageServiceLive
import zio.*
import zio.nio.file.Files
import zio.test.*
import zio.test.Assertion.*

import dsp.errors.NotFoundException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.api.admin.model.MaintenanceRequests.AssetId

@RunWith(classOf[DspZTestJUnitRunner])
class DspIngestClientLiveSpec extends ZIOSpecDefault {

  private val shortcode  = Shortcode.unsafeFrom("0001")
  private val ingestCode = ProjectShortcode.unsafeFrom("0001")
  private val assetId    = AssetId.unsafeFrom("4sAf4AmPeeg-ZjDn3Tot1Zt")

  private val client = ZIO.serviceWithZIO[DspIngestClient]

  private val storageConfig: ULayer[StorageConfig] = ZLayer.scoped {
    for {
      tmp <- Files.createTempDirectoryScoped(None, List.empty)
      _   <- Files.createDirectories(tmp / "images") *> Files.createDirectories(tmp / "temp")
    } yield StorageConfig((tmp / "images").toString, (tmp / "temp").toString)
  }.orDie

  private def ingestLayer(allowEraseProjects: Boolean) =
    ZLayer.make[DspIngestClient & AssetInfoService & IngestProjectService & StorageService](
      AssetInfoServiceLive.layer,
      DspIngestClientLive.layer,
      FileChecksumServiceLive.layer,
      ImportServiceLive.layer,
      IngestProjectService.layer,
      MimeTypeGuesser.layer,
      StorageServiceLive.layer,
      ZLayer.succeed(Features(allowEraseProjects)),
      storageConfig,
    )

  private val saveAssetInfo = for {
    ref     <- ZIO.succeed(AssetRef(assetId, ingestCode))
    folder  <- ZIO.serviceWithZIO[StorageService](_.getAssetFolder(ref))
    _       <- Files.createDirectories(folder.path)
    checksum = Sha256Hash.unsafeFrom("0" * 64)
    info     = AssetInfo(
             ref,
             FileAndChecksum(folder.path / s"$assetId.txt.orig", checksum),
             NonEmptyString.unsafeFrom("test.txt"),
             FileAndChecksum(folder.path / s"$assetId.txt", checksum),
             OtherMetadata(None, None),
           )
    _ <- ZIO.serviceWithZIO[AssetInfoService](_.save(info))
  } yield ()

  private val projectFolder =
    ZIO.serviceWithZIO[IngestProjectService](_.findOrCreateProject(ingestCode)).map(_.path)

  val spec = suite("DspIngestClientLive")(
    suite("getAssetInfo")(
      test("returns the asset info") {
        for {
          _    <- saveAssetInfo
          info <- client(_.getAssetInfo(shortcode, assetId))
        } yield assertTrue(
          info == AssetInfoResponse(
            internalFilename = s"$assetId.txt",
            originalInternalFilename = s"$assetId.txt.orig",
            originalFilename = "test.txt",
            checksumOriginal = "0" * 64,
            checksumDerivative = "0" * 64,
          ),
        )
      },
      test("fails with NotFoundException for an unknown asset") {
        client(_.getAssetInfo(shortcode, assetId)).exit.map(assert(_)(failsWithA[NotFoundException]))
      },
    ).provide(ingestLayer(allowEraseProjects = false)),
    suite("exportProject")(
      test("writes the project's BagIt zip to the output file") {
        for {
          folder <- projectFolder
          _      <- Files.writeBytes(folder / "asset.txt", Chunk.fromArray("content".getBytes))
          out    <- Files.createTempFileScoped(".zip")
          result <- client(_.exportProject(shortcode, out))
          size   <- Files.size(out)
        } yield assertTrue(result.contains(out), size > 0L)
      },
      test("returns None without a project folder") {
        for {
          out    <- Files.createTempFileScoped(".zip")
          result <- client(_.exportProject(shortcode, out))
        } yield assertTrue(result.isEmpty)
      },
    ).provide(ingestLayer(allowEraseProjects = false), Scope.default),
    suite("eraseProject")(
      test("keeps the project folder while allow-erase-projects is off") {
        for {
          folder <- projectFolder
          _      <- client(_.eraseProject(shortcode))
          kept   <- Files.exists(folder)
        } yield assertTrue(kept)
      },
    ).provide(ingestLayer(allowEraseProjects = false)),
    suite("importProject")(
      test("logs a failing import and returns the imported file") {
        for {
          file   <- Files.createTempFileScoped(".zip")
          _      <- Files.writeBytes(file, Chunk.fromArray("not a zip".getBytes))
          result <- client(_.importProject(shortcode, file))
          logs   <- ZTestLogger.logOutput
        } yield assertTrue(result == file, logs.exists(_.logLevel == LogLevel.Error))
      },
    ).provide(ingestLayer(allowEraseProjects = false), Scope.default),
  )
}
