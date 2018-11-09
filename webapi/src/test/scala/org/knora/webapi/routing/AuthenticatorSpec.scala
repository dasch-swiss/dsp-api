/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.routing

import akka.testkit.ImplicitSender
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.{UserADM, UserIdentifierADM}
import org.knora.webapi.messages.v2.routing.authenticationmessages.{KnoraPasswordCredentialsV2, KnoraTokenCredentialsV2}
import org.knora.webapi.routing.Authenticator.AUTHENTICATION_INVALIDATION_CACHE_NAME
import org.knora.webapi.util.{CacheUtil, StringFormatter}
import org.scalatest.PrivateMethodTester

import scala.concurrent.Future

object AuthenticatorSpec {
    val config = ConfigFactory.parseString(
        """
        app {

        }
        """.stripMargin)


    val rootUser = SharedTestDataADM.rootUser
    val rootUserEmail = rootUser.email
    val rootUserPassword = "test"
}



class AuthenticatorSpec extends CoreSpec("AuthenticationTestSystem") with ImplicitSender with PrivateMethodTester {

    implicit val timeout: Timeout = settings.defaultTimeout

    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val getUserADMByEmail = PrivateMethod[Future[UserADM]]('getUserADMByEmail)
    val authenticateCredentialsV2 = PrivateMethod[Future[Boolean]]('authenticateCredentialsV2)

    "During Authentication" when {
        "called, the 'getUserADMByEmail' method " should {
            "succeed with the correct 'email' " in {
                val resF = Authenticator invokePrivate getUserADMByEmail(AuthenticatorSpec.rootUserEmail, system, timeout, executionContext)
                resF map { res => assert(res == AuthenticatorSpec.rootUser)}
            }

            "fail with the wrong 'email' " in {
                    val resF = Authenticator invokePrivate getUserADMByEmail("wronguser@example.com", system, timeout, executionContext)
                    resF map {res => assertThrows(BadCredentialsException)}
            }

            "fail when not providing a email " in {
                an [BadCredentialsException] should be thrownBy {
                    Authenticator invokePrivate getUserADMByEmail("", system, timeout, executionContext)
                }
            }
        }
        "called, the 'authenticateCredentialsV2' method" should {
            "succeed with correct email/password" in {
                val correctPasswordCreds = KnoraPasswordCredentialsV2(UserIdentifierADM(AuthenticatorSpec.rootUserEmail), AuthenticatorSpec.rootUserPassword)
                val resF = Authenticator invokePrivate authenticateCredentialsV2(Some(correctPasswordCreds), system, executionContext)
                resF map {res => assert(res)}
            }
            "fail with unknown email" in {
                    val wrongPasswordCreds = KnoraPasswordCredentialsV2(UserIdentifierADM("wrongemail"), "wrongpassword")
                    val resF = Authenticator invokePrivate authenticateCredentialsV2(Some(wrongPasswordCreds), system, executionContext)
                    resF map {res => assertThrows(BadCredentialsException)}
            }
            "fail with wrong password" in {
                    val wrongPasswordCreds = KnoraPasswordCredentialsV2(UserIdentifierADM(AuthenticatorSpec.rootUserEmail), "wrongpassword")
                    val resF = Authenticator invokePrivate authenticateCredentialsV2(Some(wrongPasswordCreds), system, executionContext)
                    resF map {res => assertThrows(BadCredentialsException)}
            }
            "succeed with correct token" in {
                val token = JWTHelper.createToken("myuseriri", settings.jwtSecretKey, settings.jwtLongevity)
                val tokenCreds = KnoraTokenCredentialsV2(token)
                val resF = Authenticator invokePrivate authenticateCredentialsV2(Some(tokenCreds), system, executionContext)
                resF map {res => assert(res)}
            }
            "fail with invalidated token" in {
                    val token = JWTHelper.createToken("myuseriri", settings.jwtSecretKey, settings.jwtLongevity)
                    val tokenCreds = KnoraTokenCredentialsV2(token)
                    CacheUtil.put(AUTHENTICATION_INVALIDATION_CACHE_NAME, tokenCreds.token, tokenCreds.token)
                    val resF = Authenticator invokePrivate authenticateCredentialsV2(Some(tokenCreds), system, executionContext)
                    resF map {res => assertThrows(BadCredentialsException)}
            }
            "fail with wrong token" in {
                    val tokenCreds = KnoraTokenCredentialsV2("123456")
                    val resF = Authenticator invokePrivate authenticateCredentialsV2(Some(tokenCreds), system, executionContext)
                    resF map {res => assertThrows(BadCredentialsException)}
            }

        }
    }
}
