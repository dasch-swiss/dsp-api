/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core.actors

import org.apache.pekko
import org.apache.pekko.actor.Actor
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.Props
import org.apache.pekko.routing.RoundRobinPool
import zio.*

import dsp.errors.UnexpectedMessageException
import org.knora.webapi.core
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.core.RelayedMessage
import org.knora.webapi.util.ActorUtil

final case class MessageRelayActor(private val messageRelay: MessageRelay)(private implicit val runtime: Runtime[Any])
    extends Actor {

  def receive: Receive = {
    case msg: RelayedMessage => ActorUtil.zio2Message(sender(), messageRelay.ask[Any](msg))
    case other =>
      throw UnexpectedMessageException(
        s"RoutingActor received an unexpected message $other of type ${other.getClass.getCanonicalName}",
      )
  }
}

object MessageRelayActor {
  val layer: ZLayer[MessageRelay & ActorSystem, Nothing, ActorRef] = ZLayer
    .fromZIO(
      for {
        system       <- ZIO.service[pekko.actor.ActorSystem]
        messageRelay <- ZIO.service[MessageRelay]
        runtime      <- ZIO.runtime[Any]
        ref = system.actorOf(
                Props(MessageRelayActor(messageRelay)(runtime)).withRouter(new RoundRobinPool(1_000)),
                "applicationManager",
              )
      } yield ref,
    )
}
