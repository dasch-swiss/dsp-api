/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.export_.model

import zio.ZLayer
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.sparqlbuilder.constraint.propertypath.builder.PropertyPathBuilder
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.{`var` as variable, *}
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.*
import java.time.Instant
import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.SparqlTimeout
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.util.rdf.VariableResultsRow

final case class ExportService(
  private val projectService: KnoraProjectService,
  private val triplestore: TriplestoreService,
  private val iriConverter: IriConverter,
) {
  val (
    classIriVar,
    creationDateVar,
    creatorIriVar,
    deleteDateVar,
    labelVar,
    lastModificationDateVar,
    resourceIriVar,
  ) = ("classIri", "createdAt", "creator", "deletedAt", "label", "modifiedAt", "resourceIri")

  def getSmartIriOrThrow(map: Map[String, String], field: String): Task[SmartIri] = {
    val m = s"ExportService query for project {project.shortcode} returned inconsistent data for $field"
    for {
      value    <- ZIO.attempt(map.getOrElse(field, throw new InconsistentRepositoryDataException(m)))
      smartIri <- iriConverter.asSmartIri(value)
    } yield smartIri
  }

  def findResources(
    project: KnoraProject,
    classIris: List[ResourceClassIri],
  ) =
    for {
      query           <- ZIO.succeed(resourceQuery(project, classIris))
      dr              <- triplestore.selectWithTimeout(query, SparqlTimeout.Gravsearch).map(_.results.bindings).timed
      (duration, rows) = dr
      _               <- ZIO.logInfo(s"ExportService: ${rows.size} in ${duration.toMillis}: ${query.getQueryString}")
      // now  <- Clock.instant
      meta <- ZIO.foreach(rows)(row => formatRow(row))
    } yield meta

  private def resourceQuery(
    project: KnoraProject,
    classIris: List[ResourceClassIri],
  ) = {
    val selectPattern = SparqlBuilder
      .select(
        variable(classIriVar),
        variable(creationDateVar),
        variable(creatorIriVar),
        variable(deleteDateVar),
        variable(labelVar),
        variable(lastModificationDateVar),
        variable(resourceIriVar),
      )
      .distinct()

    val projectGraph = projectService.getDataGraphForProject(project)
    val wherePattern =
      variable(resourceIriVar)
        .isA(variable(classIriVar))
        .andHas(KB.creationDate, variable(creationDateVar))
        .andHas(KB.attachedToUser, variable(creatorIriVar))
        .andHas(RDFS.LABEL, variable(labelVar))
        .and(variable(resourceIriVar).has(KB.lastModificationDate, variable(lastModificationDateVar)).optional())
        .and(variable(resourceIriVar).has(KB.deleteDate, variable(deleteDateVar)).optional())
        .from(Rdf.iri(projectGraph.value))

    val classConstraintPattern = classIris.map(_.toInternalSchema.toIri).map(Rdf.iri) match {
      case Nil         => variable(classIriVar).has(PropertyPathBuilder.of(RDFS.SUBCLASSOF).zeroOrMore().build(), KB.Resource)
      case head :: Nil => variable(resourceIriVar).isA(head)
      case head :: tail =>
        val pat = variable(resourceIriVar).isA(head)
        pat.union(tail.map(c => variable(resourceIriVar).isA(c)): _*)
    }

    Queries
      .SELECT(selectPattern)
      .where(classConstraintPattern, wherePattern)
      .prefix(prefix(KB.NS), prefix(RDFS.NS))
  }

  private def formatRow(row: VariableResultsRow) =
    for {
      classIri    <- getSmartIriOrThrow(row.rowMap, classIriVar).map(_.toComplexSchema.toIri)
      _            = row.rowMap
      resourceIri <- getSmartIriOrThrow(row.rowMap, resourceIriVar)
      label       <- getSmartIriOrThrow(row.rowMap, labelVar)
      creatorIri  <- getSmartIriOrThrow(row.rowMap, creatorIriVar).map(_.toComplexSchema.toIri)
      // createdAt    = row.rowMap.get(creationDateVar).map(Instant.parse).getOrThrow(creationDateVar)
      // deletedAt    = row.rowMap.get(deleteDateVar).map(Instant.parse)
      // lastModAt    = row.rowMap.get(lastModificationDateVar).map(Instant.parse)
    } yield ()
}

object ExportService {
  val layer = ZLayer.derive[ExportService]
}
