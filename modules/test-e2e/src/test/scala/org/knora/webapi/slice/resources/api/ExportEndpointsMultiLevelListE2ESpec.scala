/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api

import zio.*
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.api.v3.`export`.ExportRequest
import org.knora.webapi.testservices.ResponseOps.*
import org.knora.webapi.testservices.TestExportApiClient

object ExportEndpointsMultiLevelListE2ESpec extends E2EZSpec with GoldenTest {
  override def rdfDataObjects: List[RdfDataObject] = anythingRdfOntologyAndData
  // override val rewriteAll: Boolean                 = true

  val request =
    ExportRequest(
      "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing",
      List("http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem"),
    )

  val e2eSpec = suite("ExportEndpointsMultiLevelListE2ESpec")(
    suite("postExportResources should export CSV with multi-level list labels")(
      test("includes labels for depth-2 list nodes") {
        TestExportApiClient
          .postExportResources(request, anythingAdminUser)
          .flatMap(_.assert200)
          .map(assertGolden(_, ""))
      },
    ),
  )
}
