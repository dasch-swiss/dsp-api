/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPattern
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.*

import dsp.errors.BadRequestException
import org.knora.webapi.slice.admin.domain.model.Authorship
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.api.v2.ontologies.LastModificationDate
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object ChangeResourceAuthorshipQuery extends QueryBuilderHelper {

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
      val dataGraph               = Rdf.iri(ProjectService.projectDataNamedGraphV2(project).value)
      val resource                = toRdfIri(resourceIri)
      val resourceClass           = toRdfIri(resourceClassIri)
      val oldAuthorship           = variable("oldAuthorship")
      val anyLastModificationDate = variable("anyLastModificationDate")

      // DELETE the previous last modification date (if any) and all existing authorship triples.
      val deletePatterns: List[TriplePattern] = {
        val lastModDelete =
          maybeLastModificationDate.map(toRdfLiteral).map(resource.has(KB.lastModificationDate, _)).toList
        val authorshipDelete = List(resource.has(KB.hasResourceAuthorship, oldAuthorship))
        lastModDelete ::: authorshipDelete
      }

      // INSERT the new last modification date and one triple per new authorship value (none when empty => cleared).
      val insertPatterns: List[TriplePattern] = {
        val modificationDateInsert = resource.has(KB.lastModificationDate, toRdfLiteral(newModificationDate))
        val authorshipInsert       = authorship.map(a => resource.has(KB.hasResourceAuthorship, a.value)).toList
        modificationDateInsert :: authorshipInsert
      }

      // WHERE: the resource must exist with the expected class and lastModificationDate; bind existing authorship.
      val wherePatterns: List[GraphPattern] = {
        val resourceTypePattern = resource.isA(resourceClass)

        val lastModPattern: GraphPattern = maybeLastModificationDate match {
          case Some(lmd) => resource.has(KB.lastModificationDate, toRdfLiteral(lmd))
          case None      => GraphPatterns.filterNotExists(resource.has(KB.lastModificationDate, anyLastModificationDate))
        }

        val authorshipPattern = resource.has(KB.hasResourceAuthorship, oldAuthorship).optional()

        List(resourceTypePattern, lastModPattern, authorshipPattern)
      }

      // Use `WITH <graph>` (not `.from`/`.into`) so the named project graph applies to the WHERE
      // clause as well as DELETE/INSERT. With `.from`/`.into` the DELETE/INSERT are GRAPH-wrapped but
      // the WHERE stays ungraphed, matching the dataset default graph. On a store without a union
      // default graph (e.g. the in-memory triplestore used in tests) that default graph is empty, so
      // the WHERE matches nothing and the whole update silently no-ops. `WITH` keeps it correct
      // regardless of the union-default-graph setting (production enables it, so `.from`/`.into` would
      // work there too, but at the cost of scanning the union of all graphs).
      // Invariant: this is correct only because every WHERE pattern here matches the resource's own
      // triples in the data graph (its asserted rdf:type, lastModificationDate, hasResourceAuthorship).
      // A pattern needing another graph (e.g. an ontology class or cardinality check) would not match
      // under `WITH` and would require `USING <data> USING <ontology>` or explicit `GRAPH {}` blocks.
      // `with` is a Scala keyword, hence the backticks.
      val query = Queries
        .MODIFY()
        .prefix(KB.NS, RDFS.NS, XSD.NS, RDF.NS)
        .`with`(dataGraph)
        .delete(deletePatterns: _*)
        .insert(insertPatterns: _*)
        .where(wherePatterns: _*)

      (newModificationDate, Update(query))
    }
  }
}
