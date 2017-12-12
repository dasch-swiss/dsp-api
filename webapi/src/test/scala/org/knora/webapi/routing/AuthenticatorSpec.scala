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
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.messages.v2.routing.authenticationmessages.{KnoraPasswordCredentialsV2, KnoraTokenCredentialsV2}
import org.knora.webapi.responders.RESPONDER_MANAGER_ACTOR_NAME
import org.knora.webapi.routing.Authenticator.AUTHENTICATION_INVALIDATION_CACHE_NAME
import org.knora.webapi.util.{ActorUtil, CacheUtil}
import org.knora.webapi.{BadCredentialsException, CoreSpec, SharedTestDataV1}
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

    val rootUserProfileV1 = SharedTestDataV1.rootUser
    val rootUserEmail = rootUserProfileV1.userData.email.get
    val rootUserPassword = "test"


    val mockUsersActor = actor(RESPONDER_MANAGER_ACTOR_NAME)(new Act {
        become {
            case UserProfileByEmailGetV1(submittedEmail, userProfileType) => {
                if (submittedEmail == "root@example.com") {
                    ActorUtil.future2Message(sender, Future(Some(rootUserProfileV1)), logger)
                } else {
                    ActorUtil.future2Message(sender, Future(None), logger)
                }
            }
        }
    })

    val getUserProfileV1ByEmail = PrivateMethod[Try[UserProfileV1]]('getUserProfileV1ByEmail)
    val authenticateCredentialsV2 = PrivateMethod[Boolean]('authenticateCredentialsV2)

    "During Authentication" when {
        "called, the 'getUserProfileV1ByEmail' method " should {
            "succeed with the correct 'email' " in {
                Authenticator invokePrivate getUserProfileV1ByEmail(rootUserEmail, system, timeout, executionContext) should be(rootUserProfileV1)
            }

            "fail with the wrong 'email' " in {
                an [BadCredentialsException] should be thrownBy {
                    Authenticator invokePrivate getUserProfileV1ByEmail("wronguser@example.com", system, timeout, executionContext)
                }
            }

            "fail when not providing a email " in {
                an [BadCredentialsException] should be thrownBy {
                    Authenticator invokePrivate getUserProfileV1ByEmail("", system, timeout, executionContext)
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
