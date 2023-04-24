/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi
package dsp.util

import zio._

import java.util.UUID

/**
 * Test instance of the UuidGenerator which stores the created UUIDs, so that
 * they can be queried / returned in tests
 *
 * @param uuidsForGeneration
 * @param generatedUuids     the list of created UUIDs
 */
final case class UuidGeneratorMock(uuidsForGeneration: Ref[List[UUID]], generatedUuids: Ref[List[UUID]])
    extends UuidGenerator {

  /**
   * Sets the given UUIDs that can be queried later
   *
   * @param uuids A list of UUIDs that should be set as known UUIDs
   */
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
  def getCreatedUuids = ZIO.service[UuidGeneratorMock].flatMap(_.getCreatedUuids)

  val layer: ULayer[UuidGeneratorMock] = {
    val listOfRandomUuids = List.fill(20)(UUID.randomUUID())
    ZLayer {
      for {
        uuidsForGeneration <- Ref.make(listOfRandomUuids) // initialize the list with 20 random UUIDs
        generatedUuids     <- Ref.make(List.empty[UUID])
      } yield UuidGeneratorMock(uuidsForGeneration, generatedUuids)
    }
  }

}
