/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.store.redis

import org.knora.webapi._
import org.knora.webapi.messages.store.redismessages.{RedisGetProjectADM, RedisPutProjectADM}
import org.scalatest.{Matchers, WordSpecLike}

import scala.util.Success

/**
  * This spec is used to test [[org.knora.webapi.store.redis.RedisSerialization]].
  */
class RedisManagerSpec extends WordSpecLike with Matchers {

    "" when {
        "The RedisManager" should {
            "successfully store a project" in {
                val rm = new RedisManager("localhost", 6379)
                rm.receive(RedisPutProjectADM("test1", SharedTestDataADM.imagesProject)) should be (Success(true))
            }

            "successfully retrieve a project" in {
                val rm = new RedisManager("localhost", 6379)
                rm.receive(RedisGetProjectADM("test1")) should be (Success(Some(SharedTestDataADM.imagesProject)))
            }
        }
    }
}
