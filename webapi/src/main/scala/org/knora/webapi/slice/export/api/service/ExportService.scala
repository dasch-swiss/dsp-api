/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.export_

import cats.*
import cats.implicits.*
import com.github.tototoshi.csv.CSVFormat
import zio.*
import zio.ZLayer
import zio.stream.ZStream

import java.nio.charset.StandardCharsets
import scala.collection.immutable.ListMap
import scala.util.chaining.scalaUtilChainingOps

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.listsmessages.ListRootNodeInfoADM
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadPropertyInfoV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffTagStringAttributeV2
import org.knora.webapi.messages.v2.responder.valuemessages.AudioFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.GeonameValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.HierarchicalListValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.LinkValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.MovingImageFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.StillImageExternalFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.StillImageFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.TextFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.TextValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.ValueContentV2
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.api.v3.`export`.LegalInfo
import org.knora.webapi.slice.api.v3.`export`.MetadataRecord
import org.knora.webapi.slice.api.v3.export_.ExportedResource
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.domain.LanguageCode
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.CsvRowBuilder
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
  private val sf: StringFormatter,
  private val appConfig: AppConfig,
) {
  private given StringFormatter                = sf
  private val footnoteTagIri: SmartIri         = OntologyConstants.Standoff.StandoffFootnoteTag.toSmartIri
  private val footnoteContentPropIri: SmartIri = OntologyConstants.Standoff.StandoffFootnoteTagHasContent.toSmartIri

  def exportResourcesOai(
    project: KnoraProject,
    requestingUser: User,
  ): Task[String] = {
    import zio.json.*

    for {
      resourceIris  <- findResources.findResources(project, None)
      readResources <- readResources.readResourcesSequencePar(
                         resourceIris = resourceIris,
                         targetSchema = ApiV2Complex,
                         requestingUser = requestingUser,
                         preview = false,
                         withDeleted = false,
                         skipRetrievalChecks = true,
                       )
      descriptionProp <- findDescriptionProperty(project)

      records = readResources.resources.toList.map { r =>
                  val description = descriptionProp.flatMap(r.values.get(_).flatMap(_.headOption))
                  MetadataRecord(
                    id = r.resourceIri.toString,
                    pid = sf.resourceIriToArkUrl(r.resourceIri),
                    label = Map("en" -> r.label),
                    accessRights = "Full Open Access",
                    legalInfo = LegalInfo.publicDomain,
                    howToCite = r.label,
                    publisher = "DaSCH",
                    source = None,
                    description = description.map(v => Map("en" -> v.valueContent.valueHasString)),
                    dateCreated = Some(r.creationDate.toString),
                    dateModified = r.lastModificationDate.map(_.toString),
                    datePublished = Some(r.creationDate.toString),
                    typeOfData = typeOfDataOf(r),
                    size = None,
                    keywords = List.empty,
                  )
                }
    } yield records.toJsonPretty
  }

  private def findDescriptionProperty(project: KnoraProject): Task[Option[SmartIri]] =
    (project.shortcode.value.toUpperCase() match {
      case "0803" => Some("http://www.knora.org/ontology/0803/incunabula#description")
      case "081C" => Some("http://www.knora.org/ontology/081C/hdm#hasDescription")
      case "0868" => Some("http://www.knora.org/ontology/0868/SolarEclipses#hasDescription")
      case "1612" => Some("http://www.knora.org/ontology/1612/Data#TextShort")
      case _      => None
    }).map {
      iriConverter.asInternalSmartIri(_).map(Some(_))
    }.getOrElse(ZIO.none)

  private def typeOfDataOf(r: ReadResourceV2): Option[String] =
    r.values.values.flatten.map(_.valueContent).collectFirst {
      case _: StillImageFileValueContentV2 | _: StillImageExternalFileValueContentV2 => "Image"
      case _: MovingImageFileValueContentV2                                          => "Audiovisual"
      case _: AudioFileValueContentV2                                                => "Sound"
      case _: TextFileValueContentV2                                                 => "Text"
      case _: TextValueContentV2                                                     => "Text"
    }

  def exportResources(
    project: KnoraProject,
    classIri: ResourceClassIri,
    selectedProperties: List[PropertyIri],
    requestingUser: User,
    language: LanguageCode,
    includeIris: Boolean,
    includeArkUrls: Boolean,
    // batchSize/parallelism default to the operational values from AppConfig (`app.export`), overridable per
    // deployment via env var. The explicit params exist only so tests can force resources across batch boundaries.
    batchSize: Int = appConfig.`export`.batchSize,
    parallelism: Int = appConfig.`export`.parallelism,
  ): Task[ZStream[Any, Throwable, Byte]] = {
    case class StreamingExportContext(
      orderedIris: Seq[ResourceIri],
      linkLabels: Map[ResourceIri, String],
      propsWithInfos: List[(PropertyIri, Option[ReadPropertyInfoV2])],
      vocabularies: Map[String, String],
      rowBuilder: CsvRowBuilder[ExportedResource],
      headerBytes: Chunk[Byte],
    )

    val setup: Task[StreamingExportContext] =
      for {
        orderedWithLabels <- findResources.findResourceIrisOrderedByLabel(project, classIri)
        orderedIris        = orderedWithLabels.map(_._1)
        // The ordered query already carries each resource's label, so the cross-batch link-label map is built
        // from it directly — no second SPARQL round-trip. Link targets outside the exported class are absent
        // here and fall back to "" in valueColumns, same as before.
        linkLabels        = orderedWithLabels.toMap
        propertyIriInfos <- propertyIriInfos(selectedProperties)
        labelSmartIri    <- iriConverter.asSmartIri(OntologyConstants.Rdfs.Label)
        propsWithInfos    = selectedProperties.map(p => (p, propertyIriInfos.get(p)))
        headers           = rowHeaders(propsWithInfos, labelSmartIri, language, includeIris, includeArkUrls)
        rowBuilder        = makeRowBuilder(headers)
        rootVocabularies <- listsResponder.getLists(Some(Left(project.id)))
        vocabularies     <- ZIO.foreach(rootVocabularies.lists)(rootVocabularyLabels(_, language)).map(_.foldK)
        headerBytes       = Chunk.fromArray(csvService.writeHeaderToString(rowBuilder).getBytes(StandardCharsets.UTF_8))
      } yield StreamingExportContext(orderedIris, linkLabels, propsWithInfos, vocabularies, rowBuilder, headerBytes)

    // `setup` runs eagerly as part of this Task so that failures during setup (ordered-IRI fetch, link-label
    // fetch, vocabulary load, header encoding) surface to the caller *before* the response status is committed
    // — i.e. a 5xx, not a `200 OK` with an empty/truncated body. Only per-batch failures after the first bytes
    // have been flushed remain an inherent (unavoidable) mid-stream truncation.
    setup.map { ctx =>
      val iriPosition: Map[ResourceIri, Int] = ctx.orderedIris.zipWithIndex.toMap

      val batches: Seq[Seq[ResourceIri]] = ctx.orderedIris.grouped(batchSize).toSeq

      val rowsStream: ZStream[Any, Throwable, Byte] = ZStream
        .fromIterable(batches)
        .mapZIOPar(parallelism) { batchIris =>
          for {
            batchReadResources <- readResources.readResourcesSequence(
                                    batchIris,
                                    targetSchema = ApiV2Complex,
                                    requestingUser = requestingUser,
                                    withDeleted = false,
                                    queryStandoff = true,
                                    skipRetrievalChecks = true,
                                    standoffTagFilter = Some(footnoteTagIri),
                                  )
            orderedBatch = batchReadResources.resources.toList
                             .sortBy(r => iriPosition.getOrElse(r.resourceIri, Int.MaxValue))
            rows <- ZIO.foreach(orderedBatch) { r =>
                      exportSingleRow(
                        r,
                        propsWithInfo = ctx.propsWithInfos,
                        includeIris = includeIris,
                        includeArkUrls = includeArkUrls,
                        linkLabels = ctx.linkLabels,
                        vocabularies = ctx.vocabularies,
                      )
                    }
            rowBytes = rows.map { row =>
                         csvService.writeRowToString(row)(using ctx.rowBuilder).getBytes(StandardCharsets.UTF_8)
                       }
          } yield Chunk.fromIterable(rowBytes).flatMap(Chunk.fromArray)
        }
        .flattenChunks

      ZStream.fromChunk(ctx.headerBytes) ++ rowsStream
    }
  }

  private def makeRowBuilder(headers: List[String]): CsvRowBuilder[ExportedResource] =
    new CsvRowBuilder[ExportedResource] {
      def header: Seq[String]                     = headers
      def values(row: ExportedResource): Seq[Any] = row.properties.values.toList
    }

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
    includeArkUrls: Boolean,
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
      .pipe(hdrs =>
        (if includeArkUrls then List("ARK URL") else Nil) ++
          List("Resource IRI", "Label") ++
          hdrs.flatten,
      )

  private def exportSingleRow(
    resource: ReadResourceV2,
    propsWithInfo: List[(PropertyIri, Option[ReadPropertyInfoV2])],
    includeIris: Boolean,
    includeArkUrls: Boolean,
    linkLabels: Map[ResourceIri, String],
    vocabularies: Map[String, String],
  ): Task[ExportedResource] = {
    val arkEntryTask: Task[ListMap[String, String]] =
      if includeArkUrls then
        ZIO
          .attempt(sf.resourceIriToArkUrl(resource.resourceIri))
          .orDie
          .map(url => ListMap("ARK URL" -> url))
      else ZIO.succeed(ListMap.empty)

    for arkEntry <- arkEntryTask
    yield ExportedResource(
      arkEntry ++
        ListMap("Resource IRI" -> resource.resourceIri.toString) ++
        ListMap("Label" -> resource.label) ++
        ListMap.from(
          propsWithInfo.flatMap { (propertyIri: PropertyIri, info: Option[ReadPropertyInfoV2]) =>
            val valueContents = resource.values.get(propertyIri.smartIri.toInternalSchema).foldK.map(_.valueContent)
            val values        = valueColumns(valueContents, includeIris, linkLabels, vocabularies)

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
  }

  // ValueContents are rightfully expected to have the same type, which is not guaranteed in the code
  private def valueColumns(
    vcs: Seq[ValueContentV2],
    includeIris: Boolean,
    linkLabels: Map[ResourceIri, String],
    vocabularies: Map[String, String],
  ): IntermediateValue =
    vcs.foldMap { vc =>
      vc match
        case lvc: LinkValueContentV2 =>
          val label = lvc.nestedResource.map(_.label).orElse(linkLabels.get(lvc.referredResourceIri))
          LinkValue(
            List(label.getOrElse("")),
            List(stringFormat(vc.valueHasString)).filter(_ => includeIris),
          )
        case gvc: GeonameValueContentV2 =>
          RegularValue(List("https://www.geonames.org/" ++ gvc.valueHasGeonameCode))
        case lvc: HierarchicalListValueContentV2 =>
          RegularValue(List(vocabularies.get(lvc.valueHasString).getOrElse("")))
        case tvc: TextValueContentV2 => textValueColumn(tvc)
        case vc                      =>
          RegularValue(List(stringFormat(vc.valueHasString)))
    }

  private def textValueColumn(tvc: TextValueContentV2): RegularValue = {
    val footnoteTags     = tvc.standoff.filter(_.standoffTagClassIri == footnoteTagIri).sortBy(_.startPosition)
    val footnoteContents = footnoteTags.flatMap(
      _.attributes.collectFirst {
        case StandoffTagStringAttributeV2(iri, value) if iri == footnoteContentPropIri => value
      },
    )
    if (footnoteContents.isEmpty)
      RegularValue(List(stringFormat(tvc.valueHasString)))
    else {
      // Insert [n] markers right-to-left (foldRight) to preserve character offsets in valueHasString
      val textWithMarkers = footnoteTags.zipWithIndex.foldRight(tvc.valueHasString) { case ((tag, i), text) =>
        text.patch(tag.endPosition, s"[${i + 1}]", 0)
      }
      val footnoteList = footnoteContents.zipWithIndex.map { case (fn, i) => s"[${i + 1}] $fn" }.mkString("\n")
      RegularValue(List(stringFormat(s"$textWithMarkers\n$footnoteList")))
    }
  }

  private def stringFormat(s: String): String =
    s.replaceAll("<[^>]+>", "").replaceAll("\u001e", " ")
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
