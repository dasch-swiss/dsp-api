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

package org.knora.webapi.routing

import akka.actor.ActorSelection
import akka.event.LoggingAdapter
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{RequestContext, RouteResult}
import akka.pattern._
import akka.util.Timeout
import com.github.jsonldjava.core.{JsonLdOptions, JsonLdProcessor}
import com.github.jsonldjava.utils.JsonUtils
import org.knora.webapi._
import org.knora.webapi.messages.v2.responder.{KnoraRequestV2, KnoraResponseV2}
import org.knora.webapi.util.JavaUtil
import org.knora.webapi.util.jsonld.JsonLDDocument

import scala.concurrent.{ExecutionContext, Future}

/**
  * Convenience methods for Knora routes.
  */
object RouteUtilV2 {

    /**
      * Sends a message to a responder and completes the HTTP request by returning the response as JSON.
      *
      * @param requestMessage   a future containing a [[KnoraRequestV2]] message that should be sent to the responder manager.
      * @param requestContext   the akka-http [[RequestContext]].
      * @param settings         the application's settings.
      * @param responderManager a reference to the responder manager.
      * @param log              a logging adapter.
      * @param responseSchema   the API schema that should be used in the response.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a [[Future]] containing a [[RouteResult]].
      */
    def runJsonRoute(requestMessage: KnoraRequestV2,
                     requestContext: RequestContext,
                     settings: SettingsImpl,
                     responderManager: ActorSelection,
                     log: LoggingAdapter,
                     responseSchema: ApiV2Schema = ApiV2WithValueObjects)
                    (implicit timeout: Timeout, executionContext: ExecutionContext): Future[RouteResult] = {
        // Optionally log the request message. TODO: move this to the testing framework.
        if (settings.dumpMessages) {
            log.debug(requestMessage.toString)
        }

        val httpResponse: Future[HttpResponse] = for {
            // Make sure the responder sent a reply of type KnoraResponseV2.
            knoraResponse <- (responderManager ? requestMessage).map {
                case replyMessage: KnoraResponseV2 => replyMessage

                case other =>
                    // The responder returned an unexpected message type (not an exception). This isn't the client's
                    // fault, so log it and return an error message to the client.
                    throw UnexpectedMessageException(s"Responder sent a reply of type ${other.getClass.getCanonicalName}")
            }

            // Optionally log the reply message. TODO: move this to the testing framework.
            _ = if (settings.dumpMessages) {
                log.debug(knoraResponse.toString)
            }

            // TODO: check whether to send back JSON-LD or XML (content negotiation: HTTP accept header)

            // The request was successful
            jsonLDDocument: JsonLDDocument = knoraResponse.toJsonLDDocument(responseSchema, settings)
            contextAsJava = JavaUtil.deepScalaToJava(jsonLDDocument.context.toAny)
            jsonAsJava = JavaUtil.deepScalaToJava(jsonLDDocument.body.toAny)
            compacted = JsonLdProcessor.compact(jsonAsJava, contextAsJava, new JsonLdOptions())
            jsonLDString = JsonUtils.toPrettyString(compacted)
        } yield HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
                ContentTypes.`application/json`,
                jsonLDString
            )
        )

        requestContext.complete(httpResponse)
    }

}