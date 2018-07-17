/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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
import org.knora.webapi.http.ApiStatusCodesV1
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import org.knora.webapi.messages.v2.responder.standoffmessages.{GetMappingRequestV2, GetMappingResponseV2}
import org.knora.webapi.util.standoff.StandoffTagUtilV2
import org.knora.webapi.util.standoff.StandoffTagUtilV2.TextWithStandoffTagsV2
import spray.json.{JsNumber, JsObject}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

/**
  * Convenience methods for Knora routes.
  */
object RouteUtilV1 {

    /**
      * Sends a message to a responder and completes the HTTP request by returning the response as JSON.
      *
      * @param requestMessage   a [[KnoraRequestV1]] message that should be sent to the responder manager.
      * @param requestContext   the akka-http [[RequestContext]].
      * @param settings         the application's settings.
      * @param responderManager a reference to the responder manager.
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a [[Future]] containing a [[RouteResult]].
      */
    def runJsonRoute(requestMessage: KnoraRequestV1,
                     requestContext: RequestContext,
                     settings: SettingsImpl,
                     responderManager: ActorSelection,
                     log: LoggingAdapter)
                    (implicit timeout: Timeout, executionContext: ExecutionContext): Future[RouteResult] = {
        // Optionally log the request message. TODO: move this to the testing framework.
        if (settings.dumpMessages) {
            log.debug(requestMessage.toString)
        }

        val httpResponse: Future[HttpResponse] = for {
            // Make sure the responder sent a reply of type KnoraResponseV1.
            knoraResponse <- (responderManager ? requestMessage).map {
                case replyMessage: KnoraResponseV1 => replyMessage

                case other =>
                    // The responder returned an unexpected message type (not an exception). This isn't the client's
                    // fault, so log it and return an error message to the client.
                    throw UnexpectedMessageException(s"Responder sent a reply of type ${other.getClass.getCanonicalName}")
            }

            // Optionally log the reply message. TODO: move this to the testing framework.
            _ = if (settings.dumpMessages) {
                log.debug(knoraResponse.toString)
            }

            // The request was successful, so add a status of ApiStatusCodesV1.OK to the response.
            jsonResponseWithStatus = JsObject(knoraResponse.toJsValue.asJsObject.fields + ("status" -> JsNumber(ApiStatusCodesV1.OK.id)))

        } yield HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
                ContentTypes.`application/json`,
                jsonResponseWithStatus.compactPrint
            )
        )

        requestContext.complete(httpResponse)
    }

    /**
      * Sends a message (resulting from a [[Future]]) to a responder and completes the HTTP request by returning the response as JSON.
      *
      * @param requestMessageF  a [[Future]] containing a [[KnoraRequestV1]] message that should be sent to the responder manager.
      * @param requestContext   the akka-http [[RequestContext]].
      * @param settings         the application's settings.
      * @param responderManager a reference to the responder manager.
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a [[Future]] containing a [[RouteResult]].
      */
    def runJsonRouteWithFuture[RequestMessageT <: KnoraRequestV1](requestMessageF: Future[RequestMessageT],
                                                                  requestContext: RequestContext,
                                                                  settings: SettingsImpl,
                                                                  responderManager: ActorSelection,
                                                                  log: LoggingAdapter)
                                                                 (implicit timeout: Timeout, executionContext: ExecutionContext): Future[RouteResult] = {
        for {
            requestMessage <- requestMessageF
            routeResult <- runJsonRoute(
                requestMessage = requestMessage,
                requestContext = requestContext,
                settings = settings,
                responderManager = responderManager,
                log = log
            )
        } yield routeResult
    }

    /**
      * Sends a message to a responder and completes the HTTP request by returning the response as HTML.
      *
      * @tparam RequestMessageT the type of request message to be sent to the responder.
      * @tparam ReplyMessageT   the type of reply message expected from the responder.
      * @param requestMessageF  a [[Future]] containing the message that should be sent to the responder manager.
      * @param viewHandler      a function that can generate HTML from the responder's reply message.
      * @param requestContext   the [[RequestContext]].
      * @param settings         the application's settings.
      * @param responderManager a reference to the responder manager.
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      */
    def runHtmlRoute[RequestMessageT <: KnoraRequestV1, ReplyMessageT <: KnoraResponseV1 : ClassTag](requestMessageF: Future[RequestMessageT],
                                                                                                     viewHandler: (ReplyMessageT, ActorSelection) => String,
                                                                                                     requestContext: RequestContext,
                                                                                                     settings: SettingsImpl,
                                                                                                     responderManager: ActorSelection,
                                                                                                     log: LoggingAdapter)
                                                                                                    (implicit timeout: Timeout, executionContext: ExecutionContext): Future[RouteResult] = {

        val httpResponse: Future[HttpResponse] = for {

            requestMessage <- requestMessageF

            // Optionally log the request message. TODO: move this to the testing framework.
            _ = if (settings.dumpMessages) {
                log.debug(requestMessage.toString)
            }

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
                log.debug(knoraResponse.toString)
            }

        } yield HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
                ContentTypes.`text/html(UTF-8)`,
                viewHandler(knoraResponse, responderManager)
            )
        )

        requestContext.complete(httpResponse)
    }

    /**
      *
      * Converts XML to a [[TextWithStandoffTagsV2]], representing the text and its standoff markup.
      *
      * @param xml                            the given XML to be converted to standoff.
      * @param mappingIri                     the mapping to be used to convert the XML to standoff.
      * @param acceptStandoffLinksToClientIDs if `true`, allow standoff link tags to use the client's IDs for target
      *                                       resources. In a bulk import, this allows standoff links to resources
      *                                       that are to be created by the import.
      * @param userProfile                    the user making the request.
      * @param settings                       the application's settings.
      * @param responderManager               a reference to the responder manager.
      * @param log                            a logging adapter.
      * @param timeout                        a timeout for `ask` messages.
      * @param executionContext               an execution context for futures.
      * @return a [[TextWithStandoffTagsV2]].
      */
    def convertXMLtoStandoffTagV1(xml: String,
                                  mappingIri: IRI,
                                  acceptStandoffLinksToClientIDs: Boolean,
                                  userProfile: UserADM,
                                  settings: SettingsImpl,
                                  responderManager: ActorSelection,
                                  log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[TextWithStandoffTagsV2] = {

        for {

            // get the mapping directly from v2 responder directly (to avoid useless back and forth conversions between v2 and v1 message formats)
            mappingResponse: GetMappingResponseV2 <- (responderManager ? GetMappingRequestV2(mappingIri = mappingIri, userProfile = userProfile)).mapTo[GetMappingResponseV2]

            textWithStandoffTagV1 = StandoffTagUtilV2.convertXMLtoStandoffTagV2(
                xml = xml,
                mapping = mappingResponse,
                acceptStandoffLinksToClientIDs = acceptStandoffLinksToClientIDs,
                log = log
            )

        } yield textWithStandoffTagV1
    }
}
