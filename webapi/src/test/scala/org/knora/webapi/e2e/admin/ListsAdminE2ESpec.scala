/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

package org.knora.webapi.e2e.admin

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.RouteTestTimeout
import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.admin.responder.listsadminmessages._
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionJsonProtocol
import org.knora.webapi.messages.v1.routing.authenticationmessages.CredentialsV1
import org.knora.webapi.util.{AkkaHttpUtils, MutableTestIri}
import org.knora.webapi.{E2ESpec, SharedAdminTestData, SharedListsAdminTestData}
import spray.json._

import scala.concurrent.duration._

object ListsAdminE2ESpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing users endpoint.
  */
class ListsAdminE2ESpec extends E2ESpec(ListsAdminE2ESpec.config) with SessionJsonProtocol with TriplestoreJsonProtocol with ListAdminJsonProtocol {

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(5.seconds)

    implicit override lazy val log = akka.event.Logging(system, this.getClass())

    private val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything")
    )

    val rootCreds = CredentialsV1(
        SharedAdminTestData.rootUser.userData.user_id.get,
        SharedAdminTestData.rootUser.userData.email.get,
        "test"
    )

    val normalUserCreds = CredentialsV1(
        SharedAdminTestData.normalUser.userData.user_id.get,
        SharedAdminTestData.normalUser.userData.email.get,
        "test"
    )

    val inactiveUserEmailEnc = java.net.URLEncoder.encode(SharedAdminTestData.inactiveUser.userData.email.get, "utf-8")


    val normalUserIri = SharedAdminTestData.normalUser.userData.user_id.get
    val normalUserIriEnc = java.net.URLEncoder.encode(normalUserIri, "utf-8")

    val multiUserIri = SharedAdminTestData.multiuserUser.userData.user_id.get
    val multiUserIriEnc = java.net.URLEncoder.encode(multiUserIri, "utf-8")

    val wrongEmail = "wrong@example.com"
    val wrongEmailEnc = java.net.URLEncoder.encode(wrongEmail, "utf-8")

    val testPass = java.net.URLEncoder.encode("test", "utf-8")
    val wrongPass = java.net.URLEncoder.encode("wrong", "utf-8")

    val imagesProjectIri = SharedAdminTestData.imagesProjectInfo.id
    val imagesProjectIriEnc = java.net.URLEncoder.encode(imagesProjectIri, "utf-8")

    val imagesReviewerGroupIri = SharedAdminTestData.imagesReviewerGroupInfo.id
    val imagesReviewerGroupIriEnc = java.net.URLEncoder.encode(imagesReviewerGroupIri, "utf-8")


    val bigListInfo: ListInfo = SharedListsAdminTestData.bigListInfo
    val bigListNodes: Seq[ListNode] = SharedListsAdminTestData.bigListNodes

    "Load test data" in {
        // send POST to 'v1/store/ResetTriplestoreContent'
        val request = Post(baseApiUrl + "/v1/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 300.seconds)
    }

    "The Lists Route ('/admin/lists')" when {

        "used to query information about lists" should {

            "return all lists" in {
                val request = Get(baseApiUrl + s"/admin/lists") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")

                response.status should be(StatusCodes.OK)

                val lists: Seq[ListNodeInfo] = AkkaHttpUtils.httpResponseToJson(response).fields("items").convertTo[Seq[ListNodeInfo]]

                // log.debug("lists: {}", lists)

                lists.size should be (7)
            }

            "return all lists belonging to the images project" in {
                val request = Get(baseApiUrl + s"/admin/lists?projectIri=http%3A%2F%2Frdfh.ch%2Fprojects%2F00FF") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")

                response.status should be(StatusCodes.OK)

                val lists: Seq[ListNodeInfo] = AkkaHttpUtils.httpResponseToJson(response).fields("items").convertTo[Seq[ListNodeInfo]]

                // log.debug("lists: {}", lists)

                lists.size should be (4)
            }

            "return all lists belonging to the anything project" in {
                val request = Get(baseApiUrl + s"/admin/lists?projectIri=http%3A%2F%2Frdfh.ch%2Fprojects%2Fanything") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")

                response.status should be(StatusCodes.OK)

                val lists: Seq[ListNodeInfo] = AkkaHttpUtils.httpResponseToJson(response).fields("items").convertTo[Seq[ListNodeInfo]]

                // log.debug("lists: {}", lists)

                lists.size should be (2)
            }

            "return basic list information" in {
                val request = Get(baseApiUrl + s"/admin/lists/infos/http%3A%2F%2Frdfh.ch%2Flists%2F00FF%2F73d0ec0302") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")

                response.status should be(StatusCodes.OK)

                val receivedListInfo: ListInfo = AkkaHttpUtils.httpResponseToJson(response).fields("listinfo").convertTo[ListInfo]

                val expectedListInfo: ListInfo = SharedListsAdminTestData.bigListInfo

                receivedListInfo.sorted should be (expectedListInfo.sorted)
            }

            "return a complete list" in {
                val request = Get(baseApiUrl + s"/admin/lists/http%3A%2F%2Frdfh.ch%2Flists%2F00FF%2F73d0ec0302") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")

                response.status should be(StatusCodes.OK)

                val receivedList: FullList = AkkaHttpUtils.httpResponseToJson(response).fields("list").convertTo[FullList]
                receivedList.listinfo.sorted should be (bigListInfo.sorted)
                receivedList.children.map(_.sorted) should be (bigListNodes.map(_.sorted))
            }
        }

        "used to modify list information" should {

            val newListIri = new MutableTestIri

            "create a list" ignore {

            }

            "update basic list information" ignore {

            }

            "add flat nodes" ignore {

            }

            "add hierarchical nodes" ignore {

            }

            "change node order" ignore {

            }

            "delete node if not in use" ignore {

            }

        }
    }
}
