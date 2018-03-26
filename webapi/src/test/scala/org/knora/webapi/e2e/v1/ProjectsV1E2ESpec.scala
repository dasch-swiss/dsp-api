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
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.RouteTestTimeout
import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.messages.v1.responder.projectmessages.{ProjectInfoV1, ProjectV1JsonProtocol}
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionJsonProtocol
import org.knora.webapi.messages.v1.responder.usermessages.UserDataV1
import org.knora.webapi.messages.v1.responder.usermessages.UserV1JsonProtocol._
import org.knora.webapi.util.AkkaHttpUtils
import org.knora.webapi.{E2ESpec, SharedTestDataV1}
import spray.json._

import scala.concurrent.duration._


object ProjectsV1E2ESpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing groups endpoint.
  */
class ProjectsV1E2ESpec extends E2ESpec(ProjectsV1E2ESpec.config) with SessionJsonProtocol with ProjectV1JsonProtocol with TriplestoreJsonProtocol {

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(30.seconds)

    implicit override lazy val log = akka.event.Logging(system, this.getClass())

    private val rdfDataObjects = List.empty[RdfDataObject]

    val rootEmail = SharedTestDataV1.rootUser.userData.email.get
    val rootEmailEnc = java.net.URLEncoder.encode(rootEmail, "utf-8")
    val testPass = java.net.URLEncoder.encode("test", "utf-8")
    val projectIri = SharedTestDataV1.imagesProjectInfo.id
    val projectIriEnc = java.net.URLEncoder.encode(projectIri, "utf-8")
    val projectShortName = SharedTestDataV1.imagesProjectInfo.shortname
    val projectShortnameEnc = java.net.URLEncoder.encode(projectShortName, "utf-8")


    "Load test data" in {
        // send POST to 'v1/store/ResetTriplestoreContent'
        val request = Post(baseApiUrl + "/admin/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 300.seconds)
    }

    "The Projects Route ('v1/projects')" when {

        "used to query for project information" should {

            "return all projects" in {
                val request = Get(baseApiUrl + s"/v1/projects") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)

                // log.debug("projects as objects: {}", AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[Seq[ProjectInfoV1]])

                val projects: Seq[ProjectInfoV1] = AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[Seq[ProjectInfoV1]]
                projects.size should be (8)

            }

            "return the information for a single project identified by iri" in {
                val request = Get(baseApiUrl + s"/v1/projects/$projectIriEnc") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)
            }

            "return the information for a single project identified by shortname" in {
                val request = Get(baseApiUrl + s"/v1/projects/$projectShortnameEnc?identifier=shortname") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)
            }
        }

        "used to query members" should {

            "return all members of a project identified by iri" in {
                val request = Get(baseApiUrl + s"/v1/projects/members/$projectIriEnc") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)

                val members: Seq[UserDataV1] = AkkaHttpUtils.httpResponseToJson(response).fields("members").convertTo[Seq[UserDataV1]]
                members.size should be (4)
            }

            "return all members of a project identified by shortname" in {
                val request = Get(baseApiUrl + s"/v1/projects/members/$projectShortnameEnc?identifier=shortname") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)

                val members: Seq[UserDataV1] = AkkaHttpUtils.httpResponseToJson(response).fields("members").convertTo[Seq[UserDataV1]]
                members.size should be (4)
            }

            "return all admin members of a project identified by iri" in {
                val request = Get(baseApiUrl + s"/v1/projects/admin-members/$projectIriEnc") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)

                val members: Seq[UserDataV1] = AkkaHttpUtils.httpResponseToJson(response).fields("members").convertTo[Seq[UserDataV1]]
                members.size should be (2)
            }

            "return all admin members of a project identified by shortname" in {
                val request = Get(baseApiUrl + s"/v1/projects/admin-members/$projectShortnameEnc?identifier=shortname") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)

                val members: Seq[UserDataV1] = AkkaHttpUtils.httpResponseToJson(response).fields("members").convertTo[Seq[UserDataV1]]
                members.size should be (2)
            }
        }
    }
}
