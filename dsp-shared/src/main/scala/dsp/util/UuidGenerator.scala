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
 * Test instance of the UuidGenerator which stores the created UUIDs, so that
 * they can be queried / returned in tests
 *
 * @param listOfUuids the list of created UUIDs
 */
final case class UuidGeneratorMock(uuidsForGeneration: Ref[List[UUID]], generatedUuids: Ref[List[UUID]])
    extends UuidGenerator {

  def setKnownUuidsToGenerate(uuids: List[UUID]) = uuidsForGeneration.set(uuids)

  /**
   * Creates a random UUID and stores it in a list, so that it can be queried later
   * which is necessary in tests.
   *
   * @return the created UUID
   */
  override def createRandomUuid: UIO[UUID] =
    for {
      uuids <- uuidsForGeneration.getAndUpdate(_.tail)
      uuid   = uuids.head
      _     <- generatedUuids.getAndUpdate(_.appended(uuid))
    } yield uuid

  /**
   * Returns the list of created UUIDs of this instance
   *
   * @return the list of created UUIDs
   */
  def getCreatedUuids: UIO[List[UUID]] = generatedUuids.get

}
object UuidGeneratorMock {

  /**
   * Returns the list of created UUIDs
   *
   * @return the list of created UUIDs
   */
  def getCreatedUuids =
    ZIO.service[UuidGeneratorMock].flatMap(_.getCreatedUuids)

  val layer: ULayer[UuidGeneratorMock] =
    ZLayer {
      for {
        uuidsForGenerationEmpty <- Ref.make(List.empty[UUID])
        generatedUuidsEmpty     <- Ref.make(List.empty[UUID])
      } yield UuidGeneratorMock(uuidsForGenerationEmpty, generatedUuidsEmpty)
    }

}
