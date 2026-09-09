/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

import zio.*

import java.time.Instant

import dsp.errors.InconsistentRepositoryDataException
import org.knora.sparqlbuilder.*
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.api.v2.metadata.ResourceMetadataDto
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.SparqlTimeout

/** The resource metadata query backing `MetadataService.getResourcesMetadata`. */
private[service] object ResourcesMetadataQuery {

  val classIriVar: Variable             = Variable("classIri")
  val creationDateVar: Variable         = Variable("createdAt")
  val creatorIriVar: Variable           = Variable("creator")
  val deleteDateVar: Variable           = Variable("deletedAt")
  val labelVar: Variable                = Variable("label")
  val lastModificationDateVar: Variable = Variable("modifiedAt")
  val resourceIriVar: Variable          = Variable("resourceIri")

  /**
   * Builds the metadata query for the resources of a project.
   *
   * The class constraint sits outside the GRAPH block, mirroring the previous builder in which
   * the graph applied to the resource pattern group only.
   */
  def build(projectGraph: InternalIri, classIris: List[ResourceClassIri]): Select = {
    val graph = Iri.unsafeFrom(projectGraph.value)

    val classConstraint = classIris.map(cls => Iri.unsafeFrom(cls.toInternalSchema.toIri)) match {
      case Nil                => sparql"$classIriVar rdfs:subClassOf* knora-base:Resource ."
      case singleClass :: Nil => sparql"$resourceIriVar a $singleClass ."
      case many               => Fragments.union(many.map(cls => sparql"$resourceIriVar a $cls .")*)
    }

    val resourcePattern =
      Fragments.graph(sparql"$graph")(
        sparql"""|$resourceIriVar a $classIriVar ;
                 |  knora-base:creationDate $creationDateVar ;
                 |  knora-base:attachedToUser $creatorIriVar ;
                 |  rdfs:label $labelVar .
                 |${Fragments.optional(
            sparql"$resourceIriVar knora-base:lastModificationDate $lastModificationDateVar .",
          )}
                 |${Fragments.optional(sparql"$resourceIriVar knora-base:deleteDate $deleteDateVar .")}""",
      )

    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
               |
               |SELECT DISTINCT $classIriVar $creationDateVar $creatorIriVar $deleteDateVar $labelVar $lastModificationDateVar $resourceIriVar
               |WHERE {
               |  $classConstraint
               |  $resourcePattern
               |}""".render,
      // A whole-project scan, so it runs on the long timeout tier, as it did before.
      SparqlTimeout.Gravsearch,
    )
  }
}

final case class MetadataService(
  private val projectService: KnoraProjectService,
  private val triplestore: TriplestoreService,
)(implicit val sf: StringFormatter) {
  def getResourcesMetadata(
    project: KnoraProject,
    classIris: List[ResourceClassIri],
  ): Task[Seq[ResourceMetadataDto]] = {
    import ResourcesMetadataQuery.*

    val projectGraph = projectService.getDataGraphForProject(project)
    val query        = ResourcesMetadataQuery.build(projectGraph, classIris)

    def throwEx(field: String): Nothing = throw new InconsistentRepositoryDataException(
      s"Resource metadata query for project ${project.shortcode} returned inconsistent data for $field",
    )
    for {
      rows <-
        triplestore
          .query(query)
          .map(_.results.bindings)
          .timed
          .flatMap((d, s) =>
            ZIO.logInfo(s"Query took ${d.toMillis} ms and returned ${s.size} rows:\n${query.sparql}").as(s),
          )
      now  <- Clock.instant
      meta <- ZIO
                .attempt(rows.map { row =>
                  val classIri =
                    row.rowMap.getOrElse(classIriVar.name, throwEx(classIriVar.name)).toSmartIri.toComplexSchema.toIri
                  val resourceIri         = row.rowMap.getOrElse(resourceIriVar.name, throwEx(resourceIriVar.name))
                  val parsedResourceIri   = ResourceIri.unsafeFrom(resourceIri)
                  val arkUrl              = sf.resourceIriToArkUrl(parsedResourceIri)
                  val arkUrlWithTimestamp = sf.resourceIriToArkUrl(parsedResourceIri, Some(now))
                  val label               = row.rowMap.getOrElse(labelVar.name, throwEx(labelVar.name))
                  val creatorIri          =
                    row.rowMap
                      .getOrElse(creatorIriVar.name, throwEx(creatorIriVar.name))
                      .toSmartIri
                      .toComplexSchema
                      .toIri
                  val createdAt =
                    row.rowMap.get(creationDateVar.name).map(Instant.parse).getOrElse(throwEx(creationDateVar.name))
                  val deletedAt = row.rowMap.get(deleteDateVar.name).map(Instant.parse)
                  val lastModAt = row.rowMap.get(lastModificationDateVar.name).map(Instant.parse)
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
