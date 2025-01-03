/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import sttp.model.StatusCode
import sttp.tapir.Codec
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.ztapir.*
import swiss.dasch.domain.ProjectShortcode
import zio.json.{DeriveJsonCodec, JsonCodec}
import zio.schema.{DeriveSchema, Schema}
import zio.ZLayer

final case class MappingEntry(internalFilename: String, originalFilename: String)

object MappingEntry {
  given codec: JsonCodec[MappingEntry] = DeriveJsonCodec.gen[MappingEntry]
  given schema: Schema[MappingEntry]   = DeriveSchema.gen[MappingEntry]
}

enum ActionName {
  case ApplyTopLeftCorrection extends ActionName
  case UpdateAssetMetadata    extends ActionName
  case ImportProjectsToDb     extends ActionName
}

object ActionName {
  given codec: JsonCodec[ActionName] = DeriveJsonCodec.gen[ActionName]

  given schema: Schema[ActionName] = DeriveSchema.gen[ActionName]

  given tapirCodec: sttp.tapir.Codec[String, ActionName, TextPlain] =
    sttp.tapir.Codec.derivedEnumeration[String, ActionName].defaultStringBased
}

final case class MaintenanceEndpoints(base: BaseEndpoints) {

  private val maintenance = "maintenance"

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

  val postMaintenanceActionEndpoint = base.secureEndpoint.post
    .in(maintenance / actionNamePathVar)
    .in(restrictToProjectsQuery)
    .out(stringBody)
    .out(statusCode(StatusCode.Accepted))
    .tag(maintenance)
    .description("Authorization: admin scope required.")

  val needsTopLeftCorrectionEndpoint = base.secureEndpoint.get
    .in(maintenance / "needs-top-left-correction")
    .out(stringBody)
    .out(statusCode(StatusCode.Accepted))
    .tag(maintenance)
    .description("Authorization: admin scope required.")

  val wasTopLeftCorrectionAppliedEndpoint = base.secureEndpoint.get
    .in(maintenance / "was-top-left-correction-applied")
    .out(stringBody)
    .out(statusCode(StatusCode.Accepted))
    .tag(maintenance)
    .description("Authorization: admin scope required.")

  val needsOriginalsEndpoint = base.secureEndpoint.get
    .in(maintenance / "needs-originals")
    .in(query[Option[Boolean]]("imagesOnly"))
    .out(stringBody)
    .out(statusCode(StatusCode.Accepted))
    .tag(maintenance)
    .description("Authorization: admin scope required.")

  val endpoints = List(
    postMaintenanceActionEndpoint,
    needsTopLeftCorrectionEndpoint,
    needsOriginalsEndpoint,
  )
}

object MaintenanceEndpoints {
  val layer = ZLayer.derive[MaintenanceEndpoints]
}
