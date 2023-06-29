/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.config

import zio.ZIO
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

import dsp.valueobjects.User

object AppConfigSpec extends ZIOSpecDefault {

  def spec = suite("ApplicationConfigSpec")(
    test("successfully provide the application configuration") {
      for {
        appConfig       <- ZIO.service[AppConfig]
        jwtConfig       <- ZIO.service[JwtConfig]
        dspIngestConfig <- ZIO.service[DspIngestConfig]
      } yield {
        assertTrue(
          !appConfig.printExtendedConfig,
          appConfig.sipi.timeoutInSeconds == FiniteDuration(120L, TimeUnit.SECONDS),
          appConfig.bcryptPasswordStrength == User.PasswordStrength(12),
          appConfig.instrumentationServerConfig.interval == java.time.Duration.ofSeconds(5),
          dspIngestConfig.audience == "http://localhost:3340",
          dspIngestConfig.baseUrl == "http://localhost:3340",
          jwtConfig.expiration == java.time.Duration.ofDays(30),
          jwtConfig.issuer.contains("0.0.0.0:3333")
        )
      }
    }.provideLayer(AppConfig.layer)
  )
}
