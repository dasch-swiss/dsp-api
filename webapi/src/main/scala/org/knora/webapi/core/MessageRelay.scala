/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio._

import org.knora.webapi.messages.ResponderRequest

/**
 * Marker trait which must mark messages which have a corresponding [[MessageHandler]] implementation.
 *
 * The [[org.knora.webapi.core.actors.RoutingActor]] routes messages with highest precedence to the [[MessageRelay]].
 */
trait RelayedMessage extends ResponderRequest

/**
 * Central component which is capable of relaying message to subscribed [[MessageHandler]]s.
 * Should replace the ask pattern with the [[akka.actor.ActorRef]] to our [[org.knora.webapi.core.actors.RoutingActor]]
 * in [[org.knora.webapi.responders.Responder]]s which are migrated to [[ZIO]].
 *
 * This is only meant to be replacing the [[org.knora.webapi.core.actors.RoutingActor]] short term.
 * In the long run we will prefer to invoke methods on the respective responders/services/repos directly.
 *
 * However, with the current architecture there are some circular dependencies between the responders and
 * the router such that breaking these up is a task for after the ZIO migration.
 */
trait MessageRelay {

  /**
   * The ask method is meant to be a replacement for all current occurrences of the `appRouter.ask()` pattern during
   * the migration of the [[org.knora.webapi.responders.Responder]]s to a implementation based on [[ZIO]].
   *
   * Will die with an [[IllegalStateException]] if no [[MessageHandler]] for the type of message was subscribed.
   *
   * @param message The message.
   * @return The result as type `R`, due do the untyped nature of the ask pattern it is the responsibility of the caller to ensure
   *         that the matching type is expected. Otherwise, a [[ClassCastException]] will occur during runtime.
   */
  def ask[R: Tag](message: ResponderRequest): Task[R]

  /**
   * In order to receive messages a [[MessageHandler]] must subscribe to the [[MessageRelay]] during the construction of its layer.
   * For a given type only a single [[MessageHandler]] should be subscribe.
   * The [[MessageRelay]] will route a message only to the first [[MessageHandler]] it finds.
   *
   * @param handler An instance of [[MessageHandler]] subscribing.
   * @return The subscribed instance.
   *
   * @example Example of building a handler layer with subscribing to the [[MessageRelay]]:
   *
   * {{{
   *  val layer: URLayer[MessageRelay, TestHandler] =
   *        ZLayer.fromZIO(ZIO.serviceWithZIO[MessageRelay](_.subscribe(TestHandler())))
   * }}}
   */
  def subscribe[H <: MessageHandler](handler: H): UIO[H]
}

/**
 * An instance of [[MessageHandler]] subscribes to the [[MessageRelay]].
 * Every message passed into the ask method of the relay will be routed to the handler if it is responsible for it.
 */
trait MessageHandler {

  /**
   * The [[MessageRelay]] sends messages to this method.
   *
   * @param message The message which should be handled
   * @return A [[Task]] containing the response to the message received.
   *         The type `Any` is very unfortunate and the implementation must follow type convention used in the ask pattern convention.
   */
  def handle(message: ResponderRequest): Task[Any]

  /**
   * Before the [[MessageRelay]] sends messages to the handler it will make sure that it is capable of handling the
   * type and the relay check this using this method.
   *
   * @param message The actual message which should be handled.
   * @return `true`  if the handler implementation in the handle method contains code for the type of this message.
   *         `false` if the handler does not know how to process this message.
   */
  def isResponsibleFor(message: ResponderRequest): Boolean
}

case class MessageRelayLive(handlersRef: Ref[List[MessageHandler]]) extends MessageRelay {
  override def ask[R: Tag](message: ResponderRequest): Task[R] =
    handlersRef.get.flatMap { handlers =>
      ZIO
        .fromOption(handlers.find(_.isResponsibleFor(message)))
        .orDieWith(_ => new IllegalStateException(s"No handler defined for ${message.getClass}."))
        .flatMap(_.handle(message).map(_.asInstanceOf[R]))
    }

  override def subscribe[H <: MessageHandler](handler: H): UIO[H] =
    for {
      _ <- handlersRef.update(handler :: _)
      _ <- ZIO.logInfo(s"Subscribed ${handler.getClass}")
    } yield handler
}

object MessageRelayLive {
  val layer: ULayer[MessageRelayLive] = ZLayer.fromZIO {
    for {
      ref <- Ref.make(List.empty[MessageHandler])
    } yield MessageRelayLive(ref)
  }
}
