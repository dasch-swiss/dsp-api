/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir.Endpoint
import sttp.tapir.model.UsernamePassword
import sttp.tapir.server.PartialServerEndpoint
import sttp.tapir.server.ServerEndpoint.Full
import zio.Task
import zio.ZIO
import zio.ZLayer

import scala.concurrent.Future

import dsp.errors.RequestRejectedException
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.InputType.SecurityIn

object InputType {
  type SecurityIn = (Option[String], Option[String], Option[UsernamePassword])
}

case class PublicEndpointHandler[INPUT, OUTPUT](
  endpoint: Endpoint[Unit, INPUT, RequestRejectedException, OUTPUT, PekkoStreams],
  handler: INPUT => Task[OUTPUT],
)

case class SecuredEndpointHandler[INPUT, OUTPUT](
  endpoint: PartialServerEndpoint[
    SecurityIn,
    User,
    INPUT,
    RequestRejectedException,
    OUTPUT,
    Any,
    Future,
  ],
  handler: User => INPUT => Task[OUTPUT],
)

final case class HandlerMapper()(implicit val r: zio.Runtime[Any]) {

  def mapSecuredEndpointHandler[INPUT, OUTPUT](
    handlerAndEndpoint: SecuredEndpointHandler[INPUT, OUTPUT],
  ): Full[SecurityIn, User, INPUT, RequestRejectedException, OUTPUT, Any, Future] =
    handlerAndEndpoint.endpoint.serverLogic(user => in => { runToFuture(handlerAndEndpoint.handler(user)(in)) })

  def mapPublicEndpointHandler[INPUT, OUTPUT](
    handlerAndEndpoint: PublicEndpointHandler[INPUT, OUTPUT],
  ): Full[Unit, Unit, INPUT, RequestRejectedException, OUTPUT, PekkoStreams, Future] =
    handlerAndEndpoint.endpoint.serverLogic[Future](in => runToFuture(handlerAndEndpoint.handler(in)))

  def runToFuture[OUTPUT](zio: Task[OUTPUT]): Future[Either[RequestRejectedException, OUTPUT]] =
    UnsafeZioRun.runToFuture(zio.refineOrDie { case e: RequestRejectedException => e }.either)
}

object HandlerMapper {
  val layer = ZLayer.fromZIO(ZIO.runtime[Any].map(HandlerMapper()(_)))
}
