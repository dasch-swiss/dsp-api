/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testcontainers

import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import zio.URLayer
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.testcontainers.TestContainerOps.toZio

final class DspIngestTestContainer extends GenericContainer[DspIngestTestContainer](s"daschswiss/dsp-ingest:latest")

object DspIngestTestContainer {

  private val assetDir = "/opt/images"
  private val tempDir  = "/opt/temp"

  def make(imagesVolume: SharedVolumes.Images, tempVolume: SharedVolumes.Temp): DspIngestTestContainer = {
    val port = 3340
    new DspIngestTestContainer()
      .withExposedPorts(port)
      .withEnv("SERVICE_PORT", s"$port")
      .withEnv("SERVICE_LOG_FORMAT", "text")
      .withEnv("JWT_AUDIENCE", s"http://localhost:$port")
      .withEnv("JWT_ISSUER", "0.0.0.0:3333")
      .withEnv("STORAGE_ASSET_DIR", assetDir)
      .withEnv("STORAGE_TEMP_DIR", tempDir)
      .withEnv("JWT_SECRET", "UP 4888, nice 4-8-4 steam engine")
      .withEnv("SIPI_USE_LOCAL_DEV", "false")
      .withEnv("JWT_DISABLE_AUTH", "true")
      .withEnv("DB_JDBC_URL", "jdbc:sqlite:/tmp/ingest.sqlite")
      .withFileSystemBind(imagesVolume.hostPath, assetDir, BindMode.READ_WRITE)
      .withFileSystemBind(tempVolume.hostPath, tempDir, BindMode.READ_WRITE)
      .withLogConsumer(frame => print("DSP-INGEST:" + frame.getUtf8String))
  }

  val layer: URLayer[SharedVolumes.Volumes, DspIngestTestContainer] =
    ZLayer.scoped(for {
      imagesVolume <- ZIO.service[SharedVolumes.Images]
      tmpVolume    <- ZIO.service[SharedVolumes.Temp]
      container    <- make(imagesVolume, tmpVolume).toZio
    } yield container)
}
