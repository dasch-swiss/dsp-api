/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.ports

import zio.Task

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode

trait FairProjects {
  def findByShortcode(shortcode: Shortcode): Task[Option[FairProject]]

  /** Looks a licence IRI up in the built-in licence catalogue; None when it is not a known licence. */
  def findLicence(licenceIri: String): Task[Option[FairLicence]]
}

final case class FairProject(
  shortcode: Shortcode,
  shortname: String,
  longname: Option[String],
  dataLicenseIri: Option[String],
  defaultDataAuthorship: List[String],
)

enum FairLicence {

  /** Stands for "no licence recorded". */
  case Placeholder

  /** A statement with no licence URI (unknown, AI-generated, public domain). */
  case Declaration(label: String)
  case Licence(uri: String, label: String)
}
