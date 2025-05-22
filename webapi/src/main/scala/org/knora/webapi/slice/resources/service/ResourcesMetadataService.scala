package org.knora.webapi.slice.resources.service

import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.sparqlbuilder.constraint.propertypath.builder.PropertyPathBuilder
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.*
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.`var` as variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.*

import java.time.Instant

import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.slice.infrastructure.CsvService
import org.knora.webapi.slice.resources.api.ResourceMetadataDto
import org.knora.webapi.store.triplestore.api.TriplestoreService

final case class ResourcesMetadataService(
  private val csvService: CsvService,
  private val projectService: KnoraProjectService,
  private val triplestore: TriplestoreService,
)(implicit val sf: StringFormatter) {

  def getResourcesMetadata(project: KnoraProject): Task[List[ResourceMetadataDto]] = {
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

    val classConstraintPattern =
      variable(classIriVar).has(PropertyPathBuilder.of(RDFS.SUBCLASSOF).zeroOrMore().build(), KB.Resource)

    val query = Queries
      .SELECT(selectPattern)
      .where(wherePattern, classConstraintPattern)
      .prefix(prefix(KB.NS), prefix(RDFS.NS))

    def throwEx(field: String): Nothing = throw new InconsistentRepositoryDataException(
      s"Resource metadata query for project ${project.shortcode} returned inconsistent data for $field",
    )
    for {
      _ <- ZIO.logInfo(s"QUERY: \n ${query.getQueryString}")
      rows <- triplestore
                .select(query)
                .map(_.results.bindings)
                .timed
                .flatMap((d, s) => ZIO.logInfo(s"Query took ${d.toMillis} ms and returned ${s.size} rows").ignore.as(s))
      meta <- ZIO
                .attempt(rows.map { row =>
                  val resourceIri = row.rowMap.getOrElse(resourceIriVar, throwEx(resourceIriVar))
                  val classIri    = row.rowMap.getOrElse(classIriVar, throwEx(classIriVar))
                  val creatorIri  = row.rowMap.getOrElse(creatorIriVar, throwEx(creatorIriVar))
                  val label       = row.rowMap.getOrElse(labelVar, throwEx(labelVar))
                  val createdAt   = row.rowMap.get(creationDateVar).map(Instant.parse).getOrElse(throwEx(creationDateVar))
                  val deletedAt   = row.rowMap.get(deleteDateVar).map(Instant.parse)
                  val lastModAt   = row.rowMap.get(lastModificationDateVar).map(Instant.parse)
                  val arkUrl      = resourceIri.toSmartIri.fromResourceIriToArkUrl()
                  ResourceMetadataDto(classIri, resourceIri, arkUrl, label, creatorIri, createdAt, lastModAt, deletedAt)
                })
                .timed
                .flatMap((d, m) =>
                  ZIO.logInfo(s"Mapping took ${d.toMillis} ms and returned ${m.size} rows").ignore.as(m.toList),
                )
    } yield meta
  }

  def getResourcesMetadataAsCsv(project: KnoraProject): ZIO[Scope, Throwable, String] =
    getResourcesMetadata(project).flatMap(csvService.writeToString)
}

object ResourcesMetadataService {
  val layer = ZLayer.derive[ResourcesMetadataService]
}
