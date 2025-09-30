/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testcontainers

import org.testcontainers.containers.GenericContainer
import zio.*

object ZioTestContainers {

  def toZio[T <: GenericContainer[_]](self: T): URIO[Scope, T] = {
    val acquire = ZIO
      .attemptBlocking(self.start())
      .orDie
      .as(self)
      .tap(it => ZIO.logInfo(s"Started test container: ${it.getDockerImageName} on port ${it.getFirstMappedPort}"))
    val release = (container: T) => ZIO.succeed(container.stop())
    ZIO.acquireRelease(acquire)(release)
  }

  def toLayer[T <: GenericContainer[_]: Tag](self: T): ULayer[T] = ZLayer.scoped(toZio(self))
}

object TestContainerOps {
  extension [T <: GenericContainer[_]](self: T) {
    def toZio: URIO[Scope, T]                   = ZioTestContainers.toZio(self)
    def toLayer(implicit ev: Tag[T]): ULayer[T] = ZioTestContainers.toLayer(self)
  }
}
