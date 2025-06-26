/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.it.v2

import sttp.client4.*
import sttp.model.*
import zio.*
import zio.test.*

import org.knora.webapi.*
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.testservices.TestSipiApiClient

/**
 * Tests interaction between Knora and Sipi using Knora API v2.
 */
object KnoraSipiAuthenticationE2ESpec extends E2EZSpec {

  override lazy val rdfDataObjects: List[RdfDataObject] = List(anythingRdfData)

  override val e2eSpec = suite("The Knora/Sipi authentication")(
    test("successfully get an image with provided credentials with jwt in the cookie") {
      TestSipiApiClient
        .getImage(anythingShortcode, "B1D0OkEgfFp-Cew2Seur7Wi.jp2", anythingAdminUser)
        .map(response => assertTrue(response.code == StatusCode.Ok))
    },
  )
}
