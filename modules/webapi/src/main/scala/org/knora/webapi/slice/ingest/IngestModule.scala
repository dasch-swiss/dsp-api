/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest

import swiss.dasch.api.*
import swiss.dasch.config.Configuration.ExecutorConfig
import swiss.dasch.config.Configuration.Features
import swiss.dasch.config.Configuration.IngestConfig
import swiss.dasch.config.Configuration.JwtConfig
import swiss.dasch.config.Configuration.SipiConfig
import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.domain.*
import swiss.dasch.infrastructure.*
import zio.*
import zio.metrics.connectors.prometheus.PrometheusPublisher

import org.knora.webapi.config.AppConfig
import org.knora.webapi.responders.admin.AssetPermissionsCache
import org.knora.webapi.slice.admin.domain.service.DspIngestClient
import org.knora.webapi.slice.admin.domain.service.DspIngestClientLive
import org.knora.webapi.slice.admin.domain.service.IngestFetchAssetPermissions
import org.knora.webapi.slice.security.Authenticator

/**
 * Builds ingest from its own layers and exposes only the three types dsp-api uses; ingest's other services stay out
 * of dsp-api's environment. Ingest accepts dsp-api's tokens: its token check reads dsp-api's secret and issuer and
 * the audience dsp-api mints for ingest.
 *
 * Two layers, because the admin services need [[DspIngestClient]] while the endpoints need the admin services'
 * permission cache and authenticator. Both share one set of ingest services through [[IngestModule.Internals]].
 */
object IngestModule {

  type Dependencies =
    AppConfig & PrometheusPublisher & StorageConfig & SipiConfig & IngestConfig & Features & ExecutorConfig

  type Provided = AssetInfoService & DspIngestClient & Internals

  private type Services =
    // format: off
    AssetInfoService &
    AuthorizationHandler &
    BulkIngestService &
    Features &
    ImportService &
    IngestService &
    MaintenanceEndpointsHandler &
    MonitoringEndpointsHandler &
    ProjectService &
    ProjectsEndpoints &
    ReportEndpointsHandler &
    ReportService &
    StorageService
    // format: on

  final class Internals private[IngestModule] (private[IngestModule] val services: ZEnvironment[Services])

  private val jwtConfig: URLayer[AppConfig, JwtConfig] =
    ZLayer.fromFunction((c: AppConfig) => JwtConfig(c.jwt.secret, c.dspIngest.audience, c.jwt.issuerAsString()))

  private val services: URLayer[Dependencies, Services] = ZLayer.makeSome[Dependencies, Services](
    AssetInfoServiceLive.layer,
    AuthServiceLive.layer,
    AuthorizationHandlerLive.layer.mapError(e => Exception(s"$e")).orDie,
    BaseEndpoints.layer,
    BulkIngestService.layer,
    CommandExecutorLive.layer,
    CsvService.layer,
    FileChecksumServiceLive.layer,
    FileSystemHealthIndicatorLive.layer,
    HealthCheckServiceLive.layer,
    ImportServiceLive.layer,
    IngestExecutor.layer,
    IngestService.layer,
    jwtConfig,
    MaintenanceActionsLive.layer,
    MaintenanceEndpoints.layer,
    MaintenanceEndpointsHandler.layer,
    MimeTypeGuesser.layer,
    MonitoringEndpoints.layer,
    MonitoringEndpointsHandler.layer,
    MovingImageService.layer,
    OtherFilesService.layer,
    ProjectService.layer,
    ProjectsEndpoints.layer,
    ReportEndpoints.layer,
    ReportEndpointsHandler.layer,
    ReportService.layer,
    SipiClientLive.layer,
    StillImageService.layer,
    StorageServiceLive.layer,
  )

  val layer: URLayer[Dependencies, Provided] =
    services >>> (
      ZLayer.service[AssetInfoService] ++
        DspIngestClientLive.layer ++
        ZLayer.fromZIO(ZIO.environment[Services].map(Internals(_)))
    )

  val endpointsLayer: URLayer[Internals & AssetPermissionsCache & Authenticator, IngestServerEndpoints] =
    ZLayer.service[Internals].flatMap { internals =>
      ZLayer.makeSome[AssetPermissionsCache & Authenticator, IngestServerEndpoints](
        ZLayer.succeedEnvironment(internals.get.services),
        IngestFetchAssetPermissions.layer,
        ProjectsEndpointsHandler.layer,
        IngestServerEndpoints.layer,
      )
    }
}
