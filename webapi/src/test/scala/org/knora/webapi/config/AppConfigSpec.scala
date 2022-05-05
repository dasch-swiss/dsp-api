package org.knora.webapi.config

import zio._
import zio.test._

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

object AppConfigSpec extends ZIOSpec[AppConfig] {

  val layer = ZLayer.make[AppConfig](AppConfig.live)

  def spec = suite("ApplicationConfigSpec")(
    test("successfully provide the application configuration") {
      for {
        appConfig <- ZIO.service[AppConfig]
        // _         <- ZIO.debug(appConfig)
      } yield {
        assertTrue(appConfig.printExtendedConfig == false)
        assertTrue(appConfig.jwtLongevityAsDuration == FiniteDuration(30L, TimeUnit.DAYS))
        assertTrue(appConfig.sipi.timeoutInSeconds == FiniteDuration(120L, TimeUnit.SECONDS))
      }
    }
  )
}
