/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import akka.actor.ActorRef
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import dsp.errors._
import zio._

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

object ActorUtil {

  /**
   * _Unsafely_ runs a ZIO workflow and sends the result to the `sender` actor as a message or a failure.
   * Used mainly during the refactoring phase, to be able to return ZIO inside an Actor.
   */
  def zio2Message[R, A](sender: ActorRef, zioTask: ZIO[R, Throwable, A])(implicit runtime: Runtime[R]): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run(
        zioTask.foldCause(cause => sender ! akka.actor.Status.Failure(cause.squash), success => sender ! success)
      )
    }

  /**
   * An actor that expects to receive messages sent using the `ask` pattern can use this method to handle
   * unexpected request messages in a consistent way.
   *
   * @param sender  the actor that made the request in the `ask` pattern.
   * @param message the message that was received.
   * @param log     a [[Logger]].
   */
  def handleUnexpectedMessage(sender: ActorRef, message: Any, log: Logger, who: String)(implicit
    executionContext: ExecutionContext
  ): Unit = {
    val unexpectedMessageException = UnexpectedMessageException(
      s"$who received an unexpected message $message of type ${message.getClass.getCanonicalName}"
    )
    sender ! akka.actor.Status.Failure(unexpectedMessageException)
  }

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
}

/**
 * Represents the result of a task in a sequence of tasks.
 */
trait ResultAndNext[T] {

  /**
   * Returns the underlying result of this task.
   */
  def result: T

  /**
   * Returns the next task, or `None` if this was the last task.
   */
  def next: Option[NextExecutionStep[T]]
}

/**
 * Represents a task in a sequence of tasks.
 */
trait NextExecutionStep[T] {

  /**
   * Runs the task.
   *
   * @param params the result of the previous task, or `None` if this is the first task in the sequence.
   * @return the result of this task.
   */
  def run(params: Option[ResultAndNext[T]]): Task[ResultAndNext[T]]
}
object NextExecutionStep {

  /**
   * Recursively runs a sequence of tasks.
   *
   * @param nextTask       the next task to be run.
   * @param previousResult the previous result or `None` if this is the first task in the sequence.
   * @return the result of the last task in the sequence.
   */
  def runSteps[T](
    nextTask: NextExecutionStep[T],
    previousResult: Option[ResultAndNext[T]] = None
  ): Task[ResultAndNext[T]] =
    for {
      taskResult <- nextTask.run(previousResult)
      recResult <-
        ZIO
          .fromOption(taskResult.next)
          .flatMap(runSteps(_, Some(taskResult)))
          .orElse(ZIO.succeed(taskResult))
    } yield recResult
}
