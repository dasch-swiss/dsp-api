/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cache.serialization

import zio.test.TestAspect.ignore
import zio.test.*

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.admin.domain.model.User

/**
 * This spec is used to test [[CacheSerialization]].
 */
object CacheSerializationZSpec extends ZIOSpecDefault {

  private val user    = SharedTestDataADM.imagesUser01
  private val project = SharedTestDataADM.imagesProject

  def spec: Spec[Any, Throwable] = suite("CacheSerializationSpec")(
    test("successfully serialize and deserialize a user") {
      for {
        serialized   <- CacheSerialization.serialize(user)
        deserialized <- CacheSerialization.deserialize[User](serialized)
      } yield assertTrue(deserialized.contains(user))
    } @@ ignore +
      test("successfully serialize and deserialize a project") {
        for {
          serialized   <- CacheSerialization.serialize(project)
          deserialized <- CacheSerialization.deserialize[ProjectADM](serialized)
        } yield assertTrue(deserialized.contains(project))
      }
  )
}
