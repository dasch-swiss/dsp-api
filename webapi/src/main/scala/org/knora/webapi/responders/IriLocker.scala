/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders

import zio.Task
import zio.UIO
import zio.ZIO

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import dsp.errors.ApplicationLockException
import org.knora.webapi.IRI
import org.knora.webapi.slice.common.KnoraIris.KnoraIri
import org.knora.webapi.util.JavaUtil

/**
 * Implements JVM-wide, reentrant, application-level update locks on data represented by IRIs, such as Knora
 * resources. Each API operation that intends to perform an update receives an API request ID from its API route. It
 * can then use that ID to perform update operations while holding an update lock,
 * by using the `runWithIriLock` method provided by this class.
 */
object IriLocker {

  /**
   * Represents an update lock on an IRI.
   *
   * @param apiRequestID the ID of the API request that has locked the IRI.
   * @param entryCount   the number of times the API request has acquired the lock without releasing it.
   */
  private case class IriLock(apiRequestID: UUID, entryCount: Int)

  /**
   * Contains an entry for each locked IRI. The first time an API request acquires a lock
   * on an IRI, the entry count is set to 1. If an API request already has the lock and asks for it
   * again, the entry count is incremented. Each time the API request asks to release the lock, the
   * entry count is decremented. The lock is released only when the entry count reaches 0.
   */
  private val lockMap = new ConcurrentHashMap[IRI, IriLock]()

  /**
   * The number of milliseconds to wait between attempts to acquire a lock.
   */
  private val LOCK_RETRY_MILLIS = 100

  /**
   * The number of times to try to acquire a lock before giving up.
   */
  private val MAX_LOCK_RETRIES = 150

  /**
   * The total number of milliseconds that an API request should wait before giving up on acquiring a lock.
   */
  private val MAX_LOCK_RETRY_MILLIS = LOCK_RETRY_MILLIS * MAX_LOCK_RETRIES

  /**
   * Acquires an update lock on an IRI, then runs a task that updates the IRI. The lock implementation
   * is reentrant: if the API request requesting the lock already has it, this will not cause a deadlock. The lock is
   * released after all tasks that used it for the same API request have either completed or failed. (Failure means
   * either throwing an exception or returning a failed `Future`.) If the lock cannot be acquired because another API
   * request has it, this method waits and retries. (The wait duration and maximum number of retries are defined in
   * this class. The waiting is done in a [[Future]], so this method does not block.) If this method still cannot
   * acquire the lock after the maximum number of retries, it throws [[ApplicationLockException]].
   *
   * @param apiRequestID the ID of the API request that needs the lock.
   * @param iri          the IRI to be locked.
   * @param task         a function returning a [[Future]] that performs the update. This function will be called only after
   *                     the lock has been acquired. If the task throws an exception or returns a failed `Future`,
   *                     this method will return a failed `Future`.
   * @return the result of the task.
   */
  def runWithIriLock[T](apiRequestID: UUID, iri: IRI, task: () => Future[T])(implicit
    executionContext: ExecutionContext,
  ): Future[T] =
    // Try to acquire the lock in a future. If we can't acquire the lock, this future will fail without running
    // the task.
    Future(acquireLock(iri, apiRequestID)).flatMap { _ =>
      // Once we've acquired the lock, run the task in a future, because it might throw an exception rather than
      // returning a future. Flatten nested futures into one, then decrement or release the lock.
      Future(task()).flatten.andThen { case _ =>
        decrementOrReleaseLock(iri, apiRequestID)
      }
    }

  def runWithIriLock[A](apiRequestID: UUID, iri: KnoraIri, task: Task[A]): Task[A] =
    runWithIriLock(apiRequestID, iri.toInternalSchema.toString, task)

  /**
   * Acquires an update lock on an IRI, then runs a task that updates the IRI.
   * The lock implementation is reentrant: if the API request requesting the lock already has it, this will not cause a deadlock.
   * The lock is released after all tasks that used it for the same API request have either completed or failed.
   * If the lock cannot be acquired because another API request has it, this method waits and retries.
   * (The wait duration and maximum number of retries are defined in this class.)
   * If this method still cannot acquire the lock after the maximum number of retries, it fails with a [[ApplicationLockException]].
   *
   * @param apiRequestID the ID of the API request that needs the lock.
   * @param iri          the IRI to be locked.
   * @param task         The [[Task]] that performs the update.
   * @return the result of the task.
   */
  def runWithIriLock[A](apiRequestID: UUID, iri: IRI, task: Task[A]): Task[A] = {
    val acquire: Task[Unit]        = ZIO.attemptBlocking(this.acquireLock(iri, apiRequestID)).logError
    val release: Unit => UIO[Unit] = _ => ZIO.attempt(decrementOrReleaseLock(iri, apiRequestID)).logError.ignore
    ZIO.scoped(ZIO.acquireRelease(acquire)(release) *> task)
  }

