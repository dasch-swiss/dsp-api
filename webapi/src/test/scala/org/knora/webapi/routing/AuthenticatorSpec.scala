/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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
import org.knora.webapi.responders.RESPONDER_MANAGER_ACTOR_NAME
import org.knora.webapi.util.ActorUtil
import org.knora.webapi.{BadCredentialsException, CoreSpec, NotFoundException, SharedAdminTestData}
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

/*
 *  This test needs a running http layer, so that different api access authentication schemes can be tested
 *  - Browser basic auth
 *  - Basic auth over API
 *  - Username/password over API
 *  - API Key based authentication
 */

class AuthenticatorSpec extends CoreSpec("AuthenticationTestSystem") with ImplicitSender with PrivateMethodTester {

    implicit val executionContext = system.dispatcher
    implicit val timeout: Timeout = Duration(5, SECONDS)

    val rootUserProfileV1 = SharedAdminTestData.rootUser
    val rootUserEmail = rootUserProfileV1.userData.email.get
    val rootUserPassword = "test"

    val mockUsersActor = actor(RESPONDER_MANAGER_ACTOR_NAME)(new Act {
        become {
            case UserProfileByEmailGetV1(submittedEmail, userProfileType) => {
                if (submittedEmail == "root@example.com") {
                    ActorUtil.future2Message(sender, Future(Some(rootUserProfileV1)), logger)
                } else {
                    ActorUtil.future2Message(sender, Future.failed(throw NotFoundException(s"User '$submittedEmail' not found")), logger)
                }
            }
        }
    })

    val getUserProfileByEmail = PrivateMethod[Try[UserProfileV1]]('getUserProfileByEmail)
    val authenticateCredentials = PrivateMethod[Try[String]]('authenticateCredentials)

    "During Authentication" when {
        "called, the 'getUserProfile' method " should {
            "succeed with the correct 'email' " in {
                Authenticator invokePrivate getUserProfileByEmail(rootUserEmail, system, timeout, executionContext) should be(rootUserProfileV1)
            }

            /* TODO: Find out how to mock correctly */
            "fail with the wrong 'email' " ignore {
                an [BadCredentialsException] should be thrownBy {
                    Authenticator invokePrivate getUserProfileByEmail("wronguser@example.com", system, timeout, executionContext)
                }
            }

            "fail when not providing a email " in {
                an [BadCredentialsException] should be thrownBy {
                    Authenticator invokePrivate getUserProfileByEmail("", system, timeout, executionContext)
                }
            }
        }
        "called, the 'authenticateCredentials' method " should {
            "succeed with the correct 'email' / correct 'password' " in {
                Authenticator invokePrivate authenticateCredentials(rootUserEmail, rootUserPassword, false, system, executionContext) should be("0")
            }
            "fail with correct 'email' / wrong 'password' " in {
                an [BadCredentialsException] should be thrownBy {
                    Authenticator invokePrivate authenticateCredentials(rootUserEmail, "wrongpass", false, system, executionContext)
                }
            }
        }
    }


}
