/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch

import swiss.dasch.api.*
import swiss.dasch.config.Configuration
import swiss.dasch.config.Configuration.{JwtConfig, ServiceConfig, StorageConfig}
import swiss.dasch.db.{Db, DbMigrator}
import swiss.dasch.domain.*
import swiss.dasch.infrastructure.*
import zio.*
import zio.http.*

object Main extends ZIOAppDefault {

  override val bootstrap: Layer[Config.Error, ServiceConfig with JwtConfig with StorageConfig] =
    Configuration.layer >+> Logger.layer

  override val run: ZIO[Any, Any, Nothing] =
    (FileSystemHealthIndicator.smokeTestOrDie() *>
      DbMigrator.migrateOrDie() *>
      IngestApiServer.startup() *>
      ZIO.never)
      .provide(
        AssetInfoServiceLive.layer,
        AuthServiceLive.layer,
        AuthorizationHandlerLive.layer,
        BaseEndpoints.layer,
        BulkIngestService.layer,
        CommandExecutorLive.layer,
        Configuration.layer,
        CsvService.layer,
        Db.dataSourceLive,
        Db.quillLive,
        DbHealthIndicator.layer,
        DbMigrator.layer,
        Endpoints.layer,
        FetchAssetPermissions.layer,
        FileChecksumServiceLive.layer,
        FileSystemHealthIndicatorLive.layer,
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
        ProjectRepositoryLive.layer,
        ProjectService.layer,
        ProjectsEndpoints.layer,
        ProjectsEndpointsHandler.layer,
        ReportEndpoints.layer,
        ReportEndpointsHandler.layer,
        ReportService.layer,
        SipiClientLive.layer,
        StillImageService.layer,
        StorageServiceLive.layer,
        // ZLayer.Debug.mermaid,
      )
}
