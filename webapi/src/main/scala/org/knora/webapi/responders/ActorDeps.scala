/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.util.Timeout
import zio.ZIO
import zio.ZLayer

import scala.concurrent.ExecutionContext

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.AppRouter
import org.knora.webapi.settings.KnoraDispatchers

/**
 * Class encapsulating all Akka dependencies necessary to interact with the [[org.knora.webapi.core.actors.RoutingActor]] aka. "appActor"
 *
 * When using this class in a service depending on the routing actor this will provide the necessary implicit dependencies for using the ask pattern
 * whilst making the dependency explicit for ZIO layers.
 *
 * @example Usage in client code:
 * {{{
 * final case class YourService(actorDeps: ActorDeps){
 *   private implicit val ec: ExecutionContext = actorDeps.executionContext
 *   private implicit val timeout: Timeout     = actorDeps.timeout
 *
 *   private val appActor: ActorRef = actorDeps.appActor
 *
 *   def someMethod = appActor.ask(SomeMessage())...
 * }
 * }}}
 *
 * @param system the akka.core.ActorSystem - used to extract the [[ExecutionContext]] from
 * @param appActor a reference to the [[org.knora.webapi.core.actors.RoutingActor]]
 * @param timeout the timeout needed for the ask pattern
 */
final case class ActorDeps(system: ActorSystem, appActor: ActorRef, timeout: Timeout) {
  val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)
}

object ActorDeps {
  val layer: ZLayer[AppConfig with AppRouter, Nothing, ActorDeps] = ZLayer.fromZIO {
    for {
      router  <- ZIO.service[AppRouter]
      system   = router.system
      appActor = router.ref
      timeout <- ZIO.service[AppConfig].map(_.defaultTimeoutAsDuration)
    } yield ActorDeps(system, appActor, timeout)
  }
}
