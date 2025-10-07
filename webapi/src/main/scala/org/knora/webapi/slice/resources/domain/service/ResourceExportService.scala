/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.domain.service

import com.github.tototoshi.csv.CSVFormat
import sttp.model.MediaType
import zio.*
import zio.json.*

import dsp.errors.BadRequestException
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.responders.v2.SearchResponderV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.CsvRowBuilder
import org.knora.webapi.slice.infrastructure.CsvService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resources.api.model.ExportFormat
import org.knora.webapi.slice.resources.api.model.ExportRequest
import org.knora.webapi.slice.resources.api.model.ExportResult
import org.knora.webapi.slice.resources.api.model.ResourceExportRow

final class ResourceExportService(
  private val searchService: SearchResponderV2,
  private val authService: AuthorizationRestService,
  private val iriConverter: IriConverter,
  private val csvService: CsvService,
  private val ontologyRepo: OntologyRepo,
) {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  /**
   * Validates the export request parameters.
   */
  private def validateExportRequest(request: ExportRequest): Task[Unit] =
    ZIO
      .fail(BadRequestException("Resource class IRI cannot be empty"))
      .when(request.resourceClass.trim.isEmpty) *>
      ZIO
        .fail(BadRequestException("Empty property IRI found in selected properties"))
        .when(request.selectedProperties.exists(_.exists(_.trim.isEmpty))) *>
      ZIO
        .fail(BadRequestException("Language code cannot be empty"))
        .when(request.language.exists(_.trim.isEmpty)) *>
      ZIO.unit

  def exportResourcesByClass(
    request: ExportRequest,
    projectIri: ProjectIri,
    user: User,
  ): Task[ExportResult] =
    for {
      // Validate input parameters
      _ <- validateExportRequest(request)

      // Validate user permissions for the project
      _ <- authService.ensureProjectMember(user, projectIri.toProjectShortcode).ignore

      // Convert string IRIs to SmartIris with better error messages
      resourceClassIri <- iriConverter
                            .asResourceClassIri(request.resourceClass)
                            .mapBoth(
                              error =>
                                BadRequestException(s"Invalid resource class IRI '${request.resourceClass}': $error"),
                              _.smartIri.toInternalSchema,
                            )

      selectedPropertyIris <- ZIO
                                .foreach(request.selectedProperties.getOrElse(List.empty))(iriConverter.asPropertyIri)
                                .mapBoth(
                                  error => BadRequestException(s"Invalid property IRI in selected properties: $error"),
                                  _.map(_.smartIri.toInternalSchema),
                                )

      // Fetch resources and determine properties to export
      resourcesAndProperties <- fetchResourcesAndDiscoverProperties(
                                  projectIri,
                                  resourceClassIri,
                                  selectedPropertyIris,
                                  user,
                                )
      (resources, propertiesToExport) = resourcesAndProperties

      // Generate export based on format
      result <- request.format match {
                  case ExportFormat.CSV =>
                    for {
                      // Resolve property labels for user-friendly CSV headers
                      userLang       <- ZIO.succeed(request.language.getOrElse(user.lang))
                      fallbackLang   <- ZIO.succeed("en") // Default fallback, could be configurable
                      propertyLabels <- resolvePropertyLabels(propertiesToExport, userLang, fallbackLang)
                      csvResult <-
                        generateCsvExport(resources, propertiesToExport, propertyLabels, request.includeReferenceIris)
                    } yield csvResult
                  case ExportFormat.JSON => generateJsonExport(resources)
                }
    } yield result

  /**
   * Resolves property IRIs to user-friendly labels with multi-language support.
   *
   * @param propertyIris List of property IRIs to resolve
   * @param userLang User's preferred language
   * @param fallbackLang System fallback language
   * @return List of user-friendly labels in the same order as input IRIs
   */
  private def resolvePropertyLabels(
    propertyIris: List[String],
    userLang: String,
    fallbackLang: String,
  ): Task[List[String]] =
    ZIO.foreach(propertyIris) { propertyIriString =>
      for {
        propertyIri <- iriConverter
                         .asPropertyIri(propertyIriString)
                         .mapError(BadRequestException.apply)
        propertyInfo <- ontologyRepo.findProperty(propertyIri)
        label = propertyInfo match {
                  case Some(info) =>
                    val labelsMap = info.entityInfoContent.getPredicateObjectsWithLangs(
                      OntologyConstants.Rdfs.Label.toSmartIri,
                    )
                    selectBestLabel(labelsMap, userLang, fallbackLang, propertyIriString)
                  case None =>
                    extractLocalName(propertyIriString)
                }
      } yield label
    }

  /**
   * Selects the best label based on language preferences.
   *
   * @param labelsMap Map of language codes to label text
   * @param userLang User's preferred language
   * @param fallbackLang System fallback language
   * @param propertyIri Original property IRI as fallback
   * @return The best available label
   */
  private def selectBestLabel(
    labelsMap: Map[String, String],
    userLang: String,
    fallbackLang: String,
    propertyIri: String,
  ): String =
    labelsMap
      .get(userLang)
      .orElse(labelsMap.get(fallbackLang))
      .orElse(labelsMap.values.headOption)
      .getOrElse(extractLocalName(propertyIri))

  /**
   * Extracts a readable local name from a property IRI.
   * For example: "http://example.org/ontology#location" -> "location"
   *
   * @param propertyIri Full property IRI
   * @return Extracted local name
   */
  private def extractLocalName(propertyIri: String): String = {
    val iri        = propertyIri
    val hashIndex  = iri.lastIndexOf('#')
    val slashIndex = iri.lastIndexOf('/')

    val separatorIndex = Math.max(hashIndex, slashIndex)
    if (separatorIndex >= 0 && separatorIndex < iri.length - 1) {
      iri.substring(separatorIndex + 1)
    } else {
      iri
    }
  }

  private def fetchResourcesAndDiscoverProperties(
    projectIri: ProjectIri,
    resourceClassIri: SmartIri,
    selectedPropertyIris: List[SmartIri],
    user: User,
    page: Int = 0,
  ): Task[(List[ResourceExportRow], List[String])] =
    for {
      // Use existing search service to get resources by class and project
      resourcesResponse <- searchService
                             .searchResourcesByProjectAndClassV2(
                               projectIri,
                               resourceClassIri,
                               orderByProperty = None,
                               page,
                               schemaAndOptions =
                                 org.knora.webapi.SchemaRendering(org.knora.webapi.ApiV2Complex, Set.empty),
                               user,
                             )
                             .mapError(error =>
                               BadRequestException(
                                 s"Failed to fetch resources for class '${resourceClassIri}': ${error.getMessage}",
                               ),
                             )

      // Validate that resources were found
      _ <-
        ZIO
          .fail(
            BadRequestException(s"No resources found for class '${resourceClassIri}' in project '${projectIri.value}'"),
          )
          .when(resourcesResponse.resources.isEmpty)

      // Determine which properties to export
      propertiesToExport <- if (selectedPropertyIris.nonEmpty) {
                              // Use user-specified properties
                              ZIO.succeed(selectedPropertyIris.map(_.toString))
                            } else {
                              // Auto-discover all properties from the resources
                              discoverAllProperties(resourcesResponse.resources)
                            }

      // Convert selected properties back to SmartIris for processing
      propertyIris <- ZIO
                        .foreach(propertiesToExport)(iriConverter.asPropertyIri)
                        .mapBoth(BadRequestException.apply, _.map(_.smartIri.toInternalSchema))

      // Convert to export rows
      exportRows = resourcesResponse.resources.map(convertToExportRow(_, propertyIris, propertiesToExport))
    } yield (exportRows.toList, propertiesToExport)

  private def discoverAllProperties(resources: Seq[ReadResourceV2]): Task[List[String]] =
    ZIO.succeed {
      val allPropertyIris = resources
        .flatMap(_.values.keys)
        .map(_.toString)
        .toSet
        .toList
        .sorted // Ensure consistent ordering

      // If no properties were found (because search didn't load them),
      // provide a fallback list of common properties for the resource class
      if (allPropertyIris.isEmpty && resources.nonEmpty) {
        // Get the resource class and determine common properties based on it
        val resourceClass = resources.head.resourceClassIri.toString
        getCommonPropertiesForClass(resourceClass)
      } else {
        allPropertyIris
      }
    }

  private def getCommonPropertiesForClass(resourceClassIri: String): List[String] =
    resourceClassIri match {
      case iri if iri.contains("incunabula#book") =>
        List(
          "http://www.knora.org/ontology/0803/incunabula#title",
          "http://www.knora.org/ontology/0803/incunabula#pubdate",
          "http://www.knora.org/ontology/0803/incunabula#publisher",
          "http://www.knora.org/ontology/0803/incunabula#location",
          "http://www.knora.org/ontology/0803/incunabula#partof",
          "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue",
        )
      case iri if iri.contains("mls#Lemma") =>
        List(
          "http://www.knora.org/ontology/0807/mls#hasLemmaText",
          "http://www.knora.org/ontology/0807/mls#hasLexicalEntries",
          "http://api.knora.org/ontology/knora-api/v2#hasIncomingLinkValue",
        )
      case _ =>
        // Default fallback properties that most resources might have
        List(
          "http://api.knora.org/ontology/knora-api/v2#hasStillImageFileValue",
          "http://api.knora.org/ontology/knora-api/v2#hasIncomingLinkValue",
          "http://www.knora.org/ontology/knora-base#hasComment",
        )
    }

  /**
   * Extracts a clean, human-readable value from a ValueContentV2 instance.
   * Removes all the internal metadata and serialization noise.
   */
  private def extractCleanValue(valueContent: ValueContentV2): String =
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

      // Fallback for any other value types
      case _ =>
        valueContent.toString
    }

  /**
   * Extracts meaningful information from file values.
   */
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
        val filename = fileValue.fileValue.originalFilename.getOrElse(fileValue.fileValue.internalFilename)
        filename
    }

  private def convertToExportRow(
    resource: ReadResourceV2,
    selectedPropertyIris: List[SmartIri],
    propertyOrder: List[String],
  ): ResourceExportRow = {
    // Create a map with all properties in the specified order
    val properties = propertyOrder.map { propertyIriString =>
      val propertyIri = selectedPropertyIris.find(_.toString == propertyIriString)

      val value = propertyIri.flatMap { propIri =>
        resource.values.get(propIri) match {
          case Some(values) if values.nonEmpty =>
            // Take first value and extract clean, human-readable representation
            val cleanValue = extractCleanValue(values.head.valueContent)
            Some(cleanValue)
          case _ => None
        }
      }.getOrElse("")

      propertyIriString -> value
    }.toMap

    ResourceExportRow(
      resourceIri = resource.resourceIri.toString,
      resourceClass = resource.resourceClassIri.toString,
      projectIri = resource.projectADM.id.value,
      properties = properties,
    )
  }

  private def generateCsvExport(
    rows: List[ResourceExportRow],
    propertiesToExport: List[String],
    propertyLabels: List[String],
    includeReferenceIris: Boolean,
  ): Task[ExportResult] = {
    implicit val csvFormat: CSVFormat = new CSVFormat {
      val delimiter           = ','
      val quoteChar           = '"'
      val escapeChar          = '"'
      val lineTerminator      = "\n"
      val quoting             = com.github.tototoshi.csv.QUOTE_MINIMAL
      val treatEmptyLineAsNil = false
    }
    implicit val rowBuilder: CsvRowBuilder[ResourceExportRow] =
      createCsvRowBuilder(propertiesToExport, propertyLabels, includeReferenceIris)

    for {
      csvData <- ZIO
                   .scoped(csvService.writeToString(rows))
                   .mapError(error => BadRequestException(s"Failed to generate CSV: ${error.getMessage}"))
      filename = s"resources_export_${java.time.Instant.now().getEpochSecond}.csv"
    } yield ExportResult(
      data = csvData,
      mediaType = MediaType.TextCsv,
      filename = filename,
    )
  }

  private def generateJsonExport(rows: List[ResourceExportRow]): Task[ExportResult] =
    for {
      jsonData <- ZIO
                    .attempt(rows.toJson)
                    .mapError(error => BadRequestException(s"Failed to generate JSON: ${error.getMessage}"))
      filename = s"resources_export_${java.time.Instant.now().getEpochSecond}.json"
    } yield ExportResult(
      data = jsonData,
      mediaType = MediaType.ApplicationJson,
      filename = filename,
    )

  private def createCsvRowBuilder(
    propertiesToExport: List[String],
    propertyLabels: List[String],
    includeReferenceIris: Boolean,
  ): CsvRowBuilder[ResourceExportRow] =
    new CsvRowBuilder[ResourceExportRow] {
      override def header: Seq[String] = {
        val baseHeaders = if (includeReferenceIris) {
          Seq("Resource IRI", "Resource Class", "Project IRI")
        } else {
          Seq.empty
        }
        baseHeaders ++ propertyLabels
      }

      override def values(row: ResourceExportRow): Seq[Any] = {
        val baseValues = if (includeReferenceIris) {
          Seq(row.resourceIri, row.resourceClass, row.projectIri)
        } else {
          Seq.empty
        }
        baseValues ++ propertiesToExport.map(row.getValue)
      }
    }
}

object ResourceExportService {
  val layer: ZLayer[
    SearchResponderV2 & AuthorizationRestService & IriConverter & CsvService & OntologyRepo,
    Nothing,
    ResourceExportService,
  ] =
    ZLayer.derive[ResourceExportService]
}

// Extension to convert ProjectIri to shortcode (simplified for MVP)
extension (projectIri: ProjectIri) {
  private def toProjectShortcode: org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode =
    org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode.unsafeFrom(
      projectIri.value.split("/").lastOption.getOrElse("unknown"),
    )
}
