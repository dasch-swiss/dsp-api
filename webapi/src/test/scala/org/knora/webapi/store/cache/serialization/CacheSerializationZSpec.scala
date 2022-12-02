package org.knora.webapi.store.cache.serialization

import zio.test.Assertion.equalTo
import zio.test.TestAspect.ignore
import zio.test.ZIOSpecDefault
import zio.test.assert

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM

/**
 * This spec is used to test [[CacheSerialization]].
 */
object CacheSerializationZSpec extends ZIOSpecDefault {

  val user    = SharedTestDataADM.imagesUser01
  val project = SharedTestDataADM.imagesProject

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
