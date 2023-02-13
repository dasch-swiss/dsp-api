package org.knora.webapi.core
import zio.Ref
import zio.Task
import zio.UIO
import zio.ULayer
import zio.ZIO
import zio.ZLayer
import scala.collection.mutable

import org.knora.webapi.messages.ResponderRequest

trait MessageRelay {
  def ask(message: ResponderRequest): Task[Any]
  def subscribe(handler: MessageHandler): UIO[Unit]
}

trait MessageHandler {
  def handle(message: ResponderRequest): Task[Any]
  def responsibleForClass: Class[ResponderRequest]
}

case class MessageRelayLive(handlersRef: Ref[mutable.Map[Class[_], MessageHandler]]) extends MessageRelay {
  override def ask(message: ResponderRequest): Task[Any] =
    handlersRef.get
      .flatMap(handlers =>
        ZIO
          .fromOption(handlers.get(message.getClass))
          .orDieWith(_ => new IllegalStateException(s"Handler not registered for class '${message.getClass}''"))
          .map(_.handle(message))
      )
  override def subscribe(handler: MessageHandler): UIO[Unit] =
    handlersRef.update(_.addOne((handler.responsibleForClass, handler)))
}

object MessageRelayLive {
  val layer: ULayer[MessageRelayLive] = ZLayer.fromZIO {
    val handlerMap: mutable.Map[Class[_], MessageHandler] = mutable.Map.empty
    for {
      ref <- Ref.make(handlerMap)
    } yield MessageRelayLive(ref)
  }
}
