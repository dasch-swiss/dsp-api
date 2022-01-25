/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
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
