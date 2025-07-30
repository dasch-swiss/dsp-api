/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.v3.projects.domain.model

import org.knora.webapi.slice.v3.projects.domain.model.DomainTypes.*

final case class ProjectInfo(
  shortcode: ProjectShortcode,
  shortname: ProjectShortname,
  iri: ProjectIri,
  fullName: Option[String],
  description: MultilingualText,
  status: Boolean,
  lists: List[ListPreview],
  ontologies: List[OntologyWithClasses],
)

final case class ListPreview(
  iri: ListIri,
  labels: MultilingualText,
)

final case class OntologyWithClasses(
  iri: OntologyIri,
  label: String,
  classes: List[AvailableClass],
)

final case class AvailableClass(
  iri: ClassIri,
  labels: MultilingualText,
)

final case class OntologyResourceCounts(
  ontologyLabel: String,
  classes: List[ClassCount],
)

final case class ClassCount(
  iri: ClassIri,
  instanceCount: Int,
)

final case class Ontology(
  iri: OntologyIri,
  label: String,
)
