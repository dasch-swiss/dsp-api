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
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.RouteTestTimeout
import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol._
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, StringLiteralV2, TriplestoreJsonProtocol}
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionJsonProtocol
import org.knora.webapi.util.{AkkaHttpUtils, MutableTestIri}
import org.knora.webapi.{E2ESpec, SharedTestDataADM}
import spray.json._

import scala.concurrent.duration._


object ProjectsADME2ESpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing groups endpoint.
  */
class ProjectsADME2ESpec extends E2ESpec(ProjectsADME2ESpec.config) with SessionJsonProtocol with ProjectsADMJsonProtocol with TriplestoreJsonProtocol {

    private implicit def default(implicit system: ActorSystem) = RouteTestTimeout(30.seconds)

    implicit override lazy val log = akka.event.Logging(system, this.getClass())

    private val rdfDataObjects = List.empty[RdfDataObject]

    private val rootEmail = SharedTestDataADM.rootUser.email
    private val rootEmailEnc = java.net.URLEncoder.encode(rootEmail, "utf-8")
    private val testPass = java.net.URLEncoder.encode("test", "utf-8")
    private val projectIri = SharedTestDataADM.imagesProject.id
    private val projectIriEnc = java.net.URLEncoder.encode(projectIri, "utf-8")
    private val projectShortName = SharedTestDataADM.imagesProject.shortname
    private val projectShortnameEnc = java.net.URLEncoder.encode(projectShortName, "utf-8")
    private val projectShortcode = SharedTestDataADM.imagesProject.shortcode.get
    private val projectShortcodeEnc = java.net.URLEncoder.encode(projectShortcode, "utf-8")


    "Load test data" in {
        // send POST to 'v1/store/ResetTriplestoreContent'
        val request = Post(baseApiUrl + "/admin/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 300.seconds)
    }

    "The Projects Route ('admin/projects')" when {

        "used to query for project information" should {

            "return all projects" in {
                val request = Get(baseApiUrl + s"/admin/projects") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)

                // log.debug("projects as objects: {}", AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[Seq[ProjectInfoV1]])

                val projects: Seq[ProjectADM] = AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[Seq[ProjectADM]]
                projects.size should be (8)

            }

            "return the information for a single project identified by iri" in {
                val request = Get(baseApiUrl + s"/admin/projects/$projectIriEnc") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)
            }

            "return the information for a single project identified by shortname" in {
                val request = Get(baseApiUrl + s"/admin/projects/$projectShortnameEnc?identifier=shortname") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)
            }

