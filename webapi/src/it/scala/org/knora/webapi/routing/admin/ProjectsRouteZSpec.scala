package org.knora.webapi.routing.admin

import zio.test.ZIOSpecDefault
import zio.test._
import zio._

import org.knora.webapi.config.AppConfig

object ProjectsRouteZSpec extends ZIOSpecDefault {
  val spec = suite("ProjectsRouteZSpec")(test("foo") {
    for {
      route <- ZIO.service[ProjectsRouteZ]
    } yield assertTrue(false)
  }).provide(ProjectsRouteZ.layer, AppConfig.test, ProjectServiceTest.layer)
}
