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
import org.knora.webapi.{CoreSpec, SharedTestData}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1

object CacheUtilSpec {
    val config = ConfigFactory.parseString(
        """
          # akka.loglevel = "DEBUG"
          # akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}


class CacheUtilSpec extends CoreSpec("CachingTestSystem") with ImplicitSender {

    implicit val executionContext = system.dispatcher

    val mockUserProfileV1 = SharedTestData.rootUserProfileV1
    val username = mockUserProfileV1.userData.username.get
    val sId = mockUserProfileV1.getDigest
    val cacheName = "authenticationCache"

    "Caching " should {
        "allow to set and get the value " in {
            /* use username as key */
            CacheUtil.put[UserProfileV1](cacheName, username, mockUserProfileV1)
            CacheUtil.get[UserProfileV1](cacheName, username) should be(Some(mockUserProfileV1))

            /* use digest as key */
            CacheUtil.put[UserProfileV1](cacheName, sId, mockUserProfileV1)
            CacheUtil.get[UserProfileV1](cacheName, sId) should be(Some(mockUserProfileV1))
        }
        "return none if key is not found " in {
            CacheUtil.get[UserProfileV1](cacheName, "user01") should be(None)
        }
        "allow to delete a set value " in {
            /* username case */
            CacheUtil.remove(cacheName, username)
            CacheUtil.get[UserProfileV1](cacheName, username) should be(None)

            /* digest case */
            CacheUtil.remove(cacheName, sId)
            CacheUtil.get[UserProfileV1](cacheName, sId) should be(None)
        }
    }


}