  def runWithIriLock_[A](apiRequestID: UUID, iri: IRI)(task: Task[A]): Task[A] = {
    val acquire: Task[Unit]        = ZIO.attemptBlocking(this.acquireLock(iri, apiRequestID)).logError
    val release: Unit => UIO[Unit] = _ => ZIO.attempt(decrementOrReleaseLock(iri, apiRequestID)).logError.ignore
    ZIO.scoped(ZIO.acquireRelease(acquire)(release) *> task)
  }

  /**
   * Tries to acquire an update lock for an API request on an IRI. If the API request already
   * has the lock, the lock's entry count is incremented. If another API request has the lock,
   * waits and retries. If the lock is still unavailable after the maximum number of retries,
   * throws [[ApplicationLockException]].
   *
   * @param iri          the IRI to be locked.
   * @param apiRequestID the ID of the API request that needs the lock.
   */
  private def acquireLock(iri: IRI, apiRequestID: UUID): Unit =
    acquireOrIncrementLock(iri, apiRequestID, MAX_LOCK_RETRIES)

  /**
   * Tries to acquire an update lock for an API request on an IRI. If the API request already
   * has the lock, the lock's entry count is incremented. If another API request has the lock,
   * waits and retries. If the lock is still unavailable after the maximum number of retries,
   * throws [[ApplicationLockException]].
   *
   * @param iri          the IRI to be locked.
   * @param apiRequestID the ID of the API request that needs the lock.
   * @param tries        the number of times to try to acquire the lock.
   */
  @tailrec
  private def acquireOrIncrementLock(iri: IRI, apiRequestID: UUID, tries: Int): Unit = {
    // Try to acquire the lock, giving it an initial entry count of 1 if it's unused.
    val newLock = lockMap.merge(
      iri,
      IriLock(apiRequestID, 1),
      JavaUtil.biFunction((currentLock, _) =>
        // The lock is already in use. Who has it?
        if (currentLock.apiRequestID == apiRequestID) {
          // We already have it, so increment the entry count.
          currentLock.copy(entryCount = currentLock.entryCount + 1)
        } else {
          // Another API request has it, so leave it as is.
          currentLock
        },
      ),
    )
    // Do we have the lock?
    if (newLock.apiRequestID == apiRequestID) {
      // Yes.
      ()
    } else {
      // No, another API request has it. Can we wait and retry?
      if (tries > 1) {
        // Yes.
        Thread.sleep(LOCK_RETRY_MILLIS)
        acquireOrIncrementLock(iri, apiRequestID, tries - 1)
      } else {
        // No, we've run out of retries, so throw an exception.
        throw ApplicationLockException(
          s"Could not acquire update lock on $iri for API request $apiRequestID within $MAX_LOCK_RETRY_MILLIS ms, because API request ${newLock.apiRequestID} is holding it",
        )
      }
    }
  }

  /**
   * Checks that the specified API request has a lock on the specified IRI, then either decrements
   * the lock's entry count or releases the lock.
   *
   * @param iri          the IRI that is locked.
   * @param apiRequestID the ID of the API request that has the lock.
   */
  private def decrementOrReleaseLock(iri: IRI, apiRequestID: UUID): Unit = {
    val _ = lockMap.compute(
      iri,
      JavaUtil.biFunction((_, maybeCurrentLock) =>
        Option(maybeCurrentLock) match {
          case Some(currentLock) =>
            if (currentLock.apiRequestID == apiRequestID) {
              // We have the lock. Should we decrement its entry count or release it?
              if (currentLock.entryCount > 1) {
                // Decrement its entry count.
                currentLock.copy(entryCount = currentLock.entryCount - 1)
              } else {
                // Release it.
                null
              }
            } else {
              // Another API request has the lock. This shouldn't happen.
              throw ApplicationLockException(
                s"API request $apiRequestID was supposed to have an update lock on $iri, but API request ${currentLock.apiRequestID} has it",
              )
            }

          case None =>
            // The lock is unused. This shouldn't happen.
            throw ApplicationLockException(
              s"API request $apiRequestID was supposed to have an update lock on $iri, but the lock is unused",
            )
        },
      ),
    )
  }
}
