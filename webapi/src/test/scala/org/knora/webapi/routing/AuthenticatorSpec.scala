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
import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.v1respondermessages.usermessages._
import org.knora.webapi.responders.RESPONDER_MANAGER_ACTOR_NAME
import org.knora.webapi.{BadCredentialsException, CoreSpec}
import org.scalatest.PrivateMethodTester

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

class AuthenticatorSpec extends CoreSpec("AuthenticationTestSystem") with ImplicitSender with Authenticator with PrivateMethodTester {

    implicit val executionContext = system.dispatcher

    val usernameCorrect = "isubotic"
    val usernameWrong = "usernamewrong"
    val usernameEmpty = ""

    val passUnhashed = "123456"
    // gensalt's log_rounds parameter determines the complexity
    // the work factor is 2**log_rounds, and the default is 10
    val passHashed = "7c4a8d09ca3762af61e59520943dc26494f8941b"
    val passEmpty = ""

    val lang = "en"
    val user_id = Some("http://data.knora.org/users/b83acc5f05")
    val token = None
    val username = Some(usernameCorrect)
    val firstname = Some("Ivan")
    val lastname = Some("Subotic")
    val email = Some("ivan.subotic@unibas.ch")
    val password = Some(passHashed)

    val mockUserProfileV1 = UserProfileV1(UserDataV1(lang, user_id, token, username, firstname, lastname, email, password), Nil, Nil)

    val mockUsersActor = actor(RESPONDER_MANAGER_ACTOR_NAME)(new Act {
        become {
            case UserProfileByUsernameGetRequestV1(submittedUsername) => {
                if (submittedUsername == usernameCorrect) {
                    sender ! Some(mockUserProfileV1)
                } else {
                    sender ! None
                }
            }
        }
    })

    val getUserProfileByUsername = PrivateMethod[Try[UserProfileV1]]('getUserProfileByUsername)

    "During Authentication " when {
        "called, the getUserProfile method " should {
            "succeed with the correct 'username' " ignore {
                this invokePrivate getUserProfileByUsername(usernameCorrect) should be(Success(mockUserProfileV1))
            }

            "fail with the wrong 'username' " ignore {
                this invokePrivate getUserProfileByUsername(usernameWrong) should be(Failure(BadCredentialsException(BAD_CRED_USER_NOT_FOUND)))
            }

            "fail when not providing a username " ignore {
                this invokePrivate getUserProfileByUsername(usernameEmpty) should be(Failure(BadCredentialsException(BAD_CRED_USERNAME_NOT_SUPPLIED)))
            }
        }
    }


}
