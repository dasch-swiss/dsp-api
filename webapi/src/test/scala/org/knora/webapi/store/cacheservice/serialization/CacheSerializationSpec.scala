/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 *  This file is part of the DaSCH Service Platform.
 *
 *  The DaSCH Service Platform  is free software: you can redistribute it
 *  and/or modify it under the terms of the GNU Affero General Public
 *  License as published by the Free Software Foundation, either version 3
 *  of the License, or (at your option) any later version.
 *
 *  The DaSCH Service Platform is distributed in the hope that it will be
 *  useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 *  of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with the DaSCH Service Platform.  If not, see
 *  <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.store.cacheservice.serialization

import com.typesafe.config.ConfigFactory
import org.knora.webapi.UnitSpec
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
 * This spec is used to test [[CacheSerialization]].
 */
class CacheSerializationSpec extends UnitSpec(CacheSerializationSpec.config) {

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

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
