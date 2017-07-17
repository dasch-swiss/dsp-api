/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.e2e.v1

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.RouteTestTimeout
import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.v1.responder.authenticatemessages.Credentials
import org.knora.webapi.messages.v1.responder.listmessages.{ListInfoV1, ListV1JsonProtocol}
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionJsonProtocol
import org.knora.webapi.messages.v1.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.util.{AkkaHttpUtils, MutableTestIri}
import org.knora.webapi.{E2ESpec, IRI, SharedAdminTestData}
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
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything")
    )

    val rootCreds = Credentials(
        SharedAdminTestData.rootUser.userData.user_id.get,
        SharedAdminTestData.rootUser.userData.email.get,
        "test"
    )

    val normalUserCreds = Credentials(
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

    /**
      * Convenience method returning the users project memberships.
      *
      * @param userIri     the user's IRI.
      * @param credentials the credentials of the user making the request.
      */
    private def getUserProjectMemberships(userIri: IRI, credentials: Credentials): Seq[IRI] = {
        val userIriEnc = java.net.URLEncoder.encode(userIri, "utf-8")
        val request = Get(baseApiUrl + "/v1/users/projects/" + userIriEnc) ~> addCredentials(BasicHttpCredentials(credentials.email, credentials.password))
        val response: HttpResponse = singleAwaitingRequest(request)
        AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[Seq[IRI]]
    }

    /**
      * Convenience method returning the users project-admin memberships.
      *
      * @param userIri     the user's IRI.
      * @param credentials the credentials of the user making the request.
      */
    private def getUserProjectAdminMemberships(userIri: IRI, credentials: Credentials): Seq[IRI] = {
        val userIriEnc = java.net.URLEncoder.encode(userIri, "utf-8")
        val request = Get(baseApiUrl + "/v1/users/projects-admin/" + userIriEnc) ~> addCredentials(BasicHttpCredentials(credentials.email, credentials.password))
        val response: HttpResponse = singleAwaitingRequest(request)
        AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[Seq[IRI]]
    }

    /**
      * Convenience method returning the users group memberships.
      *
      * @param userIri     the user's IRI.
      * @param credentials the credentials of the user making the request.
      */
    private def getUserGroupMemberships(userIri: IRI, credentials: Credentials): Seq[IRI] = {
        val userIriEnc = java.net.URLEncoder.encode(userIri, "utf-8")
        val request = Get(baseApiUrl + "/v1/users/groups/" + userIriEnc) ~> addCredentials(BasicHttpCredentials(credentials.email, credentials.password))
        val response: HttpResponse = singleAwaitingRequest(request)
        AkkaHttpUtils.httpResponseToJson(response).fields("groups").convertTo[Seq[IRI]]
    }

    "Load test data" in {
        // send POST to 'v1/store/ResetTriplestoreContent'
        val request = Post(baseApiUrl + "/v1/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 300.seconds)
    }

    "The Lists Route ('v1/lists')" when {

        "used to query information about lists" should {

            "return all lists" in {
                val request = Get(baseApiUrl + s"/v1/lists") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)

                val listInfos: Seq[ListInfoV1] = AkkaHttpUtils.httpResponseToJson(response).fields("lists").convertTo[Seq[ListInfoV1]]
                listInfos.size should be (6)
            }

            "return all lists belonging to the images project" in {
                val request = Get(baseApiUrl + s"/v1/lists?projectIri=http%3A%2F%2Fdata.knora.org%2Fprojects%2Fimages") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: ${response.toString}")
                response.status should be(StatusCodes.OK)

                val listInfos: Seq[ListInfoV1] = AkkaHttpUtils.httpResponseToJson(response).fields("lists").convertTo[Seq[ListInfoV1]]

                log.debug("received: " + listInfos)

                listInfos.size should be (4)
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
