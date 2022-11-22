/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.util

import zio._

import java.util.UUID

trait UuidGenerator {
  def createRandomUuid: UIO[UUID]
}

/**
 * Live instance of the UuidGenerator
 */
final case class UuidGeneratorLive() extends UuidGenerator {
  override def createRandomUuid: UIO[UUID] = ZIO.succeed(UUID.randomUUID())
}
object UuidGeneratorLive {
  val layer: ULayer[UuidGeneratorLive] =
    ZLayer.succeed(UuidGeneratorLive())
}

/**
 * Test instance of the UuidGenerator
 *
 * @param uuid the provided UUID value
 */
final case class UuidGeneratorTest(uuid: UUID) extends UuidGenerator {
  override def createRandomUuid: UIO[UUID] = ZIO.succeed(uuid)
}
object UuidGeneratorTest {
  val testUuid = UUID.fromString("89ac5805-6c7f-4a95-aeb2-e85e74aa216d")

  val layer: ULayer[UuidGeneratorTest] =
    ZLayer.succeed(UuidGeneratorTest(testUuid))
}
