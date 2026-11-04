/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import org.junit.runner.RunWith
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir.*
import zio.*
import zio.http.Header
import zio.http.Request
import zio.http.Status
import zio.http.URL
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.util.KnoraSystemInstances.Users.AnonymousUser
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo.builtIn.SystemProject
import org.knora.webapi.slice.api.admin.service.SparqlPassthroughTestEnv
import org.knora.webapi.slice.security.AuthenticatorError

@RunWith(classOf[DspZTestJUnitRunner])
class BaseEndpointsSpec extends ZIOSpecDefault {

  private val activeUser =
    User("http://rdfh.ch/users/normal", "normal", "normal@example.com", "Given", "Family", status = true, "en")

  private val systemAdmin = activeUser.copy(
    id = "http://rdfh.ch/users/admin",
    username = "admin",
    permissions = PermissionsDataADM(Map(SystemProject.id.value -> List(KnoraGroupRepo.builtIn.SystemAdmin.id.value))),
  )

  private val inactiveSystemAdmin = systemAdmin.copy(status = false)

  // The real authenticator refuses inactive and built-in users; this one hands them out, so the factories' own
  // checks are what is tested.
  private val usersByToken =
    Map("normal" -> activeUser, "admin" -> systemAdmin, "inactive" -> inactiveSystemAdmin, "anonymous" -> AnonymousUser)

  private val base = BaseEndpoints(
    SparqlPassthroughTestEnv.authenticatorAnswering(token =>
      ZIO.fromOption(usersByToken.get(token)).orElseFail(AuthenticatorError.BadCredentials),
    ),
    // The checks these factories use read only the user, never a service.
    new AuthorizationRestService(null, null),
  )

  private def policyOf[S, P](
    factory: RequiresMethod[ZPartialServerEndpoint[Any, S, P, Unit, Throwable, Unit, Any]],
  ): Option[Policy] =
    factory.get.in("probe").serverLogic[Any](_ => _ => ZIO.unit).endpoint.attribute(Policy.key)

  /** The status, and the principal the server logic received, or `None` if it never ran. */
  private def call[S, P](
    factory: RequiresMethod[ZPartialServerEndpoint[Any, S, P, Unit, Throwable, Unit, Any]],
    token: Option[String],
  ): Task[(Status, Option[P])] =
    for {
      reached <- Ref.make(Option.empty[P])
      app      = ZioHttpInterpreter().toHttp(
              List(factory.get.in("probe").serverLogic[Any](principal => _ => reached.set(Some(principal)))),
            )
      request   = Request.get(URL.decode("/probe").toOption.get)
      response <-
        ZIO.scoped(
          app.runZIO(token.fold(request)(t => request.addHeader(Header.Custom("Authorization", s"Bearer $t")))),
        )
      principal <- reached.get
    } yield (response.status, principal)

  private def refused[S, P](
    factory: RequiresMethod[ZPartialServerEndpoint[Any, S, P, Unit, Throwable, Unit, Any]],
    token: Option[String],
    status: Status,
  ) =
    call(factory, token).map((actual, principal) => assertTrue(actual == status, principal.isEmpty))

