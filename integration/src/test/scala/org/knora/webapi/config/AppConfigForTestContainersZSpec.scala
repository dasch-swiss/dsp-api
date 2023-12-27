/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.config

import zio._
import zio.test._

import org.knora.webapi.testcontainers.FusekiTestContainer
import org.knora.webapi.testcontainers.SipiTestContainer

object AppConfigForTestContainersZSpec extends ZIOSpecDefault {

  def spec = suite("AppConfigForTestContainersSpec")(
    test("successfully provide the adapted application configuration for using with test containers") {
      for {
        appConfig     <- ZIO.service[AppConfig]
        sipiContainer <- ZIO.service[SipiTestContainer]
      } yield {
        assertTrue(appConfig.sipi.internalPort == sipiContainer.getFirstMappedPort)
      }
    }
  ).provide(
    AppConfigForTestContainers.testcontainers,
    SipiTestContainer.layer,
    FusekiTestContainer.layer
  )
}
