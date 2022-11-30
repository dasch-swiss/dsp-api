package org.knora.webapi.config

import dsp.valueobjects.User
import zio.ZIO
import zio.test.{ZIOSpecDefault, assertTrue}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

object AppConfigZSpec extends ZIOSpecDefault {

  def spec = suite("ApplicationConfigSpec")(
    test("successfully provide the application configuration") {
      for {
        appConfig <- ZIO.service[AppConfig]
      } yield {
        assertTrue(appConfig.printExtendedConfig == false) &&
        assertTrue(appConfig.jwtLongevityAsDuration == FiniteDuration(30L, TimeUnit.DAYS)) &&
        assertTrue(appConfig.sipi.timeoutInSeconds == FiniteDuration(120L, TimeUnit.SECONDS)) &&
        assertTrue(appConfig.bcryptPasswordStrength == User.PasswordStrength(12))
      }
    }.provideLayer(AppConfig.live)
  )
}
