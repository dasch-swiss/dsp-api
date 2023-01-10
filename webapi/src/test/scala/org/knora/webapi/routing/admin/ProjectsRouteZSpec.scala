package org.knora.webapi.routing.admin

import zhttp.http._
import zio._
import zio.test.ZIOSpecDefault
import zio.test._

import java.net.URLEncoder

import org.knora.webapi.config.AppConfig
import org.knora.webapi.http.middleware.AuthenticationMiddleware
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.responders.admin.ProjectsServiceMock

object ProjectRouteZSpec extends ZIOSpecDefault {

  // private val systemUnderTest: URIO[ProjectsRouteZ, HttpApp[Any, Nothing]] = ZIO.service[ProjectsRouteZ].map(_.route)

  private val projectsService = ProjectsServiceMock()

  private val projectsRoutes: UIO[Http[Any, Nothing, Request, Response]] =
    (for {
      appConfig  <- ZIO.service[AppConfig]
      middleware <- ZIO.service[AuthenticationMiddleware]
      route       = ProjectsRouteZ(appConfig, projectsService, middleware)
    } yield route.route).provide(
      AppConfig.test,
      AuthenticationMiddleware.layer,
      AuthenticatorService.mock(KnoraSystemInstances.Users.AnonymousUser)
    )

  /**
   * Paths
   */
  private val basePathProjects: Path          = !! / "admin" / "projects"
  private val basePathProjectsIri: Path       = !! / "admin" / "projects" / "iri"
  private val basePathProjectsShortname: Path = !! / "admin" / "projects" / "shortname"
  private val basePathProjectsShortcode: Path = !! / "admin" / "projects" / "shortcode"

  /**
   * Test data and values used by multiple tests
   */
  // private val validProject: ProjectADM = ProjectADM(
  //   id = "id",
  //   shortname = "shortname",
  //   shortcode = "AB12",
  //   longname = None,
  //   description = List(StringLiteralV2("description")),
  //   keywords = List.empty,
  //   logo = None,
  //   ontologies = List.empty,
  //   status = false,
  //   selfjoin = false
  // )

  // private val validProject2: ProjectADM = ProjectADM(
  //   id = "id2",
  //   shortname = "shortname2",
  //   shortcode = "AB13",
  //   longname = None,
  //   description = List(StringLiteralV2("description")),
  //   keywords = List.empty,
  //   logo = None,
  //   ontologies = List.empty,
  //   status = false,
  //   selfjoin = false
  // )

  // private val expectedResponseNotFoundException: NotFoundException = NotFoundException("xxxx")

  /**
   * Common layers and expectations for ActorToZioBridge mock
   */
  // private val commonLayers: URLayer[ActorToZioBridge, ProjectsRouteZ] =
  //   ZLayer.makeSome[ActorToZioBridge, ProjectsRouteZ](
  //     AppConfig.test,
  //     ProjectsRouteZ.layer,
  //     RestProjectsService.layer,
  //     authenticationMiddlewareLayer
  //   )

  def spec = suite("ProjectsRouteZ")(getProjectsSpec, getProjectByIdentifierSpec, createProjectSpec)

  /**
   * tests for GET /admin/projects
   */
  val getProjectsSpec =
    test("get all projects") {
      val url              = URL.empty.setPath(basePathProjects)
      val request: Request = Request(url = url)
      val expectation      = projectsService.getProjectsResponseAsString

      for {
        routes         <- projectsRoutes
        response       <- routes.apply(request)
        responseString <- response.body.asString
      } yield assertTrue(responseString == expectation)
    }
  // private val expectedProjectsGetRequestADMSuccess: ProjectsGetRequestADM = ProjectsGetRequestADM()
  // private val expectedProjectsGetRequestADMFailure: ProjectsGetRequestADM = ProjectsGetRequestADM()

  // private val expectedResponseMultipleProjectsSuccess: ProjectsGetResponseADM =
  //   ProjectsGetResponseADM(Seq(validProject, validProject2))

