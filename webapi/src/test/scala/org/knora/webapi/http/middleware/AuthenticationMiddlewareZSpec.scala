package org.knora.webapi.http.middleware

import zhttp.http.Http
import zhttp.http.Request
import zhttp.http.Response
import zio._
import zio.test._

import org.knora.webapi.messages.admin.responder.groupsmessages.GroupADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.routing.admin.AuthenticatorService

object AuthenticationMiddlewareZSpec extends ZIOSpecDefault {
  private val passUserThroughApp: Http[Any, Nothing, (Request, UserADM), Response] =
    Http.collect[(Request, UserADM)] { case (_, user) => Response.text(user.id) }

  private val anonymousUser = KnoraSystemInstances.Users.AnonymousUser
  private val someUser = UserADM(
    id = "http://rdfh.ch/users/someuser",
    username = "someuser",
    email = "some.user@example.com",
    password = Some("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
    token = None,
    givenName = "Some",
    familyName = "User",
    status = true,
    lang = "en",
    groups = Seq.empty[GroupADM],
    projects = Seq.empty[ProjectADM],
    sessionId = None,
    permissions = PermissionsDataADM()
  )

  private val authServiceFailing = new AuthenticatorService {
    override def getUser(request: Request): Task[UserADM] = ZIO.fail(new RuntimeException(""))
  }
  private val authServiceAnonymous = new AuthenticatorService {
    override def getUser(request: Request): Task[UserADM] = ZIO.succeed(anonymousUser)
  }
  private val authServiceSome = new AuthenticatorService {
    override def getUser(request: Request): Task[UserADM] = ZIO.succeed(someUser)
  }

  val authenticationSuccessSuite = suite("when authentication succeeds")(
    test("should return anonymous user if no authentication was provided") {
      val middleware = AuthenticationMiddleware(authServiceAnonymous).authenticationMiddleware
      val app        = passUserThroughApp @@ middleware
      for {
        response <- app.apply(Request())
        resId    <- response.body.asString
      } yield assertTrue(resId == anonymousUser.id)
    },
    test("should return the requesting user if valid authentication was provided") {
      val middleware = AuthenticationMiddleware(authServiceSome).authenticationMiddleware
      val app        = passUserThroughApp @@ middleware
      for {
        response <- app.apply(Request())
        resId    <- response.body.asString
      } yield assertTrue(resId == someUser.id)
    }
  )

  val authenticationFailureSuite = suite("when authentication fails")(
    test("should still return anonymous user") {
      val middleware = AuthenticationMiddleware(authServiceFailing).authenticationMiddleware
      val app        = passUserThroughApp @@ middleware
      for {
        response <- app.apply(Request())
        resId    <- response.body.asString
      } yield assertTrue(resId == anonymousUser.id)
    }
  )

  override def spec = suite("AuthenticationMiddleware")(
    authenticationSuccessSuite,
    authenticationFailureSuite
  )

}
