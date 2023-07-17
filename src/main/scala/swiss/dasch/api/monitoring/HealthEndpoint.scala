/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api.monitoring

import zio.*
import zio.http.*
import zio.json.EncoderOps

object HealthEndpoint {
  val app: HttpApp[HealthCheckService, Nothing] = Http.collectZIO {
    case Method.GET -> Root / "health" =>
      HealthCheckService.check.map { result =>
        val response = Response.json(result.toJson)
        result.status match {
          case UP   => response
          case DOWN => response.withStatus(Status.ServiceUnavailable)
        }
      }
  }
}
