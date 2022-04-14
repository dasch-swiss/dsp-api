package org.knora.webapi.config

import zio.test.Assertion._
import zio.test.TestAspect.ignore
import zio.test.TestAspect.timeout
import zio.test._
import zio._
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

object ApplicationConfigSpec extends ZIOSpec[AppConfig] {

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