            "return the information for a single project identified by shortcode" in {
                val request = Get(baseApiUrl + s"/admin/projects/$projectShortcodeEnc?identifier=shortcode") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)
            }
        }

        "used to modify project information" should {

            val newProjectIri = new MutableTestIri

            "CREATE a new project and return the project info if the supplied shortname is unique" in {

                val params =
                    s"""
                       |{
                       |    "shortname": "newproject",
                       |    "shortcode": "1111",
                       |    "longname": "project longname",
                       |    "description": [{"value": "project description", "language": "en"}],
                       |    "keywords": ["keywords"],
                       |    "logo": "/fu/bar/baz.jpg",
                       |    "status": true,
                       |    "selfjoin": false
                       |}
                """.stripMargin


                val request = Post(baseApiUrl + s"/admin/projects", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                log.debug(s"response: {}", response)
                response.status should be (StatusCodes.OK)

                val result = AkkaHttpUtils.httpResponseToJson(response).fields("project").convertTo[ProjectADM]
                result.shortname should be ("newproject")
                result.shortcode should be (Some("1111"))
                result.longname should be (Some("project longname"))
                result.description should be (Seq(StringLiteralV2(value = "project description", language = Some("en"))))
                result.keywords should be (Seq("keywords"))
                result.logo should be (Some("/fu/bar/baz.jpg"))
                result.status should be (true)
                result.selfjoin should be (false)

                newProjectIri.set(result.id)
                // log.debug("newProjectIri: {}", newProjectIri.get)

            }

            "return a 'BadRequest' if the supplied project shortname during creation is not unique" in {
                val params =
                    s"""
                       |{
                       |    "shortname": "newproject",
                       |    "shortcode"; "1112",
                       |    "longname": "project longname",
                       |    "description": [{"value": "project description", "language": "en"}],
                       |    "keywords": ["keywords"],
                       |    "logo": "/fu/bar/baz.jpg",
                       |    "status": true,
                       |    "selfjoin": false
                       |}
                """.stripMargin


                val request = Post(baseApiUrl + s"/admin/projects", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                response.status should be (StatusCodes.BadRequest)
            }

            "return 'BadRequest' if 'shortname' during creation is missing" in {
                val params =
                    s"""
                       |{
                       |    "shortcode"; "1112",
                       |    "longname": "project longname",
                       |    "description": [{"value": "project description", "language": "en"}],
                       |    "keywords": ["keywords"],
                       |    "logo": "/fu/bar/baz.jpg",
                       |    "status": true,
                       |    "selfjoin": false
                       |}
                """.stripMargin


                val request = Post(baseApiUrl + s"/admin/projects", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                response.status should be (StatusCodes.BadRequest)
            }

            "return 'BadRequest' if 'shortcode' during creation is missing" in {
                val params =
                    s"""
                       |{
                       |    "shortname"; "newproject2",
                       |    "longname": "project longname",
                       |    "description": [{"value": "project description", "language": "en"}],
                       |    "keywords": ["keywords"],
                       |    "logo": "/fu/bar/baz.jpg",
                       |    "status": true,
                       |    "selfjoin": false
                       |}
                """.stripMargin


                val request = Post(baseApiUrl + s"/admin/projects", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                response.status should be (StatusCodes.BadRequest)
            }

            "UPDATE a project" in {

                val params =
                    s"""
                       |{
                       |    "shortname": "newproject",
                       |    "longname": "updated project longname",
                       |    "description": [{"value": "updated project description", "language": "en"}],
                       |    "keywords": ["updated", "keywords"],
                       |    "logo": "/fu/bar/baz-updated.jpg",
                       |    "status": true,
                       |    "selfjoin": true
                       |}
                """.stripMargin

                val projectIriEncoded = java.net.URLEncoder.encode(newProjectIri.get, "utf-8")
                val request = Put(baseApiUrl + s"/admin/projects/" + projectIriEncoded, HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                response.status should be (StatusCodes.OK)

                val result: ProjectADM = AkkaHttpUtils.httpResponseToJson(response).fields("project").convertTo[ProjectADM]
                result.shortname should be ("newproject")
                result.shortcode should be (Some("1111"))
                result.longname should be (Some("updated project longname"))
                result.description should be (Seq(StringLiteralV2(value = "updated project description", language = Some("en"))))
                result.keywords.sorted should be (Seq("updated", "keywords").sorted)
                result.logo should be (Some("/fu/bar/baz-updated.jpg"))
                result.status should be (true)
                result.selfjoin should be (true)
            }

            "DELETE a project" in {

                val projectIriEncoded = java.net.URLEncoder.encode(newProjectIri.get, "utf-8")
                val request = Delete(baseApiUrl + s"/admin/projects/" + projectIriEncoded) ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                response.status should be (StatusCodes.OK)

                val result: ProjectADM = AkkaHttpUtils.httpResponseToJson(response).fields("project").convertTo[ProjectADM]
                result.status should be (false)
            }

        }

        "used to query members" should {

            "return all members of a project identified by iri" in {
                val request = Get(baseApiUrl + s"/admin/projects/members/$projectIriEnc") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)

                val members: Seq[UserADM] = AkkaHttpUtils.httpResponseToJson(response).fields("members").convertTo[Seq[UserADM]]
                members.size should be (4)
            }

            "return all members of a project identified by shortname" in {
                val request = Get(baseApiUrl + s"/admin/projects/members/$projectShortnameEnc?identifier=shortname") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)

                val members: Seq[UserADM] = AkkaHttpUtils.httpResponseToJson(response).fields("members").convertTo[Seq[UserADM]]
                members.size should be (4)
            }

            "return all admin members of a project identified by iri" in {
                val request = Get(baseApiUrl + s"/admin/projects/admin-members/$projectIriEnc") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)

                val members: Seq[UserADM] = AkkaHttpUtils.httpResponseToJson(response).fields("members").convertTo[Seq[UserADM]]
                members.size should be (2)
            }

            "return all admin members of a project identified by shortname" in {
                val request = Get(baseApiUrl + s"/admin/projects/admin-members/$projectShortnameEnc?identifier=shortname") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)

                val members: Seq[UserADM] = AkkaHttpUtils.httpResponseToJson(response).fields("members").convertTo[Seq[UserADM]]
                members.size should be (2)
            }
        }

        "used to query keywords" should {

            "return all unique keywords for all projects" in {
                val request = Get(baseApiUrl + s"/admin/projects/keywords") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)

                val keywords: Seq[String] = AkkaHttpUtils.httpResponseToJson(response).fields("keywords").convertTo[Seq[String]]
                keywords.size should be (18)
            }

            "return all keywords for a single project" in {
                val incunabulaIriEnc = java.net.URLEncoder.encode(SharedTestDataADM.incunabulaProject.id, "utf-8")
                val request = Get(baseApiUrl + s"/admin/projects/keywords/$incunabulaIriEnc") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)

                val keywords: Seq[String] = AkkaHttpUtils.httpResponseToJson(response).fields("keywords").convertTo[Seq[String]]
                keywords should be (SharedTestDataADM.incunabulaProject.keywords)
            }

            "return empty list for a project without keywords" in {
                val anythingIriEnc = java.net.URLEncoder.encode(SharedTestDataADM.anythingProject.id, "utf-8")
                val request = Get(baseApiUrl + s"/admin/projects/keywords/$anythingIriEnc") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                assert(response.status === StatusCodes.OK)

                val keywords: Seq[String] = AkkaHttpUtils.httpResponseToJson(response).fields("keywords").convertTo[Seq[String]]
                keywords should be (Seq.empty[String])
            }

            "return 'NotFound' when the project IRI is unknown" in {
                val notexistingIriEnc = java.net.URLEncoder.encode("http://rdfh.ch/projects/notexisting", "utf-8")
                val request = Get(baseApiUrl + s"/admin/projects/keywords/$notexistingIriEnc") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
                val response: HttpResponse = singleAwaitingRequest(request)
                // log.debug(s"response: {}", response)
                assert(response.status === StatusCodes.NotFound)
            }
        }
    }
}