  // private val expectMessageToProjectResponderMultipleProjects: ULayer[ActorToZioBridge] =
  //   ActorToZioBridgeMock.AskAppActor
  //     .of[ProjectsGetResponseADM]
  //     .apply(
  //       Assertion.equalTo(expectedProjectsGetRequestADMSuccess),
  //       Expectation.value(expectedResponseMultipleProjectsSuccess)
  //     )
  //     .toLayer

  // private val expectMessageToProjectResponderMultipleProjectsFailure: ULayer[ActorToZioBridge] =
  //   ActorToZioBridgeMock.AskAppActor
  //     .of[NotFoundException]
  //     .apply(
  //       Assertion.equalTo(expectedProjectsGetRequestADMFailure),
  //       Expectation.failure(expectedResponseNotFoundException)
  //     )
  //     .toLayer

  // private val getAllProjectsSpec: Spec[Any, Serializable] =
  //   suite("get all projects")(
  //     test("given projects exist, return all projects") {
  //       val urlGetAllProjects = URL.empty.setPath(basePathProjects)
  //       for {
  //         route  <- systemUnderTest
  //         actual <- route.apply(Request(url = urlGetAllProjects)).flatMap(_.body.asString)
  //       } yield assertTrue(actual == expectedResponseMultipleProjectsSuccess.toJsValue.toString())
  //     }.provide(commonLayers, expectMessageToProjectResponderMultipleProjects),
  //     test("given no projects exist, respond with not found") {
  //       val urlGetAllProjects = URL.empty.setPath(basePathProjects)
  //       for {
  //         route  <- systemUnderTest
  //         actual <- route.apply(Request(url = urlGetAllProjects)).map(_.status)
  //       } yield assertTrue(actual == Status.NotFound)
  //     }.provide(commonLayers, expectMessageToProjectResponderMultipleProjectsFailure)
  //   )

  /**
   * common test data for GET /admin/projects/[iri | shortname | shortcode]
   */
  // private val expectNoInteractionWithProjectsResponderADM              = ActorToZioBridgeMock.empty
  // private val expectedResponseOneProjectSuccess: ProjectGetResponseADM = ProjectGetResponseADM(validProject)

  /**
   * tests for GET /admin/projects/iri
   */
  val getProjectByIriSpec =
    test("get a project by IRI") {
      val projectIri: ProjectIdentifierADM.IriIdentifier =
        ProjectIdentifierADM.IriIdentifier
          .fromString("http://rdfh.ch/projects/0001")
          .getOrElse(throw new IllegalArgumentException())
      val validIriUrlEncoded: String = URLEncoder.encode(projectIri.value.value, "utf-8")
      val url                        = URL.empty.setPath(basePathProjectsIri / validIriUrlEncoded)
      val request: Request           = Request(url = url)
      val expectation                = projectsService.getSingleProjectResponseAsString

      for {
        routes         <- projectsRoutes
        response       <- routes.apply(request)
        responseString <- response.body.asString
      } yield assertTrue(responseString == expectation)
    }

  // private val projectIri: ProjectIdentifierADM.IriIdentifier =
  //   ProjectIdentifierADM.IriIdentifier
  //     .fromString("http://rdfh.ch/projects/0001")
  //     .getOrElse(throw new IllegalArgumentException())

  // private val inexistentProjectIri: ProjectIdentifierADM.IriIdentifier =
  //   ProjectIdentifierADM.IriIdentifier
  //     .fromString("http://rdfh.ch/projects/0002")
  //     .getOrElse(throw new IllegalArgumentException())

  // private val validIriUrlEncoded: String = URLEncoder.encode(projectIri.value.value, "utf-8")

  // private val inexistentIriUrlEncoded: String = URLEncoder.encode(inexistentProjectIri.value.value, "utf-8")

  // private val expectedProjectGetRequestADMSuccessIri: ProjectGetRequestADM = ProjectGetRequestADM(projectIri)

  // private val expectedProjectGetRequestADMFailureIri: ProjectGetRequestADM = ProjectGetRequestADM(inexistentProjectIri)

