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
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.messages.v1.responder.groupmessages.{GroupInfoV1, GroupV1JsonProtocol}
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionJsonProtocol
import org.knora.webapi.util.{AkkaHttpUtils, MutableTestIri}
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
class GroupsV1E2ESpec extends E2ESpec(GroupsV1E2ESpec.config) with GroupV1JsonProtocol with SessionJsonProtocol with TriplestoreJsonProtocol {

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(30.seconds)

    implicit override lazy val log = akka.event.Logging(system, this.getClass())

    private val rdfDataObjects = List.empty[RdfDataObject]

    val rootEmail = SharedAdminTestData.rootUser.userData.email.get
    val rootEmailEnc = java.net.URLEncoder.encode(rootEmail, "utf-8")
    val imagesUser01Email = SharedAdminTestData.imagesUser01.userData.email.get
    val testPass = java.net.URLEncoder.encode("test", "utf-8")

    val groupIri = SharedAdminTestData.imagesReviewerGroupInfo.id
    val groupIriEnc = java.net.URLEncoder.encode(groupIri, "utf-8")
    val groupName = SharedAdminTestData.imagesReviewerGroupInfo.name
    val groupNameEnc = java.net.URLEncoder.encode(groupName, "utf-8")
    val projectIri = SharedAdminTestData.imagesReviewerGroupInfo.project
    val projectIriEnc = java.net.URLEncoder.encode(projectIri, "utf-8")

    "Load test data" in {
        // send POST to 'v1/store/ResetTriplestoreContent'
        val request = Post(baseApiUrl + "/v1/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 300.seconds)
    }

    "The Groups Route ('v1/groups')" when {
        "used to query for group information" should {

            "return all groups" in {
                val request = Get(baseApiUrl + s"/v1/groups") ~> addCredentials(BasicHttpCredentials(imagesUser01Email, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)
            }

            "return the group's information (identified by iri)" in {
                val request = Get(baseApiUrl + s"/v1/groups/$groupIriEnc") ~> addCredentials(BasicHttpCredentials(imagesUser01Email, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)
            }

            "return the group's information (identified by project and groupname)" in {
                val request = Get(baseApiUrl + s"/v1/groups/$groupNameEnc?projectIri=$projectIriEnc&identifier=groupname") ~> addCredentials(BasicHttpCredentials(imagesUser01Email, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)
            }
        }

        "used to modify group information" should {

            val newGroupIri = new MutableTestIri

            "CREATE a new group" in {

                val params =
                    s"""
                       |{
                       |    "name": "NewGroup",
                       |    "description": "NewGroupDescription",
                       |    "project": "http://data.knora.org/projects/images",
                       |    "status": true,
                       |    "selfjoin": false
                       |}
                """.stripMargin


                val request = Post(baseApiUrl + "/v1/groups", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(imagesUser01Email, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                response.status should be (StatusCodes.OK)

                val groupInfo: GroupInfoV1 = AkkaHttpUtils.httpResponseToJson(response).fields("group_info").convertTo[GroupInfoV1]

                groupInfo.name should be ("NewGroup")
                groupInfo.description should be (Some("NewGroupDescription"))
                groupInfo.project should be ("http://data.knora.org/projects/images")
                groupInfo.status should be (true)
                groupInfo.selfjoin should be (false)

                val iri = groupInfo.id
                newGroupIri.set(iri)
                // log.debug("newGroupIri: {}", newGroupIri.get)
            }

            "UPDATE a group" in {

                val params =
                    s"""
                       |{
                       |    "name": "UpdatedGroupName",
                       |    "description": "UpdatedGroupDescription"
                       |}
                """.stripMargin

                val groupIriEnc = java.net.URLEncoder.encode(newGroupIri.get, "utf-8")
                val request = Put(baseApiUrl + "/v1/groups/" + groupIriEnc, HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(imagesUser01Email, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                log.debug(s"response: {}", response)
                response.status should be (StatusCodes.OK)

                val groupInfo: GroupInfoV1 = AkkaHttpUtils.httpResponseToJson(response).fields("group_info").convertTo[GroupInfoV1]

                groupInfo.name should be ("UpdatedGroupName")
                groupInfo.description should be (Some("UpdatedGroupDescription"))
                groupInfo.project should be ("http://data.knora.org/projects/images")
                groupInfo.status should be (true)
                groupInfo.selfjoin should be (false)

            }

            "DELETE a group" in {

                val groupIriEnc = java.net.URLEncoder.encode(newGroupIri.get, "utf-8")
                val request = Delete(baseApiUrl + "/v1/groups/" + groupIriEnc) ~> addCredentials(BasicHttpCredentials(imagesUser01Email, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                log.debug(s"response: {}", response)
                response.status should be (StatusCodes.OK)

                val groupInfo: GroupInfoV1 = AkkaHttpUtils.httpResponseToJson(response).fields("group_info").convertTo[GroupInfoV1]

                groupInfo.name should be ("UpdatedGroupName")
                groupInfo.description should be (Some("UpdatedGroupDescription"))
                groupInfo.project should be ("http://data.knora.org/projects/images")
                groupInfo.status should be (false)
                groupInfo.selfjoin should be (false)

            }
        }

        "used to query members" should {

            "return all members of a group identified by IRI" in {
                val request = Get(baseApiUrl + s"/v1/groups/members/$groupIriEnc") ~> addCredentials(BasicHttpCredentials(imagesUser01Email, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)
            }

            "return all members of a group identified by group name and project IRI" in {
                val request = Get(baseApiUrl + s"/v1/groups/members/$groupNameEnc?projectIri=$projectIriEnc&identifier=groupname") ~> addCredentials(BasicHttpCredentials(imagesUser01Email, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)
            }
        }
    }
}
