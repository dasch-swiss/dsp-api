package org.knora.webapi

import zio._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.matchers.should.Matchers

class TestingSpec02 extends AnyWordSpecLike with Matchers with BeforeAndAfterAll {

  val layer =
    ZLayer.scoped {
      ZIO.acquireRelease {
        Console.printLine("Acquire from TestingSpec02").orDie
      } { _ =>
        Console.printLine("Release from TestingSpec02").orDie
      }
    }

  val runtime = Runtime.unsafeFromLayer(layer)

  override def afterAll() =
    println("after all TestingSpec02")
  // runtime.shutdown()

  "TestingSpec02" should {
    "run this one test" in {
      true should be(true)
    }
  }
}