  // private val expectMessageToProjectResponderIri: ULayer[ActorToZioBridge] =
  //   ActorToZioBridgeMock.AskAppActor
  //     .of[ProjectGetResponseADM]
  //     .apply(
  //       Assertion.equalTo(expectedProjectGetRequestADMSuccessIri),
  //       Expectation.value(expectedResponseOneProjectSuccess)
  //     )
  //     .toLayer

  // private val expectMessageToProjectResponderFailure: ULayer[ActorToZioBridge] =
  //   ActorToZioBridgeMock.AskAppActor
  //     .of[NotFoundException]
  //     .apply(
  //       Assertion.equalTo(expectedProjectGetRequestADMFailureIri),
  //       Expectation.failure(expectedResponseNotFoundException)
  //     )
  //     .toLayer

  // private val getProjectByIriSpec: Spec[Any, Serializable] =
  //   suite("get project by IRI")(
  //     test("given valid project iri, should respond with success") {
  //       val urlWithValidIri = URL.empty.setPath(basePathProjectsIri / validIriUrlEncoded)
  //       for {
  //         route  <- systemUnderTest
  //         actual <- route.apply(Request(url = urlWithValidIri)).flatMap(_.body.asString)
  //       } yield assertTrue(actual == expectedResponseOneProjectSuccess.toJsValue.toString())
  //     }.provide(commonLayers, expectMessageToProjectResponderIri),
  //     test("given valid project iri that doesn't exist, should respond with not found") {
  //       val urlWithInexistentIri = URL.empty.setPath(basePathProjectsIri / inexistentIriUrlEncoded)
  //       for {
  //         route  <- systemUnderTest
  //         actual <- route.apply(Request(url = urlWithInexistentIri)).map(_.status)
  //       } yield assertTrue(actual == Status.NotFound)
  //     }.provide(commonLayers, expectMessageToProjectResponderFailure),
  //     test("given invalid project iri, should respond with bad request") {
  //       val urlWithInvalidIri = URL.empty.setPath(basePathProjectsIri / "invalid")
  //       for {
  //         route  <- systemUnderTest
  //         actual <- route.apply(Request(url = urlWithInvalidIri)).map(_.status)
  //       } yield assertTrue(actual == Status.BadRequest)
  //     }.provide(commonLayers, expectNoInteractionWithProjectsResponderADM)
  //   )

  /**
   * tests for GET /admin/projects/shortname
   */
  val getProjectByShortnameSpec =
    test("get a project by shortname") {
      val validShortname   = "shortname"
      val url              = URL.empty.setPath(basePathProjectsShortname / validShortname)
      val request: Request = Request(url = url)
      val expectation      = projectsService.getSingleProjectResponseAsString

      for {
        routes         <- projectsRoutes
        response       <- routes.apply(request)
        responseString <- response.body.asString
      } yield assertTrue(responseString == expectation)
    }

  // private val projectShortname: ProjectIdentifierADM.ShortnameIdentifier =
  //   ProjectIdentifierADM.ShortnameIdentifier
  //     .fromString("shortname")
  //     .getOrElse(throw new IllegalArgumentException())

  // private val expectedProjectGetRequestADMSuccessShortname: ProjectGetRequestADM =
  //   ProjectGetRequestADM(projectShortname)

  // private val expectMessageToProjectResponderShortname: ULayer[ActorToZioBridge] =
  //   ActorToZioBridgeMock.AskAppActor
  //     .of[ProjectGetResponseADM]
  //     .apply(
  //       Assertion.equalTo(expectedProjectGetRequestADMSuccessShortname),
  //       Expectation.value(expectedResponseOneProjectSuccess)
  //     )
  //     .toLayer

  // private val getProjectByShortnameSpec: Spec[Any, Serializable] =
  //   suite("get project by shortname")(
  //     test("given valid project shortname, should respond with success") {
  //       val urlWithValidShortname = URL.empty.setPath(basePathProjectsShortname / validShortname)
  //       for {
  //         route  <- systemUnderTest
  //         actual <- route.apply(Request(url = urlWithValidShortname)).flatMap(_.body.asString)
  //       } yield assertTrue(actual == expectedResponseOneProjectSuccess.toJsValue.toString())
  //     }.provide(commonLayers, expectMessageToProjectResponderShortname),
  //     test("given invalid project shortname, should respond with bad request") {
  //       val urlWithInvalidShortname = URL.empty.setPath(basePathProjectsShortname / "123")
  //       for {
  //         route  <- systemUnderTest
  //         actual <- route.apply(Request(url = urlWithInvalidShortname)).map(_.status)
  //       } yield assertTrue(actual == Status.BadRequest)
  //     }.provide(commonLayers, expectNoInteractionWithProjectsResponderADM)
  //   )

