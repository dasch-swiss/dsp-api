package org.knora.webapi.core

import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.responders.ActorToZioBridge
import zio._

/**
 * Messages with the [[RelayedMessage]] trait are sent to the [[MessageRelay]] from the [[org.knora.webapi.core.actors.RoutingActor]].
 * This [[MessageHandler]] routes messages which are not an instance of [[RelayedMessage]] to the [[org.knora.webapi.core.actors.RoutingActor]].
 *
 * This way a bi-directional communication is possible between "ziofied" and "non-ziofied" Responders.
 *
 * @param bridge the bridge to the actor/akka world.
 */
case class AppRouterRelayingMessageHandler(bridge: ActorToZioBridge) extends MessageHandler {
  override def handle(message: ResponderRequest): Task[Any]         = bridge.askAppActor(message)
  override def isResponsibleFor(message: ResponderRequest): Boolean = !message.isInstanceOf[RelayedMessage]
}

object AppRouterRelayingMessageHandler {
  val layer: URLayer[MessageRelay with ActorToZioBridge, AppRouterRelayingMessageHandler] = ZLayer.fromZIO {
    for {
      bridge  <- ZIO.service[ActorToZioBridge]
      relay   <- ZIO.service[MessageRelay]
      handler <- relay.subscribe(AppRouterRelayingMessageHandler(bridge))
    } yield handler
  }
}
