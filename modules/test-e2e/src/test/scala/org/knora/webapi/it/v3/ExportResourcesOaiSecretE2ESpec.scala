/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.it.v3

import sttp.client4.*
import sttp.model.StatusCode
import zio.*
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.incunabulaRdfOntologyAndData
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.api.v3.`export`.ExportRequestOai
import org.knora.webapi.testservices.TestApiClient

/**
 * Verifies that the shared-secret bearer configured via `app.knora-api.oai-export-secret`
 * is honored by `POST /v3/export/resources/oai`.
 */
object ExportResourcesOaiSecretE2ESpec extends E2EZSpec {

  override def rdfDataObjects: List[RdfDataObject] = incunabulaRdfOntologyAndData

  private val request = ExportRequestOai(shortcode = Shortcode.unsafeFrom("0803"))
  private val uri     = uri"/v3/export/resources/oai"

  override val e2eSpec = suite("POST /v3/export/resources/oai with shared secret")(
    test("with the configured secret as Bearer token, returns 200") {
      for {
        secret   <- ZIO.serviceWith[KnoraApi](_.oaiExportSecret)
        response <- ZIO.serviceWithZIO[TestApiClient](
                      _.postJsonReceiveStringF(uri, request, _.auth.bearer(secret)),
                    )
      } yield assertTrue(response.code == StatusCode.Ok)
    },
    test("with a wrong Bearer token, returns 401 Unauthorized") {
      for {
        response <- ZIO.serviceWithZIO[TestApiClient](
                      _.postJsonReceiveStringF(uri, request, _.auth.bearer("not-the-secret")),
                    )
      } yield assertTrue(response.code == StatusCode.Unauthorized)
    },
  )
}
