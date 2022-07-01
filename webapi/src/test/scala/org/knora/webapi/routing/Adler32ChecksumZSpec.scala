/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import zio._
import zio.test.Assertion._
import zio.test._
import org.knora.webapi.config.AppConfig

/**
 * This spec is used to test [[org.knora.webapi.store.cache.impl.CacheServiceInMemImpl]].
 */
object Adler32ChecksumZSpec extends ZIOSpecDefault {

  /**
   * Defines a layer which encompases all dependencies that are needed for
   * running the tests.
   */
  val testLayers = ZLayer.make[AppConfig](AppConfig.live)

  def spec = tests.provideLayerShared(testLayers) @@ TestAspect.sequential

  val tests = suite("CacheInMemImplZSpec")(
    test("successfully calculate first checksumg") {
      for {
        config <- ZIO.service[AppConfig]
        result <- ZIO.succeed(Adler32Checksum.calc("Wikipedia"))
      } yield assertTrue(result == 300286872)
    } +
      test("successfully calculate third")(
        for {
          config <- ZIO.service[AppConfig]
          result <- ZIO.succeed(Adler32Checksum.calc(config.knoraApi.externalKnoraApiHostPort))
        } yield assertTrue(result == 247857745)
      )
  )
}
