/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch

import swiss.dasch.api.*
import swiss.dasch.config.Configuration
import swiss.dasch.config.Configuration.{ JwtConfig, ServiceConfig, StorageConfig }
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
        BaseEndpoints.layer,
        MonitoringEndpoints.layer,
        MonitoringEndpointsHandler.layer,
        Endpoints.layer,
        AssetInfoServiceLive.layer,
        AuthServiceLive.layer,
        BulkIngestServiceLive.layer,
        Configuration.layer,
        FileChecksumServiceLive.layer,
        FileSystemCheckLive.layer,
        HealthCheckServiceLive.layer,
        ImportServiceLive.layer,
        ImageServiceLive.layer,
        IngestApiServer.layer,
        MaintenanceEndpoints.layer,
        MaintenanceEndpointsHandler.layer,
        MaintenanceActionsLive.layer,
        Metrics.layer,
        ProjectsEndpoints.layer,
        ProjectsEndpointsHandler.layer,
        ProjectServiceLive.layer,
        ReportServiceLive.layer,
        SipiClientLive.layer,
        StorageServiceLive.layer,
//        ZLayer.Debug.mermaid ,
      )
}
