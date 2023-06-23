/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch

import swiss.dasch.version.BuildInfo
import swiss.dasch.api.*
import swiss.dasch.api.healthcheck.*
import swiss.dasch.api.info.InfoEndpoint
import swiss.dasch.config.Configuration
import swiss.dasch.config.Configuration.{ DspIngestApiConfig, JwtConfig, StorageConfig }
import swiss.dasch.domain.{ AssetService, AssetServiceLive }
import swiss.dasch.infrastructure.{ IngestApiServer, Logger }
import zio.*
import zio.config.*
import zio.http.{ Http, HttpApp, Request, Response, Server }

object Main extends ZIOAppDefault {

  override val bootstrap: ULayer[Unit] = Logger.fromEnv()

  override val run: ZIO[Any, Any, Nothing] = IngestApiServer
    .startup()
    .provide(
      HealthCheckServiceLive.layer,
      Configuration.layer,
      IngestApiServer.layer,
      AssetServiceLive.layer,
      AuthenticatorLive.layer,
    )
}
