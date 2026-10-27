/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest

import zio.*

import org.knora.webapi.slice.ingest.config.BulkIngestConfig
import org.knora.webapi.slice.ingest.config.SipiConfig
import org.knora.webapi.slice.ingest.config.StorageConfig
import org.knora.webapi.slice.ingest.domain.*
import org.knora.webapi.slice.ingest.infrastructure.*
import org.knora.webapi.slice.ingest.repo.*

/** The ingest domain, storage and process services. Their one construction, also for the export and FAIR slices. */
object IngestModule { self =>

  type Dependencies = BulkIngestConfig & SipiConfig & StorageConfig

  type Provided =
    // format: off
    AssetInfoService &
    AssetMimeTypeMigrationService &
    AssetSizeMigrationService &
    BulkIngestService &
    FileSystemHealthIndicator &
    HealthCheckService &
    ImportService &
    IngestService &
    MaintenanceActions &
    ProjectService &
    ReportService &
    StorageService
    // format: on

  val layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](
      AssetInfoServiceLive.layer,
      AssetMimeTypeMigrationService.layer,
      AssetSizeMigrationService.layer,
      BulkIngestService.layer,
      CommandExecutorLive.layer,
      CsvService.layer,
      FileChecksumServiceLive.layer,
      FileSystemHealthIndicatorLive.layer,
      HealthCheckServiceLive.layer,
      ImportServiceLive.layer,
      IngestService.layer,
      MaintenanceActionsLive.layer,
      MimeTypeGuesser.layer,
      MovingImageService.layer,
      OtherFilesService.layer,
      ProjectService.layer,
      ReportService.layer,
      SipiClientLive.layer,
      StillImageService.layer,
      StorageServiceLive.layer,
    )
}
