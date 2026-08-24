/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.api

import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.domain.AssetInfoService
import swiss.dasch.domain.AssetInfoServiceLive
import swiss.dasch.domain.StorageServiceLive
import zio.URLayer
import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.api.v3.export_.ExportService
import org.knora.webapi.slice.api.v3.export_.FindResourcesService
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.CsvService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resources.service.ReadResourcesService
import org.knora.webapi.store.triplestore.api.TriplestoreService

object ExportApiModule { self =>
  type Dependencies =
    // format: off
    AppConfig &
    ConstructResponseUtilV2 &
    CsvService &
    IriConverter &
    KnoraProjectService &
    ListsResponder &
    OntologyRepo &
    ReadResourcesService &
    StringFormatter &
    TriplestoreService
    // format: on

  type Provided =
    // format: off
    ExportService
    // format: on

  // Built from webapi's own config on purpose: ingest's Configuration.layer calls
  // ConfigFactory.defaultApplication(), which on a shared classpath reads webapi's application.conf.
  // tempDir is unused for reads.
  private val assetInfoServiceLayer: URLayer[AppConfig, AssetInfoService] =
    ZLayer
      .service[AppConfig]
      .project(c => StorageConfig(assetDir = c.dspIngest.assetDir, tempDir = c.dspIngest.assetDir)) >>>
      StorageServiceLive.layer >>> ZLayer.derive[AssetInfoServiceLive]

  val layer: URLayer[self.Dependencies, self.Provided] =
    (FindResourcesService.layer ++ assetInfoServiceLayer) >+> ExportService.layer
}
