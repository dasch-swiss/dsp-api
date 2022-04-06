/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cacheservice.serialization

import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM

import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect.{ignore, timeout}
import org.knora.webapi.store.cacheservice.api.CacheService

/**
 * This spec is used to test [[CacheSerialization]].
 */
object CacheSerializationSpec extends ZIOSpecDefault {

  private val user    = SharedTestDataADM.imagesUser01
  private val project = SharedTestDataADM.imagesProject

  def spec = suite("CacheSerializationSpec")(
    test("successfully serialize and deserialize a user") {
      for {
        serialized   <- CacheSerialization.serialize(user)
        deserialized <- CacheSerialization.deserialize[UserADM](serialized)
      } yield assert(deserialized)(equalTo(Some(user)))
    } @@ ignore +
      test("successfully serialize and deserialize a project") {
        for {
          serialized   <- CacheSerialization.serialize(project)
          deserialized <- CacheSerialization.deserialize[ProjectADM](serialized)
        } yield assert(deserialized)(equalTo(Some(project)))
      }
  )
}
