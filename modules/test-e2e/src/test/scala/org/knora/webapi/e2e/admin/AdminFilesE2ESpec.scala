/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.admin

import sttp.client4.*
import sttp.model.*
import zio.test.assertTrue

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.api.admin.model.PermissionCodeAndProjectRestrictedViewSettings
import org.knora.webapi.slice.api.admin.model.ProjectRestrictedViewSettingsADM
import org.knora.webapi.testservices.ResponseOps.*
import org.knora.webapi.testservices.TestAdminApiClient

/**
 * End-to-End (E2E) test specification for Sipi access.
 *
 * This spec tests the 'admin/files'.
 */
object AdminFilesE2ESpec extends E2EZSpec {

  override val rdfDataObjects: List[RdfDataObject] = List(anythingRdfData)

  override val e2eSpec = suite("The Files Route ('admin/files') using token credentials")(
    test("return CR (8) permission code") {
      for {
        response <- TestAdminApiClient
                      .getAdminFilesPermissions(anythingShortcode, "B1D0OkEgfFp-Cew2Seur7Wi.jp2", anythingAdminUser)
                      .flatMap(_.assert200)
      } yield assertTrue(response == PermissionCodeAndProjectRestrictedViewSettings(8, None))
    },
    test("return RV (1) permission code") {
      for {
        response <- TestAdminApiClient
                      .getAdminFilesPermissions(anythingShortcode, "B1D0OkEgfFp-Cew2Seur7Wi.jp2", normalUser)
                      .flatMap(_.assert200)
      } yield assertTrue(
        response == PermissionCodeAndProjectRestrictedViewSettings(
          1,
          Some(ProjectRestrictedViewSettingsADM(Some("!128,128"), watermark = false)),
        ),
      )
    },
    test("return 404 Not Found if a file value is in a deleted resource") {
      for {
        response <-
          TestAdminApiClient.getAdminFilesPermissions(anythingShortcode, "9hxmmrWh0a7-CnRCq0650ro.jpx", normalUser)
      } yield assertTrue(response.code == StatusCode.NotFound)
    },
    test("return permissions for a previous version of a file value") {
      for {
        response <- TestAdminApiClient
                      .getAdminFilesPermissions(anythingShortcode, "QxFMm5wlRlatStw9ft3iZA.jp2", normalUser)
                      .flatMap(_.assert200)
      } yield assertTrue(
        response == PermissionCodeAndProjectRestrictedViewSettings(
          1,
          Some(ProjectRestrictedViewSettingsADM(Some("!128,128"), watermark = false)),
        ),
      )
    },
  )
}
