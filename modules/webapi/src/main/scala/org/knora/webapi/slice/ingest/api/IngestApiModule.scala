/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.api

import zio.*
import zio.metrics.connectors.prometheus.PrometheusPublisher

import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.config.JwtConfig
import org.knora.webapi.slice.ingest.config.IngestFeatures
import org.knora.webapi.slice.ingest.domain.*
import org.knora.webapi.slice.ingest.infrastructure.HealthCheckService
import org.knora.webapi.slice.ingest.infrastructure.IngestExecutor
import org.knora.webapi.slice.ingest.ports.OriginalAccess
import org.knora.webapi.slice.ingest.repo.AssetInfoService
import org.knora.webapi.slice.ingest.repo.StorageService

/** The `OriginalAccess` adapter in `Dependencies` is provided in `LayersLive`. */
object IngestApiModule { self =>

  type Dependencies =
    // format: off
    AssetInfoService &
    AssetMimeTypeMigrationService &
    AssetSizeMigrationService &
    BulkIngestService &
    DspIngestConfig &
    HealthCheckService &
    ImportService &
    IngestExecutor &
    IngestFeatures &
    IngestService &
    JwtConfig &
    MaintenanceActions &
    OriginalAccess &
    PrometheusPublisher &
    ProjectService &
    ReportService &
    StorageService
    // format: on

  type Provided = IngestServerEndpoints

  val layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](
      IngestAuthenticator.layer,
      IngestBaseEndpoints.layer,
      IngestMaintenanceEndpoints.layer,
      IngestMaintenanceRestService.layer,
      IngestMaintenanceServerEndpoints.layer,
      IngestMonitoringEndpoints.layer,
      IngestMonitoringServerEndpoints.layer,
      IngestProjectsEndpoints.layer,
      IngestProjectsRestService.layer,
      IngestProjectsServerEndpoints.layer,
      IngestReportEndpoints.layer,
      IngestReportRestService.layer,
      IngestReportServerEndpoints.layer,
      IngestServerEndpoints.layer,
    )
}
