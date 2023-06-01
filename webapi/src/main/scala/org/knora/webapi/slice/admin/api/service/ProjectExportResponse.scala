/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import zio.json.DeriveJsonEncoder
import zio.json.JsonEncoder

case class ProjectExportResponse(file: String)

object ProjectExportResponse {
  implicit val jsonEncoder: JsonEncoder[ProjectExportResponse] = DeriveJsonEncoder.gen[ProjectExportResponse]
}
