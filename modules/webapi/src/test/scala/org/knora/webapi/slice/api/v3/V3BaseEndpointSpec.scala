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

import java.time.Instant

import org.knora.jwt.Ed25519TestSigner
import org.knora.jwt.JwtClaim
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.util.KnoraSystemInstances.Users.AnonymousUser
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo.builtIn.SystemProject
import org.knora.webapi.slice.api.admin.service.SparqlPassthroughTestEnv
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.Policy
import org.knora.webapi.slice.common.api.RequiresMethod
import org.knora.webapi.slice.common.domain.ServiceName
import org.knora.webapi.slice.security.AuthenticatorError
import org.knora.webapi.slice.security.ExportCaller
import org.knora.webapi.slice.security.ServicePrincipal
import org.knora.webapi.slice.security.ServiceTokenVerifier
import org.knora.webapi.slice.security.TrustedServiceKeys

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

  private val serviceKeys = Ed25519TestSigner.generateKeyPair()
  private val otherKeys   = Ed25519TestSigner.generateKeyPair()
  private val trustedKeys = TrustedServiceKeys
    .parse(s"""[{"name":"svc-a","kid":"a-1","publicKey":"${Ed25519TestSigner.spkiBase64(serviceKeys.getPublic)}"}]""")
    .fold(e => throw new IllegalStateException(e), identity)
  private val knoraApi = KnoraApi("0.0.0.0", 3333, "https", "api.example.org", 443, 3333)
  private val now      = 1_800_000_000L

  private val serviceClaim = JwtClaim(
    issuer = Some("svc-a"),
    subject = Some("svc-a"),
    audience = Some(Set("https://api.example.org")),
    issuedAt = Some(now),
    expiration = Some(now + 300),
    jwtId = Some("jti-1"),
  )
  private val serviceToken                = Ed25519TestSigner.sign(serviceClaim, "a-1", serviceKeys.getPrivate)
  private val serviceCaller: ExportCaller =
    ExportCaller.Service(ServicePrincipal(ServiceName.unsafeFrom("svc-a"), "a-1", "jti-1"))

  private def serviceTokenWith(alg: String, kid: String) =
    Ed25519TestSigner.signRaw(
      Ed25519TestSigner.headerJson(alg = alg, kid = Some(kid)),
      serviceClaim.toJson,
      serviceKeys.getPrivate,
    )

  private val forgedServiceToken  = Ed25519TestSigner.sign(serviceClaim, "a-1", otherKeys.getPrivate)
  private val unknownKidToken     = serviceTokenWith("EdDSA", "c-1")
  private val hs256WithServiceKid = serviceTokenWith("HS256", "a-1")
  private val lowerCaseAlgToken   = serviceTokenWith("eddsa", "a-1")
  private val noAlgToken          = Ed25519TestSigner.signRaw(
    """{"typ":"JWT","kid":"a-1"}""",
    serviceClaim.toJson,
    serviceKeys.getPrivate,
  )

  private val usersByToken = Map(
    "normal"         -> activeUser,
    "admin"          -> systemAdmin,
    "inactive-admin" -> systemAdmin.copy(status = false),
    "anonymous"      -> AnonymousUser,
  ) ++
    // The stub knows every service-shaped token as the system admin: a 401 then shows the token never reached the
    // user path, and an admission as the admin shows it did.
    List(serviceToken, forgedServiceToken, unknownKidToken, hs256WithServiceKid, lowerCaseAlgToken, noAlgToken)
      .map(_ -> systemAdmin)

  private val authenticator = SparqlPassthroughTestEnv.authenticatorAnswering(token =>
    ZIO.fromOption(usersByToken.get(token)).orElseFail(AuthenticatorError.BadCredentials),
  )

  // The checks these factories use read only the user, never a service.
  private val authorization   = new AuthorizationRestService(null, null)
  private val base            = V3BaseEndpoint(authenticator, authorization, ServiceTokenVerifier(trustedKeys, knoraApi))
  private val baseWithoutKeys =
    V3BaseEndpoint(authenticator, authorization, ServiceTokenVerifier(TrustedServiceKeys.empty, knoraApi))

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

  /** As [[call]], at the time the service tokens were issued. */
  private def callAtIssue[S, P](
    factory: RequiresMethod[ZPartialServerEndpoint[Any, S, P, Unit, V3ErrorInfo, Unit, Any]],
    token: String,
  ): Task[(Status, Option[P])] =
    TestClock.setTime(Instant.ofEpochSecond(now)) *> call(factory, Some(token))

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
      test("ObjectAccess for a write") {
        assertTrue(
          policyOf(base.authenticatedObjectAccessPolicy(errorOut, "the domain"))
            .contains(Policy.ObjectAccess("the domain", admitsAnonymous = false)),
        )
      },
      test("SystemAdminOrExportService") {
        assertTrue(
          policyOf(base.systemAdminOrExportServicePolicy(errorOut)).contains(Policy.SystemAdminOrExportService),
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
    suite("systemAdminOrExportServicePolicy")(
      test("admits a valid service token as the service") {
        callAtIssue(base.systemAdminOrExportServicePolicy(errorOut), serviceToken)
          .map((status, caller) => assertTrue(status == Status.Ok, caller.contains(serviceCaller)))
      },
      test("refuses a valid-looking service token with 401 when no keys are configured") {
        callAtIssue(baseWithoutKeys.systemAdminOrExportServicePolicy(errorOut), serviceToken)
          .map((status, caller) => assertTrue(status == Status.Unauthorized, caller.isEmpty))
      },
      test("refuses a service token signed by another key with 401") {
        callAtIssue(base.systemAdminOrExportServicePolicy(errorOut), forgedServiceToken)
          .map((status, caller) => assertTrue(status == Status.Unauthorized, caller.isEmpty))
      },
      test("refuses a service token with an unknown kid with 401") {
        callAtIssue(base.systemAdminOrExportServicePolicy(errorOut), unknownKidToken)
          .map((status, caller) => assertTrue(status == Status.Unauthorized, caller.isEmpty))
      },
      test("refuses a token whose header is malformed with 401") {
        refused(base.systemAdminOrExportServicePolicy(errorOut), Some("not-a-header.e30.c2ln"), Status.Unauthorized)
      },
      // On the user path the real authenticator refuses these with 401 (pinned in AuthenticatorLiveSpec).
      test("sends a token whose alg is not exactly EdDSA down the user path") {
        ZIO
          .foreach(List(hs256WithServiceKid, lowerCaseAlgToken, noAlgToken))(token =>
            callAtIssue(base.systemAdminOrExportServicePolicy(errorOut), token),
          )
          .map(results => assertTrue(results.forall(_ == (Status.Ok, Some(ExportCaller.Admin(systemAdmin))))))
      },
      test("refuses a caller without credentials with 401") {
        refused(base.systemAdminOrExportServicePolicy(errorOut), None, Status.Unauthorized)
      },
      test("refuses a non-admin with 403") {
        refused(base.systemAdminOrExportServicePolicy(errorOut), Some("normal"), Status.Forbidden)
      },
      test("refuses an inactive system admin with 403") {
        refused(base.systemAdminOrExportServicePolicy(errorOut), Some("inactive-admin"), Status.Forbidden)
      },
      test("admits a system admin as the user") {
        call(base.systemAdminOrExportServicePolicy(errorOut), Some("admin"))
          .map((status, caller) => assertTrue(status == Status.Ok, caller.contains(ExportCaller.Admin(systemAdmin))))
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
    suite("authenticatedObjectAccessPolicy")(
      test("refuses a caller without credentials with 401") {
        refused(base.authenticatedObjectAccessPolicy(errorOut, "the domain"), None, Status.Unauthorized)
      },
      test("refuses an invalid token with 401") {
        refused(base.authenticatedObjectAccessPolicy(errorOut, "the domain"), Some("invalid"), Status.Unauthorized)
      },
      test("refuses the anonymous user with 403") {
        refused(base.authenticatedObjectAccessPolicy(errorOut, "the domain"), Some("anonymous"), Status.Forbidden)
      },
      test("hands an authenticated user to the handler") {
        call(base.authenticatedObjectAccessPolicy(errorOut, "the domain"), Some("normal"))
          .map((status, principal) => assertTrue(status == Status.Ok, principal.contains(activeUser)))
      },
    ),
  )
}
