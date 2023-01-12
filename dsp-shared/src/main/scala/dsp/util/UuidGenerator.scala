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
  override def createRandomUuid: UIO[UUID] = ZIO.succeed(


    UUID.randomUUID())
}
object UuidGeneratorLive {
  val layer: ULayer[UuidGeneratorLive] =
    ZLayer.succeed(UuidGeneratorLive())
}
