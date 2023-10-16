/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.model

import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import dsp.valueobjects.Iri.ProjectIri
import dsp.valueobjects.Project._

object ProjectsEndpointsRequests {

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

  final case class ProjectSetRestrictedViewSizeRequest(size: String)
  object ProjectSetRestrictedViewSizeRequest {
    implicit val codec: JsonCodec[ProjectSetRestrictedViewSizeRequest] =
      DeriveJsonCodec.gen[ProjectSetRestrictedViewSizeRequest]
  }
}
