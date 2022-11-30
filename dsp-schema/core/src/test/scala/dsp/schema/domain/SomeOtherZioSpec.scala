package dsp.schema.domain

import zio.ZIO
import zio.test._

object SomeOtherZioSpec extends ZIOSpecDefault {

  override def spec: Spec[Any, Nothing] =
    suite("foo schema core")(test("bar") {
      for {
        hi <- ZIO.succeed("hi")
      } yield assertTrue(hi == "hi")
    })

}
