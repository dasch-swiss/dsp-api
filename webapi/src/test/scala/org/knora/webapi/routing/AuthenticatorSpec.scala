/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

import akka.actor.ActorDSL._
import akka.testkit.ImplicitSender
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.admin.responder.usersmessages.{UserADM, UserGetADM, UserInformationTypeADM}
import org.knora.webapi.messages.v2.routing.authenticationmessages.{KnoraPasswordCredentialsV2, KnoraTokenCredentialsV2}
import org.knora.webapi.responders.RESPONDER_MANAGER_ACTOR_NAME
import org.knora.webapi.routing.Authenticator.AUTHENTICATION_INVALIDATION_CACHE_NAME
import org.knora.webapi.util.{ActorUtil, CacheUtil}
import org.knora.webapi._
import org.scalatest.PrivateMethodTester

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

object AuthenticatorSpec {
    val config = ConfigFactory.parseString(
        """
        app {

        }
        """.stripMargin)
}

class AuthenticatorSpec extends CoreSpec("AuthenticationTestSystem") with ImplicitSender with PrivateMethodTester {

    implicit val executionContext = system.dispatcher
    implicit val timeout: Timeout = Duration(5, SECONDS)

    val rootUser = SharedTestDataADM.rootUser
    val rootUserEmail = rootUser.email
    val rootUserPassword = "test"


    val mockUsersActor = actor(RESPONDER_MANAGER_ACTOR_NAME)(new Act {
        become {
            case UserGetADM(maybeIri, maybeEmail, userInformationTypeADM, requestingUser) => {
                if (maybeEmail.contains(rootUserEmail)) {
                    ActorUtil.future2Message(sender, Future(Some(rootUser)), logger)
                } else {
                    ActorUtil.future2Message(sender, Future(None), logger)
                }
            }
        }
    })

    val getUserADMByEmail = PrivateMethod[Try[UserADM]]('getUserADMByEmail)
    val authenticateCredentialsV2 = PrivateMethod[Boolean]('authenticateCredentialsV2)

    "During Authentication" when {
        "called, the 'getUserADMByEmail' method " should {
            "succeed with the correct 'email' " in {
                Authenticator invokePrivate getUserADMByEmail(rootUserEmail, system, timeout, executionContext) should be(rootUser)
            }

            "fail with the wrong 'email' " in {
                an [BadCredentialsException] should be thrownBy {
                    Authenticator invokePrivate getUserADMByEmail("wronguser@example.com", system, timeout, executionContext)
                }
            }

            "fail when not providing a email " in {
                an [BadCredentialsException] should be thrownBy {
                    Authenticator invokePrivate getUserADMByEmail("", system, timeout, executionContext)
                }
            }
        }
        "called, the 'authenticateCredentialsV2' method" should {
            "succeed with correct email/password" in {
                val correctPasswordCreds = KnoraPasswordCredentialsV2(rootUserEmail, rootUserPassword)
                Authenticator invokePrivate authenticateCredentialsV2(Some(correctPasswordCreds), system, executionContext) should be (true)
            }
            "fail with unknown email" in {
                an [BadCredentialsException] should be thrownBy {
                    val wrongPasswordCreds = KnoraPasswordCredentialsV2("wrongemail", "wrongpassword")
                    Authenticator invokePrivate authenticateCredentialsV2(Some(wrongPasswordCreds), system, executionContext)
                }
            }
            "fail with wrong password" in {
                an [BadCredentialsException] should be thrownBy {
                    val wrongPasswordCreds = KnoraPasswordCredentialsV2(rootUserEmail, "wrongpassword")
                    Authenticator invokePrivate authenticateCredentialsV2(Some(wrongPasswordCreds), system, executionContext)
                }
            }
            "succeed with correct token" in {
                val token = JWTHelper.createToken("myuseriri", settings.jwtSecretKey, settings.jwtLongevity)
                val tokenCreds = KnoraTokenCredentialsV2(token)
                Authenticator invokePrivate authenticateCredentialsV2(Some(tokenCreds), system, executionContext) should be (true)
            }
            "fail with invalidated token" in {
                an [BadCredentialsException] should be thrownBy {
                    val token = JWTHelper.createToken("myuseriri", settings.jwtSecretKey, settings.jwtLongevity)
                    val tokenCreds = KnoraTokenCredentialsV2(token)
                    CacheUtil.put(AUTHENTICATION_INVALIDATION_CACHE_NAME, tokenCreds.token, tokenCreds.token)
                    Authenticator invokePrivate authenticateCredentialsV2(Some(tokenCreds), system, executionContext)
                }
            }
            "fail with wrong token" in {
                an [BadCredentialsException] should be thrownBy {
                    val tokenCreds = KnoraTokenCredentialsV2("123456")
                    Authenticator invokePrivate authenticateCredentialsV2(Some(tokenCreds), system, executionContext)
                }
            }

        }
    }
}
