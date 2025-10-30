/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.export_.api

import zio.*

import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.api.v3.V3ErrorInfo
import org.knora.webapi.slice.common.service.IriConverter

final case class ExportRestService(
  iriConverter: IriConverter,
) {
  def getVersion(user: User)(request: ExportRequest): ZIO[Any, V3ErrorInfo, String] =
    (for {
      resourceClassIri <- iriConverter.asSmartIri(request.resourceClass)
      fields           <- ZIO.foreach(request.selectedProperties)(iriConverter.asSmartIri)
    } yield "").mapError(_ => ???)
}

object ExportRestService {
  val layer = ZLayer.derive[ExportRestService]
}
