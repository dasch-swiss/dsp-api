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

package org.knora.webapi.util

import akka.actor.ActorDSL._
import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.v1respondermessages.usermessages._
import org.knora.webapi.routing.Authenticator

object CacheUtilSpec {
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

class CacheUtilSpec extends CoreSpec("CachingTestSystem") with ImplicitSender with Authenticator {

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

    val mockUsersActor = actor("responderManager")(new Act {
        become {
            case UserProfileByUsernameGetRequestV1(username) => {
                if (username == usernameCorrect) {
                    sender ! Some(mockUserProfileV1)
                } else {
                    sender ! None
                }
            }
        }
    })

    val cacheName = "authenticationCache"
    val sessionId = System.currentTimeMillis().toString

    "Caching " should {
        "allow to set and get the value " in {
            CacheUtil.put(cacheName, sessionId, mockUserProfileV1)
            CacheUtil.get(cacheName, sessionId) should be(Some(mockUserProfileV1))
        }
        "return none if key is not found " in {
            CacheUtil.get(cacheName, 213.toString) should be(None)
        }
        "allow to delete a set value " in {
            CacheUtil.remove(cacheName, sessionId)
            CacheUtil.get(cacheName, sessionId) should be(None)
        }
    }


}
