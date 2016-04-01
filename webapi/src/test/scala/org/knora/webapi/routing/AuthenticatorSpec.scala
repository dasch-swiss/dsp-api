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
import org.knora.webapi.messages.v1respondermessages.usermessages._
import org.knora.webapi.responders.RESPONDER_MANAGER_ACTOR_NAME
import org.knora.webapi.routing.Authenticator.{INVALID_CREDENTIALS_NON_FOUND, INVALID_CREDENTIALS_NO_USERNAME_SUPPLIED, INVALID_CREDENTIALS_USERNAME_OR_PASSWORD}
import org.knora.webapi.{CoreSpec, InvalidCredentialsException, SharedTestData}
import org.scalatest.PrivateMethodTester

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

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

    val usernameCorrect = SharedTestData.rootUserProfileV1.userData.username.get
    val usernameWrong = "wrong"
    val usernameEmpty = ""

    val passwordCorrect = "test"
    val passwordCorrectHashed = SharedTestData.rootUserProfileV1.userData.hashedpassword.get
    val passwordWrong = "wrong"
    val passwordEmpty = ""

    val mockUserProfileV1 = SharedTestData.rootUserProfileV1

    val mockUsersActor = actor(RESPONDER_MANAGER_ACTOR_NAME)(new Act {
        become {
            case UserProfileByUsernameGetRequestV1(submittedUsername, clean) => {
                if (submittedUsername == usernameCorrect) {
                    sender !  Some(mockUserProfileV1)
                } else {
                    sender ! None
                }
            }
        }
    })

    val getUserProfileByUsername = PrivateMethod[Try[UserProfileV1]]('getUserProfileByUsername)
    val authenticateCredentials = PrivateMethod[Try[String]]('authenticateCredentials)

    "During Authentication " when {
        "called, the 'getUserProfile' method " should {
            "succeed with the correct 'username' " in {
                Authenticator invokePrivate getUserProfileByUsername(usernameCorrect, system, timeout, executionContext) should be(Success(mockUserProfileV1))
            }
            "fail with the wrong 'username' " in {
                Authenticator invokePrivate getUserProfileByUsername(usernameWrong, system, timeout, executionContext) should be(Failure(InvalidCredentialsException(INVALID_CREDENTIALS_USERNAME_OR_PASSWORD)))
            }
            "fail when not providing a username " in {
                Authenticator invokePrivate getUserProfileByUsername(usernameEmpty, system, timeout, executionContext) should be(Failure(InvalidCredentialsException(INVALID_CREDENTIALS_NO_USERNAME_SUPPLIED)))
            }
        }
        "called, the 'authenticateCredentials' method " should {
            "succeed with the correct 'username' / correct 'password' " in {
                Authenticator invokePrivate authenticateCredentials(usernameCorrect, passwordCorrect, false, system) should be(Success("0"))
            }
            "fail with correct 'username' / wrong 'password' " in {
                Authenticator invokePrivate authenticateCredentials(usernameCorrect, passwordWrong, false, system) should be(Failure(InvalidCredentialsException(INVALID_CREDENTIALS_USERNAME_OR_PASSWORD)))
            }
        }
    }


}
