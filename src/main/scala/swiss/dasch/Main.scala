/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch

import swiss.dasch.api.*
import swiss.dasch.api.monitoring.*
import swiss.dasch.config.Configuration
import swiss.dasch.config.Configuration.{ JwtConfig, ServiceConfig, StorageConfig }
import swiss.dasch.domain.{ AssetService, AssetServiceLive }
import swiss.dasch.infrastructure.{ FileSystemCheck, FileSystemCheckLive, IngestApiServer, Logger }
import swiss.dasch.version.BuildInfo
import zio.*
import zio.config.*
import zio.http.*

import java.io.IOException

object Main extends ZIOAppDefault {

  override val bootstrap: Layer[ReadError[String], ServiceConfig with JwtConfig with StorageConfig] =
    Configuration.layer >+> Logger.layer

  private val ensureFilesystem = FileSystemCheck.smokeTestOrDie() *> FileSystemCheck.createTempFolders()

  override val run: ZIO[Any, Any, Nothing] =
    (ensureFilesystem *> IngestApiServer.startup())
      .provide(
        AssetServiceLive.layer,
        AuthenticatorLive.layer,
        Configuration.layer,
        FileSystemCheckLive.layer,
        HealthCheckServiceLive.layer,
        IngestApiServer.layer,
        Metrics.layer,
      )
}
