/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.fair

import swiss.dasch.domain.AssetId as IngestAssetId
import swiss.dasch.domain.AssetInfo
import swiss.dasch.domain.AssetInfoService
import swiss.dasch.domain.AssetRef
import swiss.dasch.domain.ProjectShortcode as IngestProjectShortcode
import zio.*

import java.time.Instant

import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.v2.responder.valuemessages.FileValueContentV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.api.v3.`export`.FileLink

final case class AssetDownloadLinks(appConfig: AppConfig, assetInfoService: AssetInfoService) {

  // the sidecar only ever stores SHA-256, so this is a constant rather than a field
  private val ChecksumAlgorithm = "SHA-256"

  // The direct link points at the dsp-ingest "original" download endpoint, addressed by the asset id.
  def linkOf(project: KnoraProject, fileValue: FileValueContentV2, resourceCreationDate: Instant): UIO[FileLink] = {
    val assetId = fileValue.fileValue.internalFilename.takeWhile(_ != '.')
    findAssetInfo(project, assetId).map { info =>
      FileLink(
        // never falls back to the derivative's internalMimeType: the url serves the original
        mimeType = info.flatMap(_.metadata.originalMimeType.map(_.value.value)),
        url = s"${appConfig.dspIngest.externalBaseUrl}/projects/${project.shortcode.value}/assets/$assetId/original",
        checksum = info.map(_.original.checksum.value),
        checksumAlgorithm = info.map(_ => ChecksumAlgorithm),
        fileName = info.map(_.originalFilename.value),
        fileSize = info.flatMap(_.original.size.map(_.value)),
        // the resource's date: there is no per-asset timestamp in the sidecar or in ingest's DB
        dateCreated = Some(resourceCreationDate.toString),
      )
    }
  }

  // Never fails: without the shared asset dir every asset is missing a sidecar, and failing there would
  // make the endpoint unusable. A malformed sidecar is logged and treated the same way.
  private def findAssetInfo(project: KnoraProject, assetId: String): UIO[Option[AssetInfo]] =
    ZIO
      .fromEither(for {
        id        <- IngestAssetId.from(assetId)
        shortcode <- IngestProjectShortcode.from(project.shortcode.value)
      } yield AssetRef(id, shortcode))
      .mapError(new IllegalArgumentException(_))
      .flatMap(assetInfoService.findByAssetRef)
      .catchAll(e => ZIO.logWarning(s"Could not read the sidecar of asset $assetId: ${e.getMessage}").as(None))
}

object AssetDownloadLinks {
  val layer: URLayer[AppConfig & AssetInfoService, AssetDownloadLinks] = ZLayer.derive[AssetDownloadLinks]
}
