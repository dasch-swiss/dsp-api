/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.iiif.impl

import zio.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.fair.ports.FairAsset
import org.knora.webapi.slice.fair.ports.FairAssets
import org.knora.webapi.slice.ingest.domain.AssetId as IngestAssetId
import org.knora.webapi.slice.ingest.domain.AssetRef
import org.knora.webapi.slice.ingest.domain.ProjectShortcode as IngestProjectShortcode
import org.knora.webapi.slice.ingest.repo.AssetInfoService

/**
 * Reads an asset's sidecar through the ingest service's asset info for the FAIR landing pages.
 */
final case class FairAssetsLive(assetInfoService: AssetInfoService) extends FairAssets {

  // Never fails: without the shared asset directory every sidecar is missing, and a malformed one is logged and
  // treated the same way, so a landing page still renders.
  override def findSidecar(shortcode: Shortcode, assetId: String): UIO[Option[FairAsset]] =
    ZIO
      .fromEither(for {
        id <- IngestAssetId.from(assetId)
        sc <- IngestProjectShortcode.from(shortcode.value)
      } yield AssetRef(id, sc))
      .mapError(new IllegalArgumentException(_))
      .flatMap(assetInfoService.findByAssetRef)
      .map(_.map { info =>
        FairAsset(
          mimeType = info.metadata.originalMimeType.map(_.value.value),
          fileName = Some(info.originalFilename.value),
          size = info.original.size.map(_.value),
        )
      })
      .catchAll(e => ZIO.logWarning(s"Could not read the sidecar of asset $assetId: ${e.getMessage}").as(None))
}

object FairAssetsLive {
  val layer = ZLayer.derive[FairAssetsLive]
}
