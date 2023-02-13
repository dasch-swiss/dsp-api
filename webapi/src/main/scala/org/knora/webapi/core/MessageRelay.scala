package org.knora.webapi.core
import zio.Ref
import zio.Runtime
import zio.Task
import zio.UIO
import zio.ULayer
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.routing.UnsafeZioRun

trait MessageRelay {
  def ask(message: ResponderRequest): Task[Any]
  def subscribe(handler: MessageHandler): UIO[Unit]
}

trait MessageHandler {
  def handle(message: ResponderRequest): Task[Any]
  def isResponsibleFor(message: ResponderRequest): Boolean
}

case class MessageRelayLive(handlersRef: Ref[List[MessageHandler]]) extends MessageRelay {
  override def ask(message: ResponderRequest): Task[Any] =
    handlersRef.get
      .flatMap(handlers =>
        ZIO
          .fromOption(handlers.find(_.isResponsibleFor(message)))
          .orDieWith(_ => new IllegalStateException(s"Handler not registered for class '${message.getClass}''"))
          .map(_.handle(message))
      )
  override def subscribe(handler: MessageHandler): UIO[Unit] =
    handlersRef.update(handler :: _) <* ZIO.logInfo(s"Subscribed ${handler.getClass}")
}

object MessageRelayLive {
  private val make: UIO[MessageRelay]               = Ref.make(List.empty[MessageHandler]).map(MessageRelayLive(_))
  def empty(implicit r: Runtime[Any]): MessageRelay = UnsafeZioRun.runOrThrow(make)
  val layer: ULayer[MessageRelay]                   = ZLayer.fromZIO(make)
}
