/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import sttp.model.StatusCode
import sttp.tapir.EndpointOutput
import sttp.tapir.endpoint
import sttp.tapir.generic.auto._
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.oneOf
import sttp.tapir.oneOfVariant
import sttp.tapir.statusCode
import zio.ZLayer

import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import dsp.errors.RequestRejectedException

final case class BaseEndpoints() {

  private val defaultErrorOutputs: EndpointOutput.OneOf[RequestRejectedException, RequestRejectedException] =
    oneOf[RequestRejectedException](
      oneOfVariant[NotFoundException](statusCode(StatusCode.NotFound).and(jsonBody[NotFoundException])),
      oneOfVariant[BadRequestException](statusCode(StatusCode.BadRequest).and(jsonBody[BadRequestException]))
    )

  val publicEndpoint = endpoint.errorOut(defaultErrorOutputs)
}

object BaseEndpoints {
  val layer = ZLayer.derive[BaseEndpoints]
}
