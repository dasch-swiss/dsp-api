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
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi._
import org.knora.webapi.messages.v1respondermessages.{ApiStatusCodesV1, KnoraRequestV1, KnoraResponseV1}
import org.knora.webapi.util.MessageUtil
import spray.http._
import spray.json.{JsNumber, JsObject, JsString, JsValue}
import spray.routing.RequestContext

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/**
  * Convenience methods for Knora routes.
  */
object RouteUtilV1 {
    // A generic error message that we return to clients when an internal server error occurs.
    private val GENERIC_INTERNAL_SERVER_ERROR_MESSAGE = "The request could not be completed because of an internal server error."

    /**
      * Runs an API routing function.
      * @param requestMessageTry a [[Try]] which, if successful, contains a [[KnoraRequestV1]] that should be
      *                          sent to the responder manager, and if not successful, contains an error that will be
      *                          reported to the client.
      * @param requestContext the spray [[RequestContext]].
      * @param settings the application's settings.
      * @param responderManager a reference to the responder manager.
      * @param log a logging adapter.
      * @param timeout a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      */
    def runJsonRoute[RequestMessageT <: KnoraRequestV1](requestMessageTry: Try[RequestMessageT],
                                                        requestContext: RequestContext,
                                                        settings: SettingsImpl,
                                                        responderManager: ActorSelection,
                                                        log: LoggingAdapter)
                                                       (implicit timeout: Timeout, executionContext: ExecutionContext): Unit = {
        // Check whether a request message was successfully generated.
        requestMessageTry match {
            case Success(requestMessage) =>
                // Optionally log the request message. TODO: move this to the testing framework.
                if (settings.dumpMessages) {
                    log.debug(MessageUtil.toSource(requestMessage))
                }

                val resultFuture: Future[KnoraResponseV1] = for {
                // Make sure the responder sent a reply of type KnoraResponseV1.
                    knoraResponse <- (responderManager ? requestMessage).map {
                        case replyMessage: KnoraResponseV1 => replyMessage

                        case other =>
                            // The responder returned an unexpected message type. This isn't the client's fault, so log
                            // it and return an error message to the client.
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

                resultFuture.onComplete {
                    resultTry => requestContext.complete(replyMessageTry2JsonHttpResponse(resultTry, settings, log))
                }

            case Failure(ex) =>
                // Was the error in generating the request message the client's fault?
                ex match {
                    case rre: RequestRejectedException =>
                        // Yes, just tell the client.
                        requestContext.complete(exceptionToJsonHttpResponse(rre, settings))

                    case other =>
                        // No: log the exception and notify the client.
                        log.error(ex, "Unable to run route")
                        requestContext.complete(exceptionToJsonHttpResponse(other, settings))
                }

        }
    }

    /**
      * Runs an API routing function that returns HTML.
      * @tparam RequestMessageT the type of request message to be sent to the responder.
      * @tparam ReplyMessageT the type of reply message expected from the responder.
      * @param requestMessageTry a [[Try]] containing, if successful, the message that should be sent to the responder manager.
      *                          Any exceptions thrown will be reported to the client.
      * @param viewHandler a function that can generate HTML from the responder's reply message.
      * @param requestContext the spray [[RequestContext]].
      * @param settings the application's settings.
      * @param responderManager a reference to the responder manager.
      * @param log a logging adapter.
      * @param timeout a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      */
    def runHtmlRoute[RequestMessageT <: KnoraRequestV1, ReplyMessageT <: KnoraResponseV1 : ClassTag](requestMessageTry: Try[RequestMessageT],
                                                                                                     viewHandler: (ReplyMessageT, ActorSelection) => String,
                                                                                                     requestContext: RequestContext,
                                                                                                     settings: SettingsImpl,
                                                                                                     responderManager: ActorSelection,
                                                                                                     log: LoggingAdapter)
                                                                                                    (implicit timeout: Timeout, executionContext: ExecutionContext): Unit = {
        // Check whether a request message was successfully generated.
        requestMessageTry match {
            case Success(requestMessage) =>
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
                            val logErrorMsg = s"Responder sent a reply of type ${other.getClass.getCanonicalName}"
                            val unexpectedEx = UnexpectedMessageException(logErrorMsg)
                            log.error(unexpectedEx, logErrorMsg)
                            throw unexpectedEx
                    }

                    // Optionally log the reply message. TODO: move this to the testing framework.
                    _ = if (settings.dumpMessages) {
                        log.debug(MessageUtil.toSource(knoraResponse))
                    }

                } yield knoraResponse

                resultFuture.onComplete {
                    resultTry => requestContext.complete(replyMessageTry2HtmlHttpResponse[ReplyMessageT](resultTry, viewHandler, settings, log, responderManager))
                }

            case Failure(ex) =>
                // Was the error in generating the request message the client's fault?
                ex match {
                    case rre: RequestRejectedException =>
                        // Yes, just tell the client.
                        requestContext.complete(exceptionToHtmlHttpResponse(ex, settings))

                    case _ =>
                        // No: log the exception and notify the client.
                        log.error(ex, "Unable to run route")
                        requestContext.complete(exceptionToHtmlHttpResponse(ex, settings))
                }
        }
    }

