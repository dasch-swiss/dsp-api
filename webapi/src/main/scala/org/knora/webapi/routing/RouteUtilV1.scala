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

package org.knora.webapi.routing

import akka.actor.ActorSelection
import akka.event.LoggingAdapter
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{RequestContext, RouteResult}
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.{ApiStatusCodesV1, KnoraRequestV1, KnoraResponseV1}
import org.knora.webapi.util.MessageUtil
import spray.json.{JsNumber, JsObject}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/**
  * Convenience methods for Knora routes.
  */
object RouteUtilV1 {

    /**
      * Runs an API routing function.
      *
      * @param requestMessage    a [[KnoraRequestV1]] message that should be sent to the responder manager.
      * @param requestContext    the akka-http [[RequestContext]].
      * @param settings          the application's settings.
      * @param responderManager  a reference to the responder manager.
      * @param log               a logging adapter.
      * @param timeout           a timeout for `ask` messages.
      * @param executionContext  an execution context for futures.
      */
    def runJsonRoute[RequestMessageT <: KnoraRequestV1](requestMessage: RequestMessageT,
                                                        requestContext: RequestContext,
                                                        settings: SettingsImpl,
                                                        responderManager: ActorSelection,
                                                        log: LoggingAdapter)
                                                       (implicit timeout: Timeout, executionContext: ExecutionContext): Future[RouteResult] = {

        // Optionally log the request message. TODO: move this to the testing framework.
        if (settings.dumpMessages) {
            log.debug(MessageUtil.toSource(requestMessage))
        }

        val resultFuture: Future[KnoraResponseV1] = for {
        // Make sure the responder sent a reply of type KnoraResponseV1.
            knoraResponse <- (responderManager ? requestMessage).map {
                case replyMessage: KnoraResponseV1 =>
                    replyMessage
                case other =>
                    // The responder returned an unexpected message type (not an exception). This isn't the client's
                    // fault, so log it and return an error message to the client.
                    val logErrorMsg = s"Responder sent a reply of type ${other.getClass.getCanonicalName}"
                    val logEx = UnexpectedMessageException(logErrorMsg)
                    log.error(logEx, logErrorMsg)
                    throw logEx
            }

            // Optionally log the reply message. TODO: move this to the testing framework.
            _ = if (settings.dumpMessages) {
                log.debug(MessageUtil.toSource(knoraResponse))
            }

        } yield knoraResponse

        requestContext.complete(replyMessage2JsonHttpResponse(resultFuture, settings, log))
    }

    /**
      * Runs an API routing function that returns HTML.
      *
      * @tparam RequestMessageT the type of request message to be sent to the responder.
      * @tparam ReplyMessageT   the type of reply message expected from the responder.
      * @param requestMessage   the message that should be sent to the responder manager.
      * @param viewHandler       a function that can generate HTML from the responder's reply message.
      * @param requestContext    the spray [[RequestContext]].
      * @param settings          the application's settings.
      * @param responderManager  a reference to the responder manager.
      * @param log               a logging adapter.
      * @param timeout           a timeout for `ask` messages.
      * @param executionContext  an execution context for futures.
      */
    def runHtmlRoute[RequestMessageT <: KnoraRequestV1, ReplyMessageT <: KnoraResponseV1 : ClassTag](requestMessage: RequestMessageT,
                                                                                                     viewHandler: (ReplyMessageT, ActorSelection) => String,
                                                                                                     requestContext: RequestContext,
                                                                                                     settings: SettingsImpl,
                                                                                                     responderManager: ActorSelection,
                                                                                                     log: LoggingAdapter)
                                                                                                    (implicit timeout: Timeout, executionContext: ExecutionContext): Future[RouteResult] = {

        // Optionally log the request message. TODO: move this to the testing framework.
        if (settings.dumpMessages) {
            log.debug(MessageUtil.toSource(requestMessage))
        }

        val resultFuture: Future[ReplyMessageT] = for {
        // Make sure the responder sent a reply of type ReplyMessageT.
            knoraResponse <- (responderManager ? requestMessage).map {
                case replyMessage: ReplyMessageT => replyMessage

                case other =>
                    // The responder returned an unexpected message type. This isn't the client's fault, so
                    // log the error and notify the client.
                    val msg = s"Responder sent a reply of type ${other.getClass.getCanonicalName}"
                    throw UnexpectedMessageException(msg)
            }

            // Optionally log the reply message. TODO: move this to the testing framework.
            _ = if (settings.dumpMessages) {
                log.debug(MessageUtil.toSource(knoraResponse))
            }

        } yield knoraResponse

        requestContext.complete(replyMessage2JsonHttpResponse(resultFuture, settings, log))
    }

