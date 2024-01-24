/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.model

import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import org.knora.webapi.slice.admin.api.Codecs.ZioJsonCodec.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.RestrictedViewSize

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

  final case class SetRestrictedViewRequest(size: RestrictedViewSize, watermark: Option[Boolean])
  object SetRestrictedViewRequest {
    implicit val codec: JsonCodec[SetRestrictedViewRequest] = DeriveJsonCodec.gen[SetRestrictedViewRequest]
  }

  final case class RestrictedViewResponse(size: RestrictedViewSize, watermark: Boolean)
  object RestrictedViewResponse {
    implicit val codec: JsonCodec[RestrictedViewResponse] = DeriveJsonCodec.gen[RestrictedViewResponse]
  }
}
