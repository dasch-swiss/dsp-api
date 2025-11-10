/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api
import zio.*
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.api.v3.export_.ExportRequest
import org.knora.webapi.testservices.ResponseOps.*
import org.knora.webapi.testservices.TestExportApiClient

object ExportEndpointsE2ESpec extends E2EZSpec with GoldenTest {
  override def rdfDataObjects: List[RdfDataObject] = incunabulaRdfOntologyAndData
  // override val rewriteAll: Boolean                 = true

  val e2eSpec = suite("ExportEndpointsE2ESpec")(
    test("postExportResources should export CSV resources") {
      val exportResource =
        ExportRequest(
          "http://www.knora.org/ontology/0803/incunabula#book",
          List(
            "http://www.knora.org/ontology/0803/incunabula#title",
            "http://www.knora.org/ontology/0803/incunabula#publisher",
            "http://www.knora.org/ontology/0803/incunabula#partOf",
          ),
        )

      TestExportApiClient
        .postExportResources(exportResource, anythingAdminUser)
        .flatMap(_.assert200)
        .map(assertGolden(_, ""))
    },
  )
}
