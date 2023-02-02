/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import zio._
import zio.http._
import zio.http.model.Status
import zio.mock.Expectation
import zio.prelude.Validation
import zio.test._

import java.net.URLEncoder
import java.nio.file

import dsp.errors.BadRequestException
import dsp.valueobjects.Iri._
import dsp.valueobjects.Project._
import dsp.valueobjects.V2
import dsp.valueobjects.V2._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.http.middleware.AuthenticationMiddleware
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectCreatePayloadADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectDataGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectOperationResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectUpdatePayloadADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsGetResponseADM
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.responders.admin.ProjectsService
import org.knora.webapi.responders.admin.ProjectsServiceMock

object ProjectsRouteZSpec extends ZIOSpecDefault {

  /**
   * Paths
   */
  private val basePathProjects: Path          = !! / "admin" / "projects"
  private val basePathProjectsIri: Path       = !! / "admin" / "projects" / "iri"
  private val basePathProjectsShortname: Path = !! / "admin" / "projects" / "shortname"
  private val basePathProjectsShortcode: Path = !! / "admin" / "projects" / "shortcode"

  /**
   * Creates a [[ProjectADM]] with empty content or optionally with a given ID.
   */
  private def getProjectADM(id: String = "") =
    ProjectADM(
      id = id,
      shortname = "",
      shortcode = "",
      longname = None,
      description = Seq(V2.StringLiteralV2("", None)),
      keywords = Seq.empty,
      logo = None,
      ontologies = Seq.empty,
      status = true,
      selfjoin = false
    )

  /**
   * Returns a ZIO effect that requires a [[ProjectsService]] (so that a mock can be provided) that applies the
   * provided [[Request]] to the `routes` of a [[ProjectsRouteZ]], returning a [[Response]].
   */
  private def applyRoutes(request: Request): ZIO[ProjectsService, Option[Nothing], Response] = ZIO
    .serviceWithZIO[ProjectsRouteZ](_.route.apply(request))
    .provideSome[ProjectsService](
      AppConfig.test,
      AuthenticationMiddleware.layer,
      AuthenticatorService.mock(Some(KnoraSystemInstances.Users.SystemUser)),
      ProjectsRouteZ.layer
    )

  /**
   * URL encodes a string, assuming utf-8
   */
  private def encode(iri: String) = URLEncoder.encode(iri, "utf-8")

  def spec = suite("ProjectsRouteZ")(
    getProjectsSpec,
    getSingleProjectSpec,
    createProjectSpec,
    deleteProjectSpec,
    updateProjectSpec,
    getAllDataSpec
  )

  val getProjectsSpec = test("get all projects") {
    val request        = Request.get(url = URL(basePathProjects))
    val expectedResult = Expectation.value[ProjectsGetResponseADM](ProjectsGetResponseADM(Seq(getProjectADM())))
    val mockService    = ProjectsServiceMock.GetProjects(expectedResult).toLayer
    for {
      response <- applyRoutes(request).provide(mockService)
      body     <- response.body.asString
    } yield assertTrue(true)
  }

