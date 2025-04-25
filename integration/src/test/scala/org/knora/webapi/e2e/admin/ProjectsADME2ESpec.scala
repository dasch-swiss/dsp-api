/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.admin

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.apache.pekko.http.scaladsl.model.ContentTypes
import org.apache.pekko.http.scaladsl.model.HttpEntity
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.apache.pekko.util.Timeout
import zio.json.*

import java.net.URLEncoder
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.*

import org.knora.webapi.E2ESpec
import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.IntegrationTestAdminJsonProtocol.*
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.util.rdf.RdfModel
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.admin.api.model.*
import org.knora.webapi.slice.admin.api.model.ProjectMembersGetResponseADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.SelfJoin
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Status
import org.knora.webapi.util.AkkaHttpUtils
import org.knora.webapi.util.MutableTestIri

/**
 * End-to-End (E2E) test specification for testing groups endpoint.
 */
class ProjectsADME2ESpec extends E2ESpec with SprayJsonSupport {

  private val rootEmail        = SharedTestDataADM.rootUser.email
  private val testPass         = SharedTestDataADM.testPass
  private val projectIri       = SharedTestDataADM.imagesProject.id
  private val projectIriEnc    = URLEncoder.encode(projectIri.value, "utf-8")
  private val projectShortname = SharedTestDataADM.imagesProject.shortname
  private val projectShortcode = SharedTestDataADM.imagesProject.shortcode

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/project_data/anything-data.ttl",
      name = "http://www.knora.org/data/0001/anything",
    ),
  )

  s"The Projects Route 'admin/projects'" when {
    "used to query for project information" should {
      "return all projects excluding built-in system projects" in {
        val request  = Get(baseApiUrl + s"/admin/projects") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
        val response = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)

        val projects: Seq[Project] =
          AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[Seq[Project]]
        projects.size should be(6)
      }

      "return the information for a single project identified by iri" in {
        val request = Get(baseApiUrl + s"/admin/projects/iri/$projectIriEnc") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
      }

      "return the information for a single project identified by shortname" in {
        val request = Get(baseApiUrl + s"/admin/projects/shortname/$projectShortname") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
      }

      "return the information for a single project identified by shortcode" in {
        val request = Get(baseApiUrl + s"/admin/projects/shortcode/$projectShortcode") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
      }

      "return the project's restricted view settings using its IRI" in {
        val request = Get(baseApiUrl + s"/admin/projects/iri/$projectIriEnc/RestrictedViewSettings") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)

        val settings =
          ProjectRestrictedViewSettingsGetResponseADM.codec
            .decodeJson(responseToString(response))
            .getOrElse(throw new AssertionError(s"Could not decode response for ${responseToString(response)}."))
            .settings

        settings.size should be(Some("!512,512"))
        settings.watermark should be(false)
      }

      "return the project's restricted view settings using its shortname" in {
        val request = Get(
          baseApiUrl + s"/admin/projects/shortname/$projectShortname/RestrictedViewSettings",
        ) ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)

        val settings: ProjectRestrictedViewSettingsADM =
          ProjectRestrictedViewSettingsGetResponseADM.codec
            .decodeJson(responseToString(response))
            .getOrElse(throw new AssertionError(s"Could not decode response for ${responseToString(response)}."))
            .settings

        settings.size should be(Some("!512,512"))
        settings.watermark should be(false)
      }

      "return the project's restricted view settings using its shortcode" in {
        val request = Get(
          baseApiUrl + s"/admin/projects/shortcode/$projectShortcode/RestrictedViewSettings",
        ) ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)

        val settings: ProjectRestrictedViewSettingsADM =
          ProjectRestrictedViewSettingsGetResponseADM.codec
            .decodeJson(responseToString(response))
            .getOrElse(throw new AssertionError(s"Could not decode response for ${responseToString(response)}."))
            .settings

        settings.size should be(Some("!512,512"))
        settings.watermark should be(false)
      }
    }

    "given a custom Iri" should {
      val customProjectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/wahxssy1TDqPuSk6ee8EdQ")
      "CREATE a new project with the provided custom Iri" in {

        val createProjectWithCustomIRIRequest: String =
          s"""{
             |    "id": "$customProjectIri",
             |    "shortname": "newprojectWithIri",
             |    "shortcode": "3333",
             |    "longname": "new project with a custom IRI",
             |    "description": [{"value": "a project created with a custom IRI", "language": "en"}],
             |    "keywords": ["projectIRI"],
             |    "logo": "/fu/bar/baz.jpg",
             |    "status": true,
             |    "selfjoin": false
             |}""".stripMargin

        val request = Post(
          baseApiUrl + s"/admin/projects",
          HttpEntity(ContentTypes.`application/json`, createProjectWithCustomIRIRequest),
        ) ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val result = AkkaHttpUtils.httpResponseToJson(response).fields("project").convertTo[Project]

        // check that the custom IRI is correctly assigned
        result.id should be(customProjectIri)

        // check the rest of project info
        result.shortcode.value should be("3333")
        result.shortname.value should be("newprojectWithIri")
        result.longname.map(_.value) should be(Some("new project with a custom IRI"))
        result.keywords should be(Seq("projectIRI"))
        result.description should be(Seq(StringLiteralV2.from("a project created with a custom IRI", Some("en"))))
      }

      "return 'BadRequest' if the supplied project IRI is not unique" in {
        val params =
          s"""{
             |    "id": "$customProjectIri",
             |    "shortname": "newWithDuplicateIri",
             |    "shortcode": "2222",
             |    "longname": "new project with a duplicate custom invalid IRI",
             |    "description": [{"value": "a project created with a duplicate custom IRI", "language": "en"}],
             |    "keywords": ["projectDuplicateIRI"],
             |    "logo": "/fu/bar/baz.jpg",
             |    "status": true,
             |    "selfjoin": false
             |}""".stripMargin

        val request =
          Post(baseApiUrl + s"/admin/projects", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
            BasicHttpCredentials(rootEmail, testPass),
          )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.BadRequest)

        val errorMessage: String = Await.result(Unmarshal(response.entity).to[String], 1.second)
        val invalidIri: Boolean =
          errorMessage.contains(s"IRI: '$customProjectIri' already exists, try another one.")
        invalidIri should be(true)
      }
    }

    "used to modify project information" should {
      val newProjectIri = new MutableTestIri

      "CREATE a new project and return the project info if the supplied shortname is unique" in {
        val createProjectRequest: String =
          s"""{
             |    "shortname": "newproject",
             |    "shortcode": "1111",
             |    "longname": "project longname",
             |    "description": [{"value": "project description", "language": "en"}],
             |    "keywords": ["keywords"],
             |    "logo": "/fu/bar/baz.jpg",
             |    "status": true,
             |    "selfjoin": false
             |}""".stripMargin

        val request = Post(
          baseApiUrl + s"/admin/projects",
          HttpEntity(ContentTypes.`application/json`, createProjectRequest),
        ) ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val result = AkkaHttpUtils.httpResponseToJson(response).fields("project").convertTo[Project]
        result.shortname.value should be("newproject")
        result.shortcode.value should be("1111")
        result.longname.map(_.value) should be(Some("project longname"))
        result.description should be(Seq(StringLiteralV2.from(value = "project description", language = Some("en"))))
        result.keywords should be(Seq("keywords"))
        result.logo.map(_.value) should be(Some("/fu/bar/baz.jpg"))
        result.status should be(Status.Active)
        result.selfjoin should be(SelfJoin.CannotJoin)

        newProjectIri.set(result.id.value)
      }

      "return a 'BadRequest' if the supplied project shortname during creation is not unique" in {
        val params =
          s"""
             |{
             |    "shortname": "newproject",
             |    "shortcode": "1112",
             |    "longname": "project longname",
             |    "description": [{"value": "project description", "language": "en"}],
             |    "keywords": ["keywords"],
             |    "logo": "/fu/bar/baz.jpg",
             |    "status": true,
             |    "selfjoin": false
             |}
                """.stripMargin

        val request =
          Post(baseApiUrl + s"/admin/projects", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
            BasicHttpCredentials(rootEmail, testPass),
          )
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.BadRequest)
      }

      "return 'BadRequest' if 'shortname' during creation is missing" in {
        val params =
          s"""
             |{
             |    "shortcode": "1112",
             |    "longname": "project longname",
             |    "description": [{"value": "project description", "language": "en"}],
             |    "keywords": ["keywords"],
             |    "logo": "/fu/bar/baz.jpg",
             |    "status": true,
             |    "selfjoin": false
             |}
                """.stripMargin

        val request =
          Post(baseApiUrl + s"/admin/projects", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
            BasicHttpCredentials(rootEmail, testPass),
          )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.BadRequest)
      }

      "return 'BadRequest' if 'shortcode' during creation is missing" in {
        val params =
          s"""
             |{
             |    "shortname": "newproject2",
             |    "longname": "project longname",
             |    "description": [{"value": "project description", "language": "en"}],
             |    "keywords": ["keywords"],
             |    "logo": "/fu/bar/baz.jpg",
             |    "status": true,
             |    "selfjoin": false
             |}
                """.stripMargin

        val request =
          Post(baseApiUrl + s"/admin/projects", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
            BasicHttpCredentials(rootEmail, testPass),
          )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.BadRequest)
      }

      "return 'BadRequest' if 'project description' during creation is missing" in {
        val params =
          s"""
             |{
             |    "shortcode": "1114",
             |    "shortname": "newproject5",
             |    "longname": "project longname",
             |    "description": [],
             |    "keywords": ["keywords"],
             |    "logo": "/fu/bar/baz.jpg",
             |    "status": true,
             |    "selfjoin": false
             |}
                """.stripMargin

        val request =
          Post(baseApiUrl + s"/admin/projects", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
            BasicHttpCredentials(rootEmail, testPass),
          )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.BadRequest)
      }

      "UPDATE a project" in {

        val updateProjectRequest: String =
          s"""{
             |    "shortname": "updatedproject",
             |    "longname": "updated project longname",
             |    "description": [{"value": "updated project description", "language": "en"}],
             |    "keywords": ["updated", "keywords"],
             |    "logo": "/fu/bar/baz-updated.jpg",
             |    "status": true,
             |    "selfjoin": true
             |}""".stripMargin

        val projectIriEncoded = URLEncoder.encode(newProjectIri.get, "utf-8")
        val request = Put(
          baseApiUrl + s"/admin/projects/iri/" + projectIriEncoded,
          HttpEntity(ContentTypes.`application/json`, updateProjectRequest),
        ) ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val result: Project = AkkaHttpUtils.httpResponseToJson(response).fields("project").convertTo[Project]
        result.shortname.value should be("newproject")
        result.shortcode.value should be("1111")
        result.longname.map(_.value) should be(Some("updated project longname"))
        result.description should be(
          Seq(StringLiteralV2.from(value = "updated project description", language = Some("en"))),
        )
        result.keywords.sorted should be(Seq("updated", "keywords").sorted)
        result.logo.map(_.value) should be(Some("/fu/bar/baz-updated.jpg"))
        result.status should be(Status.Active)
        result.selfjoin should be(SelfJoin.CanJoin)
      }

      "UPDATE a project with multi-language description" in {
        val updateProjectMultipleDescriptionRequest: String =
          s"""{
             |    "description": [
             |                    {"value": "Test Project", "language": "en"},
             |                    {"value": "Test Project", "language": "se"}
             |                    ]
             |}""".stripMargin

        val projectIriEncoded = URLEncoder.encode(newProjectIri.get, "utf-8")
        val request = Put(
          baseApiUrl + s"/admin/projects/iri/" + projectIriEncoded,
          HttpEntity(ContentTypes.`application/json`, updateProjectMultipleDescriptionRequest),
        ) ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val result: Project = AkkaHttpUtils.httpResponseToJson(response).fields("project").convertTo[Project]
        result.description.size should be(2)
        result.description should contain(StringLiteralV2.from(value = "Test Project", language = Some("en")))
        result.description should contain(StringLiteralV2.from(value = "Test Project", language = Some("se")))
      }

      "DELETE a project" in {
        val projectIriEncoded = URLEncoder.encode(newProjectIri.get, "utf-8")
        val request = Delete(baseApiUrl + s"/admin/projects/iri/" + projectIriEncoded) ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val result: Project = AkkaHttpUtils.httpResponseToJson(response).fields("project").convertTo[Project]
        result.status should be(Status.Inactive)
      }
    }

    "used to query members [FUNCTIONALITY]" should {
      "return all members of a project identified by iri" in {
        val request = Get(baseApiUrl + s"/admin/projects/iri/$projectIriEnc/members") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
        val members = AkkaHttpUtils.httpResponseTo[ProjectMembersGetResponseADM](response).members
        members.size should be(4)
      }

      "return all members of a project identified by shortname" in {
        val request = Get(baseApiUrl + s"/admin/projects/shortname/$projectShortname/members") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)

        val members = AkkaHttpUtils.httpResponseTo[ProjectMembersGetResponseADM](response).members
        members.size should be(4)
      }

      "return all members of a project identified by shortcode" in {
        val request = Get(baseApiUrl + s"/admin/projects/shortcode/$projectShortcode/members") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)

        val members = AkkaHttpUtils.httpResponseTo[ProjectMembersGetResponseADM](response).members
        members.size should be(4)
      }

      "return all admin members of a project identified by iri" in {
        val request = Get(baseApiUrl + s"/admin/projects/iri/$projectIriEnc/admin-members") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)

        val members = AkkaHttpUtils.httpResponseTo[ProjectAdminMembersGetResponseADM](response).members
        members.size should be(2)
      }

      "return all admin members of a project identified by shortname" in {
        val request = Get(baseApiUrl + s"/admin/projects/shortname/$projectShortname/admin-members") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)

        val members = AkkaHttpUtils.httpResponseTo[ProjectAdminMembersGetResponseADM](response).members
        members.size should be(2)
      }

      "return all admin members of a project identified by shortcode" in {
        val request = Get(baseApiUrl + s"/admin/projects/shortcode/$projectShortcode/admin-members") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)

        val members = AkkaHttpUtils.httpResponseTo[ProjectAdminMembersGetResponseADM](response).members
        members.size should be(2)
      }
    }

    "used to query members [PERMISSIONS]" should {
      "return members of a project to a SystemAdmin" in {
        val request = Get(baseApiUrl + s"/admin/projects/iri/$projectIriEnc/members") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
      }

      "return members of a project to a ProjectAdmin" in {
        val request = Get(baseApiUrl + s"/admin/projects/iri/$projectIriEnc/members") ~> addCredentials(
          BasicHttpCredentials(SharedTestDataADM.imagesUser01.email, testPass),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
      }

      "return `Forbidden` for members of a project to a normal user" in {
        val request = Get(baseApiUrl + s"/admin/projects/iri/$projectIriEnc/members") ~> addCredentials(
          BasicHttpCredentials(SharedTestDataADM.imagesUser02.email, testPass),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.Forbidden)
      }

      "return admin-members of a project to a SystemAdmin" in {
        val request = Get(baseApiUrl + s"/admin/projects/iri/$projectIriEnc/admin-members") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
      }

      "return admin-members of a project to a ProjectAdmin" in {
        val request = Get(baseApiUrl + s"/admin/projects/iri/$projectIriEnc/admin-members") ~> addCredentials(
          BasicHttpCredentials(SharedTestDataADM.imagesUser01.email, testPass),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
      }

      "return `Forbidden` for admin-members of a project to a normal user" in {
        val request = Get(baseApiUrl + s"/admin/projects/iri/$projectIriEnc/admin-members") ~> addCredentials(
          BasicHttpCredentials(SharedTestDataADM.imagesUser02.email, testPass),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.Forbidden)
      }
    }

    "used to query keywords" should {
      "return all unique keywords for all projects" in {
        val request =
          Get(baseApiUrl + s"/admin/projects/Keywords") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)

        val keywords: Seq[String] = AkkaHttpUtils.httpResponseToJson(response).fields("keywords").convertTo[Seq[String]]
        keywords.size should be(21)
      }

      "return all keywords for a single project" in {
        val incunabulaIriEnc = URLEncoder.encode(SharedTestDataADM.incunabulaProject.id.value, "utf-8")
        val request = Get(baseApiUrl + s"/admin/projects/iri/$incunabulaIriEnc/Keywords") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)

        val keywords: Seq[String] = AkkaHttpUtils.httpResponseToJson(response).fields("keywords").convertTo[Seq[String]]
        keywords should be(SharedTestDataADM.incunabulaProject.keywords)
      }

      "return empty list for a project without keywords" in {
        val dokubibIriEnc = URLEncoder.encode(SharedTestDataADM.dokubibProject.id.value, "utf-8")
        val request = Get(baseApiUrl + s"/admin/projects/iri/$dokubibIriEnc/Keywords") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)

        val keywords: Seq[String] = AkkaHttpUtils.httpResponseToJson(response).fields("keywords").convertTo[Seq[String]]
        keywords should be(Seq.empty[String])
      }

      "return 'NotFound' when the project IRI is unknown" in {
        val notexistingIriEnc = URLEncoder.encode("http://rdfh.ch/projects/notexisting", "utf-8")
        val request = Get(baseApiUrl + s"/admin/projects/iri/$notexistingIriEnc/Keywords") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.NotFound)
      }
    }

    "used to dump project data" should {
      "return a TriG file containing all data from a project" in {
        val anythingProjectIriEnc = URLEncoder.encode(SharedTestDataADM.anythingProject.id.value, "utf-8")
        val request = Get(baseApiUrl + s"/admin/projects/iri/$anythingProjectIriEnc/AllData") ~> addCredentials(
          BasicHttpCredentials(SharedTestDataADM.anythingAdminUser.email, testPass),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
        val trigStrFuture: Future[String] = Unmarshal(response.entity).to[String]
        val trigStr: String               = Await.result(trigStrFuture, Timeout(5.seconds).duration)
        val parsedTrig: RdfModel          = parseTrig(trigStr)
        val contextIris: Set[IRI]         = parsedTrig.getContexts

        assert(
          contextIris == Set(
            "http://www.knora.org/ontology/0001/something",
            "http://www.knora.org/ontology/0001/anything",
            "http://www.knora.org/data/0001/anything",
            "http://www.knora.org/data/permissions",
            "http://www.knora.org/data/admin",
          ),
        )
      }
    }

    "used to set RestrictedViewSize by project IRI" should {
      "return requested value to be set with 200 Response Status" in {
        val encodedIri = URLEncoder.encode(SharedTestDataADM.imagesProject.id.value, "utf-8")
        val payload    = """{"size":"pct:1"}"""
        val request =
          Post(
            baseApiUrl + s"/admin/projects/iri/$encodedIri/RestrictedViewSettings",
            HttpEntity(ContentTypes.`application/json`, payload),
          ) ~> addCredentials(
            BasicHttpCredentials(rootEmail, testPass),
          )
        val response = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
        assert(responseToString(response) === """{"size":"pct:1"}""")
      }

      "return the `BadRequest` if the size value is invalid" in {
        val encodedIri = URLEncoder.encode(SharedTestDataADM.imagesProject.id.value, "utf-8")
        val payload    = """{"size":"pct:0"}"""
        val request =
          Post(
            baseApiUrl + s"/admin/projects/iri/$encodedIri/RestrictedViewSettings",
            HttpEntity(ContentTypes.`application/json`, payload),
          ) ~> addCredentials(
            BasicHttpCredentials(rootEmail, testPass),
          )
        val response: HttpResponse = singleAwaitingRequest(request)
        val result: String         = responseToString(response)
        assert(response.status === StatusCodes.BadRequest)
        assert(result.contains("Invalid RestrictedViewSize: pct:0"))
      }

      "return `Forbidden` for the user who is not a system nor project admin" in {
        val encodedIri = URLEncoder.encode(SharedTestDataADM.imagesProject.id.value, "utf-8")
        val payload    = """{"size":"pct:1"}"""
        val request =
          Post(
            baseApiUrl + s"/admin/projects/iri/$encodedIri/RestrictedViewSettings",
            HttpEntity(ContentTypes.`application/json`, payload),
          ) ~> addCredentials(
            BasicHttpCredentials(SharedTestDataADM.imagesUser02.email, testPass),
          )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.Forbidden)
      }
    }

    "used to set RestrictedViewSize by project Shortcode" should {
      "when setting watermark to false return default size with 200 Response Status" in {
        val shortcode = SharedTestDataADM.imagesProject.shortcode
        val payload = """
                        |{
                        |   "watermark": false
                        |}""".stripMargin
        val request =
          Post(
            baseApiUrl + s"/admin/projects/shortcode/$shortcode/RestrictedViewSettings",
            HttpEntity(ContentTypes.`application/json`, payload),
          ) ~> addCredentials(
            BasicHttpCredentials(rootEmail, testPass),
          )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
        assert(responseToString(response) === """{"size":"!128,128"}""")
      }

      "return the `BadRequest` if the size value is invalid" in {
        val shortcode = SharedTestDataADM.imagesProject.shortcode
        val payload   = """{"size":"pct:0"}"""
        val request =
          Post(
            baseApiUrl + s"/admin/projects/shortcode/$shortcode/RestrictedViewSettings",
            HttpEntity(ContentTypes.`application/json`, payload),
          ) ~> addCredentials(
            BasicHttpCredentials(rootEmail, testPass),
          )
        val response: HttpResponse = singleAwaitingRequest(request)
        val result: String         = responseToString(response)
        assert(response.status === StatusCodes.BadRequest)
        assert(result.contains("Invalid RestrictedViewSize: pct:0"))
      }

      "return `Forbidden` for the user who is not a system nor project admin" in {
        val shortcode = SharedTestDataADM.imagesProject.shortcode
        val payload   = """{"size":"pct:1"}"""
        val request =
          Post(
            baseApiUrl + s"/admin/projects/shortcode/$shortcode/RestrictedViewSettings",
            HttpEntity(ContentTypes.`application/json`, payload),
          ) ~> addCredentials(
            BasicHttpCredentials(SharedTestDataADM.imagesUser02.email, testPass),
          )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.Forbidden)
      }
    }
  }
}
