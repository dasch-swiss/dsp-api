/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders

import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.*

import dsp.errors.ApplicationLockException
import org.knora.webapi.IRI

/**
 * Tests [[IriLocker]].
 */
object IriLockerSpec extends ZIOSpecDefault {

  import scala.concurrent.ExecutionContext.Implicits.global

  val SUCCESS = "success"
  val FAILURE = "failure"

  val spec: Spec[Any, Nothing] = suite("IriLocker")(
    test("not allow a request to acquire a lock when another request already has it") {
      def runLongTask(): Future[String] = Future {
        Thread.sleep(16000)
        SUCCESS
      }

      def runShortTask(): Future[String] = Future(SUCCESS)

      val testIri: IRI = "http://example.org/test1"

      val firstApiRequestID = UUID.randomUUID

      IriLocker.runWithIriLock(
        apiRequestID = firstApiRequestID,
        iri = testIri,
        task = () => runLongTask(),
      )

      // Wait a bit to allow the first request to get the lock.
      Thread.sleep(500)

      val secondApiRequestID = UUID.randomUUID

      val secondTaskResultFuture = IriLocker.runWithIriLock(
        apiRequestID = secondApiRequestID,
        iri = testIri,
        task = () => runShortTask(),
      )

      val secondTaskFailedWithLockTimeout =
        try {
          Await.result(secondTaskResultFuture, 20.seconds)
          false
        } catch {
          case _: ApplicationLockException => true
        }

      assertTrue(secondTaskFailedWithLockTimeout)
    },
    test("provide reentrant locks") {
      def runRecursiveTask(iri: IRI, apiRequestID: UUID, count: Int): Future[String] =
        if (count > 0) {
          IriLocker.runWithIriLock(
            apiRequestID = apiRequestID,
            iri = iri,
            task = () => runRecursiveTask(iri, apiRequestID, count - 1),
          )
        } else {
          Future(SUCCESS)
        }

      val testIri: IRI = "http://example.org/test2"

      val firstApiRequestID  = UUID.randomUUID
      val firstTestResult    = Await.result(runRecursiveTask(testIri, firstApiRequestID, 3), 1.second)
      val secondApiRequestID = UUID.randomUUID
      val secondTestResult   = Await.result(runRecursiveTask(testIri, secondApiRequestID, 3), 1.second)
      assertTrue(firstTestResult == SUCCESS, secondTestResult == SUCCESS)
    },
    test("release a lock when a task returns a failed future") {
      // If succeed is true, returns a successful future, otherwise returns a failed future.
      def runTask(succeed: Boolean): Future[String] = Future {
        if (succeed) {
          SUCCESS
        } else {
          throw new Exception(FAILURE)
        }
      }

      val testIri: IRI = "http://example.org/test3"

      val firstApiRequestID = UUID.randomUUID

      val firstTaskResultFuture = IriLocker.runWithIriLock(
        apiRequestID = firstApiRequestID,
        iri = testIri,
        task = () => runTask(false),
      )

      val firstTaskFailed =
        try {
          Await.result(firstTaskResultFuture, 1.second)
          false
        } catch {
          case _: Exception => true
        }

      val secondApiRequestID = UUID.randomUUID

      val secondTaskResultFuture = IriLocker.runWithIriLock(
        apiRequestID = secondApiRequestID,
        iri = testIri,
        task = () => runTask(true),
      )

      val secondTaskResult = Await.result(secondTaskResultFuture, 1.second)

      assertTrue(firstTaskFailed, secondTaskResult == SUCCESS)
    },
    test("release a lock when a task throws an exception instead of returning a future") {
      // If succeed is true, returns a successful future, otherwise throws an exception.
      def runTask(succeed: Boolean): Future[String] =
        if (succeed) {
          Future(SUCCESS)
        } else {
          throw new Exception(FAILURE)
        }

      val testIri: IRI = "http://example.org/test4"

      val firstApiRequestID = UUID.randomUUID

      val firstTaskResultFuture = IriLocker.runWithIriLock(
        apiRequestID = firstApiRequestID,
        iri = testIri,
        task = () => runTask(false),
      )

      val firstTaskFailed =
        try {
          Await.result(firstTaskResultFuture, 1.second)
          false
        } catch {
          case _: Exception => true
        }

      val secondApiRequestID = UUID.randomUUID

      val secondTaskResultFuture = IriLocker.runWithIriLock(
        apiRequestID = secondApiRequestID,
        iri = testIri,
        task = () => runTask(true),
      )

      val secondTaskResult = Await.result(secondTaskResultFuture, 1.second)

      assertTrue(firstTaskFailed, secondTaskResult == SUCCESS)
    },
  )
}
