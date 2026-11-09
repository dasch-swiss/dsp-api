/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3

import org.junit.runner.RunWith
import sttp.tapir.EndpointOutput
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
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.Policy
import org.knora.webapi.slice.common.api.RequiresMethod
import org.knora.webapi.slice.security.AuthenticatorError

@RunWith(classOf[DspZTestJUnitRunner])
class V3BaseEndpointSpec extends ZIOSpecDefault {

  private object helper extends EndpointHelper

  private val activeUser =
    User("http://rdfh.ch/users/normal", "normal", "normal@example.com", "Given", "Family", status = true, "en")

  private val systemAdmin = activeUser.copy(
    id = "http://rdfh.ch/users/admin",
    username = "admin",
    permissions = PermissionsDataADM(Map(SystemProject.id.value -> List(KnoraGroupRepo.builtIn.SystemAdmin.id.value))),
  )

  private val usersByToken = Map(
    "normal"         -> activeUser,
    "admin"          -> systemAdmin,
    "inactive-admin" -> systemAdmin.copy(status = false),
    "anonymous"      -> AnonymousUser,
  )

  private val authenticator = SparqlPassthroughTestEnv.authenticatorAnswering(token =>
    ZIO.fromOption(usersByToken.get(token)).orElseFail(AuthenticatorError.BadCredentials),
  )

  // The checks these factories use read only the user, never a service.
  private val base = V3BaseEndpoint(authenticator, new AuthorizationRestService(null, null))

  private val errorOut: EndpointOutput.OneOf[V3ErrorInfo, V3ErrorInfo] = oneOf(
    helper.notFoundVariant(V3ErrorCode.project_not_found),
  )

  private def policyOf[S, P](
    factory: RequiresMethod[ZPartialServerEndpoint[Any, S, P, Unit, V3ErrorInfo, Unit, Any]],
  ): Option[Policy] =
    factory.get.in("probe").serverLogic[Any](_ => _ => ZIO.unit).endpoint.attribute(Policy.key)

  /** The status, and the principal the server logic received, or `None` if it never ran. */
  private def call[S, P](
    factory: RequiresMethod[ZPartialServerEndpoint[Any, S, P, Unit, V3ErrorInfo, Unit, Any]],
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
    factory: RequiresMethod[ZPartialServerEndpoint[Any, S, P, Unit, V3ErrorInfo, Unit, Any]],
    token: Option[String],
    status: Status,
  ) =
    call(factory, token).map((actual, principal) => assertTrue(actual == status, principal.isEmpty))

  private def admitted[S, P](
    factory: RequiresMethod[ZPartialServerEndpoint[Any, S, P, Unit, V3ErrorInfo, Unit, Any]],
    token: Option[String],
  ) =
    call(factory, token).map((actual, principal) => assertTrue(actual == Status.Ok, principal.nonEmpty))

  val spec: Spec[Any, Any] = suite("V3BaseEndpoint policy factories")(
    suite("set their policy on the completed server endpoint")(
      test("Public") {
        assertTrue(
          base
            .publicPolicy(errorOut, "the reason")
            .get
            .in("probe")
            .zServerLogic(_ => ZIO.unit)
            .endpoint
            .attribute(Policy.key)
            .contains(Policy.Public("the reason")),
        )
      },
      test("SystemAdmin") {
        assertTrue(policyOf(base.systemAdminPolicy(errorOut)).contains(Policy.SystemAdmin))
      },
      test("ObjectAccess") {
        assertTrue(
          policyOf(base.objectAccessPolicy(errorOut, "the domain"))
            .contains(Policy.ObjectAccess("the domain", admitsAnonymous = true)),
        )
      },
      test("ProjectAdmin") {
        assertTrue(
          policyOf(base.projectAdminPolicy(errorOut, "path projectIri"))
            .contains(Policy.ProjectAdmin("path projectIri")),
        )
      },
    ),
    suite("systemAdminPolicy")(
      test("refuses a caller without credentials with 401") {
        refused(base.systemAdminPolicy(errorOut), None, Status.Unauthorized)
      },
      test("refuses an invalid token with 401") {
        refused(base.systemAdminPolicy(errorOut), Some("invalid"), Status.Unauthorized)
      },
      test("refuses a non-admin with 403") {
        refused(base.systemAdminPolicy(errorOut), Some("normal"), Status.Forbidden)
      },
      test("refuses the anonymous user with 403") {
        refused(base.systemAdminPolicy(errorOut), Some("anonymous"), Status.Forbidden)
      },
      test("refuses an inactive system admin with 403") {
        refused(base.systemAdminPolicy(errorOut), Some("inactive-admin"), Status.Forbidden)
      },
      test("lets a system admin through") {
        call(base.systemAdminPolicy(errorOut), Some("admin"))
          .map((status, principal) => assertTrue(status == Status.Ok, principal.contains(systemAdmin)))
      },
    ),
    suite("projectAdminPolicy")(
      test("refuses a caller without credentials with 401") {
        refused(base.projectAdminPolicy(errorOut, "path projectIri"), None, Status.Unauthorized)
      },
      test("refuses the anonymous user with 403") {
        refused(base.projectAdminPolicy(errorOut, "path projectIri"), Some("anonymous"), Status.Forbidden)
      },
      test("hands an active user a candidate; the project decision is the handler's") {
        admitted(base.projectAdminPolicy(errorOut, "path projectIri"), Some("normal"))
      },
    ),
    suite("publicPolicy")(
      test("admits a caller without credentials") {
        for {
          reached <- Ref.make(false)
          app      = ZioHttpInterpreter().toHttp(
                  List(
                    base
                      .publicPolicy(errorOut, "the reason")
                      .get
                      .in("probe")
                      .zServerLogic(_ => reached.set(true)),
                  ),
                )
          response <- ZIO.scoped(app.runZIO(Request.get(URL.decode("/probe").toOption.get)))
          ran      <- reached.get
        } yield assertTrue(response.status == Status.Ok, ran)
      },
    ),
    suite("objectAccessPolicy")(
      test("refuses an invalid token with 401") {
        refused(base.objectAccessPolicy(errorOut, "the domain"), Some("invalid"), Status.Unauthorized)
      },
      test("admits a caller without credentials as the anonymous user") {
        call(base.objectAccessPolicy(errorOut, "the domain"), None)
          .map((status, principal) => assertTrue(status == Status.Ok, principal.contains(AnonymousUser)))
      },
      test("admits an authenticated user") {
        call(base.objectAccessPolicy(errorOut, "the domain"), Some("normal"))
          .map((status, principal) => assertTrue(status == Status.Ok, principal.contains(activeUser)))
      },
    ),
  )
}
