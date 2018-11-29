/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.e2e.v1

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.messages.v1.responder.sessionmessages.{SessionJsonProtocol, SessionResponse}
import org.knora.webapi.messages.v1.responder.sipimessages.{FilesResponse, SipiJsonProtocol}
import org.knora.webapi.{E2ESpec, SharedTestDataV1}

import scala.concurrent.Await
import scala.concurrent.duration._


object SipiV1E2ESpec {
    val config: Config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for Sipi access.
  *
  * This spec tests the 'v1/files'.
  */
class SipiV1E2ESpec extends E2ESpec(SipiV1E2ESpec.config) with SipiJsonProtocol with TriplestoreJsonProtocol {

    private implicit def default(implicit system: ActorSystem) = RouteTestTimeout(30.seconds)

    private val rootIri = SharedTestDataV1.rootUser.userData.user_id.get
    private val rootIriEnc = java.net.URLEncoder.encode(rootIri, "utf-8")
    private val rootEmail = SharedTestDataV1.rootUser.userData.email.get
    private val rootEmailEnc = java.net.URLEncoder.encode(rootEmail, "utf-8")
    private val anythingAdminEmail = SharedTestDataV1.anythingAdminUser.userData.email.get
    private val anythingAdminEmailEnc = java.net.URLEncoder.encode(anythingAdminEmail, "utf-8")
    private val normalUserEmail = SharedTestDataV1.normalUser.userData.email.get
    private val normalUserEmailEnc = java.net.URLEncoder.encode(normalUserEmail, "utf-8")
    private val testPass = java.net.URLEncoder.encode("test", "utf-8")

    override lazy val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
    )

    "The Files Route ('v1/files') using session credentials" should {

        "return V (2) permission code if user has the necessary permission" in {
            /* anything image */
            val request = Get(baseApiUrl + s"/v1/files/B1D0OkEgfFp-Cew2Seur7Wi.jp2?email=$anythingAdminEmailEnc&password=$testPass")
            val response: HttpResponse = singleAwaitingRequest(request)

            // println(response.toString)

            assert(response.status == StatusCodes.OK)

            val fr: FilesResponse = Await.result(Unmarshal(response.entity).to[FilesResponse], 1.seconds)

            (fr.permissionCode >= 2) should be (true)
        }

        "return RV (1) permission code corresponding to the user's permission" in {
            /* anything image */
            val request = Get(baseApiUrl + s"/v1/files/B1D0OkEgfFp-Cew2Seur7Wi.jp2?email=$normalUserEmailEnc&password=$testPass")
            val response: HttpResponse = singleAwaitingRequest(request)

            // println(response.toString)

            assert(response.status == StatusCodes.OK)

            val fr: FilesResponse = Await.result(Unmarshal(response.entity).to[FilesResponse], 1.seconds)

            (fr.permissionCode === 1) should be (true)
        }
    }
}
