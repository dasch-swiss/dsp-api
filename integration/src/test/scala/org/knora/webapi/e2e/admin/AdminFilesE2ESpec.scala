/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.admin

import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import zio.ZIO

import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM2
import org.knora.webapi.slice.admin.api.model.PermissionCodeAndProjectRestrictedViewSettings
import org.knora.webapi.slice.admin.api.model.ProjectRestrictedViewSettingsADM

/**
 * End-to-End (E2E) test specification for Sipi access.
 *
 * This spec tests the 'admin/files'.
 */
class AdminFilesE2ESpec extends E2ESpec with TriplestoreJsonProtocol {

  private val anythingAdminEmail = SharedTestDataADM2.anythingAdminUser.userData.email.get
  private val normalUserEmail    = SharedTestDataADM2.normalUser.userData.email.get
  private val testPass           = "test"

  val KnoraAuthenticationCookieName = UnsafeZioRun.runOrThrow(ZIO.serviceWith[Authenticator](_.calculateCookieName()))

  override lazy val rdfDataObjects = List(
    RdfDataObject(
      path = "test_data/project_data/anything-data.ttl",
      name = "http://www.knora.org/data/0001/anything",
    ),
  )

  "The Files Route ('admin/files') using token credentials" should {

    "return CR (8) permission code" in {
      /* anything image */
      val request = Get(baseApiUrl + s"/admin/files/0001/B1D0OkEgfFp-Cew2Seur7Wi.jp2") ~>
        addCredentials(BasicHttpCredentials(anythingAdminEmail, testPass))
      val response: HttpResponse = singleAwaitingRequest(request)

      assert(response.status == StatusCodes.OK)

      val result: PermissionCodeAndProjectRestrictedViewSettings =
        PermissionCodeAndProjectRestrictedViewSettings.codec
          .decodeJson(responseToString(response))
          .getOrElse(throw new AssertionError(s"Could not decode response for ${responseToString(response)}."))

      assert(result == PermissionCodeAndProjectRestrictedViewSettings(8, None))
    }

    "return RV (1) permission code" in {
      /* anything image */
      val request =
        Get(baseApiUrl + s"/admin/files/0001/B1D0OkEgfFp-Cew2Seur7Wi.jp2") ~>
          addCredentials(BasicHttpCredentials(normalUserEmail, testPass))
      val response: HttpResponse = singleAwaitingRequest(request)

      assert(response.status == StatusCodes.OK)

      val result: PermissionCodeAndProjectRestrictedViewSettings =
        PermissionCodeAndProjectRestrictedViewSettings.codec
          .decodeJson(responseToString(response))
          .getOrElse(throw new AssertionError(s"Could not decode response for ${responseToString(response)}."))

      assert(
        result == PermissionCodeAndProjectRestrictedViewSettings(
          1,
          Some(ProjectRestrictedViewSettingsADM(Some("!128,128"), watermark = false)),
        ),
      )
    }

    "return 404 Not Found if a file value is in a deleted resource" in {
      val request =
        Get(baseApiUrl + s"/admin/files/0001/9hxmmrWh0a7-CnRCq0650ro.jpx") ~>
          addCredentials(BasicHttpCredentials(normalUserEmail, testPass))
      val response: HttpResponse = singleAwaitingRequest(request)

      assert(response.status == StatusCodes.NotFound)
    }

    "return permissions for a previous version of a file value" in {
      val request =
        Get(baseApiUrl + s"/admin/files/0001/QxFMm5wlRlatStw9ft3iZA.jp2") ~>
          addCredentials(BasicHttpCredentials(normalUserEmail, testPass))
      val response: HttpResponse = singleAwaitingRequest(request)

      assert(response.status == StatusCodes.OK)

      val result: PermissionCodeAndProjectRestrictedViewSettings =
        PermissionCodeAndProjectRestrictedViewSettings.codec
          .decodeJson(responseToString(response))
          .getOrElse(throw new AssertionError(s"Could not decode response for ${responseToString(response)}."))

      assert(
        result == PermissionCodeAndProjectRestrictedViewSettings(
          1,
          Some(ProjectRestrictedViewSettingsADM(Some("!128,128"), watermark = false)),
        ),
      )
    }
  }
}
