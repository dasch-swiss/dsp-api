package dsp.util

import zio.ZIO
import zio.test._

import java.util.UUID

/**
 * This spec is used to test the [[UuidGeneratorMockSpec]].
 */
object UuidGeneratorMockSpec extends ZIOSpecDefault {
  override def spec =
    suite("UuidGeneratorMockSpec - UUID generation")(
      test("create a random UUID, store it as expected UUID and get it back when calling the createRandomUuid method") {
        val expected = UUID.randomUUID()
        for {
          uuidGenerator <- ZIO.service[UuidGeneratorMock]
          _             <- uuidGenerator.setKnownUuidsToGenerate(List(expected))
          uuid          <- uuidGenerator.createRandomUuid
        } yield assertTrue(uuid == expected)
      },
      test("create a random UUID, store it as expected UUID and retrieve it from the list of created UUIDs") {
        val expected = UUID.randomUUID()
        for {
          uuidGenerator <- ZIO.service[UuidGeneratorMock]
          _             <- uuidGenerator.setKnownUuidsToGenerate(List(expected))
          _             <- uuidGenerator.createRandomUuid
          uuid          <- uuidGenerator.getCreatedUuids.map(_.head)
        } yield assertTrue(uuid == expected)
      }
    ).provide(UuidGeneratorMock.layer)
}
