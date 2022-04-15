package org.knora.webapi.config

import zio.test.Assertion._
import zio.test.TestAspect.ignore
import zio.test.TestAspect.timeout
import zio.test._
import zio._
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import org.knora.webapi.testcontainers.SipiTestContainer

object AppConfigForTestContainersSpec extends ZIOSpecDefault {

  def spec = suite("AppConfigForTestContainersSpec")(
    test("successfully provide the adapted application configuration for using with test containers") {
      for {
        appConfig     <- ZIO.service[AppConfig]
        sipiContainer <- ZIO.service[SipiTestContainer]
        sipiPort      <- ZIO.succeed(sipiContainer.container.getFirstMappedPort)
        _             <- ZIO.debug(appConfig)
        _             <- ZIO.debug(sipiContainer.container.getFirstMappedPort)
      } yield {
        assertTrue(appConfig.sipi.internalPort == sipiPort)
      }
    }
  ).provide(
    AppConfigForTestContainers.testcontainers,
    SipiTestContainer.layer
  )
}
