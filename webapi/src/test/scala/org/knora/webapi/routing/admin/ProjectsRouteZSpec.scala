package org.knora.webapi.routing.admin

import zhttp.http._
import zio._
import zio.mock.Expectation
import zio.test.ZIOSpecDefault
import zio.test._

import java.net.URLEncoder

import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.responders.ActorToZioBridge
import org.knora.webapi.responders.ActorToZioBridgeMock
import org.knora.webapi.responders.admin.RestProjectsService

object ProjectRouteZSpec extends ZIOSpecDefault {

  private val systemUnderTest: URIO[ProjectsRouteZ, HttpApp[Any, Nothing]] = ZIO.service[ProjectsRouteZ].map(_.route)

  // Test data
  private val projectIri: ProjectIdentifierADM.IriIdentifier =
    ProjectIdentifierADM.IriIdentifier
      .fromString("http://rdfh.ch/projects/0001")
      .getOrElse(throw new IllegalArgumentException())

  private val projectShortname: ProjectIdentifierADM.ShortnameIdentifier =
    ProjectIdentifierADM.ShortnameIdentifier
      .fromString("shortname")
      .getOrElse(throw new IllegalArgumentException())

  private val projectShortcode: ProjectIdentifierADM.ShortcodeIdentifier =
    ProjectIdentifierADM.ShortcodeIdentifier
      .fromString("AB12")
      .getOrElse(throw new IllegalArgumentException())

  private val basePathIri: Path                                     = !! / "admin" / "projects" / "iri"
  private val basePathShortname: Path                               = !! / "admin" / "projects" / "shortname"
  private val basePathShortcode: Path                               = !! / "admin" / "projects" / "shortcode"
  private val validIriUrlEncoded: String                            = URLEncoder.encode(projectIri.value.value, "utf-8")
  private val validShortname: String                                = "shortname"
  private val validShortcode: String                                = "AB12"
  private val expectedRequestSuccessIri: ProjectGetRequestADM       = ProjectGetRequestADM(projectIri)
  private val expectedRequestSuccessShortname: ProjectGetRequestADM = ProjectGetRequestADM(projectShortname)
  private val expectedRequestSuccessShortcode: ProjectGetRequestADM = ProjectGetRequestADM(projectShortcode)
  private val expectedResponseSuccess: ProjectGetResponseADM =
    ProjectGetResponseADM(
      ProjectADM(
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
    )

  // Expectations and layers for ActorToZioBridge mock
  private val commonLayers: URLayer[ActorToZioBridge, ProjectsRouteZ] =
    ZLayer.makeSome[ActorToZioBridge, ProjectsRouteZ](AppConfig.test, ProjectsRouteZ.layer, RestProjectsService.layer)

  private val expectMessageToProjectResponderIri: ULayer[ActorToZioBridge] =
    ActorToZioBridgeMock.AskAppActor
      .of[ProjectGetResponseADM]
      .apply(Assertion.equalTo(expectedRequestSuccessIri), Expectation.value(expectedResponseSuccess))
      .toLayer

  private val expectMessageToProjectResponderShortname: ULayer[ActorToZioBridge] =
    ActorToZioBridgeMock.AskAppActor
      .of[ProjectGetResponseADM]
      .apply(Assertion.equalTo(expectedRequestSuccessShortname), Expectation.value(expectedResponseSuccess))
      .toLayer

  private val expectMessageToProjectResponderShortcode: ULayer[ActorToZioBridge] =
    ActorToZioBridgeMock.AskAppActor
      .of[ProjectGetResponseADM]
      .apply(Assertion.equalTo(expectedRequestSuccessShortcode), Expectation.value(expectedResponseSuccess))
      .toLayer

  private val expectNoInteractionWithProjectsResponderADM = ActorToZioBridgeMock.empty

  val spec: Spec[Any, Serializable] =
    suite("ProjectsRouteZSpec")(
      test("given valid project iri should respond with success") {
        val urlWithValidIri = URL.empty.setPath(basePathIri / validIriUrlEncoded)
        for {
          route  <- systemUnderTest
          actual <- route.apply(Request(url = urlWithValidIri)).flatMap(_.body.asString)
        } yield assertTrue(actual == expectedResponseSuccess.toJsValue.toString())
      }.provide(commonLayers, expectMessageToProjectResponderIri),
      test("given valid project shortname should respond with success") {
        val urlWithValidShortname = URL.empty.setPath(basePathShortname / validShortname)
        for {
          route  <- systemUnderTest
          actual <- route.apply(Request(url = urlWithValidShortname)).flatMap(_.body.asString)
        } yield assertTrue(actual == expectedResponseSuccess.toJsValue.toString())
      }.provide(commonLayers, expectMessageToProjectResponderShortname),
      test("given valid project shortcode should respond with success") {
        val urlWithValidShortcode = URL.empty.setPath(basePathShortcode / validShortcode)
        for {
          route  <- systemUnderTest
          actual <- route.apply(Request(url = urlWithValidShortcode)).flatMap(_.body.asString)
        } yield assertTrue(actual == expectedResponseSuccess.toJsValue.toString())
      }.provide(commonLayers, expectMessageToProjectResponderShortcode),
      test("given invalid project iri should respond with bad request") {
        val urlWithInvalidIri = URL.empty.setPath(basePathIri / "invalid")
        for {
          route  <- systemUnderTest
          actual <- route.apply(Request(url = urlWithInvalidIri)).map(_.status)
        } yield assertTrue(actual == Status.BadRequest)
      }.provide(commonLayers, expectNoInteractionWithProjectsResponderADM),
      test("given invalid project shortname should respond with bad request") {
        val urlWithInvalidShortname = URL.empty.setPath(basePathShortname / "123")
        for {
          route  <- systemUnderTest
          actual <- route.apply(Request(url = urlWithInvalidShortname)).map(_.status)
        } yield assertTrue(actual == Status.BadRequest)
      }.provide(commonLayers, expectNoInteractionWithProjectsResponderADM),
      test("given invalid project shortcode should respond with bad request") {
        val urlWithInvalidShortcode = URL.empty.setPath(basePathShortcode / "invalid")
        for {
          route  <- systemUnderTest
          actual <- route.apply(Request(url = urlWithInvalidShortcode)).map(_.status)
        } yield assertTrue(actual == Status.BadRequest)
      }.provide(commonLayers, expectNoInteractionWithProjectsResponderADM)
    )
}
