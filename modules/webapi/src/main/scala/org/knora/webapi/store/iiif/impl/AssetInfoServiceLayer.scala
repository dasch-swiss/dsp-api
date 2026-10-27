/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.iiif.impl

import zio.URLayer
import zio.ZLayer

import org.knora.webapi.slice.ingest.config.StorageConfig
import org.knora.webapi.slice.ingest.domain.MimeTypeGuesser
import org.knora.webapi.slice.ingest.repo.AssetInfoService
import org.knora.webapi.slice.ingest.repo.AssetInfoServiceLive
import org.knora.webapi.slice.ingest.repo.StorageServiceLive

/** The one construction of the ingest service's [[AssetInfoService]], shared by the export and FAIR slices. */
object AssetInfoServiceLayer {

  val layer: URLayer[StorageConfig, AssetInfoService] =
    (StorageServiceLive.layer ++ MimeTypeGuesser.layer) >>> ZLayer.derive[AssetInfoServiceLive]
}
