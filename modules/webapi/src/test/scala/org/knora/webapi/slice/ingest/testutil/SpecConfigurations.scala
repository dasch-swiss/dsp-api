/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.testutil

import org.apache.commons.io.FileUtils
import zio.Layer
import zio.ULayer
import zio.ZIO
import zio.ZLayer
import zio.durationInt
import zio.nio.file.Files

import java.io.IOException

import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.config.JwtConfig
import org.knora.webapi.slice.ingest.config.BulkIngestConfig
import org.knora.webapi.slice.ingest.config.SipiConfig
import org.knora.webapi.slice.ingest.config.StorageConfig
import org.knora.webapi.slice.ingest.infrastructure.IngestExecutor

object SpecConfigurations {

  val storageConfigLayer: Layer[IOException, StorageConfig] = ZLayer.scoped {
    for {
      tmpDir  <- Files.createTempDirectoryScoped(None, List.empty)
      assetDir = tmpDir / "asset"
      tempDir  = tmpDir / "temp"
      _       <- Files.createDirectories(assetDir)
      _       <- Files.createDirectories(tempDir)
      _       <- ZIO.attemptBlockingIO(FileUtils.copyDirectory(SpecPaths.testFolder.toFile, assetDir.toFile))
    } yield StorageConfig(assetDir.toString, tempDir.toString)
  }

  val ingestConfigLayer = ZLayer.succeed(BulkIngestConfig(2))

  val ingestExecutorLayer: ULayer[IngestExecutor] = ZLayer.scoped(IngestExecutor.make(2))

  val jwtConfig: JwtConfig =
    JwtConfig("secret-key", 1.day, Some("https://admin.dev.dasch.swiss"))

  val dspIngestConfig: DspIngestConfig =
    DspIngestConfig("http://localhost:3340", "http://localhost:3340", "https://dsp-ingest.dev.dasch.swiss", "")

  val jwtConfigLayer: ULayer[JwtConfig & DspIngestConfig] =
    ZLayer.succeed(jwtConfig) ++ ZLayer.succeed(dspIngestConfig)

  val sipiConfigLayer = ZLayer.succeed(SipiConfig(useLocalDev = true, localDevImage = "daschswiss/knora-sipi:latest"))
}