  val getSingleProjectSpec = suite("get a single project by identifier")(
    test("get a project by IRI") {
      val iri        = "http://rdfh.ch/projects/0001"
      val identifier = IriIdentifier.fromString(iri).getOrElse(throw BadRequestException("Invalid IRI"))
      val request    = Request.get(url = URL(basePathProjectsIri / encode(iri)))
      val mockService: ULayer[ProjectsService] = ProjectsServiceMock
        .GetSingleProject(
          assertion = Assertion.equalTo(identifier),
          result = Expectation.valueF[ProjectIdentifierADM, ProjectGetResponseADM](id =>
            ProjectGetResponseADM(getProjectADM(ProjectIdentifierADM.getId((id))))
          )
        )
        .toLayer
      for {
        response <- applyRoutes(request).provide(mockService)
        body     <- response.body.asString
      } yield assertTrue(body.contains(iri))
    },
    test("return a BadRequest Exception if project IRI is invalid") {
      val iri     = "http://rdfh.ch/project/0001"
      val user    = KnoraSystemInstances.Users.SystemUser
      val request = Request.get(url = URL(basePathProjectsIri / encode(iri)))

      for {
        response     <- applyRoutes(request).provide(ProjectsServiceMock.empty)
        bodyAsString <- response.body.asString
      } yield assertTrue(response.status == Status.BadRequest) &&
        assertTrue(bodyAsString == """{"error":"dsp.errors.BadRequestException: Project IRI is invalid."}""")
    },
    test("get a project by shortname") {
      val shortname = "someProject"
      val identifier =
        ShortnameIdentifier.fromString(shortname).getOrElse(throw BadRequestException("Invalid Shortname"))
      val request = Request.get(url = URL(basePathProjectsShortname / shortname))
      val mockService: ULayer[ProjectsService] = ProjectsServiceMock
        .GetSingleProject(
          assertion = Assertion.equalTo(identifier),
          result = Expectation.valueF[ProjectIdentifierADM, ProjectGetResponseADM](id =>
            ProjectGetResponseADM(getProjectADM(ProjectIdentifierADM.getId((id))))
          )
        )
        .toLayer
      for {
        response <- applyRoutes(request).provide(mockService)
        body     <- response.body.asString
      } yield assertTrue(body.contains(shortname))
    },
    test("return a BadRequest Exception if shortname is invalid") {
      val shortname = "short name"
      val user      = KnoraSystemInstances.Users.SystemUser
      val request   = Request.get(url = URL(basePathProjectsShortname / shortname))

      for {
        response     <- applyRoutes(request).provide(ProjectsServiceMock.empty)
        bodyAsString <- response.body.asString
      } yield assertTrue(response.status == Status.BadRequest) &&
        assertTrue(bodyAsString == """{"error":"dsp.errors.BadRequestException: Shortname is invalid: short name"}""")
    },
    test("get a project by shortcode") {
      val shortcode = "0001"
      val identifier =
        ShortcodeIdentifier.fromString(shortcode).getOrElse(throw BadRequestException("Invalid Shortcode"))
      val request = Request.get(url = URL(basePathProjectsShortcode / shortcode))
      val mockService: ULayer[ProjectsService] = ProjectsServiceMock
        .GetSingleProject(
          assertion = Assertion.equalTo(identifier),
          result = Expectation.valueF[ProjectIdentifierADM, ProjectGetResponseADM](id =>
            ProjectGetResponseADM(getProjectADM(ProjectIdentifierADM.getId((id))))
          )
        )
        .toLayer
      for {
        response <- applyRoutes(request).provide(mockService)
        body     <- response.body.asString
      } yield assertTrue(body.contains(shortcode))
    },
    test("return a BadRequest Exception if shortcode is invalid") {
      val shortcode = "XY"
      val user      = KnoraSystemInstances.Users.SystemUser
      val request   = Request.get(url = URL(basePathProjectsShortcode / shortcode))

      for {
        response     <- applyRoutes(request).provide(ProjectsServiceMock.empty)
        bodyAsString <- response.body.asString
      } yield assertTrue(response.status == Status.BadRequest) &&
        assertTrue(bodyAsString == """{"error":"dsp.errors.BadRequestException: ShortCode is invalid: XY"}""")
    }
  )

  val createProjectSpec = suite("create a project")(
    test("successfully create a project") {
      val projectCreatePayloadString =
        """|{
           |  "shortname": "newproject",
           |  "shortcode": "3333",
           |  "longname": "project longname",
           |  "description": [{"value": "project description", "language": "en"}],
           |  "keywords": ["test project"],
           |  "status": true,
           |  "selfjoin": false
           |}
           |""".stripMargin
      val body    = Body.fromString(projectCreatePayloadString)
      val request = Request.post(url = URL(basePathProjects), body = body)
      val user    = KnoraSystemInstances.Users.SystemUser
      val projectCreatePayload: ProjectCreatePayloadADM =
        Validation
          .validateWith(
            ProjectIri.make(None),
            ShortName.make("newproject"),
            ShortCode.make("3333"),
            Name.make(Some("project longname")),
            ProjectDescription.make(Seq(StringLiteralV2("project description", Some("en")))),
            Keywords.make(Seq("test project")),
            Logo.make(None),
            ProjectStatus.make(true),
            ProjectSelfJoin.make(false)
          )(ProjectCreatePayloadADM.apply)
          .getOrElse(throw BadRequestException("Invalid Payload"))
      val expectedResult = Expectation.value[ProjectOperationResponseADM](ProjectOperationResponseADM(getProjectADM()))
      val mockService = ProjectsServiceMock
        .CreateProject(
          assertion = Assertion.equalTo((projectCreatePayload, user)),
          result = expectedResult
        )
        .toLayer
      for {
        _ <- applyRoutes(request).provide(mockService)
      } yield assertTrue(true)
    },
    test("return a BadRequest Exception if input (payload) is invalid (wrong attribute)") {
      val projectCreatePayloadString =
        """|{
           |  "shortname": "new project",
           |  "shortcode": "3333",
           |  "longname": "project longname",
           |  "description": [{"value": "project description", "language": "en"}],
           |  "keywords": ["test project"],
           |  "status": true,
           |  "selfjoin": false
           |}
           |""".stripMargin
      val body    = Body.fromString(projectCreatePayloadString)
      val request = Request.post(url = URL(basePathProjects), body = body)
      val user    = KnoraSystemInstances.Users.SystemUser
      for {
        response     <- applyRoutes(request).provide(ProjectsServiceMock.empty)
        bodyAsString <- response.body.asString
      } yield assertTrue(response.status == Status.BadRequest) &&
        assertTrue(
          bodyAsString == """{"error":"dsp.errors.BadRequestException: .shortname(Shortname is invalid: new project)"}"""
        )
    },
    test("return a BadRequest Exception if input (syntax) is invalid") {
      val projectCreatePayloadString =
        """|{
           |  "shortname": "newproject",
           |  "shortcode": "3333"
           |  "longname": "project longname",
           |  "description": [{"value": "project description", "language": "en"}],
           |  "keywords": ["test project"],
           |  "status": true,
           |  "selfjoin": false
           |}
           |""".stripMargin
      val body    = Body.fromString(projectCreatePayloadString)
      val request = Request.post(url = URL(basePathProjects), body = body)
      val user    = KnoraSystemInstances.Users.SystemUser
      for {
        response <- applyRoutes(request).provide(ProjectsServiceMock.empty)
      } yield assertTrue(response.status == Status.BadRequest)
    }
  )

