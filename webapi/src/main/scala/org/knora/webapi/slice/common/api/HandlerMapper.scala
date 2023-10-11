/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import dsp.errors.RequestRejectedException
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.slice.common.api.InputType.SecurityIn
import sttp.tapir.Endpoint
import sttp.tapir.model.UsernamePassword
import sttp.tapir.server.PartialServerEndpoint
import sttp.tapir.server.ServerEndpoint.Full
import zio.{Task, ZIO, ZLayer}

import scala.concurrent.Future

object InputType {
  type SecurityIn = (Option[String], Option[String], Option[UsernamePassword])
}

case class EndpointAndZioHandler[SECURITY_INPUT, INPUT, OUTPUT](
  endpoint: Endpoint[SECURITY_INPUT, INPUT, RequestRejectedException, OUTPUT, Any],
  handler: INPUT => Task[OUTPUT]
)

case class SecuredEndpointAndZioHandler[INPUT, OUTPUT](
  endpoint: PartialServerEndpoint[
    SecurityIn,
    UserADM,
    INPUT,
    RequestRejectedException,
    OUTPUT,
    Any,
    Future
  ],
  handler: UserADM => INPUT => Task[OUTPUT]
)

final case class HandlerMapperF()(implicit val r: zio.Runtime[Any]) {

  def mapEndpointAndHandler[INPUT, OUTPUT](
    handlerAndEndpoint: SecuredEndpointAndZioHandler[INPUT, OUTPUT]
  ): Full[SecurityIn, UserADM, INPUT, RequestRejectedException, OUTPUT, Any, Future] =
    handlerAndEndpoint.endpoint.serverLogic(user => in => { runToFuture(handlerAndEndpoint.handler(user)(in)) })

  def mapEndpointAndHandler[INPUT, OUTPUT](
    handlerAndEndpoint: EndpointAndZioHandler[Unit, INPUT, OUTPUT]
  ): Full[Unit, Unit, INPUT, RequestRejectedException, OUTPUT, Any, Future] =
    handlerAndEndpoint.endpoint.serverLogic[Future](in => runToFuture(handlerAndEndpoint.handler(in)))

  def runToFuture[OUTPUT](zio: Task[OUTPUT]): Future[Either[RequestRejectedException, OUTPUT]] =
    UnsafeZioRun.runToFuture(zio.refineOrDie { case e: RequestRejectedException => e }.either)
}

object HandlerMapperF {
  val layer = ZLayer.fromZIO(ZIO.runtime[Any].map(HandlerMapperF()(_)))
}
