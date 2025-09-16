/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testservices

import org.apache.pekko
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.client.RequestBuilding
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import zio.*

import scala.concurrent.ExecutionContext

import dsp.errors.AssertionException
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.settings.KnoraDispatchers

final case class TestClientService()(implicit system: ActorSystem)
    extends TriplestoreJsonProtocol
    with RequestBuilding {

  implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraBlockingDispatcher)

  /**
   * Performs a http request.
   *
   * @param request the request to be performed.
   * @param timeout the timeout for the request. Default timeout is 5 seconds.
   * @param printFailure If true, the response body will be printed if the request fails.
   *                     This flag is intended to be used for debugging purposes only.
   *                     Since this is unsafe, it is false by default.
   *                     It is unsafe because the response body can only be unmarshalled (i.e. printed) to a string once.
   *                     It will fail if the test code is also unmarshalling the response.
   * @return the response.
   */
  def singleAwaitingRequest(
    request: pekko.http.scaladsl.model.HttpRequest,
    timeout: Option[zio.Duration] = None,
    printFailure: Boolean = false,
  ): Task[pekko.http.scaladsl.model.HttpResponse] =
    ZIO
      .fromFuture[pekko.http.scaladsl.model.HttpResponse](_ =>
        pekko.http.scaladsl
          .Http()
          .singleRequest(request)
          .map { resp =>
            if (printFailure && resp.status.isFailure()) {
              val _ = Unmarshal(resp.entity).to[String].map { body =>
                println(s"Request failed with status ${resp.status} and body $body")
              }
            }
            resp
          },
      )
      .timeout(timeout.getOrElse(10.seconds))
      .some
      .mapError {
        case None            => throw AssertionException("Request timed out.")
        case Some(throwable) => throw throwable
      }
}

object TestClientService {
  def layer = ZLayer.derive[TestClientService]
}
