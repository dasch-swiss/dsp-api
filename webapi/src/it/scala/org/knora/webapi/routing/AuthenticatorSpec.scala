/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.testkit.ImplicitSender
import akka.util.Timeout
import org.scalatest.PrivateMethodTester
import dsp.errors.BadCredentialsException
import dsp.errors.BadRequestException

import org.knora.webapi._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraJWTTokenCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraPasswordCredentialsV2
import org.knora.webapi.routing.Authenticator.AUTHENTICATION_INVALIDATION_CACHE_NAME
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util.cache.CacheUtil

object AuthenticatorSpec {
  private val rootUser         = SharedTestDataADM.rootUser
  private val rootUserEmail    = rootUser.email
  private val rootUserPassword = "test"
}

class AuthenticatorSpec extends CoreSpec with ImplicitSender with PrivateMethodTester {

  implicit val timeout: Timeout = appConfig.defaultTimeoutAsDuration

  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  "During Authentication" when {
    "called, the 'getUserADMByEmail' method " should {
      "succeed with the correct 'email' " in {
        val resF = UnsafeZioRun.runToFuture(
          Authenticator.getUserByIdentifier(UserIdentifierADM(maybeEmail = Some(AuthenticatorSpec.rootUserEmail)))
        )
        resF map { res =>
          assert(res == AuthenticatorSpec.rootUser)
        }
      }

      "fail with the wrong 'email' " in {
        val resF = UnsafeZioRun.runToFuture(
          Authenticator.getUserByIdentifier(
            UserIdentifierADM(maybeEmail = Some("wronguser@example.com"))
          )
        )
        resF map { _ =>
          assertThrows(BadCredentialsException)
        }
      }

      "fail when not providing anything " in {
        a[BadRequestException] should be thrownBy {
          throw UnsafeZioRun.run(Authenticator.getUserByIdentifier(UserIdentifierADM())).causeOption.get.squash
        }
      }
    }

    "called, the 'authenticateCredentialsV2' method" should {
      "succeed with correct email/password" in {
        val correctPasswordCreds =
          KnoraPasswordCredentialsV2(
            UserIdentifierADM(maybeEmail = Some(AuthenticatorSpec.rootUserEmail)),
            AuthenticatorSpec.rootUserPassword
          )
        val resF = UnsafeZioRun.runToFuture(Authenticator.authenticateCredentialsV2(Some(correctPasswordCreds)))
        resF map { res => assert(res) }
      }
      "fail with unknown email" in {
        val wrongPasswordCreds =
          KnoraPasswordCredentialsV2(UserIdentifierADM(maybeEmail = Some("wrongemail@example.com")), "wrongpassword")
        val resF = UnsafeZioRun.runToFuture(Authenticator.authenticateCredentialsV2(Some(wrongPasswordCreds)))
        resF map { _ => assertThrows(BadCredentialsException) }
      }
      "fail with wrong password" in {
        val wrongPasswordCreds =
          KnoraPasswordCredentialsV2(
            UserIdentifierADM(maybeEmail = Some(AuthenticatorSpec.rootUserEmail)),
            "wrongpassword"
          )
        val resF = UnsafeZioRun.runToFuture(Authenticator.authenticateCredentialsV2(Some(wrongPasswordCreds)))
        resF map { _ => assertThrows(BadCredentialsException) }
      }
      "succeed with correct token" in {
        val token = JWTHelper.createToken(
          "http://rdfh.ch/users/X-T8IkfQTKa86UWuISpbOA",
          appConfig.jwtSecretKey,
          appConfig.jwtLongevityAsDuration,
          appConfig.knoraApi.externalKnoraApiHostPort
        )
        val tokenCreds = KnoraJWTTokenCredentialsV2(token)
        val resF       = UnsafeZioRun.runToFuture(Authenticator.authenticateCredentialsV2(Some(tokenCreds)))
        resF map { res => assert(res) }
      }
      "fail with invalidated token" in {
        val token = JWTHelper.createToken(
          "http://rdfh.ch/users/X-T8IkfQTKa86UWuISpbOA",
          appConfig.jwtSecretKey,
          appConfig.jwtLongevityAsDuration,
          appConfig.knoraApi.externalKnoraApiHostPort
        )
        val tokenCreds = KnoraJWTTokenCredentialsV2(token)
        CacheUtil.put(AUTHENTICATION_INVALIDATION_CACHE_NAME, tokenCreds.jwtToken, tokenCreds.jwtToken)

        assertThrows[BadCredentialsException] {
          throw UnsafeZioRun.run(Authenticator.authenticateCredentialsV2(Some(tokenCreds))).causeOption.get.squash
        }
      }
      "fail with wrong token" in {
        val tokenCreds = KnoraJWTTokenCredentialsV2("123456")

        assertThrows[BadCredentialsException] {
          throw UnsafeZioRun.run(Authenticator.authenticateCredentialsV2(Some(tokenCreds))).causeOption.get.squash
        }
      }
    }

    "called, the 'calculateCookieName' method" should {
      "succeed with generating the name" in {
        UnsafeZioRun.runOrThrow(Authenticator.calculateCookieName()) should equal(
          "KnoraAuthenticationGAXDALRQFYYDUMZTGMZQ9999"
        )
      }
    }
  }
}
