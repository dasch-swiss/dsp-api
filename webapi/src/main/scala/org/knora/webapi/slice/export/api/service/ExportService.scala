/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.export_.model

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

import dsp.errors.InconsistentRepositoryDataException as InconsistentDataException
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.export_.api.ExportedResource
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.SparqlTimeout

import scala.collection.immutable.ListMap

final case class ExportService(
  private val projectService: KnoraProjectService,
  private val triplestore: TriplestoreService,
  private val iriConverter: IriConverter,
  private val constructResponseUtilV2: ConstructResponseUtilV2,
  private val ontologyRepo: OntologyRepo,
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
    language: String,
  ): Task[(List[String], List[ExportedResource])] =
    for {
      resourceIris <- findResources(project, classIri).map(_.map(_.toString))
      resourcesWithValues <-
        triplestore
          .query(
            Construct(
              sparql.v2.txt
                .getResourcePropertiesAndValues(
                  resourceIris = resourceIris,
                  preview = false,
                  queryAllNonStandoff = true,
                  withDeleted = false,
                  queryStandoff = false,
                  maybePropertyIri = None,
                  maybeVersionDate = None,
                ),
            ),
          )
          .flatMap(_.asExtended(iriConverter.sf))
          .map(constructResponseUtilV2.splitMainResourcesAndValueRdfData(_, requestingUser))

      readResources <-
        constructResponseUtilV2.createApiResponse(
          mainResourcesAndValueRdfData = resourcesWithValues,
          orderByResourceIri = resourceIris,
          pageSizeBeforeFiltering = resourceIris.size,
          mappings = Map.empty,
          queryStandoff = false,
          versionDate = None,
          calculateMayHaveMoreResults = true,
          targetSchema = ApiV2Complex,
          requestingUser = requestingUser,
        )
      headers <- rowHeaders(selectedProperties, language)
      rows     = readResources.resources.toList.map(convertToExportRow(_, selectedProperties))
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
    language: String,
  ): Task[List[String]] =
    propertyLabelsTranslated(selectedProperties, language).map(
      "Label" +: "Resource IRI" +: _
    )

  private def convertToExportRow(
    resource: ReadResourceV2,
    selectedProperties: List[PropertyIri],
  ): ExportedResource =
    ExportedResource(
      resource.label,
      resource.resourceIri.toString,
      ListMap.from(selectedProperties.map { property =>
        property.smartIri.toString -> {
          val values: List[ReadValueV2] = resource.values.get(property.smartIri).map(_.toList).combineAll
          values.map(v => valueContentString(v.valueContent)).mkString(" :: ")
        }
      }),
    )

  // NOTE: good candidate to move to the ValueContentV2 trait itself, also would enable better testing
  private def valueContentString(valueContent: ValueContentV2): String =
    valueContent match {
      case TextValueContentV2(_, maybeString, _, _, _, _, _, _, _) =>
        maybeString.getOrElse("")

      case IntegerValueContentV2(_, valueHasInteger, _) =>
        valueHasInteger.toString

      case DecimalValueContentV2(_, valueHasDecimal, _) =>
        valueHasDecimal.toString

      case BooleanValueContentV2(_, valueHasBoolean, _) =>
        valueHasBoolean.toString

      case d: DateValueContentV2 =>
        d.toString

      case UriValueContentV2(_, valueHasUri, _) =>
        valueHasUri

      case ColorValueContentV2(_, valueHasColor, _) =>
        valueHasColor

      case GeomValueContentV2(_, valueHasGeometry, _) =>
        valueHasGeometry

      case TimeValueContentV2(_, timestamp, _) =>
        timestamp.toString

      case IntervalValueContentV2(_, start, end, _) =>
        s"$start - $end"

      case HierarchicalListValueContentV2(_, nodeIri, labelOption, _) =>
        labelOption.getOrElse(nodeIri)

      case GeonameValueContentV2(_, code, _) =>
        code

      case fileValue: FileValueContentV2 =>
        extractFileInfo(fileValue)

      case LinkValueContentV2(_, referredResourceIri, _, _, _, _) =>
        referredResourceIri

      case DeletedValueContentV2(ontologySchema, comment) => "deleted value"
    }

  private def extractFileInfo(fileValue: FileValueContentV2): String =
    fileValue match {
      case StillImageFileValueContentV2(_, file, dimX, dimY, _) =>
        val filename = file.originalFilename.getOrElse(file.internalFilename)
        s"$filename (${dimX}×${dimY})"
      case DocumentFileValueContentV2(_, file, pageCount, dimX, dimY, _) =>
        val filename = file.originalFilename.getOrElse(file.internalFilename)
        val dimensions = (dimX, dimY) match {
          case (Some(x), Some(y)) => s" (${x}×${y})"
          case _                  => ""
        }
        s"$filename$dimensions"
      case _ =>
        fileValue.fileValue.originalFilename.getOrElse(fileValue.fileValue.internalFilename)
    }

  private def propertyLabelsTranslated(
    propertyIris: List[PropertyIri],
    userLang: String,
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
                  .get(userLang)
                  .orElse(labelsMap.get("en"))
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
