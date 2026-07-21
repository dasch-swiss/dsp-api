/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.GoldenTest
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.api.v3.`export`.ExportRequest
import org.knora.webapi.testservices.ResponseOps.*
import org.knora.webapi.testservices.TestExportApiClient

@RunWith(classOf[DspZTestJUnitRunner])
class ExportEndpointsE2ESpec extends E2EZSpec with GoldenTest {
  override def rdfDataObjects: List[RdfDataObject] = incunabulaRdfOntologyAndData
  // override val rewriteAll: Boolean                 = true

  val request =
    ExportRequest(
      "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book", // this IRI should be converted to an internal schema
      List(
        "http://0.0.0.0:3333/ontology/0803/incunabula/v2#title",
        "http://0.0.0.0:3333/ontology/0803/incunabula/v2#publisher",
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
          .postExportResources(request.copy(includeIris = true), anythingAdminUser)
          .flatMap(_.assert200)
          .map(assertGolden(_, "withResourceIri"))
      },
      test("with ARK URLs included") {
        TestExportApiClient
          .postExportResources(request.copy(includeArkUrls = true), anythingAdminUser)
          .flatMap(_.assert200)
          .map(assertGolden(_, "withArkUrls"))
      },
      test("streams the response chunked (no buffered Content-Length)") {
        // Proves the endpoint streams rather than buffers: a chunked response carries no Content-Length, whereas
        // the pre-streaming buffered body set one. This is the only assertion that exercises REQ-1.1 on the wire.
        TestExportApiClient
          .postExportResources(request, anythingAdminUser)
          .map { response =>
            val transferEncoding = response.header("Transfer-Encoding")
            val contentLength    = response.header("Content-Length")
            assertTrue(transferEncoding.exists(_.toLowerCase.contains("chunked")) || contentLength.isEmpty)
          }
      },
    ),
  )
}
