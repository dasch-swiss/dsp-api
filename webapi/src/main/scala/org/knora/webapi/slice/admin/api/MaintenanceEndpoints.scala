/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.EndpointIO.Example
import sttp.tapir.json.zio.jsonBody
import zio.ZLayer
import zio.json.*
import zio.json.ast.*

import org.knora.webapi.slice.admin.api.service.MaintenanceRestService
import org.knora.webapi.slice.common.api.BaseEndpoints

final case class MaintenanceEndpoints(baseEndpoints: BaseEndpoints) {

  private val maintenanceBase = "admin" / "maintenance"

  given Schema[Option[Json]] =
    Schema.schemaForOption[Json](Schema.string.map((str: String) => str.fromJson[Json].toOption)(_.toJson))

  val postMaintenance = baseEndpoints.securedEndpoint.post
    .in(
      maintenanceBase / path[String]
        .name("action-name")
        .description("""The name of the maintenance action to be executed.
                       |Maintenance actions are executed asynchronously in the background.
                       |""".stripMargin)
        .examples(MaintenanceRestService.allActions.map(Example.of(_))),
    )
    .in(
      jsonBody[Option[Json]]
        .description("""The optional parameters as json for the maintenance action.
                       |May be required by certain actions.
                       |""".stripMargin),
    )
    .out(statusCode(StatusCode.Accepted))

  val endpoints: Seq[AnyEndpoint] = Seq(postMaintenance).map(_.endpoint.tag("Admin Maintenance"))
}

object MaintenanceEndpoints {
  val layer = ZLayer.derive[MaintenanceEndpoints]
}
