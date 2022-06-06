/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.actor.ActorRef
import com.typesafe.scalalogging.Logger
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.RouteResult
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi.exceptions.UnexpectedMessageException
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.ResponderRequest.KnoraRequestADM
import org.knora.webapi.messages.admin.responder.KnoraResponseADM
import org.knora.webapi.responders.ResponderManager
import org.knora.webapi.settings.KnoraSettingsImpl

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/**
 * Convenience methods for Knora Admin routes.
 */
object RouteUtilADM {

  /**
   * Sends a message to a responder and completes the HTTP request by returning the response as JSON.
   *
   * @param requestMessageF      a future containing a [[KnoraRequestADM]] message that should be sent to the responder manager.
   * @param requestContext       the akka-http [[RequestContext]].
   * @param featureFactoryConfig the per-request feature factory configuration.
   * @param settings             the application's settings.
   * @param appActor             a reference to the application actor.
   * @param log                  a logging adapter.
   * @param timeout              a timeout for `ask` messages.
   * @param executionContext     an execution context for futures.
   * @return a [[Future]] containing a [[RouteResult]].
   */
  def runJsonRoute(
    requestMessageF: Future[KnoraRequestADM],
    requestContext: RequestContext,
    featureFactoryConfig: FeatureFactoryConfig,
    settings: KnoraSettingsImpl,
    appActor: ActorRef,
    log: Logger
  )(implicit timeout: Timeout, executionContext: ExecutionContext): Future[RouteResult] = {

    val httpResponse: Future[HttpResponse] = for {

      requestMessage <- requestMessageF

      // Optionally log the request message. TODO: move this to the testing framework.
      _ = if (settings.dumpMessages) {
            log.debug(requestMessage.toString)
          }

      // Make sure the responder sent a reply of type KnoraResponseV2.
      knoraResponse <- (appActor.ask(requestMessage)).map {
                         case replyMessage: KnoraResponseADM => replyMessage

                         case other =>
                           // The responder returned an unexpected message type (not an exception). This isn't the client's
                           // fault, so log it and return an error message to the client.
                           throw UnexpectedMessageException(
                             s"Responder sent a reply of type ${other.getClass.getCanonicalName}"
                           )
                       }

      // Optionally log the reply message. TODO: move this to the testing framework.
      _ = if (settings.dumpMessages) {
            log.debug(knoraResponse.toString)
          }

      jsonResponse = knoraResponse.toJsValue.asJsObject
    } yield featureFactoryConfig.addHeaderToHttpResponse(
      HttpResponse(
        status = StatusCodes.OK,
        entity = HttpEntity(
          ContentTypes.`application/json`,
          jsonResponse.compactPrint
        )
      )
    )

    requestContext.complete(httpResponse)
  }
}
