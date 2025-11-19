/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.export_

import cats.implicits.*
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.sparqlbuilder.constraint.propertypath.builder.PropertyPathBuilder
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.{`var` as variable, *}
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.*
import zio.ZLayer

import scala.collection.immutable.ListMap

import dsp.errors.InconsistentRepositoryDataException as InconsistentDataException
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.api.v3.export_.ExportedResource
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.domain.LanguageCode
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resources.service.ReadResourcesService
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.SparqlTimeout

final case class ExportService(
  private val projectService: KnoraProjectService,
  private val triplestore: TriplestoreService,
  private val iriConverter: IriConverter,
  private val constructResponseUtilV2: ConstructResponseUtilV2,
  private val ontologyRepo: OntologyRepo,
  private val readResources: ReadResourcesService,
) {
  val (
    classIriVar,
    resourceIriVar,
  ) = ("classIri", "resourceIri")

  def exportResources(
    project: KnoraProject,
    classIri: ResourceClassIri,
    selectedProperties: List[PropertyIri],
    requestingUser: User,
    language: LanguageCode,
    includeResourceIri: Boolean,
  ): Task[(List[String], List[ExportedResource])] =
    for {
      resourceIris <- findResources(project, classIri).map(_.map(_.toString))
      readResources <- readResources.readResourcesSequence(
                         resourceIris = resourceIris,
                         targetSchema = ApiV2Complex,
                         requestingUser = requestingUser,
                         preview = false,
                       )
      headers <- rowHeaders(selectedProperties, language, includeResourceIri)
      rows     = readResources.resources.toList.map(convertToExportRow(_, selectedProperties, includeResourceIri))
    } yield (headers, rows)

  private def findResources(
    project: KnoraProject,
    classIri: ResourceClassIri,
  ): Task[Seq[SmartIri]] =
    for {
      query <- ZIO.succeed(resourceQuery(project, classIri))
      rows  <- triplestore.selectWithTimeout(query, SparqlTimeout.Gravsearch).map(_.results.bindings)
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
  ): SelectQuery = {
    val selectPattern = SparqlBuilder
      .select(variable(resourceIriVar))
      .distinct()

    val projectGraph = projectService.getDataGraphForProject(project)
    val resourceWhere =
      variable(resourceIriVar)
        .isA(variable(classIriVar))
        .from(Rdf.iri(projectGraph.value))

    val classConstraint = variable(resourceIriVar).isA(Rdf.iri(classIri.toInternalSchema.toIri))

    val classSubclassOfResource =
      variable(classIriVar).has(PropertyPathBuilder.of(RDFS.SUBCLASSOF).zeroOrMore().build(), KB.Resource)

    Queries
      .SELECT(selectPattern)
      .where(resourceWhere, classConstraint, classSubclassOfResource)
      .prefix(prefix(KB.NS), prefix(RDFS.NS))
  }

  private def rowHeaders(
    selectedProperties: List[PropertyIri],
    language: LanguageCode,
    includeResourceIri: Boolean,
  ): Task[List[String]] =
    propertyLabelsTranslated(selectedProperties, language).map(labels =>
      Option.when(includeResourceIri)("Resource IRI").toList ++ ("Label" +: labels),
    )

  private def convertToExportRow(
    resource: ReadResourceV2,
    selectedProperties: List[PropertyIri],
    includeResourceIri: Boolean,
  ): ExportedResource =
    ExportedResource(
      ListMap.from(Option.when(includeResourceIri)("Resource IRI" -> resource.resourceIri.toString)) ++
        ListMap("Label" -> resource.label) ++
        ListMap.from(selectedProperties.map { property =>
          property.smartIri.toString -> {
            resource.values
              .get(property.smartIri.toInternalSchema)
              .map(_.toList)
              .combineAll
              .map(_.valueContent.valueHasString.replaceAll("\n", "\\n"))
              .mkString(" :: ")
          }
        }),
    )

  private def propertyLabelsTranslated(
    propertyIris: List[PropertyIri],
    language: LanguageCode,
  ): Task[List[String]] =
    ZIO.foreach(propertyIris) { propertyIri =>
      ontologyRepo.findProperty(propertyIri).flatMap { propertyInfo =>
        propertyInfo match {
          case Some(info) =>
            iriConverter
              .asSmartIri(OntologyConstants.Rdfs.Label)
              .map(info.entityInfoContent.getPredicateObjectsWithLangs(_))
              .map(labelsMap =>
                labelsMap
                  .get(language.value)
                  .orElse(labelsMap.get(LanguageCode.EN.value))
                  .orElse(labelsMap.values.headOption)
                  .getOrElse(propertyIri.toString),
              )
          case None =>
            ZIO.succeed(propertyIri.toString)
        }
      }
    }
}

object ExportService {
  val layer = ZLayer.derive[ExportService]
}
