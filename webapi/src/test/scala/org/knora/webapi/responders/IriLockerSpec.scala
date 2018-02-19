package org.knora.webapi.responders

import java.util.UUID

import org.knora.webapi.{ApplicationLockException, IRI}
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * Tests [[IriLocker]].
  */
class IriLockerSpec extends WordSpec with Matchers {

    import scala.concurrent.ExecutionContext.Implicits.global

    val SUCCESS = "success"
    val FAILURE = "failure"

    "IriLocker" should {
        "not allow a request to acquire a lock when another request already has it" in {
            def runLongTask(): Future[String] = Future {
                Thread.sleep(4500)
                SUCCESS
            }

            def runShortTask(): Future[String] = Future(SUCCESS)

            val testIri: IRI = "http://example.org/test1"

            val firstApiRequestID = UUID.randomUUID

            IriLocker.runWithIriLock(
                apiRequestID = firstApiRequestID,
                iri = testIri,
                task = () => runLongTask()
            )

            // Wait a bit to allow the first request to get the lock.
            Thread.sleep(200)

            val secondApiRequestID = UUID.randomUUID

            val secondTaskResultFuture = IriLocker.runWithIriLock(
                apiRequestID = secondApiRequestID,
                iri = testIri,
                task = () => runShortTask()
            )

            val secondTaskFailedWithLockTimeout = try {
                Await.result(secondTaskResultFuture, 5.seconds)
                false
            } catch {
                case ale: ApplicationLockException => true
            }

            assert(secondTaskFailedWithLockTimeout, "Second task did not get a lock timeout")
        }

        "provide reentrant locks" in {
            def runRecursiveTask(iri: IRI, apiRequestID: UUID, count: Int): Future[String] = {
                if (count > 0) {
                    IriLocker.runWithIriLock(
                        apiRequestID = apiRequestID,
                        iri = iri,
                        task = () => runRecursiveTask(iri, apiRequestID, count - 1)
                    )
                } else {
                    Future(SUCCESS)
                }
            }

            val testIri: IRI = "http://example.org/test2"

            val firstApiRequestID = UUID.randomUUID
            val firstTestResult = Await.result(runRecursiveTask(testIri, firstApiRequestID, 3), 1.second)
            assert(firstTestResult == SUCCESS)
            val secondApiRequestID = UUID.randomUUID
            val secondTestResult = Await.result(runRecursiveTask(testIri, secondApiRequestID, 3), 1.second)
            assert(secondTestResult == SUCCESS)
        }

        "release a lock when a task returns a failed future" in {
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
                task = () => runTask(false)
            )

            val firstTaskFailed = try {
                Await.result(firstTaskResultFuture, 1.second)
                false
            } catch {
                case e: Exception => true
            }

            assert(firstTaskFailed, "First task did not fail")

            val secondApiRequestID = UUID.randomUUID

            val secondTaskResultFuture = IriLocker.runWithIriLock(
                apiRequestID = secondApiRequestID,
                iri = testIri,
                task = () => runTask(true)
            )

            val secondTaskResult = Await.result(secondTaskResultFuture, 1.second)

            assert(secondTaskResult == SUCCESS, "Second task did not succeed")
        }

        "release a lock when a task throws an exception instead of returning a future" in {
            // If succeed is true, returns a successful future, otherwise throws an exception.
            def runTask(succeed: Boolean): Future[String] = {
                if (succeed) {
                    Future(SUCCESS)
                } else {
                    throw new Exception(FAILURE)
                }
            }

            val testIri: IRI = "http://example.org/test4"

            val firstApiRequestID = UUID.randomUUID

            val firstTaskResultFuture = IriLocker.runWithIriLock(
                apiRequestID = firstApiRequestID,
                iri = testIri,
                task = () => runTask(false)
            )

            val firstTaskFailed = try {
                Await.result(firstTaskResultFuture, 1.second)
                false
            } catch {
                case e: Exception => true
            }

            assert(firstTaskFailed, "First task did not fail")

            val secondApiRequestID = UUID.randomUUID

            val secondTaskResultFuture = IriLocker.runWithIriLock(
                apiRequestID = secondApiRequestID,
                iri = testIri,
                task = () => runTask(true)
            )

            val secondTaskResult = Await.result(secondTaskResultFuture, 1.second)

            assert(secondTaskResult == SUCCESS, "Second task did not succeed")
        }
    }
}
