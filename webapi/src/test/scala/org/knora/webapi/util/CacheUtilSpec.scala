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

import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.v1respondermessages.usermessages._

object CacheUtilSpec {
    val config = ConfigFactory.parseString(
        """
        app {

        }
        """.stripMargin)
}


class CacheUtilSpec extends CoreSpec("CachingTestSystem") with ImplicitSender {

    implicit val executionContext = system.dispatcher

    val mockUserProfileV1 = UserProfileV1(
        UserDataV1(
            user_id = Some("http://data.knora.org/users/b83acc5f05"),
            username = Some("dduck"),
            firstname = Some("Donald"),
            lastname = Some("Duck"),
            email = Some("donald.duck@example.com"),
            hashedpassword = Some("7c4a8d09ca3762af61e59520943dc26494f8941b"),
            token = None,
            lang = "en"
        ), Nil, Nil, Nil, Nil)

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
