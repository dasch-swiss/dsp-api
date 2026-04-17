/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.export_

import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.sparqlbuilder.constraint.propertypath.builder.PropertyPathBuilder
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.`var` as variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.*
import zio.ZLayer

import dsp.errors.InconsistentRepositoryDataException as InconsistentDataException
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.SparqlTimeout

trait FindResourcesService {
  def findResources(
    project: KnoraProject,
    classIri: Option[ResourceClassIri],
  ): Task[Seq[ResourceIri]]

  def findResourcesByClass(
    project: KnoraProject,
    classIri: ResourceClassIri,
  ): Task[Seq[ResourceIri]] = findResources(project, Some(classIri))
}

final case class FindResourcesServiceLive(
  private val projectService: KnoraProjectService,
  private val triplestore: TriplestoreService,
  private val constructResponseUtilV2: ConstructResponseUtilV2,
  private val ontologyRepo: OntologyRepo,
) extends FindResourcesService {
  def findResources(
    project: KnoraProject,
    classIri: Option[ResourceClassIri],
  ): Task[Seq[ResourceIri]] =
    for {
      sparql <- classIri match {
                  case Some(iri) => buildClassQuery(project, iri)
                  case None      => ZIO.succeed(buildAllResourcesQuery(project))
                }
      rows <- triplestore.query(sparql).map(_.results.bindings)
      rows <- ZIO.foreach(rows) { row =>
                for {
                  value       <- ZIO.attempt(row.rowMap.getOrElse(resourceIriVar, throw new InconsistentDataException("")))
                  resourceIri <- ZIO
                                   .fromEither(ResourceIri.from(value))
                                   .mapError(InconsistentDataException(_))
                } yield resourceIri
              }
    } yield rows

  private val (classIriVar, resourceIriVar) = ("classIri", "resourceIri")

  // Resolves subclasses from the ontology cache and builds a VALUES-based SPARQL query.
  // This avoids expensive rdfs:subClassOf* triplestore traversal, which times out
  // for projects like BEOL where basicLetter has subclasses across multiple ontologies.
  private def buildClassQuery(project: KnoraProject, classIri: ResourceClassIri): Task[Select] =
    ontologyRepo.findAllSubclassesBy(classIri).map { subclasses =>
      val projectGraph = projectService.getDataGraphForProject(project)
      val allClassIris = classIri.toInternalSchema.toIri +: subclasses.map(_.entityInfoContent.classIri.toIri)
      val valuesClause = allClassIris.map(iri => s"<$iri>").mkString(" ")
      // String interpolation is used here because rdf4j SparqlBuilder does not support VALUES blocks.
      Select.gravsearch(
        s"""PREFIX knora-base: <${KB.NS.getName}>
           |SELECT DISTINCT ?$resourceIriVar WHERE {
           |  GRAPH <${projectGraph.value}> { ?$resourceIriVar a ?$classIriVar }
           |  VALUES ?$classIriVar { $valuesClause }
           |}""".stripMargin,
      )
    }

  private def buildAllResourcesQuery(project: KnoraProject): Select = {
    val selectPattern = SparqlBuilder
      .select(variable(resourceIriVar))
      .distinct()

    val projectGraph  = projectService.getDataGraphForProject(project)
    val resourceWhere =
      variable(resourceIriVar)
        .isA(variable(classIriVar))
        .from(Rdf.iri(projectGraph.value))

    val classSubclassOfResource =
      variable(classIriVar).has(PropertyPathBuilder.of(RDFS.SUBCLASSOF).zeroOrMore().build(), KB.Resource)

    val query = Queries
      .SELECT(selectPattern)
      .where(resourceWhere, classSubclassOfResource)
      .prefix(KB.NS, RDFS.NS)

    Select(query, SparqlTimeout.Gravsearch)
  }
}

object FindResourcesService {
  val layer = ZLayer.derive[FindResourcesServiceLive]

  val Empty = ZLayer.succeed[FindResourcesService]((_: KnoraProject, _: Option[ResourceClassIri]) => ZIO.succeed(Seq()))
}
