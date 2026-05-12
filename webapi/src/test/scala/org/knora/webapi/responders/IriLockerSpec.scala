/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders

import zio.Promise
import zio.Task
import zio.ZIO
import zio.durationInt
import zio.test.Spec
import zio.test.TestAspect
import zio.test.TestClock
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import java.util.UUID

import dsp.errors.ApplicationLockException
import org.knora.webapi.IRI

/**
 * Tests [[IriLocker]].
 */
object IriLockerSpec extends ZIOSpecDefault {

  private val SUCCESS = "success"
  private val FAILURE = "failure"

  val spec: Spec[Any, Throwable] = suite("IriLocker")(
    test("not allow a request to acquire a lock when another request already has it") {
      val testIri: IRI = "http://example.org/test1"

      val firstApiRequestID  = UUID.randomUUID
      val secondApiRequestID = UUID.randomUUID

      for {
        // Use promises to signal lock acquisition and to keep the lock held for as long as we want.
        lockHeld <- Promise.make[Nothing, Unit]
        release  <- Promise.make[Nothing, Unit]
        longTask  = lockHeld.succeed(()) *> release.await.as(SUCCESS)
        shortTask = ZIO.succeed(SUCCESS)

        // Start the long-running task in the background so it holds the lock.
        firstFiber <- IriLocker.runWithIriLock(firstApiRequestID, testIri)(longTask).fork
        // Wait until the first request has acquired the lock.
        _ <- lockHeld.await

        // The second request will retry for MAX_LOCK_RETRY_MILLIS (15s) before failing.
        secondFiber <- IriLocker.runWithIriLock(secondApiRequestID, testIri)(shortTask).either.fork
        // Advance virtual time past the maximum lock-retry window.
        _      <- TestClock.adjust(16.seconds)
        result <- secondFiber.join

        // Let the first task finish and clean up.
        _ <- release.succeed(())
        _ <- firstFiber.join
      } yield assertTrue(result.left.exists(_.isInstanceOf[ApplicationLockException]))
    },
    test("provide reentrant locks") {
      def runRecursiveTask(iri: IRI, apiRequestID: UUID, count: Int): Task[String] =
        if (count > 0) {
          IriLocker.runWithIriLock(apiRequestID, iri)(runRecursiveTask(iri, apiRequestID, count - 1))
        } else {
          ZIO.succeed(SUCCESS)
        }

      val testIri: IRI = "http://example.org/test2"

      for {
        firstResult  <- runRecursiveTask(testIri, UUID.randomUUID, 3)
        secondResult <- runRecursiveTask(testIri, UUID.randomUUID, 3)
      } yield assertTrue(firstResult == SUCCESS, secondResult == SUCCESS)
    },
    test("release a lock when a task fails") {
      def runTask(succeed: Boolean): Task[String] =
        if (succeed) ZIO.succeed(SUCCESS) else ZIO.fail(new Exception(FAILURE))

      val testIri: IRI = "http://example.org/test3"

      for {
        firstResult  <- IriLocker.runWithIriLock(UUID.randomUUID, testIri)(runTask(false)).either
        secondResult <- IriLocker.runWithIriLock(UUID.randomUUID, testIri)(runTask(true))
      } yield assertTrue(firstResult.isLeft, secondResult == SUCCESS)
    },
    test("release a lock when a task dies with an exception") {
      def runTask(succeed: Boolean): Task[String] =
        if (succeed) ZIO.succeed(SUCCESS) else ZIO.attempt(throw new Exception(FAILURE))

      val testIri: IRI = "http://example.org/test4"

      for {
        firstResult  <- IriLocker.runWithIriLock(UUID.randomUUID, testIri)(runTask(false)).either
        secondResult <- IriLocker.runWithIriLock(UUID.randomUUID, testIri)(runTask(true))
      } yield assertTrue(firstResult.isLeft, secondResult == SUCCESS)
    },
  ) @@ TestAspect.sequential
}
