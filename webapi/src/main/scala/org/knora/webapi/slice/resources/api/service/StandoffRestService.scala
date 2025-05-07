/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api.service

import sttp.model.MediaType
import zio.*

import dsp.errors.BadRequestException
import org.knora.webapi.responders.v2.StandoffResponderV2
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.ApiComplexV2JsonLdRequestParser
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.RenderedResponse
import org.knora.webapi.slice.resources.api.CreateStandoffMappingForm

case class StandoffRestService(
  private val auth: AuthorizationRestService,
  private val renderer: KnoraResponseRenderer,
  private val requestParser: ApiComplexV2JsonLdRequestParser,
  private val standoffResponder: StandoffResponderV2,
) {
  def createMapping(
    user: User,
  )(mappingForm: CreateStandoffMappingForm, opts: FormatOptions): ZIO[Any, Throwable, (RenderedResponse, MediaType)] =
    for {
      _ <- auth.ensureUserIsNotAnonymous(user)
      metadata <- requestParser
                    .createMappingRequestMetadataV2(mappingForm.json)
                    .mapError(BadRequestException.apply)
      uuid     <- Random.nextUUID
      result   <- standoffResponder.createMappingV2(metadata, mappingForm.xml, user, uuid)
      response <- renderer.render(result, opts)
    } yield response
}

object StandoffRestService {
  val layer = ZLayer.derive[StandoffRestService]
}
