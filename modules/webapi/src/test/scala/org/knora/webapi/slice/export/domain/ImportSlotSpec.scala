/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class ImportSlotSpec extends ZIOSpecDefault {

  private val noWork: Int => UIO[Unit] = _ => ZIO.unit

  /** A following run succeeds and runs its prepare. */
  private def isFree(slot: ImportSlot): UIO[Boolean] =
    for {
      ran    <- Ref.make(false)
      result <- slot.run(ran.set(true).as(1))(noWork).either
      did    <- ran.get
    } yield result.isRight && did

  /** Waits until the slot is free; `work` has released it by then. */
  private def awaitFree(slot: ImportSlot): UIO[Boolean] =
    isFree(slot).repeatUntil(identity)

  private val busySuite = suite("busy slot")(
    test("a second run fails while work runs and does not run its prepare") {
      for {
        slot   <- ZIO.service[ImportSlot]
        gate   <- Promise.make[Nothing, Unit]
        ran    <- Ref.make(false)
        _      <- slot.run(ZIO.succeed(1))(_ => gate.await)
        second <- slot.run(ran.set(true).as(2))(noWork).either
        did    <- ran.get
        _      <- gate.succeed(())
      } yield assertTrue(second == Left(ImportSlotBusyError()), !did)
    },
    test("a second run fails while prepare runs") {
      for {
        slot   <- ZIO.service[ImportSlot]
        gate   <- Promise.make[Nothing, Unit]
        first  <- slot.run(gate.await.as(1))(noWork).fork
        second <- slot.run(ZIO.succeed(2))(noWork).either
        _      <- gate.succeed(())
        _      <- first.join
      } yield assertTrue(second == Left(ImportSlotBusyError()))
    },
  )

  private val prepareSuite = suite("end of prepare")(
    test("the slot is free after a failure") {
      for {
        slot   <- ZIO.service[ImportSlot]
        failed <- slot.run(ZIO.fail("boom"))(noWork).either
        free   <- isFree(slot)
      } yield assertTrue(failed == Left("boom"), free)
    },
    test("the slot is free after a defect") {
      for {
        slot <- ZIO.service[ImportSlot]
        exit <- slot.run(ZIO.die(new IllegalStateException("boom")))(noWork).exit
        free <- isFree(slot)
      } yield assertTrue(exit.isFailure, free)
    },
    test("the slot is free after an interruption") {
      for {
        slot    <- ZIO.service[ImportSlot]
        started <- Promise.make[Nothing, Unit]
        fiber   <- slot.run(started.succeed(()) *> ZIO.never)(noWork).fork
        _       <- started.await
        _       <- fiber.interrupt
        free    <- isFree(slot)
      } yield assertTrue(free)
    },
  )

  private val workSuite = suite("end of work")(
    test("the slot is free after success") {
      for {
        slot <- ZIO.service[ImportSlot]
        _    <- slot.run(ZIO.succeed(1))(noWork)
        free <- awaitFree(slot)
      } yield assertTrue(free)
    },
    test("the slot is free after a defect") {
      for {
        slot <- ZIO.service[ImportSlot]
        _    <- slot.run(ZIO.succeed(1))(_ => ZIO.die(new IllegalStateException("boom")))
        free <- awaitFree(slot)
      } yield assertTrue(free)
    },
    test("the slot is free after an interruption of work, which stays interruptible") {
      for {
        slot    <- ZIO.service[ImportSlot]
        started <- Promise.make[Nothing, Unit]
        workId  <- Promise.make[Nothing, FiberId]
        _       <- slot.run(ZIO.succeed(1))(_ => ZIO.fiberId.flatMap(workId.succeed) *> started.succeed(()).unit *> ZIO.never)
        _       <- started.await
        id      <- workId.await
        roots   <- Fiber.roots
        daemon   = roots.filter(_.id == id)
        _       <- ZIO.foreachDiscard(daemon)(_.interrupt)
        free    <- awaitFree(slot)
      } yield assertTrue(daemon.size == 1, free)
    },
  )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("ImportSlot")(busySuite, prepareSuite, workSuite).provide(ImportSlot.layer)
      @@ TestAspect.timeout(30.seconds)
}
