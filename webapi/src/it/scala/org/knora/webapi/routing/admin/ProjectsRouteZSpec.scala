package org.knora.webapi.routing.admin

import zhttp.http._
import zio.test.ZIOSpecDefault
import zio.test._
import zio._
import zio.mock.Expectation
import java.net.URLEncoder

import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.responders.admin.ProjectsService
import org.knora.webapi.responders.ActorToZioBridge
import org.knora.webapi.responders.ActorToZioBridgeMock

object ProjectsRouteZSpec extends ZIOSpecDefault {

  private val systemUnderTest = ZIO.service[ProjectsRouteZ].map(_.route)

  // Test data
  private val projectIri =
    ProjectIdentifierADM.IriIdentifier
      .fromString("http://rdfh.ch/projects/0001")
      .getOrElse(throw new IllegalArgumentException())

  private val basePath                                     = !! / "admin" / "projects" / "iri"
  private val validIri                                     = URLEncoder.encode(projectIri.value.value, "utf-8")
  private val expectedRequestSuccess: ProjectGetRequestADM = ProjectGetRequestADM(projectIri)
  private val expectedResponseSuccess: ProjectGetResponseADM = ProjectGetResponseADM(
    ProjectADM(
      "id",
      "shortname",
      "shortcode",
      None,
      List(StringLiteralV2("description")),
      List.empty,
      None,
      List.empty,
      status = false,
      selfjoin = false
    )
  )

  // Expectations and layers for ActorToZioBridge mock
  private val commonLayers =
    ZLayer.makeSome[ActorToZioBridge, ProjectsRouteZ](AppConfig.test, ProjectsRouteZ.layer, ProjectsService.layer)
  private val expectMessageToProjectResponderADM =
    ActorToZioBridgeMock.AskAppActor
      .of[ProjectGetResponseADM]
      .apply(Assertion.equalTo(expectedRequestSuccess), Expectation.value(expectedResponseSuccess))
      .toLayer
  private val expectNoInteractionWithProjectsResponderADM = ActorToZioBridgeMock.empty

  val spec =
    suite("ProjectsRouteZSpec")(
      test("given valid project iri should respond with success") {
        val urlWithValidIri = URL.empty.setPath(basePath / validIri)
        for {
          route  <- systemUnderTest
          actual <- route.apply(Request(url = urlWithValidIri)).flatMap(_.body.asString)
        } yield assertTrue(actual == expectedResponseSuccess.toJsValue.toString())
      }.provide(commonLayers, expectMessageToProjectResponderADM),
      test("given invalid project iri but no authentication should respond with bad request") {
        val urlWithInvalidIri = URL.empty.setPath(basePath / "invalid")
        for {
          route  <- systemUnderTest
          actual <- route.apply(Request(url = urlWithInvalidIri)).map(_.status)
        } yield assertTrue(actual == Status.BadRequest)
      }.provide(commonLayers, expectNoInteractionWithProjectsResponderADM)
    )
}
