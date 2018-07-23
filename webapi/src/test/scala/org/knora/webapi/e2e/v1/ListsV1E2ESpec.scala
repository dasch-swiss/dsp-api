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
import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.messages.v1.responder.listmessages._
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionJsonProtocol
import org.knora.webapi.messages.v1.routing.authenticationmessages.CredentialsV1
import org.knora.webapi.{E2ESpec, SharedTestDataV1}
import spray.json._

import scala.concurrent.duration._

object ListsV1E2ESpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing users endpoint.
  */
class ListsV1E2ESpec extends E2ESpec(ListsV1E2ESpec.config) with SessionJsonProtocol with TriplestoreJsonProtocol with ListV1JsonProtocol {

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(5.seconds)

    implicit override lazy val log = akka.event.Logging(system, this.getClass())

    private val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
    )

    val rootCreds = CredentialsV1(
        SharedTestDataV1.rootUser.userData.user_id.get,
        SharedTestDataV1.rootUser.userData.email.get,
        "test"
    )

    val normalUserCreds = CredentialsV1(
        SharedTestDataV1.normalUser.userData.user_id.get,
        SharedTestDataV1.normalUser.userData.email.get,
        "test"
    )

    val inactiveUserEmailEnc = java.net.URLEncoder.encode(SharedTestDataV1.inactiveUser.userData.email.get, "utf-8")


    val normalUserIri = SharedTestDataV1.normalUser.userData.user_id.get
    val normalUserIriEnc = java.net.URLEncoder.encode(normalUserIri, "utf-8")

    val multiUserIri = SharedTestDataV1.multiuserUser.userData.user_id.get
    val multiUserIriEnc = java.net.URLEncoder.encode(multiUserIri, "utf-8")

    val wrongEmail = "wrong@example.com"
    val wrongEmailEnc = java.net.URLEncoder.encode(wrongEmail, "utf-8")

    val testPass = java.net.URLEncoder.encode("test", "utf-8")
    val wrongPass = java.net.URLEncoder.encode("wrong", "utf-8")

    val imagesProjectIri = SharedTestDataV1.imagesProjectInfo.id
    val imagesProjectIriEnc = java.net.URLEncoder.encode(imagesProjectIri, "utf-8")


    "Load test data" in {
        loadTestData(rdfDataObjects)
    }

    "The HList Route ('v1/hlists')" when {

        "used to query information about hierarchical lists" should {

            "return an hlist" ignore {
                /*
                val request = Get(baseApiUrl + s"/v1/lists") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)

                val listInfos: Seq[ListInfoV1] = AkkaHttpUtils.httpResponseToJson(response).fields("lists").convertTo[Seq[ListInfoV1]]
                listInfos.size should be (6)
                */
            }
        }
    }

    "The Selections Route ('v1/selections')" when {

        "used to query information about selections (flat lists)" should {

            "return a selection" ignore {
                /*
                val request = Get(baseApiUrl + s"/v1/lists") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)

                val listInfos: Seq[ListInfoV1] = AkkaHttpUtils.httpResponseToJson(response).fields("lists").convertTo[Seq[ListInfoV1]]
                listInfos.size should be (6)
                */
            }
        }
    }
}
