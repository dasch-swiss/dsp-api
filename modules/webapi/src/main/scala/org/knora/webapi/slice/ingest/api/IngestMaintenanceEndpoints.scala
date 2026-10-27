/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.api

import sttp.model.StatusCode
import sttp.tapir.Codec
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.ztapir.*
import zio.ZLayer

import org.knora.webapi.slice.ingest.domain.ActionName
import org.knora.webapi.slice.ingest.domain.ProjectShortcode

final class IngestMaintenanceEndpoints(base: IngestBaseEndpoints) {

  private val maintenance   = "maintenance"
  private val adminRequired = "Authorization: admin scope required (SystemAdmins only)."

  private val actionNamePathVar = path[ActionName]("name")
    .description("The name of the action to be performed")
    .example(ActionName.UpdateAssetMetadata)

  private val restrictToProjectsQuery = {
    given Codec[List[String], List[ProjectShortcode], TextPlain] = Codec
      .list[String, String, TextPlain]
      .mapEither { list =>
        val (errors, codes) = list.map(ProjectShortcode.from).partitionMap(identity)
        if (errors.nonEmpty) Left(errors.mkString(", ")) else Right(codes)
      }(_.map(_.value))

    query[List[ProjectShortcode]]("restrictToProjects")
      .description(
        "Restrict the action to a list of projects, " +
          "if no project is given apply the action to all projects.",
      )
  }

  val postMaintenanceAction = base.securedEndpoint.post
    .in(maintenance / actionNamePathVar)
    .in(restrictToProjectsQuery)
    .out(stringBody)
    .out(statusCode(StatusCode.Accepted))
    .tag(maintenance)
    .description(adminRequired)

  val getNeedsTopLeftCorrection = base.securedEndpoint.get
    .in(maintenance / "needs-top-left-correction")
    .out(stringBody)
    .out(statusCode(StatusCode.Accepted))
    .tag(maintenance)
    .description(adminRequired)

  val getWasTopLeftCorrectionApplied = base.securedEndpoint.get
    .in(maintenance / "was-top-left-correction-applied")
    .out(stringBody)
    .out(statusCode(StatusCode.Accepted))
    .tag(maintenance)
    .description(adminRequired)
}

object IngestMaintenanceEndpoints {
  val layer = ZLayer.derive[IngestMaintenanceEndpoints]
}
