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
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.scalatest.{Matchers, WordSpecLike}

/**
  * This spec is used to test [[org.knora.webapi.store.redis.RedisSerialization]].
  */
class RedisSerializationSpec extends WordSpecLike with Matchers {

    "serialize and deserialize" should {

        "work succeed with the ProjectADM case class" in {
            val serialized: Array[Byte] =  RedisSerialization.serialize(SharedTestDataADM.imagesProject)
            val deserialized: ProjectADM = RedisSerialization.deserialize(serialized).asInstanceOf[ProjectADM]
            deserialized should equal (SharedTestDataADM.imagesProject)
        }


    }
}
