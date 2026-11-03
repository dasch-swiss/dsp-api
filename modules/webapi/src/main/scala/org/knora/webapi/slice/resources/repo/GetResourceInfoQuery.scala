/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import java.time.Instant

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

object GetResourceInfoQuery {

  /**
   * Lists the resources of a project with their class and dates, optionally restricted to one class (exact match, no
   * subclasses) and to resources whose effective date (`lastModificationDate`, else `creationDate`) is at or after
   * `cutoff`. Deleted resources are included. `?resourceClass` is selected only when no class is given, since the
   * caller already knows it otherwise.
   *
   * `VALUES` must lead the `WHERE` block: a mid-block `VALUES` splits the pattern group. The cutoff `FILTER` must come
   * after both date patterns, since a filter splits the group where its variables are bound.
   */
  def build(projectIri: ProjectIri, resourceClass: Option[InternalIri], cutoff: Option[Instant]): Select = {
    val project = Iri.unsafeFrom(projectIri.value)
    val clazz   = resourceClass.map(c => Iri.unsafeFrom(c.value))
    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |SELECT DISTINCT ?resource ?creationDate ?isDeleted ?lastModificationDate ?deleteDate${sparql" ?resourceClass"
          .unless(clazz.isDefined)}
               |WHERE {
               |  ${clazz.whenSome(c => sparql"VALUES ?resourceClass { $c }")}
               |  ?resource a ?resourceClass ;
               |    knora-base:attachedToProject $project ;
               |    knora-base:creationDate ?creationDate ;
               |    knora-base:isDeleted ?isDeleted .
               |  OPTIONAL { ?resource knora-base:lastModificationDate ?lastModificationDate . }
               |  OPTIONAL { ?resource knora-base:deleteDate ?deleteDate . }
               |  ${cutoff.whenSome(c =>
          sparql"FILTER(COALESCE(?lastModificationDate, ?creationDate) >= ${Literal.dateTime(c)})",
        )}
               |}""".render,
    )
  }
}
