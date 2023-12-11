/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch

import swiss.dasch.api.*
import swiss.dasch.config.Configuration
import swiss.dasch.config.Configuration.{JwtConfig, ServiceConfig, StorageConfig}
import swiss.dasch.domain.*
import swiss.dasch.infrastructure.*
import zio.*
import zio.config.*
import zio.http.*

import java.io.IOException

object Main extends ZIOAppDefault {

  override val bootstrap: Layer[ReadError[String], ServiceConfig with JwtConfig with StorageConfig] =
    Configuration.layer >+> Logger.layer

  override val run: ZIO[Any, Any, Nothing] =
    (FileSystemCheck.smokeTestOrDie() *>
      IngestApiServer.startup() *>
      ZIO.never)
      .provide(
        AssetInfoServiceLive.layer,
        AuthServiceLive.layer,
        BaseEndpoints.layer,
        BulkIngestServiceLive.layer,
        CommandExecutorLive.layer,
        Configuration.layer,
        Endpoints.layer,
        FileChecksumServiceLive.layer,
        FileSystemCheckLive.layer,
        HealthCheckServiceLive.layer,
        ImportServiceLive.layer,
        IngestApiServer.layer,
        IngestService.layer,
        MaintenanceActionsLive.layer,
        MaintenanceEndpoints.layer,
        MaintenanceEndpointsHandler.layer,
        Metrics.layer,
        MonitoringEndpoints.layer,
        MonitoringEndpointsHandler.layer,
        MovingImageService.layer,
        ProjectServiceLive.layer,
        ProjectsEndpoints.layer,
        ProjectsEndpointsHandler.layer,
        ReportServiceLive.layer,
        SipiClientLive.layer,
        StillImageServiceLive.layer,
        StorageServiceLive.layer
//        ZLayer.Debug.mermaid ,
      )
}
