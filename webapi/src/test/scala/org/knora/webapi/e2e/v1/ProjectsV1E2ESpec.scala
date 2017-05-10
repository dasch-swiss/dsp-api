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
class ProjectsV1E2ESpec extends E2ESpec(ProjectsV1E2ESpec.config) with SessionJsonProtocol with TriplestoreJsonProtocol {

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(5.seconds)

    private val rdfDataObjects = List.empty[RdfDataObject]

    val rootEmail = SharedAdminTestData.rootUser.userData.email.get
    val rootEmailEnc = java.net.URLEncoder.encode(rootEmail, "utf-8")
    val testPass = java.net.URLEncoder.encode("test", "utf-8")
    val projectIri = SharedAdminTestData.imagesProjectInfo.id
    val projectIriEnc = java.net.URLEncoder.encode(projectIri, "utf-8")
    val projectShortName = SharedAdminTestData.imagesProjectInfo.shortname
    val projectShortnameEnc = java.net.URLEncoder.encode(projectShortName, "utf-8")


    "Load test data" in {
        // send POST to 'v1/store/ResetTriplestoreContent'
        val request = Post(baseApiUrl + "/v1/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 300.seconds)
    }

    "The Projects Route ('v1/projects')" when {

        "used to query for project information" should {

            "return all projects" in {
                val request = Get(baseApiUrl + s"/v1/projects") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                log.debug(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)
            }

            "return the information for a single project identified by iri" in {
                val request = Get(baseApiUrl + s"/v1/projects/$projectIriEnc") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                log.debug(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)
            }

            "return the information for a single project identified by shortname" in {
                val request = Get(baseApiUrl + s"/v1/projects/$projectShortnameEnc?identifier=shortname") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                log.debug(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)
            }
        }

        "used to modify project information" should {

            "create a new project and return the 'full' project info if the supplied shortname is unique" in {
                fail("test not implemented")
            }

            "update a project" in {
                fail("test not implemented")
            }

            "return a 'DuplicateValueException' if the supplied project shortname is not unique" in {
                fail("test not implemented")
            }

            "return 'BadRequestException' if 'shortname' is missing" in {
                fail("test not implemented")
            }
        }

        "used to query members" should {

            "return all members of a project identified by iri" in {
                val request = Get(baseApiUrl + s"/v1/projects/members/$projectIriEnc") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                log.debug(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)
            }

            "return all members of a project identified by shortname" in {
                val request = Get(baseApiUrl + s"/v1/projects/members/$projectShortnameEnc?identifier=shortname") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                log.debug(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)
            }
        }

        "used to modify members" should {

            "add user to project" in {
                fail("test not implemented")
            }

            "remove user from project" in {
                fail("test not implemented")
            }
        }
    }
}
