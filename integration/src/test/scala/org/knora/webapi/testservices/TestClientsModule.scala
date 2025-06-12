/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testservices
import org.apache.pekko.actor.ActorSystem
import sttp.client4.httpclient.zio.HttpClientZioBackend
import zio.URLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.slice.security.ScopeResolver

object TestClientsModule { self =>

  type Provided = TestAdminApiClient & TestApiClient & TestClientService & TestDspIngestClient & TestOntologyApiClient

  type Dependencies = ActorSystem & AppConfig & DspIngestConfig & JwtService & KnoraApi & ScopeResolver

  val layer: URLayer[self.Dependencies, self.Provided] =
    HttpClientZioBackend.layer().orDie >>> (
      TestApiClient.layer >+>
        TestAdminApiClient.layer ++
        TestDspIngestClient.layer ++
        TestClientService.layer ++
        TestOntologyApiClient.layer
    )
}
