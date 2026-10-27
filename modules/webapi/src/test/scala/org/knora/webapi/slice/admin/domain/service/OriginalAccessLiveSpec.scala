/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.util.KnoraSystemInstances.Users.AnonymousUser
import org.knora.webapi.messages.util.KnoraSystemInstances.Users.SystemUser
import org.knora.webapi.responders.admin.AssetPermissionsCache
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.infrastructure.Jwt
import org.knora.webapi.slice.ingest.domain.ProjectShortcode
import org.knora.webapi.slice.ingest.ports.OriginalAccess
import org.knora.webapi.slice.security.Authenticator
import org.knora.webapi.slice.security.AuthenticatorError

@RunWith(classOf[DspZTestJUnitRunner])
class OriginalAccessLiveSpec extends ZIOSpecDefault {

  private val validToken  = "valid-token"
  private val grantedFile = "granted.jp2"
  private val shortcode   = ProjectShortcode.unsafeFrom("0001")

  private val authenticator: Authenticator = new Authenticator {
    def calculateCookieName(): String                                                         = "stub"
    def invalidateToken(jwt: String): IO[AuthenticatorError, Unit]                            = ZIO.unit
    def parseToken(jwt: String): IO[AuthenticatorError, Jwt]                                  = ZIO.fail(AuthenticatorError.BadCredentials)
    def authenticate(userIri: UserIri, password: String): IO[AuthenticatorError, (User, Jwt)] =
      ZIO.fail(AuthenticatorError.BadCredentials)
    def authenticate(username: Username, password: String): IO[AuthenticatorError, (User, Jwt)] =
      ZIO.fail(AuthenticatorError.BadCredentials)
    def authenticate(email: Email, password: String): IO[AuthenticatorError, (User, Jwt)] =
      ZIO.fail(AuthenticatorError.BadCredentials)
    def authenticate(jwtToken: String): IO[AuthenticatorError, User] =
      if (jwtToken == validToken) ZIO.succeed(SystemUser) else ZIO.fail(AuthenticatorError.BadCredentials)
  }

  // The system user may view `granted.jp2`; nobody else may view anything.
  private val cacheLayer: ULayer[AssetPermissionsCache] = ZLayer {
    AssetPermissionsCache
      .makeCache(10, 1.minute) { key =>
        val canView = key.userIri.value == SystemUser.id && key.filename.value == grantedFile
        ZIO.succeed(
          AssetAccess.from(Option.when(canView)(Permission.ObjectAccess.View), MediaKind.RasterStillImage, None),
        )
      }
      .map(AssetPermissionsCache(_))
  }

  private def isOriginalGranted(token: Option[String], filename: String) =
    ZIO.serviceWithZIO[OriginalAccess](_.isOriginalGranted(token, shortcode, filename))

  val spec = suite("OriginalAccessLive")(
    test("grants the original to a user with view permission") {
      isOriginalGranted(Some(validToken), grantedFile).map(granted => assertTrue(granted))
    },
    test("withholds the original from a user without view permission") {
      isOriginalGranted(Some(validToken), "other.jp2").map(granted => assertTrue(!granted))
    },
    test("decides for the anonymous user when no token is given") {
      for {
        granted <- isOriginalGranted(None, grantedFile)
      } yield assertTrue(!granted, AnonymousUser.id != SystemUser.id)
    },
    test("fails when the token does not authenticate") {
      isOriginalGranted(Some("invalid-token"), grantedFile).exit.map(exit => assertTrue(exit.isFailure))
    },
  ).provide(ZLayer.succeed(authenticator), cacheLayer, OriginalAccessLive.layer)
}
