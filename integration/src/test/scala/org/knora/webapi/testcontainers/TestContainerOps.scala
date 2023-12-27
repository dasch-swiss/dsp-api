package org.knora.webapi.testcontainers

import org.testcontainers.lifecycle.Startable
import zio.Scope
import zio.Tag
import zio.ULayer
import zio.URIO
import zio.ZIO
import zio.ZLayer

object ZioTestContainers {

  def toZio[T <: Startable](self: T): URIO[Scope, T] = { // using `logError.ignore` because there's no point to try to recover if starting/stopping the container fails
    val acquire = ZIO.attemptBlocking(self.start()).logError.ignore.as(self)
    val release = (container: T) => ZIO.attemptBlocking(container.stop()).logError.ignore
    ZIO.acquireRelease(acquire)(release)
  }

  def toLayer[T <: Startable: Tag](container: T): ULayer[T] =
    ZLayer.scoped(toZio(container))
}

object TestContainerOps {
  implicit final class StartableOps[T <: Startable](private val self: T) extends AnyVal {
    def toZio: URIO[Scope, T]                   = ZioTestContainers.toZio(self)
    def toLayer(implicit ev: Tag[T]): ULayer[T] = ZioTestContainers.toLayer(self)
  }
}
