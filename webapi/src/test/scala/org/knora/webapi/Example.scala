package org.knora.webapi

import zio._

object Example extends scala.App {

  val layer =
    ZLayer.scoped {
      ZIO.acquireRelease {
        Console.printLine("Acquire").orDie
      } { _ =>
        Console.printLine("Release").orDie
      }
    }

  val runtime = Runtime.unsafeFromLayer(layer)

  println("before shutdown")
  runtime.shutdown()
  println("after shutdown")
}
