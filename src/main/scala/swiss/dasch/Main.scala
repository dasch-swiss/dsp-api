/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch

import swiss.dasch.api.*
import swiss.dasch.config.Configuration
import swiss.dasch.config.Configuration.{JwtConfig, ServiceConfig, StorageConfig}
import swiss.dasch.domain.*
import swiss.dasch.infrastructure.*
import zio.*
import zio.http.*

import java.io.IOException

object Main extends ZIOAppDefault {

  override val bootstrap: Layer[Config.Error, ServiceConfig with JwtConfig with StorageConfig] =
    Configuration.layer >+> Logger.layer

  override val run: ZIO[Any, Any, Nothing] =
    (FileSystemCheck.smokeTestOrDie() *>
      IngestApiServer.startup() *>
      ZIO.never)
      .provide(
        AssetInfoServiceLive.layer,
        AuthServiceLive.layer,
        BaseEndpoints.layer,
        BulkIngestService.layer,
        CommandExecutorLive.layer,
        Configuration.layer,
        CsvService.layer,
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
        MimeTypeGuesser.layer,
        MonitoringEndpoints.layer,
        MonitoringEndpointsHandler.layer,
        MovingImageService.layer,
        OtherFilesService.layer,
        ProjectService.layer,
        ProjectsEndpoints.layer,
        ProjectsEndpointsHandler.layer,
        ReportEndpoints.layer,
        ReportEndpointsHandler.layer,
        ReportService.layer,
        SipiClientLive.layer,
        StillImageService.layer,
        StorageServiceLive.layer,
//        ZLayer.Debug.mermaid ,
      )
}
