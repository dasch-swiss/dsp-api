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

import dsp.errors.{InconsistentRepositoryDataException => InconsistentDataException}
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
import scala.util.chaining.scalaUtilChainingOps
import org.knora.webapi.slice.common.KnoraIris.PropertyIri

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
    selectedProperties: List[PropertyIri],
  ): Task[List[ExportedResource]] =
    for {
      rows <- findResources(project, classIri, selectedProperties)

      // val resourceRequestSparql =
      //   Construct(
      //     sparql.v2.txt
      //       .getResourcePropertiesAndValues(
      //         resourceIris = mainResourceIris,
      //         preview = false,
      //         withDeleted = false,
      //         queryAllNonStandoff = true,
      //         queryStandoff = queryStandoff,
      //         maybePropertyIri = None,
      //         maybeVersionDate = None,
      //       ),
      //   )
      // for {
      //   resourceRequestResponse <- triplestore.query(resourceRequestSparql).flatMap(_.asExtended)
      //   mainResourcesAndValueRdfData = constructResponseUtilV2.splitMainResourcesAndValueRdfData(
      //                                    resourceRequestResponse,
      //                                    requestingUser,
      //                                  )

    } yield List()

    // if (mainResourceIris.nonEmpty) {
    //   // Yes. Do a CONSTRUCT query to get the contents of those resources. If we're querying standoff, get
    //   // at most one page of standoff per text value.
    //   val resourceRequestSparql =
    //     Construct(
    //       sparql.v2.txt
    //         .getResourcePropertiesAndValues(
    //           resourceIris = mainResourceIris,
    //           preview = false,
    //           queryAllNonStandoff = true,
    //           withDeleted = false,
    //           queryStandoff = queryStandoff,
    //           maybePropertyIri = None,
    //           maybeVersionDate = None,
    //         ),
    //     )

    //   for {
    //     resourceRequestResponse <- triplestore.query(resourceRequestSparql).flatMap(_.asExtended)

    //     // separate resources and values
    //     mainResourcesAndValueRdfData = constructResponseUtilV2.splitMainResourcesAndValueRdfData(
    //                                      resourceRequestResponse,
    //                                      requestingUser,
    //                                    )

    //     // If we're querying standoff, get XML-to standoff mappings.
    //     mappings <-
    //       if (queryStandoff) {
    //         constructResponseUtilV2.getMappingsFromQueryResultsSeparated(
    //           mainResourcesAndValueRdfData.resources,
    //           requestingUser,
    //         )
    //       } else {
    //         ZIO.succeed(Map.empty[IRI, MappingAndXSLTransformation])
    //       }

    //     // Construct a ReadResourceV2 for each resource that the user has permission to see.
    //     readResourcesSequence <- constructResponseUtilV2.createApiResponse(
    //                                mainResourcesAndValueRdfData = mainResourcesAndValueRdfData,
    //                                orderByResourceIri = mainResourceIris,
    //                                pageSizeBeforeFiltering = mainResourceIris.size,
    //                                mappings = mappings,
    //                                queryStandoff = queryStandoff,
    //                                versionDate = None,
    //                                calculateMayHaveMoreResults = true,
    //                                targetSchema = schemaAndOptions.schema,
    //                                requestingUser = requestingUser,
    //                              )
    //   } yield readResourcesSequence
    // } else {

  private def findResources(
    project: KnoraProject,
    classIri: ResourceClassIri,
    selectedProperties: List[PropertyIri],
  ): Task[Seq[SmartIri]] =
    for {
      query           <- ZIO.succeed(resourceQuery(project, classIri, selectedProperties))
      dr              <- triplestore.selectWithTimeout(query, SparqlTimeout.Gravsearch).map(_.results.bindings).timed
      (duration, rows) = dr
      _               <- ZIO.logInfo(s"ExportService: ${rows.size} rows in ${duration.toMillis} ms: ${query.getQueryString}")
      rows <- ZIO.foreach(rows) { row =>
                for {
                  value    <- ZIO.attempt(row.rowMap.getOrElse(resourceIriVar, throw new InconsistentDataException("")))
                  smartIri <- iriConverter.asSmartIri(value)
                } yield smartIri
              }
    } yield rows

  private def resourceQuery(
    project: KnoraProject,
    classIri: ResourceClassIri,
    selectedProperties: List[PropertyIri],
  ): SelectQuery = {
    val selectPattern = SparqlBuilder
      .select(variable(resourceIriVar))
      .distinct()

    val projectGraph = projectService.getDataGraphForProject(project)
    val resourceWhere =
      variable(resourceIriVar)
        .isA(variable(classIriVar))
        // .pipe(propConstraints.foldLeft(_) { case (w, (p, v)) => w.andHas(p, v) })
        .from(Rdf.iri(projectGraph.value))

    val classConstraint = variable(resourceIriVar).isA(Rdf.iri(classIri.toInternalSchema.toIri))

    val classSubclassOfResource =
      variable(classIriVar).has(PropertyPathBuilder.of(RDFS.SUBCLASSOF).zeroOrMore().build(), KB.Resource)

    Queries
      .SELECT(selectPattern)
      .where(resourceWhere, classConstraint, classSubclassOfResource)
      .prefix(prefix(KB.NS), prefix(RDFS.NS))
  }
}

object ExportService {
  val layer = ZLayer.derive[ExportService]
}
