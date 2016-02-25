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
import org.knora.webapi.routing.Authenticator.{BAD_CRED_PASSWORD_MISMATCH, BAD_CRED_USERNAME_NOT_SUPPLIED, BAD_CRED_USER_NOT_FOUND}
import org.knora.webapi.{BadCredentialsException, CoreSpec}
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

    val usernameCorrect = "root"
    val usernameWrong = "wrong"
    val usernameEmpty = ""

    val passwordCorrect = "test"
    val passwordCorrectHashed = "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3" // hashed with sha-1
    val passwordWrong = "wrong"
    val passwordEmpty = ""


    val lang = "en"
    val user_id = Some("http://data.knora.org/users/91e19f1e01")
    val token = None
    val username = Some(usernameCorrect)
    val firstname = Some("Administrator")
    val lastname = Some("Admin")
    val email = Some("test@test.ch")
    val password = Some(passwordCorrectHashed)

    val mockUserProfileV1 = UserProfileV1(UserDataV1(lang, user_id, token, username, firstname, lastname, email, password), Nil, Nil)

    val mockUsersActor = actor(RESPONDER_MANAGER_ACTOR_NAME)(new Act {
        become {
            case UserProfileByUsernameGetRequestV1(submittedUsername) => {
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
                Authenticator invokePrivate getUserProfileByUsername(usernameWrong, system, timeout, executionContext) should be(Failure(BadCredentialsException(BAD_CRED_USER_NOT_FOUND)))
            }

            "fail when not providing a username " in {
                Authenticator invokePrivate getUserProfileByUsername(usernameEmpty, system, timeout, executionContext) should be(Failure(BadCredentialsException(BAD_CRED_USERNAME_NOT_SUPPLIED)))
            }
        }
        "called, the 'authenticateCredentials' method " should {
            "succeed with the correct 'username' / correct 'password' " in {
                Authenticator invokePrivate authenticateCredentials(usernameCorrect, passwordCorrect, false, system) should be(Success("0"))
            }
            "fail with correct 'username' / wrong 'password' " in {
                Authenticator invokePrivate authenticateCredentials(usernameCorrect, passwordWrong, false, system) should be(Failure(BadCredentialsException(BAD_CRED_PASSWORD_MISMATCH)))
            }
        }
    }


}
