package org.knora.webapi.slice.admin.api.service

import zio.json.DeriveJsonEncoder
import zio.json.JsonEncoder

case class ProjectExportResponse(file: String)

object ProjectExportResponse {
  implicit val jsonEncoder: JsonEncoder[ProjectExportResponse] = DeriveJsonEncoder.gen[ProjectExportResponse]
}
