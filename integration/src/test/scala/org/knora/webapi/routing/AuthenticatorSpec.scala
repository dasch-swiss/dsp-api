/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import org.apache.pekko
import org.scalatest.PrivateMethodTester
import zio.ZIO

import dsp.errors.BadCredentialsException
import dsp.errors.BadRequestException
import org.knora.webapi._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraJWTTokenCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraPasswordCredentialsV2
import org.knora.webapi.routing.Authenticator.AUTHENTICATION_INVALIDATION_CACHE_NAME
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util.ZioScalaTestUtil.assertFailsWithA
import org.knora.webapi.util.cache.CacheUtil

import pekko.testkit.ImplicitSender

object AuthenticatorSpec {
  private val rootUser         = SharedTestDataADM.rootUser
  private val rootUserEmail    = rootUser.email
  private val rootUserPassword = "test"
}

class AuthenticatorSpec extends CoreSpec with ImplicitSender with PrivateMethodTester {

  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  private def testUserAdmFromIri(iri: String) = UserADM(iri, "", "", "", "", false, "")

  "During Authentication" when {
    "called, the 'getUserADMByEmail' method " should {
      "succeed with the correct 'email' " in {
        val user = UnsafeZioRun.runOrThrow(
          Authenticator.getUserByIdentifier(UserIdentifierADM(maybeEmail = Some(AuthenticatorSpec.rootUserEmail)))
        )
        assert(user == AuthenticatorSpec.rootUser)
      }

      "fail with the wrong 'email' " in {
        val actual = UnsafeZioRun.run(
          Authenticator.getUserByIdentifier(UserIdentifierADM(maybeEmail = Some("wronguser@example.com")))
        )
        assertFailsWithA[BadCredentialsException](actual)
      }

      "fail when not providing anything " in {
        val actual = UnsafeZioRun.run(ZIO.attempt(UserIdentifierADM()))

        assertFailsWithA[BadRequestException](actual)
      }
    }

    "called, the 'authenticateCredentialsV2' method" should {
      "succeed with correct email/password" in {
        val correctPasswordCreds =
          KnoraPasswordCredentialsV2(
            UserIdentifierADM(maybeEmail = Some(AuthenticatorSpec.rootUserEmail)),
            AuthenticatorSpec.rootUserPassword
          )
        val isAuthenticated =
          UnsafeZioRun.runOrThrow(Authenticator.authenticateCredentialsV2(Some(correctPasswordCreds)))
        assert(isAuthenticated)
      }
      "fail with unknown email" in {
        val invalidEmailCreds =
          KnoraPasswordCredentialsV2(UserIdentifierADM(maybeEmail = Some("wrongemail@example.com")), "wrongpassword")
        val resF = UnsafeZioRun.run(Authenticator.authenticateCredentialsV2(Some(invalidEmailCreds)))
        assertFailsWithA[BadCredentialsException](resF)
      }
      "fail with wrong password" in {
        val invalidPasswordCreds =
          KnoraPasswordCredentialsV2(
            UserIdentifierADM(maybeEmail = Some(AuthenticatorSpec.rootUserEmail)),
            "wrongpassword"
          )
        val actual = UnsafeZioRun.run(Authenticator.authenticateCredentialsV2(Some(invalidPasswordCreds)))
        assertFailsWithA[BadCredentialsException](actual)
      }
      "succeed with correct token" in {
        val isAuthenticated = UnsafeZioRun.runOrThrow(
          for {
            token <-
              JwtService.createJwt(testUserAdmFromIri("http://rdfh.ch/users/X-T8IkfQTKa86UWuISpbOA")).map(_.jwtString)
            tokenCreds = KnoraJWTTokenCredentialsV2(token)
            result    <- Authenticator.authenticateCredentialsV2(Some(tokenCreds))
          } yield result
        )
        assert(isAuthenticated)
      }
      "fail with invalidated token" in {
        val actual = UnsafeZioRun
          .run(for {
            token <-
              JwtService.createJwt(testUserAdmFromIri("http://rdfh.ch/users/X-T8IkfQTKa86UWuISpbOA")).map(_.jwtString)
            tokenCreds = KnoraJWTTokenCredentialsV2(token)
            _          = CacheUtil.put(AUTHENTICATION_INVALIDATION_CACHE_NAME, tokenCreds.jwtToken, tokenCreds.jwtToken)
            result    <- Authenticator.authenticateCredentialsV2(Some(tokenCreds))
          } yield result)
        assertFailsWithA[BadCredentialsException](actual)
      }
      "fail with wrong token" in {
        val invalidTokenCreds = KnoraJWTTokenCredentialsV2("123456")
        val actual            = UnsafeZioRun.run(Authenticator.authenticateCredentialsV2(Some(invalidTokenCreds)))
        assertFailsWithA[BadCredentialsException](actual)
      }
    }

    "called, the 'calculateCookieName' method" should {
      "succeed with generating the name" in {
        val cookieName = UnsafeZioRun.runOrThrow(Authenticator.calculateCookieName())
        cookieName should equal("KnoraAuthenticationGAXDALRQFYYDUMZTGMZQ9999")
      }
    }
  }
}
