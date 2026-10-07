/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.fair

import java.time.Instant

enum CreatorKind {
  case Person, Organization
}

final case class Creator(name: String, kind: CreatorKind, orcid: Option[String])

enum AccessLevel {
  case FullOpen, Restricted
}

/** What the anonymous user may download; every field is absent when its source lacks it. */
final case class FileFacts(
  contentUrl: String,
  name: Option[String],
  encodingFormat: Option[String],
  contentSize: Option[Long],
  license: Option[String],
)

/**
 * The facts published about one resource, gathered once; every representation is a projection of it.
 * Holds only allow-listed facts, never raw values.
 */
final case class ResourceFairGraph(
  ark: String,
  pageUrl: String,
  title: String,
  creators: Seq[Creator],
  dateCreated: Instant,
  dateModified: Option[Instant],
  license: Option[String],
  copyrightHolder: Option[String],
  generalType: String,
  accessLevel: AccessLevel,
  file: Option[FileFacts],
  projectArk: String,
  projectShortcode: String,
  projectName: String,
  resourceClassIri: String,
)
