/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Scope
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.nio.file.Path

import org.knora.webapi.slice.admin.api.model.MaintenanceRequests.AssetId
import org.knora.webapi.slice.admin.domain.model.KnoraProject

final case class DspIngestClientMock() extends DspIngestClient {
  override def exportProject(shortcode: KnoraProject.Shortcode): ZIO[Scope, Throwable, Path] =
    ZIO.succeed(Path("/tmp/test.zip"))
  override def importProject(shortcode: KnoraProject.Shortcode, fileToImport: Path): Task[Path] =
    ZIO.succeed(Path("/tmp/test.zip"))

  override def getAssetInfo(shortcode: KnoraProject.Shortcode, assetId: AssetId): Task[AssetInfoResponse] =
    ZIO.succeed(
      AssetInfoResponse(
        s"$assetId.txt",
        s"$assetId.txt.orig",
        "test.txt",
        "bfd3192ea04d5f42d79836cf3b8fbf17007bab71",
        "17bab70071fbf8b3fc63897d24f5d40ae2913dfb",
        internalMimeType = Some("text/plain"),
        originalMimeType = Some("text/plain"),
      ),
    )
}

object DspIngestClientMock {
  val layer = ZLayer.derive[DspIngestClientMock]
}
