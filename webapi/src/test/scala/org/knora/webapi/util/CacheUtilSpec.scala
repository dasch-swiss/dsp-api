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

package org.knora.webapi.util

import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.{CoreSpec, SharedTestDataV1}

object CacheUtilSpec {
    val config = ConfigFactory.parseString(
        """
        app {

        }
        """.stripMargin)
}

class CacheUtilSpec extends CoreSpec("CachingTestSystem") with ImplicitSender with Authenticator {

    implicit val executionContext = system.dispatcher

    val cacheName = Authenticator.AUTHENTICATION_INVALIDATION_CACHE_NAME
    val sessionId = System.currentTimeMillis().toString

    "Caching" should {

        "allow to set and get the value " in {
            CacheUtil.put(cacheName, sessionId, SharedTestDataV1.rootUser)
            CacheUtil.get(cacheName, sessionId) should be(Some(SharedTestDataV1.rootUser))
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
