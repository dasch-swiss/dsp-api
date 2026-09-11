/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.export_

import zio.*
import zio.ZLayer

import dsp.errors.InconsistentRepositoryDataException as InconsistentDataException
import org.knora.sparqlbuilder.Iri
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

trait FindResourcesService {
  def findResources(
    project: KnoraProject,
    classIri: Option[ResourceClassIri],
  ): Task[Seq[ResourceIri]]

  def findResourcesByClass(
    project: KnoraProject,
    classIri: ResourceClassIri,
  ): Task[Seq[ResourceIri]] = findResources(project, Some(classIri))

  def findResourceIrisOrderedByLabel(
    project: KnoraProject,
    classIri: ResourceClassIri,
  ): Task[Seq[(ResourceIri, String)]]
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
                  value <- ZIO
                             .fromOption(row.rowMap.get(resourceIriVar))
                             .orElseFail(InconsistentDataException(""))
                  resourceIri <- ZIO
                                   .fromEither(ResourceIri.from(value))
                                   .mapError(InconsistentDataException(_))
                } yield resourceIri
              }
    } yield rows

  private val resourceIriVar = FindResourcesQueries.resourceIriVar.name
  private val labelVar       = FindResourcesQueries.labelVar.name

  // Resolves the class plus its subclasses to (projectGraph, class IRIs) for the VALUES-based queries below.
  // Shared by both query builders so the subclass-expansion invariant — which avoids the expensive
  // rdfs:subClassOf* triplestore traversal that times out for projects like BEOL (basicLetter has subclasses
  // across multiple ontologies) — lives in one place and cannot drift between the two query shapes.
  // The class itself is always the head of the list, so the VALUES clause never sees an empty list.
  private def graphAndClassIrisFor(project: KnoraProject, classIri: ResourceClassIri): Task[(Iri, Seq[Iri])] =
    ontologyRepo.findAllSubclassesBy(classIri).map { subclasses =>
      val projectGraph = projectService.getDataGraphForProject(project)
      val allClassIris = classIri.toInternalSchema.toIri +: subclasses.map(_.entityInfoContent.classIri.toIri)
      (Iri.unsafeFrom(projectGraph.value), allClassIris.map(Iri.unsafeFrom))
    }

  // The rendered SPARQL lives in FindResourcesQueries; all three queries there exclude deleted resources
  // (DEV-7008).
  private def buildClassQuery(project: KnoraProject, classIri: ResourceClassIri): Task[Select] =
    graphAndClassIrisFor(project, classIri).map(FindResourcesQueries.byClass)

  private def buildClassQueryOrderedByLabel(project: KnoraProject, classIri: ResourceClassIri): Task[Select] =
    graphAndClassIrisFor(project, classIri).map(FindResourcesQueries.byClassOrderedByLabel)

  // Returns (resourceIri, label) pairs sorted by (label, resourceIri) in the JVM. Label ordering is
  // case-sensitive lexicographic, matching the pre-streaming Scala `.sortBy(_.label)`; resourceIri is the
  // secondary key so resources sharing a label get a deterministic, reproducible order (Fuseki's ORDER BY does
  // not guarantee a stable tie-break). The labels are returned alongside the IRIs so the caller can resolve
  // link-target labels without issuing a second query.
  def findResourceIrisOrderedByLabel(
    project: KnoraProject,
    classIri: ResourceClassIri,
  ): Task[Seq[(ResourceIri, String)]] =
    for {
      sparql <- buildClassQueryOrderedByLabel(project, classIri)
      rows   <- triplestore.query(sparql).map(_.results.bindings)
      pairs  <- ZIO.foreach(rows) { row =>
                 for {
                   value <- ZIO
                              .fromOption(row.rowMap.get(resourceIriVar))
                              .orElseFail(InconsistentDataException(""))
                   resourceIri <- ZIO.fromEither(ResourceIri.from(value)).mapError(InconsistentDataException(_))
                 } yield (resourceIri, row.rowMap.getOrElse(labelVar, ""))
               }
    } yield pairs.sortBy { case (resourceIri, label) => (label, resourceIri.value) }

  private def buildAllResourcesQuery(project: KnoraProject): Select =
    FindResourcesQueries.allResources(Iri.unsafeFrom(projectService.getDataGraphForProject(project).value))
}

object FindResourcesService {
  val layer = ZLayer.derive[FindResourcesServiceLive]

  val Empty = ZLayer.succeed[FindResourcesService](
    new FindResourcesService {
      def findResources(project: KnoraProject, classIri: Option[ResourceClassIri]): Task[Seq[ResourceIri]] =
        ZIO.succeed(Seq())
      def findResourceIrisOrderedByLabel(
        project: KnoraProject,
        classIri: ResourceClassIri,
      ): Task[Seq[(ResourceIri, String)]] = ZIO.succeed(Seq())
    },
  )
}
