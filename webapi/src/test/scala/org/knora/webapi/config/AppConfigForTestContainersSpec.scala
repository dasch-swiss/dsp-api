package org.knora.webapi.config

import org.knora.webapi.testcontainers.SipiTestContainer
import zio._
import zio.test._
import org.knora.webapi.testcontainers.FusekiTestContainer

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
    SipiTestContainer.layer,
    FusekiTestContainer.layer
  )
}
