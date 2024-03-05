/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.config

import zio.ZIO
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import java.time.Duration

import org.knora.webapi.slice.admin.domain.model.PasswordStrength

object AppConfigSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Nothing] = suite("ApplicationConfigSpec")(
    test("successfully provide the application configuration") {
      for {
        appConfig       <- ZIO.service[AppConfig]
        jwtConfig       <- ZIO.service[JwtConfig]
        dspIngestConfig <- ZIO.service[DspIngestConfig]
      } yield {
        assertTrue(
          !appConfig.printExtendedConfig,
          appConfig.defaultTimeout == Duration.ofMinutes(120),
          appConfig.sipi.timeout == Duration.ofSeconds(120),
          appConfig.triplestore.queryTimeout == Duration.ofSeconds(20),
          appConfig.triplestore.gravsearchTimeout == Duration.ofSeconds(120),
          appConfig.bcryptPasswordStrength == PasswordStrength.unsafeFrom(12).value,
          appConfig.instrumentationServerConfig.interval == Duration.ofSeconds(5),
          dspIngestConfig.audience == "http://localhost:3340",
          dspIngestConfig.baseUrl == "http://localhost:3340",
          jwtConfig.expiration == java.time.Duration.ofDays(30),
          jwtConfig.issuer.contains("0.0.0.0:3333"),
          jwtConfig.issuerAsString() == "0.0.0.0:3333",
        )
      }
    }.provideLayer(AppConfig.layer),
  )
}
