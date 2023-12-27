/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.config

import org.knora.webapi.testcontainers.{FusekiTestContainer, SharedVolumes, SipiTestContainer}
import zio._
import zio.test._

object AppConfigForTestContainersZSpec extends ZIOSpecDefault {

  def spec: Spec[Scope, Nothing] = suite("AppConfigForTestContainersSpec")(
    test("successfully provide the adapted application configuration for using with test containers") {
      for {
        appConfig     <- ZIO.service[AppConfig]
        sipiContainer <- ZIO.service[SipiTestContainer]
      } yield {
        assertTrue(appConfig.sipi.internalPort == sipiContainer.getFirstMappedPort)
      }
    }
  ).provideSome[Scope](
    AppConfigForTestContainers.testcontainers,
    FusekiTestContainer.layer,
    SharedVolumes.Images.layer,
    SipiTestContainer.layer
  )
}