  val deleteProjectSpec = suite("delete a project")(
    test("successfully delete a project by IRI") {
      val projectIri =
        ProjectIri.make("http://rdfh.ch/projects/0001").getOrElse(throw BadRequestException("Invalid project IRI"))
      val request        = Request.delete(url = URL(basePathProjectsIri / encode(projectIri.value)))
      val user           = KnoraSystemInstances.Users.SystemUser
      val expectedResult = Expectation.value[ProjectOperationResponseADM](ProjectOperationResponseADM(getProjectADM()))
      val mockService: ULayer[ProjectsService] = ProjectsServiceMock
        .DeleteProject(
          assertion = Assertion.equalTo(projectIri, user),
          result = expectedResult
        )
        .toLayer
      for {
        _ <- applyRoutes(request).provide(mockService)
      } yield assertTrue(true)
    },
    test("return a BadRequest Exception if project IRI is invalid") {
      val iri     = "http://rdfh.ch/project/0001"
      val user    = KnoraSystemInstances.Users.SystemUser
      val request = Request.delete(url = URL(basePathProjectsIri / encode(iri)))

      for {
        response     <- applyRoutes(request).provide(ProjectsServiceMock.empty)
        bodyAsString <- response.body.asString
      } yield assertTrue(response.status == Status.BadRequest) &&
        assertTrue(bodyAsString == """{"error":"dsp.errors.BadRequestException: Project IRI is invalid."}""")
    }
  )