  val spec: Spec[Any, Any] = suite("BaseEndpoints policy factories")(
    suite("set their policy on the completed server endpoint")(
      test("Public") {
        val policy = base
          .publicPolicyEndpoint("reason")
          .get
          .in("probe")
          .zServerLogic[Any](_ => ZIO.unit)
          .endpoint
          .attribute(Policy.key)
        assertTrue(policy.contains(Policy.Public("reason")))
      },
      test("Authenticated") {
        val policy = policyOf(base.authenticatedPolicyEndpoint("reason"))
        assertTrue(policy.contains(Policy.Authenticated("reason")))
      },
      test("SystemAdmin") {
        val policy = policyOf(base.systemAdminPolicyEndpoint)
        assertTrue(policy.contains(Policy.SystemAdmin))
      },
      test("ProjectAdmin") {
        val policy = policyOf(base.projectAdminPolicyEndpoint("path projectIri"))
        assertTrue(policy.contains(Policy.ProjectAdmin("path projectIri")))
      },
      test("ObjectAccess") {
        val policy = policyOf(base.objectAccessPolicyEndpoint("the responder"))
        assertTrue(policy.contains(Policy.ObjectAccess("the responder", admitsAnonymous = true)))
      },
      test("ObjectAccess for a write") {
        val policy = policyOf(base.authenticatedObjectAccessPolicyEndpoint("the responder"))
        assertTrue(policy.contains(Policy.ObjectAccess("the responder", admitsAnonymous = false)))
      },
    ),
    suite("SystemAdmin refuses in the security logic")(
      test("a caller without credentials with 401") {
        refused(base.systemAdminPolicyEndpoint, None, Status.Unauthorized)
      },
      test("an invalid token with 401") {
        refused(base.systemAdminPolicyEndpoint, Some("invalid"), Status.Unauthorized)
      },
      test("the anonymous user with 403") {
        refused(base.systemAdminPolicyEndpoint, Some("anonymous"), Status.Forbidden)
      },
      test("an active user who is not a system admin with 403") {
        refused(base.systemAdminPolicyEndpoint, Some("normal"), Status.Forbidden)
      },
      test("an inactive system admin with 403") {
        refused(base.systemAdminPolicyEndpoint, Some("inactive"), Status.Forbidden)
      },
      test("and lets an active system admin through") {
        call(base.systemAdminPolicyEndpoint, Some("admin"))
          .map((status, principal) => assertTrue(status == Status.Ok, principal.contains(systemAdmin)))
      },
    ),
    suite("Authenticated refuses in the security logic")(
      test("a caller without credentials with 401") {
        refused(base.authenticatedPolicyEndpoint("reason"), None, Status.Unauthorized)
      },
      test("the anonymous user with 403") {
        refused(base.authenticatedPolicyEndpoint("reason"), Some("anonymous"), Status.Forbidden)
      },
      test("an inactive user with 403") {
        refused(base.authenticatedPolicyEndpoint("reason"), Some("inactive"), Status.Forbidden)
      },
      test("and lets an active user through") {
        call(base.authenticatedPolicyEndpoint("reason"), Some("normal"))
          .map((status, principal) => assertTrue(status == Status.Ok, principal.contains(activeUser)))
      },
    ),
    suite("ProjectAdmin refuses in the security logic")(
      test("a caller without credentials with 401") {
        refused(base.projectAdminPolicyEndpoint("path projectIri"), None, Status.Unauthorized)
      },
      test("the anonymous user with 403") {
        refused(base.projectAdminPolicyEndpoint("path projectIri"), Some("anonymous"), Status.Forbidden)
      },
      test("an inactive user with 403") {
        refused(base.projectAdminPolicyEndpoint("path projectIri"), Some("inactive"), Status.Forbidden)
      },
      test("and hands an active user to the handler as a candidate") {
        call(base.projectAdminPolicyEndpoint("path projectIri"), Some("normal"))
          .map((status, candidate) => assertTrue(status == Status.Ok, candidate.map(_.user).contains(activeUser)))
      },
    ),
    suite("ObjectAccess for a write refuses in the security logic")(
      test("a caller without credentials with 401") {
        refused(base.authenticatedObjectAccessPolicyEndpoint("the responder"), None, Status.Unauthorized)
      },
      test("the anonymous user with 403") {
        refused(base.authenticatedObjectAccessPolicyEndpoint("the responder"), Some("anonymous"), Status.Forbidden)
      },
      test("an inactive user with 403") {
        refused(base.authenticatedObjectAccessPolicyEndpoint("the responder"), Some("inactive"), Status.Forbidden)
      },
      test("and hands an active user to the handler") {
        call(base.authenticatedObjectAccessPolicyEndpoint("the responder"), Some("normal"))
          .map((status, principal) => assertTrue(status == Status.Ok, principal.contains(activeUser)))
      },
    ),
    test("Public answers a caller without credentials") {
      for {
        response <-
          ZIO.scoped(
            ZioHttpInterpreter()
              .toHttp(List(base.publicPolicyEndpoint("reason").get.in("probe").zServerLogic[Any](_ => ZIO.unit)))
              .runZIO(Request.get(URL.decode("/probe").toOption.get)),
          )
      } yield assertTrue(response.status == Status.Ok)
    },
    suite("ObjectAccess")(
      test("refuses an invalid token with 401 instead of treating the caller as anonymous") {
        refused(base.objectAccessPolicyEndpoint("the responder"), Some("invalid"), Status.Unauthorized)
      },
      test("hands a caller without credentials to the handler as the anonymous user") {
        call(base.objectAccessPolicyEndpoint("the responder"), None)
          .map((status, principal) => assertTrue(status == Status.Ok, principal.contains(AnonymousUser)))
      },
      test("hands an authenticated caller to the handler") {
        call(base.objectAccessPolicyEndpoint("the responder"), Some("normal"))
          .map((status, principal) => assertTrue(status == Status.Ok, principal.contains(activeUser)))
      },
    ),
  )
}
