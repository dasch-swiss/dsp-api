/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

package org.knora.webapi.util

import akka.actor.{ActorRef, Status}
import akka.event.LoggingAdapter
import akka.util.Timeout
import org.knora.webapi._

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

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
    def future2Message[ReplyT](sender: ActorRef, future: Future[ReplyT], log: LoggingAdapter)(implicit executionContext: ExecutionContext): Unit = {
        future.onComplete {
            case Success(result) => sender ! result

            case Failure(e) => e match {
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
    }

    /**
      * An actor that expects to receive messages sent using the `ask` pattern can use this method to handle
      * unexpected request messages in a consistent way.
      *
      * @param sender  the actor that made the request in the `ask` pattern.
      * @param message the message that was received.
      * @param log     a [[LoggingAdapter]].
      */
    def handleUnexpectedMessage(sender: ActorRef, message: Any, log: LoggingAdapter, who: String)(implicit executionContext: ExecutionContext): Unit = {
        val unexpectedMessageException = UnexpectedMessageException(s"$who received an unexpected message $message of type ${message.getClass.getCanonicalName}")
        sender ! akka.actor.Status.Failure(unexpectedMessageException)
    }

    /**
      * Convert a [[Map]] containing futures of sequences into a future containing a [[Map]] containing sequences.
      *
      * @param mapToSequence the [[Map]] to be converted.
      * @return a future that will provide the results of the futures that were in the [[Map]].
      */
    def sequenceFuturesInMap[KeyT: ClassTag, ElemT](mapToSequence: Map[KeyT, Future[Seq[ElemT]]])(implicit timeout: Timeout, executionContext: ExecutionContext): Future[Map[KeyT, Seq[ElemT]]] = {
        // See http://stackoverflow.com/a/17479415
        Future.sequence {
            mapToSequence.map {
                case (propertyIri: KeyT, responseFutures: Future[Seq[ElemT]]) =>
                    responseFutures.map {
                        responses: Seq[ElemT] => (propertyIri, responses)
                    }
            }
        }.map(_.toMap)
    }
}
