/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search.ports

import zio.Task

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.domain.InternalIri

/**
 * Resolves a project to the named graph that holds its data, so search can scope a Lucene lookup to it
 * (docs/adr/0012-graph-scoped-lucene-text-lookups.md).
 */
trait ProjectDataGraphs {

  /** `None` for an unknown project; any other failure (e.g. a triplestore error) fails the effect. */
  def dataGraphOf(project: ProjectIri): Task[Option[InternalIri]]
}
