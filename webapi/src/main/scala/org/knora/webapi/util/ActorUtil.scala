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

package org.knora.webapi.util

import akka.actor.{ActorRef, ActorSelection, Status}
import akka.event.LoggingAdapter
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi._

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

object ActorUtil {

    /**
      * Sends a list of messages in parallel to an actor, and returns a [[Future]] containing a list
      * of the results. If any of the requests fails, the [[Future]] will contain an exception representing
      * the first failure.
      *
      * @param addressee   the actor to send the messages to.
      * @param reqMessages the messages to send.
      * @tparam ReplyT the type of value expected as a result. This type must be specified explicitly by the caller, because the
      *                Scala compiler can't infer it.
      * @return A [[Future]] containing either a list of the results or an exception.
      */
    def parallelAsk[ReplyT: ClassTag](addressee: ActorSelection, reqMessages: Seq[Any])(implicit timeout: Timeout, executionContext: ExecutionContext): Future[Seq[ReplyT]] = {
        Future.sequence(reqMessages.map(reqMsg => (addressee ? reqMsg).mapTo[ReplyT]))
    }

    /**
      * A convenience function that simplifies error-handling in the `receive` method of a supervised Akka actor that uses
      * the `ask` pattern. Such an actor will want to respond to client errors by returning an [[Status.Failure]]
      * to the sender to indicate that the request could not be fulfilled. If a more serious error occurs, the actor will want
      * to report it both to the sender and to the actor's supervisor, so the supervisor can carry out its own error-handling
      * policy.
      *
      * When reporting errors, it is also useful to provide the original exception object so that a stack trace can be
      * logged. However, some exception classes are not serializable and therefore cannot be sent as Akka messages. This
      * function ensures that stack traces are logged in those cases.
      *
      * The function takes as arguments the sender of the `ask` request, and a [[scala.util.Try]] representing the result
      * of processing the request. It first converts the `Try` to a reply message, which it sends back to the sender.
      * If the `Try` is a [[scala.util.Success]], the reply message is the object contained in the `Success`. If the `Try` is a
      * [[scala.util.Failure]], the reply message is an a [[Status.Failure]] containing an exception. This will be the original
      * exception if it is serializable; otherwise, the original exception is logged, and a [[WrapperException]] is sent
      * instead. The [[WrapperException]] will contain the result of calling `toString` on the original exception.
      *
      * After the reply message is sent, the following rule is applied: if the `Try` was a `Success`, or a `Failure`
      * containing a [[RequestRejectedException]], nothing more is done. If it was a `Failure` containing anything else,
      * the function triggers the supervisor's error-handling policy by throwing whatever exception was returned to the
      * sender.
      *
      * @param sender the actor that made the request in the `ask` pattern.
      * @param tryObj either a [[Success]] containing a response message, or a [[scala.util.Failure]] containing an exception.
      * @param log    a [[LoggingAdapter]] for logging non-serializable exceptions.
      */
    def try2Message[ReplyT](sender: ActorRef, tryObj: Try[ReplyT], log: LoggingAdapter): Unit = {
        tryObj match {
            case Success(result) => sender ! result
            case Failure(e) => e match {
                case rejectedEx: RequestRejectedException =>
                    sender ! akka.actor.Status.Failure(rejectedEx)

                case otherEx: Throwable =>
                    val exToReport = ExceptionUtil.logAndWrapIfNotSerializable(otherEx, log)
                    sender ! akka.actor.Status.Failure(exToReport)
                    throw exToReport
            }
        }
    }

    /**
      * A wrapper around `try2Message` that takes a [[Future]] instead of a [[Try]], and calls `try2Message` when the future
      * completes.
      *
      * @param sender the actor that made the request in the `ask` pattern.
      * @param future a [[Future]] that will provide the result of the sender's request.
      * @param log    a [[LoggingAdapter]] for logging non-serializable exceptions.
      */
    def future2Message[ReplyT](sender: ActorRef, future: Future[ReplyT], log: LoggingAdapter)(implicit executionContext: ExecutionContext): Unit = {
        future.onComplete {
            tryObj => try2Message(sender, tryObj, log)
        }
    }

    /**
      * Converts `None` values into `Failure`s, and facilitates using the `Try` monad to check
      * for `None`.
      *
      * @param optionTry         A `Try` containing an `Option`.
      * @param notFoundException An exception that should be returned in a `Failure` if the `Option` is a `None`.
      * @tparam T the type contained in the `Option` if it is a `Some`.
      * @return a `Try` containing either a `Success` (if the option contained a `Some`) or a `Failure` (if the option contained a `None`).
      */
    def option2Try[T](optionTry: Try[Option[T]], notFoundException: NotFoundException): Try[T] = {
        optionTry match {
            case Success(Some(v)) => Success(v)
            case Success(None) => Failure(notFoundException)
            case Failure(e) => Failure(e)
        }
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
