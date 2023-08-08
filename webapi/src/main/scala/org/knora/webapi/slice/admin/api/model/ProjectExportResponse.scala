/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.model

import zio.json.DeriveJsonEncoder
import zio.json.JsonEncoder

import org.knora.webapi.slice.admin.domain.service.ProjectExportInfo

case class ProjectImportResponse(location: String)
object ProjectImportResponse {
  implicit val jsonEncoder: JsonEncoder[ProjectImportResponse] = DeriveJsonEncoder.gen[ProjectImportResponse]
}

case class ProjectExportInfoResponse(projectShortname: String, location: String)
object ProjectExportInfoResponse {
  def apply(info: ProjectExportInfo) =
    new ProjectExportInfoResponse(info.projectShortname, info.path.toFile.toPath.toAbsolutePath.toString)

  implicit val jsonEncoder: JsonEncoder[ProjectExportInfoResponse] = DeriveJsonEncoder.gen[ProjectExportInfoResponse]
}
