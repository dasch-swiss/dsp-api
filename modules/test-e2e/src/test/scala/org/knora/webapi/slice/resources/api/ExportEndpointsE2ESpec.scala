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

  val request =
    ExportRequest(
      "http://api.knora.org/ontology/0803/incunabula#book", // this IRI should be converted to an internal schema
      List(
        "http://api.knora.org/ontology/0803/incunabula#title",
        "http://api.knora.org/ontology/0803/incunabula#publisher",
        "http://www.knora.org/ontology/0803/incunabula#hasAuthor", // this IRI schema shouldn't pose a problem to the handler
      ),
    )

  val e2eSpec = suite("ExportEndpointsE2ESpec")(
    suite("postExportResources should export CSV resources")(
      test("simply") {
        TestExportApiClient
          .postExportResources(request, anythingAdminUser)
          .flatMap(_.assert200)
          .map(assertGolden(_, ""))
      },
      test("with resource IRI included") {
        TestExportApiClient
          .postExportResources(request.copy(includeResourceIri = true), anythingAdminUser)
          .flatMap(_.assert200)
          .map(assertGolden(_, "withResourceIri"))
      },
    ),
  )
}
