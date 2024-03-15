/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.apache.pekko.actor
import zio.*

import scala.concurrent.ExecutionContext

object ActorSystem {

  private def acquire(executionContext: ExecutionContext): URIO[Any, actor.ActorSystem] =
    ZIO
      .attempt(actor.ActorSystem("webapi", None, None, Some(executionContext)))
      .zipLeft(ZIO.logInfo(">>> Acquire Actor System <<<"))
      .orDie

  private def release(system: actor.ActorSystem): URIO[Any, actor.Terminated] =
    ZIO.fromFuture(_ => system.terminate()).zipLeft(ZIO.logInfo(">>> Release Actor System <<<")).orDie

  val layer: ZLayer[Any, Nothing, actor.ActorSystem] =
    ZLayer.scoped(
      for {
        context <- ZIO.executor.map(_.asExecutionContext)
        system  <- ZIO.acquireRelease(acquire(context))(release)
      } yield system,
    )
}
