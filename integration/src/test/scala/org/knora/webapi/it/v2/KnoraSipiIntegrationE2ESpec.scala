/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.it.v2

import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import zio.ZIO

import scala.concurrent.Await
import scala.concurrent.duration.*

import org.knora.webapi.*
import org.knora.webapi.e2e.v2.AuthenticationV2JsonProtocol
import org.knora.webapi.e2e.v2.LoginResponse
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.security.Authenticator

/**
 * Tests interaction between Knora and Sipi using Knora API v2.
 */
class KnoraSipiIntegrationE2ESpec extends E2ESpec with AuthenticationV2JsonProtocol with TriplestoreJsonProtocol {

  private val anythingUserEmail = SharedTestDataADM.anythingAdminUser.email
  private val password          = SharedTestDataADM.testPass

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/project_data/anything-data.ttl",
      name = "http://www.knora.org/data/0001/anything",
    ),
  )

  "The Knora/Sipi authentication" should {
    lazy val loginToken: String = {
      val params =
        s"""
           |{
           |    "email": "$anythingUserEmail",
           |    "password": "$password"
           |}
              """.stripMargin
      val request                = Post(baseApiUrl + s"/v2/authentication", HttpEntity(ContentTypes.`application/json`, params))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK)
      Await.result(Unmarshal(response.entity).to[LoginResponse], 1.seconds).token
    }

    "log in as a Knora user" in {
      loginToken.nonEmpty should be(true)
    }

    "successfully get an image with provided credentials inside cookie" in {
      // using cookie to authenticate when accessing sipi (test for cookie parsing in sipi)
      val KnoraAuthenticationCookieName =
        UnsafeZioRun.runOrThrow(ZIO.serviceWith[Authenticator](_.calculateCookieName()))
      val cookieHeader = headers.Cookie(KnoraAuthenticationCookieName, loginToken)

      // Request the permanently stored image from Sipi.
      val sipiGetImageRequest =
        Get(s"$baseSipiUrl/0001/B1D0OkEgfFp-Cew2Seur7Wi.jp2/full/max/0/default.jpg") ~> addHeader(cookieHeader)
      val response = singleAwaitingRequest(sipiGetImageRequest)
      assert(response.status === StatusCodes.OK)
    }

    "accept a request with valid credentials to clean_temp_dir route which requires basic auth" in {
      // set the environment variables
      val username = "clean_tmp_dir_user"
      val password = "clean_tmp_dir_pw"

      val request =
        Get(s"$baseSipiUrl/clean_temp_dir") ~> addCredentials(BasicHttpCredentials(username, password))

      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK)
    }

    "not accept a request with invalid credentials to clean_temp_dir route which requires basic auth" in {
      val username = "username"
      val password = "password"

      val request =
        Get(s"$baseSipiUrl/clean_temp_dir") ~> addCredentials(BasicHttpCredentials(username, password))

      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.Unauthorized)
    }
  }
}
