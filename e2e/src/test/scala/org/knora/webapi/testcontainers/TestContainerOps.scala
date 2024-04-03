/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testcontainers

import org.testcontainers.lifecycle.Startable
import zio._

object ZioTestContainers {
  def toZio[T <: Startable](self: T): URIO[Scope, T] = { // using `logError.ignore` because there's no point to try to recover if starting/stopping the container fails
    val acquire = ZIO.attemptBlocking(self.start()).logError.ignore.as(self)
    val release = (container: T) => ZIO.attemptBlocking(container.stop()).logError.ignore
    ZIO.acquireRelease(acquire)(release)
  }
  def layer[T <: Startable: Tag](self: T) = ZLayer.scoped(toZio(self))
}
