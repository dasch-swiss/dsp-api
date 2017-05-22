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
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionJsonProtocol
import org.knora.webapi.messages.v1.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.{E2ESpec, SharedAdminTestData}
import spray.json._

import scala.concurrent.duration._


object GroupsV1E2ESpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing groups endpoint.
  */
class GroupsV1E2ESpec extends E2ESpec(GroupsV1E2ESpec.config) with SessionJsonProtocol with TriplestoreJsonProtocol {

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(5.seconds)

    private val rdfDataObjects = List.empty[RdfDataObject]

    val rootEmail = SharedAdminTestData.rootUser.userData.email.get
    val rootEmailEnc = java.net.URLEncoder.encode(rootEmail, "utf-8")
    val testPass = java.net.URLEncoder.encode("test", "utf-8")

    val groupIri = SharedAdminTestData.imagesReviewerGroupInfo.id
    val groupIriEnc = java.net.URLEncoder.encode(groupIri, "utf-8")
    val groupName = SharedAdminTestData.imagesReviewerGroupInfo.name
    val groupNameEnc = java.net.URLEncoder.encode(groupName, "utf-8")
    val projectIri = SharedAdminTestData.imagesReviewerGroupInfo.belongsToProject
    val projectIriEnc = java.net.URLEncoder.encode(projectIri, "utf-8")

    "Load test data" in {
        // send POST to 'v1/store/ResetTriplestoreContent'
        val request = Post(baseApiUrl + "/v1/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 300.seconds)
    }

    "The Groups Route ('v1/groups')" when {
        "used to query for group information" should {

            "return all groups" in {
                val request = Get(baseApiUrl + s"/v1/groups") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                println(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)
            }

            "return the group's information (identified by iri)" in {
                val request = Get(baseApiUrl + s"/v1/groups/$groupIriEnc") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                println(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)
            }

            "return the group's information (identified by project and groupname)" in {
                val request = Get(baseApiUrl + s"/v1/groups/$groupNameEnc?projectIri=$projectIriEnc&identifier=groupname") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                println(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)
            }
        }

        "used to modify group information" should {

            "create a new group" ignore {
                fail("test not implemented")
            }

            "update a group" ignore {
                fail("test not implemented")
            }

            "return a 'DuplicateValueException' if the supplied group name is not unique (inside project)" ignore {
                fail("test not implemented")
            }

            "return 'BadRequestException' if 'name' is missing" ignore {
                fail("test not implemented")
            }
        }

        "used to query members" should {

            "return all members of a group identified by IRI" in {
                val request = Get(baseApiUrl + s"/v1/groups/members/$groupIriEnc") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                println(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)
            }

            "return all members of a group identified by group name and project IRI" in {
                val request = Get(baseApiUrl + s"/v1/groups/members/$groupNameEnc?projectIri=$projectIriEnc&identifier=groupname") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                println(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)
            }
        }
    }
}
