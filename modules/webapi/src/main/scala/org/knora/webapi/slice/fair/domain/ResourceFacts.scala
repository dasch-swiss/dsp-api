/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain

import java.time.Instant

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.common.ResourceIri

final case class ResourceFacts(
  iri: ResourceIri,
  ark: String,
  pageUrl: String,
  title: String,
  description: Option[String],
  creators: Creators,
  licence: Option[ResourceLicence],
  dateCreated: Instant,
  dateModified: Option[Instant],
  typeOfData: Option[String],
  classIri: String,
  classLabel: String,
  file: Option[FileFacts],
  accessLevel: AccessLevel,
  projectArk: String,
  shortcode: Shortcode,
)

final case class Creators(names: List[String], source: CreatorSource)

enum CreatorSource {
  case Authorship
  case ProjectDefault

  /** The single name is the project's longname or shortname; used in DataCite only, as an organizational creator. */
  case ProjectOrganisation
}

/** `uri` is None for a declaration. */
final case class ResourceLicence(uri: Option[String], label: String, spdxId: Option[String])

final case class FileFacts(contentUrl: String, mimeType: Option[String], fileName: Option[String], size: Option[Long])

enum AccessLevel(val coarUri: String, val text: String) {
  case FullOpenAccess extends AccessLevel("http://purl.org/coar/access_right/c_abf2", "Full Open Access")
  case OpenAccessWithRestrictions
      extends AccessLevel("http://purl.org/coar/access_right/c_16ec", "Open Access with Restrictions")
}
