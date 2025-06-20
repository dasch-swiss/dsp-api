/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure.api

import sttp.client4.*
import sttp.model.*
import zio.*
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.core.State
import org.knora.webapi.core.domain.AppState
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient

object ManagementEndpointsE2ESpec extends E2EZSpec {

  // load just a single small set even though it is not used.
  override def rdfDataObjects: List[RdfDataObject] = List(anythingRdfData)

  private val getVersion = TestApiClient.getJson[VersionResponse](uri"/version")
  private val getHealth  = TestApiClient.getJson[HealthResponse](uri"/health")

  private val versionSuite = suite("The Version Route")(
    test("return 'OK'") {
      getVersion.map(r => assertTrue(r.code == StatusCode.Ok))
    },
    test("contain nonempty value for key 'webapi'") {
      getVersion.flatMap(_.assert200).map(resp => assertTrue(resp == VersionResponse.current))
    },
  )

  private val healthSuite = {
    def setState(s: AppState) = ZIO.serviceWithZIO[State](_.set(s))
    suite("The Health Route")(
      test("return 'OK' for state 'Running'") {
        setState(AppState.Running) *>
          getHealth.map(r => assertTrue(r.code == StatusCode.Ok))
      },
      test("return 'ServiceUnavailable' for state 'Stopped'") {
        setState(AppState.Stopped) *>
          getHealth.map(r => assertTrue(r.code == StatusCode.ServiceUnavailable))
      },
      test("return 'ServiceUnavailable' for state 'MaintenanceMode'") {
        setState(AppState.MaintenanceMode) *>
          getHealth.map(r => assertTrue(r.code == StatusCode.ServiceUnavailable))
      },
    )
  }
  override val e2eSpec = suite("Health and Version Endpoints E2E")(versionSuite, healthSuite)
}
