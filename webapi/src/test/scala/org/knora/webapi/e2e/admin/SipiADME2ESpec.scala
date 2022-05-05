/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.admin

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Cookie
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.admin.responder.sipimessages.SipiFileInfoGetResponseADM
import org.knora.webapi.messages.admin.responder.sipimessages.SipiResponderResponseADMJsonProtocol._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionJsonProtocol
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionResponse
import org.knora.webapi.routing.Authenticator.KNORA_AUTHENTICATION_COOKIE_NAME
import org.knora.webapi.sharedtestdata.SharedTestDataV1

import scala.concurrent.Await
import scala.concurrent.duration._

object SipiADME2ESpec {
  val config: Config = ConfigFactory.parseString("""
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
 * End-to-End (E2E) test specification for Sipi access.
 *
 * This spec tests the 'admin/files'.
 */
class SipiADME2ESpec extends E2ESpec(SipiADME2ESpec.config) with SessionJsonProtocol with TriplestoreJsonProtocol {

  private implicit def default(implicit system: ActorSystem) = RouteTestTimeout(30.seconds)

  private val anythingAdminEmail    = SharedTestDataV1.anythingAdminUser.userData.email.get
  private val anythingAdminEmailEnc = java.net.URLEncoder.encode(anythingAdminEmail, "utf-8")
  private val normalUserEmail       = SharedTestDataV1.normalUser.userData.email.get
  private val normalUserEmailEnc    = java.net.URLEncoder.encode(normalUserEmail, "utf-8")
  private val testPass              = java.net.URLEncoder.encode("test", "utf-8")

  override lazy val rdfDataObjects = List(
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
  )

  def sessionLogin(email: String, password: String): String = {

    val request                = Get(baseApiUrl + s"/v1/session?login&email=$email&password=$password")
    val response: HttpResponse = singleAwaitingRequest(request)
    assert(response.status == StatusCodes.OK)

    val sr: SessionResponse = Await.result(Unmarshal(response.entity).to[SessionResponse], 1.seconds)
    sr.sid
  }

  def sessionLogout(sessionId: String): Unit =
    Get(baseApiUrl + "/v1/session?logout") ~> Cookie(KNORA_AUTHENTICATION_COOKIE_NAME, sessionId)

  "The Files Route ('admin/files') using token credentials" should {

    "return CR (8) permission code" in {
      /* anything image */
      val request = Get(
        baseApiUrl + s"/admin/files/0001/B1D0OkEgfFp-Cew2Seur7Wi.jp2?email=$anythingAdminEmailEnc&password=$testPass"
      )
      val response: HttpResponse = singleAwaitingRequest(request)

      // println(response.toString)

      assert(response.status == StatusCodes.OK)

      val fr: SipiFileInfoGetResponseADM =
        Await.result(Unmarshal(response.entity).to[SipiFileInfoGetResponseADM], 1.seconds)

      (fr.permissionCode === 8) should be(true)
    }

    "return RV (1) permission code" in {
      /* anything image */
      val request =
        Get(baseApiUrl + s"/admin/files/0001/B1D0OkEgfFp-Cew2Seur7Wi.jp2?email=$normalUserEmailEnc&password=$testPass")
      val response: HttpResponse = singleAwaitingRequest(request)

      // println(response.toString)

      assert(response.status == StatusCodes.OK)

      val fr: SipiFileInfoGetResponseADM =
        Await.result(Unmarshal(response.entity).to[SipiFileInfoGetResponseADM], 1.seconds)

      (fr.permissionCode === 1) should be(true)
    }

    "return 404 Not Found if a file value is in a deleted resource" in {
      val request =
        Get(baseApiUrl + s"/admin/files/0001/9hxmmrWh0a7-CnRCq0650ro.jpx?email=$normalUserEmailEnc&password=$testPass")
      val response: HttpResponse = singleAwaitingRequest(request)

      // println(response.toString)

      assert(response.status == StatusCodes.NotFound)
    }

    "return permissions for a previous version of a file value" in {
      val request =
        Get(baseApiUrl + s"/admin/files/0001/QxFMm5wlRlatStw9ft3iZA.jp2?email=$normalUserEmailEnc&password=$testPass")
      val response: HttpResponse = singleAwaitingRequest(request)

      assert(response.status == StatusCodes.OK)

      val fr: SipiFileInfoGetResponseADM =
        Await.result(Unmarshal(response.entity).to[SipiFileInfoGetResponseADM], 1.seconds)

      (fr.permissionCode === 1) should be(true)

    }
  }

  "The Files Route ('admin/files') using session credentials" should {

    "return CR (8) permission code" in {
      /* login */
      val sessionId = sessionLogin(anythingAdminEmailEnc, testPass)

      /* anything image */
      val request = Get(baseApiUrl + s"/admin/files/0001/B1D0OkEgfFp-Cew2Seur7Wi.jp2") ~> Cookie(
        KNORA_AUTHENTICATION_COOKIE_NAME,
        sessionId
      )
      val response: HttpResponse = singleAwaitingRequest(request)

      // println(response.toString)

      assert(response.status == StatusCodes.OK)

      val fr: SipiFileInfoGetResponseADM =
        Await.result(Unmarshal(response.entity).to[SipiFileInfoGetResponseADM], 1.seconds)

      (fr.permissionCode === 8) should be(true)

      /* logout */
      sessionLogout(sessionId)
    }

    "return RV (1) permission code" in {
      /* login */
      val sessionId = sessionLogin(normalUserEmailEnc, testPass)

      /* anything image */
      val request = Get(baseApiUrl + s"/admin/files/0001/B1D0OkEgfFp-Cew2Seur7Wi.jp2") ~> Cookie(
        KNORA_AUTHENTICATION_COOKIE_NAME,
        sessionId
      )
      val response: HttpResponse = singleAwaitingRequest(request)

      // println(response.toString)

      assert(response.status == StatusCodes.OK)

      val fr: SipiFileInfoGetResponseADM =
        Await.result(Unmarshal(response.entity).to[SipiFileInfoGetResponseADM], 1.seconds)

      (fr.permissionCode === 1) should be(true)
    }
  }
}
