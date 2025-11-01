/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.export_.model

import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.sparqlbuilder.constraint.propertypath.builder.PropertyPathBuilder
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.{`var` as variable, *}
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.*
import zio.ZLayer

import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.export_.api.ExportedResource
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.SparqlTimeout
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery

// TODO: this file is not done
// TODO: respect permissions on the resource and value level
// TODO: verify that knora-base:hasPermissions is also allowed to be exproted (it is to anonymous users)
final case class ExportService(
  private val projectService: KnoraProjectService,
  private val triplestore: TriplestoreService,
  private val iriConverter: IriConverter,
) {
  val (
    classIriVar,
    resourceIriVar,
  ) = ("classIri", "resourceIri")

  def exportResources(
    project: KnoraProject,
    classIri: ResourceClassIri,
    selectedProperties: List[SmartIri],
  ): Task[List[ExportedResource]] =
    for {
      rows              <- findResources(project, classIri, selectedProperties)
      exportedResources <- ZIO.foreach(rows)(formatRow(_))
    } yield exportedResources.toList

  private def findResources(
    project: KnoraProject,
    classIri: ResourceClassIri,
    selectedProperties: List[SmartIri],
  ): Task[Seq[VariableResultsRow]] =
    for {
      query           <- ZIO.succeed(resourceQuery(project, classIri, selectedProperties))
      dr              <- triplestore.selectWithTimeout(query, SparqlTimeout.Gravsearch).map(_.results.bindings).timed
      (duration, rows) = dr
      _               <- ZIO.logInfo(s"ExportService: ${rows.size} rows in ${duration.toMillis} ms: ${query.getQueryString}")
    } yield rows

  private def resourceQuery(
    project: KnoraProject,
    classIri: ResourceClassIri,
    selectedProperties: List[SmartIri],
  ): SelectQuery = {
    val selectPattern = SparqlBuilder
      .select(
        variable(classIriVar),
        variable(resourceIriVar),
      )
      .distinct()

    val projectGraph = projectService.getDataGraphForProject(project)
    val wherePattern =
      variable(resourceIriVar)
        .isA(variable(classIriVar))
        // .andHas(KB.attachedToUser, variable(creatorIriVar))
        // .andHas(RDFS.LABEL, variable(labelVar))
        .from(Rdf.iri(projectGraph.value))

    val classConstraint = variable(resourceIriVar).isA(Rdf.iri(classIri.toInternalSchema.toIri))

    val classSubclassOfResource =
      variable(classIriVar).has(PropertyPathBuilder.of(RDFS.SUBCLASSOF).zeroOrMore().build(), KB.Resource)

    Queries
      .SELECT(selectPattern)
      .where(wherePattern, classConstraint, classSubclassOfResource)
      .prefix(prefix(KB.NS), prefix(RDFS.NS))
  }

  // TODO: just do for comprehension on plain Eithers with propertyIri in the Left channel, then collect at last step
  private def getSmartIriOrThrow(map: Map[String, String], field: String): Task[SmartIri] = {
    val m = s"ExportService query for project {project.shortcode} returned inconsistent data for $field"
    for {
      value    <- ZIO.attempt(map.getOrElse(field, throw new InconsistentRepositoryDataException(m)))
      smartIri <- iriConverter.asSmartIri(value)
    } yield smartIri
  }

  private def formatRow(row: VariableResultsRow): Task[ExportedResource] =
    for {
      resourceIri <- getSmartIriOrThrow(row.rowMap, resourceIriVar)
    } yield ExportedResource("", resourceIri.toString)
}

object ExportService {
  val layer = ZLayer.derive[ExportService]
}
