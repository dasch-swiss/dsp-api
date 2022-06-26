package org.knora.webapi

import zio._

object TestingExample extends scala.App {

  val layer =
    ZLayer.scoped {
      ZIO.acquireRelease {
        Console.printLine("Acquire").orDie
      } { _ =>
        Console.printLine("Release").orDie
      }
    }

  // The ZIO runtime used to run functional effects
  lazy val runtime =
    Unsafe.unsafe { implicit u =>
      Runtime.unsafe.fromLayer(layer)
    }

  println("before shutdown")

  Unsafe.unsafe { implicit u =>
    runtime.unsafe.shutdown()
  }

  println("after shutdown")
}
