/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.actor.ActorRef
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.RouteResult
import akka.pattern._
import akka.util.Timeout
import com.typesafe.scalalogging.Logger

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import dsp.errors.UnexpectedMessageException
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.OntologySchema
import org.knora.webapi.messages.ResponderRequest.KnoraRequestADM
import org.knora.webapi.messages.admin.responder.KnoraResponseADM

/**
 * Convenience methods for Knora Admin routes.
 */
object RouteUtilADM {

  /**
   * Sends a message to a responder and completes the HTTP request by returning the response as JSON.
   *
   * @param requestMessageF      a future containing a [[KnoraRequestADM]] message that should be sent to the responder manager.
   * @param requestContext       the akka-http [[RequestContext]].
   *
   * @param appActor             a reference to the application actor.
   * @param log                  a logging adapter.
   * @param timeout              a timeout for `ask` messages.
   * @param executionContext     an execution context for futures.
   * @return a [[Future]] containing a [[RouteResult]].
   */
  def runJsonRoute(
    requestMessageF: Future[KnoraRequestADM],
    requestContext: RequestContext,
    appActor: ActorRef,
    targetSchema: OntologySchema = ApiV2Complex,
    log: Logger
  )(implicit timeout: Timeout, executionContext: ExecutionContext): Future[RouteResult] = {

    val httpResponse: Future[HttpResponse] = for {

      requestMessage <- requestMessageF

      // Make sure the responder sent a reply of type KnoraResponseADM.
      knoraResponse <- (appActor.ask(requestMessage)).map {
                         case replyMessage: KnoraResponseADM => replyMessage

                         case other =>
                           // The responder returned an unexpected message type (not an exception). This isn't the client's
                           // fault, so log it and return an error message to the client.
                           throw UnexpectedMessageException(
                             s"Responder sent a reply of type ${other.getClass.getCanonicalName}"
                           )
                       }

      jsonResponse = knoraResponse.format.toJsValue.asJsObject
    } yield HttpResponse(
      status = StatusCodes.OK,
      entity = HttpEntity(
        ContentTypes.`application/json`,
        jsonResponse.compactPrint
      )
    )

    requestContext.complete(httpResponse)
  }
}
