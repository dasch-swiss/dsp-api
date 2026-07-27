/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.mapping

import sttp.model.MediaType
import zio.*

import dsp.errors.BadRequestException
import dsp.errors.StandoffInternalException
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.responders.v2.StandoffResponderV2
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.api.v2.mapping.CreateStandoffMappingForm
import org.knora.webapi.slice.common.ApiComplexV2JsonLdRequestParser
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.RenderedResponse
import org.knora.webapi.slice.standoff.service.StandoffMappingService

final class StandoffRestService(
  auth: AuthorizationRestService,
  renderer: KnoraResponseRenderer,
  requestParser: ApiComplexV2JsonLdRequestParser,
  standoffResponder: StandoffResponderV2,
  mappingService: StandoffMappingService,
) {
  def createMapping(
    user: User,
  )(mappingForm: CreateStandoffMappingForm, opts: FormatOptions): ZIO[Any, Throwable, (RenderedResponse, MediaType)] =
    for {
      _             <- auth.ensureUserIsNotAnonymous(user)
      createRequest <- requestParser
                         .createMappingRequestMetadataV2(mappingForm)
                         .mapError(BadRequestException.apply)
      uuid     <- Random.nextUUID
      result   <- standoffResponder.createMappingV2(createRequest, uuid)
      response <- renderer.render(result, opts)
    } yield response

  /**
   * Canonicalizes standard-mapping rich-text XML by round-tripping it through standoff markup and back,
   * yielding the exact XML dsp-api would return when reading the value. The conversion is idempotent, so
   * the result is comparable to the `textValueAsXml` of a stored value for change detection.
   */
  def canonicalize(user: User)(xml: String): Task[String] =
    for {
      _ <- auth.ensureUserIsNotAnonymous(user)
      // The mapping IRI is a constant built-in; any failure to load it is a server-side problem, not a
      // client error, so it must surface as a 500 rather than the BadRequestException getMappingV2 returns.
      mapping <- mappingService
                   .getMappingV2(StandoffMappingIri.StandardMapping)
                   .mapError(StandoffInternalException("The standard standoff mapping could not be loaded.", _))
      canonical <-
        ZIO
          .attempt(StandoffTagUtilV2.canonicalize(xml, mapping))
          // Log only the failure category server-side. The parser message can contain fragments of
          // the submitted rich text, and must not be echoed to the client (leaks internal detail /
          // acts as an error-based exfiltration oracle) nor shipped verbatim to the log backend.
          .tapError(e => ZIO.logInfo(s"Rich-text canonicalization rejected: ${e.getClass.getSimpleName}"))
          .mapError(_ =>
            BadRequestException(
              "The provided rich text could not be canonicalized; it must be well-formed XML using the standard mapping.",
            ),
          )
    } yield canonical
}

object StandoffRestService {
  private[mapping] val layer = ZLayer.derive[StandoffRestService]
}
