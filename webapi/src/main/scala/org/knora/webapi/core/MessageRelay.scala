package org.knora.webapi.core
import zio.Ref
import zio.Task
import zio.UIO
import zio.ULayer
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.messages.ResponderRequest

trait MessageRelay {
  def ask(message: ResponderRequest): Task[Any]
  def subscribe(handler: MessageHandler): UIO[Unit]
}

trait MessageHandler {
  def handle(message: ResponderRequest): Task[Any]
  def isReponsibleFor(message: ResponderRequest): Boolean
}

case class MessageRelayLive(handlersRef: Ref[List[MessageHandler]]) extends MessageRelay {
  override def ask(message: ResponderRequest): Task[Any] =
    handlersRef.get
      .flatMap(handlers =>
        ZIO
          .fromOption(handlers.find(_.isReponsibleFor(message)))
          .orDieWith(_ => new IllegalStateException(s"Handler not registered for class '${message.getClass}''"))
          .map(_.handle(message))
      )
  override def subscribe(handler: MessageHandler): UIO[Unit] = handlersRef.update(_.prepended(handler))
}

object MessageRelayLive {
  val layer: ULayer[MessageRelayLive] = ZLayer.fromZIO(Ref.make(List.empty[MessageHandler]).map(MessageRelayLive(_)))
}
