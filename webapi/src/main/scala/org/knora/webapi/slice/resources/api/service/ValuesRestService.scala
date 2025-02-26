/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api.service
import sttp.model.MediaType
import zio.Task
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.messages.v2.responder.KnoraResponseV2
import org.knora.webapi.responders.v2.ResourcesResponderV2
import org.knora.webapi.responders.v2.ValuesResponderV2
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.RenderedResponse
import org.knora.webapi.slice.resources.api.model.ValueUuid
import org.knora.webapi.slice.resources.api.model.ValueVersionDate

final class ValuesRestService(
  val valuesService: ValuesResponderV2,
  val resourcesService: ResourcesResponderV2,
  val renderer: KnoraResponseRenderer,
) {

  def getValue(user: User)(
    resourceIri: String,
    valueUuid: ValueUuid,
    versionDate: Option[ValueVersionDate],
    formatOptions: FormatOptions,
  ): Task[(MediaType, RenderedResponse)] =
    render(
      resourcesService.getResourcesV2(
        Seq(resourceIri),
        None,
        Some(valueUuid.value),
        versionDate.map(_.value),
        withDeleted = true,
        showDeletedValues = false,
        formatOptions.schema,
        formatOptions.rendering,
        user,
      ),
      formatOptions,
    )

  private def render(task: Task[KnoraResponseV2], formatOptions: FormatOptions) =
    task.flatMap(renderer.render(_, formatOptions)).map(_.swap)
}

object ValuesRestService {
  val layer = ZLayer.derive[ValuesRestService]
}
