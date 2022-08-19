/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.admin

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.util.Timeout
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import java.net.URLEncoder
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

import org.knora.webapi.E2ESpec
import org.knora.webapi.IRI
import org.knora.webapi.e2e.ClientTestDataCollector
import org.knora.webapi.e2e.TestDataFileContent
import org.knora.webapi.e2e.TestDataFilePath
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.util.rdf.RdfModel
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionJsonProtocol
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util.AkkaHttpUtils
import org.knora.webapi.util.MutableTestIri

object ProjectsADME2ESpec {
  val config: Config = ConfigFactory.parseString("""
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
 * End-to-End (E2E) test specification for testing groups endpoint.
 */
class ProjectsADME2ESpec
    extends E2ESpec(ProjectsADME2ESpec.config)
    with SessionJsonProtocol
    with ProjectsADMJsonProtocol
    with TriplestoreJsonProtocol {

  

  private val rootEmail        = SharedTestDataADM.rootUser.email
  private val testPass         = SharedTestDataADM.testPass
  private val projectIri       = SharedTestDataADM.imagesProject.id
  private val projectIriEnc    = URLEncoder.encode(projectIri, "utf-8")
  private val projectShortname = SharedTestDataADM.imagesProject.shortname
  private val projectShortcode = SharedTestDataADM.imagesProject.shortcode

  // Directory path for generated client test data
  private val clientTestDataPath: Seq[String] = Seq("admin", "projects")

  // Collects client test data
  private val clientTestDataCollector = new ClientTestDataCollector(settings)

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
  )

  "The Projects Route ('admin/projects')" when {
    "used to query for project information" should {
      "return all projects" in {
        val request                = Get(baseApiUrl + s"/admin/projects") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        // log.debug(s"response: {}", response)
        assert(response.status === StatusCodes.OK)

        // log.debug("projects as objects: {}", AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[Seq[ProjectInfoV1]])

        val projects: Seq[ProjectADM] =
          AkkaHttpUtils.httpResponseToJson(response).fields("projects").convertTo[Seq[ProjectADM]]
        projects.size should be(8)
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "get-projects-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "return the information for a single project identified by iri" in {
        val request = Get(baseApiUrl + s"/admin/projects/iri/$projectIriEnc") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        // log.debug(s"response: {}", response)
        assert(response.status === StatusCodes.OK)
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "get-project-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "return the information for a single project identified by shortname" in {
        val request = Get(baseApiUrl + s"/admin/projects/shortname/$projectShortname") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        // log.debug(s"response: {}", response)
        assert(response.status === StatusCodes.OK)
      }

      "return the information for a single project identified by shortcode" in {
        val request = Get(baseApiUrl + s"/admin/projects/shortcode/$projectShortcode") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        // log.debug(s"response: {}", response)
        assert(response.status === StatusCodes.OK)
      }

      "return the project's restricted view settings using its IRI" in {
        val request = Get(baseApiUrl + s"/admin/projects/iri/$projectIriEnc/RestrictedViewSettings") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        logger.debug(s"response: {}", response)
        assert(response.status === StatusCodes.OK)

        val settings: ProjectRestrictedViewSettingsADM =
          AkkaHttpUtils.httpResponseToJson(response).fields("settings").convertTo[ProjectRestrictedViewSettingsADM]
        settings.size should be(Some("!512,512"))
        settings.watermark should be(Some("path_to_image"))

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "get-project-restricted-view-settings-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "return the project's restricted view settings using its shortname" in {
        val request = Get(
          baseApiUrl + s"/admin/projects/shortname/$projectShortname/RestrictedViewSettings"
        ) ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        logger.debug(s"response: {}", response)
        assert(response.status === StatusCodes.OK)

        val settings: ProjectRestrictedViewSettingsADM =
          AkkaHttpUtils.httpResponseToJson(response).fields("settings").convertTo[ProjectRestrictedViewSettingsADM]
        settings.size should be(Some("!512,512"))
        settings.watermark should be(Some("path_to_image"))
      }

      "return the project's restricted view settings using its shortcode" in {
        val request = Get(
          baseApiUrl + s"/admin/projects/shortcode/$projectShortcode/RestrictedViewSettings"
        ) ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        logger.debug(s"response: {}", response)
        assert(response.status === StatusCodes.OK)

        val settings: ProjectRestrictedViewSettingsADM =
          AkkaHttpUtils.httpResponseToJson(response).fields("settings").convertTo[ProjectRestrictedViewSettingsADM]
        settings.size should be(Some("!512,512"))
        settings.watermark should be(Some("path_to_image"))
      }
    }

    "given a custom Iri" should {
      val customProjectIri: IRI = "http://rdfh.ch/projects/wahxssy1TDqPuSk6ee8EdQ"
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

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "create-project-with-custom-Iri-request",
              fileExtension = "json"
            ),
            text = createProjectWithCustomIRIRequest
          )
        )

        val request = Post(
          baseApiUrl + s"/admin/projects",
          HttpEntity(ContentTypes.`application/json`, createProjectWithCustomIRIRequest)
        ) ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val result = AkkaHttpUtils.httpResponseToJson(response).fields("project").convertTo[ProjectADM]

        // check that the custom IRI is correctly assigned
        result.id should be(customProjectIri)

        // check the rest of project info
        result.shortcode should be("3333")
        result.shortname should be("newprojectWithIri")
        result.longname should be(Some("new project with a custom IRI"))
        result.keywords should be(Seq("projectIRI"))
        result.description should be(
          Seq(StringLiteralV2(value = "a project created with a custom IRI", language = Some("en")))
        )

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "create-project-with-custom-Iri-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )

      }

      "return 'BadRequest' if the supplied project IRI is not unique" in {
        val params =
          s"""{
             |    "id": "$customProjectIri",
             |    "shortname": "newprojectWithDuplicateIri",
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
            BasicHttpCredentials(rootEmail, testPass)
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

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "create-project-request",
              fileExtension = "json"
            ),
            text = createProjectRequest
          )
        )
        val request = Post(
          baseApiUrl + s"/admin/projects",
          HttpEntity(ContentTypes.`application/json`, createProjectRequest)
        ) ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        logger.debug(s"response: {}", response)
        response.status should be(StatusCodes.OK)

        val result = AkkaHttpUtils.httpResponseToJson(response).fields("project").convertTo[ProjectADM]
        result.shortname should be("newproject")
        result.shortcode should be("1111")
        result.longname should be(Some("project longname"))
        result.description should be(Seq(StringLiteralV2(value = "project description", language = Some("en"))))
        result.keywords should be(Seq("keywords"))
        result.logo should be(Some("/fu/bar/baz.jpg"))
        result.status should be(true)
        result.selfjoin should be(false)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "create-project-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )

        newProjectIri.set(result.id)
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
            BasicHttpCredentials(rootEmail, testPass)
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
            BasicHttpCredentials(rootEmail, testPass)
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
            BasicHttpCredentials(rootEmail, testPass)
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
            BasicHttpCredentials(rootEmail, testPass)
          )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.BadRequest)
      }

      "UPDATE a project" in {

        val updateProjectRequest: String =
          s"""{
             |    "shortname": "newproject",
             |    "longname": "updated project longname",
             |    "description": [{"value": "updated project description", "language": "en"}],
             |    "keywords": ["updated", "keywords"],
             |    "logo": "/fu/bar/baz-updated.jpg",
             |    "status": true,
             |    "selfjoin": true
             |}""".stripMargin

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-project-request",
              fileExtension = "json"
            ),
            text = updateProjectRequest
          )
        )
        val projectIriEncoded = URLEncoder.encode(newProjectIri.get, "utf-8")
        val request = Put(
          baseApiUrl + s"/admin/projects/iri/" + projectIriEncoded,
          HttpEntity(ContentTypes.`application/json`, updateProjectRequest)
        ) ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val result: ProjectADM = AkkaHttpUtils.httpResponseToJson(response).fields("project").convertTo[ProjectADM]
        result.shortname should be("newproject")
        result.shortcode should be("1111")
        result.longname should be(Some("updated project longname"))
        result.description should be(Seq(StringLiteralV2(value = "updated project description", language = Some("en"))))
        result.keywords.sorted should be(Seq("updated", "keywords").sorted)
        result.logo should be(Some("/fu/bar/baz-updated.jpg"))
        result.status should be(true)
        result.selfjoin should be(true)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-project-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "UPDATE a project with multiple description" in {
        val updateProjectMultipleDescriptionRequest: String =
          s"""{
             |    "description": [
             |                    {"value": "Test Project", "language": "en"},
             |                    {"value": "Test Project", "language": "se"}
             |                    ]
             |}""".stripMargin

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-project-with-multiple-description-request",
              fileExtension = "json"
            ),
            text = updateProjectMultipleDescriptionRequest
          )
        )
        val projectIriEncoded = URLEncoder.encode(newProjectIri.get, "utf-8")
        val request = Put(
          baseApiUrl + s"/admin/projects/iri/" + projectIriEncoded,
          HttpEntity(ContentTypes.`application/json`, updateProjectMultipleDescriptionRequest)
        ) ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val result: ProjectADM = AkkaHttpUtils.httpResponseToJson(response).fields("project").convertTo[ProjectADM]
        result.description.size should be(2)
        result.description should contain(StringLiteralV2(value = "Test Project", language = Some("en")))
        result.description should contain(StringLiteralV2(value = "Test Project", language = Some("se")))

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-project-with-multiple-description-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "DELETE a project" in {
        val projectIriEncoded = URLEncoder.encode(newProjectIri.get, "utf-8")
        val request = Delete(baseApiUrl + s"/admin/projects/iri/" + projectIriEncoded) ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        // log.debug(s"response: {}", response)
        response.status should be(StatusCodes.OK)

        val result: ProjectADM = AkkaHttpUtils.httpResponseToJson(response).fields("project").convertTo[ProjectADM]
        result.status should be(false)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "delete-project-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

    }

    "used to query members [FUNCTIONALITY]" should {
      "return all members of a project identified by iri" in {
        val request = Get(baseApiUrl + s"/admin/projects/iri/$projectIriEnc/members") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        // log.debug(s"response: {}", response)
        assert(response.status === StatusCodes.OK)

        val members: Seq[UserADM] = AkkaHttpUtils.httpResponseToJson(response).fields("members").convertTo[Seq[UserADM]]
        members.size should be(4)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "get-project-members-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "return all members of a project identified by shortname" in {
        val request = Get(baseApiUrl + s"/admin/projects/shortname/$projectShortname/members") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        // log.debug(s"response: {}", response)
        assert(response.status === StatusCodes.OK)

        val members: Seq[UserADM] = AkkaHttpUtils.httpResponseToJson(response).fields("members").convertTo[Seq[UserADM]]
        members.size should be(4)
      }

      "return all members of a project identified by shortcode" in {
        val request = Get(baseApiUrl + s"/admin/projects/shortcode/$projectShortcode/members") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        // log.debug(s"response: {}", response)
        assert(response.status === StatusCodes.OK)

        val members: Seq[UserADM] = AkkaHttpUtils.httpResponseToJson(response).fields("members").convertTo[Seq[UserADM]]
        members.size should be(4)
      }

      "return all admin members of a project identified by iri" in {
        val request = Get(baseApiUrl + s"/admin/projects/iri/$projectIriEnc/admin-members") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        // log.debug(s"response: {}", response)
        assert(response.status === StatusCodes.OK)

        val members: Seq[UserADM] = AkkaHttpUtils.httpResponseToJson(response).fields("members").convertTo[Seq[UserADM]]
        members.size should be(2)
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "get-project-admin-members-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "return all admin members of a project identified by shortname" in {
        val request = Get(baseApiUrl + s"/admin/projects/shortname/$projectShortname/admin-members") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        // log.debug(s"response: {}", response)
        assert(response.status === StatusCodes.OK)

        val members: Seq[UserADM] = AkkaHttpUtils.httpResponseToJson(response).fields("members").convertTo[Seq[UserADM]]
        members.size should be(2)
      }

      "return all admin members of a project identified by shortcode" in {
        val request = Get(baseApiUrl + s"/admin/projects/shortcode/$projectShortcode/admin-members") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        // log.debug(s"response: {}", response)
        assert(response.status === StatusCodes.OK)

        val members: Seq[UserADM] = AkkaHttpUtils.httpResponseToJson(response).fields("members").convertTo[Seq[UserADM]]
        members.size should be(2)
      }
    }

    "used to query members [PERMISSIONS]" should {
      "return members of a project to a SystemAdmin" in {
        val request = Get(baseApiUrl + s"/admin/projects/iri/$projectIriEnc/members") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
      }

      "return members of a project to a ProjectAdmin" in {
        val request = Get(baseApiUrl + s"/admin/projects/iri/$projectIriEnc/members") ~> addCredentials(
          BasicHttpCredentials(SharedTestDataADM.imagesUser01.email, testPass)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
      }

      "return `Forbidden` for members of a project to a normal user" in {
        val request = Get(baseApiUrl + s"/admin/projects/iri/$projectIriEnc/members") ~> addCredentials(
          BasicHttpCredentials(SharedTestDataADM.imagesUser02.email, testPass)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.Forbidden)
      }

      "return admin-members of a project to a SystemAdmin" in {
        val request = Get(baseApiUrl + s"/admin/projects/iri/$projectIriEnc/admin-members") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
      }

      "return admin-members of a project to a ProjectAdmin" in {
        val request = Get(baseApiUrl + s"/admin/projects/iri/$projectIriEnc/admin-members") ~> addCredentials(
          BasicHttpCredentials(SharedTestDataADM.imagesUser01.email, testPass)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
      }

      "return `Forbidden` for admin-members of a project to a normal user" in {
        val request = Get(baseApiUrl + s"/admin/projects/iri/$projectIriEnc/admin-members") ~> addCredentials(
          BasicHttpCredentials(SharedTestDataADM.imagesUser02.email, testPass)
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
        // log.debug(s"response: {}", response)
        assert(response.status === StatusCodes.OK)

        val keywords: Seq[String] = AkkaHttpUtils.httpResponseToJson(response).fields("keywords").convertTo[Seq[String]]
        keywords.size should be(21)
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "get-keywords-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "return all keywords for a single project" in {
        val incunabulaIriEnc = URLEncoder.encode(SharedTestDataADM.incunabulaProject.id, "utf-8")
        val request = Get(baseApiUrl + s"/admin/projects/iri/$incunabulaIriEnc/Keywords") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        // log.debug(s"response: {}", response)
        assert(response.status === StatusCodes.OK)

        val keywords: Seq[String] = AkkaHttpUtils.httpResponseToJson(response).fields("keywords").convertTo[Seq[String]]
        keywords should be(SharedTestDataADM.incunabulaProject.keywords)
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "get-project-keywords-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "return empty list for a project without keywords" in {
        val dokubibIriEnc = URLEncoder.encode(SharedTestDataADM.dokubibProject.id, "utf-8")
        val request = Get(baseApiUrl + s"/admin/projects/iri/$dokubibIriEnc/Keywords") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        // log.debug(s"response: {}", response)
        assert(response.status === StatusCodes.OK)

        val keywords: Seq[String] = AkkaHttpUtils.httpResponseToJson(response).fields("keywords").convertTo[Seq[String]]
        keywords should be(Seq.empty[String])
      }

      "return 'NotFound' when the project IRI is unknown" in {
        val notexistingIriEnc = URLEncoder.encode("http://rdfh.ch/projects/notexisting", "utf-8")
        val request = Get(baseApiUrl + s"/admin/projects/iri/$notexistingIriEnc/Keywords") ~> addCredentials(
          BasicHttpCredentials(rootEmail, testPass)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        // log.debug(s"response: {}", response)
        assert(response.status === StatusCodes.NotFound)
      }
    }

    "used to dump project data" should {
      "return a TriG file containing all data from a project" in {
        val anythingProjectIriEnc = URLEncoder.encode(SharedTestDataADM.anythingProject.id, "utf-8")
        val request = Get(baseApiUrl + s"/admin/projects/iri/$anythingProjectIriEnc/AllData") ~> addCredentials(
          BasicHttpCredentials(SharedTestDataADM.anythingAdminUser.email, testPass)
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
            "http://www.knora.org/data/admin"
          )
        )
      }
    }
  }
}
