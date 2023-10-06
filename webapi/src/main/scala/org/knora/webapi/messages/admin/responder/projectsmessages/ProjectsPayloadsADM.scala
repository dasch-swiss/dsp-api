/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.projectsmessages

import zio.json._

import dsp.valueobjects.Iri.ProjectIri
import dsp.valueobjects.Project._

/**
 * Project creation payload
 */
final case class ProjectCreateRequest(
  id: Option[ProjectIri] = None,
  shortname: Shortname,
  shortcode: Shortcode,
  longname: Option[Name] = None,
  description: ProjectDescription,
  keywords: Keywords,
  logo: Option[Logo] = None,
  status: ProjectStatus,
  selfjoin: ProjectSelfJoin
)

object ProjectCreateRequest {
  implicit val codec: JsonCodec[ProjectCreateRequest] = DeriveJsonCodec.gen[ProjectCreateRequest]
}

/**
 * Project update payload
 */
final case class ProjectUpdateRequest(
  shortname: Option[Shortname] = None,
  longname: Option[Name] = None,
  description: Option[ProjectDescription] = None,
  keywords: Option[Keywords] = None,
  logo: Option[Logo] = None,
  status: Option[ProjectStatus] = None,
  selfjoin: Option[ProjectSelfJoin] = None
)

object ProjectUpdateRequest {
  implicit val codec: JsonCodec[ProjectUpdateRequest] = DeriveJsonCodec.gen[ProjectUpdateRequest]
}

final case class ProjectSetRestrictedViewSizePayload(size: String)

object ProjectSetRestrictedViewSizePayload {
  implicit val codec: JsonCodec[ProjectSetRestrictedViewSizePayload] =
    DeriveJsonCodec.gen[ProjectSetRestrictedViewSizePayload]
}