  val updateProjectSpec = suite("update a project")(
    test("successfully update a project") {
      val projectIri =
        ProjectIri.make("http://rdfh.ch/projects/0001").getOrElse(throw BadRequestException("Invalid Project IRI"))
      val updatedShortname = ShortName.make("usn").getOrElse(throw BadRequestException("Invalid Shortname"))
      val updatedLongname =
        Name.make("updated project longname").getOrElse(throw BadRequestException("Invalid Longname"))
      val updatedDescription = ProjectDescription
        .make(Seq(V2.StringLiteralV2("updated project description", Some("en"))))
        .getOrElse(throw BadRequestException("Invalid Project Description"))
      val updatedKeywords =
        Keywords.make(Seq("updated", "keywords")).getOrElse(throw BadRequestException("Invalid Keywords"))
      val updatedLogo   = Logo.make("../logo.png").getOrElse(throw BadRequestException("Invalid Logo"))
      val projectStatus = ProjectStatus.make(true).getOrElse(throw BadRequestException("Invalid Project Status"))
      val selfJoin      = ProjectSelfJoin.make(true).getOrElse(throw BadRequestException("Invalid SelfJoin"))

      val projectUpdatePayload = ProjectUpdatePayloadADM(
        shortname = Some(updatedShortname),
        longname = Some(updatedLongname),
        description = Some(updatedDescription),
        keywords = Some(updatedKeywords),
        logo = Some(updatedLogo),
        status = Some(projectStatus),
        selfjoin = Some(selfJoin)
      )

      val projectUpdatePayloadString =
        s"""|{
            |  "shortname": "${updatedShortname.value}",
            |  "longname": "${updatedLongname.value}",
            |  "description": [{"value": "updated project description", "language": "en"}],
            |  "keywords": ["updated", "keywords"],
            |  "logo": "${updatedLogo.value}",
            |  "status": ${projectStatus.value},
            |  "selfjoin": ${selfJoin.value}
            |}
            |""".stripMargin

      val body    = Body.fromString(projectUpdatePayloadString)
      val request = Request.put(url = URL(basePathProjectsIri / encode(projectIri.value)), body = body)
      val user    = KnoraSystemInstances.Users.SystemUser

      val expectedResult = Expectation.value[ProjectOperationResponseADM](ProjectOperationResponseADM(getProjectADM()))
      val mockService = ProjectsServiceMock
        .UpdateProject(
          assertion = Assertion.equalTo((projectIri, projectUpdatePayload, user)),
          result = expectedResult
        )
        .toLayer
      for {
        _ <- applyRoutes(request).provide(mockService)
      } yield assertTrue(true)
    },
    test("return a BadRequest Exception if input (shortname) is invalid") {
      val projectIri                 = "http://rdfh.ch/projects/0001"
      val projectUpdatePayloadString = """{"shortname": "invalid shortname"}""".stripMargin
      val body                       = Body.fromString(projectUpdatePayloadString)
      val request                    = Request.put(url = URL(basePathProjectsIri / encode(projectIri)), body = body)
      val user                       = KnoraSystemInstances.Users.SystemUser

      for {
        response     <- applyRoutes(request).provide(ProjectsServiceMock.empty)
        bodyAsString <- response.body.asString
      } yield assertTrue(response.status == Status.BadRequest) &&
        assertTrue(
          bodyAsString == """{"error":"dsp.errors.BadRequestException: .shortname(Shortname is invalid: invalid shortname)"}"""
        )
    },
    test("return a BadRequest Exception if input (syntax) is invalid") {
      val projectIri                 = "http://rdfh.ch/projects/0001"
      val projectUpdatePayloadString = """{"shortname":"usn" "longname":"updated longname"}""".stripMargin
      val body                       = Body.fromString(projectUpdatePayloadString)
      val request                    = Request.put(url = URL(basePathProjectsIri / encode(projectIri)), body = body)
      val user                       = KnoraSystemInstances.Users.SystemUser

      for {
        response <- applyRoutes(request).provide(ProjectsServiceMock.empty)
      } yield assertTrue(response.status == Status.BadRequest)
    },
    test("return a BadRequest Exception if project IRI is invalid") {
      val projectIri                 = "http://rdfh.ch/project/0001"
      val projectUpdatePayloadString = """{"shortname":"usn"}""".stripMargin
      val body                       = Body.fromString(projectUpdatePayloadString)
      val request                    = Request.put(url = URL(basePathProjectsIri / encode(projectIri)), body = body)
      val user                       = KnoraSystemInstances.Users.SystemUser

      for {
        response     <- applyRoutes(request).provide(ProjectsServiceMock.empty)
        bodyAsString <- response.body.asString
      } yield assertTrue(response.status == Status.BadRequest) &&
        assertTrue(bodyAsString == """{"error":"dsp.errors.BadRequestException: Project IRI is invalid."}""")
    }
  )

  val getAllDataSpec = suite("get all data")(
    test("successfully get all data") {
      val identifier = ProjectIdentifierADM.IriIdentifier
        .fromString("http://rdfh.ch/projects/0001")
        .getOrElse(throw BadRequestException("Invalid project IRI"))
      val iri      = identifier.asIriIdentifierOption.getOrElse(throw BadRequestException("Invalid project IRI"))
      val user     = KnoraSystemInstances.Users.SystemUser
      val request  = Request.get(url = URL(basePathProjectsIri / encode(iri) / "AllData"))
      val path     = file.Paths.get("getAllDataFile.trig")
      val testFile = file.Files.createFile(path)

      val mockService: ULayer[ProjectsService] = ProjectsServiceMock
        .GetAllProjectData(
          assertion = Assertion.equalTo(identifier, user),
          result = Expectation.value[ProjectDataGetResponseADM](ProjectDataGetResponseADM(testFile))
        )
        .toLayer
      for {
        response <- applyRoutes(request).provide(mockService)
        body     <- response.body.asString
      } yield assertTrue(true)
    },
    test("return a BadRequest Exception if project IRI is invalid") {
      val iri     = "http://rdfh.ch/project/0001"
      val user    = KnoraSystemInstances.Users.SystemUser
      val request = Request.get(url = URL(basePathProjectsIri / encode(iri) / "AllData"))
      val path    = file.Paths.get("...")

      for {
        response     <- applyRoutes(request).provide(ProjectsServiceMock.empty)
        bodyAsString <- response.body.asString
      } yield assertTrue(response.status == Status.BadRequest) &&
        assertTrue(bodyAsString == """{"error":"dsp.errors.BadRequestException: Project IRI is invalid."}""")
    }
  )
}
