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

package org.knora.webapi.store.cacheservice

import com.typesafe.config.ConfigFactory
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM

object CacheSerializationSpec {
  val config = ConfigFactory.parseString("""
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * This spec is used to test [[org.knora.webapi.store.cacheservice.CacheSerialization]].
  */
class CacheSerializationSpec extends CoreSpec(CacheSerializationSpec.config) {

  "serialize and deserialize" should {

    "work with the UserADM case class" in {
      val user = SharedTestDataADM.imagesUser01
      for {
        serialized <- CacheSerialization.serialize(user)
        deserialized: Option[UserADM] <- CacheSerialization.deserialize[UserADM](serialized)
      } yield deserialized shouldBe Some(user)
    }

    "work with the ProjectADM case class" in {
      val project = SharedTestDataADM.imagesProject
      for {
        serialized <- CacheSerialization.serialize(project)
        deserialized: Option[ProjectADM] <- CacheSerialization.deserialize[ProjectADM](serialized)
      } yield deserialized shouldBe Some(project)
    }

  }
}