  /**
   * tests for GET /admin/projects/shortcode
   */
  val getProjectByShortcodeSpec =
    test("get a project by shortcode") {
      val validShortcode   = "AB12"
      val url              = URL.empty.setPath(basePathProjectsShortcode / validShortcode)
      val request: Request = Request(url = url)
      val expectation      = projectsService.getSingleProjectResponseAsString

      for {
        routes         <- projectsRoutes
        response       <- routes.apply(request)
        responseString <- response.body.asString
      } yield assertTrue(responseString == expectation)
    }

  // private val projectShortcode: ProjectIdentifierADM.ShortcodeIdentifier =
  //   ProjectIdentifierADM.ShortcodeIdentifier
  //     .fromString("AB12")
  //     .getOrElse(throw new IllegalArgumentException())

  // private val expectedProjectGetRequestADMSuccessShortcode: ProjectGetRequestADM =
  //   ProjectGetRequestADM(projectShortcode)

  // private val expectMessageToProjectResponderShortcode: ULayer[ActorToZioBridge] =
  //   ActorToZioBridgeMock.AskAppActor
  //     .of[ProjectGetResponseADM]
  //     .apply(
  //       Assertion.equalTo(expectedProjectGetRequestADMSuccessShortcode),
  //       Expectation.value(expectedResponseOneProjectSuccess)
  //     )
  //     .toLayer

  // private val getProjectByShortcodeSpec: Spec[Any, Serializable] =
  //   suite("get project by shortcode")(
  //     test("given valid project shortcode, should respond with success") {
  //       val urlWithValidShortcode = URL.empty.setPath(basePathProjectsShortcode / validShortcode)
  //       for {
  //         route  <- systemUnderTest
  //         actual <- route.apply(Request(url = urlWithValidShortcode)).flatMap(_.body.asString)
  //       } yield assertTrue(actual == expectedResponseOneProjectSuccess.toJsValue.toString())
  //     }.provide(commonLayers, expectMessageToProjectResponderShortcode),
  //     test("given invalid project shortcode, should respond with bad request") {
  //       val urlWithInvalidShortcode = URL.empty.setPath(basePathProjectsShortcode / "invalid")
  //       for {
  //         route  <- systemUnderTest
  //         actual <- route.apply(Request(url = urlWithInvalidShortcode)).map(_.status)
  //       } yield assertTrue(actual == Status.BadRequest)
  //     }.provide(commonLayers, expectNoInteractionWithProjectsResponderADM)
  //   )

  private val getProjectByIdentifierSpec =
    suite("get project by identifier")(
      getProjectByIriSpec,
      getProjectByShortcodeSpec,
      getProjectByShortnameSpec
    )

  /**
   * tests for POST /admin/projects
   */
  val createProjectSpec =
    test("create a project") {
      val url = URL.empty.setPath(basePathProjects)
      val projectCreatePayload =
        """|{
           |  "shortname": "newproject",
           |  "shortcode": "3333",
           |  "longname": "project longname",
           |  "description": [{"value": "project description", "language": "en"}],
           |  "keywords": ["test project"],
           |  "logo": "/fu/bar/baz.jpg",
           |  "status": true,
           |  "selfjoin": false
           |}
           |""".stripMargin
      val body             = Body.fromString(projectCreatePayload)
      val request: Request = Request(url = url, method = Method.POST, body = body)
      val expectation      = projectsService.createProjectResponseAsString

      for {
        routes         <- projectsRoutes
        response       <- routes.apply(request)
        responseString <- response.body.asString
      } yield assertTrue(responseString == expectation)
    }

}
