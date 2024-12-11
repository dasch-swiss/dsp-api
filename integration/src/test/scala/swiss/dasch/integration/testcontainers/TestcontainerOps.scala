/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.integration.testcontainers

import org.testcontainers.lifecycle.Startable
import zio.{Scope, Tag, ULayer, URIO, ZIO, ZLayer}

object ZioTestContainers {

  def toZio[T <: Startable](self: T): URIO[Scope, T] = {
    val acquire = ZIO.attemptBlocking(self.start()).orDie.as(self)
    val release = (container: T) => ZIO.succeed(container.stop())
    ZIO.acquireRelease(acquire)(release)
  }

  def toLayer[T <: Startable: Tag](self: T): ULayer[T] = ZLayer.scoped(toZio(self))
}

object TestContainerOps {
  extension [T <: Startable](self: T) {
    def toZio: URIO[Scope, T]                   = ZioTestContainers.toZio(self)
    def toLayer(implicit ev: Tag[T]): ULayer[T] = ZioTestContainers.toLayer(self)
  }
}
