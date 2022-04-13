package org.knora.webapi.config

import zio.test.Assertion._
import zio.test.TestAspect.ignore
import zio.test.TestAspect.timeout
import zio.test._
import zio._

object ApplicationConfigSpec extends ZIOSpec[AppConfig] {

  val layer = ZLayer.make[AppConfig](AppConfig.live)

  def spec = suite("ApplicationConfigSpec")(
    test("successfully provide the application configuration") {
      for {
        appConfig <- ZIO.service[AppConfig]
      } yield assertTrue(appConfig.printExtendedConfig == false)
    }
  )
}
