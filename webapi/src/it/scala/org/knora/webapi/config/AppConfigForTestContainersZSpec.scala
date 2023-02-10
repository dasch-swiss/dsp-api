package org.knora.webapi.config

import zio._
import zio.test._

import org.knora.webapi.testcontainers.SipiTestContainer

object AppConfigForTestContainersZSpec extends ZIOSpecDefault {

  def spec = suite("AppConfigForTestContainersSpec")(
    test("successfully provide the adapted application configuration for using with test containers") {
      for {
        appConfig     <- ZIO.service[AppConfig]
        sipiContainer <- ZIO.service[SipiTestContainer]
        sipiPort      <- ZIO.succeed(sipiContainer.container.getFirstMappedPort)
      } yield {
        assertTrue(appConfig.sipi.internalPort == sipiPort)
      }
    }
  ).provide(
    AppConfigForTestContainers.testcontainers,
    SipiTestContainer.layer
  )
}
