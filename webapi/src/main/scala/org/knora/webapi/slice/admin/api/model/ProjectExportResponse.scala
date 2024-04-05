/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.model

import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import org.knora.webapi.slice.admin.domain.service.ProjectExportInfo

case class ProjectImportResponse(location: String)
object ProjectImportResponse {
  implicit val codec: JsonCodec[ProjectImportResponse] = DeriveJsonCodec.gen[ProjectImportResponse]
}

case class ProjectExportInfoResponse(projectShortcode: String, location: String)
object ProjectExportInfoResponse {
  def apply(info: ProjectExportInfo) =
    new ProjectExportInfoResponse(info.projectShortcode, info.path.toFile.toPath.toAbsolutePath.toString)

  implicit val codec: JsonCodec[ProjectExportInfoResponse] = DeriveJsonCodec.gen[ProjectExportInfoResponse]
}
