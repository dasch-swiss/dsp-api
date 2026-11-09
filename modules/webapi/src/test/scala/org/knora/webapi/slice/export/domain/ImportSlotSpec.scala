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

  private def awaitFree(slot: ImportSlot): UIO[Unit] = ImportSlotTestSupport.awaitFree(slot)

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
        _      <- awaitFree(slot)
      } yield assertTrue(failed == Left("boom"))
    },
    test("the slot is free after a defect") {
      for {
        slot <- ZIO.service[ImportSlot]
        exit <- slot.run(ZIO.die(new IllegalStateException("boom")))(noWork).exit
        _    <- awaitFree(slot)
      } yield assertTrue(exit.isFailure)
    },
    test("the slot is free after an interruption") {
      for {
        slot    <- ZIO.service[ImportSlot]
        started <- Promise.make[Nothing, Unit]
        fiber   <- slot.run(started.succeed(()) *> ZIO.never)(noWork).fork
        _       <- started.await
        _       <- fiber.interrupt
        _       <- awaitFree(slot)
      } yield assertTrue(true)
    },
  )

  private val workSuite = suite("end of work")(
    test("the slot is free after success") {
      for {
        slot <- ZIO.service[ImportSlot]
        _    <- slot.run(ZIO.succeed(1))(noWork)
        _    <- awaitFree(slot)
      } yield assertTrue(true)
    },
    test("the slot is free after a defect") {
      for {
        slot <- ZIO.service[ImportSlot]
        _    <- slot.run(ZIO.succeed(1))(_ => ZIO.die(new IllegalStateException("boom")))
        _    <- awaitFree(slot)
      } yield assertTrue(true)
    },
    test("work runs interruptibly") {
      for {
        slot          <- ZIO.service[ImportSlot]
        interruptible <- Promise.make[Nothing, Boolean]
        _             <- slot.run(ZIO.succeed(1))(_ => ZIO.checkInterruptible(i => interruptible.succeed(i.isInterruptible)).unit)
        result        <- interruptible.await
        _             <- awaitFree(slot)
      } yield assertTrue(result)
    },
    test("the slot is free after an interruption of work") {
      for {
        slot <- ZIO.service[ImportSlot]
        _    <- slot.run(ZIO.succeed(1))(_ => ZIO.interrupt)
        _    <- awaitFree(slot)
      } yield assertTrue(true)
    },
  )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("ImportSlot")(busySuite, prepareSuite, workSuite).provide(ImportSlot.layer)
      @@ TestAspect.timeout(30.seconds)
}
