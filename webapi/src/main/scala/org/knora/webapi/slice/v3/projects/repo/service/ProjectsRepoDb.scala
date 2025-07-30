/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.v3.projects.repo.service

import zio.*

import org.knora.webapi.messages.admin.responder.listsmessages.ListsGetResponseADM
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.lists.domain.ListsService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.v3.projects.domain.model.DomainTypes.*
import org.knora.webapi.slice.v3.projects.domain.model.ProjectsRepo
import org.knora.webapi.slice.v3.projects.repo.query.ProjectsQueryBuilder
import org.knora.webapi.store.triplestore.api.TriplestoreService

private[repo] final case class ProjectsRepoDb(
  knoraProjectService: KnoraProjectService,
  listsService: ListsService,
  listsResponder: ListsResponder,
  ontologyRepo: OntologyRepo,
  triplestore: TriplestoreService,
) extends ProjectsRepo {

  override def findProjectByIri(id: ProjectIri): Task[Option[KnoraProject]] =
    knoraProjectService.findById(id)

  override def findProjectByShortcode(shortcode: Shortcode): Task[Option[KnoraProject]] =
    knoraProjectService.findByShortcode(shortcode)

  override def findOntologiesByProject(projectId: ProjectIri): Task[List[ReadOntologyV2]] =
    ontologyRepo.findByProject(projectId)

  override def findListsByProject(projectId: ProjectIri): Task[ListsGetResponseADM] =
    listsResponder.getLists(Some(Left(projectId)))

  override def countInstancesByClasses(
    shortcode: Shortcode,
    shortname: Shortname,
    classIris: List[String],
  ): Task[Map[String, Int]] =
    if (classIris.isEmpty) {
      ZIO.succeed(Map.empty)
    } else {
      val query = ProjectsQueryBuilder.buildInstanceCountQuery(shortcode, shortname, classIris)
      triplestore
        .query(query)
        .map(result => buildClassCountMap(classIris, result))
        .mapError(ex =>
          new RuntimeException(
            s"Failed to count instances for classes in project ${shortcode.value}/${shortname.value}",
            ex,
          ),
        )
    }

  override def getClassesFromOntology(ontologyIri: OntologyIri): Task[List[(String, Map[String, String])]] = {
    val query = ProjectsQueryBuilder.buildClassesQuery(ontologyIri)
    triplestore
      .query(query)
      .map(processClassQueryResults)
      .mapError(ex => new RuntimeException(s"Failed to get classes from ontology ${ontologyIri.value}", ex))
  }

  private def processClassQueryResults(
    result: SparqlSelectResult,
  ): List[(String, Map[String, String])] =
    result.results.bindings
      .groupBy(row => row.rowMap("class"))
      .map { case (classIri, rows) =>
        classIri -> rows.flatMap(extractLabelAndLanguage).toMap
      }
      .toList

  private def buildClassCountMap(classIris: List[String], result: SparqlSelectResult): Map[String, Int] =
    classIris.map(_ -> 0).toMap ++ result.results.bindings.map { row =>
      val classIri = row.rowMap("class")
      val count    = row.rowMap.get("count").fold(0)(_.toInt)
      classIri -> count
    }.toMap

  private def extractLabelAndLanguage(row: VariableResultsRow): Option[(String, String)] =
    for {
      label <- row.rowMap.get("label")
      lang  <- row.rowMap.get("lang")
      if lang.nonEmpty
    } yield lang -> label
}

private[repo] object ProjectsRepoDb {
  val layer = ZLayer.derive[ProjectsRepoDb]
}
