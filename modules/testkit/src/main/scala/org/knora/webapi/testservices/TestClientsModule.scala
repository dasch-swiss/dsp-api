/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testservices
import sttp.client4.httpclient.zio.HttpClientZioBackend
import zio.URLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.config.Sipi
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.slice.security.Authenticator
import org.knora.webapi.slice.security.ScopeResolver
import org.knora.webapi.util.TestDataFileUtil

object TestClientsModule { self =>

  type Provided =
    // format: off
    TestAdminApiClient &
    TestApiClient &
    TestDataFileUtil &
    TestDspIngestClient &
    TestMetadataApiClient &
    TestOntologyApiClient &
    TestResourcesApiClient &
    TestSipiApiClient
    // format: on

  type Dependencies =
    // format: off
    AppConfig &
    Authenticator &
    DspIngestConfig &
    JwtService &
    KnoraApi &
    Sipi &
    ScopeResolver
    // format: on

  val layer: URLayer[self.Dependencies, self.Provided] =
    HttpClientZioBackend.layer().orDie >>> (
      TestApiClient.layer >+>
        TestAdminApiClient.layer ++
        TestDataFileUtil.layer ++
        TestDspIngestClient.layer ++
        TestMetadataApiClient.layer ++
        TestOntologyApiClient.layer ++
        TestResourcesApiClient.layer ++
        TestSipiApiClient.layer
    )
}
