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
final case class UuidGeneratorTest(listOfUuids: Ref[List[UUID]]) extends UuidGenerator {

  /**
   * Creates a random UUID and stores it in a list, so that it can be queried later
   * which is necessary in tests.
   *
   * @return the created UUID
   */
  override def createRandomUuid: UIO[UUID] =
    for {
      uuid <- ZIO.succeed(UUID.randomUUID())
      _    <- listOfUuids.update(list => uuid :: list)
    } yield uuid

  /**
   * Returns the list of created UUIDs of this instance
   *
   * @return the list of created UUIDs
   */
  def getCreatedUuids: UIO[List[UUID]] =
    listOfUuids.get

}
object UuidGeneratorTest {
  val listOfUuids = Ref.make(List.empty[UUID])

  /**
   * Returns the list of created UUIDs
   *
   * @return the list of created UUIDs
   */
  def getCreatedUuids =
    ZIO.service[UuidGeneratorTest].flatMap(_.getCreatedUuids)

  val layer: ULayer[UuidGeneratorTest] =
    ZLayer {
      for {
        listOfUuids <- Ref.make(List.empty[UUID])
      } yield UuidGeneratorTest(listOfUuids)
    }

}
