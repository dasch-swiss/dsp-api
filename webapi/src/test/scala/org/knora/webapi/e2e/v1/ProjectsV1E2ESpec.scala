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
import org.knora.webapi.messages.v1.responder.projectmessages.{ProjectInfoV1, ProjectV1JsonProtocol}
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionJsonProtocol
import org.knora.webapi.messages.v1.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.util.{AkkaHttpUtils, MutableTestIri}
import org.knora.webapi.{E2ESpec, IRI, SharedAdminTestData}
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

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(5.seconds)

    implicit override val log = akka.event.Logging(system, this.getClass())

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

                log.debug("projects as objects: {}", AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[Seq[ProjectInfoV1]])

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

            val newProjectIri = new MutableTestIri

            "create a new project and return the project info if the supplied shortname is unique" in {

                val params =
                    s"""
                       |{
                       |    "shortname": "newproject",
                       |    "longname": "project longname",
                       |    "description": "project description",
                       |    "keywords": "keywords",
                       |    "logo": "/fu/bar/baz.jpg",
                       |    "status": true,
                       |    "selfjoin": false
                       |}
                """.stripMargin


                val request = Post(baseApiUrl + s"/v1/projects", HttpEntity(ContentTypes.`application/json`, params))
                val response: HttpResponse = singleAwaitingRequest(request)
                //println(s"response: ${response.toString}")
                response.status should be (StatusCodes.OK)

                val jsonResult: Map[String, JsValue] = AkkaHttpUtils.httpResponseToJson(response).fields("project_info").asJsObject.fields
                jsonResult("shortname").convertTo[String] should be ("newproject")
                jsonResult("longname").convertTo[String] should be ("project longname")
                jsonResult("description").convertTo[String] should be ("project description")
                jsonResult("keywords").convertTo[String] should be ("keywords")
                jsonResult("logo").convertTo[String] should be ("/fu/bar/baz.jpg")
                jsonResult("status").convertTo[Boolean] should be (true)
                jsonResult("selfjoin").convertTo[Boolean] should be (false)

                val iri = jsonResult("id").convertTo[String]
                newProjectIri.set(iri)
                //println(s"iri: ${newProjectIri.get}")

            }

            "return a 'BadRequest' if the supplied project shortname during creation is not unique" in {
                val params =
                    s"""
                       |{
                       |    "shortname": "newproject",
                       |    "longname": "project longname",
                       |    "description": "project description",
                       |    "keywords": "keywords",
                       |    "logo": "/fu/bar/baz.jpg",
                       |    "status": true,
                       |    "selfjoin": false
                       |}
                """.stripMargin


                val request = Post(baseApiUrl + s"/v1/projects", HttpEntity(ContentTypes.`application/json`, params))
                val response: HttpResponse = singleAwaitingRequest(request)
                //println(s"response: ${response.toString}")
                response.status should be (StatusCodes.BadRequest)
            }

            "return 'BadRequest' if 'shortname' during creation is missing" in {
                val params =
                    s"""
                       |{
                       |    "longname": "project longname",
                       |    "description": "project description",
                       |    "keywords": "keywords",
                       |    "logo": "/fu/bar/baz.jpg",
                       |    "status": true,
                       |    "selfjoin": false
                       |}
                """.stripMargin


                val request = Post(baseApiUrl + s"/v1/projects", HttpEntity(ContentTypes.`application/json`, params))
                val response: HttpResponse = singleAwaitingRequest(request)
                //println(s"response: ${response.toString}")
                response.status should be (StatusCodes.BadRequest)
            }

            "update a project" in {

                val params =
                    s"""
                       |{
                       |    "shortname": "newproject",
                       |    "longname": "updated project longname",
                       |    "description": "updated project description",
                       |    "keywords": "updated keywords",
                       |    "logo": "/fu/bar/baz-updated.jpg",
                       |    "institution": "http://data.knora.org/institutions/dhlab-basel",
                       |    "status": false,
                       |    "selfjoin": true
                       |}
                """.stripMargin

                val projectIriEncoded = java.net.URLEncoder.encode(newProjectIri.get, "utf-8")
                val request = Put(baseApiUrl + s"/v1/projects/" + projectIriEncoded, HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                //println(s"response: ${response.toString}")
                response.status should be (StatusCodes.OK)

                val jsonResult: Map[String, JsValue] = AkkaHttpUtils.httpResponseToJson(response).fields("project_info").asJsObject.fields
                jsonResult("shortname").convertTo[String] should be ("newproject")
                jsonResult("longname").convertTo[String] should be ("updated project longname")
                jsonResult("description").convertTo[String] should be ("updated project description")
                jsonResult("keywords").convertTo[String] should be ("updated keywords")
                jsonResult("logo").convertTo[String] should be ("/fu/bar/baz-updated.jpg")
                jsonResult("status").convertTo[Boolean] should be (false)
                jsonResult("selfjoin").convertTo[Boolean] should be (true)
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

            "return all admin members of a project identified by iri" in {
                val request = Get(baseApiUrl + s"/v1/projects/admin-members/$projectIriEnc") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                log.debug(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)
            }

            "return all admin members of a project identified by shortname" in {
                val request = Get(baseApiUrl + s"/v1/projects/admin-members/$projectShortnameEnc?identifier=shortname") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                log.debug(s"response: ${response.toString}")
                assert(response.status === StatusCodes.OK)
            }
        }
    }
}
