package org.knora.webapi.core

import zio._
import zio.test.Assertion.anything
import zio.test.Assertion.dies
import zio.test.Assertion.isSubtype
import zio.test._

import org.knora.webapi.messages.ResponderRequest

object MessageRelaySpec extends ZIOSpecDefault {

  case class TestHandler() extends MessageHandler {
    override def handle(message: ResponderRequest): Task[Any]         = ZIO.succeed("handled")
    override def isResponsibleFor(message: ResponderRequest): Boolean = message.isInstanceOf[HandledTestMessage]
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
    suite("MessageRelay")(
      test("when asked with an UnknownTestMessage then it should die with an IllegalStateException") {
        for {
          // need to include the TestHandler in the test otherwise the layer and hence its subscription is ignored
          _      <- ZIO.service[TestHandler]
          mr     <- ZIO.service[MessageRelay]
          actual <- mr.ask(UnknownTestMessage()).exit
        } yield assert(actual)(dies(isSubtype[IllegalStateException](anything)))
      },
      test("when asked with a HandledTestMessage then it should relay it to the registered TestHandler") {
        for {
          // need to include the TestHandler in the test otherwise the layer and hence its subscription is ignored
          _      <- ZIO.service[TestHandler]
          mr     <- ZIO.service[MessageRelay]
          actual <- mr.ask(HandledTestMessage())
        } yield assertTrue(actual == "handled")
      }
    ).provide(MessageRelayLive.layer, TestHandler.layer)
}
