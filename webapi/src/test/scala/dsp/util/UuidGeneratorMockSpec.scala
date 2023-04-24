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
      test(
        "create several random UUIDs, store them as expected UUIDs and get them back IN THE SAME ORDER when calling the createRandomUuid method"
      ) {
        val expected1 = UUID.randomUUID()
        val expected2 = UUID.randomUUID()
        val expected3 = UUID.randomUUID()
        for {
          uuidGenerator <- ZIO.service[UuidGeneratorMock]
          _             <- uuidGenerator.setKnownUuidsToGenerate(List(expected1, expected2, expected3))
          uuid1         <- uuidGenerator.createRandomUuid
          uuid2         <- uuidGenerator.createRandomUuid
          uuid3         <- uuidGenerator.createRandomUuid
        } yield assertTrue(
          uuid1 == expected1 &&
            uuid2 == expected2 &&
            uuid3 == expected3
        )
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
