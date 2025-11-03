/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.export_.model

import cats.implicits._
import org.knora.webapi.messages.twirl.queries.sparql
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.sparqlbuilder.constraint.propertypath.builder.PropertyPathBuilder
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.{`var` as variable, *}
import org.knora.webapi.slice.admin.domain.model.User
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
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
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.SchemaRendering
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.*

// TODO: this file is not done
// TODO: verify that knora-base:hasPermissions is also allowed to be exproted (it is to anonymous users)
final case class ExportService(
  private val projectService: KnoraProjectService,
  private val triplestore: TriplestoreService,
  private val iriConverter: IriConverter,
  private val constructResponseUtilV2: ConstructResponseUtilV2,
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
  ): Task[List[ExportedResource]] =
    for {
      resourceIris <- findResources(project, classIri, selectedProperties).map(_.map(_.toString))
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

      // TODO: will this omit results via pagination?
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
    } yield readResources.resources.map(convertToExportRow(_, selectedProperties)).toList

  // private def resolvePropertyLabels(
  //   propertyIris: List[String],
  //   userLang: String,
  //   fallbackLang: String,
  // ): Task[List[String]] =
  //   ZIO.foreach(propertyIris) { propertyIriString =>
  //     for {
  //       propertyIri <- iriConverter
  //                        .asPropertyIri(propertyIriString)
  //                        .mapError(BadRequestException.apply)
  //       propertyInfo <- ontologyRepo.findProperty(propertyIri)
  //       label = propertyInfo match {
  //                 case Some(info) =>
  //                   val labelsMap = info.entityInfoContent.getPredicateObjectsWithLangs(
  //                     OntologyConstants.Rdfs.Label.toSmartIri,
  //                   )
  //                   selectBestLabel(labelsMap, userLang, fallbackLang, propertyIriString)
  //                 case None =>
  //                   extractLocalName(propertyIriString)
  //               }
  //     } yield label
  //   }

  private def findResources(
    project: KnoraProject,
    classIri: ResourceClassIri,
    selectedProperties: List[PropertyIri],
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

  private def convertToExportRow(
    resource: ReadResourceV2,
    selectedProperties: List[PropertyIri],
  ): ExportedResource =
    ExportedResource(
      resource.resourceIri.toString,
      selectedProperties.foldMap { property =>
        resource.values.get(property.smartIri).foldMap { values =>
          values.foldMap { value =>
            Map(property.toString -> valueContentString(value.valueContent))
          }
        }
      },
    )

  private def valueContentString(valueContent: ValueContentV2): String =
    valueContent match {
      // Text values - extract just the actual text
      case TextValueContentV2(_, maybeString, _, _, _, _, _, _, _) =>
        maybeString.getOrElse("")

      // Integer values - extract the number
      case IntegerValueContentV2(_, valueHasInteger, _) =>
        valueHasInteger.toString

      // Decimal values - extract the decimal
      case DecimalValueContentV2(_, valueHasDecimal, _) =>
        valueHasDecimal.toString

      // Boolean values - extract the boolean
      case BooleanValueContentV2(_, valueHasBoolean, _) =>
        valueHasBoolean.toString

      // Date values - format as human readable
      case DateValueContentV2(_, startJDN, endJDN, _, _, _, _) =>
        // Simple conversion to approximate years
        val startYear = ((startJDN - 1721426) / 365.25).toInt
        val endYear   = ((endJDN - 1721426) / 365.25).toInt
        if (startYear == endYear) startYear.toString else s"$startYear - $endYear"

      // URI values - extract the URI string
      case UriValueContentV2(_, valueHasUri, _) =>
        valueHasUri

      // Color values - extract the color string
      case ColorValueContentV2(_, valueHasColor, _) =>
        valueHasColor

      // Geometry values - extract the geometry string
      case GeomValueContentV2(_, valueHasGeometry, _) =>
        valueHasGeometry

      // Time values - format the timestamp
      case TimeValueContentV2(_, timestamp, _) =>
        timestamp.toString

      // Interval values - format as range
      case IntervalValueContentV2(_, start, end, _) =>
        s"$start - $end"

      // Hierarchical list values - extract label or IRI
      case HierarchicalListValueContentV2(_, nodeIri, labelOption, _) =>
        labelOption.getOrElse(nodeIri)

      // Geoname values - extract the code
      case GeonameValueContentV2(_, code, _) =>
        code

      // File values - extract filename and basic info
      case fileValue: FileValueContentV2 =>
        extractFileInfo(fileValue)

      // Link values - extract target resource IRI
      case LinkValueContentV2(_, referredResourceIri, _, _, _, _) =>
        referredResourceIri
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
        val pages = pageCount.map(p => s", $p pages").getOrElse("")
        s"$filename$dimensions$pages"
      case _ =>
        fileValue.fileValue.originalFilename.getOrElse(fileValue.fileValue.internalFilename)
    }
}

object ExportService {
  val layer = ZLayer.derive[ExportService]
}
