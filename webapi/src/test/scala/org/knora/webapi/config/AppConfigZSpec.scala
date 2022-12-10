/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.config

import zio.ZIO
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

import dsp.valueobjects.User

object AppConfigZSpec extends ZIOSpecDefault {

  def spec = suite("ApplicationConfigSpec")(
    test("successfully provide the application configuration") {
      for {
        appConfig <- ZIO.service[AppConfig]
      } yield {
        assertTrue(appConfig.printExtendedConfig == false) &&
        assertTrue(appConfig.jwtLongevityAsDuration == FiniteDuration(30L, TimeUnit.DAYS)) &&
        assertTrue(appConfig.sipi.timeoutInSeconds == FiniteDuration(120L, TimeUnit.SECONDS)) &&
        assertTrue(appConfig.bcryptPasswordStrength == User.PasswordStrength(12)) &&
        assertTrue(
          appConfig.prometheusServerConfig.interval == java.time.Duration.ofSeconds(5)
        )
      }
    }.provideLayer(AppConfig.live)
  )
}
