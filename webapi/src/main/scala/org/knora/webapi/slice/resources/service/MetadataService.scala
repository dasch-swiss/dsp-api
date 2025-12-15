/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

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
import org.knora.webapi.slice.api.v2.metadata.ResourceMetadataDto
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.SparqlTimeout

final case class MetadataService(
  private val projectService: KnoraProjectService,
  private val triplestore: TriplestoreService,
)(implicit val sf: StringFormatter) {
  def getResourcesMetadata(
    project: KnoraProject,
    classIris: List[ResourceClassIri],
  ): Task[Seq[ResourceMetadataDto]] = {
    val (
      classIriVar,
      creationDateVar,
      creatorIriVar,
      deleteDateVar,
      labelVar,
      lastModificationDateVar,
      resourceIriVar,
    ) = ("classIri", "createdAt", "creator", "deletedAt", "label", "modifiedAt", "resourceIri")

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
      case Nil          => variable(classIriVar).has(PropertyPathBuilder.of(RDFS.SUBCLASSOF).zeroOrMore().build(), KB.Resource)
      case head :: Nil  => variable(resourceIriVar).isA(head)
      case head :: tail =>
        val pat = variable(resourceIriVar).isA(head)
        pat.union(tail.map(c => variable(resourceIriVar).isA(c)): _*)
    }

    val query = Queries
      .SELECT(selectPattern)
      .where(classConstraintPattern, wherePattern)
      .prefix(prefix(KB.NS), prefix(RDFS.NS))

    def throwEx(field: String): Nothing = throw new InconsistentRepositoryDataException(
      s"Resource metadata query for project ${project.shortcode} returned inconsistent data for $field",
    )
    for {
      rows <-
        triplestore
          .selectWithTimeout(query, SparqlTimeout.Gravsearch)
          .map(_.results.bindings)
          .timed
          .flatMap((d, s) =>
            ZIO.logInfo(s"Query took ${d.toMillis} ms and returned ${s.size} rows:\n${query.getQueryString}").as(s),
          )
      now  <- Clock.instant
      meta <- ZIO
                .attempt(rows.map { row =>
                  val classIri =
                    row.rowMap.getOrElse(classIriVar, throwEx(classIriVar)).toSmartIri.toComplexSchema.toIri
                  val resourceIri         = row.rowMap.getOrElse(resourceIriVar, throwEx(resourceIriVar))
                  val arkUrl              = resourceIri.toSmartIri.fromResourceIriToArkUrl(None)
                  val arkUrlWithTimestamp = resourceIri.toSmartIri.fromResourceIriToArkUrl(Some(now))
                  val label               = row.rowMap.getOrElse(labelVar, throwEx(labelVar))
                  val creatorIri          =
                    row.rowMap.getOrElse(creatorIriVar, throwEx(creatorIriVar)).toSmartIri.toComplexSchema.toIri
                  val createdAt = row.rowMap.get(creationDateVar).map(Instant.parse).getOrElse(throwEx(creationDateVar))
                  val deletedAt = row.rowMap.get(deleteDateVar).map(Instant.parse)
                  val lastModAt = row.rowMap.get(lastModificationDateVar).map(Instant.parse)
                  ResourceMetadataDto(
                    classIri,
                    resourceIri,
                    arkUrl,
                    arkUrlWithTimestamp,
                    label,
                    creatorIri,
                    createdAt,
                    lastModAt,
                    deletedAt,
                  )
                })
    } yield meta
  }
}

object MetadataService {
  val layer = ZLayer.derive[MetadataService]
}
