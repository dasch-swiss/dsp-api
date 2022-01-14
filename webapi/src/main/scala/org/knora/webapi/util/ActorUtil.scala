/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import akka.actor.{ActorRef, Status}
import akka.event.LoggingAdapter
import akka.http.scaladsl.util.FastFuture
import akka.util.Timeout
import org.knora.webapi.exceptions.{
  ExceptionUtil,
  RequestRejectedException,
  UnexpectedMessageException,
  WrapperException
}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

object ActorUtil {

  /**
   * A convenience function that simplifies and centralises error-handling in the `receive` method of supervised Akka
   * actors that expect to receive messages sent using the `ask` pattern.
   *
   * Such an actor should handle errors by returning a [[Status.Failure]] message to the sender. If this is not done,
   * the sender's `ask` times out, and the sender has no way of finding out why. This function ensures that a
   * [[Status.Failure]] is sent.
   *
   * If an error occurs that isn't the client's fault, the actor will also want to report it to the actor's supervisor,
   * so the supervisor can carry out its own error-handling policy.
   *
   * It is also useful to give the sender the original exception object so that a stack trace can be
   * logged. However, some exception classes are not serializable and therefore cannot be sent as Akka messages. This
   * function ensures that stack traces are logged in those cases.
   *
   * The function takes as arguments the sender of the `ask` request, and a [[Future]] representing the result
   * of processing the request. It first converts the `Future` to a reply message, which it sends back to the sender.
   * If the `Future` succeeded, the reply message is the object it contains. If the `Future` failed, the reply message
   * is a [[Status.Failure]]. It will contain the original exception if it is serializable; otherwise, the original
   * exception is logged, and a [[WrapperException]] is sent instead. The [[WrapperException]] will contain the result
   * of calling `toString` on the original exception.
   *
   * After the reply message is sent, if the `Future` succeeded, or contained a [[RequestRejectedException]],
   * nothing more is done. If it contained any other exception, the function triggers the supervisor's error-handling
   * policy by throwing whatever exception was returned to the sender.
   *
   * @param sender the actor that made the request in the `ask` pattern.
   * @param future a [[Future]] that will provide the result of the sender's request.
   * @param log    a [[LoggingAdapter]] for logging non-serializable exceptions.
   */
  def future2Message[ReplyT](sender: ActorRef, future: Future[ReplyT], log: LoggingAdapter)(implicit
    executionContext: ExecutionContext
  ): Unit =
    future.onComplete { tryObj: Try[ReplyT] =>
      try2Message(
        sender = sender,
        tryObj = tryObj,
        log = log
      )
    }

  /**
   * Like `future2Message`, but takes a `Try` instead of a `Future`.
   *
   * @param sender the actor that made the request in the `ask` pattern.
   * @param tryObj a [[Try]] that will provide the result of the sender's request.
   * @param log    a [[LoggingAdapter]] for logging non-serializable exceptions.
   */
  def try2Message[ReplyT](sender: ActorRef, tryObj: Try[ReplyT], log: LoggingAdapter)(implicit
    executionContext: ExecutionContext
  ): Unit =
    tryObj match {
      case Success(result) => sender ! result

      case Failure(e) =>
        e match {
          case rejectedEx: RequestRejectedException =>
            // The error was the client's fault, so just tell the client.
            log.debug("future2Message - rejectedException: {}", rejectedEx)
            sender ! akka.actor.Status.Failure(rejectedEx)

          case otherEx: Exception =>
            // The error wasn't the client's fault. Log the exception, and also
            // let the client know.
            val exToReport = ExceptionUtil.logAndWrapIfNotSerializable(otherEx, log)
            log.debug("future2Message - otherException: {}", exToReport)
            sender ! akka.actor.Status.Failure(exToReport)
            throw exToReport

          case otherThrowable: Throwable =>
            // Don't try to recover from a Throwable that isn't an Exception.
            throw otherThrowable
        }
    }

  /**
   * An actor that expects to receive messages sent using the `ask` pattern can use this method to handle
   * unexpected request messages in a consistent way.
   *
   * @param sender  the actor that made the request in the `ask` pattern.
   * @param message the message that was received.
   * @param log     a [[LoggingAdapter]].
   */
  def handleUnexpectedMessage(sender: ActorRef, message: Any, log: LoggingAdapter, who: String)(implicit
    executionContext: ExecutionContext
  ): Unit = {
    val unexpectedMessageException = UnexpectedMessageException(
      s"$who received an unexpected message $message of type ${message.getClass.getCanonicalName}"
    )
    sender ! akka.actor.Status.Failure(unexpectedMessageException)
  }