    /**
      * Given a [[Try]] containing the result of processing an API request, checks whether the operation was successful and contains
      * a [[KnoraResponseV1]]. If so, returns an [[HttpResponse]] containing the JSON representation of the result of
      * the operation. If the operation was unsuccessful, returns an [[HttpResponse]] containing the error message from
      * the [[Try]]. The HTTP responses returned by this method contain appropriate HTTP status codes.
      * @param resultTry a [[Try]] containing the result of an operation performed by an Actor.
      * @return an [[HttpResponse]] containing a JSON representation of the result.
      */
    private def replyMessageTry2JsonHttpResponse(resultTry: Try[KnoraResponseV1], settings: SettingsImpl, log: LoggingAdapter): HttpResponse = {
        resultTry match {
            case Success(jsonResponse) =>
                Try {
                    // The request was successful, so add a status of ApiStatusCodesV1.OK to the response.
                    val jsonResponseWithStatus = JsObject(jsonResponse.toJsValue.asJsObject.fields + ("status" -> JsNumber(ApiStatusCodesV1.OK.id)))

                    // Convert the response message to an HTTP response in JSON format.
                    HttpResponse(
                        status = StatusCodes.OK,
                        entity = HttpEntity(
                            ContentType(MediaTypes.`application/json`, HttpCharsets.`UTF-8`),
                            jsonResponseWithStatus.compactPrint
                        )
                    )
                } match {
                    case Success(httpResponse) => httpResponse

                    case Failure(ex) =>
                        // The conversion to JSON failed. Log the error and notify the client.
                        log.error(ex, "Unable to convert responder's reply to JSON")
                        exceptionToJsonHttpResponse(ex, settings)
                }

            case Failure(ex) =>
                // The responder sent back an exception. Convert it to an HTTP response. We assume that it has already
                // been logged by the responder, if appropriate.
                exceptionToJsonHttpResponse(ex, settings)
        }
    }

    /**
      * Given a [[Try]] containing the result of processing an API request, checks whether the operation was successful and contains
      * a [[KnoraResponseV1]]. If so, returns an [[HttpResponse]] containing an HTML representation of the result of
      * the operation. If the operation was unsuccessful, returns an [[HttpResponse]] containing the error message from
      * the [[Try]]. The HTTP responses returned by this method contain appropriate HTTP status codes.
      * @tparam ReplyMessageT the type of reply message expected.
      * @param resultTry a [[Try]] containing the result of an operation performed by an Actor.
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
                            ContentType(MediaTypes.`text/html`, HttpCharsets.`UTF-8`),
                            viewHandler(responderReply, responderManager)
                        )
                    )
                } match {
                    case Success(httpResponse) => httpResponse

                    case Failure(ex) =>
                        // The conversion to JSON failed. Log the error and notify the client.
                        log.error(ex, "Unable to convert responder's reply to JSON")
                        exceptionToHtmlHttpResponse(ex, settings)
                }

            case Failure(ex) =>
                // The responder sent back an exception. Convert it to an HTTP response. We assume that it has already
                // been logged by the responder, if appropriate.
                exceptionToHtmlHttpResponse(ex, settings)
        }
    }

    /**
      * Converts an exception to an HTTP response in JSON format.
      * @param ex the exception to be converted.
      * @return an [[HttpResponse]] in JSON format.
      */
    private def exceptionToJsonHttpResponse(ex: Throwable, settings: SettingsImpl): HttpResponse = {
        // Get the API status code that corresponds to the exception.
        val apiStatus: ApiStatusCodesV1.Value = ApiStatusCodesV1.fromException(ex)

        // Convert the API status code to the corresponding HTTP status code.
        val httpStatus: StatusCode = ApiStatusCodesV1.toHttpStatus(apiStatus)

        // Generate an HTTP response containing the error message, the API status code, and the HTTP status code.

        val maybeAccess: Option[(String, JsValue)] = if (apiStatus == ApiStatusCodesV1.NO_RIGHTS_FOR_OPERATION) {
            Some("access" -> JsString("NO_ACCESS"))
        } else {
            None
        }

        val responseFields: Map[String, JsValue] = Map(
            "status" -> JsNumber(apiStatus.id),
            "error" -> JsString(makeClientErrorMessage(ex, settings))
        ) ++ maybeAccess

        HttpResponse(
            status = httpStatus,
            entity = HttpEntity(ContentType(MediaTypes.`application/json`, HttpCharsets.`UTF-8`), JsObject(responseFields).compactPrint)
        )
    }

    /**
      * Converts an exception to an HTTP response in HTML format.
      * @param ex the exception to be converted.
      * @return an [[HttpResponse]] in HTML format.
      */
    private def exceptionToHtmlHttpResponse(ex: Throwable, settings: SettingsImpl): HttpResponse = {
        // Get the API status code that corresponds to the exception.
        val apiStatus: ApiStatusCodesV1.Value = ApiStatusCodesV1.fromException(ex)

        // Convert the API status code to the corresponding HTTP status code.
        val httpStatus: StatusCode = ApiStatusCodesV1.toHttpStatus(apiStatus)

        // Generate an HTTP response containing the error message, the API status code, and the HTTP status code.
        HttpResponse(
            status = httpStatus,
            entity = HttpEntity(
                MediaTypes.`application/xml`,
                <html xmlns="http://www.w3.org/1999/xhtml">
                    <head>
                        <title>Error</title>
                    </head>
                    <body>
                        <h2>Error</h2>
                        <p>
                            <code>
                                {makeClientErrorMessage(ex, settings)}
                            </code>
                        </p>
                        <h2>Status code</h2>
                        <p>
                            <code>
                                {apiStatus.id}
                            </code>
                        </p>
                    </body>
                </html>.toString()
            )
        )
    }

    /**
      * Given an exception, returns an error message suitable for clients.
      * @param ex the exception.
      * @param settings the application settings.
      * @return an error message suitable for clients.
      */
    private def makeClientErrorMessage(ex: Throwable, settings: SettingsImpl): String = {
        ex match {
            case rre: RequestRejectedException => rre.toString

            case other =>
                if (settings.showInternalErrors) {
                    other.toString
                } else {
                    GENERIC_INTERNAL_SERVER_ERROR_MESSAGE
                }
        }
    }
}
