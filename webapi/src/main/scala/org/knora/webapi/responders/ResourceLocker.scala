/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.responders

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import org.knora.webapi.util.JavaFunctionUtil
import org.knora.webapi.{ApplicationLockException, IRI}

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

/**
  * Implements JVM-wide, reentrant, application-level update locks on Knora resources. Each API operation that
  * intends to update a resource (or any part of a resource) receives an API request ID from its API route. It
  * can then use that ID to perform update operations on the resource while holding an update lock,
  * by using the `runWithResourceLock` method provided by this class.
  */
object ResourceLocker {

    /**
      * Represents an update lock on a resource.
      *
      * @param apiRequestID the ID of the API request that has locked the resource.
      * @param entryCount the number of times the API request has acquired the lock without releasing it.
      */
    private case class ResourceLock(apiRequestID: UUID, entryCount: Int)

    /**
      * Contains an entry for each locked resource. The first time an API request acquires a lock
      * on a resource, the entry count is set to 1. If an API request already has the lock and asks for it
      * again, the entry count is incremented. Each time the API request asks to release the lock, the
      * entry count is decremented. The lock is released only when the entry count reaches 0.
      */
    private val lockMap = new ConcurrentHashMap[IRI, ResourceLock]()

    /**
      * The number of milliseconds to wait between attempts to acquire a lock.
      */
    private val LOCK_RETRY_MILLIS = 100

    /**
      * The number of times to try to acquire a lock before giving up.
      */
    private val MAX_LOCK_RETRIES = 10

    /**
      * The total number of milliseconds that an API request should wait before giving up on acquiring a lock.
      */
    private val MAX_LOCK_RETRY_MILLIS = LOCK_RETRY_MILLIS * MAX_LOCK_RETRIES

    /**
      * Acquires an update lock on a Knora resource, then runs a task that updates the resource. The lock implementation
      * is reentrant: if the API request requesting the lock already has it, this will not cause a deadlock. After the
      * completion of all tasks that used the lock for the same the API request, the lock is released. If the lock cannot
      * be acquired because another API request has it, this method waits and retries. (The wait duration and maximum
      * number of retries are defined in this class. The waiting is done in a [[Future]], so this method does not block.)
      * If this method still cannot acquire the lock after the maximum number of retries, it throws
      * [[ApplicationLockException]].
      *
      * @param apiRequestID the ID of the API request that needs the lock.
      * @param resourceIri the IRI of the resource to be updated.
      * @param task a function returning a [[Future]] that updates the resource. This function will be called only after
      *             the lock has been acquired.
      * @return the result of the task.
      */
    def runWithResourceLock[T](apiRequestID: UUID, resourceIri: IRI, task: () => Future[T])(implicit executionContext: ExecutionContext): Future[T] = {
        Future(acquireOrIncrementLock(resourceIri, apiRequestID, MAX_LOCK_RETRIES)).flatMap {
            _ => task().andThen {
                case _ => decrementOrReleaseLock(resourceIri, apiRequestID)
            }
        }
    }

    /**
      * Tries to acquire an update lock for an API request on a resource. If the API request already
      * has the lock, the lock's entry count is incremented. If another API request has the lock,
      * waits and retries. If the lock is still unavailable after the maximum number of retries,
      * throws [[ApplicationLockException]].
      *
      * @param resourceIri the IRI of the resource to be locked.
      * @param apiRequestID the ID of the API request that needs the lock.
      * @param tries the number of times to try to acquire the lock.
      */
    @tailrec
    private def acquireOrIncrementLock(resourceIri: IRI, apiRequestID: UUID, tries: Int): Unit = {
        // Try to acquire the lock, giving it an initial entry count of 1 if it's unused.
        val newLock = lockMap.merge(
            resourceIri,
            ResourceLock(apiRequestID, 1),
            JavaFunctionUtil.biFunction({ (currentLock, _) =>
                // The lock is already in use. Who has it?
                if (currentLock.apiRequestID == apiRequestID) {
                    // We already have it, so increment the entry count.
                    currentLock.copy(entryCount = currentLock.entryCount + 1)
                } else {
                    // Another API request has it, so leave it as is.
                    currentLock
                }
            })
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
                acquireOrIncrementLock(resourceIri, apiRequestID, tries - 1)
            } else {
                // No, we've run out of retries, so throw an exception.
                throw ApplicationLockException(s"Could not acquire update lock on resource $resourceIri within $MAX_LOCK_RETRY_MILLIS ms")
            }
        }
    }

    /**
      * Checks that the specified API request has a lock on the specified resource, then either decrements
      * the lock's entry count or releases the lock.
      *
      * @param resourceIri the IRI of the resource that is locked.
      * @param apiRequestID the ID of the API request that has the lock.
      */
    private def decrementOrReleaseLock(resourceIri: IRI, apiRequestID: UUID): Unit = {
        lockMap.compute(
            resourceIri,
            JavaFunctionUtil.biFunction({ (_, maybeCurrentLock) =>
                Option(maybeCurrentLock) match {
                    case Some(currentLock) =>
                        if (currentLock.apiRequestID == apiRequestID) {
                            // We have the lock.
                            if (currentLock.entryCount > 1) {
                                // Decrement its entry count.
                                currentLock.copy(entryCount = currentLock.entryCount - 1)
                            } else {
                                // Release it.
                                null
                            }
                        } else {
                            // Another API request has the lock. This shouldn't happen.
                            throw ApplicationLockException(s"API request $apiRequestID was supposed to have an update lock on resource $resourceIri, but API request ${currentLock.apiRequestID} has it")
                        }

                    case None =>
                        // The lock is unused. This shouldn't happen.
                        throw ApplicationLockException(s"API request $apiRequestID was supposed to have an update lock on resource $resourceIri, but the lock is unused")
                }
            })
        )
    }

    def dumpLockMap(): Unit = {
        println(lockMap)
    }
}
