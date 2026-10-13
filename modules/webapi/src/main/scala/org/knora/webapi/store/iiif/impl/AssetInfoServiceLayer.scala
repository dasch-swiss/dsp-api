/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.iiif.impl

import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.domain.AssetInfoService
import swiss.dasch.domain.AssetInfoServiceLive
import swiss.dasch.domain.MimeTypeGuesser
import swiss.dasch.domain.StorageServiceLive
import zio.URLayer
import zio.ZLayer

import org.knora.webapi.config.AppConfig

/** The one construction of the ingest service's [[AssetInfoService]], shared by the export and FAIR slices. */
object AssetInfoServiceLayer {

  // Built from webapi's own config on purpose: ingest's Configuration.layer calls
  // ConfigFactory.defaultApplication(), which on a shared classpath reads webapi's application.conf.
  // tempDir is unused for reads.
  val layer: URLayer[AppConfig, AssetInfoService] =
    ZLayer
      .service[AppConfig]
      .project(c => StorageConfig(assetDir = c.dspIngest.assetDir, tempDir = c.dspIngest.assetDir)) >>>
      (StorageServiceLive.layer ++ MimeTypeGuesser.layer) >>> ZLayer.derive[AssetInfoServiceLive]
}
