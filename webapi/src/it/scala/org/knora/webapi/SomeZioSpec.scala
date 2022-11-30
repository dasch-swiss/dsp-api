package org.knora.webapi

import zio.ZIO
import zio.test._

object SomeZioSpec extends ZIOSpecDefault {

  override def spec: Spec[Any, Nothing] =
    suite("foo")(test("bar") {
      for {
        hi <- ZIO.succeed("hi")
      } yield assertTrue(hi == "bye")
    })

}
