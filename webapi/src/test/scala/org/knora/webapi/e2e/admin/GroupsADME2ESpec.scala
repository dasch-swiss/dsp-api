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

package org.knora.webapi.e2e.admin

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.RouteTestTimeout
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.messages.admin.responder.groupsmessages.{GroupADM, GroupsADMJsonProtocol}
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionJsonProtocol
import org.knora.webapi.util.{AkkaHttpUtils, MutableTestIri}
import org.knora.webapi.{E2ESpec, SharedTestDataADM, SharedTestDataV1}

import scala.concurrent.duration._


object GroupsADME2ESpec {
    val config: Config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing groups endpoint.
  */
class GroupsADME2ESpec extends E2ESpec(GroupsADME2ESpec.config) with GroupsADMJsonProtocol with SessionJsonProtocol {

    implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(30.seconds)

    implicit override lazy val log: LoggingAdapter = akka.event.Logging(system, this.getClass)

    private val rootEmail = SharedTestDataADM.rootUser.email
    private val rootEmailEnc = java.net.URLEncoder.encode(rootEmail, "utf-8")
    private val imagesUser01Email = SharedTestDataADM.imagesUser01.email
    private val testPass = java.net.URLEncoder.encode("test", "utf-8")

    private val groupIri = SharedTestDataADM.imagesReviewerGroup.id
    private val groupIriEnc = java.net.URLEncoder.encode(groupIri, "utf-8")
    private val groupName = SharedTestDataADM.imagesReviewerGroup.name
    private val groupNameEnc = java.net.URLEncoder.encode(groupName, "utf-8")
    private val projectIri = SharedTestDataADM.imagesReviewerGroup.project.id
    private val projectIriEnc = java.net.URLEncoder.encode(projectIri, "utf-8")

    "The Groups Route ('admin/groups')" when {
        "used to query for group information" should {

            "return all groups" in {
                val request = Get(baseApiUrl + s"/admin/groups") ~> addCredentials(BasicHttpCredentials(imagesUser01Email, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)
            }

            "return the group's information" in {
                val request = Get(baseApiUrl + s"/admin/groups/$groupIriEnc") ~> addCredentials(BasicHttpCredentials(imagesUser01Email, testPass))
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
                       |    "project": "${SharedTestDataV1.IMAGES_PROJECT_IRI}",
                       |    "status": true,
                       |    "selfjoin": false
                       |}
                """.stripMargin


                val request = Post(baseApiUrl + "/admin/groups", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(imagesUser01Email, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                response.status should be (StatusCodes.OK)

                val groupInfo: GroupADM = AkkaHttpUtils.httpResponseToJson(response).fields("group").convertTo[GroupADM]

                groupInfo.name should be ("NewGroup")
                groupInfo.description should be ("NewGroupDescription")
                groupInfo.project should be (SharedTestDataADM.imagesProject)
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
                val request = Put(baseApiUrl + "/admin/groups/" + groupIriEnc, HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(imagesUser01Email, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                log.debug(s"response: {}", response)
                response.status should be (StatusCodes.OK)

                val groupInfo: GroupADM = AkkaHttpUtils.httpResponseToJson(response).fields("group").convertTo[GroupADM]

                groupInfo.name should be ("UpdatedGroupName")
                groupInfo.description should be ("UpdatedGroupDescription")
                groupInfo.project should be (SharedTestDataADM.imagesProject)
                groupInfo.status should be (true)
                groupInfo.selfjoin should be (false)

            }

            "DELETE a group" in {

                val groupIriEnc = java.net.URLEncoder.encode(newGroupIri.get, "utf-8")
                val request = Delete(baseApiUrl + "/admin/groups/" + groupIriEnc) ~> addCredentials(BasicHttpCredentials(imagesUser01Email, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                log.debug(s"response: {}", response)
                response.status should be (StatusCodes.OK)

                val groupInfo: GroupADM = AkkaHttpUtils.httpResponseToJson(response).fields("group").convertTo[GroupADM]

                groupInfo.name should be ("UpdatedGroupName")
                groupInfo.description should be ("UpdatedGroupDescription")
                groupInfo.project should be (SharedTestDataADM.imagesProject)
                groupInfo.status should be (false)
                groupInfo.selfjoin should be (false)

            }
        }

        "used to query members" should {

            "return all members of a group" in {
                val request = Get(baseApiUrl + s"/admin/groups/members/$groupIriEnc") ~> addCredentials(BasicHttpCredentials(imagesUser01Email, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)
            }
        }
    }
}
