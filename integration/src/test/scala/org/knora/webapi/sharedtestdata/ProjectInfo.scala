/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.sharedtestdata

import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.projectsmessages.Project

/**
 * Represents basic information about a project.
 *
 * @param id          The project's IRI.
 * @param shortname   The project's shortname. Needs to be system wide unique.
 * @param longname    The project's long name. Needs to be system wide unique.
 * @param description The project's description.
 * @param keywords    The project's keywords.
 * @param logo        The project's logo.
 * @param ontologies  The project's ontologies.
 * @param status      The project's status.
 * @param selfjoin    The project's self-join status.
 */
case class ProjectInfo(
  id: IRI,
  shortname: IRI,
  shortcode: IRI,
  longname: Option[IRI],
  description: Option[IRI],
  keywords: Option[IRI],
  logo: Option[IRI],
  ontologies: Seq[IRI],
  status: Boolean,
  selfjoin: Boolean,
)

object ProjectInfo {
  def from(projectADM: Project): ProjectInfo =
    ProjectInfo(
      id = projectADM.id,
      shortname = projectADM.shortname,
      shortcode = projectADM.shortcode,
      longname = projectADM.longname,
      description = projectADM.description.headOption.map(_.value),
      keywords = projectADM.keywords.headOption.map(_ => projectADM.keywords.mkString(", ")),
      logo = projectADM.logo,
      ontologies = projectADM.ontologies,
      status = projectADM.status,
      selfjoin = projectADM.selfjoin,
    )
}
