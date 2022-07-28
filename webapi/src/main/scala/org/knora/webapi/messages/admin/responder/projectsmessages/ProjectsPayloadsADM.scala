/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.projectsmessages

import dsp.valueobjects.Iri.ProjectIri
import dsp.valueobjects.Project._

/**
 * Project creation payload
 */
final case class ProjectCreatePayloadADM(
  id: Option[ProjectIri] = None,
  shortname: Shortname,
  shortcode: ShortCode,
  longname: Option[Longname] = None,
  description: ProjectDescription,
  keywords: Keywords,
  logo: Option[Logo] = None,
  status: ProjectStatus,
  selfjoin: ProjectSelfJoin
)
