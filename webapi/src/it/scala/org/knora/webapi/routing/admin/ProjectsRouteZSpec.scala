package org.knora.webapi.routing.admin

import dsp.errors.AuthenticationException
import org.apache.commons.codec.binary.Base32
import zhttp.http.Method.GET
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
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.responders.admin.ProjectsService
import org.knora.webapi.responders.ActorToZioBridge
import org.knora.webapi.responders.ActorToZioBridgeMock

object ProjectsRouteZSpec extends ZIOSpecDefault {

  private val projectIri =
    ProjectIdentifierADM.IriIdentifier
      .fromString("http://rdfh.ch/projects/0001")
      .getOrElse(throw new IllegalArgumentException())

  private val user: UserADM                         = KnoraSystemInstances.Users.SystemUser
  private val expectedRequest: ProjectGetRequestADM = ProjectGetRequestADM(projectIri, user)
  private val expectedResponse: ProjectGetResponseADM = ProjectGetResponseADM(
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

  def urlEncode(str: String): String = URLEncoder.encode(str, "utf-8")

  private val expectMessageToProjectResponderADM: ULayer[ActorToZioBridge] = ActorToZioBridgeMock.AskAppActor
    .of[ProjectGetResponseADM]
    .apply(Assertion.equalTo(expectedRequest), Expectation.value(expectedResponse))
    .toLayer

  private val noMessageExpectedToProjectsResponderADM: ULayer[ActorToZioBridge] = ActorToZioBridgeMock.AskAppActor
    .of[ProjectGetResponseADM]
    .apply(Assertion.nothing, Expectation.never)
    .toLayer

  val commonLayers: ZLayer[ActorToZioBridge, Nothing, ProjectsRouteZ] =
    ZLayer.makeSome[ActorToZioBridge, ProjectsRouteZ](
      AppConfig.test,
      ProjectsRouteZ.layer,
      AuthenticatorService.stub,
      ProjectsService.layer
    )

  val spec = {
    val validIri        = urlEncode(projectIri.value.value)
    val urlWithValidIri = URL.empty.setPath(!! / "admin" / "projects" / "iri" / validIri)
    val headers         = Headers("Authorization", "Bearer stubToken")
    suite("ProjectsRouteZSpec")(
      test("given valid project iri and authentication should respond with sucess") {
        val request = Request(method = GET, url = urlWithValidIri, headers = headers)
        for {
          route  <- ZIO.service[ProjectsRouteZ].map(_.route)
          actual <- route.apply(request).flatMap(_.body.asString)
        } yield assertTrue(actual == expectedResponse.toJsValue.toString())
      }.provide(commonLayers, expectMessageToProjectResponderADM),
      test("given valid project iri but no authentication should respond with unauthorized") {
        val request = Request(method = GET, url = urlWithValidIri, headers = Headers.empty)
        for {
          route  <- ZIO.service[ProjectsRouteZ].map(_.route)
          actual <- route.apply(request).map(_.status)
        } yield assertTrue(actual == Status.Unauthorized)
      }.provide(commonLayers, noMessageExpectedToProjectsResponderADM)
    )
  }

  final case class AuthenticatorServiceStub(appConfig: AppConfig) extends AuthenticatorService {

    private val authCookieName: String = {
      val base32 = new Base32('9'.toByte)
      "KnoraAuthentication" + base32.encodeAsString(appConfig.knoraApi.externalKnoraApiHostPort.getBytes())
    }
    override def getUser(request: Request): Task[UserADM] = {
      val bearerToken = request.bearerToken
      val authCookie  = request.cookieValue(authCookieName)
      val basicAuth   = request.basicAuthorizationCredentials
      if (bearerToken.isEmpty && authCookie.isEmpty && basicAuth.isEmpty) {
        ZIO.fail(new AuthenticationException("unauthorized"))
      } else {
        ZIO.succeed(user)
      }
    }
  }
  object AuthenticatorService {
    val stub = ZLayer.fromFunction(AuthenticatorServiceStub.apply _)
  }
}
