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
import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.GeonameValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.LinkValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.ValueContentV2
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

final case class ExportService(
  private val iriConverter: IriConverter,
  private val ontologyRepo: OntologyRepo,
  private val readResources: ReadResourcesService,
  private val findAllResources: FindAllResourcesService,
  private val csvService: CsvService,
) {
  def exportResources(
    project: KnoraProject,
    classIri: ResourceClassIri,
    selectedProperties: List[PropertyIri],
    requestingUser: User,
    language: LanguageCode,
    includeIris: Boolean,
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
      headers <- rowHeaders(selectedProperties, language, includeIris)
    } yield ExportedCsv(
      headers,
      sort(readResources.resources.toList).map(
        exportSingleRow(_, selectedProperties, includeIris, readResources.resourcesMap),
      ),
    )

  def toCsv(csv: ExportedCsv): Task[String] =
    ZIO.scoped(csvService.writeToString(csv.rows)(using csv.rowBuilder))

  private def sort(resources: List[ReadResourceV2]): List[ReadResourceV2] =
    resources.sortBy(_.label)

  // NOTE: hidden invariant: if `includeIris`, then both rowHeaders and exportSingleRow must duplicate the IRI rows
  private def rowHeaders(
    selectedProperties: List[PropertyIri],
    language: LanguageCode,
    includeIris: Boolean,
  ): Task[List[String]] =
    propertyLabelsTranslated(selectedProperties, language, includeIris).map(labels =>
      "Resource IRI" +: "Label" +: labels,
    )

  private def propertyLabelsTranslated(
    propertyIris: List[PropertyIri],
    language: LanguageCode,
    includeIris: Boolean,
  ): Task[List[String]] =
    ZIO
      .foreach(propertyIris) { propertyIri =>
        ontologyRepo
          .findProperty(propertyIri)
          .flatMap { propertyInfo =>
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
                  .map(name =>
                    if (info.isLinkValueProp) List(name) ++ List(s"${name} IRI").filter(_ => includeIris)
                    else List(name),
                  )
              case None =>
                ZIO.succeed(List(propertyIri.toString))
            }
          }
      }
      .map(_.flatten)

  private def exportSingleRow(
    resource: ReadResourceV2,
    selectedProperties: List[PropertyIri],
    includeIris: Boolean,
    resources: Map[IRI, ReadResourceV2],
  ): ExportedResource =
    ExportedResource(
      ListMap("Resource IRI" -> resource.resourceIri.toString) ++
        ListMap("Label" -> resource.label) ++
        ListMap.from(
          selectedProperties.flatMap { property =>
            val readValues = resource.values.get(property.smartIri.toInternalSchema).foldK
            valueColumns(property, readValues.map(_.valueContent), includeIris, resources).map { case (k, vs) =>
              (k, vs.mkString(" :: "))
            }
          },
        ),
    )

  private def valueColumns(
    property: PropertyIri,
    vcs: Seq[ValueContentV2],
    includeIris: Boolean,
    resources: Map[IRI, ReadResourceV2],
  ): ListMap[String, List[String]] =
    Some(vcs)
      .filter(_.nonEmpty)
      .map { vcs =>
        vcs.map { vc =>
          vc match
            case gvc: GeonameValueContentV2 =>
              ListMap(property.smartIri.toString -> List("https://www.geonames.org/" ++ gvc.valueHasGeonameCode))
            case lvc: LinkValueContentV2 =>
              val resource    = lvc.nestedResource.orElse(resources.get(lvc.referredResourceIri))
              val propertyIri = property.smartIri.toString
              List[ListMap[String, List[String]]](
                ListMap(propertyIri -> List(resource.map(_.label).getOrElse(""))),
                ListMap(s"${propertyIri}_IRI" -> List(stringFormat(vc.valueHasString))).filter(_ => includeIris),
              ).fold(ListMap.empty)(_ ++ _)
            case vc =>
              ListMap(property.smartIri.toString -> List(stringFormat(vc.valueHasString)))
        }.fold(ListMap.empty)(_ ++ _)
      }
      .getOrElse(ListMap(property.smartIri.toString -> List()))

  private def stringFormat(s: String): String =
    s.replaceAll("\n", "\\\\n").replaceAll("\u001e", " ")
}

object ExportService {
  val layer = ZLayer.derive[ExportService]
}
