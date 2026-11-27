/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import org.junit.runner.RunWith
import swiss.dasch.api.ApiProblem.InternalServerError
import swiss.dasch.api.ApiProblem.NotFound
import swiss.dasch.infrastructure.CorrelationId
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class HandlerFunctionsSpec extends ZIOSpecDefault {

  private val traceId = "0af7651916cd43dd8448eb211c80319c"

  private val errorLogs =
    ZTestLogger.logOutput.map(_.filter(_.logLevel == LogLevel.Error))

  val spec = suite("HandlerFunctions.guarded")(
    test("answer a defect with the structured 500 and log its cause once with the same correlation_id") {
      for {
        exit <- HandlerFunctions.guarded(ZIO.die(new IllegalStateException("boom at /tmp/x"))).exit
        logs <- errorLogs
        id    = exit.causeOption.flatMap(_.failureOption).collect { case e: InternalServerError => e.correlationId }
      } yield assertTrue(
        exit.causeOption.flatMap(_.failureOption).exists {
          case InternalServerError(_, "internal", message) => !message.contains("/tmp/")
          case _                                           => false
        },
        logs.size == 1,
        logs.headOption.exists(_.cause.prettyPrint.contains("boom at /tmp/x")),
        logs.headOption.flatMap(_.annotations.get(CorrelationId.annotationKey)) == id,
      )
    },
    test("keep a typed failure and log nothing") {
      for {
        exit <- HandlerFunctions.guarded(ZIO.fail(NotFound("0001", "project"))).exit
        logs <- errorLogs
      } yield assertTrue(exit == Exit.fail(NotFound("0001", "project")), logs.isEmpty)
    },
    test("keep a typed failure and log the defect that came with it once") {
      val failsThenDies = ZIO.fail(NotFound("0001", "project")).ensuring(ZIO.die(new IllegalStateException("release")))
      for {
        exit <- HandlerFunctions.guarded(failsThenDies).exit
        logs <- errorLogs
      } yield assertTrue(
        exit == Exit.fail(NotFound("0001", "project")),
        logs.size == 1,
        logs.headOption.exists(_.cause.prettyPrint.contains("release")),
      )
    },
    test("pass an interruption through without a log entry") {
      for {
        exit <- HandlerFunctions.guarded(ZIO.interrupt).exit
        logs <- errorLogs
      } yield assertTrue(exit.isInterrupted, logs.isEmpty)
    },
    test("use the request's trace_id as the correlation_id") {
      for {
        exit <- (HandlerFunctions.guarded(ZIO.die(new RuntimeException("boom"))) @@
                  ZIOAspect.annotated("trace_id", traceId)).exit
      } yield assertTrue(
        exit.causeOption.flatMap(_.failureOption).exists {
          case InternalServerError(id, _, _) => id == traceId
          case _                             => false
        },
      )
    },
    test("give a fresh 32-hex correlation_id outside a request") {
      for {
        exit <- HandlerFunctions.guarded(ZIO.die(new RuntimeException("boom"))).exit
      } yield assertTrue(
        exit.causeOption.flatMap(_.failureOption).exists {
          case InternalServerError(id, _, _) => id.matches("[0-9a-f]{32}")
          case _                             => false
        },
      )
    },
  )
}
