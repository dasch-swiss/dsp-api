/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.util

import zio._

import java.util.UUID

/**
 * Handles UUID creation
 */
trait UuidGenerator {
  def createRandomUuid: UIO[UUID]
}

/**
 * Live instance of the UuidGenerator
 */
final case class UuidGeneratorLive() extends UuidGenerator {

  /**
   * Creates a random UUID
   *
   * @return a random UUID
   */
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
final case class UuidGeneratorTest(listOfUuids: Ref[List[UUID]]) extends UuidGenerator {

  /**
   * For test reasons, the method returns always the same UUID
   *
   * @return a predictable UUID ("89ac5805-6c7f-4a95-aeb2-e85e74aa216d")
   */
  override def createRandomUuid: UIO[UUID] =
    for {
      uuid <- ZIO.succeed(UUID.randomUUID())
      _    <- listOfUuids.update(list => uuid :: list)
    } yield uuid

  def getCreatedUuids: UIO[List[UUID]] =
    listOfUuids.get

}
object UuidGeneratorTest {
  val listOfUuids = Ref.make(List.empty[UUID])

  def getCreatedUuids =
    ZIO.service[UuidGeneratorTest].flatMap(_.getCreatedUuids)

  val layer: ULayer[UuidGeneratorTest] =
    ZLayer {
      for {
        listOfUuids <- Ref.make(List.empty[UUID])
      } yield UuidGeneratorTest(listOfUuids)
    }

}