    /**
      * Given a [[Try]] containing the result of processing an API request, checks whether the operation was successful and contains
      * a [[KnoraResponseV1]]. If so, returns an [[HttpResponse]] containing the JSON representation of the result of
      * the operation. If the operation was unsuccessful, returns an [[HttpResponse]] containing the error message from
      * the [[Try]]. The HTTP responses returned by this method contain appropriate HTTP status codes.
      *
      * @param resultFuture a [[Future]] containing the result of an operation performed by an Actor.
      * @return an [[HttpResponse]] containing a JSON representation of the result.
      */
    private def replyMessage2JsonHttpResponse(resultFuture: Future[KnoraResponseV1], settings: SettingsImpl, log: LoggingAdapter): Future[HttpResponse] = for {
        jsonResponse: KnoraResponseV1 <- resultFuture

        // The request was successful, so add a status of ApiStatusCodesV1.OK to the response.
        jsonResponseWithStatus = JsObject(jsonResponse.toJsValue.asJsObject.fields + ("status" -> JsNumber(ApiStatusCodesV1.OK.id)))

        // Convert the response message to an HTTP response in JSON format.
        response = HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
                ContentTypes.`application/json`,
                jsonResponseWithStatus.compactPrint
            )
        )

    } yield response

    /**
      * Given a [[Try]] containing the result of processing an API request, checks whether the operation was successful and contains
      * a [[KnoraResponseV1]]. If so, returns an [[HttpResponse]] containing an HTML representation of the result of
      * the operation. If the operation was unsuccessful, returns an [[HttpResponse]] containing the error message from
      * the [[Try]]. The HTTP responses returned by this method contain appropriate HTTP status codes.
      *
      * @tparam ReplyMessageT the type of reply message expected.
      * @param resultTry   a [[Try]] containing the result of an operation performed by an Actor.
      * @param viewHandler a function that can generate HTML from the responder's reply message.
      * @return an [[HttpResponse]] containing a JSON representation of the result.
      */
    private def replyMessageTry2HtmlHttpResponse[ReplyMessageT <: KnoraResponseV1 : ClassTag](resultTry: Try[ReplyMessageT],
                                                                                              viewHandler: (ReplyMessageT, ActorSelection) => String,
                                                                                              settings: SettingsImpl,
                                                                                              log: LoggingAdapter,
                                                                                              responderManager: ActorSelection): HttpResponse = {
        resultTry match {
            case Success(responderReply) =>
                Try {
                    // Convert the response message to HTML through the viewHandler and create an HTTP response in HTML format.
                    HttpResponse(
                        status = StatusCodes.OK,
                        entity = HttpEntity(
                            ContentTypes.`text/html(UTF-8)`,
                            viewHandler(responderReply, responderManager)
                        )
                    )
                } match {
                    case Success(httpResponse) => httpResponse

                    case Failure(ex) =>
                        // The conversion to JSON failed. Log the error and notify the client.
                        log.error(ex, "Unable to convert responder's reply to JSON")

                        // FIXME: Remove match and throw exception earlier
                        //exceptionToHtmlHttpResponse(ex, settings)
                        throw ex
                }

            case Failure(ex) =>
                // The responder sent back an exception. Convert it to an HTTP response. We assume that it has already
                // been logged by the responder, if appropriate.

                // FIXME: Remove match and throw exception earlier
                // exceptionToHtmlHttpResponse(ex, settings)
                throw ex
        }
    }
}
