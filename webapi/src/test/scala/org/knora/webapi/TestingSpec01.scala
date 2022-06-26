package org.knora.webapi

import zio._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.matchers.should.Matchers

class TestingSpec01 extends AnyWordSpecLike with Matchers with BeforeAndAfterAll {

  val layer =
    ZLayer.scoped {
      ZIO.acquireRelease {
        Console.printLine("Acquire from TestingSpec01").orDie
      } { _ =>
        Console.printLine("Release from TestingSpec01").orDie
      }
    }

  lazy val runtime =
    Unsafe.unsafe { implicit u =>
      Runtime.unsafe.fromLayer(layer)
    }

  override def afterAll() =
    println("after all TestingSpec01")
  // runtime.shutdown()

  "TestingSpec01" should {
    "run this one test" in {
      true should be(true)
    }
  }
}
