/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.export_

import cats.implicits.*
import zio.*
import zio.ZLayer

import scala.collection.immutable.ListMap

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.api.v3.export_.ExportedResource
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.domain.LanguageCode
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.CsvService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resources.service.ReadResourcesService
import org.knora.webapi.messages.v2.responder.valuemessages.ValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.GeonameValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.LinkValueContentV2

final case class ExportService(
  private val iriConverter: IriConverter,
  private val ontologyRepo: OntologyRepo,
  private val readResources: ReadResourcesService,
  private val findAllResources: FindAllResourcesService,
  private val csvService: CsvService,
) {
  type Resources = List[ReadResourceV2]

  def exportResources(
    project: KnoraProject,
    classIri: ResourceClassIri,
    selectedProperties: List[PropertyIri],
    requestingUser: User,
    language: LanguageCode,
    includeResourceIri: Boolean,
  ): Task[ExportedCsv] =
    for {
      resourceIris <- findAllResources(project, classIri).map(_.map(_.toString))
      readResources <- readResources.readResourcesSequence(
                         resourceIris = resourceIris,
                         targetSchema = ApiV2Complex,
                         requestingUser = requestingUser,
                         preview = false,
                         withDeleted = false,
                       )
      headers <- rowHeaders(selectedProperties, language, includeResourceIri)
    } yield ExportedCsv(
      headers,
      sort(readResources.resources.toList).map(exportSingleRow(_, selectedProperties, includeResourceIri)),
    )

  def toCsv(csv: ExportedCsv): Task[String] =
    ZIO.scoped(csvService.writeToString(csv.rows)(using csv.rowBuilder))

  private def sort(resources: Resources): Resources =
    resources.sortBy(_.label)

  // TODO: use the property/column information OntologyRepo to make additional columns for link values
  private def rowHeaders(
    selectedProperties: List[PropertyIri],
    language: LanguageCode,
    includeResourceIri: Boolean,
  ): Task[List[String]] =
    propertyLabelsTranslated(selectedProperties, language).map(labels =>
      Option.when(includeResourceIri)("Resource IRI").toList ++ ("Label" +: labels),
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

  private def exportSingleRow(
    resource: ReadResourceV2,
    selectedProperties: List[PropertyIri],
    includeResourceIri: Boolean,
  ): ExportedResource =
    ExportedResource(
      ListMap.from(Option.when(includeResourceIri)("Resource IRI" -> resource.resourceIri.toString)) ++
        ListMap("Label" -> resource.label) ++
        ListMap.from(
          selectedProperties.flatMap { property =>
            val readValues = resource.values.get(property.smartIri.toInternalSchema).foldK
            valueColumns(property, readValues.map(_.valueContent)).view.mapValues(vs => vs.mkString(" :: ")).toList
          },
        ),
    )

  private def valueColumns(property: PropertyIri, vcs: Seq[ValueContentV2]): Map[String, List[String]] =
    vcs.foldMap { vc =>
      vc match
        case gvc: GeonameValueContentV2 =>
          Map(property.smartIri.toString -> List("https://www.geonames.org/" ++ gvc.valueHasGeonameCode))
        case lvc: LinkValueContentV2 =>
          Map(
            property.smartIri.toString           -> List("label to be"),
            s"${property.smartIri.toString}_IRI" -> List(vc.valueHasString.replaceAll("\n", "\\\\n")),
          )
        case vc =>
          Map(property.smartIri.toString -> List(vc.valueHasString.replaceAll("\n", "\\\\n")))
    }
}

object ExportService {
  val layer = ZLayer.derive[ExportService]
}
