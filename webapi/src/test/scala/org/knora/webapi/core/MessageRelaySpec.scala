package org.knora.webapi.core

import zio.test._
import zio._

import org.knora.webapi.messages.ResponderRequest

object MessageRelaySpec extends ZIOSpecDefault {

  case class TestHandler() extends MessageHandler {
    override def handle(message: ResponderRequest): Task[Any] = ZIO.succeed("handled")
    override def isResponsibleFor(message: ResponderRequest): Boolean = message match {
      case _: HandledTestMessage => true
      case _                     => false
    }
  }
  object TestHandler {
    val layer: URLayer[MessageRelay, TestHandler] = ZLayer.fromZIO {
      for {
        mr     <- ZIO.service[MessageRelay]
        handler = TestHandler()
        _      <- mr.subscribe(handler)
      } yield handler
    }
  }

  case class HandledTestMessage() extends ResponderRequest
  case class UnknownTestMessage() extends ResponderRequest

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("MessageRelay")(test("should relay HandledTestMessage to registered TestHandler") {
      for {
        _      <- ZIO.service[TestHandler]
        mr     <- ZIO.service[MessageRelay]
        result <- mr.ask(HandledTestMessage())
      } yield assertTrue(result == "handled")
    }).provide(MessageRelayLive.layer, TestHandler.layer)

}
