/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.Terminated
import zio.*

import scala.concurrent.ExecutionContext

object PekkoActorSystem {

  private def acquire(executionContext: ExecutionContext): UIO[ActorSystem] =
    ZIO
      .attempt(ActorSystem("webapi", None, None, Some(executionContext)))
      .retry(Schedule.exponential(1.second) && Schedule.recurs(3))
      .tapError(error => ZIO.logError(s"Failed to initialize Actor System: ${error.getMessage}"))
      .orDie <*
      ZIO.logInfo(">>> Acquire Actor System <<<")

  private def release(system: ActorSystem): UIO[Terminated] =
    ZIO.fromFuture(_ => system.terminate()).orDie <* ZIO.logInfo(">>> Release Actor System <<<")

  val layer: ULayer[ActorSystem] = ZLayer.scoped(
    for {
      context <- ZIO.executor.map(_.asExecutionContext)
      system  <- ZIO.acquireRelease(acquire(context))(release)
    } yield system,
  )
}
