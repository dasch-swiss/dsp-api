/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import zhttp.http._
import zio._
import zio.mock.Expectation
import zio.prelude.Validation
import zio.test.ZIOSpecDefault
import zio.test._

import java.net.URLEncoder

import dsp.valueobjects.Iri._
import dsp.valueobjects.Project._
import dsp.valueobjects.V2
import org.knora.webapi.IRI
import org.knora.webapi.config.AppConfig
import org.knora.webapi.http.middleware.AuthenticationMiddleware
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectCreatePayloadADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectOperationResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsGetResponseADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
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
  private val validProject: ProjectADM = ProjectADM(
    id = "id",
    shortname = "shortname",
    shortcode = "AB12",
    longname = None,
    description = List(StringLiteralV2("description")),
    keywords = List.empty,
    logo = None,
    ontologies = List.empty,
    status = false,
    selfjoin = false
  )

  private val validProject2: ProjectADM = ProjectADM(
    id = "id2",
    shortname = "shortname2",
    shortcode = "AB13",
    longname = None,
    description = List(StringLiteralV2("description")),
    keywords = List.empty,
    logo = None,
    ontologies = List.empty,
    status = false,
    selfjoin = false
  )

  /**
   * Creates a [[ProjectADM]] with empty content or optionally with a given ID.
   */
  private def getProjectADM(id: String = "") =
    ProjectADM(
      id = id,
      shortname = "",
      shortcode = "",
      longname = None,
      description = Seq(StringLiteralV2("")),
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
    createProjectSpec
  )

  val createProjectSpec = test("create a project") {
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
    val request = Request(url = URL(basePathProjects), method = Method.POST, body = body)
    val user    = KnoraSystemInstances.Users.SystemUser
    val projectCreatePayload: ProjectCreatePayloadADM =
      Validation
        .validateWith(
          ProjectIri.make(None),
          ShortName.make("newproject"),
          ShortCode.make("3333"),
          Name.make(Some("project longname")),
          ProjectDescription.make(Seq(V2.StringLiteralV2("project description", Some("en")))),
          Keywords.make(Seq("test project")),
          Logo.make(None),
          ProjectStatus.make(true),
          ProjectSelfJoin.make(false)
        )(ProjectCreatePayloadADM.apply)
        .getOrElse(throw new Exception("Invalid Payload"))
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
  }

  val getProjectsSpec = test("get all projects") {
    val request        = Request(url = URL(basePathProjects))
    val expectedResult = Expectation.value[ProjectsGetResponseADM](ProjectsGetResponseADM(Seq(getProjectADM())))
    val mockService    = ProjectsServiceMock.GetProjects(expectedResult).toLayer
    for {
      response <- applyRoutes(request).provide(mockService)
      body     <- response.body.asString
    } yield assertTrue(true)
  }

  val getSingleProjectSpec =
    suite("get a single project by identifier")(
      test("get a project by IRI") {
        val iri = "http://rdfh.ch/projects/0001"
        val identifier: ProjectIdentifierADM = ProjectIdentifierADM.IriIdentifier
          .fromString(iri)
          .getOrElse(throw new Exception("Invalid IRI"))
        val request = Request(url = URL(basePathProjectsIri / encode(iri)))
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
      test("get a project by shortcode") {
        val shortcode = "0001"
        val identifier: ProjectIdentifierADM = ProjectIdentifierADM.ShortcodeIdentifier
          .fromString(shortcode)
          .getOrElse(throw new Exception("Invalid Shortcode"))
        val request = Request(url = URL(basePathProjectsShortcode / shortcode))
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
      test("get a project by shortname") {
        val shortname = "someProject"
        val identifier: ProjectIdentifierADM = ProjectIdentifierADM.ShortnameIdentifier
          .fromString(shortname)
          .getOrElse(throw new Exception("Invalid Shortname"))
        val request = Request(url = URL(basePathProjectsShortname / shortname))
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
      }
    )

  val deleteProjectSpec =
    test("delete a project by IRI") {
      val iri: IRI       = "http://rdfh.ch/projects/0001"
      val request        = Request(url = URL(basePathProjectsIri / encode(iri)), method = Method.DELETE)
      val user           = KnoraSystemInstances.Users.SystemUser
      val expectedResult = Expectation.value[ProjectOperationResponseADM](ProjectOperationResponseADM(getProjectADM()))
      val mockService: ULayer[ProjectsService] = ProjectsServiceMock
        .DeleteProject(
          assertion = Assertion.equalTo(iri, user),
          result = expectedResult
        )
        .toLayer
      for {
        _ <- applyRoutes(request).provide(mockService)
      } yield assertTrue(true)
    }

}
