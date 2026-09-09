/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.*

import dsp.errors.BadRequestException
import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.admin.domain.model.Authorship
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.api.v2.ontologies.LastModificationDate
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object ChangeResourceAuthorshipQuery {

  def build(
    project: KnoraProject,
    resourceIri: ResourceIri,
    resourceClassIri: ResourceClassIri,
    maybeLastModificationDate: Option[LastModificationDate],
    maybeNewModificationDate: Option[LastModificationDate],
    authorship: Seq[Authorship],
  ): IO[BadRequestException, (LastModificationDate, Update)] = {

    // Determine the new modification date: use the submitted value if provided and valid, otherwise use the current time.
    val newModificationDateEffect: IO[BadRequestException, LastModificationDate] =
      maybeNewModificationDate.fold(Clock.instant.map(LastModificationDate.from)) { submittedNewDate =>
        maybeLastModificationDate match {
          case Some(currentDate) if currentDate.value.isAfter(submittedNewDate.value) =>
            val msg =
              "Submitted knora-api:newModificationDate is before the resource's current knora-api:lastModificationDate"
            ZIO.fail(BadRequestException(msg))
          case _ => ZIO.succeed(submittedNewDate)
        }
      }

    newModificationDateEffect.map { newModificationDate =>
      val dataGraph     = Iri.unsafeFrom(ProjectService.projectDataNamedGraphV2(project).value)
      val resource      = Iri.unsafeFrom(resourceIri.value)
      val resourceClass = Iri.unsafeFrom(resourceClassIri.toInternalSchema.toIri)
      val newDate       = Literal.dateTime(newModificationDate.value)
      val currentDate   = maybeLastModificationDate.map(lmd => Literal.dateTime(lmd.value))

      // DELETE the previous last modification date (if any) and all existing authorship triples.
      val lastModDelete = currentDate.whenSome(d => sparql"$resource knora-base:lastModificationDate $d .")

      // INSERT one triple per new authorship value (none when empty => authorship is cleared).
      val authorshipInserts =
        authorship.map(a => sparql"$resource knora-base:hasResourceAuthorship ${Literal.string(a.value)} .").joinLines

      // WHERE: the resource must exist with the expected class and lastModificationDate.
      val lastModWhere = currentDate.fold(
        sparql"FILTER NOT EXISTS { $resource knora-base:lastModificationDate ?anyLastModificationDate . }",
      )(d => sparql"$resource knora-base:lastModificationDate $d .")

      // WITH <graph> scopes the named graph to the WHERE clause too, not just DELETE/INSERT;
      // an ungraphed WHERE matches the default graph, which is empty (so the update no-ops) on a
      // store without a union default graph, e.g. the in-memory test store. Safe only because the
      // WHERE matches the resource's own data-graph triples; a cross-graph pattern (e.g. an ontology
      // check) would need USING/GRAPH.
      val query =
        sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                 |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                 |
                 |WITH $dataGraph
                 |DELETE {
                 |  $lastModDelete
                 |  $resource knora-base:hasResourceAuthorship ?oldAuthorship .
                 |}
                 |INSERT {
                 |  $resource knora-base:lastModificationDate $newDate .
                 |  $authorshipInserts
                 |}
                 |WHERE {
                 |  $resource a $resourceClass .
                 |  $lastModWhere
                 |  OPTIONAL { $resource knora-base:hasResourceAuthorship ?oldAuthorship . }
                 |}""".render

      (newModificationDate, Update(query))
    }
  }
}