  /**
   * Converts a [[Map]] containing futures of values into a future containing a [[Map]] of values.
   *
   * @param mapToSequence the [[Map]] to be converted.
   * @return a future that will provide the results of the futures that were in the [[Map]].
   */
  def sequenceFuturesInMap[KeyT: ClassTag, ValueT](
    mapToSequence: Map[KeyT, Future[ValueT]]
  )(implicit timeout: Timeout, executionContext: ExecutionContext): Future[Map[KeyT, ValueT]] =
    Future.sequence {
      mapToSequence.map { case (key: KeyT, futureValue: Future[ValueT]) =>
        futureValue.map { value =>
          key -> value
        }
      }
    }
      .map(_.toMap)

  /**
   * Converts a [[Map]] containing futures of sequences into a future containing a [[Map]] containing sequences.
   *
   * @param mapToSequence the [[Map]] to be converted.
   * @return a future that will provide the results of the futures that were in the [[Map]].
   */
  def sequenceFutureSeqsInMap[KeyT: ClassTag, ElemT](
    mapToSequence: Map[KeyT, Future[Seq[ElemT]]]
  )(implicit timeout: Timeout, executionContext: ExecutionContext): Future[Map[KeyT, Seq[ElemT]]] =
    // See http://stackoverflow.com/a/17479415
    Future.sequence {
      mapToSequence.map { case (key: KeyT, futureSeq: Future[Seq[ElemT]]) =>
        futureSeq.map { elements: Seq[ElemT] =>
          (key, elements)
        }
      }
    }
      .map(_.toMap)

  /**
   * Converts a [[Map]] containing sequences of futures into a future containing a [[Map]] containing sequences.
   *
   * @param mapToSequence the [[Map]] to be converted.
   * @return a future that will provide the results of the futures that were in the [[Map]].
   */
  def sequenceSeqFuturesInMap[KeyT: ClassTag, ElemT](
    mapToSequence: Map[KeyT, Seq[Future[ElemT]]]
  )(implicit timeout: Timeout, executionContext: ExecutionContext): Future[Map[KeyT, Seq[ElemT]]] = {
    val transformedMap: Map[KeyT, Future[Seq[ElemT]]] = mapToSequence.map {
      case (key: KeyT, seqFuture: Seq[Future[ElemT]]) => key -> Future.sequence(seqFuture)
    }

    sequenceFutureSeqsInMap(transformedMap)
  }

  /**
   * Converts an option containing a future to a future containing an option.
   *
   * @param optionFuture an option containing a future.
   * @return a future containing an option.
   */
  def optionFuture2FutureOption[A](
    optionFuture: Option[Future[A]]
  )(implicit executionContext: ExecutionContext): Future[Option[A]] =
    optionFuture match {
      case Some(f) => f.map(Some(_))
      case None    => Future.successful(None)
    }

  /**
   * Runs a sequence of tasks.
   *
   * @param firstTask the first task in the sequence.
   * @tparam T the type of the underlying task result.
   * @return the result of the last task in the sequence.
   */
  def runTasks[T](
    firstTask: Task[T]
  )(implicit timeout: Timeout, executionContext: ExecutionContext): Future[TaskResult[T]] =
    runTasksRec(previousResult = None, nextTask = firstTask)

  /**
   * Recursively runs a sequence of tasks.
   *
   * @param previousResult the previous result or `None` if this is the first task in the sequence.
   * @param nextTask       the next task to be run.
   * @tparam T the type of the underlying task result.
   * @return the result of the last task in the sequence.
   */
  private def runTasksRec[T](previousResult: Option[TaskResult[T]], nextTask: Task[T])(implicit
    timeout: Timeout,
    executionContext: ExecutionContext
  ): Future[TaskResult[T]] =
    // This function doesn't need to be tail recursive: https://stackoverflow.com/a/16986416
    for {
      taskResult: TaskResult[T] <- nextTask.runTask(previousResult)

      recResult: TaskResult[T] <- taskResult.nextTask match {
        case Some(definedNextTask) => runTasksRec(previousResult = Some(taskResult), nextTask = definedNextTask)
        case None                  => FastFuture.successful(taskResult)
      }
    } yield recResult
}

/**
 * Represents the result of a task in a sequence of tasks.
 *
 * @tparam T the type of the underlying task result.
 */
trait TaskResult[T] {

  /**
   * Returns the underlying result of this task.
   */
  def underlyingResult: T

  /**
   * Returns the next task, or `None` if this was the last task.
   */
  def nextTask: Option[Task[T]]
}

/**
 * Represents a task in a sequence of tasks.
 *
 * @tparam T the type of the underlying task result.
 */
trait Task[T] {

  /**
   * Runs the task.
   *
   * @param previousResult the result of the previous task, or `None` if this is the first task in the sequence.
   * @return the result of this task.
   */
  def runTask(
    previousResult: Option[TaskResult[T]]
  )(implicit timeout: Timeout, executionContext: ExecutionContext): Future[TaskResult[T]]
}
