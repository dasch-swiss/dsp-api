/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import sttp.tapir.Endpoint
import sttp.tapir.server.ServerEndpoint.Full
import zio.Task
import zio.ZIO
import zio.ZLayer

import scala.concurrent.Future

import dsp.errors.RequestRejectedException

case class EndpointAndZioHandler[INPUT, OUTPUT](
  endpoint: Endpoint[Unit, INPUT, RequestRejectedException, OUTPUT, Any],
  handler: INPUT => Task[OUTPUT]
)

final case class HandlerMapperF()(implicit val r: zio.Runtime[Any]) {

  def mapEndpointAndHandler[INPUT, OUTPUT](
    it: EndpointAndZioHandler[INPUT, OUTPUT]
  ): Full[Unit, Unit, INPUT, RequestRejectedException, OUTPUT, Any, Future] =
    it.endpoint.serverLogic[Future](handlerFromZio(it.handler))

  private def runToFuture[OUTPUT](zio: Task[OUTPUT]): Future[Either[RequestRejectedException, OUTPUT]] =
    UnsafeZioRun.runToFuture(zio.refineOrDie { case e: RequestRejectedException => e }.either)

  private def handlerFromZio[INPUT, OUTPUT](
    zio: INPUT => Task[OUTPUT]
  ): INPUT => Future[Either[RequestRejectedException, OUTPUT]] =
    input => runToFuture(zio.apply(input))
}

object HandlerMapperF {
  val layer = ZLayer.fromZIO(ZIO.runtime[Any].map(HandlerMapperF()(_)))
}
