/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.export_

import cats.*
import cats.implicits.*
import zio.*
import zio.ZLayer

import scala.collection.immutable.ListMap
import scala.util.chaining.scalaUtilChainingOps

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.admin.responder.listsmessages.ListRootNodeInfoADM
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadPropertyInfoV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.GeonameValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.HierarchicalListValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.LinkValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.ValueContentV2
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.api.v3.export_.ExportedResource
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.domain.LanguageCode
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.CsvService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resources.service.ReadResourcesService
import org.knora.webapi.util.WithAsIs

import ExportService.Internals.*

/* ExportService gathers data from IRIs and converts them to CSV rows.
 *
 * Design notes:
 * - hidden invariant: if `includeIris`, then both rowHeaders and exportSingleRow must duplicate the IRI rows
 * - when deciding to duplicate rows for `includeIris`, make sure to duplicate only depending on column-, not value-level data
 */
final case class ExportService(
  private val iriConverter: IriConverter,
  private val ontologyRepo: OntologyRepo,
  private val readResources: ReadResourcesService,
  private val findResources: FindResourcesService,
  private val listsResponder: ListsResponder,
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
      resourceIris  <- findResources.findResources(project, classIri).map(_.map(_.toString))
      readResources <- readResources.readResourcesSequencePar(
                         resourceIris = resourceIris,
                         targetSchema = ApiV2Complex,
                         requestingUser = requestingUser,
                         preview = false,
                         withDeleted = false,
                         skipRetrievalChecks = true,
                       )

      propertyIriInfos <- propertyIriInfos(selectedProperties)
      labelSmartIri    <- iriConverter.asSmartIri(OntologyConstants.Rdfs.Label)
      propsWithInfos    = selectedProperties.map(p => (p, propertyIriInfos.get(p)))
      headers           = rowHeaders(propsWithInfos, labelSmartIri, language, includeIris)

      rootVocabularies <- listsResponder.getLists(Some(Left(project.id)))
      vocabularies     <- ZIO.foreach(rootVocabularies.lists)(rootVocabularyLabels(_, language)).map(_.foldK)
      resourcesMap      = readResources.resourcesMap // must be cached
    } yield ExportedCsv(
      headers,
      readResources.resources.toList.sortBy(_.label).map {
        exportSingleRow(_, propsWithInfos, includeIris, resourcesMap, vocabularies)
      },
    )

  def toCsv(csv: ExportedCsv): Task[String] =
    ZIO.scoped(csvService.writeToString(csv.rows)(using csv.rowBuilder))

  private def rootVocabularyLabels(list: ListRootNodeInfoADM, language: LanguageCode): Task[Map[String, String]] =
    listsResponder
      .listGetRequestADM(ListIri.unsafeFrom(list.id))
      .map(_.toIriLabelMap(language.value))

  private def propertyIriInfos(
    propertyIris: List[PropertyIri],
  ): Task[Map[PropertyIri, ReadPropertyInfoV2]] =
    ZIO
      .foreach(propertyIris)(i => ontologyRepo.findProperty(i).map(_.map(i -> _)))
      .map(_.foldMapK(_.toMap))

  private def rowHeaders(
    propsWithInfo: List[(PropertyIri, Option[ReadPropertyInfoV2])],
    labelSmartIri: SmartIri,
    language: LanguageCode,
    includeIris: Boolean,
  ): List[String] =
    propsWithInfo.map { (propertyIri: PropertyIri, info: Option[ReadPropertyInfoV2]) =>
      val labelsMap = info.map(_.entityInfoContent.getPredicateObjectsWithLangs(labelSmartIri)).foldK

      val name = labelsMap
        .get(language.value)
        .orElse(labelsMap.get(LanguageCode.EN.value))
        .orElse(labelsMap.values.headOption)
        .getOrElse(propertyIri.toString)
      List(name) ++ (if (info.exists(_.isLinkValueProp) && includeIris) List(s"${name} IRI") else List.empty)
    }
      .pipe("Resource IRI" +: "Label" +: _.flatten)

  private def exportSingleRow(
    resource: ReadResourceV2,
    propsWithInfo: List[(PropertyIri, Option[ReadPropertyInfoV2])],
    includeIris: Boolean,
    resources: Map[IRI, ReadResourceV2],
    vocabularies: Map[String, String],
  ): ExportedResource =
    ExportedResource(
      ListMap("Resource IRI" -> resource.resourceIri.toString) ++
        ListMap("Label" -> resource.label) ++
        ListMap.from(
          propsWithInfo.flatMap { (propertyIri: PropertyIri, info: Option[ReadPropertyInfoV2]) =>
            val valueContents = resource.values.get(propertyIri.smartIri.toInternalSchema).foldK.map(_.valueContent)
            val values        = valueColumns(valueContents, includeIris, resources, vocabularies)

            val columnKey = propertyIri.smartIri.toString

            info.exists(_.isLinkValueProp) match {
              case true =>
                val linkValue = values.asOpt[LinkValue]
                val labels    = ListMap(columnKey -> linkValue.foldMapK(_.labels).mkString(ValueSep))
                val iris      = ListMap(s"${columnKey}_IRI" -> linkValue.foldMapK(_.labelIris).mkString(ValueSep))

                labels ++ iris.filter(_ => includeIris)
              case false =>
                ListMap(columnKey -> values.mkString)
            }
          },
        ),
    )

  // ValueContents are rightfully expected to have the same type, which is not guaranteed in the code
  private def valueColumns(
    vcs: Seq[ValueContentV2],
    includeIris: Boolean,
    resources: Map[IRI, ReadResourceV2],
    vocabularies: Map[String, String],
  ): IntermediateValue =
    vcs.foldMap { vc =>
      vc match
        case lvc: LinkValueContentV2 =>
          val resource = lvc.nestedResource.orElse(resources.get(lvc.referredResourceIri))
          LinkValue(
            List(resource.map(_.label).getOrElse("")),
            List(stringFormat(vc.valueHasString)).filter(_ => includeIris),
          )
        case gvc: GeonameValueContentV2 =>
          RegularValue(List("https://www.geonames.org/" ++ gvc.valueHasGeonameCode))
        case lvc: HierarchicalListValueContentV2 =>
          RegularValue(List(vocabularies.get(lvc.valueHasString).getOrElse("")))
        case vc =>
          RegularValue(List(stringFormat(vc.valueHasString)))
    }

  private def stringFormat(s: String): String =
    s.replaceAll("\n", "\\\\n").replaceAll("\u001e", " ")
}

object ExportService {
  val layer = ZLayer.derive[ExportService]

  object Internals {
    val ValueSep = " :: "

    sealed trait IntermediateValue extends WithAsIs[IntermediateValue] {
      def mkString = (this match
        case RegularValue(ls) => ls
        case LinkValue(ls, _) => ls
        case NullValue        => List()
      ).mkString(ValueSep)
    }

    case object NullValue                                                     extends IntermediateValue
    final case class RegularValue(values: List[String])                       extends IntermediateValue
    final case class LinkValue(labels: List[String], labelIris: List[String]) extends IntermediateValue

    given Monoid[IntermediateValue] = new Monoid[IntermediateValue] {
      def empty = NullValue

      def combine(a: IntermediateValue, b: IntermediateValue): IntermediateValue =
        (a, b) match
          case (RegularValue(vs1), RegularValue(vs2))     => RegularValue(vs1 ++ vs2)
          case (LinkValue(ls1, li1), LinkValue(ls2, li2)) => LinkValue(ls1 ++ ls2, li1 ++ li2) // should never happen
          case (NullValue, a)                             => a
          case (a, _)                                     => a                                 // should never happen
    }
  }
}
