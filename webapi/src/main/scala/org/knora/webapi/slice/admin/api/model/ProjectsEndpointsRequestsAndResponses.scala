/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.model

import zio.IO
import zio.ZIO
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import dsp.errors.BadRequestException
import org.knora.webapi.slice.admin.api.Codecs.ZioJsonCodec.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.RestrictedView

object ProjectsEndpointsRequestsAndResponses {

  final case class ProjectCreateRequest(
    id: Option[ProjectIri] = None,
    shortname: Shortname,
    shortcode: Shortcode,
    longname: Option[Longname] = None,
    description: List[Description],
    keywords: List[Keyword],
    logo: Option[Logo] = None,
    status: Status,
    selfjoin: SelfJoin
  )
  object ProjectCreateRequest {
    implicit val codec: JsonCodec[ProjectCreateRequest] = DeriveJsonCodec.gen[ProjectCreateRequest]
  }

  final case class ProjectUpdateRequest(
    shortname: Option[Shortname] = None,
    longname: Option[Longname] = None,
    description: Option[List[Description]] = None,
    keywords: Option[List[Keyword]] = None,
    logo: Option[Logo] = None,
    status: Option[Status] = None,
    selfjoin: Option[SelfJoin] = None
  )
  object ProjectUpdateRequest {
    implicit val codec: JsonCodec[ProjectUpdateRequest] = DeriveJsonCodec.gen[ProjectUpdateRequest]
  }

  final case class SetRestrictedViewRequest(
    size: Option[RestrictedView.Size],
    watermark: Option[RestrictedView.Watermark]
  ) {
    def toRestrictedView: IO[BadRequestException, RestrictedView] =
      (size, watermark) match {
        case (Some(size), None)      => ZIO.succeed(size)
        case (None, Some(watermark)) => ZIO.succeed(RestrictedView.Watermark.from(watermark.value))
        case _                       => ZIO.fail(BadRequestException("Exactly one one of size or watermark must be provided."))
      }
  }

  object SetRestrictedViewRequest {
    implicit val codec: JsonCodec[SetRestrictedViewRequest] = DeriveJsonCodec.gen[SetRestrictedViewRequest]
  }

  final case class RestrictedViewResponse(
    size: Option[RestrictedView.Size],
    watermark: Option[RestrictedView.Watermark]
  )
  object RestrictedViewResponse {
    implicit val codec: JsonCodec[RestrictedViewResponse] = DeriveJsonCodec.gen[RestrictedViewResponse]

    def from(restrictedView: RestrictedView): RestrictedViewResponse =
      restrictedView match {
        case size: RestrictedView.Size           => RestrictedViewResponse(Some(size), None)
        case watermark: RestrictedView.Watermark => RestrictedViewResponse(None, Some(watermark))
      }
  }
}
